package com.chaosforge.recordcoding.semantic;

import java.util.List;

/** 一条完整建议：首页编码 / 建议编码 / 判定 / 归因 / 置信度 / 证据引用 / 确认状态。 */
public final class SemanticFinding {
    public enum ConfirmState { PENDING, ACCEPTED, REJECTED, PROMPT_ONLY }

    private final String findingId;
    private final String homeCode;
    private final List<String> suggestedCodes;
    private final Verdict verdict;
    private final Attribution attribution;
    private final double confidence;
    private final List<String> evidenceRefs;
    private final ConfirmState state;
    private final String exemptionReason;
    private String confirmedBy = "";
    private String confirmedAt = "";
    private String rejectReason = "";

    public SemanticFinding(String findingId, String homeCode, List<String> suggestedCodes,
                           Verdict verdict, Attribution attribution, double confidence,
                           List<String> evidenceRefs, ConfirmState state, String exemptionReason) {
        this.findingId = findingId;
        this.homeCode = homeCode;
        this.suggestedCodes = List.copyOf(suggestedCodes);
        this.verdict = verdict;
        this.attribution = attribution;
        this.confidence = confidence;
        this.evidenceRefs = List.copyOf(evidenceRefs);
        this.state = state;
        this.exemptionReason = exemptionReason == null ? "" : exemptionReason;
    }

    public static SemanticFinding prompt(String findingId, String homeCode, String note) {
        return new SemanticFinding(findingId, homeCode, List.of(), Verdict.EVIDENCE_MISSING,
                Attribution.NONE, 0.0, List.of(), ConfirmState.PROMPT_ONLY, note);
    }

    public String findingId() { return findingId; }
    public String homeCode() { return homeCode; }
    public List<String> suggestedCodes() { return suggestedCodes; }
    public Verdict verdict() { return verdict; }
    public Attribution attribution() { return attribution; }
    public double confidence() { return confidence; }
    public List<String> evidenceRefs() { return evidenceRefs; }
    public ConfirmState state() { return state; }
    public String exemptionReason() { return exemptionReason; }
    public String confirmedBy() { return confirmedBy; }
    public String confirmedAt() { return confirmedAt; }
    public String rejectReason() { return rejectReason; }

    public boolean hasEvidenceRefs() { return !evidenceRefs.isEmpty(); }
    public boolean isConfirmable() { return state == ConfirmState.PENDING && hasEvidenceRefs(); }
    public boolean isPromptOnly() { return state == ConfirmState.PROMPT_ONLY; }

    public void confirm(String by, String at) { confirmedBy = by; confirmedAt = at; }
    public void reject(String reason) { rejectReason = reason; }
}
