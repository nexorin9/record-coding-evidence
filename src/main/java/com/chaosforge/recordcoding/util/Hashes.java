package com.chaosforge.recordcoding.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** 稳定哈希：证据出处、幂等键与请求指纹的唯一来源。 */
public final class Hashes {
    private Hashes() {}

    public static String sha256Hex(String s) {
        return sha256Hex(s.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(bytes)) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("运行环境缺少 SHA-256", e);
        }
    }

    public static String shortHash(String s, int len) {
        String h = sha256Hex(s);
        return h.substring(0, Math.min(len, h.length()));
    }

    /** QC 回写幂等键：sha256(visit_no|field|evidence_hash)。 */
    public static String idempotencyKey(String visitNo, String field, String evidenceHash) {
        return sha256Hex(visitNo + "|" + field + "|" + evidenceHash);
    }

    /** 字段顺序无关的快照哈希：先排序再拼接。 */
    public static String stableHash(Collection<String> parts) {
        List<String> sorted = new ArrayList<>(parts);
        Collections.sort(sorted);
        return sha256Hex(String.join("\u001f", sorted));
    }
}
