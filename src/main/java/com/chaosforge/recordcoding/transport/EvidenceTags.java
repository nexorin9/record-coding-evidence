package com.chaosforge.recordcoding.transport;

import java.util.List;

/** 证据所需 DICOM tag 清单集中一处定义，URL 与解析共用，禁止各处硬编码。 */
public final class EvidenceTags {
    public static final String PATIENT_ID = "00100020";
    public static final String STUDY_INSTANCE_UID = "0020000D";
    public static final String SERIES_INSTANCE_UID = "0020000E";
    public static final String ACCESSION_NUMBER = "00080050";
    public static final String STUDY_ID = "00200010";
    public static final String SERIES_DESCRIPTION = "0008103E";
    public static final String MODALITY = "00080060";
    public static final String STUDY_DATE = "00080020";
    public static final String STUDY_TIME = "00080030";
    public static final String PROCEDURE_CODE_SEQUENCE = "00081032";

    public static final List<String> ALL = List.of(
            PATIENT_ID, STUDY_INSTANCE_UID, SERIES_INSTANCE_UID, ACCESSION_NUMBER, STUDY_ID,
            SERIES_DESCRIPTION, MODALITY, STUDY_DATE, STUDY_TIME, PROCEDURE_CODE_SEQUENCE);

    /** 明确禁止的取像路径，供请求断言使用。 */
    public static final List<String> FORBIDDEN_PATHS = List.of("wado", "/instances", "/series/", "/frames", "pixel");

    private EvidenceTags() {}
}
