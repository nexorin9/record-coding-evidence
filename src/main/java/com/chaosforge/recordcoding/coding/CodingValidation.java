package com.chaosforge.recordcoding.coding;

import com.chaosforge.recordcoding.domain.CodeSystem;
import com.chaosforge.recordcoding.domain.MedicalRecord;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 三层编排：结构 → 字典映射 → 业务；收集全部结论，字典缺失显式标注缺失层。 */
public final class CodingValidation {
    public record Result(List<ValidationFinding> findings, List<String> layersExecuted,
                         List<String> missingLayers, boolean dictionaryUnavailable) {
        public List<ValidationFinding> failures() {
            return findings.stream().filter(ValidationFinding::isFailure).toList();
        }
    }

    private final Icd10StructureValidator icd10 = new Icd10StructureValidator();
    private final Icd9cm3StructureValidator icd9 = new Icd9cm3StructureValidator();
    private final BusinessValidator business = new BusinessValidator();
    private final CodeDictionary dictionary;

    public CodingValidation(CodeDictionary dictionary) { this.dictionary = dictionary; }

    public Result validate(MedicalRecord record) {
        List<ValidationFinding> findings = new ArrayList<>();
        List<String> executed = new ArrayList<>();
        Set<String> missing = new LinkedHashSet<>();

        // 第一层：结构校验（确定性，始终执行）
        executed.add("STRUCTURE");
        String mainCode = record.coding().mainDiagnosis().code().orElse("");
        findings.addAll(icd10.validate(mainCode));
        record.coding().procedures().forEach(p -> findings.addAll(icd9.validate(p.code().orElse(""))));
        record.coding().otherDiagnoses().forEach(d -> findings.addAll(icd10.validate(d.code().orElse(""))));

        // 第二层：字典映射（不可用时只标注缺失层，不产出存在性结论）
        DictionaryResult dr = dictionary.lookup(mainCode);
        if (dr.isUnavailable()) {
            missing.add("DICTIONARY");
            findings.add(ValidationFinding.fail("DICTIONARY_UNAVAILABLE", "DICTIONARY-v1", "INFO",
                    "字典不可用，未做编码存在性判定", "domain/dictionaries/医保编码规则_ICD与药品.md 第 1 节"));
        } else {
            executed.add("DICTIONARY");
            findings.add(ValidationFinding.ok("DICTIONARY_MAPPING", "DICTIONARY-v1",
                    "字典层结论：" + dr.state() + " " + dr.reason(),
                    "domain/dictionaries/医保编码规则_ICD与药品.md 第 1 节"));
        }

        // 第三层：业务校验（确定性，始终执行）
        executed.add("BUSINESS");
        findings.addAll(business.validate(record));

        return new Result(List.copyOf(findings), List.copyOf(executed), List.copyOf(missing),
                dr.isUnavailable());
    }

    public List<String> layersFor(CodeSystem system) {
        return system == CodeSystem.ICD10_MEDICARE
                ? List.of("STRUCTURE", "DICTIONARY", "BUSINESS") : List.of("STRUCTURE", "DICTIONARY", "BUSINESS");
    }
}
