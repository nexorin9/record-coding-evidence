package com.chaosforge.recordcoding.protocol.hl7;

import java.util.List;

/** 已严格分词的 HL7 消息。 */
public final class Hl7Message {
    private final List<Hl7Segment> segments;
    private final Hl7Delimiter delimiter;

    public Hl7Message(List<Hl7Segment> segments, Hl7Delimiter delimiter) {
        this.segments = List.copyOf(segments);
        this.delimiter = delimiter;
    }

    public List<Hl7Segment> segments() { return segments; }
    public Hl7Delimiter delimiter() { return delimiter; }
    public int size() { return segments.size(); }

    public Hl7Segment first(String name) {
        for (Hl7Segment s : segments) if (s.name().equals(name)) return s;
        return null;
    }

    public List<Hl7Segment> all(String name) {
        return segments.stream().filter(s -> s.name().equals(name)).toList();
    }
}
