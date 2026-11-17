package com.chaosforge.recordcoding.store;

import com.chaosforge.recordcoding.evidence.EvidenceRow;
import com.chaosforge.recordcoding.evidence.EvidenceSnapshot;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** 证据快照 DAO：逐行落库，含行哈希与快照内容哈希。 */
public final class EvidenceSnapshotDao {
    private final Connection c;
    public EvidenceSnapshotDao(Connection c) { this.c = c; }

    public void insertAll(EvidenceSnapshot snapshot) throws SQLException {
        for (EvidenceRow r : snapshot.rows()) {
            Jdbc.update(c, "INSERT INTO evidence_snapshot(batch_id,visit_no,evidence_id,source_system,field,"
                            + "field_locator,value_text,row_hash,content_hash) VALUES(?,?,?,?,?,?,?,?,?)",
                    snapshot.batchId(), snapshot.visitNo(), r.evidenceId(), r.source().label(),
                    DeidentifiedWrite.clean(r.fieldName()), DeidentifiedWrite.clean(r.fieldLocator()),
                    DeidentifiedWrite.clean(r.displayValue()), r.hash(), snapshot.evidenceHash());
        }
    }

    public List<Map<String, Object>> byBatch(String batchId) throws SQLException {
        return Jdbc.query(c, "SELECT * FROM evidence_snapshot WHERE batch_id=? ORDER BY id", batchId);
    }

    public int count(String batchId) throws SQLException {
        String v = Jdbc.scalar(c, "SELECT COUNT(*) FROM evidence_snapshot WHERE batch_id=?", batchId);
        return v == null ? 0 : Integer.parseInt(v);
    }
}
