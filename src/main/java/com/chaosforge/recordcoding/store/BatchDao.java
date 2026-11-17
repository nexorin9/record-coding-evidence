package com.chaosforge.recordcoding.store;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 复核批次 DAO：基础写入与按批次 / 住院号查询，不含业务规则。 */
public final class BatchDao {
    private final Connection c;
    public BatchDao(Connection c) { this.c = c; }

    public void insert(String batchId, String visitNo, String state, String evidenceHash) throws SQLException {
        Jdbc.update(c, "INSERT OR IGNORE INTO review_batch(batch_id,visit_no,state,evidence_hash,created_at) "
                + "VALUES(?,?,?,?,?)", batchId, visitNo, state, evidenceHash, Instant.now().toString());
    }

    public void updateState(String batchId, String state) throws SQLException {
        Jdbc.update(c, "UPDATE review_batch SET state=? WHERE batch_id=?", state, batchId);
    }

    public String stateOf(String batchId) throws SQLException {
        return Jdbc.scalar(c, "SELECT state FROM review_batch WHERE batch_id=?", batchId);
    }

    public List<Map<String, Object>> byVisit(String visitNo) throws SQLException {
        return Jdbc.query(c, "SELECT * FROM review_batch WHERE visit_no=? ORDER BY batch_id", visitNo);
    }
}
