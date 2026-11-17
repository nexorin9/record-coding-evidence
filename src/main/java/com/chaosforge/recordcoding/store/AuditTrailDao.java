package com.chaosforge.recordcoding.store;

import com.chaosforge.recordcoding.util.Deidentifier;
import com.chaosforge.recordcoding.util.Json;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 审计链：只追加、不可变、去标识；导出动作本身也生成一条事件，可按批次导出 JSON / CSV。 */
public final class AuditTrailDao {
    public enum EventType {
        BATCH_CREATED("批次创建"), EVIDENCE_FETCHED("证据取回"), FINDING_GENERATED("判定生成"),
        HUMAN_CONFIRMED("人工确认"), SUBMITTED("提交"), EXPORTED("导出"), DEGRADED("降级"),
        MODEL_CALLED("模型调用");

        private final String label;
        EventType(String label) { this.label = label; }
        public String label() { return label; }
    }

    private final Connection c;
    public AuditTrailDao(Connection c) { this.c = c; }

    public void append(String batchId, EventType type, String actor, Map<String, Object> payload) throws SQLException {
        Jdbc.update(c, "INSERT INTO audit_event(batch_id,event_type,event_time,actor,payload_json) VALUES(?,?,?,?,?)",
                batchId, type.name(), Instant.now().toString(),
                Deidentifier.scrub(actor == null ? "" : actor), DeidentifiedWrite.payload(payload));
    }

    public List<Map<String, Object>> byBatch(String batchId) throws SQLException {
        return Jdbc.query(c, "SELECT * FROM audit_event WHERE batch_id=? ORDER BY id", batchId);
    }

    public int count(String batchId) throws SQLException {
        String v = Jdbc.scalar(c, "SELECT COUNT(*) FROM audit_event WHERE batch_id=?", batchId);
        return v == null ? 0 : Integer.parseInt(v);
    }

    /** append-only：不提供更新与删除接口，显式调用即被拒绝。 */
    public void updateOrDelete() {
        throw new UnsupportedOperationException("审计链只追加，禁止更新与删除（append-only）");
    }

    public String exportJson(String batchId, String actor, String scope) throws SQLException {
        List<Map<String, Object>> events = byBatch(batchId);
        append(batchId, EventType.EXPORTED, actor, Map.of("format", "json", "scope", scope,
                "event_count", events.size()));
        return Json.stringify(Map.of("batch_id", batchId, "events", events));
    }

    public String exportCsv(String batchId, String actor, String scope) throws SQLException {
        List<Map<String, Object>> events = byBatch(batchId);
        append(batchId, EventType.EXPORTED, actor, Map.of("format", "csv", "scope", scope,
                "event_count", events.size()));
        StringBuilder sb = new StringBuilder("id,batch_id,event_type,event_time,actor,payload_json\n");
        for (Map<String, Object> e : events) {
            sb.append(e.get("id")).append(',').append(csv(e.get("batch_id"))).append(',')
                    .append(csv(e.get("event_type"))).append(',').append(csv(e.get("event_time")))
                    .append(',').append(csv(e.get("actor"))).append(',').append(csv(e.get("payload_json")))
                    .append('\n');
        }
        return sb.toString();
    }

    public Path writeExport(String batchId, String actor, String scope, Path out, boolean json)
            throws SQLException, java.io.IOException {
        String content = json ? exportJson(batchId, actor, scope) : exportCsv(batchId, actor, scope);
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, content, StandardCharsets.UTF_8);
        return out;
    }

    private static String csv(Object o) {
        String s = o == null ? "" : String.valueOf(o);
        return s.contains(",") || s.contains("\"") || s.contains("\n")
                ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
