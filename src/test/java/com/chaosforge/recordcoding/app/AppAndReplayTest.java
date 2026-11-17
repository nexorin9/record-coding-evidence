package com.chaosforge.recordcoding.app;

import com.chaosforge.recordcoding.domain.MedicalRecord;
import com.chaosforge.recordcoding.evidence.EvidenceRow;
import com.chaosforge.recordcoding.evidence.EvidenceSource;
import com.chaosforge.recordcoding.evidence.EvidenceType;
import com.chaosforge.recordcoding.export.ReviewRecordExporter;
import com.chaosforge.recordcoding.export.SuggestionTextBuilder;
import com.chaosforge.recordcoding.semantic.Attribution;
import com.chaosforge.recordcoding.semantic.SemanticFinding;
import com.chaosforge.recordcoding.semantic.Verdict;
import com.chaosforge.recordcoding.service.EvidenceCollector;
import com.chaosforge.recordcoding.service.ReviewService;
import com.chaosforge.recordcoding.store.AuditTrailDao;
import com.chaosforge.recordcoding.store.Database;
import com.chaosforge.recordcoding.support.Fixtures;
import com.chaosforge.recordcoding.transport.EmrProvider;
import com.chaosforge.recordcoding.transport.QidoProvider;
import com.chaosforge.recordcoding.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** id48 / id51 / id52 / id14：CLI 入口、离线回放、案例集、跑批、导出与截图采集。 */
class AppAndReplayTest {

    private static Path seed(Path dir) throws Exception {
        Files.createDirectories(dir.resolve("dicom"));
        Files.createDirectories(dir.resolve("hl7"));
        Files.createDirectories(dir.resolve("emr"));
        Files.createDirectories(dir.resolve("out"));
        for (int i = 1; i <= 2; i++) {
            Files.writeString(dir.resolve("dicom/study_000" + i + ".json"), Fixtures.dicomJson());
        }
        for (int i = 1; i <= 2; i++) {
            Files.writeString(dir.resolve("hl7/oru_000" + i + ".hl7"), Fixtures.oru());
        }
        for (int i = 1; i <= 3; i++) {
            Files.writeString(dir.resolve("emr/emr_000" + i + ".json"), Fixtures.emrJson());
        }
        return dir;
    }

    private static final class Capt {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8);
        String text() { return buf.toString(StandardCharsets.UTF_8); }
    }

    @Test
    void cliEntryKeepsVersionHelpAndUsageExitCodes() {
        Capt out = new Capt();
        Capt err = new Capt();
        assertEquals(App.EXIT_OK, App.run(new String[] {"--version"}, out.out, err.out));
        assertTrue(out.text().contains(App.PRODUCT_NAME));
        assertEquals(App.EXIT_OK, App.run(new String[] {"--help"}, out.out, err.out));
        assertEquals(App.EXIT_OK, App.run(new String[0], out.out, err.out));
        assertEquals(App.EXIT_USAGE, App.run(new String[] {"not-a-command"}, out.out, err.out));
        assertTrue(err.text().contains("未知子命令"));
        assertEquals(Map.of("data", "data", "visit", "V1"),
                AppCli.parseOptions(new String[] {"--data", "data", "--visit", "V1"}));
    }

    @Test
    void configCheckAndConnCheckAreSafeToRunWithoutRealEndpoints() {
        Capt out = new Capt();
        int code = AppCli.configCheck(out.out);
        assertTrue(code == App.EXIT_OK || code == App.EXIT_PRECONDITION);
        assertTrue(out.text().contains("配置校验"));
        assertTrue(AppCli.connCheck(new Capt().out) == App.EXIT_OK);
    }

    @Test
    void offlineReplayProducesNonEmptyTableAndCsv(@TempDir Path dir) throws Exception {
        Path data = seed(dir.resolve("data"));
        List<String> lines = new ArrayList<>();
        ReplayCommand.Result r = ReplayCommand.execute(data, "VISIT-TEST", lines::add);
        assertTrue(r.rows() > 15, "两路来源必须归一成对照表草案");
        assertEquals(r.lines().size() - 1, r.rows());
        assertEquals(ReplayCommand.EvidenceRowDraft.header(), r.lines().get(0));
        assertTrue(r.lines().stream().anyMatch(l -> l.contains("PACS·QIDO-RS")));
        assertTrue(r.lines().stream().anyMatch(l -> l.contains("RIS·HL7-MLLP")));
        assertTrue(r.lines().stream().anyMatch(l -> l.contains("MSG-TEST-001") || l.contains("unmapped")));
        assertTrue(Files.exists(r.csv()));
        assertEquals(r.lines().size(), Files.readAllLines(r.csv(), StandardCharsets.UTF_8).size());
        assertEquals("VISIT-000001", ReplayCommand.defaultVisit(data));
        assertThrows(IllegalStateException.class,
                () -> ReplayCommand.execute(dir.resolve("no-data"), "V", lines::add));
    }

    @Test
    void projectCaseSuiteHitsEveryCase() throws Exception {
        List<CaseSuiteRunner.CaseResult> results = CaseSuiteRunner.execute(Path.of("cases"));
        assertFalse(results.isEmpty());
        List<String> misses = results.stream().filter(r -> !r.hit()).map(CaseSuiteRunner.CaseResult::caseId).toList();
        assertTrue(misses.isEmpty(), "案例集不符：" + misses);
        assertEquals(14, results.size());
    }

    @Test
    void batchRunProducesConsistentRowsAndThroughput(@TempDir Path dir) throws Exception {
        BatchRunner.Summary s = BatchRunner.execute(seed(dir.resolve("data")));
        assertEquals(5, s.rows());
        assertTrue(s.consistency() >= 0.99, "归一一致率必须 ≥ 99%");
        assertTrue(s.throughput() > 0);
        assertTrue(s.elapsedMillis() >= 1);
        assertTrue(s.deviations().isEmpty());
    }

    @Test
    void captureWritesThreeRealRuntimeImages(@TempDir Path dir) throws Exception {
        Path data = seed(dir.resolve("data"));
        Path out = dir.resolve("screenshots");
        assertEquals(App.EXIT_OK, ScreenshotCapture.run(
                new String[] {"--data", data.toString(), "--out", out.toString()}, new Capt().out, new Capt().out));
        assertTrue(Files.exists(out.resolve("terminal-replay.png")));
        assertTrue(Files.exists(out.resolve("main-window.png")));
        assertTrue(Files.exists(out.resolve("export-sample.png")));
        assertTrue(Files.size(out.resolve("terminal-replay.png")) > 1000);
        assertTrue(Files.size(out.resolve("main-window.png")) > 1000);
    }

    @Test
    void exportContainsEvidenceProvenanceAndAuditEvents(@TempDir Path dir) throws Exception {
        EvidenceRow row = EvidenceRow.present(EvidenceType.REPORT_CONCLUSION, EvidenceSource.HL7_MLLP,
                "报告结论", "OBX-5", "右下肺实变影", "ACC-1");
        SemanticFinding f = new SemanticFinding("F1", "J18900", List.of("J18900-建议复核"),
                Verdict.CONFLICT, Attribution.SIDE_MISSING, 0.9, List.of(row.evidenceId()),
                SemanticFinding.ConfirmState.ACCEPTED, "");
        f.confirm("编码员A", "2026-11-15T09:20:31Z");
        ReviewRecordExporter.ExportRequest req = new ReviewRecordExporter.ExportRequest(
                "VISIT-TEST", "B1", List.of(f), Map.of(row.evidenceId(), row), "质控员", "全量");
        try (Database db = Database.inMemory()) {
            AuditTrailDao audit = new AuditTrailDao(db.connection());
            ReviewRecordExporter exporter = new ReviewRecordExporter(audit);
            assertEquals(ReviewRecordExporter.csvHeader(), exporter.csv(req).lines().findFirst().orElseThrow());
            String csv = exporter.csv(req);
            assertTrue(csv.contains("J18900"));
            assertTrue(csv.contains("OBX-5"));
            assertTrue(csv.contains("编码员A"));
            assertTrue(csv.contains("conflict"));
            Map<String, Object> json = Json.object(exporter.json(req));
            assertEquals("B1", json.get("batch_id"));
            assertEquals(1, ((List<?>) json.get("rows")).size());
            Path csvPath = exporter.exportCsv(req, dir.resolve("out/review.csv"));
            Path jsonPath = exporter.exportJson(req, dir.resolve("out/review.json"));
            assertTrue(Files.exists(csvPath));
            assertTrue(Files.exists(jsonPath));
            assertEquals(2, audit.count("B1"), "每次导出都留一条审计事件");
        }
        String text = new SuggestionTextBuilder().build(req);
        assertTrue(text.contains("住院号 VISIT-TEST"));
        assertTrue(text.contains("证据出处"));
        assertTrue(text.contains("编码结论由编码员确认"));
    }

    @Test
    void reviewServiceStopsAtHumanConfirmationAndNeverWritesToQc() {
        QidoProvider qido = q -> List.of(Fixtures.qidoFlat());
        EmrProvider emr = v -> MedicalRecord.fromEmr(Json.object(Fixtures.emrJson()));
        EvidenceCollector collector = new EvidenceCollector(qido, emr,
                () -> List.of(Map.of("accessionNumber", "ACC-TEST",
                        "report_conclusion", "右下肺实变影，考虑感染性病变")));
        ReviewService.ReviewRun run = new ReviewService(collector).review("VISIT-TEST");
        assertEquals(5, run.stages().size());
        assertTrue(run.reachedConfirmation());
        assertEquals("待确认", run.state());
        assertTrue(run.reason().contains("未发出任何 QC 写请求"));
        assertTrue(run.snapshot().size() > 10);
        assertEquals(64, run.snapshot().evidenceHash().length());
        assertTrue(run.stages().stream().anyMatch(s -> "三层校验".equals(s.name()) && s.ok()));
        assertTrue(run.findings().isEmpty(), "模型只出草稿，主路径不得自动采纳建议");
    }
}
