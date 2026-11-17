package com.chaosforge.recordcoding.evidence;

import com.chaosforge.recordcoding.domain.BindingResolver;
import com.chaosforge.recordcoding.domain.VisitBinding;
import com.chaosforge.recordcoding.support.Fixtures;
import com.chaosforge.recordcoding.transport.EvidenceTags;
import com.chaosforge.recordcoding.util.Hashes;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** id6 / id25 / id27：跨源证据归一、稳定哈希与三方实体绑定歧义即拒。 */
class EvidenceAndBindingTest {

    @Test
    void evidenceRowCarriesSourceLocatorHashAndMissingSemantics() {
        EvidenceRow p = EvidenceRow.present(EvidenceType.STUDY_METADATA, EvidenceSource.QIDO_RS,
                "患者标识", "00100020", "PID-1", "ACC-1");
        assertFalse(p.isMissing());
        assertEquals(64, p.hash().length());
        assertEquals(Hashes.sha256Hex(EvidenceSource.QIDO_RS + "|00100020|PID-1"), p.hash());
        assertTrue(p.evidenceId().startsWith("E"));
        assertEquals("PID-1", p.displayValue());
        assertTrue(p.csv("V1", "B1").contains("V1,B1"));
        assertEquals(6, p.toMap().size());

        EvidenceRow m = EvidenceRow.missing(EvidenceType.REPORT_CONCLUSION, EvidenceSource.HL7_MLLP,
                "报告结论", "OBX-5", MissingReason.EMPTY_VALUE, "ACC-1");
        assertTrue(m.isMissing());
        assertEquals(MissingReason.EMPTY_VALUE, m.missingReason());
        assertTrue(m.displayValue().startsWith("missing("));
        assertEquals("", m.value());
        assertTrue(EvidenceRow.csvHeader().contains("hash8"));
    }

    @Test
    void stableSnapshotHashIgnoresFieldOrder() {
        EvidenceRow a = EvidenceRow.present(EvidenceType.STUDY_METADATA, EvidenceSource.QIDO_RS,
                "患者标识", "00100020", "PID-1", "ACC-1");
        EvidenceRow b = EvidenceRow.present(EvidenceType.REPORT_CONCLUSION, EvidenceSource.HL7_MLLP,
                "报告结论", "OBX-5", "右下肺实变影", "ACC-1");
        assertEquals(EvidenceHash.of(List.of(a, b)), EvidenceHash.of(List.of(b, a)));
        assertNotEquals(EvidenceHash.of(List.of(a)), EvidenceHash.of(List.of(a, b)));
        assertEquals(Hashes.sha256Hex("QIDO_RS|00100020|PID-1"), EvidenceHash.rowHash("QIDO_RS", "00100020", "PID-1"));
    }

    @Test
    void assemblerNormalizesQidoAndHl7IntoOneRowSet() {
        Map<String, Object> study = Fixtures.qidoFlat();
        Map<String, String> report = new LinkedHashMap<>();
        report.put("accessionNumber", "ACC-TEST");
        report.put("report_conclusion", "右下肺实变影，考虑感染性病变");
        report.put("controlId", "MSG-TEST-001");
        EvidenceAssembler assembler = new EvidenceAssembler();
        EvidenceSnapshot s = assembler.assemble("VISIT-TEST", "B1", List.of(study), List.of(report));
        assertEquals(EvidenceTags.ALL.size() + 2, s.size(), "10 个证据 tag + 报告结论 + 访问号对齐键");
        assertEquals("VISIT-TEST", s.visitNo());
        assertEquals("B1", s.batchId());
        assertEquals(64, s.evidenceHash().length());
        assertTrue(s.rows().stream().anyMatch(r -> r.source() == EvidenceSource.QIDO_RS));
        assertTrue(s.rows().stream().anyMatch(r -> r.source() == EvidenceSource.HL7_MLLP));
        assertTrue(s.rows().stream().anyMatch(EvidenceRow::isMissing), "未提供的 tag 必须显式标缺口");
        assertEquals(MissingReason.FIELD_ABSENT,
                s.rows().stream().filter(EvidenceRow::isMissing).findFirst().orElseThrow().missingReason());
        assertTrue(assembler.groupByAccession(s).containsKey("ACC-TEST"));

        EvidenceSnapshot again = assembler.assemble("VISIT-TEST", "B1", List.of(study), List.of(report));
        assertEquals(s.evidenceHash(), again.evidenceHash(), "同一批证据两次装配哈希必须一致");
        assertEquals(s.evidenceHash(), assembler.fromRows("VISIT-TEST", "B1", s.rows()).evidenceHash());
    }

    @Test
    void bindingResolverRejectsAmbiguityAndZeroHits() {
        BindingResolver r = new BindingResolver();
        VisitBinding resolved = r.resolve("V1", List.of(BindingResolver.study("P1", "U1", "A1")));
        assertEquals(VisitBinding.Result.RESOLVED, resolved.result());
        assertEquals("P1", resolved.patientId());
        assertEquals(List.of("U1"), resolved.studyUids());
        assertTrue(resolved.canIssueVerdict());
        assertFalse(r.conflicts("P1", resolved));
        assertTrue(r.conflicts("P2", resolved));

        VisitBinding ambiguous = r.resolve("V1", List.of(
                BindingResolver.study("P1", "U1", "A1"), BindingResolver.study("P2", "U2", "A2")));
        assertEquals(VisitBinding.Result.AMBIGUOUS, ambiguous.result());
        assertFalse(ambiguous.canIssueVerdict(), "多 PatientID 一律拒绝出具判定");
        assertEquals(2, ambiguous.candidates().size());
        assertTrue(ambiguous.ambiguityReason().contains("歧义"));
        assertTrue(r.conflicts("P1", ambiguous));

        VisitBinding none = r.resolve("V1", List.of());
        assertEquals(VisitBinding.Result.NOT_FOUND, none.result());
        assertFalse(none.canIssueVerdict());
        assertTrue(none.ambiguityReason().contains("零命中"));
    }
}
