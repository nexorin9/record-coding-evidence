package com.chaosforge.recordcoding.transport;

import java.util.List;

/** QIDO-RS 查询：患者标识 + 证据字段集 + 条数上限。 */
public record QidoQuery(String patientId, List<String> tags, int limit) {
    public QidoQuery(String patientId) { this(patientId, EvidenceTags.ALL, 100); }
}
