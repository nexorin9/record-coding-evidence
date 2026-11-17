package com.chaosforge.recordcoding.service;

import com.chaosforge.recordcoding.coding.CodingValidation;
import com.chaosforge.recordcoding.coding.DictionaryLookup;
import com.chaosforge.recordcoding.evidence.EvidenceAssembler;
import com.chaosforge.recordcoding.evidence.EvidenceSnapshot;
import com.chaosforge.recordcoding.semantic.SemanticFinding;
import com.chaosforge.recordcoding.semantic.SemanticFindingAssembler;
import com.chaosforge.recordcoding.util.Hashes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** 主路径编排：取回 → 装配 → 判定 → 等待人工确认 → 提交；阶段可观测且失败不推进状态。 */
public final class ReviewService {
    public record Stage(String name, boolean ok, String detail) {}
    public record ReviewRun(String visitNo, String batchId, List<Stage> stages,
                            EvidenceSnapshot snapshot, List<SemanticFinding> findings,
                            String state, String reason) {
        public boolean reachedConfirmation() {
            return stages.stream().anyMatch(s -> "等待确认".equals(s.name()) && s.ok());
        }
    }

    private final EvidenceCollector collector;
    private final EvidenceAssembler assembler = new EvidenceAssembler();
    private final CodingValidation validation = new CodingValidation(DictionaryLookup.unavailable());

    public ReviewService(EvidenceCollector collector) { this.collector = collector; }

    public ReviewRun review(String visitNo) {
        String batchId = "B" + Hashes.shortHash(visitNo + System.currentTimeMillis(), 10);
        List<Stage> stages = new ArrayList<>();
        EvidenceCollector.CollectionResult c = collector.collect(visitNo);
        stages.add(new Stage("取回证据", true,
                "QIDO " + c.qidoStudies().size() + " 条 / 快照 " + c.snapshotReports().size() + " 条"
                        + (c.hasUnreachable() ? "（含不可达来源，已标缺口）" : "")));
        EvidenceSnapshot snapshot = assembler.assemble(visitNo, batchId, c.qidoStudies(), c.snapshotReports());
        stages.add(new Stage("装配对照表", true, "证据行 " + snapshot.size() + " 行，哈希 "
                + snapshot.evidenceHash().substring(0, 8)));
        if (c.record() != null) {
            CodingValidation.Result r = validation.validate(c.record());
            stages.add(new Stage("三层校验", true, "结论 " + r.findings().size() + " 条，执行层 "
                    + String.join("/", r.layersExecuted()) + (r.missingLayers().isEmpty()
                    ? "" : "，缺层 " + String.join("/", r.missingLayers()))));
        } else {
            stages.add(new Stage("三层校验", false, "EMR 不可达，未做编码三层校验（不推进状态）"));
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        snapshot.rows().forEach(row -> ids.add(row.evidenceId()));
        SemanticFindingAssembler.AssemblyResult a = new SemanticFindingAssembler(
                com.chaosforge.recordcoding.semantic.ExemptionWhitelist.defaults())
                .assemble(c.record() == null ? "" : c.record().coding().mainCodeText(),
                        List.of(), List.of(), ids);
        List<SemanticFinding> findings = new ArrayList<>(a.confirmable());
        findings.addAll(a.prompts());
        stages.add(new Stage("语义判定", true, "可确认 " + a.confirmable().size() + " 条，提示 "
                + a.prompts().size() + " 条" + (a.errorCodes().isEmpty() ? "" : "，装配错误 " + a.errorCodes())));
        stages.add(new Stage("等待确认", true, "模型只出草稿，确认只能由人工采纳 / 驳回触发"));
        return new ReviewRun(visitNo, batchId, List.copyOf(stages), snapshot, List.copyOf(findings),
                "待确认", "等待人工逐条确认，未发出任何 QC 写请求");
    }
}
