package com.chaosforge.recordcoding.service;

import com.chaosforge.recordcoding.domain.MedicalRecord;
import com.chaosforge.recordcoding.transport.EmrProvider;
import com.chaosforge.recordcoding.transport.QidoProvider;
import com.chaosforge.recordcoding.transport.QidoQuery;
import com.chaosforge.recordcoding.transport.TransportException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** 三路证据来源编排：QIDO 检查元数据 + EMR 病历 + 本机已落证据快照；单源不可达不静默当成无差异。 */
public final class EvidenceCollector {
    public record CollectionResult(List<Map<String, Object>> qidoStudies, MedicalRecord record,
                                   List<Map<String, String>> snapshotReports,
                                   Map<String, SourceOutcome> outcomes,
                                   Map<String, String> reachabilityMatrix) {
        public boolean hasUnreachable() {
            return outcomes.values().stream().anyMatch(SourceOutcome::isUnreachable);
        }
    }

    private final QidoProvider qido;
    private final EmrProvider emr;
    private final Supplier<List<Map<String, String>>> snapshotSupplier;
    private final int perSourceTimeoutSeconds;

    public EvidenceCollector(QidoProvider qido, EmrProvider emr,
                             Supplier<List<Map<String, String>>> snapshotSupplier) {
        this(qido, emr, snapshotSupplier, 5);
    }

    public EvidenceCollector(QidoProvider qido, EmrProvider emr,
                             Supplier<List<Map<String, String>>> snapshotSupplier, int perSourceTimeoutSeconds) {
        this.qido = qido;
        this.emr = emr;
        this.snapshotSupplier = snapshotSupplier;
        this.perSourceTimeoutSeconds = perSourceTimeoutSeconds;
    }

    public CollectionResult collect(String visitNo) {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Map<String, SourceOutcome> outcomes = new LinkedHashMap<>();
        List<Map<String, Object>> studies = List.of();
        MedicalRecord record = null;
        try {
            Future<List<Map<String, Object>>> fq = pool.submit(() -> qido.fetch(new QidoQuery(visitNo)));
            Future<MedicalRecord> fe = pool.submit(() -> emr.fetch(visitNo));
            studies = await("QIDO", fq, outcomes, List.of());
            record = await("EMR", fe, outcomes, null);
        } finally {
            pool.shutdownNow();
        }
        List<Map<String, String>> reports = new ArrayList<>();
        try {
            List<Map<String, String>> fromSnapshot = snapshotSupplier == null ? List.of() : snapshotSupplier.get();
            if (fromSnapshot != null) reports.addAll(fromSnapshot);
            outcomes.put("LOCAL_SNAPSHOT", fromSnapshot == null || fromSnapshot.isEmpty()
                    ? SourceOutcome.empty("LOCAL_SNAPSHOT", "本机暂无该住院号的报告快照")
                    : SourceOutcome.ok("LOCAL_SNAPSHOT", fromSnapshot.size()));
        } catch (RuntimeException e) {
            outcomes.put("LOCAL_SNAPSHOT", SourceOutcome.error("LOCAL_SNAPSHOT", "SNAPSHOT_READ_FAILED", e.getMessage()));
        }
        Map<String, String> matrix = new LinkedHashMap<>();
        outcomes.forEach((k, v) -> matrix.put(k, v.status() + (v.errorCode().isBlank() ? "" : "/" + v.errorCode())));
        return new CollectionResult(studies, record, List.copyOf(reports),
                Map.copyOf(outcomes), Map.copyOf(matrix));
    }

    private <T> T await(String source, Future<T> future, Map<String, SourceOutcome> outcomes, T fallback) {
        try {
            T value = future.get(perSourceTimeoutSeconds, TimeUnit.SECONDS);
            if (value == null) {
                outcomes.put(source, SourceOutcome.empty(source, "来源返回空"));
                return fallback;
            }
            if (value instanceof List<?> l && l.isEmpty()) {
                outcomes.put(source, SourceOutcome.empty(source, "来源返回空集合"));
            } else {
                outcomes.put(source, SourceOutcome.ok(source, value instanceof List<?> l ? l.size() : 1));
            }
            return value;
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            outcomes.put(source, SourceOutcome.unreachable(source, "TIMEOUT", "单源超时 " + perSourceTimeoutSeconds + "s"));
            return fallback;
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            String code = cause instanceof TransportException te ? te.code().name() : "SOURCE_ERROR";
            outcomes.put(source, SourceOutcome.unreachable(source, code,
                    cause == null ? "来源异常" : String.valueOf(cause.getMessage())));
            return fallback;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outcomes.put(source, SourceOutcome.unreachable(source, "INTERRUPTED", "取回被中断"));
            return fallback;
        }
    }
}
