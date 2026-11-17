package com.chaosforge.recordcoding.app;

import com.chaosforge.recordcoding.coding.CodingValidation;
import com.chaosforge.recordcoding.coding.DictionaryLookup;
import com.chaosforge.recordcoding.coding.Icd10StructureValidator;
import com.chaosforge.recordcoding.domain.MedicalRecord;
import com.chaosforge.recordcoding.evidence.EvidenceAssembler;
import com.chaosforge.recordcoding.evidence.EvidenceSnapshot;
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

/** 数百条级批量跑批：归一 + 三层校验，产出条数 / 耗时 / 吞吐 / 一致率并落 logs/。 */
public final class BatchRunner {
    public record Summary(int rows, long elapsedMillis, double throughput, double consistency,
                          List<String> deviations) {}

    private BatchRunner() {}

    public static int run(String[] args, PrintStream out, PrintStream err) {
        Map<String, String> opts = AppCli.parseOptions(args);
        Path data = Path.of(opts.getOrDefault("data", "data"));
        try {
            Summary s = execute(data);
            out.println("批量跑批完成");
            out.println("  条数：" + s.rows());
            out.println("  耗时：" + s.elapsedMillis() + " ms");
            out.println("  吞吐：" + String.format("%.1f", s.throughput()) + " 条/秒");
            out.println("  归一一致率：" + String.format("%.2f%%", s.consistency() * 100));
            out.println("  偏差样例：" + (s.deviations().isEmpty() ? "无" : String.join(", ", s.deviations())));
            Path log = Path.of("logs", "batch-summary.log");
            Files.createDirectories(log.getParent());
            Files.writeString(log, "条数=" + s.rows() + " 耗时_ms=" + s.elapsedMillis()
                    + " 吞吐=" + String.format("%.1f", s.throughput())
                    + " 一致率=" + String.format("%.4f", s.consistency()) + "\n",
                    StandardCharsets.UTF_8);
            if (s.consistency() < 0.99) {
                err.println("归一一致率低于 99% 阈值，跑批判失败并列出偏差样例。");
                return App.EXIT_PRECONDITION;
            }
            return App.EXIT_OK;
        } catch (IOException e) {
            err.println("批量跑批失败：" + e.getMessage());
            return App.EXIT_PRECONDITION;
        }
    }

    @SuppressWarnings("unchecked")
    public static Summary execute(Path data) throws IOException {
        List<Path> emr = list(data.resolve("emr"), ".json");
        List<Path> dicom = list(data.resolve("dicom"), ".json");
        if (emr.isEmpty() && dicom.isEmpty()) {
            throw new IOException("data/ 下无样例，请先运行 scripts/fetch-public-data.sh");
        }
        EvidenceAssembler assembler = new EvidenceAssembler();
        Icd10StructureValidator icd10 = new Icd10StructureValidator();
        CodingValidation validation = new CodingValidation(DictionaryLookup.unavailable());
        int total = 0;
        int consistent = 0;
        List<String> deviations = new ArrayList<>();
        long start = System.currentTimeMillis();
        for (int i = 0; i < dicom.size(); i++) {
            Map<String, Object> study = Json.object(Files.readString(dicom.get(i), StandardCharsets.UTF_8));
            Map<String, Object> flat = new java.util.LinkedHashMap<>();
            for (String tag : com.chaosforge.recordcoding.transport.EvidenceTags.ALL) {
                Object raw = study.get(tag);
                flat.put(tag, raw instanceof Map<?, ?> m ? Json.firstValue((Map<String, Object>) m) : null);
            }
            EvidenceSnapshot snapshot = assembler.assemble("VISIT-BATCH", "BATCH", List.of(flat), List.of());
            // 归一一致率：同一份样例两次装配哈希必须一致，且证据行字段定位完整
            EvidenceSnapshot again = assembler.assemble("VISIT-BATCH", "BATCH", List.of(flat), List.of());
            boolean ok = snapshot.evidenceHash().equals(again.evidenceHash())
                    && snapshot.rows().stream().allMatch(r -> r.fieldLocator() != null && r.hash().length() == 64);
            if (ok) consistent++;
            else if (deviations.size() < 10) deviations.add(dicom.get(i).getFileName().toString());
            total++;
        }
        for (Path p : emr) {
            MedicalRecord record = MedicalRecord.fromEmr(Json.object(Files.readString(p, StandardCharsets.UTF_8)));
            CodingValidation.Result r = validation.validate(record);
            boolean ok = !r.findings().isEmpty()
                    && r.findings().stream().allMatch(f -> f.ruleVersion() != null && f.basis() != null);
            if (ok) consistent++;
            else if (deviations.size() < 10) deviations.add(p.getFileName().toString());
            total++;
        }
        long elapsed = Math.max(1, System.currentTimeMillis() - start);
        double throughput = total * 1000.0 / elapsed;
        double rate = total == 0 ? 0 : (double) consistent / total;
        return new Summary(total, elapsed, throughput, rate, List.copyOf(deviations));
    }

    private static List<Path> list(Path dir, String suffix) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (var s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(suffix))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        }
    }
}
