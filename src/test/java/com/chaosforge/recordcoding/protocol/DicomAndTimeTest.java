package com.chaosforge.recordcoding.protocol;

import com.chaosforge.recordcoding.protocol.dicom.DicomJsonAttributeSelector;
import com.chaosforge.recordcoding.protocol.dicom.DicomJsonModel;
import com.chaosforge.recordcoding.protocol.dicom.DicomJsonPath;
import com.chaosforge.recordcoding.protocol.dicom.MissingReason;
import com.chaosforge.recordcoding.protocol.time.ClinicalInstant;
import com.chaosforge.recordcoding.protocol.time.ClinicalInstantException;
import com.chaosforge.recordcoding.protocol.time.ClinicalInstantParser;
import com.chaosforge.recordcoding.protocol.time.InstantPrecision;
import com.chaosforge.recordcoding.support.Fixtures;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/** id18 / id19：DICOM JSON 属性选择器与带时区精度的临床时刻。 */
class DicomAndTimeTest {

    @Test
    void dicomJsonModelReadsTagsVrAndSequenceItems() {
        DicomJsonModel model = DicomJsonModel.parse(Fixtures.dicomJson());
        assertTrue(model.hasTag("00100020"));
        assertEquals("PID-000001", model.singleValue("00100020"));
        assertEquals("DA", model.vr("00080020"));
        assertEquals("20260202", model.singleValue("00080020"));
        assertEquals(1, model.items("00081032").size());
        assertNull(model.singleValue("DEADBEEF"));
        assertTrue(model.values("DEADBEEF").isEmpty());
    }

    @Test
    void attributeSelectorResolvesNestedPathsAndReportsMissingReasons() {
        DicomJsonAttributeSelector s = new DicomJsonAttributeSelector(DicomJsonModel.parse(Fixtures.dicomJson()));
        DicomJsonAttributeSelector.Selection ok = s.selectStringValue("00100020");
        assertTrue(ok.present());
        assertEquals("PID-000001", ok.value());
        assertFalse(ok.isMissing());

        DicomJsonAttributeSelector.Selection nested =
                s.selectValue(DicomJsonPath.parse("00081032/0/00080100"));
        assertTrue(nested.present());
        assertEquals("332400", nested.value());

        assertEquals(MissingReason.TAG_NOT_PRESENT, s.selectStringValue("DEADBEEF").reason());
        assertEquals(MissingReason.PATH_NOT_RESOLVED,
                s.selectValue(DicomJsonPath.parse("00081032/9/00080100")).reason());
        assertEquals(MissingReason.VR_MISMATCH, s.selectStringValue("00081032").reason());
        DicomJsonAttributeSelector empty = new DicomJsonAttributeSelector(
                DicomJsonModel.parse("{\"00080061\":{\"Value\":[],\"vr\":\"CS\"}}"));
        assertEquals(MissingReason.VALUE_EMPTY, empty.selectStringValue("00080061").reason());
        assertEquals(MissingReason.TAG_NOT_PRESENT, s.selectStringValue("00080061").reason());
        assertTrue(s.selectValues("DEADBEEF").isEmpty());
        assertTrue(s.selectStringValue("DEADBEEF").display().startsWith("missing("));
    }

    @Test
    void pathExpressionSupportsTagsIndicesAndDots() {
        DicomJsonPath p = DicomJsonPath.parse("00081032.0.00080100");
        assertEquals(3, p.steps().size());
        assertEquals("00081032", p.firstTag());
        assertTrue(DicomJsonPath.isIndex("0"));
        assertFalse(p.isSingle());
        assertTrue(DicomJsonPath.of("00100020").isSingle());
        assertThrows(IllegalArgumentException.class, () -> DicomJsonPath.parse("  "));
    }

    @Test
    void hl7TsKeepsZonePrecisionAndDegradesExplicitly() {
        ClinicalInstant full = ClinicalInstantParser.parseHl7Ts("20260202101500+0800");
        assertEquals(InstantPrecision.SECOND, full.precision());
        assertFalse(full.isDegraded());
        assertEquals(ZoneOffset.ofHours(8), full.zone());
        ClinicalInstant day = ClinicalInstantParser.parseHl7Ts("20260202");
        assertEquals(InstantPrecision.DAY, day.precision());
        assertTrue(day.isDegraded());
        assertTrue(day.degradedReason().contains("缺分量"));
        assertFalse(day.display().isBlank());
    }

    @Test
    void illegalClinicalInstantsThrowWithRawValueAndLocator() {
        ClinicalInstantException e = assertThrows(ClinicalInstantException.class,
                () -> ClinicalInstantParser.parseHl7Ts("not-a-time"));
        assertEquals("not-a-time", e.rawValue());
        assertEquals("HL7 TS", e.fieldLocator());
        assertThrows(ClinicalInstantException.class, () -> ClinicalInstantParser.parseDicomDa("20261301"));
        assertThrows(ClinicalInstantException.class, () -> ClinicalInstantParser.parseDicomTm("250000"));
    }

    @Test
    void dicomDaTmDtEachUseTheirOwnPrecisionRules() {
        assertEquals(InstantPrecision.DAY, ClinicalInstantParser.parseDicomDa("20260202").precision());
        assertFalse(ClinicalInstantParser.parseDicomDa("20260202").isDegraded());
        assertEquals(InstantPrecision.SECOND, ClinicalInstantParser.parseDicomTm("101530").precision());
        assertEquals(InstantPrecision.MINUTE, ClinicalInstantParser.parseDicomTm("1015").precision());
        assertTrue(ClinicalInstantParser.parseDicomTm("10").isDegraded());
        assertEquals(InstantPrecision.SECOND, ClinicalInstantParser.parseDicomDt("20260202101500").precision());
        assertEquals(ZoneOffset.ofHours(8),
                ClinicalInstantParser.parseDicomDt("20260202101500&+0800").zone());
        assertEquals(InstantPrecision.SECOND,
                ClinicalInstantParser.combineDaTm("20260202", "101530").precision());
    }

    @Test
    void reportOlderThanAdmissionIsDeterministicallyStale() {
        ClinicalInstant report = ClinicalInstantParser.parseHl7Ts("20251201090000");
        ClinicalInstant admission = ClinicalInstantParser.parseHl7Ts("20260101090000");
        assertTrue(report.isBefore(admission));
        assertFalse(admission.isBefore(report));
    }
}
