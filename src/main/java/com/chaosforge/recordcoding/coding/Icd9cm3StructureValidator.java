package com.chaosforge.recordcoding.coding;

import java.util.ArrayList;
import java.util.List;

/** ICD-9-CM-3 医保版：全部由阿拉伯数字组成的 6 位码，四段衔接校验。 */
public final class Icd9cm3StructureValidator implements CodeStructureRule {
    public static final String RULE_VERSION = "ICD9CM3-医保版-结构-v1";
    public static final String BASIS = "domain/dictionaries/医保编码规则_ICD与药品.md 第 2 节";
    private static final String RULE_ID = "ICD9CM3_STRUCTURE";

    @Override public String ruleId() { return RULE_ID; }
    @Override public String ruleVersion() { return RULE_VERSION; }
    @Override public String basis() { return BASIS; }

    @Override
    public List<ValidationFinding> validate(String code) {
        List<ValidationFinding> out = new ArrayList<>();
        String c = code == null ? "" : code.trim();
        if (c.length() != 6) {
            out.add(ValidationFinding.fail(RULE_ID + "_LENGTH", RULE_VERSION, "ERROR",
                    "编码位数 " + c.length() + "，医保版 ICD-9-CM-3 应为 6 位纯数字", BASIS));
        }
        if (!c.isEmpty() && !c.matches("\\d+")) {
            out.add(ValidationFinding.fail(RULE_ID + "_CHARSET", RULE_VERSION, "ERROR",
                    "编码含非数字字符，医保版要求全数字（含前导零形态）", BASIS));
        }
        if (c.length() == 5) {
            out.add(ValidationFinding.fail(RULE_ID + "_EXTENSION_EARLY", RULE_VERSION, "ERROR",
                    "只有 5 位：区分码未完整出现，不得早于细目", BASIS));
        }
        if (c.length() == 6 && c.matches("\\d{6}")) {
            out.add(ValidationFinding.ok(RULE_ID, RULE_VERSION,
                    "结构合法：类目 " + c.substring(0, 2) + " / 亚目 " + c.charAt(2)
                            + " / 细目 " + c.charAt(3) + " / 区分码 " + c.substring(4, 6), BASIS));
        }
        if (out.isEmpty()) {
            out.add(ValidationFinding.fail(RULE_ID + "_SHAPE", RULE_VERSION, "ERROR",
                    "编码形态不合法", BASIS));
        }
        return out;
    }

    public boolean isStructurallyValid(String code) {
        return validate(code).stream().noneMatch(ValidationFinding::isFailure);
    }
}
