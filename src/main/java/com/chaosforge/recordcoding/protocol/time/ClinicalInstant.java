package com.chaosforge.recordcoding.protocol.time;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 时刻 + 时区 + 精度三要素不可分离；缺精度时降级并显式标注原因。 */
public record ClinicalInstant(LocalDateTime moment, ZoneId zone, InstantPrecision precision,
                              String degradedReason) {
    public static ClinicalInstant of(LocalDateTime moment, ZoneId zone, InstantPrecision precision) {
        if (moment == null || zone == null || precision == null) {
            throw new IllegalArgumentException("时刻 / 时区 / 精度三要素缺一不可");
        }
        return new ClinicalInstant(moment, zone, precision, "");
    }

    public static ClinicalInstant degraded(LocalDateTime moment, ZoneId zone,
                                           InstantPrecision precision, String reason) {
        ClinicalInstant base = of(moment, zone, precision);
        return new ClinicalInstant(base.moment(), base.zone(), base.precision(), reason);
    }

    public boolean isDegraded() { return degradedReason != null && !degradedReason.isBlank(); }

    public String iso() {
        return moment.atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public String display() {
        return iso() + " [精度:" + precision.label() + "]" + (isDegraded() ? " [降级:" + degradedReason + "]" : "");
    }

    public boolean isBefore(ClinicalInstant other) {
        return moment.atZone(zone).toInstant().isBefore(other.moment.atZone(other.zone).toInstant());
    }
}
