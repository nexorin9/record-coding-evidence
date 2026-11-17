package com.chaosforge.recordcoding.coding;

/** 校验结论：规则版本与依据来源可追溯，每条结论不因首错即停而丢失。 */
public record ValidationFinding(String ruleId, String ruleVersion, String severity,
                                String message, String basis, boolean passed) {
    public static ValidationFinding ok(String ruleId, String ruleVersion, String message, String basis) {
        return new ValidationFinding(ruleId, ruleVersion, "INFO", message, basis, true);
    }

    public static ValidationFinding fail(String ruleId, String ruleVersion, String severity,
                                         String message, String basis) {
        return new ValidationFinding(ruleId, ruleVersion, severity, message, basis, false);
    }

    public boolean isFailure() { return !passed; }
}
