package com.chaosforge.recordcoding.transport;

import com.chaosforge.recordcoding.util.Hashes;
import com.chaosforge.recordcoding.util.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** QC 复核写入：Bearer + Idempotency-Key；重试由底座执行且每次携带同一幂等键。 */
public final class QcReviewClient implements QcReviewSink {
    private final HttpTransport http;
    private final String baseUrl;
    private final String token;

    public QcReviewClient(HttpTransport http, String baseUrl, String token) {
        this.http = http;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
    }

    public String endpoint() { return baseUrl + "/api/v1/encoding-reviews"; }

    public String body(String visitNo, String batchId, List<Map<String, Object>> findings, String evidenceHash) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("visit_no", visitNo);
        b.put("batch_id", batchId);
        b.put("findings", findings);
        b.put("evidence_hash", evidenceHash);
        return Json.stringify(b);
    }

    public Map<String, String> headers(String visitNo, String field, String evidenceHash) {
        Map<String, String> h = HttpTransport.bearerHeaders(token);
        h.put("Content-Type", "application/json");
        h.put("Idempotency-Key", Hashes.idempotencyKey(visitNo, field, evidenceHash));
        return h;
    }

    @Override
    public QcReceipt submit(String visitNo, String batchId, List<Map<String, Object>> findings, String evidenceHash) {
        Map<String, String> h = headers(visitNo, "encoding-review", evidenceHash);
        HttpTransport.Response r = http.postJson(endpoint(), h,
                body(visitNo, batchId, findings, evidenceHash));
        HttpTransport.requireJson(r);
        Object parsed = Json.parse(r.body());
        if (!(parsed instanceof Map<?, ?> m)) {
            throw new TransportException(TransportException.ErrorCode.STRUCTURE_INVALID, "QC 回执不是对象");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> receipt = (Map<String, Object>) m;
        String id = Json.str(receipt, "id");
        String at = Json.str(receipt, "received_at");
        if (id == null) id = Hashes.shortHash(visitNo + batchId, 16);
        if (at == null) at = java.time.Instant.now().toString();
        return new QcReceipt(id, at);
    }
}
