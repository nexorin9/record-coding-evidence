package com.chaosforge.recordcoding.protocol.dicom;

import java.util.ArrayList;
import java.util.List;

/** 属性路径：tag 序列 + 序号（如 00081032/0/00080102），支持单层与多层嵌套寻址。 */
public final class DicomJsonPath {
    private final List<String> steps;
    private final String expression;

    private DicomJsonPath(List<String> steps, String expression) {
        this.steps = List.copyOf(steps);
        this.expression = expression;
    }

    public static DicomJsonPath parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("属性路径为空");
        }
        String[] parts = expression.trim().replace('.', '/').split("/");
        List<String> steps = new ArrayList<>();
        for (String p : parts) if (!p.isBlank()) steps.add(p.trim());
        if (steps.isEmpty()) throw new IllegalArgumentException("属性路径无有效步骤：" + expression);
        return new DicomJsonPath(steps, expression);
    }

    public static DicomJsonPath of(String tag) { return parse(tag); }

    public List<String> steps() { return steps; }
    public String expression() { return expression; }
    public String firstTag() { return steps.get(0); }
    public boolean isSingle() { return steps.size() == 1; }

    /** DICOM tag 固定 8 位十六进制；纯数字 tag（如 00100020）不得被当成序列序号。 */
    public static boolean isTag(String step) {
        return step != null && step.matches("[0-9A-Fa-f]{8}");
    }

    /** 序号是短纯数字（如 0 / 1 / 12），且必须排除 8 位十六进制 tag。 */
    public static boolean isIndex(String step) {
        return step != null && step.matches("\\d{1,7}") && !isTag(step);
    }
}
