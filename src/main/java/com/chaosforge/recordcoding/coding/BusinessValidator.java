package com.chaosforge.recordcoding.coding;

import com.chaosforge.recordcoding.domain.HomePageCoding;
import com.chaosforge.recordcoding.domain.MedicalRecord;

import java.util.ArrayList;
import java.util.List;

/** 确定性业务规则：性别与诊断匹配、年龄限定与诊断匹配、诊断与操作匹配。 */
public final class BusinessValidator {
    public static final String RULE_VERSION = "BUSINESS-医保版-v1";
    public static final String BASIS = "domain/dictionaries/医保编码规则_ICD与药品.md 业务校验口径";

    public List<ValidationFinding> validate(MedicalRecord record) {
        List<ValidationFinding> out = new ArrayList<>();
        HomePageCoding coding = record.coding();
        String diagText = coding.mainDiagnosis().name().orElse("");
        String gender = record.gender().orElse("");
        int age = record.age().orElse(-1);

        if (!gender.isBlank()) {
            if (("女".equals(gender) || "F".equalsIgnoreCase(gender)) && diagText.contains("男性")) {
                out.add(ValidationFinding.fail("BUSINESS_GENDER", RULE_VERSION, "WARN",
                        "性别与诊断文本冲突：女性病历出现男性限定诊断", BASIS));
            } else if (("男".equals(gender) || "M".equalsIgnoreCase(gender)) && diagText.contains("女性")) {
                out.add(ValidationFinding.fail("BUSINESS_GENDER", RULE_VERSION, "WARN",
                        "性别与诊断文本冲突：男性病历出现女性限定诊断", BASIS));
            } else {
                out.add(ValidationFinding.ok("BUSINESS_GENDER", RULE_VERSION, "性别与诊断文本无冲突", BASIS));
            }
        } else {
            out.add(ValidationFinding.fail("BUSINESS_GENDER", RULE_VERSION, "INFO",
                    "性别字段缺失，跳过性别匹配", BASIS));
        }

        if (age >= 0) {
            if ((diagText.contains("新生儿") && age > 1) || (diagText.contains("儿童") && age > 18)
                    || (diagText.contains("老年") && age < 60)) {
                out.add(ValidationFinding.fail("BUSINESS_AGE", RULE_VERSION, "WARN",
                        "年龄限定与诊断文本不符（年龄 " + age + "）", BASIS));
            } else {
                out.add(ValidationFinding.ok("BUSINESS_AGE", RULE_VERSION, "年龄限定无冲突", BASIS));
            }
        } else {
            out.add(ValidationFinding.fail("BUSINESS_AGE", RULE_VERSION, "INFO",
                    "年龄字段缺失，跳过年龄限定", BASIS));
        }

        if (coding.mainDiagnosis().code().isMissing() && !coding.procedures().isEmpty()) {
            out.add(ValidationFinding.fail("BUSINESS_DIAG_PROC", RULE_VERSION, "ERROR",
                    "有手术操作编码但主要诊断编码缺失，诊断与操作不匹配", BASIS));
        } else {
            out.add(ValidationFinding.ok("BUSINESS_DIAG_PROC", RULE_VERSION, "诊断与操作匹配检查通过", BASIS));
        }
        return out;
    }
}
