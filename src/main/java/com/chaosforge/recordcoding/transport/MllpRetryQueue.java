package com.chaosforge.recordcoding.transport;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** NAK / 落库失败的原始帧入队；按 MSH-10 消息控制号去重，重投命中已落快照即丢弃。 */
public final class MllpRetryQueue {
    public record QueuedFrame(String controlId, byte[] payload, String reason) {}

    private final List<QueuedFrame> queue = new ArrayList<>();
    private final Set<String> seenControlIds = new LinkedHashSet<>();
    private final Set<String> landedControlIds = new LinkedHashSet<>();
    private int dedupCount;

    public synchronized boolean offer(byte[] payload, String controlId, String reason) {
        if (controlId != null && !controlId.isBlank()) {
            if (landedControlIds.contains(controlId)) { dedupCount++; return false; }
            if (!seenControlIds.add(controlId)) { dedupCount++; return false; }
        }
        queue.add(new QueuedFrame(controlId, payload == null ? new byte[0] : payload.clone(), reason));
        return true;
    }

    public synchronized boolean markLanded(String controlId) {
        if (controlId == null || controlId.isBlank()) return false;
        return landedControlIds.add(controlId);
    }

    public synchronized List<QueuedFrame> pending() { return List.copyOf(queue); }

    public synchronized int size() { return queue.size(); }

    public synchronized int dedupCount() { return dedupCount; }

    public synchronized void clear() { queue.clear(); }
}
