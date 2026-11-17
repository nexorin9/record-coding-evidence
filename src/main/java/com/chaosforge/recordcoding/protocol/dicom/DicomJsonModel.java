package com.chaosforge.recordcoding.protocol.dicom;

import com.chaosforge.recordcoding.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** DICOM JSON（PS3.18 F.2）最小取值模型：按 tag 取单值，未命中显式 missing。 */
public final class DicomJsonModel {
    private final Map<String, Object> root;

    @SuppressWarnings("unchecked")
    public DicomJsonModel(Map<String, Object> root) {
        this.root = root == null ? Map.of() : root;
    }

    public static DicomJsonModel parse(String json) { return new DicomJsonModel(Json.object(json)); }

    public Map<String, Object> root() { return root; }

    public boolean hasTag(String tag) { return root.containsKey(tag); }

    @SuppressWarnings("unchecked")
    public Map<String, Object> element(String tag) {
        Object e = root.get(tag);
        return e instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    public String vr(String tag) {
        Map<String, Object> e = element(tag);
        return e == null ? null : Json.str(e, "vr");
    }

    @SuppressWarnings("unchecked")
    public List<Object> rawValues(String tag) {
        Map<String, Object> e = element(tag);
        Object v = e == null ? null : e.get("Value");
        return v instanceof List<?> l ? new ArrayList<>((List<Object>) l) : List.of();
    }

    public List<String> values(String tag) {
        List<String> out = new ArrayList<>();
        for (Object o : rawValues(tag)) out.add(o instanceof Map || o instanceof List ? Json.stringify(o) : String.valueOf(o));
        return out;
    }

    /** 未命中返回 null，由调用方显式标注 missing（不填默认值）。 */
    public String singleValue(String tag) {
        List<String> v = values(tag);
        return v.isEmpty() ? null : v.get(0);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> items(String tag) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : rawValues(tag)) if (o instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        return out;
    }

    public String selectStringValue(String tag) { return singleValue(tag); }
}
