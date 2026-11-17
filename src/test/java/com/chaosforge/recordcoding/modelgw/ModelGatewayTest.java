package com.chaosforge.recordcoding.modelgw;

import com.chaosforge.recordcoding.support.Fixtures;
import com.chaosforge.recordcoding.support.TestHttpServer;
import com.chaosforge.recordcoding.transport.HttpTransport;
import com.chaosforge.recordcoding.transport.TransportException;
import com.chaosforge.recordcoding.util.Json;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** id8 / id34 / id35：结构化输出契约、五类错误分类与可审计调用元数据。 */
class ModelGatewayTest {

    @Test
    void schemaAcceptsCompletePayloadAndRejectsEveryViolation() {
        StructuredSchema.SchemaResult ok = StructuredSchema.validate(Fixtures.schemaPayload());
        assertTrue(ok.valid(), ok.describe());
        assertEquals("结构合格", ok.describe());
        assertEquals(4, StructuredSchema.VERDICTS.size());
        assertEquals(8, StructuredSchema.REQUIRED_FIELDS.size());

        Map<String, Object> missing = new LinkedHashMap<>(Fixtures.schemaPayload());
        missing.remove("evidence_refs");
        assertTrue(StructuredSchema.validate(missing).problems().stream()
                .anyMatch(p -> p.contains("缺字段 evidence_refs")));
        Map<String, Object> badVerdict = new LinkedHashMap<>(Fixtures.schemaPayload());
        badVerdict.put("verdict", "maybe");
        assertTrue(StructuredSchema.validate(badVerdict).problems().stream()
                .anyMatch(p -> p.contains("枚举越界")));
        Map<String, Object> badFact = new LinkedHashMap<>(Fixtures.schemaPayload());
        badFact.put("evidence_fact", "not-an-object");
        assertTrue(StructuredSchema.validate(badFact).problems().stream()
                .anyMatch(p -> p.contains("类型不符")));
        Map<String, Object> badConfidence = new LinkedHashMap<>(Fixtures.schemaPayload());
        badConfidence.put("confidence", "high");
        assertFalse(StructuredSchema.validate(badConfidence).valid());
        assertFalse(StructuredSchema.validate(null).valid());
    }

    @Test
    void jsonSchemaIsMachineReadableAndClosed() {
        Map<String, Object> schema = Json.object(StructuredSchema.jsonSchema());
        assertEquals("object", schema.get("type"));
        assertEquals(Boolean.FALSE, schema.get("additionalProperties"));
        assertEquals(StructuredSchema.REQUIRED_FIELDS, schema.get("required"));
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        Map<String, Object> verdict = (Map<String, Object>) props.get("verdict");
        assertEquals(StructuredSchema.VERDICTS, verdict.get("enum"));
    }

    @Test
    void gatewayExceptionClassifiesFiveErrorCodes() {
        assertEquals(GatewayException.ErrorCode.HTTP_4XX,
                GatewayException.from(new TransportException(TransportException.ErrorCode.TOKEN_MISSING, "x")).code());
        assertEquals(GatewayException.ErrorCode.HTTP_5XX,
                GatewayException.from(new TransportException(TransportException.ErrorCode.HTTP_5XX, "x")).code());
        assertEquals(GatewayException.ErrorCode.TIMEOUT,
                GatewayException.from(new TransportException(TransportException.ErrorCode.TIMEOUT, "x")).code());
        assertEquals(GatewayException.ErrorCode.CONNECT_FAILED,
                GatewayException.from(new TransportException(TransportException.ErrorCode.CONNECT_FAILED, "x")).code());
        assertEquals(GatewayException.ErrorCode.STRUCTURE_INVALID,
                GatewayException.from(new TransportException(TransportException.ErrorCode.STRUCTURE_INVALID, "x")).code());
        assertTrue(new GatewayException(GatewayException.ErrorCode.TIMEOUT, "x").retryable());
        assertFalse(new GatewayException(GatewayException.ErrorCode.HTTP_4XX, "x").retryable());
        assertTrue(new GatewayException(GatewayException.ErrorCode.HTTP_4XX, "x").readableReason().contains("HTTP_4XX"));
    }

    @Test
    void callMetaRecordsFingerprintNotPatientText() {
        ModelCallMeta meta = ModelCallMeta.of("qwen", "https://llm.internal/v1", 12L,
                "完整病历文本 13800138000", StructuredSchema.VERSION, "hash", 42L, false);
        assertEquals("qwen", meta.modelName());
        assertEquals("llm.internal", meta.endpointHost());
        assertEquals(16, meta.requestFingerprint().length());
        assertEquals(StructuredSchema.VERSION, meta.ruleVersion());
        assertEquals(7, meta.toMap().size());
        assertFalse(meta.containsPatientText());
        assertFalse(meta.toString().contains("完整病历文本"));
    }

    private static GatewayConfig config(String baseUrl) {
        return new GatewayConfig(baseUrl, "model-key", "hospital-coder", 5);
    }

    @Test
    void openAiCompatibleGatewaySendsSchemaAndValidatesResponse() throws Exception {
        String content = Json.stringify(Fixtures.schemaPayload());
        try (TestHttpServer s = TestHttpServer.always(new TestHttpServer.Reply(200, Fixtures.chatCompletion(content)))) {
            OpenAiCompatibleGateway gw = new OpenAiCompatibleGateway(new HttpTransport(3, 0, 0L), config(s.baseUrl()));
            assertTrue(gw.endpoint().endsWith("/chat/completions"));
            assertEquals("Bearer model-key", gw.headers().get("Authorization"));
            Map<String, Object> body = Json.object(gw.requestBody(Map.of("task", "equivalence_judge")));
            assertEquals("hospital-coder", body.get("model"));
            assertTrue(body.containsKey("response_format"));
            assertTrue(body.containsKey("messages"));

            Map<String, Object> result = gw.chat(Map.of("task", "equivalence_judge"), "finding");
            assertEquals("conflict", result.get("verdict"));
            assertNotNull(gw.lastMeta());
            assertFalse(gw.lastMeta().degraded());
            assertEquals(42L, gw.lastMeta().tokenUsage());
            assertEquals("POST", s.lastRequest().method());
            assertTrue(s.lastRequest().body().contains("json_schema"));
            assertTrue(s.lastRequest().body().contains("coding_finding"));
        }
    }

    @Test
    void factSchemaAndInvalidStructureAreHandledSeparately() throws Exception {
        String fact = Json.stringify(Fixtures.factPayload());
        try (TestHttpServer s = TestHttpServer.always(new TestHttpServer.Reply(200, Fixtures.chatCompletion(fact)))) {
            OpenAiCompatibleGateway gw = new OpenAiCompatibleGateway(new HttpTransport(3, 0, 0L), config(s.baseUrl()));
            Map<String, Object> result = gw.chat(Map.of("task", "fact_extraction"), "fact");
            assertTrue(result.containsKey("evidence_fact"));
            assertThrows(GatewayException.class,
                    () -> gw.chat(Map.of("task", "fact_extraction"), "finding"));
        }
        try (TestHttpServer s = TestHttpServer.always(new TestHttpServer.Reply(200, Fixtures.chatCompletion("{}")))) {
            OpenAiCompatibleGateway gw = new OpenAiCompatibleGateway(new HttpTransport(3, 0, 0L), config(s.baseUrl()));
            GatewayException e = assertThrows(GatewayException.class,
                    () -> gw.chat(Map.of("task", "x"), "finding"));
            assertEquals(GatewayException.ErrorCode.STRUCTURE_INVALID, e.code());
        }
    }

    @Test
    void modelUnreachableMarksDegradedMetadata() throws Exception {
        try (TestHttpServer s = TestHttpServer.always(new TestHttpServer.Reply(503, "{}"))) {
            OpenAiCompatibleGateway gw = new OpenAiCompatibleGateway(new HttpTransport(3, 0, 0L), config(s.baseUrl()));
            GatewayException e = assertThrows(GatewayException.class,
                    () -> gw.chat(Map.of("task", "x"), "finding"));
            assertEquals(GatewayException.ErrorCode.HTTP_5XX, e.code());
            assertNotNull(gw.lastMeta());
            assertTrue(gw.lastMeta().degraded());
        }
    }
}
