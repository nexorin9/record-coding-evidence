package com.chaosforge.recordcoding.transport;

import com.chaosforge.recordcoding.domain.MedicalRecord;
import com.chaosforge.recordcoding.util.Json;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** EMR 病历读取：真实 GET + Bearer；缺字段由域模型显式标缺失，不填默认值。 */
public final class EmrClient implements EmrProvider {
    private final HttpTransport http;
    private final String baseUrl;
    private final String token;

    public EmrClient(HttpTransport http, String baseUrl, String token) {
        this.http = http;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
    }

    public String buildUrl(String visitNo) {
        return baseUrl + "/api/v1/medical-records/" + URLEncoder.encode(visitNo, StandardCharsets.UTF_8);
    }

    public Map<String, String> headers() { return HttpTransport.bearerHeaders(token); }

    @Override
    public MedicalRecord fetch(String visitNo) {
        HttpTransport.Response r = http.getJson(buildUrl(visitNo), headers());
        HttpTransport.requireJson(r);
        Object parsed = Json.parse(r.body());
        if (!(parsed instanceof Map<?, ?> m)) {
            throw new TransportException(TransportException.ErrorCode.STRUCTURE_INVALID, "EMR 响应不是对象");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> record = (Map<String, Object>) m;
        return MedicalRecord.fromEmr(record);
    }
}
