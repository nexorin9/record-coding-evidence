package com.chaosforge.recordcoding.coding;

import com.chaosforge.recordcoding.domain.Field;
import com.chaosforge.recordcoding.domain.MedicalRecord;
import com.chaosforge.recordcoding.support.Fixtures;
import com.chaosforge.recordcoding.transport.HttpTransport;
import com.chaosforge.recordcoding.support.TestHttpServer;
import com.chaosforge.recordcoding.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** id7 / id30 / id31 / id32 / id33：三层编码校验与字典不可用降级。 */
class CodingValidationTest {

    @Test
    void icd10StructureAcceptsOnlySixPositionMedicareCodes() {
        Icd10StructureValidator v = new Icd10StructureValidator();
        assertTrue(v.isStructurallyValid("J18900"));
        assertTrue(v.isStructurallyValid("U07100"));
        assertTrue(v.isStructurallyValid("E11900"));
        assertFalse(v.isStructurallyValid("J18"));
        assertFalse(v.isStructurallyValid("J1890012"));
        assertFalse(v.isStructurallyValid("J1A900"));
        assertFalse(v.isStructurallyValid("[00000"));
        assertFalse(v.isStructurallyValid(""));
        assertEquals("ICD10_STRUCTURE", v.ruleId());
        assertFalse(v.ruleVersion().isBlank());
        assertFalse(v.basis().isBlank());
        assertTrue(v.validate("J18900").stream().allMatch(f ->
                f.ruleVersion() != null && f.basis() != null));
    }

    @Test
    void icd9cm3StructureRequiresSixDigits() {
        Icd9cm3StructureValidator v = new Icd9cm3StructureValidator();
        assertTrue(v.isStructurallyValid("475100"));
        assertTrue(v.isStructurallyValid("332400"));
        assertFalse(v.isStructurallyValid("47510"));
        assertFalse(v.isStructurallyValid("4751A0"));
        assertFalse(v.isStructurallyValid("4751000"));
        assertFalse(v.isStructurallyValid(""));
        assertEquals("ICD9CM3_STRUCTURE", v.ruleId());
        assertFalse(v.basis().isBlank());
    }

    @Test
    void unavailableDictionaryNeverProducesExistenceConclusion() {
        DictionaryResult u = DictionaryLookup.unavailable("本地无明细字典").lookup("J18900");
        assertTrue(u.isUnavailable());
        assertFalse(u.isExistenceConclusion());
        assertEquals(DictionaryResult.State.DICTIONARY_UNAVAILABLE, u.state());
        assertTrue(u.reason().contains("无明细字典"));
        assertEquals(DictionaryLookup.Mode.UNAVAILABLE, DictionaryLookup.unavailable().mode());
    }

    @Test
    void localDictionaryDistinguishesFoundFromNotFound(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("icd10.txt");
        Files.writeString(file, "# 本地字典\nJ18900,社区获得性肺炎\nJ18900,重复项应去重\n",
                StandardCharsets.UTF_8);
        DictionaryLookup d = DictionaryLookup.local(file);
        assertEquals(DictionaryLookup.Mode.LOCAL_FILE, d.mode());
        assertEquals(1, d.localSize());
        assertEquals(DictionaryResult.State.FOUND, d.lookup("j18900").state());
        assertEquals("社区获得性肺炎", d.lookup("J18900").name());
        assertEquals(DictionaryResult.State.NOT_FOUND, d.lookup("Z99999").state());
        assertTrue(d.lookup("Z99999").isExistenceConclusion());
        assertTrue(DictionaryLookup.local(dir.resolve("missing.txt")).lookup("J18900").isUnavailable());
    }

    @Test
    void onlineProbeReportsNotConfiguredAndRealConnectivity(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("dict-probe.log");
        OnlineDictionaryProbe missing = new OnlineDictionaryProbe(
                new HttpTransport(3, 0, 0L), "", "", log);
        assertFalse(missing.configured());
        OnlineDictionaryProbe.ProbeResult p = missing.probe("J18900");
        assertEquals("not_configured", p.status());
        assertFalse(p.reachable());
        assertTrue(p.reason().contains("NHSA_DICT_URL"));

        try (TestHttpServer s = TestHttpServer.always(new TestHttpServer.Reply(200,
                "{\"exists\":true,\"code\":\"J18900\",\"name\":\"社区获得性肺炎\"}"))) {
            OnlineDictionaryProbe probe = new OnlineDictionaryProbe(
                    new HttpTransport(3, 0, 0L), s.baseUrl() + "/dict", "dict-token", log);
            assertTrue(probe.configured());
            OnlineDictionaryProbe.ProbeResult ok = probe.probe("J18900");
            assertEquals("reachable", ok.status());
            assertTrue(ok.reachable());
            assertTrue(ok.exists());
            assertEquals("社区获得性肺炎", ok.name());
            assertTrue(s.lastRequest().query().contains("code=J18900"));
            assertTrue(Files.exists(log));
        }
        try (TestHttpServer s = TestHttpServer.always(new TestHttpServer.Reply(503, "{}"))) {
            OnlineDictionaryProbe probe = new OnlineDictionaryProbe(
                    new HttpTransport(3, 0, 0L), s.baseUrl(), "t", null);
            assertFalse(probe.probe("J18900").reachable());
            assertEquals("unreachable", probe.probe("J18900").status());
        }
        DictionaryLookup online = DictionaryLookup.online(missing);
        assertTrue(online.lookup("J18900").isUnavailable());
    }

    @Test
    void businessValidationFlagsGenderConflictAndMissingFields() {
        BusinessValidator v = new BusinessValidator();
        Map<String, Object> emr = Json.object(Fixtures.emrJson());
        MedicalRecord ok = MedicalRecord.fromEmr(emr);
        assertEquals(3, v.validate(ok).size());
        assertTrue(v.validate(ok).stream().allMatch(f -> f.ruleVersion() != null && f.basis() != null));

        Map<String, Object> conflict = new java.util.LinkedHashMap<>(emr);
        conflict.put("gender", "女");
        conflict.put("main_diagnosis", Map.of("code", "N40900", "name", "男性限定诊断"));
        assertTrue(v.validate(MedicalRecord.fromEmr(conflict)).stream()
                .anyMatch(f -> f.ruleId().equals("BUSINESS_GENDER") && f.isFailure()));

        assertTrue(v.validate(MedicalRecord.fromEmr(Map.of())).stream()
                .anyMatch(f -> f.isFailure() && f.message().contains("缺失")));
    }

    @Test
    void threeLayerValidationExplicitlyMarksMissingDictionaryLayer() {
        MedicalRecord record = MedicalRecord.fromEmr(Json.object(Fixtures.emrJson()));
        CodingValidation.Result r = new CodingValidation(DictionaryLookup.unavailable()).validate(record);
        assertTrue(r.dictionaryUnavailable());
        assertTrue(r.missingLayers().contains("DICTIONARY"));
        assertFalse(r.layersExecuted().contains("DICTIONARY"));
        assertTrue(r.layersExecuted().contains("STRUCTURE"));
        assertTrue(r.layersExecuted().contains("BUSINESS"));
        assertTrue(r.findings().stream().anyMatch(f -> f.ruleId().equals("DICTIONARY_UNAVAILABLE")));
        assertTrue(r.findings().stream().anyMatch(f -> f.message().contains("未做编码存在性判定")));
        assertTrue(r.findings().size() >= 5, "结构层 / 字典层 / 业务层结论都要收集");

        CodingValidation.Result withDict = new CodingValidation(DictionaryLookup.local(
                Path.of("src/test/resources/does-not-exist.txt"))).validate(record);
        assertTrue(withDict.dictionaryUnavailable(), "字典文件不存在也必须显式降级");
        assertEquals(3, new CodingValidation(DictionaryLookup.unavailable()).layersFor(
                com.chaosforge.recordcoding.domain.CodeSystem.ICD10_MEDICARE).size());
    }

    @Test
    void missingEmrFieldsAreExplicitMissingNotZeroOrEmptyString() {
        MedicalRecord r = MedicalRecord.fromEmr(Map.of());
        assertTrue(r.visitNo().isMissing());
        assertTrue(r.gender().isMissing());
        assertTrue(r.age().isMissing());
        assertThrows(IllegalStateException.class, () -> r.age().value());
        assertEquals(-1, r.age().orElse(-1));
        assertTrue(r.coding().mainDiagnosis().code().isMissing());
        assertTrue(r.coding().mainCodeText().contains("缺失"));
        assertFalse(Field.present("J18900").isMissing());
        assertEquals("J18900", Field.present("J18900").display());
    }
}
