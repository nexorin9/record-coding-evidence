package com.chaosforge.recordcoding.modelgw;

import com.chaosforge.recordcoding.config.ConfigException;

import java.util.List;
import java.util.Map;

/** 单一模型出口配置：base_url / api_key / model / timeout 全部来自环境变量，缺失即 fail-fast。 */
public final class GatewayConfig {
    public static final List<String> REQUIRED = List.of(
            "MODEL_BASE_URL", "MODEL_API_KEY", "MODEL_NAME", "MODEL_TIMEOUT_SECONDS");

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;

    public GatewayConfig(String baseUrl, String apiKey, String model, int timeoutSeconds) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
    }

    public static GatewayConfig fromEnv() { return from(System.getenv()); }

    public static GatewayConfig from(Map<String, String> env) {
        List<String> missing = REQUIRED.stream()
                .filter(k -> env.get(k) == null || env.get(k).isBlank()).toList();
        if (!missing.isEmpty()) throw new ConfigException(missing);
        int timeout;
        try { timeout = Integer.parseInt(env.get("MODEL_TIMEOUT_SECONDS")); }
        catch (NumberFormatException e) { throw new ConfigException(List.of("MODEL_TIMEOUT_SECONDS")); }
        return new GatewayConfig(env.get("MODEL_BASE_URL").trim(), env.get("MODEL_API_KEY").trim(),
                env.get("MODEL_NAME").trim(), timeout);
    }

    public String baseUrl() { return baseUrl; }
    public String apiKey() { return apiKey; }
    public String model() { return model; }
    public int timeoutSeconds() { return timeoutSeconds; }
}
