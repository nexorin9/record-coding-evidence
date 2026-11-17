package com.chaosforge.recordcoding.transport;

import com.chaosforge.recordcoding.domain.MedicalRecord;

/** 病历读取出站接口。 */
public interface EmrProvider {
    MedicalRecord fetch(String visitNo);
}
