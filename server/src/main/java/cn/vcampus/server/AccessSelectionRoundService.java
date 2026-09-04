package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.SelectionRound;
import cn.vcampus.course.SelectionRoundService;
import cn.vcampus.course.SelectionRoundStatus;
import cn.vcampus.course.SelectionRoundType;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Access 持久化的选课轮次服务，供教务人员维护并供学生选课流程读取。 */
public final class AccessSelectionRoundService implements SelectionRoundService {
    private final Path databasePath;

    public AccessSelectionRoundService(Path databasePath) {
        if (databasePath == null) {
            throw new IllegalArgumentException("databasePath must not be null");
        }
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override
    public synchronized ServiceResult<SelectionRound> create(SelectionRound round) {
        if (round == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "round must not be null");
        }
        ServiceResult<SelectionRound> existing = findById(round.getRoundId());
        if (existing.getStatus() == StatusCode.OK) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "selection round id already exists");
        }
        if (existing.getStatus() != StatusCode.NOT_FOUND) return existing;
        ServiceResult<Boolean> sameType = existsSameTermAndType(round.getTerm(), round.getType());
        if (sameType.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(sameType.getStatus(), sameType.getMessage());
        }
        if (sameType.getData().booleanValue()) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "selection round already exists for this term and type");
        }
        String sql = "INSERT INTO tblSelectionRound(round_id,term,round_type,starts_at,ends_at,status) "
                + "VALUES(?,?,?,?,?,?)";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            writeRound(statement, round);
            statement.executeUpdate();
            return ServiceResult.ok(round);
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public ServiceResult<SelectionRound> findById(String roundId) {
        String normalizedRoundId = normalize(roundId);
        if (normalizedRoundId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "roundId must not be blank");
        }
        String sql = "SELECT round_id,term,round_type,starts_at,ends_at,status "
                + "FROM tblSelectionRound WHERE round_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedRoundId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? ServiceResult.ok(readRound(results))
                        : ServiceResult.<SelectionRound>failure(StatusCode.NOT_FOUND,
                                "selection round not found");
            }
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public ServiceResult<List<SelectionRound>> listByTerm(String term) {
        String normalizedTerm = normalize(term);
        if (normalizedTerm == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "term must not be blank");
        }
        String sql = "SELECT round_id,term,round_type,starts_at,ends_at,status "
                + "FROM tblSelectionRound WHERE term=? ORDER BY starts_at,round_id";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedTerm);
            try (ResultSet results = statement.executeQuery()) {
                List<SelectionRound> rounds = new ArrayList<SelectionRound>();
                while (results.next()) rounds.add(readRound(results));
                return ServiceResult.ok(Collections.unmodifiableList(rounds));
            }
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public ServiceResult<List<SelectionRound>> listOpenRounds(String term, LocalDateTime time) {
        if (time == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "time must not be null");
        }
        ServiceResult<List<SelectionRound>> listed = listByTerm(term);
        if (listed.getStatus() != StatusCode.OK) return listed;
        List<SelectionRound> open = new ArrayList<SelectionRound>();
        for (SelectionRound round : listed.getData()) {
            if (round.isOpenAt(time)) open.add(round);
        }
        return ServiceResult.ok(Collections.unmodifiableList(open));
    }

    @Override
    public ServiceResult<SelectionRound> updateTimeWindow(String roundId, LocalDateTime startsAt,
            LocalDateTime endsAt) {
        String normalizedRoundId = normalize(roundId);
        if (normalizedRoundId == null || startsAt == null || endsAt == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "roundId, startsAt and endsAt must not be null");
        }
        ServiceResult<SelectionRound> existing = findById(normalizedRoundId);
        if (existing.getStatus() != StatusCode.OK) return existing;
        final SelectionRound changed;
        try {
            changed = existing.getData().withTimeWindow(startsAt, endsAt);
        } catch (IllegalArgumentException invalid) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, invalid.getMessage());
        }
        String sql = "UPDATE tblSelectionRound SET starts_at=?,ends_at=? WHERE round_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(changed.getStartsAt()));
            statement.setTimestamp(2, Timestamp.valueOf(changed.getEndsAt()));
            statement.setString(3, changed.getRoundId());
            return statement.executeUpdate() == 1 ? ServiceResult.ok(changed)
                    : ServiceResult.<SelectionRound>failure(StatusCode.NOT_FOUND,
                            "selection round not found");
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public ServiceResult<SelectionRound> changeStatus(String roundId, SelectionRoundStatus status) {
        String normalizedRoundId = normalize(roundId);
        if (normalizedRoundId == null || status == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "roundId and status must not be null");
        }
        ServiceResult<SelectionRound> existing = findById(normalizedRoundId);
        if (existing.getStatus() != StatusCode.OK) return existing;
        SelectionRound changed = existing.getData().withStatus(status);
        String sql = "UPDATE tblSelectionRound SET status=? WHERE round_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, changed.getStatus().name());
            statement.setString(2, changed.getRoundId());
            return statement.executeUpdate() == 1 ? ServiceResult.ok(changed)
                    : ServiceResult.<SelectionRound>failure(StatusCode.NOT_FOUND,
                            "selection round not found");
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    private ServiceResult<Boolean> existsSameTermAndType(String term, SelectionRoundType type) {
        String sql = "SELECT round_id FROM tblSelectionRound WHERE term=? AND round_type=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, term);
            statement.setString(2, type.name());
            try (ResultSet results = statement.executeQuery()) {
                return ServiceResult.ok(Boolean.valueOf(results.next()));
            }
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    private static void writeRound(PreparedStatement statement, SelectionRound round)
            throws SQLException {
        statement.setString(1, round.getRoundId());
        statement.setString(2, round.getTerm());
        statement.setString(3, round.getType().name());
        statement.setTimestamp(4, Timestamp.valueOf(round.getStartsAt()));
        statement.setTimestamp(5, Timestamp.valueOf(round.getEndsAt()));
        statement.setString(6, round.getStatus().name());
    }

    private static SelectionRound readRound(ResultSet results) throws SQLException {
        return new SelectionRound(results.getString("round_id"), results.getString("term"),
                SelectionRoundType.valueOf(results.getString("round_type")),
                results.getTimestamp("starts_at").toLocalDateTime(),
                results.getTimestamp("ends_at").toLocalDateTime(),
                SelectionRoundStatus.valueOf(results.getString("status")));
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath
                + ";immediatelyReleaseResources=true");
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static <T> ServiceResult<T> databaseFailure(SQLException failure) {
        return ServiceResult.failure(StatusCode.SERVER_ERROR,
                "selection round database operation failed: " + failure.getMessage());
    }
}
