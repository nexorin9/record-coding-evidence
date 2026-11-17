package com.chaosforge.recordcoding.evidence;

import java.time.Instant;
import java.util.List;

/** 证据快照：一次复核取回的全部证据行 + 稳定哈希。 */
public final class EvidenceSnapshot {
    private final String visitNo;
    private final String batchId;
    private final List<EvidenceRow> rows;
    private final String evidenceHash;
    private final String createdAt;

    public EvidenceSnapshot(String visitNo, String batchId, List<EvidenceRow> rows, String evidenceHash) {
        this.visitNo = visitNo;
        this.batchId = batchId;
        this.rows = List.copyOf(rows);
        this.evidenceHash = evidenceHash;
        this.createdAt = Instant.now().toString();
    }

    public String visitNo() { return visitNo; }
    public String batchId() { return batchId; }
    public List<EvidenceRow> rows() { return rows; }
    public String evidenceHash() { return evidenceHash; }
    public String createdAt() { return createdAt; }
    public int size() { return rows.size(); }
}
