package com.chaosforge.recordcoding.transport;

import com.chaosforge.recordcoding.domain.MedicalRecord;
import com.chaosforge.recordcoding.support.Fixtures;
import com.chaosforge.recordcoding.support.TestHttpServer;
import com.chaosforge.recordcoding.util.Hashes;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** id5 / id20 / id21 / id22 / id4：MLLP 分帧与去重、HTTP 五类错误码与真实端点客户端契约。 */
class MllpAndHttpTest {

    private static byte[] frame(String text) {
        return MllpBytes.wrap(text.getBytes(StandardCharsets.UTF_8));
    }

    /** HttpServer 回显头名大小写可能规范化，按忽略大小写取值。 */
    private static String header(Map<String, String> headers, String name) {
        return headers.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    @Test
    void mllpWrapAndFramerHandleSingleFrame() {
        byte[] payload = Fixtures.oru().getBytes(StandardCharsets.UTF_8);
        byte[] wrapped = MllpBytes.wrap(payload);
        assertEquals(MllpBytes.START, wrapped[0]);
        assertEquals(MllpBytes.END, wrapped[wrapped.length - 2]);
        assertEquals(MllpBytes.CR, wrapped[wrapped.length - 1]);
        MllpFramer.FramingResult r = new MllpFramer().decode(wrapped);
        assertFalse(r.hasError());
        assertEquals(1, r.frames().size());
        assertArrayEquals(payload, r.frames().get(0));
        assertEquals(0, r.remainder().length);
    }

    @Test
    void halfPacketStickyPacketAndFrameErrors() {
        byte[] a = frame("AAA");
        byte[] b = frame("BBB");
        byte[] joined = new byte[a.length + b.length];
        System.arraycopy(a, 0, joined, 0, a.length);
        System.arraycopy(b, 0, joined, a.length, b.length);
        MllpFramer framer = new MllpFramer();
        assertEquals(2, framer.decode(joined).frames().size());

        int cut = a.length / 2;
        byte[] first = java.util.Arrays.copyOfRange(a, 0, cut);
        byte[] second = java.util.Arrays.copyOfRange(a, cut, a.length);
        MllpFramer.FramingResult half = framer.decode(first);
        assertFalse(half.hasError());
        assertTrue(half.frames().isEmpty());
        assertEquals(first.length, half.remainder().length);
        assertEquals(1, framer.decode(second, half.remainder()).frames().size());

        assertEquals("FRAME_START_MISSING", framer.decode("junk".getBytes(StandardCharsets.UTF_8)).errorCode());
        assertEquals("FRAME_TRUNCATED", framer.decode(new byte[] {MllpBytes.START, 'A', MllpBytes.END}).errorCode());
        assertEquals("FRAME_TOO_LONG", new MllpFramer(2).decode(frame("LONGPAYLOAD")).errorCode());
    }

    @Test
    void ackPolicyAndRetryQueueDeduplicateByControlId() {
        assertArrayEquals(new byte[] {MllpBytes.ACK}, MllpAckPolicy.ackBytes(MllpAckPolicy.Mode.ACK));
        assertArrayEquals(new byte[] {MllpBytes.NAK}, MllpAckPolicy.ackBytes(MllpAckPolicy.Mode.NAK));
        assertEquals(0, MllpAckPolicy.ackBytes(MllpAckPolicy.Mode.NONE).length);
        MllpAckPolicy ack = new MllpAckPolicy(MllpAckPolicy.Mode.ACK);
        assertArrayEquals(new byte[] {MllpBytes.ACK}, ack.ackFor(true));
        assertArrayEquals(new byte[] {MllpBytes.NAK}, ack.ackFor(false));

        MllpRetryQueue q = new MllpRetryQueue();
        byte[] p = "payload".getBytes(StandardCharsets.UTF_8);
        assertTrue(q.offer(p, "MSG-1", "NAK"));
        assertFalse(q.offer(p, "MSG-1", "NAK"));
        assertEquals(1, q.size());
        assertEquals(1, q.dedupCount());
        assertEquals("MSG-1", q.pending().get(0).controlId());
        assertTrue(q.markLanded("MSG-1"));
        assertFalse(q.offer(p, "MSG-1", "重投"));
        q.clear();
        assertEquals(0, q.size());
    }

    @Test
    void mllpServerLandsSnapshotOncePerMessageControlId() throws Exception {
        ConnectionHealth health = new ConnectionHealth();
        MllpServer server = new MllpServer(0, StandardCharsets.UTF_8,
                new MllpAckPolicy(MllpAckPolicy.Mode.ACK), new MllpRetryQueue(), health);
        try {
            byte[] payload = Fixtures.oru().getBytes(StandardCharsets.UTF_8);
            assertTrue(server.land(payload));
            assertEquals(1, server.snapshotCount());
            assertTrue(server.land(payload));
            assertEquals(1, server.snapshotCount(), "MSH-10 相同不得重复入快照");
            assertEquals("MSG-TEST-001", server.snapshots().get(0).controlId());
            assertEquals("PID-TEST", server.snapshots().get(0).patientId());
            assertFalse(server.snapshots().get(0).conclusion().isBlank());
            assertEquals(1, health.pendingCount());
        } finally {
            server.close();
        }
    }

    @Test
    void httpTransportRetriesOnlyRetryableFailuresAndKeepsBackoffSequence() throws Exception {
        try (TestHttpServer s = new TestHttpServer((req, n) ->
                n < 3 ? new TestHttpServer.Reply(503, "{}") : new TestHttpServer.Reply(200, "{\"ok\":1}"))) {
            HttpTransport http = new HttpTransport(3, 2, 1L);
            HttpTransport.Response r = http.getJson(s.url("/qido/studies"), Map.of("Accept", "application/json"));
            assertEquals(200, r.status());
            assertEquals(3, s.requestCount());
            assertEquals(List.of(1L, 2L), http.lastBackoffSequence());
            assertEquals("GET", s.lastRequest().method());
            assertEquals("/qido/studies", s.lastRequest().path());
        }
        try (TestHttpServer s = TestHttpServer.always(new TestHttpServer.Reply(404, "{\"error\":\"no\"}"))) {
            HttpTransport http = new HttpTransport(3, 2, 0L);
            TransportException e = assertThrows(TransportException.class,
                    () -> http.getJson(s.url("/x"), Map.of()));
            assertEquals(TransportException.ErrorCode.HTTP_4XX, e.code());
            assertFalse(e.retryable());
            assertEquals(1, s.requestCount(), "4xx 不得重试");
        }
        try (TestHttpServer s = TestHttpServer.always(new TestHttpServer.Reply(500, "{}"))) {
            HttpTransport http = new HttpTransport(3, 1, 0L);
            TransportException e = assertThrows(TransportException.class,
                    () -> http.postJson(s.url("/x"), Map.of(), "{}"));
            assertEquals(TransportException.ErrorCode.HTTP_5XX, e.code());
            assertTrue(e.retryable());
            assertEquals(2, s.requestCount());
        }
    }

    @Test
    void bearerHeadersAndJsonValidationDoNotDegradeSilently() {
        Map<String, String> h = HttpTransport.bearerHeaders("token-1");
        assertEquals("Bearer token-1", h.get("Authorization"));
        assertEquals("application/json", h.get("Accept"));
        assertThrows(TransportException.class, () -> HttpTransport.bearerHeaders("  "));
        TransportException e = assertThrows(TransportException.class, () -> HttpTransport.requireJson(
                new HttpTransport.Response(200, "not-json", Map.of())));
        assertEquals(TransportException.ErrorCode.STRUCTURE_INVALID, e.code());
    }

    @Test
    void qidoClientOnlyRequestsEvidenceTagsNeverImagingPaths() throws Exception {
        HttpTransport http = new HttpTransport(3, 0, 0L);
        QidoRsClient client = new QidoRsClient(http, "https://pacs.internal/", "pacs-token");
        String url = client.buildUrl(new QidoQuery("PID-TEST"));
        assertTrue(url.startsWith("https://pacs.internal/qido/studies?PatientID=PID-TEST"));
        for (String tag : EvidenceTags.ALL) assertTrue(url.contains("includefield=" + tag));
        for (String forbidden : EvidenceTags.FORBIDDEN_PATHS) {
            assertFalse(url.toLowerCase().contains(forbidden.toLowerCase()), "不得出现取像路径 " + forbidden);
        }
        assertEquals("application/dicom+json", client.headers().get("Accept"));
        assertEquals("Bearer pacs-token", client.headers().get("Authorization"));

        try (TestHttpServer s = TestHttpServer.always(new TestHttpServer.Reply(200, "[" + Fixtures.dicomJson() + "]"))) {
            QidoRsClient local = new QidoRsClient(new HttpTransport(3, 0, 0L), s.baseUrl(), "t");
            List<Map<String, Object>> studies = local.fetch(new QidoQuery("PID-TEST"));
            assertEquals(1, studies.size());
            assertEquals("PID-000001", studies.get(0).get("00100020"));
            assertEquals("missing", studies.get(0).get("00080050"));
            assertEquals(EvidenceTags.ALL.size(), studies.get(0).size());
        }
    }

    @Test
    void emrAndQcClientsUseRealEndpointsWithBearerAndIdempotencyKey() throws Exception {
        try (TestHttpServer s = TestHttpServer.always(new TestHttpServer.Reply(200, Fixtures.emrJson()))) {
            EmrClient emr = new EmrClient(new HttpTransport(3, 0, 0L), s.baseUrl() + "/", "emr-token");
            assertTrue(emr.buildUrl("VISIT-TEST").endsWith("/api/v1/medical-records/VISIT-TEST"));
            MedicalRecord record = emr.fetch("VISIT-TEST");
            assertEquals("VISIT-TEST", record.visitNo().value());
            assertEquals("J18900", record.coding().mainCodeText());
            assertEquals("Bearer emr-token", header(s.lastRequest().headers(), "Authorization"));
        }
        String hash = Hashes.sha256Hex("evidence");
        try (TestHttpServer s = TestHttpServer.always(
                new TestHttpServer.Reply(200, "{\"id\":\"R-1\",\"received_at\":\"2026-11-15T00:00:00Z\"}"))) {
            QcReviewClient qc = new QcReviewClient(new HttpTransport(3, 0, 0L), s.baseUrl() + "/", "qc-token");
            assertTrue(qc.endpoint().endsWith("/api/v1/encoding-reviews"));
            assertEquals(Hashes.idempotencyKey("VISIT-TEST", "encoding-review", hash),
                    qc.headers("VISIT-TEST", "encoding-review", hash).get("Idempotency-Key"));
            String body = qc.body("VISIT-TEST", "B1", List.of(), hash);
            assertTrue(body.contains("VISIT-TEST"));
            assertTrue(body.contains(hash));
            QcReceipt receipt = qc.submit("VISIT-TEST", "B1", List.of(), hash);
            assertEquals("R-1", receipt.recordId());
            assertEquals("POST", s.lastRequest().method());
            assertEquals(Hashes.idempotencyKey("VISIT-TEST", "encoding-review", hash),
                    header(s.lastRequest().headers(), "Idempotency-Key"));
        }
    }
}
