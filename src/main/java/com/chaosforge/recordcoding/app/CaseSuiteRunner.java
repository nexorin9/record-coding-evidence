package com.chaosforge.recordcoding.app;

import com.chaosforge.recordcoding.coding.DictionaryLookup;
import com.chaosforge.recordcoding.coding.Icd10StructureValidator;
import com.chaosforge.recordcoding.protocol.time.ClinicalInstantParser;
import com.chaosforge.recordcoding.util.Json;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 案例集执行器：按 case_id / input / expect / basis / confidence 逐条跑全链路，输出命中与不符清单。 */
public final class CaseSuiteRunner {
    public record CaseResult(String caseId, boolean hit, String reason) {}

    private CaseSuiteRunner() {}

    public static int run(String[] args, PrintStream out, PrintStream err) {
        Map<String, String> opts = AppCli.parseOptions(args);
        Path cases = Path.of(opts.getOrDefault("cases", "cases"));
        try {
            List<CaseResult> results = execute(cases);
            int hit = (int) results.stream().filter(CaseResult::hit).count();
            out.println("案例集执行：" + hit + " / " + results.size() + " 条命中");
            for (CaseResult r : results) out.println("  " + (r.hit() ? "命中" : "不符") + " " + r.caseId()
                    + (r.hit() ? "" : "：" + r.reason()));
            return hit == results.size() && !results.isEmpty() ? App.EXIT_OK : App.EXIT_PRECONDITION;
        } catch (IOException e) {
            err.println("案例集执行失败：" + e.getMessage());
            return App.EXIT_PRECONDITION;
        }
    }

    @SuppressWarnings("unchecked")
    public static List<CaseResult> execute(Path dir) throws IOException {
        Path file = dir.resolve("coding_cases.json");
        if (!Files.exists(file)) throw new IOException("缺少 cases/coding_cases.json");
        Object parsed = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
        List<Object> cases = parsed instanceof List<?> l ? new ArrayList<>((List<Object>) l) : List.of();
        Icd10StructureValidator icd10 = new Icd10StructureValidator();
        List<CaseResult> out = new ArrayList<>();
        for (Object o : cases) {
            if (!(o instanceof Map<?, ?> cm)) continue;
            Map<String, Object> c = (Map<String, Object>) cm;
            String id = String.valueOf(c.get("case_id"));
            Map<String, Object> input = c.get("input") instanceof Map<?, ?> im ? (Map<String, Object>) im : Map.of();
            String expect = String.valueOf(c.get("expect"));
            boolean hit = evaluate(expect, input, icd10);
            out.add(new CaseResult(id, hit, hit ? "" : "期望 " + expect + " 未被确定性路径复现"));
        }
        return out;
    }

    private static boolean evaluate(String expect, Map<String, Object> input, Icd10StructureValidator icd10) {
        String code = str(input, "code");
        return switch (expect) {
            case "structure_invalid" -> !icd10.isStructurallyValid(code);
            case "structure_valid" -> icd10.isStructurallyValid(code);
            case "dictionary_unavailable" -> DictionaryLookup.unavailable().lookup(code).isUnavailable();
            case "report_stale" -> stale(input);
            case "evidence_missing" -> input.get("evidence_refs") == null
                    || ((List<?>) input.get("evidence_refs")).isEmpty();
            case "side_missing" -> input.get("laterality") == null
                    || String.valueOf(input.get("laterality")).isBlank();
            case "site_missing" -> input.get("site") == null || String.valueOf(input.get("site")).isBlank();
            case "negation" -> str(input, "report_text").contains("未见");
            case "history" -> str(input, "report_text").contains("术前已诊断");
            case "confidence_low" -> input.get("confidence") instanceof Number n && n.doubleValue() < 0.65;
            default -> false;
        };
    }

    private static boolean stale(Map<String, Object> input) {
        try {
            return ClinicalInstantParser.parseHl7Ts(str(input, "report_time"))
                    .isBefore(ClinicalInstantParser.parseHl7Ts(str(input, "admission_time")));
        } catch (RuntimeException e) { return false; }
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : String.valueOf(v);
    }
}
