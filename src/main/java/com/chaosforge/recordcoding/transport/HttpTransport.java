package com.chaosforge.recordcoding.transport;

import com.chaosforge.recordcoding.util.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 统一 GET/POST JSON 出站底座（JDK HttpClient）；无内置端点与密钥默认值。 */
public final class HttpTransport {
    public record Response(int status, String body, Map<String, List<String>> headers) {}

    private final HttpClient client;
    private final int timeoutSeconds;
    private final int maxRetries;
    private final long backoffMillis;
    private int lastAttempts;
    private final List<Long> lastBackoffSequence = new ArrayList<>();

    public HttpTransport() { this(10, 3, 0L); }

    public HttpTransport(int timeoutSeconds, int maxRetries, long backoffMillis) {
        this.timeoutSeconds = timeoutSeconds;
        this.maxRetries = maxRetries;
        this.backoffMillis = backoffMillis;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                .build();
    }

    public static Map<String, String> bearerHeaders(String token) {
        if (token == null || token.isBlank()) {
            throw new TransportException(TransportException.ErrorCode.TOKEN_MISSING,
                    "Bearer 令牌为空，不回落匿名请求");
        }
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Authorization", "Bearer " + token);
        h.put("Accept", "application/json");
        h.put("Content-Type", "application/json");
        return h;
    }

    public Response getJson(String url, Map<String, String> headers) {
        return execute(request(url, headers, "GET", null));
    }

    public Response postJson(String url, Map<String, String> headers, String body) {
        return execute(request(url, headers, "POST", body));
    }

    private HttpRequest request(String url, Map<String, String> headers, String method, String body) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)));
        if (headers != null) for (Map.Entry<String, String> e : headers.entrySet()) b.header(e.getKey(), e.getValue());
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8));
        return b.build();
    }

    private Response execute(HttpRequest req) {
        lastAttempts = 0;
        lastBackoffSequence.clear();
        TransportException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            lastAttempts++;
            try {
                HttpResponse<String> r = client.send(req, HttpResponse.BodyHandlers.ofString(
                        java.nio.charset.StandardCharsets.UTF_8));
                int status = r.statusCode();
                if (status >= 500) {
                    last = new TransportException(TransportException.ErrorCode.HTTP_5XX,
                            "GET/POST " + req.uri().getHost() + " 返回 " + status, status);
                } else if (status >= 400) {
                    throw new TransportException(TransportException.ErrorCode.HTTP_4XX,
                            "GET/POST " + req.uri().getHost() + " 返回 " + status, status);
                } else {
                    return new Response(status, r.body(), new LinkedHashMap<>(r.headers().map()));
                }
            } catch (TransportException te) {
                if (!te.retryable()) throw te;
                last = te;
            } catch (java.net.http.HttpTimeoutException e) {
                last = new TransportException(TransportException.ErrorCode.TIMEOUT, "请求超时 " + timeoutSeconds + "s");
            } catch (java.net.ConnectException e) {
                throw new TransportException(TransportException.ErrorCode.CONNECT_FAILED, e.getMessage() == null
                        ? "连接失败" : e.getMessage());
            } catch (java.io.IOException e) {
                throw classifyIo(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TransportException(TransportException.ErrorCode.CONNECT_FAILED, "调用被中断");
            }
            if (last != null && last.retryable() && attempt < maxRetries) {
                long delay = backoffMillis * (1L << attempt);
                lastBackoffSequence.add(delay);
                if (delay > 0) {
                    try { Thread.sleep(delay); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new TransportException(TransportException.ErrorCode.CONNECT_FAILED, "退避被中断");
                    }
                }
            } else if (last != null && last.retryable()) {
                throw last;
            } else if (last != null) {
                throw last;
            }
        }
        throw last == null ? new TransportException(TransportException.ErrorCode.CONNECT_FAILED, "未知出站失败") : last;
    }

    /** 对已成功的响应做结构校验：非 JSON 归入 STRUCTURE_INVALID，不重试。 */
    public static void requireJson(Response response) {
        try {
            Json.parse(response.body());
        } catch (Json.JsonException e) {
            throw new TransportException(TransportException.ErrorCode.STRUCTURE_INVALID,
                    "响应不是合法 JSON：" + e.getMessage(), response.status());
        }
    }

    private TransportException classifyIo(java.io.IOException e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (m.contains("timed out") || m.contains("timeout")) {
            return new TransportException(TransportException.ErrorCode.TIMEOUT, m);
        }
        return new TransportException(TransportException.ErrorCode.CONNECT_FAILED, m.isEmpty() ? "连接失败" : m);
    }

    public int lastAttempts() { return lastAttempts; }
    public List<Long> lastBackoffSequence() { return List.copyOf(lastBackoffSequence); }
    public int maxRetries() { return maxRetries; }
    public int timeoutSeconds() { return timeoutSeconds; }
}
