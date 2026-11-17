package com.chaosforge.recordcoding.semantic;

import java.util.List;

/** 判定草稿：附置信度、证据引用与是否由确定性路径产生；置信度不足只提示不生成建议。 */
public record Judgement(Verdict verdict, double confidence, List<String> evidenceRefs,
                        String note, boolean advise, boolean deterministic) {
    public boolean hasEvidence() { return evidenceRefs != null && !evidenceRefs.isEmpty(); }
}
