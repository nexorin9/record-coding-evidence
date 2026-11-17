package com.chaosforge.recordcoding.store;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** 人工确认驳回：confirmed_by / confirmed_at 齐备，驳回需理由；事务边界由调用方开启。 */
public final class ConfirmationDao {
    private final Connection c;
    public ConfirmationDao(Connection c) { this.c = c; }

    public void confirm(String batchId, String findingId, String confirmedBy, String confirmedAt,
                        String decision, String rejectReason) throws SQLException {
        if (confirmedBy == null || confirmedBy.isBlank() || confirmedAt == null || confirmedAt.isBlank()) {
            throw new ConfirmationException("CONFIRM_FIELDS_MISSING", "确认人与确认时间必填");
        }
        if ("已驳回".equals(decision) && (rejectReason == null || rejectReason.isBlank())) {
            throw new ConfirmationException("REJECT_REASON_REQUIRED", "驳回必须填写理由");
        }
        Jdbc.update(c, "INSERT OR REPLACE INTO confirmation(finding_id,batch_id,confirmed_by,confirmed_at,"
                        + "decision,reject_reason) VALUES(?,?,?,?,?,?)",
                findingId, batchId, DeidentifiedWrite.clean(confirmedBy), confirmedAt, decision,
                rejectReason == null ? "" : DeidentifiedWrite.clean(rejectReason));
    }

    public List<Map<String, Object>> byBatch(String batchId) throws SQLException {
        return Jdbc.query(c, "SELECT * FROM confirmation WHERE batch_id=? ORDER BY finding_id", batchId);
    }

    public int count(String batchId) throws SQLException {
        String v = Jdbc.scalar(c, "SELECT COUNT(*) FROM confirmation WHERE batch_id=?", batchId);
        return v == null ? 0 : Integer.parseInt(v);
    }
}
