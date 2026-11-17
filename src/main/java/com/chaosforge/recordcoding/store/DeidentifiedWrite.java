package com.chaosforge.recordcoding.store;

import com.chaosforge.recordcoding.util.Deidentifier;
import com.chaosforge.recordcoding.util.Json;

/** 写入前剥离姓名 / 身份证 / 联系方式等患者可识别明细，保留住院号作为业务主键。 */
public final class DeidentifiedWrite {
    private DeidentifiedWrite() {}

    public static String clean(String text) { return Deidentifier.scrub(text); }

    public static String payload(java.util.Map<String, Object> fields) {
        return Json.stringify(Deidentifier.scrubMap(fields));
    }
}
