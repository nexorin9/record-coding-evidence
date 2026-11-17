package com.chaosforge.recordcoding.evidence;

import com.chaosforge.recordcoding.util.Hashes;

import java.util.ArrayList;
import java.util.List;

/** 证据快照稳定哈希（字段顺序无关），作为对照表证据出处与 QC 回写 evidence_hash 的唯一来源。 */
public final class EvidenceHash {
    private EvidenceHash() {}

    public static String of(List<EvidenceRow> rows) {
        List<String> parts = new ArrayList<>();
        for (EvidenceRow r : rows) {
            parts.add(r.source() + "|" + r.fieldLocator() + "|" + r.displayValue() + "|" + r.hash());
        }
        return Hashes.stableHash(parts);
    }

    public static String rowHash(String source, String locator, String value) {
        return Hashes.sha256Hex(source + "|" + locator + "|" + value);
    }
}
