package com.chaosforge.recordcoding.semantic;

import java.util.ArrayList;
import java.util.List;

/** 豁免白名单前置过滤：命中规则 id 与说明写入判定记录并可回看。 */
public final class ExemptionWhitelist {
    public record Rule(String ruleId, String keyword, String explanation) {}
    public record FilterResult(boolean exempted, List<String> matchedRuleIds, String reason) {}

    private final List<Rule> rules = new ArrayList<>();

    public ExemptionWhitelist addRule(String ruleId, String keyword, String explanation) {
        rules.add(new Rule(ruleId, keyword, explanation));
        return this;
    }

    public static ExemptionWhitelist defaults() {
        return new ExemptionWhitelist().addRule("EX-NO-REPORT", "无影像报告",
                "院内已确认本次住院无影像报告，豁免证据缺失归因");
    }

    public FilterResult filter(String homeCode, String factText) {
        String text = (homeCode == null ? "" : homeCode) + " " + (factText == null ? "" : factText);
        List<String> hits = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (Rule r : rules) {
            if (text.contains(r.keyword())) {
                hits.add(r.ruleId());
                reasons.add(r.ruleId() + "：" + r.explanation());
            }
        }
        return new FilterResult(!hits.isEmpty(), hits, String.join("；", reasons));
    }

    public List<Rule> rules() { return List.copyOf(rules); }
}
