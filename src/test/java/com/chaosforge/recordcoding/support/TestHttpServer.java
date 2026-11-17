package com.chaosforge.recordcoding.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

/**
 * 本地协议替身（仅 src/test）：真实 HTTP 协议 + 本地端口，用于接口兼容性测试。
 * 记录每次请求的方法 / 路径 / 查询串 / 头 / 体，并可按请求次数返回不同状态。
 */
public final class TestHttpServer implements AutoCloseable {
    public record Request(String method, String path, String query, Map<String, String> headers, String body) {}
    public record Reply(int status, String body) {}

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final BiFunction<Request, Integer, Reply> handler;

    public TestHttpServer(BiFunction<Request, Integer, Reply> handler) throws IOException {
        this.handler = handler;
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.server.createContext("/", this::handle);
        this.server.setExecutor(null);
        this.server.start();
    }

    public static TestHttpServer always(Reply reply) throws IOException {
        return new TestHttpServer((req, n) -> reply);
    }

    private void handle(HttpExchange ex) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        ex.getRequestHeaders().forEach((k, v) -> headers.put(k, String.join(",", v)));
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Request req = new Request(ex.getRequestMethod(), ex.getRequestURI().getPath(),
                ex.getRequestURI().getRawQuery() == null ? "" : ex.getRequestURI().getRawQuery(),
                Map.copyOf(headers), body);
        requests.add(req);
        Reply reply = handler.apply(req, requests.size());
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(reply.status(), bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    public String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    public String url(String path) { return baseUrl() + path; }
    public List<Request> requests() { return List.copyOf(requests); }
    public int requestCount() { return requests.size(); }
    public Request lastRequest() { return requests.isEmpty() ? null : requests.get(requests.size() - 1); }

    @Override public void close() { server.stop(0); }
}
