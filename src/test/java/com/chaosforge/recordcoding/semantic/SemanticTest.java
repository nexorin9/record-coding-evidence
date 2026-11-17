package com.chaosforge.recordcoding.semantic;

import com.chaosforge.recordcoding.domain.Field;
import com.chaosforge.recordcoding.modelgw.GatewayException;
import com.chaosforge.recordcoding.modelgw.ModelGateway;
import com.chaosforge.recordcoding.support.Fixtures;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** id9 / id36 / id37 / id38：四值判定、确定性路径、归因、豁免与证据出处装配。 */
class SemanticTest {

    private static ModelGateway stub(Map<String, Object> response) {
        return (payload, schema) -> new LinkedHashMap<>(response);
    }

    private static DiagnosticFact completeFact() {
        return new DiagnosticFact(Field.present("社区获得性肺炎"), Field.present("右下肺"),
                Field.present("右"), Field.present("感染性病变"),
                Field.present(false), Field.present(false), "E1", 0.9, "单句");
    }

    private static Judgement advise(Verdict v, double confidence, List<String> refs) {
        return new Judgement(v, confidence, refs, "模型判定草稿", true, false);
    }

    @Test
    void fourVerdictsAreMutuallyExclusiveAndComplete() {
        assertTrue(Verdict.mutuallyExclusiveComplete());
        assertEquals(4, Verdict.values().length);
        for (String wire : List.of("equivalent", "conflict", "report_stale", "evidence_missing")) {
            assertNotNull(Verdict.fromWire(wire));
            assertFalse(Verdict.fromWire(wire).label().isBlank());
        }
        assertThrows(IllegalArgumentException.class, () -> Verdict.fromWire("maybe"));
    }

    @Test
    void extractedFactsKeepMissingElementsExplicit() {
        Map<String, Object> partial = new LinkedHashMap<>();
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("diagnosis", "肺炎");
        fact.put("negation", "false");
        partial.put("evidence_fact", fact);
        partial.put("confidence", 0.8);
        DiagnosticFact d = DiagnosticFact.fromModel(partial, "E1");
        assertTrue(d.diagnosis().isPresent());
        assertTrue(d.site().isMissing());
        assertTrue(d.laterality().isMissing());
        assertFalse(d.isNegated());
        assertTrue(d.hasNoSite());
        assertTrue(d.hasNoLaterality());
        assertEquals("E1", d.evidenceRef());
        assertEquals(0.8, d.confidence());
        assertTrue(d.display().contains("缺失"));
        assertEquals("跨句", d.withScope("跨句").scope());
        assertFalse(DiagnosticFact.empty("E1").isNegated());
    }

    @Test
    void factExtractorUsesModelAndEscapesUnparsedResponses() {
        FactExtractor ok = new FactExtractor(stub(Fixtures.factPayload()));
        Map<String, Object> payload = ok.payload("右下肺实变影。未见明显异常。", "E1");
        assertEquals("fact_extraction", payload.get("task"));
        assertEquals("跨句", payload.get("scope"));
        assertEquals(3, ((List<?>) payload.get("hints")).size());
        assertEquals("跨句", ok.scopeOf("右下肺实变影。未见明显异常。"));
        assertEquals("单句", ok.scopeOf("右下肺实变影"));
        DiagnosticFact fact = ok.extract("右下肺实变影。未见明显异常。", "E1");
        assertEquals("右下肺", fact.site().value());
        assertEquals("跨句", fact.scope());

        FactExtractor broken = new FactExtractor((p, s) -> {
            throw new GatewayException(GatewayException.ErrorCode.STRUCTURE_INVALID, "bad shape");
        });
        FactExtractor.FactExtractionException e = assertThrows(
                FactExtractor.FactExtractionException.class, () -> broken.extract("x", "E1"));
        assertEquals("MODEL_UNPARSED", e.code());
        assertTrue(e.getMessage().contains("人工全量复核"));
    }

    @Test
    void deterministicPathsNeverAskTheModel() {
        ModelGateway forbidden = (p, s) -> { throw new AssertionError("确定性路径不得调用模型"); };
        EquivalenceJudge judge = new EquivalenceJudge(forbidden);
        Judgement noEvidence = judge.judge("J18900", completeFact(), "20260102090000", "20260101090000", List.of());
        assertEquals(Verdict.EVIDENCE_MISSING, noEvidence.verdict());
        assertTrue(noEvidence.deterministic());
        assertFalse(noEvidence.advise());
        Judgement stale = judge.judge("J18900", completeFact(), "20251201090000", "20260101090000", List.of("E1"));
        assertEquals(Verdict.REPORT_STALE, stale.verdict());
        assertTrue(stale.deterministic());
        assertFalse(stale.advise());
        assertTrue(judge.isStale("2025-12-01T09:00:00Z", "2026-01-01T09:00:00Z"));
        assertFalse(judge.isStale("2026-01-02T09:00:00Z", "2026-01-01T09:00:00Z"));
        assertFalse(judge.isStale("garbage", "2026-01-01T09:00:00Z"));
    }

    @Test
    void lowConfidenceOnlyPromptsAndNeverSuggestsCodes() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("verdict", "conflict");
        resp.put("confidence", 0.42);
        EquivalenceJudge judge = new EquivalenceJudge(stub(resp));
        Judgement j = judge.judge("J18900", completeFact(), "20260102090000", "20260101090000", List.of("E1"));
        assertEquals(Verdict.CONFLICT, j.verdict());
        assertFalse(j.advise());
        assertTrue(j.note().contains("低于阈值"));
        assertFalse(j.deterministic());
        assertEquals(0.65, judge.threshold());
    }

    @Test
    void attributionEngineBindsEveryReasonToAnEvidenceReference() {
        AttributionEngine engine = new AttributionEngine();
        Judgement noRefs = new Judgement(Verdict.CONFLICT, 0.9, List.of(), "", true, false);
        assertFalse(engine.attribute(noRefs, "J18900", completeFact()).produced());
        assertEquals(Attribution.EVIDENCE_MISSING, engine.attribute(noRefs, "J18900", completeFact()).attribution());
        assertEquals(Attribution.EVIDENCE_MISSING, engine.attribute(
                advise(Verdict.EVIDENCE_MISSING, 1.0, List.of("E1")), "J18900", completeFact()).attribution());
        assertEquals(Attribution.STALE_REPORT, engine.attribute(
                advise(Verdict.REPORT_STALE, 1.0, List.of("E1")), "J18900", completeFact()).attribution());
        assertEquals(Attribution.CODE_ABSENT, engine.attribute(
                advise(Verdict.CONFLICT, 0.9, List.of("E1")), "", completeFact()).attribution());
        assertEquals(Attribution.SIDE_MISSING, engine.attribute(
                advise(Verdict.CONFLICT, 0.9, List.of("E1")), "J18900", DiagnosticFact.empty("E1")).attribution());
        assertEquals(Attribution.CODE_ABSENT, engine.attribute(
                advise(Verdict.CONFLICT, 0.9, List.of("E1")), "J18900", completeFact()).attribution());
        assertEquals(Attribution.NONE, engine.attribute(
                advise(Verdict.EQUIVALENT, 0.9, List.of("E1")), "J18900", completeFact()).attribution());
        assertEquals("E1", engine.attribute(
                advise(Verdict.CONFLICT, 0.9, List.of("E1")), "J18900", completeFact()).evidenceRef());
    }

    @Test
    void exemptionWhitelistRecordsMatchedRuleIds() {
        ExemptionWhitelist w = ExemptionWhitelist.defaults();
        assertEquals(1, w.rules().size());
        ExemptionWhitelist.FilterResult hit = w.filter("J18900", "无影像报告");
        assertTrue(hit.exempted());
        assertEquals(List.of("EX-NO-REPORT"), hit.matchedRuleIds());
        assertFalse(hit.reason().isBlank());
        assertFalse(w.filter("J18900", "右下肺实变影").exempted());
    }

    @Test
    void assemblerRejectsDanglingEvidenceReferences() {
        SemanticFindingAssembler a = new SemanticFindingAssembler(ExemptionWhitelist.defaults());
        List<AttributionEngine.AttributionResult> attrs = List.of(
                new AttributionEngine.AttributionResult(Attribution.SIDE_MISSING, "侧别缺失", "E1", true));
        SemanticFindingAssembler.AssemblyResult ok = a.assemble("J18900",
                List.of(advise(Verdict.CONFLICT, 0.9, List.of("E1"))), attrs, Set.of("E1"));
        assertEquals(1, ok.confirmable().size());
        assertEquals(1, ok.differenceCount());
        assertTrue(ok.errorCodes().isEmpty());
        assertEquals("F1", ok.rowToFinding().get("E1"));
        assertTrue(ok.confirmable().get(0).hasEvidenceRefs());

        SemanticFindingAssembler.AssemblyResult dangling = a.assemble("J18900",
                List.of(advise(Verdict.CONFLICT, 0.9, List.of("GHOST"))), attrs, Set.of("E1"));
        assertTrue(dangling.confirmable().isEmpty());
        assertTrue(dangling.errorCodes().contains("DANGLING_EVIDENCE_REF:F1"));

        Judgement prompt = new Judgement(Verdict.CONFLICT, 0.2, List.of("E1"), "置信度不足", false, false);
        SemanticFindingAssembler.AssemblyResult low = a.assemble("J18900", List.of(prompt), attrs, Set.of("E1"));
        assertEquals(1, low.prompts().size());
        assertTrue(low.prompts().get(0).isPromptOnly());
        assertNotNull(SemanticFindingAssembler.findingId("J18900", "E1"));
    }

    @Test
    void humanConfirmationIsRecordedExplicitly() {
        SemanticFinding f = new SemanticFinding("F1", "J18900", List.of("J18900-建议复核"),
                Verdict.CONFLICT, Attribution.SIDE_MISSING, 0.9, List.of("E1"),
                SemanticFinding.ConfirmState.PENDING, "");
        assertTrue(f.isConfirmable());
        assertFalse(f.isPromptOnly());
        assertEquals("conflict", f.verdict().wire());
        f.confirm("编码员A", "2026-11-15T09:20:31Z");
        assertEquals("编码员A", f.confirmedBy());
        assertEquals("2026-11-15T09:20:31Z", f.confirmedAt());
        f.reject("证据不足");
        assertEquals("证据不足", f.rejectReason());
        SemanticFinding prompt = SemanticFinding.prompt("F2", "J18900", "无对应报告证据");
        assertTrue(prompt.isPromptOnly());
        assertFalse(prompt.isConfirmable());
    }
}
