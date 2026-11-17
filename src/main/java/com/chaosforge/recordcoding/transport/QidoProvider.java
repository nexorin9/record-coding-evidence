package com.chaosforge.recordcoding.transport;

import java.util.List;
import java.util.Map;

/** 按患者标识取检查元数据（真实实现与测试替身实现同一接口）。 */
public interface QidoProvider {
    List<Map<String, Object>> fetch(QidoQuery query);
}
