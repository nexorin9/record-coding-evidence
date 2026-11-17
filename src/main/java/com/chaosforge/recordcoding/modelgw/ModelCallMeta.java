package com.chaosforge.recordcoding.modelgw;

import com.chaosforge.recordcoding.util.Deidentifier;
import com.chaosforge.recordcoding.util.Hashes;

import java.util.LinkedHashMap;
import java.util.Map;

/** 调用审计元数据七项；不记录完整病历文本与证据原文。 */
public record ModelCallMeta(String modelName, String endpointHost, long elapsedMillis,
                            String requestFingerprint, Long tokenUsage, boolean degraded,
                            String ruleVersion) {
    public static ModelCallMeta of(String model, String url, long elapsed, String prompt,
                                   String schema, String evidenceHash, Long tokens, boolean degraded) {
        String host = "";
        try { host = java.net.URI.create(url).getHost(); } catch (RuntimeException ignored) { /* 保留空 */ }
        String fp = Hashes.shortHash(prompt + schema + evidenceHash, 16);
        return new ModelCallMeta(model, host == null ? "" : host, elapsed, fp, tokens, degraded,
                StructuredSchema.VERSION);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("model", modelName);
        m.put("endpoint_host", endpointHost);
        m.put("elapsed_ms", elapsedMillis);
        m.put("request_fingerprint", requestFingerprint);
        m.put("token_usage", tokenUsage == null ? "" : tokenUsage);
        m.put("degraded", degraded);
        m.put("rule_version", ruleVersion);
        return Deidentifier.scrubMap(m);
    }

    public boolean containsPatientText() { return Deidentifier.containsPii(toString()); }
}
