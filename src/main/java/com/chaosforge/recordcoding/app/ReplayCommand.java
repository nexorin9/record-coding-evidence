package com.chaosforge.recordcoding.app;

import com.chaosforge.recordcoding.config.FieldMapping;
import com.chaosforge.recordcoding.protocol.dicom.DicomJsonModel;
import com.chaosforge.recordcoding.protocol.hl7.Hl7Message;
import com.chaosforge.recordcoding.protocol.hl7.Hl7Parser;
import com.chaosforge.recordcoding.protocol.hl7.Orur01Mapper;
import com.chaosforge.recordcoding.transport.EvidenceTags;
import com.chaosforge.recordcoding.util.Hashes;
import com.chaosforge.recordcoding.util.Json;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** 离线回放：读 data/ 公开样例，把两路来源解析成同一张对照表草案打到 stdout 并写 CSV。 */
public final class ReplayCommand {
    public record Result(int rows, List<String> lines, Path csv) {}

    private ReplayCommand() {}

    public static int run(String[] args, PrintStream out, PrintStream err) {
        Map<String, String> opts = AppCli.parseOptions(args);
        Path data = Path.of(opts.getOrDefault("data", "data"));
        String visit = opts.getOrDefault("visit", defaultVisit(data));
        try {
            Result r = execute(data, visit, out::println);
            out.println("对照表草案共 " + r.rows() + " 行，已写入 " + r.csv());
            return App.EXIT_OK;
        } catch (IllegalStateException e) {
            err.println("离线回放失败：" + e.getMessage());
            return App.EXIT_PRECONDITION;
        } catch (IOException e) {
            err.println("离线回放失败（IO）：" + e.getMessage());
            return App.EXIT_PRECONDITION;
        }
    }

    public static Result execute(Path data, String visitNo, Consumer<String> sink) throws IOException {
        List<Path> dicom = jsonFiles(data.resolve("dicom"));
        List<Path> hl7 = hl7Files(data.resolve("hl7"));
        if (dicom.isEmpty() && hl7.isEmpty()) {
            throw new IllegalStateException("data/ 下无 dicom 或 hl7 样例，请先运行 scripts/fetch-public-data.sh（不产出空表）");
        }
        List<String> lines = new ArrayList<>();
        lines.add(EvidenceRowDraft.header());
        for (Path p : dicom) {
            DicomJsonModel model = DicomJsonModel.parse(Files.readString(p, StandardCharsets.UTF_8));
            for (String tag : EvidenceTags.ALL) {
                lines.add(EvidenceRowDraft.of(visitNo, "PACS·QIDO-RS", tag, tag,
                        readableValue(model, tag)).csv());
            }
        }
        Orur01Mapper mapper = new Orur01Mapper(FieldMapping.fromClasspath());
        for (Path p : hl7) {
            String text = Files.readString(p, StandardCharsets.UTF_8);
            try {
                Hl7Message msg = Hl7Parser.parse(text);
                for (Orur01Mapper.MappedField f : mapper.map(msg)) {
                    lines.add(EvidenceRowDraft.of(visitNo, "RIS·HL7-MLLP", f.role(), f.locator(),
                            f.isUnmapped() ? "unmapped" : f.value()).csv());
                }
            } catch (RuntimeException e) {
                lines.add(EvidenceRowDraft.of(visitNo, "RIS·HL7-MLLP", "parse_error", p.getFileName().toString(),
                        "SEGMENT_TOO_SHORT/UNMAPPED").csv());
            }
        }
        Path csv = data.resolve("out").resolve("replay-table.csv");
        Files.createDirectories(csv.getParent());
        Files.write(csv, lines, StandardCharsets.UTF_8);
        for (String line : lines) sink.accept(line);
        return new Result(lines.size() - 1, List.copyOf(lines), csv);
    }

    /**
     * 序列型 tag（如 00081032 操作编码序列）按 DICOM 编码串口径投影为「编码^含义」，
     * 不把整段嵌套 DICOM JSON 塞进取值列；缺值一律显式 missing，不静默填默认值。
     */
    private static String readableValue(DicomJsonModel model, String tag) {
        List<Object> raw = model.rawValues(tag);
        if (raw.isEmpty()) return "missing";
        Object first = raw.get(0);
        if (first instanceof Map<?, ?> item) {
            String code = nestedScalar(item, "00080100");
            String meaning = nestedScalar(item, "00080104");
            if (code.isBlank() && meaning.isBlank()) return "missing(VALUE_EMPTY)";
            if (code.isBlank()) return meaning;
            return meaning.isBlank() ? code : code + "^" + meaning;
        }
        if (first instanceof List<?>) return "missing(VR_MISMATCH)";
        return String.valueOf(first);
    }

    private static String nestedScalar(Map<?, ?> item, String tag) {
        Object element = item.get(tag);
        if (element instanceof Map<?, ?> el && el.get("Value") instanceof List<?> values
                && !values.isEmpty() && !(values.get(0) instanceof Map)) {
            return String.valueOf(values.get(0));
        }
        return "";
    }

    public static String defaultVisit(Path data) {
        Path manifest = data.resolve("manifest.json");
        if (!Files.exists(manifest)) return "VISIT-000001";
        try {
            Object parsed = Json.parse(Files.readString(manifest, StandardCharsets.UTF_8));
            if (parsed instanceof Map<?, ?> m && m.get("sample_visit_no") != null) {
                return String.valueOf(m.get("sample_visit_no"));
            }
            if (parsed instanceof Map<?, ?> m && m.get("visits") instanceof List<?> l && !l.isEmpty()) {
                return String.valueOf(l.get(0));
            }
        } catch (IOException | RuntimeException ignored) { /* 回落默认住院号 */ }
        return "VISIT-000001";
    }

    private static List<Path> jsonFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (var s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        }
    }

    private static List<Path> hl7Files(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (var s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".hl7") || p.toString().endsWith(".txt"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        }
    }

    /** 对照表草案行：来源系统 / 字段 / 取值 / 哈希。 */
    public record EvidenceRowDraft(String visitNo, String source, String field, String locator, String value) {
        public static String header() { return "visit_no,source_system,field,field_locator,value,hash8"; }
        public static EvidenceRowDraft of(String v, String s, String f, String l, String value) {
            return new EvidenceRowDraft(v, s, f, l, value);
        }
        public String csv() {
            String h = Hashes.shortHash(source + "|" + locator + "|" + value, 8);
            return String.join(",", esc(visitNo), esc(source), esc(field), esc(locator), esc(value), h);
        }
        private static String esc(String s) {
            String v = s == null ? "" : s.replace("\r", " ").replace("\n", " ");
            return v.contains(",") || v.contains("\"") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
        }
    }
}
