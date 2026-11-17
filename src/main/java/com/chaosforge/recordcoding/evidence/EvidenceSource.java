package com.chaosforge.recordcoding.evidence;

/** 证据来源系统标识，逐行可回溯。 */
public enum EvidenceSource {
    QIDO_RS("PACS·QIDO-RS"), HL7_MLLP("RIS·HL7-MLLP"),
    EMR("EMR·病历"), LOCAL_SNAPSHOT("本机证据快照");

    private final String label;
    EvidenceSource(String label) { this.label = label; }
    public String label() { return label; }
}
