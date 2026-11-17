package com.chaosforge.recordcoding.evidence;

import com.chaosforge.recordcoding.util.Hashes;

import java.util.LinkedHashMap;
import java.util.Map;

/** 统一证据行：evidence_id / 来源系统 / 字段 / 来源定位 / 取值 / 哈希 / missing 语义。 */
public final class EvidenceRow {
    private final String evidenceId;
    private final EvidenceType type;
    private final EvidenceSource source;
    private final String fieldName;
    private final String fieldLocator;
    private final String value;
    private final boolean missing;
    private final MissingReason missingReason;
    private final String hash;
    private final String accession;

    private EvidenceRow(String evidenceId, EvidenceType type, EvidenceSource source, String fieldName,
                        String fieldLocator, String value, boolean missing, MissingReason missingReason,
                        String hash, String accession) {
        this.evidenceId = evidenceId;
        this.type = type;
        this.source = source;
        this.fieldName = fieldName;
        this.fieldLocator = fieldLocator;
        this.value = value;
        this.missing = missing;
        this.missingReason = missingReason;
        this.hash = hash;
        this.accession = accession;
    }

    public static EvidenceRow present(EvidenceType type, EvidenceSource source, String fieldName,
                                      String fieldLocator, String value, String accession) {
        String id = "E" + Hashes.shortHash(source + "|" + fieldLocator + "|" + value + "|" + accession, 12);
        String h = Hashes.sha256Hex(source + "|" + fieldLocator + "|" + value);
        return new EvidenceRow(id, type, source, fieldName, fieldLocator, value, false, null, h, accession);
    }

    public static EvidenceRow missing(EvidenceType type, EvidenceSource source, String fieldName,
                                      String fieldLocator, MissingReason reason, String accession) {
        String id = "E" + Hashes.shortHash(source + "|" + fieldLocator + "|missing|" + reason + "|" + accession, 12);
        String h = Hashes.sha256Hex(source + "|" + fieldLocator + "|missing|" + reason);
        return new EvidenceRow(id, type, source, fieldName, fieldLocator, "", true, reason, h, accession);
    }

    public String evidenceId() { return evidenceId; }
    public EvidenceType type() { return type; }
    public EvidenceSource source() { return source; }
    public String fieldName() { return fieldName; }
    public String fieldLocator() { return fieldLocator; }
    public String value() { return value; }
    public boolean isMissing() { return missing; }
    public MissingReason missingReason() { return missingReason; }
    public String hash() { return hash; }
    public String accession() { return accession; }

    public String displayValue() { return missing ? "missing(" + missingReason + ")" : value; }

    public String csv(String visitNo, String batchId) {
        return String.join(",", escape(visitNo), escape(batchId), escape(evidenceId),
                escape(source.label()), escape(fieldName), escape(fieldLocator),
                escape(displayValue()), escape(hash.substring(0, 8)));
    }

    public static String csvHeader() {
        return "visit_no,batch_id,evidence_id,source_system,field,field_locator,value,hash8";
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("evidence_id", evidenceId);
        m.put("source_system", source.label());
        m.put("field", fieldName);
        m.put("field_locator", fieldLocator);
        m.put("value", displayValue());
        m.put("hash", hash);
        return m;
    }

    private static String escape(String s) {
        String v = s == null ? "" : s;
        return v.contains(",") || v.contains("\"") || v.contains("\n")
                ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }
}
