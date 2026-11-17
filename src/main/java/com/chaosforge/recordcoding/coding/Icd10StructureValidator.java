package com.chaosforge.recordcoding.coding;

import java.util.ArrayList;
import java.util.List;

/** ICD-10 医保版 6 位：1 字母 + 2 数字类目 / 1 位亚目 / 2 位区分码；22 章节 A00–Z99 + U 章。 */
public final class Icd10StructureValidator implements CodeStructureRule {
    public static final String RULE_VERSION = "ICD10-医保版-结构-v1";
    public static final String BASIS = "domain/dictionaries/医保编码规则_ICD与药品.md 第 1 节";
    private static final String RULE_ID = "ICD10_STRUCTURE";

    @Override public String ruleId() { return RULE_ID; }
    @Override public String ruleVersion() { return RULE_VERSION; }
    @Override public String basis() { return BASIS; }

    @Override
    public List<ValidationFinding> validate(String code) {
        List<ValidationFinding> out = new ArrayList<>();
        String c = code == null ? "" : code.trim().toUpperCase();
        if (c.length() != 6) {
            out.add(ValidationFinding.fail(RULE_ID + "_LENGTH", RULE_VERSION, "ERROR",
                    "编码位数 " + c.length() + "，医保版 ICD-10 应为 6 位", BASIS));
        }
        if (c.isEmpty() || !Character.isLetter(c.charAt(0))) {
            out.add(ValidationFinding.fail(RULE_ID + "_CATEGORY", RULE_VERSION, "ERROR",
                    "首位必须为字母（类目第 1 位）", BASIS));
        }
        if (c.length() >= 3 && !(Character.isDigit(c.charAt(1)) && Character.isDigit(c.charAt(2)))) {
            out.add(ValidationFinding.fail(RULE_ID + "_CATEGORY_DIGITS", RULE_VERSION, "ERROR",
                    "类目第 2–3 位必须为数字", BASIS));
        }
        if (c.length() >= 4 && !Character.isDigit(c.charAt(3))) {
            out.add(ValidationFinding.fail(RULE_ID + "_SUBCATEGORY", RULE_VERSION, "ERROR",
                    "第 4 位亚目必须为数字", BASIS));
        }
        if (c.length() >= 6 && !(Character.isDigit(c.charAt(4)) && Character.isDigit(c.charAt(5)))) {
            out.add(ValidationFinding.fail(RULE_ID + "_EXTENSION", RULE_VERSION, "ERROR",
                    "第 5–6 位区分码必须为数字", BASIS));
        }
        if (!c.isEmpty() && Character.isLetter(c.charAt(0))) {
            char chapter = c.charAt(0);
            boolean inRange = (chapter >= 'A' && chapter <= 'Z') || chapter == 'U';
            if (!inRange) {
                out.add(ValidationFinding.fail(RULE_ID + "_CHAPTER", RULE_VERSION, "ERROR",
                        "章节字母 " + chapter + " 越界（应落在 A00–Z99 + U 章）", BASIS));
            } else if (chapter == 'U') {
                out.add(ValidationFinding.ok(RULE_ID + "_CHAPTER_U", RULE_VERSION,
                        "U 章为合法特殊用途章节", BASIS));
            }
        }
        if (out.isEmpty()) {
            out.add(ValidationFinding.ok(RULE_ID, RULE_VERSION,
                    "结构合法：类目 " + c.substring(0, 3) + " / 亚目 " + c.charAt(3)
                            + " / 区分码 " + c.substring(4, 6), BASIS));
        }
        return out;
    }

    public boolean isStructurallyValid(String code) {
        return validate(code).stream().noneMatch(ValidationFinding::isFailure);
    }
}
