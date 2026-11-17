package com.chaosforge.recordcoding.protocol.dicom;

import com.chaosforge.recordcoding.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** tag + 嵌套路径取值；未命中一律返回带 MissingReason 的 missing，不静默填默认值。 */
public final class DicomJsonAttributeSelector {
    public record Selection(boolean present, String value, List<String> values,
                            MissingReason reason, String detail) {
        public boolean isMissing() { return !present; }
        public String display() { return present ? value : "missing(" + reason + ")"; }
    }

    private final DicomJsonModel model;

    public DicomJsonAttributeSelector(DicomJsonModel model) { this.model = model; }

    public static Selection present(String value, List<String> values) {
        return new Selection(true, value, List.copyOf(values), null, "");
    }

    public static Selection missing(MissingReason reason, String detail) {
        return new Selection(false, "", List.of(), reason, detail);
    }

    public Selection selectStringValue(String tag) { return selectValue(DicomJsonPath.of(tag)); }

    public List<String> selectValues(String tag) {
        Selection s = selectStringValue(tag);
        return s.present() ? s.values() : List.of();
    }

    @SuppressWarnings("unchecked")
    public Selection selectValue(DicomJsonPath path) {
        Map<String, Object> scope = model.root();
        Map<String, Object> element = null;
        List<String> steps = path.steps();
        for (int i = 0; i < steps.size(); i++) {
            String step = steps.get(i);
            if (DicomJsonPath.isIndex(step)) {
                if (element == null) return missing(MissingReason.PATH_NOT_RESOLVED, "序号前无序列元素：" + step);
                Object v = element.get("Value");
                int idx = Integer.parseInt(step);
                if (!(v instanceof List<?> list) || idx >= list.size() || !(list.get(idx) instanceof Map)) {
                    return missing(MissingReason.PATH_NOT_RESOLVED, "序列项序号越界：" + step);
                }
                scope = (Map<String, Object>) list.get(idx);
                element = null;
                continue;
            }
            if (!scope.containsKey(step)) {
                return missing(MissingReason.TAG_NOT_PRESENT, "路径 " + path.expression() + " 缺 tag " + step);
            }
            Object e = scope.get(step);
            if (!(e instanceof Map)) {
                return missing(MissingReason.VR_MISMATCH, "tag " + step + " 不是属性元素");
            }
            element = (Map<String, Object>) e;
            if (i + 1 < steps.size() && !DicomJsonPath.isIndex(steps.get(i + 1))) {
                Object v = element.get("Value");
                if (v instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Map) {
                    scope = (Map<String, Object>) l.get(0);
                } else {
                    return missing(MissingReason.PATH_NOT_RESOLVED, "tag " + step + " 无嵌套项可供继续寻址");
                }
            }
        }
        if (element == null) return missing(MissingReason.PATH_NOT_RESOLVED, "路径未指向最终属性：" + path.expression());
        Object raw = element.get("Value");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return missing(MissingReason.VALUE_EMPTY, "属性 Value 为空：" + path.expression());
        }
        Object first = list.get(0);
        if (first instanceof Map || first instanceof List) {
            return missing(MissingReason.VR_MISMATCH, "属性值是序列/对象而非标量：" + path.expression());
        }
        List<String> vals = new ArrayList<>();
        for (Object o : list) vals.add(String.valueOf(o));
        return present(vals.get(0), vals);
    }
}
