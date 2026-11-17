package com.chaosforge.recordcoding.modelgw;

import java.util.Map;

/** 业务层唯一模型出口：结构化请求 → 结构化响应，不出现任何厂商 SDK 语义。 */
public interface ModelGateway {
    /** @param schemaName "finding"（判定建议契约）或 "fact"（诊断要素抽取契约） */
    Map<String, Object> chat(Map<String, Object> payload, String schemaName);
}
