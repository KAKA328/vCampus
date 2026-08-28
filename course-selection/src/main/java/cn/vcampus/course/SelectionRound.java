package cn.vcampus.course;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 某个学期的一段选课时间窗口。
 *
 * <p>首修轮次由培养方案决定学生应修的必修、选修和跨专业课程；重修轮次则由学生的
 * 历史不及格记录决定。教学班不直接绑定某一个轮次，因此同一个教学班可以同时被首修
 * 必修生和重修生选择，并共同占用必修容量。</p>
 */
public final class SelectionRound implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String roundId;
    private final String term;
    private final SelectionRoundType type;
    private final LocalDateTime startsAt;
    private final LocalDateTime endsAt;
    private final SelectionRoundStatus status;

    public SelectionRound(String roundId, String term, SelectionRoundType type,
            LocalDateTime startsAt, LocalDateTime endsAt, SelectionRoundStatus status) {
        this.roundId = requireText(roundId, "roundId");
        this.term = requireText(term, "term");
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (startsAt == null || endsAt == null) {
            throw new IllegalArgumentException("selection round time must not be null");
        }
        if (!startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("startsAt must be before endsAt");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.type = type;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = status;
    }

    public String getRoundId() {
        return roundId;
    }

    public String getTerm() {
        return term;
    }

    public SelectionRoundType getType() {
        return type;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }

    public SelectionRoundStatus getStatus() {
        return status;
    }

    /**
     * 判断指定时刻能否接受选课请求。
     *
     * <p>状态必须为开放，且时刻位于开始和结束时间之间（包含两个端点）。</p>
     */
    public boolean isOpenAt(LocalDateTime time) {
        if (time == null) {
            throw new IllegalArgumentException("time must not be null");
        }
        return status == SelectionRoundStatus.OPEN
                && !time.isBefore(startsAt)
                && !time.isAfter(endsAt);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
