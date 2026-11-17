package com.chaosforge.recordcoding.domain;

import java.util.List;

/** 住院号 ↔ PatientID ↔ StudyInstanceUID 三方绑定结果。 */
public final class VisitBinding {
    public enum Result { RESOLVED, AMBIGUOUS, NOT_FOUND }

    private final String visitNo;
    private final Result result;
    private final String patientId;
    private final List<String> studyUids;
    private final List<String> candidates;
    private final String ambiguityReason;

    public VisitBinding(String visitNo, Result result, String patientId, List<String> studyUids,
                        List<String> candidates, String ambiguityReason) {
        this.visitNo = visitNo;
        this.result = result;
        this.patientId = patientId;
        this.studyUids = List.copyOf(studyUids);
        this.candidates = List.copyOf(candidates);
        this.ambiguityReason = ambiguityReason;
    }

    public String visitNo() { return visitNo; }
    public Result result() { return result; }
    public String patientId() { return patientId; }
    public List<String> studyUids() { return studyUids; }
    public List<String> candidates() { return candidates; }
    public String ambiguityReason() { return ambiguityReason; }
    public boolean canIssueVerdict() { return result == Result.RESOLVED; }
}
