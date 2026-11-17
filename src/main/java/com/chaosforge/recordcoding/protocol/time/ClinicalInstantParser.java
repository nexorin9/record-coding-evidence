package com.chaosforge.recordcoding.protocol.time;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HL7 TS 与 DICOM DA/TM/DT 精度规则不同，各写一套；
 * 缺精度降级并显式标注原因，非法值抛类型化异常，不静默取默认值。
 */
public final class ClinicalInstantParser {
    private static final Pattern HL7_TZ = Pattern.compile("^(.*?)([+-])(\\d{2})(\\d{2})$");
    private static final Pattern DT_TZ = Pattern.compile("^(.*)&([+-])(\\d{2})(\\d{2})$");

    private ClinicalInstantParser() {}

    /** HL7 TS：YYYY[MM[DD[HH[MM[SS[.S[S[S[S]]]]]]]]][±ZZZZ]。 */
    public static ClinicalInstant parseHl7Ts(String raw) {
        String locator = "HL7 TS";
        if (raw == null || raw.isBlank()) throw invalid("空值", raw, locator);
        String s = raw.trim();
        ZoneId zone = ZoneId.systemDefault();
        Matcher tz = HL7_TZ.matcher(s);
        if (tz.matches()) {
            s = tz.group(1);
            int sign = "-".equals(tz.group(2)) ? -1 : 1;
            zone = ZoneOffset.ofHoursMinutes(sign * Integer.parseInt(tz.group(3)),
                    sign * Integer.parseInt(tz.group(4)));
        }
        String frac = "";
        int dot = s.indexOf('.');
        if (dot >= 0) {
            frac = s.substring(dot + 1);
            s = s.substring(0, dot);
            if (!frac.matches("\\d{1,4}")) throw invalid("小数秒非法", raw, locator);
        }
        if (!s.matches("\\d{4,14}")) throw invalid("TS 主体非法", raw, locator);
        int len = s.length();
        LocalDateTime moment = dateTime(padTo14(s), raw, locator);
        InstantPrecision precision = precisionFromDigits(len);
        if (len < 14) {
            return ClinicalInstant.degraded(moment, zone, precision,
                    "HL7 TS 仅 " + len + " 位，缺分量，按" + precision.label() + "精度降级");
        }
        return ClinicalInstant.of(moment, zone, precision);
    }

    /** DICOM DA：YYYYMMDD（日期，天精度）。 */
    public static ClinicalInstant parseDicomDa(String raw) {
        String locator = "DICOM DA";
        if (raw == null || !raw.matches("\\d{8}")) throw invalid("DA 必须为 8 位 YYYYMMDD", raw, locator);
        LocalDate d = date(raw, locator);
        return ClinicalInstant.of(d.atStartOfDay(), ZoneId.systemDefault(), InstantPrecision.DAY);
    }

    /** DICOM TM：HH[MM[SS[.FFFFFF]]]（时间，可带小数秒）。 */
    public static ClinicalInstant parseDicomTm(String raw) {
        String locator = "DICOM TM";
        if (raw == null || raw.isBlank()) throw invalid("空值", raw, locator);
        String s = raw.trim();
        int dot = s.indexOf('.');
        String frac = "";
        if (dot >= 0) {
            frac = s.substring(dot + 1);
            s = s.substring(0, dot);
            if (!frac.matches("\\d{1,6}")) throw invalid("小数秒非法", raw, locator);
        }
        if (!s.matches("\\d{2,6}")) throw invalid("TM 主体非法", raw, locator);
        int len = s.length();
        String padded = (s + "000000").substring(0, 6);
        int hh = Integer.parseInt(padded.substring(0, 2));
        int mm = Integer.parseInt(padded.substring(2, 4));
        int ss = Integer.parseInt(padded.substring(4, 6));
        LocalDateTime moment;
        try {
            moment = LocalDateTime.of(1970, 1, 1, hh, mm, ss);
        } catch (DateTimeException e) {
            throw invalid("时分秒越界", raw, locator);
        }
        InstantPrecision precision = len >= 6 ? InstantPrecision.SECOND
                : len >= 4 ? InstantPrecision.MINUTE : InstantPrecision.HOUR;
        if (len < 6) {
            return ClinicalInstant.degraded(moment, ZoneId.systemDefault(), precision,
                    "DICOM TM 仅 " + len + " 位，按" + precision.label() + "精度降级");
        }
        return ClinicalInstant.of(moment, ZoneId.systemDefault(), precision);
    }

    /** DICOM DT：YYYY[MM[DD[HH[MM[SS[.FFFFFF[&ZZXX]]]]]]]，缺分量按精度降级并标注。 */
    public static ClinicalInstant parseDicomDt(String raw) {
        String locator = "DICOM DT";
        if (raw == null || raw.isBlank()) throw invalid("空值", raw, locator);
        String s = raw.trim();
        ZoneId zone = ZoneId.systemDefault();
        Matcher tz = DT_TZ.matcher(s);
        if (tz.matches()) {
            s = tz.group(1);
            int sign = "-".equals(tz.group(2)) ? -1 : 1;
            zone = ZoneOffset.ofHoursMinutes(sign * Integer.parseInt(tz.group(3)),
                    sign * Integer.parseInt(tz.group(4)));
        }
        int dot = s.indexOf('.');
        if (dot >= 0) {
            String frac = s.substring(dot + 1);
            if (!frac.matches("\\d{1,6}")) throw invalid("小数秒非法", raw, locator);
            s = s.substring(0, dot);
        }
        if (!s.matches("\\d{4,14}")) throw invalid("DT 主体非法", raw, locator);
        int len = s.length();
        LocalDateTime moment = dateTime(padTo14(s), raw, locator);
        InstantPrecision precision = precisionFromDigits(len);
        if (len < 14) {
            return ClinicalInstant.degraded(moment, zone, precision,
                    "DICOM DT 仅 " + len + " 位，缺分量按" + precision.label() + "精度降级");
        }
        return ClinicalInstant.of(moment, zone, precision);
    }

    /** DA + TM 拼接为 DT。 */
    public static ClinicalInstant combineDaTm(String da, String tm) {
        String cleanDa = da == null ? "" : da.trim();
        String cleanTm = tm == null ? "" : tm.trim();
        if (!cleanDa.matches("\\d{8}")) throw invalid("DA 非法", cleanDa, "DA+TM");
        return parseDicomDt(cleanDa + cleanTm);
    }

    /** 缺分量按 HL7/DICOM 约定补月 / 日 / 时分秒，仅用于构造可比较时刻，精度另行标注。 */
    private static String padTo14(String digits) {
        StringBuilder b = new StringBuilder(digits);
        if (b.length() < 6) b.append("01");
        if (b.length() < 8) b.append("01");
        if (b.length() < 10) b.append("00");
        if (b.length() < 12) b.append("00");
        if (b.length() < 14) b.append("00");
        return b.substring(0, 14);
    }

    private static InstantPrecision precisionFromDigits(int len) {
        if (len >= 14) return InstantPrecision.SECOND;
        if (len >= 12) return InstantPrecision.MINUTE;
        if (len >= 10) return InstantPrecision.HOUR;
        return InstantPrecision.DAY;
    }

    private static LocalDateTime dateTime(String padded, String raw, String locator) {
        try {
            return LocalDateTime.of(
                    Integer.parseInt(padded.substring(0, 4)),
                    Integer.parseInt(padded.substring(4, 6)),
                    Integer.parseInt(padded.substring(6, 8)),
                    Integer.parseInt(padded.substring(8, 10)),
                    Integer.parseInt(padded.substring(10, 12)),
                    Integer.parseInt(padded.substring(12, 14)));
        } catch (DateTimeException | NumberFormatException e) {
            throw invalid("日期时间越界（月份 / 日 / 时分秒）", raw, locator);
        }
    }

    private static LocalDate date(String raw, String locator) {
        try {
            return LocalDate.of(Integer.parseInt(raw.substring(0, 4)),
                    Integer.parseInt(raw.substring(4, 6)), Integer.parseInt(raw.substring(6, 8)));
        } catch (DateTimeException | NumberFormatException e) {
            throw invalid("日期越界", raw, locator);
        }
    }

    private static ClinicalInstantException invalid(String message, String raw, String locator) {
        return new ClinicalInstantException(message, String.valueOf(raw), locator);
    }
}
