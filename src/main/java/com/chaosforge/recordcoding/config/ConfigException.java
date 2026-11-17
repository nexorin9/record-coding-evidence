package com.chaosforge.recordcoding.config;

import java.util.List;

/** 缺关键端点或令牌时抛出，连同缺失变量名清单，用于 fail-fast 与 config-check。 */
public class ConfigException extends RuntimeException {
    private final List<String> missingVariables;

    public ConfigException(List<String> missingVariables) {
        super("缺少关键配置项：" + String.join(", ", missingVariables));
        this.missingVariables = List.copyOf(missingVariables);
    }

    public ConfigException(String message) {
        super(message);
        this.missingVariables = List.of();
    }

    public List<String> missingVariables() { return missingVariables; }

    public String missingList() { return String.join(", ", missingVariables); }
}
