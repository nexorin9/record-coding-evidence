package com.chaosforge.recordcoding.store;

import com.chaosforge.recordcoding.evidence.EvidenceRow;
import com.chaosforge.recordcoding.evidence.EvidenceSnapshot;
import com.chaosforge.recordcoding.evidence.EvidenceSource;
import com.chaosforge.recordcoding.evidence.EvidenceType;
import com.chaosforge.recordcoding.semantic.Attribution;
import com.chaosforge.recordcoding.semantic.SemanticFinding;
import com.chaosforge.recordcoding.semantic.Verdict;
import com.chaosforge.recordcoding.transport.QcReceipt;
import com.chaosforge.recordcoding.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** id10 / id40 / id41 / id42 / id43：SQLite 七表、状态机、幂等、审计与一致性自检。 */
class StoreTest {

    private static EvidenceSnapshot snapshot(String batchId, String hash) {
        EvidenceRow row = EvidenceRow.present(EvidenceType.STUDY_METADATA, EvidenceSource.QIDO_RS,
                "患者标识", "00100020", "PID-1", "ACC-1");
        return new EvidenceSnapshot("VISIT-1", batchId, List.of(row), hash);
    }

    private static SemanticFinding finding() {
        return new SemanticFinding("F1", "J18900", List.of("J18900-建议复核"),
                Verdict.CONFLICT, Attribution.SIDE_MISSING, 0.9, List.of("E1"),
                SemanticFinding.ConfirmState.ACCEPTED, "");
    }

    @Test
    void inMemoryDatabaseCreatesAllSevenTablesAndIndexes() throws Exception {
        try (Database db = Database.inMemory()) {
            assertEquals(Database.SCHEMA_VERSION, db.schemaVersion());
            assertEquals(7, Database.TABLES.size());
            for (String table : Database.TABLES) assertTrue(db.tableExists(table), "缺表 " + table);
            assertDoesNotThrow(() -> db.usesIndex(
                    "SELECT * FROM review_batch WHERE visit_no='V1'", "idx_batch_visit"));
        }
    }

    @Test
    void fileDatabaseIsPersistentAcrossReopen(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("state.sqlite");
        try (Database db = Database.open(file)) {
            new BatchDao(db.connection()).insert("B1", "VISIT-1", "待复核", "h1");
        }
        assertTrue(java.nio.file.Files.exists(file));
        try (Database db = Database.open(file)) {
            assertEquals("待复核", new BatchDao(db.connection()).stateOf("B1"));
        }
    }

    @Test
    void daosRoundTripBatchSnapshotFindingConfirmationReceipt() throws Exception {
        try (Database db = Database.inMemory()) {
            var c = db.connection();
            BatchDao batches = new BatchDao(c);
            batches.insert("B1", "VISIT-1", "待复核", "h1");
            assertEquals(1, batches.byVisit("VISIT-1").size());
            batches.updateState("B1", "证据已取回");
            assertEquals("证据已取回", batches.stateOf("B1"));

            new EvidenceSnapshotDao(c).insertAll(snapshot("B1", "h1"));
            assertEquals(1, new EvidenceSnapshotDao(c).count("B1"));
            assertTrue(new EvidenceSnapshotDao(c).byBatch("B1").get(0).containsKey("content_hash"));

            new FindingDao(c).insert("B1", finding());
            assertEquals(1, new FindingDao(c).byBatch("B1").size());

            ConfirmationDao confirmations = new ConfirmationDao(c);
            confirmations.confirm("B1", "F1", "编码员A", "2026-11-15T09:20:31Z", "已采纳", "");
            assertEquals(1, confirmations.count("B1"));

            new ReceiptDao(c).insert("B1", new QcReceipt("R-1", "2026-11-15T09:21:00Z"), "h1");
            assertEquals(1, new ReceiptDao(c).count("B1"));
            assertTrue(new ReceiptDao(c).byBatch("B1").get(0).containsKey("evidence_hash"));
        }
    }

    @Test
    void confirmationRequiresActorTimeAndRejectReason() throws Exception {
        try (Database db = Database.inMemory()) {
            ConfirmationDao dao = new ConfirmationDao(db.connection());
            ConfirmationException a = assertThrows(ConfirmationException.class,
                    () -> dao.confirm("B1", "F1", "", "2026-11-15T09:20:31Z", "已采纳", ""));
            assertEquals("CONFIRM_FIELDS_MISSING", a.code());
            ConfirmationException b = assertThrows(ConfirmationException.class,
                    () -> dao.confirm("B1", "F1", "编码员A", "2026-11-15T09:20:31Z", "已驳回", ""));
            assertEquals("REJECT_REASON_REQUIRED", b.code());
            assertEquals(0, dao.count("B1"));
        }
    }

    @Test
    void idempotencyKeyReturnsExistingReceipt() throws Exception {
        try (Database db = Database.inMemory()) {
            IdempotencyDao dao = new IdempotencyDao(db.connection());
            assertTrue(dao.register("K1", "B1", "R-1"));
            assertEquals("R-1", dao.existingReceipt("K1"));
            assertFalse(dao.register("K1", "B1", "R-2"));
            assertEquals(1, dao.count("B1"));
        }
    }

    @Test
    void auditTrailIsAppendOnlyAndExportLeavesItsOwnEvent() throws Exception {
        try (Database db = Database.inMemory()) {
            AuditTrailDao dao = new AuditTrailDao(db.connection());
            dao.append("B1", AuditTrailDao.EventType.BATCH_CREATED, "编码员A",
                    Map.of("visit_no", "VISIT-1", "姓名", "张三", "phone", "13800138000"));
            dao.append("B1", AuditTrailDao.EventType.EVIDENCE_FETCHED, "system", Map.of("count", 10));
            assertEquals(2, dao.count("B1"));
            assertThrows(UnsupportedOperationException.class, dao::updateOrDelete);
            Map<String, Object> first = dao.byBatch("B1").get(0);
            assertFalse(String.valueOf(first.get("payload_json")).contains("张三"));
            assertFalse(String.valueOf(first.get("payload_json")).contains("13800138000"));
            assertFalse(String.valueOf(first.get("payload_json")).contains("姓名"));

            String json = dao.exportJson("B1", "质控员", "全量");
            assertEquals(3, dao.count("B1"), "导出动作本身必须留一条审计事件");
            assertTrue(Json.object(json).containsKey("events"));
            String csv = dao.exportCsv("B1", "质控员", "全量");
            assertEquals(4, dao.count("B1"));
            assertTrue(csv.startsWith("id,batch_id,event_type"));
            assertTrue(dao.byBatch("B1").stream()
                    .anyMatch(e -> "EXPORTED".equals(String.valueOf(e.get("event_type")))));
        }
    }

    @Test
    void deidentifiedWriteScrubsPayloadBeforePersisting() {
        String payload = DeidentifiedWrite.payload(Map.of("visit_no", "VISIT-1", "姓名", "张三",
                "note", "联系方式 13800138000"));
        assertFalse(payload.contains("张三"));
        assertFalse(payload.contains("13800138000"));
        assertTrue(payload.contains("VISIT-1"));
        assertEquals("联系电话 [已去标识]", DeidentifiedWrite.clean("联系电话 13800138000"));
    }

    @Test
    void stateMachineRejectsSkipsAndModelDrivenConfirmation() {
        assertEquals(6, ReviewState.legalPath().size());
        assertTrue(ReviewState.canTransition(ReviewState.PENDING_REVIEW, ReviewState.EVIDENCE_FETCHED));
        assertFalse(ReviewState.canTransition(ReviewState.PENDING_REVIEW, ReviewState.CONFIRMED));
        assertEquals(ReviewState.JUDGED,
                ReviewState.transition(ReviewState.EVIDENCE_FETCHED, ReviewState.JUDGED));
        ReviewState.IllegalTransitionException e = assertThrows(
                ReviewState.IllegalTransitionException.class,
                () -> ReviewState.transition(ReviewState.PENDING_REVIEW, ReviewState.SUBMITTED));
        assertEquals("STATE_ILLEGAL_TRANSITION", e.errorCode());
        assertEquals(ReviewState.PENDING_REVIEW, e.from());
        assertEquals(ReviewState.SUBMITTED, e.to());
        assertEquals(ReviewState.CONFIRMED,
                ReviewState.humanConfirm(ReviewState.AWAITING_CONFIRMATION, true));
        assertThrows(ReviewState.IllegalTransitionException.class,
                () -> ReviewState.humanConfirm(ReviewState.JUDGED, true));
        assertThrows(ReviewState.IllegalTransitionException.class,
                () -> ReviewState.assertModelCannotAdvance(ReviewState.CONFIRMED));
        assertThrows(ReviewState.IllegalTransitionException.class,
                () -> ReviewState.assertModelCannotAdvance(ReviewState.SUBMITTED));
        assertDoesNotThrow(() -> ReviewState.assertModelCannotAdvance(ReviewState.JUDGED));
    }

    @Test
    void consistencyCheckerListsButNeverRepairsInconsistencies() throws Exception {
        try (Database db = Database.inMemory()) {
            var c = db.connection();
            new BatchDao(c).insert("B1", "VISIT-1", "待复核", "h1");
            new EvidenceSnapshotDao(c).insertAll(snapshot("B1", "h1"));
            ConsistencyChecker checker = new ConsistencyChecker(c);
            assertTrue(checker.check("B1").isEmpty());
            assertTrue(checker.report("B1").contains("通过"));

            new BatchDao(c).insert("B2", "VISIT-2", "已提交", "declared-hash");
            new EvidenceSnapshotDao(c).insertAll(snapshot("B2", "stored-hash"));
            List<ConsistencyChecker.Issue> issues = checker.check("B2");
            assertTrue(issues.stream().anyMatch(i -> i.reason().contains("CONSISTENCY_HASH_MISMATCH")));
            assertTrue(issues.stream().anyMatch(i -> i.table().equals("receipt")));
            assertEquals(issues.size(), checker.check("B2").size(), "自检只列不一致，不自动修改数据");
            assertEquals("已提交", new BatchDao(c).stateOf("B2"), "自检不得改写批次状态");
        }
    }

    @Test
    void batchResumeSkipsAlreadyCompletedBatches() throws Exception {
        try (Database db = Database.inMemory()) {
            var c = db.connection();
            BatchResume resume = new BatchResume(c);
            assertTrue(resume.completedBatches().isEmpty());
            assertEquals(3, resume.resume(List.of("B1", "B2", "B3"), "VISIT-1"));
            assertEquals(3, resume.completedBatches().size());
            assertEquals(1, resume.resume(List.of("B1", "B2", "B4"), "VISIT-1"),
                    "B1/B2 已完成被跳过，仅新批次 B4 写入");
            assertTrue(resume.completedBatches().contains("B4"));
            assertEquals(0, resume.resume(List.of("B1", "B2", "B4"), "VISIT-1"),
                    "全部已完成时不再重复写入");
        }
    }
}
