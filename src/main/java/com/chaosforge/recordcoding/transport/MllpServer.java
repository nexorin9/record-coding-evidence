package com.chaosforge.recordcoding.transport;

import com.chaosforge.recordcoding.config.FieldMapping;
import com.chaosforge.recordcoding.protocol.hl7.Hl7Message;
import com.chaosforge.recordcoding.protocol.hl7.Hl7Parser;
import com.chaosforge.recordcoding.protocol.hl7.Orur01Mapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/** 本机 MLLP 监听：监听线程只收包与分发，收到完整帧交协议层解析并落证据快照。 */
public final class MllpServer implements AutoCloseable {
    public record Snapshot(String controlId, byte[] payload, String patientId, String conclusion) {}

    private final int port;
    private final Charset charset;
    private final MllpFramer framer;
    private final MllpAckPolicy ackPolicy;
    private final MllpRetryQueue retryQueue;
    private final ConnectionHealth health;
    private final Orur01Mapper mapper;
    private final List<Snapshot> snapshots = new ArrayList<>();
    private volatile String lastErrorCode = "";
    private ServerSocket serverSocket;
    private Thread listener;
    private volatile boolean running;

    public MllpServer(int port, Charset charset, MllpAckPolicy ackPolicy,
                      MllpRetryQueue retryQueue, ConnectionHealth health) {
        this.port = port;
        this.charset = charset == null ? java.nio.charset.StandardCharsets.UTF_8 : charset;
        this.framer = new MllpFramer();
        this.ackPolicy = ackPolicy;
        this.retryQueue = retryQueue;
        this.health = health;
        this.mapper = new Orur01Mapper(FieldMapping.fromClasspath());
    }

    public void start() throws IOException {
        try {
            serverSocket = new ServerSocket(port, 16, InetAddress.getLoopbackAddress());
        } catch (IOException e) {
            lastErrorCode = "PORT_IN_USE";
            health.markUnavailable("PORT_IN_USE");
            throw new IOException("监听端口 " + port + " 不可用（PORT_IN_USE）：" + e.getMessage(), e);
        }
        running = true;
        health.markAvailable();
        listener = new Thread(this::acceptLoop, "mllp-listener");
        listener.setDaemon(true);
        listener.start();
    }

    private void acceptLoop() {
        while (running) {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(3000);
                byte[] pending = new byte[0];
                try (InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        byte[] chunk = java.util.Arrays.copyOf(buf, n);
                        MllpFramer.FramingResult r = framer.decode(chunk, pending);
                        if (r.hasError()) {
                            lastErrorCode = r.errorCode();
                            health.markDegraded(r.errorCode());
                            for (byte[] f : r.frames()) land(f);
                            pending = new byte[0];
                            break;
                        }
                        pending = r.remainder();
                        for (byte[] frame : r.frames()) {
                            boolean ok = land(frame);
                            byte[] ack = ackPolicy.ackFor(ok);
                            if (ack.length > 0) { out.write(ack); out.flush(); }
                            if (!ok) retryQueue.offer(frame, controlId(frame), "NAK_OR_STORE_FAILED");
                        }
                        if (n < buf.length) break;
                    }
                    if (pending.length > 0) {
                        lastErrorCode = "FRAME_TRUNCATED";
                        health.markDegraded("FRAME_TRUNCATED");
                    }
                }
            } catch (java.net.SocketTimeoutException ignored) {
                // 空闲连接超时，继续监听
            } catch (IOException e) {
                if (running) {
                    lastErrorCode = "CONNECT_FAILED";
                    health.markUnavailable("CONNECT_FAILED");
                }
            }
        }
    }

    /** 落证据快照：按 MSH-10 去重，不重复入库；返回是否成功落库。 */
    public synchronized boolean land(byte[] frame) {
        String controlId = controlId(frame);
        if (controlId != null && !controlId.isBlank()
                && snapshots.stream().anyMatch(s -> controlId.equals(s.controlId()))) {
            retryQueue.markLanded(controlId);
            return true;
        }
        try {
            Hl7Message msg = Hl7Parser.parse(new String(frame, charset));
            String pid = mapper.mapRole(msg, "patient_id").value();
            String conclusion = mapper.mapRole(msg, "report_conclusion").value();
            snapshots.add(new Snapshot(mapper.messageControlId(msg), frame.clone(), pid,
                    com.chaosforge.recordcoding.util.Deidentifier.scrub(conclusion)));
            retryQueue.markLanded(controlId);
            health.setPendingCount(snapshots.size());
            return true;
        } catch (RuntimeException e) {
            lastErrorCode = "HL7_PARSE_FAILED";
            return false;
        }
    }

    private String controlId(byte[] frame) {
        try { return mapper.messageControlId(Hl7Parser.parse(new String(frame, charset))); }
        catch (RuntimeException e) { return HashesLite.shortHash(frame); }
    }

    public synchronized List<Snapshot> snapshots() { return List.copyOf(snapshots); }
    public synchronized int snapshotCount() { return snapshots.size(); }
    public String lastErrorCode() { return lastErrorCode; }
    public ConnectionHealth health() { return health; }
    public MllpRetryQueue retryQueue() { return retryQueue; }

    @Override
    public void close() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) { /* 关闭即可 */ }
        if (listener != null) listener.interrupt();
        health.markUnavailable("LISTENER_STOPPED");
    }

    /** 仅用于无法解析的帧生成稳定标识，避免依赖完整报文。 */
    private static final class HashesLite {
        static String shortHash(byte[] b) { return com.chaosforge.recordcoding.util.Hashes.sha256Hex(b).substring(0, 16); }
    }
}
