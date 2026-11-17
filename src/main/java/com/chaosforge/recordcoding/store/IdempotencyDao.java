package com.chaosforge.recordcoding.store;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

/** 幂等键查重：同一键二次提交返回既有回执，不新建复核记录。 */
public final class IdempotencyDao {
    private final Connection c;
    public IdempotencyDao(Connection c) { this.c = c; }

    /** @return 既有回执 id；null 表示首次登记 */
    public String existingReceipt(String key) throws SQLException {
        return Jdbc.scalar(c, "SELECT receipt_id FROM idempotency WHERE idem_key=?", key);
    }

    public boolean register(String key, String batchId, String receiptId) throws SQLException {
        if (existingReceipt(key) != null) return false;
        Jdbc.update(c, "INSERT INTO idempotency(idem_key,batch_id,created_at,receipt_id) VALUES(?,?,?,?)",
                key, batchId, Instant.now().toString(), receiptId);
        return true;
    }

    public int count(String batchId) throws SQLException {
        String v = Jdbc.scalar(c, "SELECT COUNT(*) FROM idempotency WHERE batch_id=?", batchId);
        return v == null ? 0 : Integer.parseInt(v);
    }
}
