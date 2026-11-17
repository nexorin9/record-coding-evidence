package com.chaosforge.recordcoding.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全部凭据位与端点从环境变量读取；缺任一关键项即 fail-fast，
 * 不回落 localhost / mock / example。默认启动路径只指向真实端点。
 */
public final class AppConfig {
    public static final List<String> REQUIRED = List.of(
            "PACS_BASE_URL", "PACS_QIDO_TOKEN", "RIS_MLLP_LISTEN_PORT",
            "EMR_BASE_URL", "EMR_API_TOKEN", "QC_BASE_URL", "QC_API_TOKEN",
            "MODEL_BASE_URL", "MODEL_API_KEY", "MODEL_NAME");

    public static final List<String> OPTIONAL = List.of(
            "MODEL_TIMEOUT_SECONDS", "HTTP_TIMEOUT_SECONDS", "HTTP_RETRY_MAX",
            "HTTP_BACKOFF_MILLIS", "NHSA_DICT_URL", "NHSA_DICT_TOKEN");

    private final Map<String, String> values;

    private AppConfig(Map<String, String> values) { this.values = values; }

    public static AppConfig fromEnv() { return from(System.getenv()); }

    public static AppConfig from(Map<String, String> env) {
        Map<String, String> v = new LinkedHashMap<>();
        for (String key : REQUIRED) {
            String s = env.get(key);
            if (s != null && !s.isBlank()) v.put(key, s.trim());
        }
        for (String key : OPTIONAL) {
            String s = env.get(key);
            if (s != null && !s.isBlank()) v.put(key, s.trim());
        }
        v.putIfAbsent("MODEL_TIMEOUT_SECONDS", "30");
        v.putIfAbsent("HTTP_TIMEOUT_SECONDS", "10");
        v.putIfAbsent("HTTP_RETRY_MAX", "3");
        v.putIfAbsent("HTTP_BACKOFF_MILLIS", "200");
        return new AppConfig(v);
    }

    public String get(String key) { return values.get(key); }

    public boolean has(String key) { return values.containsKey(key) && !values.get(key).isBlank(); }

    public String require(String key) {
        String v = get(key);
        if (v == null || v.isBlank()) throw new ConfigException(List.of(key));
        return v;
    }

    public int getInt(String key, int fallback) {
        try { return Integer.parseInt(get(key)); } catch (Exception e) { return fallback; }
    }

    public List<String> missingRequired() {
        return REQUIRED.stream().filter(k -> !has(k)).toList();
    }

    public void validateRequired() {
        List<String> missing = missingRequired();
        if (!missing.isEmpty()) throw new ConfigException(missing);
    }

    public String pacsBaseUrl() { return require("PACS_BASE_URL"); }
    public String pacsQidoToken() { return require("PACS_QIDO_TOKEN"); }
    public int risMllpListenPort() { return getInt("RIS_MLLP_LISTEN_PORT", 2575); }
    public String emrBaseUrl() { return require("EMR_BASE_URL"); }
    public String emrApiToken() { return require("EMR_API_TOKEN"); }
    public String qcBaseUrl() { return require("QC_BASE_URL"); }
    public String qcApiToken() { return require("QC_API_TOKEN"); }
    public String modelBaseUrl() { return require("MODEL_BASE_URL"); }
    public String modelApiKey() { return require("MODEL_API_KEY"); }
    public String modelName() { return require("MODEL_NAME"); }
    public int modelTimeoutSeconds() { return getInt("MODEL_TIMEOUT_SECONDS", 30); }
    public int httpTimeoutSeconds() { return getInt("HTTP_TIMEOUT_SECONDS", 10); }
    public int httpRetryMax() { return getInt("HTTP_RETRY_MAX", 3); }
    public int httpBackoffMillis() { return getInt("HTTP_BACKOFF_MILLIS", 200); }
    public String dictionaryUrl() { return get("NHSA_DICT_URL"); }
    public String dictionaryToken() { return get("NHSA_DICT_TOKEN"); }

    public Map<String, String> presentKeys() { return Map.copyOf(values); }
}
