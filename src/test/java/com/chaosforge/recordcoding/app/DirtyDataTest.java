package com.chaosforge.recordcoding.app;

import com.chaosforge.recordcoding.config.FieldMapping;
import com.chaosforge.recordcoding.domain.MedicalRecord;
import com.chaosforge.recordcoding.protocol.dicom.DicomJsonAttributeSelector;
import com.chaosforge.recordcoding.protocol.dicom.DicomJsonModel;
import com.chaosforge.recordcoding.protocol.dicom.MissingReason;
import com.chaosforge.recordcoding.protocol.hl7.Hl7Parser;
import com.chaosforge.recordcoding.protocol.hl7.Hl7ParseException;
import com.chaosforge.recordcoding.protocol.hl7.Orur01Mapper;
import com.chaosforge.recordcoding.support.Fixtures;
import com.chaosforge.recordcoding.transport.HttpTransport;
import com.chaosforge.recordcoding.transport.TransportException;
import com.chaosforge.recordcoding.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** id13：GBK / UTF-8 混编、超长字段、空值与脏行，全部显式报错或标缺口，不静默吞掉。 */
class DirtyDataTest {

    @Test
    void mixedGbkAndUtf8PayloadsBothParse(@TempDir Path dir) throws Exception {
        Charset gbk = Charset.forName("GBK");
        Path gbkFile = dir.resolve("oru-gbk.hl7");
        Path utf8File = dir.resolve("oru-utf8.hl7");
        Files.write(gbkFile, Fixtures.oru().getBytes(gbk));
        Files.writeString(utf8File, Fixtures.oru(), StandardCharsets.UTF_8);
        assertEquals("PID-TEST", Hl7Parser.parse(Files.readAllBytes(gbkFile), gbk).first("PID").field(3));
        assertEquals("PID-TEST", Hl7Parser.parse(Files.readString(utf8File, StandardCharsets.UTF_8))
                .first("PID").field(3));
        assertEquals("右下肺实变影，考虑感染性病变",
                Hl7Parser.parse(Files.readAllBytes(gbkFile), gbk).first("OBX").field(5));
    }

    @Test
    void veryLongFieldIsPreservedWithoutTruncation() {
        String longText = "右下肺".repeat(4000);
        String obx = Fixtures.segment("OBX", 14, Map.of(5, longText));
        var msg = Hl7Parser.parse(Fixtures.mshLine() + "\r" + obx);
        assertEquals(longText, msg.first("OBX").field(5));
        assertEquals(longText.length(), msg.first("OBX").field(5).length());
    }

    @Test
    void emptyValueIsEmptyNotUnmapped() {
        String pid = Fixtures.segment("PID", 8, Map.of());
        var msg = Hl7Parser.parse(Fixtures.mshLine() + "\r" + pid);
        Orur01Mapper mapper = new Orur01Mapper(FieldMapping.fromClasspath());
        var f = mapper.mapRole(msg, "patient_id");
        assertEquals(Orur01Mapper.Status.EMPTY, f.status());
        assertFalse(f.isUnmapped());
        assertEquals("PID-3", f.locator());
        assertTrue(f.note().contains("empty"));
    }

    @Test
    void dirtySegmentsAndMalformedJsonFailLoudly() {
        assertThrows(Hl7ParseException.class, () -> Hl7Parser.parse(""));
        assertThrows(Hl7ParseException.class, () -> Hl7Parser.parse(Fixtures.mshLine() + "\rXX"));
        assertThrows(IllegalArgumentException.class, () -> Hl7Parser.parse("PID|1||P1"));
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\":}"));
        TransportException e = assertThrows(TransportException.class, () -> HttpTransport.requireJson(
                new HttpTransport.Response(200, "<html>not json</html>", Map.of())));
        assertEquals(TransportException.ErrorCode.STRUCTURE_INVALID, e.code());
    }

    @Test
    void dicomDirtyAttributesReturnExplicitMissingReasons() {
        String dirty = "{\"00080060\":{\"Value\":[],\"vr\":\"CS\"},"
                + "\"00080061\":{\"Value\":\"not-a-list\",\"vr\":\"CS\"},"
                + "\"00081032\":{\"Value\":[{\"00080100\":{\"Value\":[],\"vr\":\"SH\"}}],\"vr\":\"SQ\"}}";
        var selector = new DicomJsonAttributeSelector(DicomJsonModel.parse(dirty));
        assertEquals(MissingReason.VALUE_EMPTY, selector.selectStringValue("00080060").reason());
        assertEquals(MissingReason.VALUE_EMPTY, selector.selectStringValue("00080061").reason());
        assertNull(DicomJsonModel.parse(dirty).singleValue("DEADBEEF"));
        assertDoesNotThrow(() -> DicomJsonModel.parse(dirty).items("00081032"));
        assertTrue(selector.selectStringValue("00081032").isMissing());
    }

    @Test
    void emrWithBlankAndDirtyFieldsNeverInventsValues() {
        Map<String, Object> dirty = new java.util.LinkedHashMap<>();
        dirty.put("visit_no", "VISIT-DIRTY");
        dirty.put("age", "不是数字");
        dirty.put("main_diagnosis", Map.of("code", "", "name", "肺炎"));
        dirty.put("other_diagnosis", "not-a-list");
        MedicalRecord r = MedicalRecord.fromEmr(dirty);
        assertEquals("VISIT-DIRTY", r.visitNo().value());
        assertTrue(r.age().isMissing());
        assertTrue(r.gender().isMissing());
        assertTrue(r.admissionTime().isMissing());
        assertTrue(r.coding().mainCodeText().isBlank(), "空编码原样保留，由结构校验判失败");
        assertFalse(new com.chaosforge.recordcoding.coding.Icd10StructureValidator()
                .isStructurallyValid(r.coding().mainDiagnosis().code().orElse("")));
        assertTrue(r.coding().otherDiagnoses().isEmpty());
        assertTrue(r.coding().procedures().isEmpty());
    }
}
