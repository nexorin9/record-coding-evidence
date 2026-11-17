package com.chaosforge.recordcoding.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 统一错误码表：transport / protocol / modelgw / store / coding 各层共用一张可查询表。 */
public final class ErrorCodeTable {
    public enum Layer { CONFIG, TRANSPORT, PROTOCOL, MODELGW, CODING, STORE, UI, SERVICE }

    public record Entry(String code, String reason, boolean retryable, String action, Layer layer) {}

    private static final List<Entry> ALL = new ArrayList<>();
    private static final Map<String, Entry> INDEX = new LinkedHashMap<>();

    static {
        reg("CONFIG_MISSING", "缺少关键端点或令牌", false, "补齐环境变量后重启", Layer.CONFIG);
        reg("CONNECT_FAILED", "连接失败", true, "检查网络与目标服务地址", Layer.TRANSPORT);
        reg("TIMEOUT", "请求超时", true, "按退避序列重试，仍失败则转人工", Layer.TRANSPORT);
        reg("HTTP_4XX", "对端拒绝（4xx）", false, "检查字段 / 鉴权 / 状态，不重试", Layer.TRANSPORT);
        reg("HTTP_5XX", "对端服务异常（5xx）", true, "退避重试并携带同一幂等键", Layer.TRANSPORT);
        reg("STRUCTURE_INVALID", "响应结构校验失败", false, "核对字段规范，不重试", Layer.TRANSPORT);
        reg("TOKEN_MISSING", "凭据头为空", false, "补齐令牌，不回落匿名请求", Layer.TRANSPORT);
        reg("SEGMENT_TOO_SHORT", "HL7 段长 < 3", false, "修正上游报文，不跳段", Layer.PROTOCOL);
        reg("HL7_ESCAPE_INVALID", "HL7 转义序列非法", false, "核对转义规则", Layer.PROTOCOL);
        reg("FRAME_START_MISSING", "MLLP 帧缺起始字节", false, "检查分帧边界", Layer.PROTOCOL);
        reg("FRAME_TRUNCATED", "MLLP 帧结束字节截断", false, "等待半包或报可读错误", Layer.PROTOCOL);
        reg("FRAME_TOO_LONG", "MLLP 帧超过配置上限", false, "调大上限或拆包", Layer.PROTOCOL);
        reg("TAG_NOT_PRESENT", "DICOM tag 不存在", false, "显式标 missing，不填默认值", Layer.PROTOCOL);
        reg("PATH_NOT_RESOLVED", "嵌套属性路径未解析", false, "显式标 missing", Layer.PROTOCOL);
        reg("VALUE_EMPTY", "属性值为空", false, "显式标 missing", Layer.PROTOCOL);
        reg("VR_MISMATCH", "取值类型与 VR 不符", false, "显式标 missing", Layer.PROTOCOL);
        reg("TS_INVALID", "临床时刻非法或缺精度", false, "报错或显式降级，不取当前时间", Layer.PROTOCOL);
        reg("DICTIONARY_UNAVAILABLE", "字典不可用", false, "只出结构层结论", Layer.CODING);
        reg("DICTIONARY_NOT_CONFIGURED", "官方字典端点未配置", false, "标注 not_configured 并降级", Layer.CODING);
        reg("MODEL_UNPARSED", "模型返回不可解析", true, "按人工全量复核处理", Layer.MODELGW);
        reg("MODEL_UNREACHABLE", "模型服务不可达", true, "降级并禁用提交", Layer.MODELGW);
        reg("DEGRADED_MODE", "当前为降级模式", false, "提交按钮禁用，不回写状态", Layer.SERVICE);
        reg("SUBMIT_REJECTED", "提交闸门拒绝", false, "补齐确认 / 证据引用", Layer.SERVICE);
        reg("STATE_ILLEGAL_TRANSITION", "非法状态迁移", false, "拒绝改数据并记原状态与目标状态", Layer.STORE);
        reg("DUPLICATE_IDEMPOTENCY_KEY", "重复提交", false, "返回既有回执，不新建记录", Layer.STORE);
        reg("CONSISTENCY_HASH_MISMATCH", "证据快照与哈希不匹配", false, "列出不一致条目，不自动修", Layer.STORE);
        reg("PORT_IN_USE", "监听端口被占用", true, "记录错误码并可自动重绑", Layer.TRANSPORT);
        reg("NAK_RETRY_QUEUED", "NAK 消息进重试队列", true, "按消息控制号重投去重", Layer.TRANSPORT);
        reg("UNMAPPED_FIELD", "字段语义映射缺失", false, "标 unmapped，不猜", Layer.PROTOCOL);
        reg("EVIDENCE_MISSING", "缺少对应报告证据", false, "不出具语义判定", Layer.SERVICE);
        reg("BINDING_AMBIGUOUS", "住院号命中多个 PatientID", false, "拒绝出具判定并列出候选", Layer.SERVICE);
        reg("SOURCE_UNREACHABLE", "证据来源不可达", true, "标缺口，不静默当成无差异", Layer.SERVICE);
        reg("REJECT_REASON_REQUIRED", "驳回缺少理由", false, "补齐理由后再提交", Layer.UI);
    }

    private static void reg(String code, String reason, boolean retryable, String action, Layer layer) {
        ALL.add(new Entry(code, reason, retryable, action, layer));
        INDEX.putIfAbsent(code, ALL.get(ALL.size() - 1));
    }

    private ErrorCodeTable() {}

    public static Entry lookup(String code) { return INDEX.get(code); }

    public static List<Entry> all() { return List.copyOf(ALL); }

    public static Set<String> codes() { return new LinkedHashSet<>(INDEX.keySet()); }

    /** 用于自检：注册清单里出现重复码时返回重复项。 */
    public static List<String> duplicateCodes() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Entry e : ALL) counts.merge(e.code(), 1, Integer::sum);
        List<String> dup = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) if (e.getValue() > 1) dup.add(e.getKey());
        return dup;
    }

    public static String describe(String code) {
        Entry e = lookup(code);
        return e == null ? "未知错误码：" + code : e.code() + " / " + e.reason() + " / " + e.action();
    }
}
