package com.chaosforge.recordcoding.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 结构化日志：时间 / 级别 / 事件码 / 上下文；只落去标识字段，带体积上限与轮转。 */
public final class StructuredLog {
    public static final long MAX_BYTES = 1024L * 1024L;

    private StructuredLog() {}

    public static String line(String level, String eventCode, Map<String, String> context) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ts", Instant.now().toString());
        m.put("level", level);
        m.put("event", eventCode);
        Map<String, String> ctx = new LinkedHashMap<>();
        if (context != null) for (Map.Entry<String, String> e : context.entrySet()) {
            ctx.put(e.getKey(), Deidentifier.scrub(e.getValue()));
        }
        m.put("context", ctx);
        return Json.stringify(m);
    }

    public static synchronized void append(Path file, String level, String eventCode, Map<String, String> context)
            throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        rotate(file);
        Files.writeString(file, line(level, eventCode, context) + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** 超过上限时轮转为 .1，避免常驻程序写爆磁盘。 */
    public static void rotate(Path file) throws IOException {
        if (Files.exists(file) && Files.size(file) > MAX_BYTES) {
            Path backup = file.resolveSibling(file.getFileName().toString() + ".1");
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static List<String> read(Path file) throws IOException {
        return Files.exists(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : List.of();
    }
}
