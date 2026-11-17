package com.chaosforge.recordcoding.transport;

import java.util.List;
import java.util.Map;

/** 复核记录写侧接口（真实实现与测试替身实现同一接口）。 */
public interface QcReviewSink {
    QcReceipt submit(String visitNo, String batchId, List<Map<String, Object>> findings, String evidenceHash);
}
