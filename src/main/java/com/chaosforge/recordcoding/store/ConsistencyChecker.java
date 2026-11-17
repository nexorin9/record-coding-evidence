package com.chaosforge.recordcoding.store;

import com.chaosforge.recordcoding.util.Json;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 跨表一致性自检：只列出不一致条目，不自动修改数据。 */
public final class ConsistencyChecker {
    public record Issue(String table, String key, String expected, String actual, String reason) {
        public String describe() {
            return table + " / " + key + " / 期望 " + expected + " / 实际 " + actual + " / " + reason;
        }
    }

    private final Connection c;
    public ConsistencyChecker(Connection c) { this.c = c; }

    public List<Issue> check(String batchId) throws SQLException {
        List<Issue> issues = new ArrayList<>();
        // 1) 快照内容哈希必须一致
        for (Map<String, Object> r : Jdbc.query(c,
                "SELECT content_hash, COUNT(*) AS n FROM evidence_snapshot WHERE batch_id=? GROUP BY content_hash",
                batchId)) {
            String h = String.valueOf(r.get("content_hash"));
            long n = ((Number) r.get("n")).longValue();
            if (n == 0) issues.add(new Issue("evidence_snapshot", batchId, ">0 行", "0 行", "证据快照为空"));
        }
        String declared = Jdbc.scalar(c, "SELECT evidence_hash FROM review_batch WHERE batch_id=?", batchId);
        String stored = Jdbc.scalar(c, "SELECT content_hash FROM evidence_snapshot WHERE batch_id=? LIMIT 1", batchId);
        if (declared != null && stored != null && !declared.equals(stored)) {
            issues.add(new Issue("evidence_snapshot", batchId, declared, stored,
                    "CONSISTENCY_HASH_MISMATCH：批次登记哈希与快照内容哈希不匹配"));
        }
        // 2) 已确认批次必须每条判定都有确认人与时间
        String state = Jdbc.scalar(c, "SELECT state FROM review_batch WHERE batch_id=?", batchId);
        if ("已确认".equals(state) || "已提交".equals(state)) {
            int findings = count("finding", batchId);
            int confirmations = count("confirmation", batchId);
            if (findings > confirmations) {
                issues.add(new Issue("confirmation", batchId, String.valueOf(findings),
                        String.valueOf(confirmations), "存在未确认的判定"));
            }
            for (Map<String, Object> row : Jdbc.query(c,
                    "SELECT finding_id,confirmed_by,confirmed_at FROM confirmation WHERE batch_id=?", batchId)) {
                if (isBlank(row.get("confirmed_by")) || isBlank(row.get("confirmed_at"))) {
                    issues.add(new Issue("confirmation", String.valueOf(row.get("finding_id")),
                            "确认人 + 时间齐备", "缺项", "已确认批次缺确认人或确认时间"));
                }
            }
        }
        // 3) 已提交批次必须有回执
        if ("已提交".equals(state) && count("receipt", batchId) == 0) {
            issues.add(new Issue("receipt", batchId, "存在回执", "0 条", "已提交批次缺提交回执"));
        }
        // 4) 幂等键唯一性（主键保证，交叉校验回执）
        for (Map<String, Object> row : Jdbc.query(c,
                "SELECT idem_key,receipt_id FROM idempotency WHERE batch_id=?", batchId)) {
            if (isBlank(row.get("receipt_id"))) {
                issues.add(new Issue("idempotency", String.valueOf(row.get("idem_key")),
                        "关联回执", "空", "幂等键未关联回执"));
            }
        }
        return issues;
    }

    public String report(String batchId) throws SQLException {
        List<Issue> issues = check(batchId);
        if (issues.isEmpty()) return "批次 " + batchId + " 一致性自检通过，未发现不一致条目";
        StringBuilder sb = new StringBuilder("批次 " + batchId + " 一致性自检发现 " + issues.size() + " 项：\n");
        for (Issue i : issues) sb.append("  - ").append(i.describe()).append('\n');
        return sb.toString();
    }

    private int count(String table, String batchId) throws SQLException {
        String v = Jdbc.scalar(c, "SELECT COUNT(*) FROM " + table + " WHERE batch_id=?", batchId);
        return v == null ? 0 : Integer.parseInt(v);
    }

    private static boolean isBlank(Object o) { return o == null || String.valueOf(o).isBlank(); }
}
