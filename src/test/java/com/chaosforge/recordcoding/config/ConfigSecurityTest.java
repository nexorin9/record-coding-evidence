package com.chaosforge.recordcoding.config;

import com.chaosforge.recordcoding.util.Deidentifier;
import com.chaosforge.recordcoding.util.ErrorCodeTable;
import com.chaosforge.recordcoding.util.Hashes;
import com.chaosforge.recordcoding.util.Json;
import com.chaosforge.recordcoding.util.SecureParsers;
import com.chaosforge.recordcoding.util.StructuredLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** id2 / id50：fail-fast 配置、外置映射、安全解析、去标识、错误码表与结构化日志。 */
class ConfigSecurityTest {

    @Test
    void failFastListsEveryMissingVariable() {
        AppConfig cfg = AppConfig.from(Map.of());
        assertEquals(AppConfig.REQUIRED, cfg.missingRequired());
        ConfigException e = assertThrows(ConfigException.class, cfg::validateRequired);
        assertTrue(e.missingList().contains("PACS_BASE_URL"));
        assertTrue(e.missingList().contains("QC_API_TOKEN"));
        assertTrue(e.missingVariables().containsAll(AppConfig.REQUIRED));
    }

    @Test
    void neverFallsBackToLocalhostMockOrExample() {
        AppConfig cfg = AppConfig.from(Map.of());
        for (String key : AppConfig.REQUIRED) {
            assertNull(cfg.get(key), key + " 不得有内置端点默认值");
            assertFalse(cfg.has(key));
        }
        assertThrows(ConfigException.class, cfg::pacsBaseUrl);
        assertTrue(cfg.presentKeys().values().stream()
                .noneMatch(v -> v.contains("localhost") || v.contains("mock") || v.contains("example")));
    }

    @Test
    void fullyConfiguredEnvPassesAndKeepsNumericOptionalDefaults() {
        Map<String, String> env = new LinkedHashMap<>();
        for (String key : AppConfig.REQUIRED) env.put(key, "https://his.internal/" + key);
        env.put("RIS_MLLP_LISTEN_PORT", "2575");
        AppConfig cfg = AppConfig.from(env);
        assertDoesNotThrow(cfg::validateRequired);
        assertTrue(cfg.missingRequired().isEmpty());
        assertEquals(2575, cfg.risMllpListenPort());
        assertEquals(30, cfg.modelTimeoutSeconds());
        assertEquals(3, cfg.httpRetryMax());
        assertEquals("https://his.internal/PACS_BASE_URL", cfg.pacsBaseUrl());
    }

    @Test
    void partialConfigReportsOnlyTheMissingNames() {
        Map<String, String> env = new HashMap<>();
        for (String key : AppConfig.REQUIRED) if (!key.equals("PACS_BASE_URL")) env.put(key, "x");
        assertEquals(List.of("PACS_BASE_URL"), AppConfig.from(env).missingRequired());
    }

    @Test
    void classpathMappingCoversCoreRolesAndNeverGuesses() {
        FieldMapping m = FieldMapping.fromClasspath();
        assertEquals("1.0.0", m.version());
        assertEquals("PID-3", m.locator("patient_id"));
        assertEquals("OBX-5", m.locator("report_conclusion"));
        assertTrue(m.roles().contains("message_control_id"));
        assertTrue(m.isUnmapped("no_such_role"));
        assertEquals(FieldMapping.UNMAPPED, m.locator("no_such_role"));
    }

    @Test
    void customMappingWithMissingRoleStaysUnmapped() {
        FieldMapping m = FieldMapping.fromJson(
                "{\"version\":\"9\",\"roles\":{\"patient_id\":\"PID-3\",\"report_conclusion\":\"unmapped\"}}");
        assertTrue(m.isUnmapped("report_conclusion"));
        assertEquals("9", m.version());
        assertFalse(m.isUnmapped("patient_id"));
    }

    @Test
    void dtdAndEntityDeclarationsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SecureParsers.rejectDtd("<!DOCTYPE foo>"));
        assertThrows(IllegalArgumentException.class,
                () -> SecureParsers.rejectDtd("<!ENTITY x SYSTEM \"file:///etc/passwd\">"));
        assertDoesNotThrow(() -> SecureParsers.rejectDtd("{\"roles\":{}}"));
        assertThrows(IllegalArgumentException.class,
                () -> FieldMapping.fromJson("<!DOCTYPE x> {\"roles\":{}}"));
    }

    @Test
    void secureSaxFactoryRefusesDoctypeAndExternalEntities() throws Exception {
        var factory = SecureParsers.secureSaxFactory();
        assertNotNull(factory);
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        SAXParser parser = factory.newSAXParser();
        byte[] xml = ("<!DOCTYPE r [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><r>&e;</r>")
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(org.xml.sax.SAXException.class, () -> parser.parse(new ByteArrayInputStream(xml),
                new org.xml.sax.helpers.DefaultHandler()));
    }

    @Test
    void deidentifierDropsPiiKeysAndMasksIdentifiers() {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("姓名", "张三");
        in.put("patientName", "李四");
        in.put("id_card", "110101199001011234");
        in.put("visit_no", "VISIT-1");
        in.put("note", "联系电话 13800138000");
        Map<String, Object> out = Deidentifier.scrubMap(in);
        assertFalse(out.containsKey("姓名"));
        assertFalse(out.containsKey("patientName"));
        assertFalse(out.containsKey("id_card"));
        assertEquals("VISIT-1", out.get("visit_no"));
        assertEquals("联系电话 [已去标识]", out.get("note"));
        assertTrue(Deidentifier.isPiiKey("PATIENTNAME"));
        assertFalse(Deidentifier.isPiiKey("visit_no"));
        assertTrue(Deidentifier.containsPii("身份证 110101199001011234"));
    }

    @Test
    void errorCodeTableIsUniqueAndQueryable() {
        var all = ErrorCodeTable.all();
        assertTrue(all.size() >= 30, "错误码表应覆盖配置 / 传输 / 协议 / 模型 / 存储 / 服务各层");
        assertTrue(ErrorCodeTable.duplicateCodes().isEmpty());
        assertTrue(ErrorCodeTable.codes().size() == all.size());
        for (var e : all) {
            assertFalse(e.code().isBlank());
            assertFalse(e.reason().isBlank());
            assertFalse(e.action().isBlank());
            assertNotNull(e.layer());
            assertEquals(e.code(), ErrorCodeTable.lookup(e.code()).code());
            assertTrue(ErrorCodeTable.describe(e.code()).contains(e.code()));
        }
        assertEquals("HTTP_4XX", ErrorCodeTable.lookup("HTTP_4XX").code());
        assertFalse(ErrorCodeTable.lookup("HTTP_4XX").retryable());
        assertTrue(ErrorCodeTable.lookup("HTTP_5XX").retryable());
        assertTrue(ErrorCodeTable.describe("NO_SUCH_CODE").contains("未知错误码"));
    }

    @Test
    void structuredLogAppendsDeidentifiedJsonLines(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("run.log");
        StructuredLog.append(log, "INFO", "EVIDENCE_FETCHED",
                Map.of("visit_no", "VISIT-1", "phone", "13800138000"));
        List<String> lines = StructuredLog.read(log);
        assertEquals(1, lines.size());
        Map<String, Object> parsed = Json.object(lines.get(0));
        assertEquals("INFO", parsed.get("level"));
        assertEquals("EVIDENCE_FETCHED", parsed.get("event"));
        assertTrue(parsed.containsKey("ts"));
        assertFalse(lines.get(0).contains("13800138000"));
        StructuredLog.append(log, "WARN", "SOURCE_UNREACHABLE", Map.of("code", "TIMEOUT"));
        assertEquals(2, StructuredLog.read(log).size());
        StructuredLog.rotate(log);
        assertTrue(java.nio.file.Files.exists(log));
    }

    @Test
    void jsonRoundTripAndStableHashes() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("a", 1);
        m.put("b", List.of("x", "y"));
        m.put("c", true);
        Map<String, Object> back = Json.object(Json.stringify(m));
        assertEquals(1, ((Number) back.get("a")).intValue());
        assertEquals("x", ((List<?>) back.get("b")).get(0));
        assertEquals(Boolean.TRUE, back.get("c"));
        assertEquals("PID-000001", Json.firstValue(Json.object(FixturesHolder.DICOM_ELEMENT)));
        assertThrows(Json.JsonException.class, () -> Json.parse("not-json"));
        assertEquals(64, Hashes.sha256Hex("x").length());
        assertEquals(Hashes.sha256Hex("a|b|c"), Hashes.idempotencyKey("a", "b", "c"));
        assertEquals(Hashes.stableHash(List.of("a", "b")), Hashes.stableHash(List.of("b", "a")));
        assertNotEquals(Hashes.stableHash(List.of("a")), Hashes.stableHash(List.of("a", "b")));
    }

    /** 避免测试里重复写 DICOM 元素字面量。 */
    private static final class FixturesHolder {
        static final String DICOM_ELEMENT = "{\"Value\":[\"PID-000001\"],\"vr\":\"LO\"}";
    }
}
