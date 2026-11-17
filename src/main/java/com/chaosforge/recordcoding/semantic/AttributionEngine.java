package com.chaosforge.recordcoding.semantic;

import java.util.List;

/** 归因与判定绑定：每条归因挂在一条判定上并带证据引用，无证据不产出归因。 */
public final class AttributionEngine {
    public record AttributionResult(Attribution attribution, String explanation,
                                    String evidenceRef, boolean produced) {}

    public AttributionResult attribute(Judgement judgement, String homeCode, DiagnosticFact fact) {
        if (!judgement.hasEvidence()) {
            return new AttributionResult(Attribution.EVIDENCE_MISSING, "无证据引用，不产出归因", "", false);
        }
        String ref = judgement.evidenceRefs().get(0);
        if (judgement.verdict() == Verdict.EVIDENCE_MISSING) {
            return new AttributionResult(Attribution.EVIDENCE_MISSING,
                    "无对应报告证据，归因到证据缺失", ref, true);
        }
        if (judgement.verdict() == Verdict.REPORT_STALE) {
            return new AttributionResult(Attribution.STALE_REPORT,
                    "报告早于入院，归因到报告陈旧", ref, true);
        }
        if (homeCode == null || homeCode.isBlank()) {
            return new AttributionResult(Attribution.CODE_ABSENT,
                    "首页无对应编码，归因到编码缺失", ref, true);
        }
        if (fact != null && fact.hasNoLaterality()) {
            return new AttributionResult(Attribution.SIDE_MISSING,
                    "报告事实含侧别而编码未体现，归因到侧别缺失", ref, true);
        }
        if (fact != null && fact.hasNoSite()) {
            return new AttributionResult(Attribution.SITE_MISSING,
                    "报告事实含部位而编码未体现，归因到部位缺失", ref, true);
        }
        if (judgement.verdict() == Verdict.CONFLICT) {
            return new AttributionResult(Attribution.CODE_ABSENT,
                    "语义冲突但具体缺口未定位，保守归因到编码缺失", ref, true);
        }
        return new AttributionResult(Attribution.NONE, "语义等价，无差异归因", ref, true);
    }
}
