package com.chaosforge.recordcoding.protocol.hl7;

import com.chaosforge.recordcoding.config.FieldMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** ORU^R01 角色语义映射：外置映射表驱动，缺失角色标 unmapped，不猜、不静默填默认值。 */
public final class Orur01Mapper {
    public enum Status { OK, UNMAPPED, EMPTY, MULTI }

    public record MappedField(String role, String value, String locator, Status status, String note) {
        public boolean isUnmapped() { return status == Status.UNMAPPED; }
    }

    private static final Pattern LOCATOR = Pattern.compile("^([A-Z0-9]{3})-(\\d+)(?:\\.(\\d+))?$");

    private final FieldMapping mapping;

    public Orur01Mapper(FieldMapping mapping) { this.mapping = mapping; }

    public List<MappedField> map(Hl7Message message) {
        List<MappedField> out = new ArrayList<>();
        for (String role : mapping.roles()) out.add(mapRole(message, role));
        return out;
    }

    public MappedField mapRole(Hl7Message message, String role) {
        if (mapping.isUnmapped(role)) {
            return new MappedField(role, "", FieldMapping.UNMAPPED, Status.UNMAPPED,
                    "映射表未覆盖角色 " + role + "，不猜");
        }
        String locator = mapping.locator(role);
        Matcher m = LOCATOR.matcher(locator);
        if (!m.matches()) {
            return new MappedField(role, "", locator, Status.UNMAPPED, "映射位置格式非法：" + locator);
        }
        String segmentName = m.group(1);
        int fieldNo = Integer.parseInt(m.group(2));
        Hl7Segment seg = message.first(segmentName);
        if (seg == null || !seg.hasField(fieldNo)) {
            return new MappedField(role, "", locator, Status.EMPTY,
                    "报文缺少 " + locator + "，标 empty 而非 unmapped");
        }
        String raw = seg.field(fieldNo);
        if (raw.isBlank()) {
            return new MappedField(role, "", locator, Status.EMPTY,
                    locator + " 取值为空，标 empty 而非 unmapped");
        }
        List<String> reps = message.delimiter().repetitions(raw);
        String value = reps.isEmpty() ? "" : reps.get(0);
        Status status = reps.size() > 1 ? Status.MULTI : Status.OK;
        String note = status == Status.MULTI ? "同角色多值，取首条并标 multi（共 " + reps.size() + " 条）" : "";
        return new MappedField(role, value, locator, status, note);
    }

    /** MSH-10 消息控制号，供 MLLP 去重与证据快照幂等使用。 */
    public String messageControlId(Hl7Message message) {
        return mapRole(message, "message_control_id").value();
    }
}
