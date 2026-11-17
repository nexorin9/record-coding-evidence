package com.chaosforge.recordcoding.coding;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 字典层可插拔：本地文件字典 / 在线查询 / 不可用三态。
 * 本地无字典时显式返回 dictionary_unavailable 并降级为只出结构层结论，
 * 严禁用模型记忆或硬编码名单判定「编码不存在」。
 */
public final class DictionaryLookup implements CodeDictionary {
    public enum Mode { LOCAL_FILE, ONLINE, UNAVAILABLE }

    private final Mode mode;
    private final Map<String, String> local = new LinkedHashMap<>();
    private final OnlineDictionaryProbe onlineProbe;
    private final String unavailableReason;

    private DictionaryLookup(Mode mode, OnlineDictionaryProbe probe, String reason) {
        this.mode = mode;
        this.onlineProbe = probe;
        this.unavailableReason = reason;
    }

    public static DictionaryLookup unavailable(String reason) {
        return new DictionaryLookup(Mode.UNAVAILABLE, null, reason);
    }

    public static DictionaryLookup unavailable() { return unavailable("本地无 ICD 明细字典"); }

    public static DictionaryLookup online(OnlineDictionaryProbe probe) {
        return new DictionaryLookup(Mode.ONLINE, probe, "");
    }

    /** 本地字典文件：UTF-8、行首 # 注释忽略、重复编码去重；加载失败降级为不可用而非报错退出。 */
    public static DictionaryLookup local(Path file) {
        DictionaryLookup d = new DictionaryLookup(Mode.LOCAL_FILE, null, "");
        try {
            if (file == null || !Files.exists(file)) return unavailable("字典文件不存在：" + file);
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) continue;
                String[] parts = s.split("[,\\t]", 2);
                if (parts.length == 0 || parts[0].isBlank()) continue;
                d.local.putIfAbsent(parts[0].trim().toUpperCase(), parts.length > 1 ? parts[1].trim() : "");
            }
            return d;
        } catch (IOException e) {
            return unavailable("字典加载异常：" + e.getMessage());
        }
    }

    public Mode mode() { return mode; }
    public int localSize() { return local.size(); }

    @Override
    public DictionaryResult lookup(String code) {
        String c = code == null ? "" : code.trim().toUpperCase();
        if (mode == Mode.UNAVAILABLE) return DictionaryResult.unavailable(unavailableReason);
        if (mode == Mode.LOCAL_FILE) {
            return local.containsKey(c) ? DictionaryResult.found(c, local.get(c)) : DictionaryResult.notFound(c);
        }
        OnlineDictionaryProbe.ProbeResult probe = onlineProbe.probe(c);
        if (!probe.reachable()) return DictionaryResult.unavailable(probe.reason());
        return probe.exists() ? DictionaryResult.found(c, probe.name()) : DictionaryResult.notFound(c);
    }
}
