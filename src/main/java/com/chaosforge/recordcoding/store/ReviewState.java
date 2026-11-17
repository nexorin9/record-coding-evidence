package com.chaosforge.recordcoding.store;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 批次状态机：合法迁移表显式枚举，非法迁移抛错并记错误码，不静默改数据。 */
public enum ReviewState {
    PENDING_REVIEW("待复核"), EVIDENCE_FETCHED("证据已取回"), JUDGED("已判定"),
    AWAITING_CONFIRMATION("待确认"), CONFIRMED("已确认"), SUBMITTED("已提交");

    private final String label;
    ReviewState(String label) { this.label = label; }
    public String label() { return label; }

    private static final Map<ReviewState, Set<ReviewState>> ALLOWED = new LinkedHashMap<>();
    static {
        ALLOWED.put(PENDING_REVIEW, Set.of(EVIDENCE_FETCHED));
        ALLOWED.put(EVIDENCE_FETCHED, Set.of(JUDGED));
        ALLOWED.put(JUDGED, Set.of(AWAITING_CONFIRMATION));
        ALLOWED.put(AWAITING_CONFIRMATION, Set.of(CONFIRMED));
        ALLOWED.put(CONFIRMED, Set.of(SUBMITTED));
        ALLOWED.put(SUBMITTED, Set.of());
    }

    public static boolean canTransition(ReviewState from, ReviewState to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static ReviewState transition(ReviewState from, ReviewState to) {
        if (!canTransition(from, to)) throw new IllegalTransitionException(from, to);
        return to;
    }

    public static List<ReviewState> legalPath() {
        return List.of(PENDING_REVIEW, EVIDENCE_FETCHED, JUDGED, AWAITING_CONFIRMATION, CONFIRMED, SUBMITTED);
    }

    /** 模型任何路径都不能把状态推到「已确认」：只有人工采纳 / 驳回动作可推进。 */
    public static ReviewState humanConfirm(ReviewState from, boolean allConfirmed) {
        if (from == AWAITING_CONFIRMATION && allConfirmed) return CONFIRMED;
        throw new IllegalTransitionException(from, CONFIRMED);
    }

    public static void assertModelCannotAdvance(ReviewState target) {
        if (target == CONFIRMED || target == SUBMITTED) {
            throw new IllegalTransitionException(AWAITING_CONFIRMATION, target);
        }
    }

    public static final class IllegalTransitionException extends RuntimeException {
        private final ReviewState from;
        private final ReviewState to;
        public IllegalTransitionException(ReviewState from, ReviewState to) {
            super("STATE_ILLEGAL_TRANSITION：非法迁移 " + (from == null ? "null" : from.label())
                    + " → " + (to == null ? "null" : to.label()) + "，不静默改数据");
            this.from = from;
            this.to = to;
        }
        public ReviewState from() { return from; }
        public ReviewState to() { return to; }
        public String errorCode() { return "STATE_ILLEGAL_TRANSITION"; }
    }
}
