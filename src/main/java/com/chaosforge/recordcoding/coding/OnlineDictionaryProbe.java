package com.chaosforge.recordcoding.coding;

import com.chaosforge.recordcoding.config.AppConfig;
import com.chaosforge.recordcoding.transport.HttpTransport;
import com.chaosforge.recordcoding.transport.TransportException;
import com.chaosforge.recordcoding.util.Json;
import com.chaosforge.recordcoding.util.StructuredLog;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 官方在线编码字典连通性验证：真实协议 + 真实凭据位（端点与令牌一律从环境变量读）。
 * 未配置返回 not_configured；不可达按 dictionary_unavailable 降级，不静默失败，不把本地替身当官方接口。
 */
public final class OnlineDictionaryProbe {
    public record ProbeResult(String status, String host, long elapsedMillis, String reason,
                              boolean exists, String name) {
        public boolean reachable() { return "reachable".equals(status); }
    }

    private final HttpTransport http;
    private final String url;
    private final String token;
    private final Path logFile;

    public OnlineDictionaryProbe(HttpTransport http, String url, String token, Path logFile) {
        this.http = http;
        this.url = url;
        this.token = token;
        this.logFile = logFile;
    }

    public static OnlineDictionaryProbe fromEnv(Path logFile) {
        AppConfig cfg = AppConfig.fromEnv();
        return new OnlineDictionaryProbe(new HttpTransport(cfg.httpTimeoutSeconds(), 0, 0),
                cfg.dictionaryUrl(), cfg.dictionaryToken(), logFile);
    }

    public boolean configured() { return url != null && !url.isBlank() && token != null && !token.isBlank(); }

    public ProbeResult probe(String code) {
        if (!configured()) {
            return record(new ProbeResult("not_configured", "", 0,
                    "未配置 NHSA_DICT_URL / NHSA_DICT_TOKEN，未实连官方接口", false, ""));
        }
        String host = "";
        try { host = URI.create(url).getHost(); } catch (IllegalArgumentException ignored) { /* 保留空 host */ }
        long start = System.currentTimeMillis();
        try {
            Map<String, String> h = HttpTransport.bearerHeaders(token);
            HttpTransport.Response r = http.getJson(url + "?code=" + code, h);
            HttpTransport.requireJson(r);
            Object parsed = Json.parse(r.body());
            boolean exists = false;
            String name = "";
            if (parsed instanceof Map<?, ?> m) {
                exists = Boolean.TRUE.equals(m.get("exists")) || m.get("code") != null;
                Object n = m.get("name");
                name = n == null ? "" : String.valueOf(n);
            }
            return record(new ProbeResult("reachable", host, System.currentTimeMillis() - start,
                    "官方字典接口连通", exists, name));
        } catch (TransportException e) {
            return record(new ProbeResult("unreachable", host, System.currentTimeMillis() - start,
                    e.code() + "：" + e.readableReason(), false, ""));
        }
    }

    private ProbeResult record(ProbeResult r) {
        if (logFile != null) {
            Map<String, String> ctx = new LinkedHashMap<>();
            ctx.put("host", r.host() == null ? "" : r.host());
            ctx.put("status", r.status());
            ctx.put("elapsed_ms", String.valueOf(r.elapsedMillis()));
            ctx.put("at", Instant.now().toString());
            ctx.put("reason", r.reason());
            try { StructuredLog.append(logFile, "INFO", "DICTIONARY_PROBE", ctx); }
            catch (java.io.IOException ignored) { /* 日志失败不影响探测结论 */ }
        }
        return r;
    }
}
