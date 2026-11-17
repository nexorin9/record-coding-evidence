package com.chaosforge.recordcoding.store;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 批量跑批断点续跑：从最后成功批次重放，已落库批次不重复写。 */
public final class BatchResume {
    private final Connection c;
    public BatchResume(Connection c) { this.c = c; }

    public Set<String> completedBatches() throws SQLException {
        Set<String> out = new LinkedHashSet<>();
        for (Map<String, Object> row : Jdbc.query(c,
                "SELECT batch_id FROM review_batch WHERE state IN ('已确认','已提交')")) {
            out.add(String.valueOf(row.get("batch_id")));
        }
        return out;
    }

    /** @return 本次实际写入的批次数（已存在的批次被跳过）。 */
    public int resume(List<String> batchIds, String visitNo) throws SQLException {
        Set<String> done = completedBatches();
        int written = 0;
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            for (String id : batchIds) {
                if (done.contains(id)) continue;
                Jdbc.update(c, "INSERT OR IGNORE INTO review_batch(batch_id,visit_no,state,evidence_hash,created_at) "
                        + "VALUES(?,?,?,?,?)", id, visitNo, "已确认", "", java.time.Instant.now().toString());
                written++;
            }
            c.commit();
            return written;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }
}
