package com.chaosforge.recordcoding.ui;

import com.chaosforge.recordcoding.evidence.EvidenceRow;
import com.chaosforge.recordcoding.evidence.EvidenceSource;
import com.chaosforge.recordcoding.evidence.EvidenceType;
import com.chaosforge.recordcoding.semantic.Attribution;
import com.chaosforge.recordcoding.semantic.SemanticFinding;
import com.chaosforge.recordcoding.semantic.Verdict;
import com.chaosforge.recordcoding.service.CodingAuditService;
import com.chaosforge.recordcoding.transport.ConnectionHealth;
import com.chaosforge.recordcoding.transport.QcReceipt;
import com.chaosforge.recordcoding.transport.QcReviewSink;
import com.chaosforge.recordcoding.ui.theme.BadgeRenderer;
import com.chaosforge.recordcoding.ui.theme.UiTheme;
import com.chaosforge.recordcoding.ui.theme.UiTokens;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** id39 / id44 / id45 / id46 / id47：提交闸门、三栏表、证据抽屉、托盘与三态。 */
class UiAndGateTest {

    private static SemanticFinding accepted(String id, List<String> refs) {
        SemanticFinding f = new SemanticFinding(id, "J18900", List.of("J18900-建议复核"),
                Verdict.CONFLICT, Attribution.SIDE_MISSING, 0.9, refs,
                SemanticFinding.ConfirmState.ACCEPTED, "");
        f.confirm("编码员A", "2026-11-15T09:20:31Z");
        return f;
    }

    private static SemanticFinding pending(String id, List<String> refs) {
        return new SemanticFinding(id, "J18900", List.of(), Verdict.CONFLICT,
                Attribution.SIDE_MISSING, 0.9, refs, SemanticFinding.ConfirmState.PENDING, "");
    }

    @Test
    void submitGateAllowsOnlyFullyConfirmedNonDegradedReview() {
        SubmitGate gate = new SubmitGate();
        SubmitGate.GateResult ok = gate.check(new SubmitGate.Review(
                List.of(accepted("F1", List.of("E1"))), false, "已确认"));
        assertTrue(ok.allowed());
        assertEquals("允许提交", ok.describe());

        assertFalse(gate.check(new SubmitGate.Review(
                List.of(accepted("F1", List.of("E1"))), true, "已确认")).allowed());
        assertFalse(gate.check(new SubmitGate.Review(
                List.of(pending("F1", List.of("E1"))), false, "待确认")).allowed());
        assertFalse(gate.check(new SubmitGate.Review(
                List.of(accepted("F1", List.of())), false, "已确认")).allowed());
        assertFalse(gate.check(new SubmitGate.Review(List.of(), false, "已确认")).allowed());
        assertFalse(gate.check(new SubmitGate.Review(
                List.of(accepted("F1", List.of("E1"))), false, "已提交")).allowed());
        assertFalse(gate.check(null).allowed());
    }

    @Test
    void degradedModeNeverIssuesAWriteRequest() {
        class CountingSink implements QcReviewSink {
            int calls;
            @Override public QcReceipt submit(String v, String b, List<Map<String, Object>> rows, String h) {
                calls++;
                return new QcReceipt("R-1", "2026-11-15T09:21:00Z");
            }
        }
        CountingSink sink = new CountingSink();
        CodingAuditService service = new CodingAuditService(sink);
        CodingAuditService.SubmitResult ok = service.submit("VISIT-1", "B1", "hash",
                List.of(accepted("F1", List.of("E1"))), "已确认");
        assertTrue(ok.submitted());
        assertEquals(1, sink.calls);
        assertEquals("R-1", ok.receipt().recordId());

        service.markModelUnavailable("MODEL_UNREACHABLE");
        assertTrue(service.isDegraded());
        assertTrue(service.degradeReason().contains("全量人工复核"));
        CodingAuditService.SubmitResult blocked = service.submit("VISIT-1", "B2", "hash",
                List.of(accepted("F2", List.of("E1"))), "已确认");
        assertFalse(blocked.submitted());
        assertEquals(1, sink.calls, "降级态禁止提交且不发 QC 写请求");
        assertTrue(blocked.reasons().stream().anyMatch(r -> r.contains("降级")));

        service.clearDegraded();
        assertFalse(service.isDegraded());
        assertTrue(service.submit("VISIT-1", "B3", "hash",
                List.of(accepted("F3", List.of("E1"))), "已确认").submitted());
    }

    @Test
    void uiStateCoversLoadingEmptyAndErrorTriples() {
        assertEquals(4, UiState.loadingStages().size());
        assertTrue(UiState.loadingText(UiState.LoadingStage.FETCH, 40).contains("取回证据"));
        assertTrue(UiState.loadingText(UiState.LoadingStage.FETCH, 40).contains("40%"));
        assertTrue(UiState.allEmptyTextsDistinct());
        assertTrue(UiState.emptyText(UiState.EmptyKind.DICTIONARY_UNAVAILABLE).contains("字典"));
        assertTrue(UiState.errorBanner("TIMEOUT", "请求超时").contains("建议"));
        assertTrue(UiState.errorBanner("CONFIG_MISSING", "缺配置").contains("环境变量"));
        assertTrue(UiState.errorBanner("DEGRADED_MODE", "模型不可达").contains("提交按钮已禁用"));
    }

    @Test
    void designTokensAreCentralizedAndSemantic() {
        assertEquals(java.awt.Color.decode("#1A6FD4"), UiTokens.ACCENT);
        assertEquals(java.awt.Color.decode("#C0392B"), UiTokens.CONFLICT);
        assertTrue(UiTokens.SPACING.contains(8));
        assertEquals(28, UiTokens.ROW_HEIGHT);
        assertEquals(4, UiTokens.FONT_CANDIDATES.size());
        assertEquals(UiTokens.CONFLICT, UiTokens.badgeColor("conflict"));
        assertEquals(UiTokens.PENDING, UiTokens.badgeColor("待确认"));
        assertEquals(UiTokens.EQUIVALENT, UiTokens.badgeColor("equivalent"));
        assertTrue(UiTokens.font(UiTokens.LEVEL_TITLE).getSize() > UiTokens.font(UiTokens.LEVEL_BODY).getSize());
        assertNotEquals(UiTokens.badgeBackground("conflict"), UiTokens.badgeBackground("equivalent"));
        assertDoesNotThrow(UiTheme::apply);
        assertNotNull(UiTheme.cardBorder());
        assertNotNull(UiTheme.focusBorder());
        assertNotEquals(BadgeRenderer.colorFor("conflict"), BadgeRenderer.colorFor("equivalent"));
    }

    @Test
    void comparisonTableCountersDrawerAndDetailPanelAreWired() {
        EvidenceRow row = EvidenceRow.present(EvidenceType.REPORT_CONCLUSION, EvidenceSource.HL7_MLLP,
                "报告结论", "OBX-5", "右下肺实变影", "ACC-1");
        SemanticFinding f = accepted("F1", List.of(row.evidenceId()));
        ComparisonPanel panel = new ComparisonPanel();
        panel.setFindings(List.of(f), Map.of(row.evidenceId(), row), 3, 2);
        assertEquals(1, panel.model().getRowCount());
        assertEquals(3, panel.model().getColumnCount());
        assertEquals("首页编码", panel.model().getColumnName(0));
        assertTrue(panel.model().isDifferenceRow(0));
        assertTrue(panel.model().rowAt(0).evidence().contains("OBX-5"));
        assertEquals(f, panel.model().rowForEvidenceId(row.evidenceId()).finding());
        assertEquals(3, panel.counters().studyCount());
        assertEquals(2, panel.counters().reportCount());
        assertEquals(1, panel.counters().differenceCount());
        assertEquals("差异数 1", panel.counters().differencesText());
        assertEquals(3, panel.table().getColumnCount());

        EvidenceDrawer drawer = new EvidenceDrawer();
        drawer.setEvidence(List.of(row), Map.of(row.evidenceId(), 1));
        assertEquals(1, drawer.itemCount());
        assertTrue(drawer.textAt(0).contains("已被引用 1 处"));
        drawer.setCollapsed(true);
        assertTrue(drawer.isCollapsed());
        assertFalse(drawer.isVisible());

        FindingDetailPanel detail = new FindingDetailPanel();
        assertFalse(detail.isRejectEnabled());
        detail.show(f);
        assertTrue(detail.detailText().contains("建议编码"));
        assertTrue(detail.detailText().contains("证据出处"));
        detail.setRejectReason("证据不足");
        assertTrue(detail.isRejectEnabled());
        detail.setRejectReason("  ");
        assertFalse(detail.isRejectEnabled());
    }

    @Test
    void actionFeedbackCoversSuccessAndFailureWithoutSilentChanges() {
        assertEquals(4, ActionFeedback.ACTIONS.size());
        assertEquals(8, ActionFeedback.allOutcomes().size());
        ActionFeedback.Feedback fail = ActionFeedback.of("提交", false, "对端拒绝");
        assertFalse(fail.success());
        assertFalse(fail.dataChanged());
        assertTrue(fail.message().contains("保持原样"));
        assertTrue(ActionFeedback.of("导出", true, "已完成").dataChanged());
    }

    @Test
    void trayIsDrawnProgrammaticallyAndReflectsConnectionHealth() {
        assertTrue(com.chaosforge.recordcoding.app.TrayIcon.isValid(
                com.chaosforge.recordcoding.app.TrayIcon.image(24)));
        assertFalse(com.chaosforge.recordcoding.app.TrayIcon.isValid(null));
        assertTrue(TrayController.createIcon(16).getWidth() == 16);
        ConnectionHealth health = new ConnectionHealth();
        assertEquals(ConnectionHealth.Status.UNAVAILABLE, health.status());
        health.markAvailable();
        assertEquals(ConnectionHealth.Status.AVAILABLE, health.status());
        health.setPendingCount(7);
        health.setSubmittedToday(3);
        health.markUnavailable("PORT_IN_USE");
        assertEquals("PORT_IN_USE", health.lastErrorCode());
        assertFalse(health.lastErrorTime().isBlank());
        assertTrue(health.tooltip().contains("待处理 7"));
        assertTrue(health.tooltip().contains("PORT_IN_USE"));
        health.markDegraded("MODEL_UNREACHABLE");
        assertEquals(ConnectionHealth.Status.DEGRADED, health.status());

        TrayController tray = new TrayController();
        assertTrue(tray.closeMinimizesToTray());
        assertFalse(tray.isInstalled(), "无图形界面环境不得声称已安装托盘");
        assertTrue(TrayController.tooltip(health, true).contains("降级模式"));
        assertFalse(TrayController.tooltip(null, false).isBlank());
    }
}
