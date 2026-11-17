package com.chaosforge.recordcoding.store;

import com.chaosforge.recordcoding.transport.QcReceipt;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 提交回执落库（对端记录 id / 接收时间 / 批次 / 证据哈希）。 */
public final class ReceiptDao {
    private final Connection c;
    public ReceiptDao(Connection c) { this.c = c; }

    public String insert(String batchId, QcReceipt r, String evidenceHash) throws SQLException {
        String id = "R" + com.chaosforge.recordcoding.util.Hashes.shortHash(batchId + r.recordId(), 12);
        Jdbc.update(c, "INSERT OR REPLACE INTO receipt(receipt_id,batch_id,record_id,received_at,evidence_hash) "
                + "VALUES(?,?,?,?,?)", id, batchId, r.recordId(),
                r.receivedAt() == null ? Instant.now().toString() : r.receivedAt(), evidenceHash);
        return id;
    }

    public List<Map<String, Object>> byBatch(String batchId) throws SQLException {
        return Jdbc.query(c, "SELECT * FROM receipt WHERE batch_id=?", batchId);
    }

    public int count(String batchId) throws SQLException {
        String v = Jdbc.scalar(c, "SELECT COUNT(*) FROM receipt WHERE batch_id=?", batchId);
        return v == null ? 0 : Integer.parseInt(v);
    }
}
