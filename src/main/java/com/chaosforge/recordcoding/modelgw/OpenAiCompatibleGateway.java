package com.chaosforge.recordcoding.modelgw;

import com.chaosforge.recordcoding.transport.HttpTransport;
import com.chaosforge.recordcoding.transport.TransportException;
import com.chaosforge.recordcoding.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** OpenAI-compatible /chat/completions 客户端：请求带 JSON Schema，响应按同一 schema 校验。 */
public final class OpenAiCompatibleGateway implements ModelGateway {
    private final HttpTransport http;
    private final GatewayConfig config;
    private double temperature = 0.0;
    private ModelCallMeta lastMeta;

    public OpenAiCompatibleGateway(HttpTransport http, GatewayConfig config) {
        this.http = http;
        this.config = config;
    }

    public void setTemperature(double t) { this.temperature = t; }

    public String endpoint() { return trim(config.baseUrl()) + "/chat/completions"; }

    public String requestBody(Map<String, Object> payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("temperature", temperature);
        body.put("messages", List.of(
                Map.of("role", "system", "content", "你是病案编码复核助手，只输出结构化 JSON，不给出诊断或治疗建议。"),
                Map.of("role", "user", "content", Json.stringify(payload))));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("name", "coding_finding");
        schema.put("strict", true);
        schema.put("schema", Json.parse(StructuredSchema.jsonSchema()));
        body.put("response_format", Map.of("type", "json_schema", "json_schema", schema));
        return Json.stringify(body);
    }

    public Map<String, String> headers() { return HttpTransport.bearerHeaders(config.apiKey()); }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> chat(Map<String, Object> payload, String schemaName) {
        long start = System.currentTimeMillis();
        String body = requestBody(payload);
        try {
            HttpTransport.Response r = http.postJson(endpoint(), headers(), body);
            HttpTransport.requireJson(r);
            Object parsed = Json.parse(r.body());
            if (!(parsed instanceof Map<?, ?> root)) throw structure("响应不是对象");
            Object choices = root.get("choices");
            if (!(choices instanceof List<?> list) || list.isEmpty()) throw structure("响应缺 choices");
            Object first = list.get(0);
            if (!(first instanceof Map<?, ?> choice)) throw structure("choices[0] 不是对象");
            Object message = choice.get("message");
            if (!(message instanceof Map<?, ?> msg)) throw structure("choices[0].message 缺失");
            Object content = msg.get("content");
            if (!(content instanceof String s)) throw structure("message.content 不是字符串");
            Object finding = Json.parse(s);
            if (!(finding instanceof Map<?, ?> fm)) throw structure("结构化输出不是对象");
            Map<String, Object> result = (Map<String, Object>) fm;
            StructuredSchema.SchemaResult check = "fact".equals(schemaName)
                    ? validateFact(result) : StructuredSchema.validate(result);
            if (!check.valid()) throw structure("结构化输出不合格：" + check.describe());
            Object usage = root.get("usage");
            Long tokens = null;
            if (usage instanceof Map<?, ?> u && u.get("total_tokens") instanceof Number n) tokens = n.longValue();
            lastMeta = ModelCallMeta.of(config.model(), endpoint(), System.currentTimeMillis() - start,
                    Json.stringify(payload), StructuredSchema.VERSION, "", tokens, false);
            return result;
        } catch (Json.JsonException e) {
            throw structure("响应 JSON 解析失败：" + e.getMessage());
        } catch (TransportException e) {
            lastMeta = ModelCallMeta.of(config.model(), endpoint(), System.currentTimeMillis() - start,
                    Json.stringify(payload), StructuredSchema.VERSION, "", null, true);
            throw GatewayException.from(e);
        }
    }

    /** 要素抽取契约：至少含 evidence_fact 六要素，缺失标缺失而非空串。 */
    private StructuredSchema.SchemaResult validateFact(Map<String, Object> result) {
        Object fact = result.get("evidence_fact");
        if (!(fact instanceof Map<?, ?> m)) {
            return new StructuredSchema.SchemaResult(false, List.of("evidence_fact 缺失或类型不符"));
        }
        List<String> problems = new ArrayList<>();
        for (String f : StructuredSchema.FACT_FIELDS) if (!m.containsKey(f)) problems.add("evidence_fact 缺 " + f);
        return new StructuredSchema.SchemaResult(problems.isEmpty(), List.copyOf(problems));
    }

    private GatewayException structure(String reason) {
        return new GatewayException(GatewayException.ErrorCode.STRUCTURE_INVALID, reason);
    }

    public ModelCallMeta lastMeta() { return lastMeta; }

    private static String trim(String url) { return url.endsWith("/") ? url.substring(0, url.length() - 1) : url; }
}
