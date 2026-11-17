package com.chaosforge.recordcoding.protocol.hl7;

import com.chaosforge.recordcoding.config.FieldMapping;
import com.chaosforge.recordcoding.support.Fixtures;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** id3 / id17：严格分词、MSH 分隔符、ORU^R01 外置角色映射与 unmapped 语义。 */
class Hl7ProtocolTest {

    @Test
    void parsesOruFieldsByExactOneBasedLocators() {
        Hl7Message msg = Hl7Parser.parse(Fixtures.oru());
        assertEquals(5, msg.size());
        assertEquals("MSG-TEST-001", msg.first("MSH").field(10));
        assertEquals("PID-TEST", msg.first("PID").field(3));
        assertEquals("VISIT-TEST", msg.first("PV1").field(19));
        assertEquals("右下肺实变影，考虑感染性病变", msg.first("OBX").field(5));
        assertEquals("20260202103000", msg.first("OBX").field(14));
        assertEquals("F", msg.first("OBR").field(25));
        assertEquals("CT^胸部 CT", msg.first("OBR").field(4));
        assertEquals("", msg.first("PID").field(0));
        assertFalse(msg.first("PID").hasField(999));
        assertEquals(2, msg.first("OBR").components(4).size());
    }

    @Test
    void externalMappingResolvesEveryConfiguredRole() {
        Orur01Mapper mapper = new Orur01Mapper(FieldMapping.fromClasspath());
        Hl7Message msg = Hl7Parser.parse(Fixtures.oru());
        assertEquals("PID-TEST", mapper.mapRole(msg, "patient_id").value());
        assertEquals(Orur01Mapper.Status.OK, mapper.mapRole(msg, "patient_id").status());
        assertEquals("VISIT-TEST", mapper.mapRole(msg, "visit_no").value());
        assertEquals("右下肺实变影，考虑感染性病变", mapper.mapRole(msg, "report_conclusion").value());
        assertEquals("20260202103000", mapper.mapRole(msg, "report_time").value());
        assertEquals("F", mapper.mapRole(msg, "report_status").value());
        assertEquals("MSG-TEST-001", mapper.messageControlId(msg));
        assertEquals(FieldMapping.fromClasspath().roles().size(), mapper.map(msg).size());
    }

    @Test
    void missingMappingIsUnmappedAndMissingFieldIsEmpty() {
        Hl7Message msg = Hl7Parser.parse(Fixtures.oru());
        Orur01Mapper empty = new Orur01Mapper(FieldMapping.fromJson("{\"version\":\"0\",\"roles\":{}}"));
        Orur01Mapper.MappedField f = empty.mapRole(msg, "patient_id");
        assertTrue(f.isUnmapped());
        assertEquals(FieldMapping.UNMAPPED, f.locator());
        assertTrue(f.note().contains("不猜"));

        Orur01Mapper partial = new Orur01Mapper(FieldMapping.fromJson(
                "{\"version\":\"0\",\"roles\":{\"report_time\":\"OBX-14\",\"report_conclusion\":\"OBX-5\"}}"));
        assertTrue(partial.mapRole(msg, "no_such_role").isUnmapped(),
                "映射表未覆盖的角色仍标 unmapped");

        Hl7Message truncated = Hl7Parser.parse(
                Fixtures.mshLine() + "\r" + Fixtures.segment("OBX", 5, Map.of(5, "右下肺实变影")));
        Orur01Mapper.MappedField emptyField = partial.mapRole(truncated, "report_time");
        assertEquals(Orur01Mapper.Status.EMPTY, emptyField.status(),
                "映射存在但报文缺该字段，标 empty 而非 unmapped");
        assertEquals("", emptyField.value());
        assertEquals("OBX-14", emptyField.locator());
    }

    @Test
    void repeatedFieldTakesFirstAndMarksMulti() {
        Orur01Mapper mapper = new Orur01Mapper(FieldMapping.fromJson(
                "{\"version\":\"0\",\"roles\":{\"patient_id\":\"PID-3\"}}"));
        String msh = Fixtures.mshLine();
        String pid = Fixtures.segment("PID", 3, Map.of(3, "P1~P2"));
        Hl7Message msg = Hl7Parser.parse(msh + "\r" + pid);
        Orur01Mapper.MappedField f = mapper.mapRole(msg, "patient_id");
        assertEquals("P1", f.value());
        assertEquals(Orur01Mapper.Status.MULTI, f.status());
        assertTrue(f.note().contains("multi"));
    }

    @Test
    void shortOrMalformedSegmentsFailInsteadOfBeingSkipped() {
        Hl7ParseException e1 = assertThrows(Hl7ParseException.class,
                () -> Hl7Parser.parse(Fixtures.mshLine() + "\rAB"));
        assertEquals("SEGMENT_TOO_SHORT", e1.code());
        assertEquals(2, e1.segmentIndex());
        Hl7ParseException e2 = assertThrows(Hl7ParseException.class,
                () -> Hl7Parser.parse(Fixtures.mshLine() + "\rABCD|1"));
        assertEquals("SEGMENT_NAME_INVALID", e2.code());
        Hl7ParseException e3 = assertThrows(Hl7ParseException.class, () -> Hl7Parser.parse("   "));
        assertEquals("HL7_EMPTY", e3.code());
    }

    @Test
    void delimitersComeFromMshNotHardcoded() {
        String custom = "MSH*^~\\&*RIS*HOSP*EMR*HOSP*20260202101500**ORU^R01*MSG-STAR*P*2.5"
                + "\rPID*1**PID-STAR";
        Hl7Message msg = Hl7Parser.parse(custom);
        assertEquals('*', msg.delimiter().field());
        assertEquals('^', msg.delimiter().component());
        assertEquals('~', msg.delimiter().repetition());
        assertEquals('\\', msg.delimiter().escape());
        assertEquals('&', msg.delimiter().subcomponent());
        assertEquals("PID-STAR", msg.first("PID").field(3));
        assertEquals("MSG-STAR", msg.first("MSH").field(10));
    }

    @Test
    void escapeSequencesAreRestored() {
        Hl7Delimiter d = Hl7Delimiter.STANDARD;
        assertEquals("A|B^C~D&E", d.unescape("A\\F\\B\\S\\C\\R\\D\\T\\E"));
        assertEquals(3, d.components("右^下^肺").size());
        assertEquals(3, d.repetitions("x~y~z").size());
        assertEquals(2, d.subcomponents("a&b").size());
    }

    @Test
    void gbkEncodedPayloadParsesWithoutGuesswork() {
        Charset gbk = Charset.forName("GBK");
        Hl7Message msg = Hl7Parser.parse(Fixtures.oru().getBytes(gbk), gbk);
        assertEquals("右下肺实变影，考虑感染性病变", msg.first("OBX").field(5));
        assertEquals(5, msg.size());
    }
}
