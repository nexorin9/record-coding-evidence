package com.chaosforge.recordcoding.semantic;

import com.chaosforge.recordcoding.util.Hashes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 判定与证据出处装配：建议 ↔ 对照表行一一对应，悬空引用视为装配失败。 */
public final class SemanticFindingAssembler {
    public record AssemblyResult(List<SemanticFinding> confirmable, List<SemanticFinding> prompts,
                                 List<String> errorCodes, Map<String, String> rowToFinding) {
        public int differenceCount() { return confirmable.size(); }
    }

    private final ExemptionWhitelist whitelist;

    public SemanticFindingAssembler(ExemptionWhitelist whitelist) { this.whitelist = whitelist; }

    /** knownEvidenceIds 为对照表实际存在的证据行 id；指向不存在的引用即悬空。 */
    public AssemblyResult assemble(String homeCode, List<Judgement> judgements,
                                   List<AttributionEngine.AttributionResult> attributions,
                                   Set<String> knownEvidenceIds) {
        List<SemanticFinding> confirmable = new ArrayList<>();
        List<SemanticFinding> prompts = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, String> rowToFinding = new LinkedHashMap<>();
        for (int i = 0; i < judgements.size(); i++) {
            Judgement j = judgements.get(i);
            String id = "F" + (i + 1);
            if (!j.advise()) {
                prompts.add(SemanticFinding.prompt(id, homeCode, j.note()));
                continue;
            }
            if (!j.hasEvidence()) {
                errors.add("MISSING_EVIDENCE_REFS:" + id);
                continue;
            }
            boolean dangling = j.evidenceRefs().stream().anyMatch(ref -> !knownEvidenceIds.contains(ref));
            if (dangling) {
                errors.add("DANGLING_EVIDENCE_REF:" + id);
                continue;
            }
            AttributionEngine.AttributionResult attr = i < attributions.size()
                    ? attributions.get(i) : new AttributionEngine.AttributionResult(
                    Attribution.NONE, "无归因", "", false);
            ExemptionWhitelist.FilterResult exempt = whitelist.filter(homeCode, j.note());
            List<String> suggested = j.verdict() == Verdict.EQUIVALENT ? List.of() : List.of(homeCode + "-建议复核");
            SemanticFinding f = new SemanticFinding(id, homeCode, suggested, j.verdict(),
                    attr.attribution(), j.confidence(), j.evidenceRefs(),
                    SemanticFinding.ConfirmState.PENDING, exempt.reason());
            confirmable.add(f);
            rowToFinding.put(j.evidenceRefs().get(0), id);
        }
        return new AssemblyResult(List.copyOf(confirmable), List.copyOf(prompts),
                List.copyOf(errors), Map.copyOf(rowToFinding));
    }

    public static String findingId(String homeCode, String evidenceId) {
        return "F" + Hashes.shortHash(homeCode + "|" + evidenceId, 8);
    }
}
