package com.chaosforge.recordcoding.coding;

import java.util.List;

/** 可插拔结构规则接口：校验项、规则版本、依据来源、严重级。 */
public interface CodeStructureRule {
    String ruleId();
    String ruleVersion();
    String basis();
    List<ValidationFinding> validate(String code);
}
