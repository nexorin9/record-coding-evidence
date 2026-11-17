package com.chaosforge.recordcoding.config;

import com.chaosforge.recordcoding.util.Json;
import com.chaosforge.recordcoding.util.SecureParsers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** HL7 字段语义映射外置配置；缺失角色一律返回 unmapped，不猜、不静默填默认值。 */
public final class FieldMapping {
    public static final String UNMAPPED = "unmapped";

    private final Map<String, String> roles;
    private final String version;

    private FieldMapping(Map<String, String> roles, String version) {
        this.roles = roles;
        this.version = version;
    }

    public static FieldMapping fromClasspath() {
        try (InputStream in = FieldMapping.class.getResourceAsStream("/hl7-field-mapping.json")) {
            if (in == null) throw new IllegalStateException("缺少 resources/hl7-field-mapping.json");
            return fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("读取字段映射失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static FieldMapping fromJson(String text) {
        SecureParsers.rejectDtd(text);
        Map<String, Object> root = Json.object(text);
        Map<String, String> roles = new LinkedHashMap<>();
        Object raw = root.get("roles");
        if (raw instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                roles.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        }
        String v = Json.str(root, "version");
        return new FieldMapping(roles, v == null ? "unversioned" : v);
    }

    public String mappingFor(String role) { return roles.get(role); }

    public boolean isUnmapped(String role) {
        String m = roles.get(role);
        return m == null || m.isBlank() || UNMAPPED.equals(m);
    }

    public String locator(String role) { return isUnmapped(role) ? UNMAPPED : roles.get(role); }

    public Set<String> roles() { return new LinkedHashSet<>(roles.keySet()); }

    public String version() { return version; }
}
