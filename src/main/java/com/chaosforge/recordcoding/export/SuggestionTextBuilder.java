package com.chaosforge.recordcoding.export;

import com.chaosforge.recordcoding.evidence.EvidenceRow;

import java.util.ArrayList;
import java.util.List;

/** 可直接粘贴进病案系统的编码修改建议文本：院内口语，不含模型黑话与内部方法名。 */
public final class SuggestionTextBuilder {
    public String build(ReviewRecordExporter.ExportRequest r) {
        List<String> lines = new ArrayList<>();
        lines.add("住院号 " + r.visitNo() + " 编码复核意见（批次 " + r.batchId() + "）");
        lines.add("说明：以下为待确认建议，编码员逐条核对后再录入病案系统。");
        int i = 1;
        for (var f : r.findings()) {
            if (f.isPromptOnly()) {
                lines.add(i + ". 提示：" + f.exemptionReason() + "（不生成编码修改建议）");
                i++;
                continue;
            }
            lines.add(i + ". 首页编码 " + f.homeCode() + " → 建议复核编码 "
                    + String.join("、", f.suggestedCodes())
                    + "；判定：" + f.verdict().label() + "；原因：" + f.attribution().label());
            List<String> refs = new ArrayList<>();
            for (String id : f.evidenceRefs()) {
                EvidenceRow e = r.evidenceById().get(id);
                if (e != null) refs.add(e.source().label() + " " + e.fieldName()
                        + "（哈希 " + e.hash().substring(0, 8) + "）");
            }
            lines.add("   证据出处：" + (refs.isEmpty() ? "无" : String.join("；", refs)));
            lines.add("   状态：" + (f.confirmedBy().isBlank() ? "待确认" : "已由 " + f.confirmedBy() + " 确认")
                    + "；请核对后决定是否采纳。");
            i++;
        }
        lines.add("本工具不给出诊断或治疗建议，编码结论由编码员确认并承担责任。");
        return String.join("\n", lines);
    }
}
