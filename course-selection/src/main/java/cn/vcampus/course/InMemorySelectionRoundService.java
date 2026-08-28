package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用于开发和测试的内存选课轮次管理服务。
 *
 * <p>程序关闭后数据会丢失。后续接入 Access 数据库时，应保持
 * {@link SelectionRoundService} 接口不变。</p>
 */
public final class InMemorySelectionRoundService implements SelectionRoundService {
    private final Map<String, SelectionRound> roundsById;

    public InMemorySelectionRoundService() {
        this(Collections.<SelectionRound>emptyList());
    }

    /**
     * 使用已有轮次创建服务，便于测试或加载演示数据。
     */
    public InMemorySelectionRoundService(List<SelectionRound> rounds) {
        if (rounds == null) {
            throw new IllegalArgumentException("rounds must not be null");
        }
        this.roundsById = new LinkedHashMap<String, SelectionRound>();
        for (SelectionRound round : rounds) {
            if (round == null) {
                throw new IllegalArgumentException("rounds must not contain null");
            }
            if (roundsById.put(round.getRoundId(), round) != null) {
                throw new IllegalArgumentException("duplicate roundId: " + round.getRoundId());
            }
        }
    }

    @Override
    public synchronized ServiceResult<SelectionRound> create(SelectionRound round) {
        if (round == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "round must not be null");
        }
        if (roundsById.containsKey(round.getRoundId())) {
            return ServiceResult.failure(StatusCode.CONFLICT, "selection round already exists");
        }
        roundsById.put(round.getRoundId(), round);
        return ServiceResult.ok(round);
    }

    @Override
    public synchronized ServiceResult<SelectionRound> findById(String roundId) {
        String normalizedRoundId = normalize(roundId);
        if (normalizedRoundId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "roundId must not be blank");
        }
        SelectionRound round = roundsById.get(normalizedRoundId);
        if (round == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "selection round not found");
        }
        return ServiceResult.ok(round);
    }

    @Override
    public synchronized ServiceResult<List<SelectionRound>> listByTerm(String term) {
        String normalizedTerm = normalize(term);
        if (normalizedTerm == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "term must not be blank");
        }
        List<SelectionRound> rounds = new ArrayList<SelectionRound>();
        for (SelectionRound round : roundsById.values()) {
            if (normalizedTerm.equals(round.getTerm())) {
                rounds.add(round);
            }
        }
        return ServiceResult.ok(Collections.unmodifiableList(rounds));
    }

    @Override
    public synchronized ServiceResult<List<SelectionRound>> listOpenRounds(String term,
            LocalDateTime time) {
        String normalizedTerm = normalize(term);
        if (normalizedTerm == null || time == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "term and time must not be null");
        }
        List<SelectionRound> rounds = new ArrayList<SelectionRound>();
        for (SelectionRound round : roundsById.values()) {
            if (normalizedTerm.equals(round.getTerm()) && round.isOpenAt(time)) {
                rounds.add(round);
            }
        }
        return ServiceResult.ok(Collections.unmodifiableList(rounds));
    }

    @Override
    public synchronized ServiceResult<SelectionRound> changeStatus(String roundId,
            SelectionRoundStatus status) {
        String normalizedRoundId = normalize(roundId);
        if (normalizedRoundId == null || status == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "roundId and status must not be null");
        }
        SelectionRound existing = roundsById.get(normalizedRoundId);
        if (existing == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "selection round not found");
        }
        SelectionRound changed = existing.withStatus(status);
        roundsById.put(normalizedRoundId, changed);
        return ServiceResult.ok(changed);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
