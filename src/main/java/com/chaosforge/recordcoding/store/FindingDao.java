package com.chaosforge.recordcoding.store;

import com.chaosforge.recordcoding.semantic.SemanticFinding;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** 判定 DAO。 */
public final class FindingDao {
    private final Connection c;
    public FindingDao(Connection c) { this.c = c; }

    public void insert(String batchId, SemanticFinding f) throws SQLException {
        Jdbc.update(c, "INSERT OR REPLACE INTO finding(finding_id,batch_id,home_code,verdict,attribution,"
                        + "confidence,evidence_refs) VALUES(?,?,?,?,?,?,?)",
                f.findingId(), batchId, f.homeCode(), f.verdict().wire(), f.attribution().wire(),
                f.confidence(), String.join("|", f.evidenceRefs()));
    }

    public List<Map<String, Object>> byBatch(String batchId) throws SQLException {
        return Jdbc.query(c, "SELECT * FROM finding WHERE batch_id=? ORDER BY finding_id", batchId);
    }
}
