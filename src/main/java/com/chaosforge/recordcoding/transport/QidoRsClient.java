package com.chaosforge.recordcoding.transport;

import com.chaosforge.recordcoding.protocol.dicom.DicomJsonModel;
import com.chaosforge.recordcoding.util.Json;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** QIDO-RS 客户端：只带证据 tag 的 includefield，不出现任何取像路径、不下载影像本体。 */
public final class QidoRsClient implements QidoProvider {
    private final HttpTransport http;
    private final String baseUrl;
    private final String token;

    public QidoRsClient(HttpTransport http, String baseUrl, String token) {
        this.http = http;
        this.baseUrl = normalize(baseUrl);
        this.token = token;
    }

    private static String normalize(String url) { return url.endsWith("/") ? url.substring(0, url.length() - 1) : url; }

    /** 只拼证据 tag；测试逐项断言 URL 与请求头。 */
    public String buildUrl(QidoQuery q) {
        StringBuilder sb = new StringBuilder(baseUrl).append("/qido/studies?PatientID=")
                .append(enc(q.patientId()));
        for (String tag : q.tags()) sb.append("&includefield=").append(enc(tag));
        sb.append("&limit=").append(q.limit());
        return sb.toString();
    }

    public Map<String, String> headers() {
        Map<String, String> h = HttpTransport.bearerHeaders(token);
        h.put("Accept", "application/dicom+json");
        return h;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetch(QidoQuery q) {
        HttpTransport.Response r = http.getJson(buildUrl(q), headers());
        HttpTransport.requireJson(r);
        Object parsed = Json.parse(r.body());
        List<Object> items = parsed instanceof List<?> l ? new ArrayList<>((List<Object>) l) : List.of(parsed);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : items) {
            if (!(o instanceof Map<?, ?> raw)) continue;
            Map<String, Object> study = (Map<String, Object>) raw;
            Map<String, Object> projected = new LinkedHashMap<>();
            DicomJsonModel model = new DicomJsonModel(study);
            for (String tag : EvidenceTags.ALL) {
                String v = model.singleValue(tag);
                projected.put(tag, v == null ? "missing" : v);
            }
            out.add(projected);
        }
        return out;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
