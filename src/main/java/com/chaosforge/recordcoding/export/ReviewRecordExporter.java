package com.chaosforge.recordcoding.export;

import com.chaosforge.recordcoding.evidence.EvidenceRow;
import com.chaosforge.recordcoding.semantic.SemanticFinding;
import com.chaosforge.recordcoding.store.AuditTrailDao;
import com.chaosforge.recordcoding.util.Deidentifier;
import com.chaosforge.recordcoding.util.Json;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 复核记录导出 CSV / JSON：含证据出处与确认人时间，导出动作本身生成一条审计事件。 */
public final class ReviewRecordExporter {
    public record ExportRequest(String visitNo, String batchId, List<SemanticFinding> findings,
                                Map<String, EvidenceRow> evidenceById, String actor, String scope) {}

    private final AuditTrailDao audit;

    public ReviewRecordExporter(AuditTrailDao audit) { this.audit = audit; }

    public static String csvHeader() {
        return "visit_no,home_code,suggested_codes,verdict,attribution,evidence_refs,confirmed_by,confirmed_at";
    }

    public List<Map<String, Object>> rows(ExportRequest r) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SemanticFinding f : r.findings()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("visit_no", r.visitNo());
            m.put("home_code", Deidentifier.scrub(f.homeCode()));
            m.put("suggested_codes", String.join("|", f.suggestedCodes()));
            m.put("verdict", f.verdict().wire());
            m.put("attribution", f.attribution().wire());
            List<String> refs = new ArrayList<>();
            for (String id : f.evidenceRefs()) {
                EvidenceRow e = r.evidenceById().get(id);
                refs.add(e == null ? id + "[悬空引用]" : e.source().label() + ":" + e.fieldLocator()
                        + ":" + e.hash().substring(0, 8));
            }
            m.put("evidence_refs", String.join("|", refs));
            m.put("confirmed_by", Deidentifier.scrub(f.confirmedBy()));
            m.put("confirmed_at", f.confirmedAt());
            out.add(m);
        }
        return out;
    }

    public String csv(ExportRequest r) {
        StringBuilder sb = new StringBuilder(csvHeader()).append('\n');
        for (Map<String, Object> row : rows(r)) {
            sb.append(esc(row.get("visit_no"))).append(',').append(esc(row.get("home_code"))).append(',')
                    .append(esc(row.get("suggested_codes"))).append(',').append(esc(row.get("verdict")))
                    .append(',').append(esc(row.get("attribution"))).append(',')
                    .append(esc(row.get("evidence_refs"))).append(',').append(esc(row.get("confirmed_by")))
                    .append(',').append(esc(row.get("confirmed_at"))).append('\n');
        }
        return sb.toString();
    }

    public String json(ExportRequest r) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("visit_no", r.visitNo());
        root.put("batch_id", r.batchId());
        root.put("rows", rows(r));
        return Json.stringify(Deidentifier.scrubMap(root));
    }

    public Path exportCsv(ExportRequest r, Path out) throws java.io.IOException, SQLException {
        writeAudit(r, "csv");
        return write(out, csv(r));
    }

    public Path exportJson(ExportRequest r, Path out) throws java.io.IOException, SQLException {
        writeAudit(r, "json");
        return write(out, json(r));
    }

    private void writeAudit(ExportRequest r, String format) throws SQLException {
        if (audit == null) return;
        audit.append(r.batchId(), AuditTrailDao.EventType.EXPORTED, r.actor(),
                Map.of("format", format, "scope", r.scope(), "visit_no", r.visitNo(),
                        "row_count", r.findings().size()));
    }

    private Path write(Path out, String content) throws java.io.IOException {
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, content, StandardCharsets.UTF_8);
        return out;
    }

    private static String esc(Object o) {
        String s = o == null ? "" : String.valueOf(o);
        return s.contains(",") || s.contains("\"") || s.contains("\n")
                ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
