package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.SelectionRound;
import cn.vcampus.course.SelectionRoundStatus;
import cn.vcampus.course.SelectionRoundType;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 验证选课轮次能真实写入 Access，而不是仅存在于内存中。 */
class AccessSelectionRoundServiceTest {
    private static final String TERM = "2026-2027-1";

    @TempDir
    Path temporaryDirectory;

    private AccessSelectionRoundService service;

    @BeforeEach
    void setUp() throws Exception {
        Path database = temporaryDirectory.resolve("selection-round-test.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblSelectionRound ("
                    + "round_id VARCHAR(36) NOT NULL,"
                    + "term VARCHAR(32) NOT NULL,"
                    + "round_type VARCHAR(16) NOT NULL,"
                    + "starts_at DATETIME NOT NULL,"
                    + "ends_at DATETIME NOT NULL,"
                    + "status VARCHAR(16) NOT NULL,"
                    + "PRIMARY KEY (round_id))");
        }
        service = new AccessSelectionRoundService(database);
    }

    @Test
    void createsPersistsAndListsRounds() {
        SelectionRound initial = round("ROUND-INITIAL", SelectionRoundType.INITIAL,
                SelectionRoundStatus.DRAFT);
        SelectionRound retake = round("ROUND-RETAKE", SelectionRoundType.RETAKE,
                SelectionRoundStatus.OPEN);

        assertEquals(StatusCode.OK, service.create(initial).getStatus());
        assertEquals(StatusCode.OK, service.create(retake).getStatus());

        AccessSelectionRoundService restartedService = new AccessSelectionRoundService(
                temporaryDirectory.resolve("selection-round-test.accdb"));
        ServiceResult<List<SelectionRound>> listed = restartedService.listByTerm(TERM);

        assertEquals(StatusCode.OK, listed.getStatus());
        assertEquals(2, listed.getData().size());
        assertEquals("ROUND-INITIAL", listed.getData().get(0).getRoundId());
    }

    @Test
    void rejectsSecondRoundWithSameTermAndType() {
        assertEquals(StatusCode.OK, service.create(round("ROUND-INITIAL-1",
                SelectionRoundType.INITIAL, SelectionRoundStatus.DRAFT)).getStatus());

        ServiceResult<SelectionRound> result = service.create(round("ROUND-INITIAL-2",
                SelectionRoundType.INITIAL, SelectionRoundStatus.DRAFT));

        assertEquals(StatusCode.CONFLICT, result.getStatus());
    }

    @Test
    void updatesTimeWindowAndStatus() {
        assertEquals(StatusCode.OK, service.create(round("ROUND-INITIAL",
                SelectionRoundType.INITIAL, SelectionRoundStatus.DRAFT)).getStatus());
        LocalDateTime changedStart = LocalDateTime.of(2026, 9, 3, 8, 0);
        LocalDateTime changedEnd = LocalDateTime.of(2026, 9, 9, 18, 0);

        assertEquals(StatusCode.OK, service.updateTimeWindow("ROUND-INITIAL", changedStart,
                changedEnd).getStatus());
        assertEquals(StatusCode.OK, service.changeStatus("ROUND-INITIAL",
                SelectionRoundStatus.OPEN).getStatus());

        ServiceResult<SelectionRound> saved = service.findById("ROUND-INITIAL");
        assertEquals(changedStart, saved.getData().getStartsAt());
        assertEquals(changedEnd, saved.getData().getEndsAt());
        assertEquals(SelectionRoundStatus.OPEN, saved.getData().getStatus());
        assertTrue(service.listOpenRounds(TERM, changedStart.plusHours(1)).getData().size() == 1);
    }

    private static SelectionRound round(String roundId, SelectionRoundType type,
            SelectionRoundStatus status) {
        return new SelectionRound(roundId, TERM, type, LocalDateTime.of(2026, 9, 1, 8, 0),
                LocalDateTime.of(2026, 9, 7, 18, 0), status);
    }
}
