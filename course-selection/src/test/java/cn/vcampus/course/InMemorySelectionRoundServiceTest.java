package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemorySelectionRoundServiceTest {

    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 9, 1, 8, 0);
    private static final LocalDateTime ENDS_AT = LocalDateTime.of(2026, 9, 7, 18, 0);

    @Test
    void createsRoundAndListsItByTerm() {
        InMemorySelectionRoundService service = new InMemorySelectionRoundService();
        SelectionRound round = round("ROUND-INITIAL", SelectionRoundType.INITIAL,
                SelectionRoundStatus.DRAFT);

        ServiceResult<SelectionRound> createResult = service.create(round);
        ServiceResult<List<SelectionRound>> listResult = service.listByTerm("2026-2027-1");

        assertEquals(StatusCode.OK, createResult.getStatus());
        assertEquals(StatusCode.OK, listResult.getStatus());
        assertEquals(1, listResult.getData().size());
        assertEquals("ROUND-INITIAL", listResult.getData().get(0).getRoundId());
    }

    @Test
    void rejectsDuplicateRoundId() {
        SelectionRound round = round("ROUND-INITIAL", SelectionRoundType.INITIAL,
                SelectionRoundStatus.DRAFT);
        InMemorySelectionRoundService service = new InMemorySelectionRoundService(
                Arrays.asList(round));

        ServiceResult<SelectionRound> result = service.create(round);

        assertEquals(StatusCode.CONFLICT, result.getStatus());
    }

    @Test
    void rejectsSameRoundTypeInSameTerm() {
        InMemorySelectionRoundService service = new InMemorySelectionRoundService();
        assertEquals(StatusCode.OK, service.create(round("ROUND-INITIAL-1", SelectionRoundType.INITIAL,
                SelectionRoundStatus.DRAFT)).getStatus());

        ServiceResult<SelectionRound> result = service.create(round("ROUND-INITIAL-2",
                SelectionRoundType.INITIAL, SelectionRoundStatus.DRAFT));

        assertEquals(StatusCode.CONFLICT, result.getStatus());
    }

    @Test
    void listsOnlyRoundsThatAreOpenAtSpecifiedTime() {
        SelectionRound initialRound = round("ROUND-INITIAL", SelectionRoundType.INITIAL,
                SelectionRoundStatus.OPEN);
        SelectionRound retakeRound = round("ROUND-RETAKE", SelectionRoundType.RETAKE,
                SelectionRoundStatus.DRAFT);
        InMemorySelectionRoundService service = new InMemorySelectionRoundService(
                Arrays.asList(initialRound, retakeRound));

        ServiceResult<List<SelectionRound>> result = service.listOpenRounds("2026-2027-1",
                STARTS_AT.plusDays(1));

        assertEquals(StatusCode.OK, result.getStatus());
        assertEquals(1, result.getData().size());
        assertEquals("ROUND-INITIAL", result.getData().get(0).getRoundId());
    }

    @Test
    void changesRoundStatusAndAffectsOpenRoundQuery() {
        SelectionRound round = round("ROUND-INITIAL", SelectionRoundType.INITIAL,
                SelectionRoundStatus.DRAFT);
        InMemorySelectionRoundService service = new InMemorySelectionRoundService(
                Arrays.asList(round));

        ServiceResult<SelectionRound> changeResult = service.changeStatus("ROUND-INITIAL",
                SelectionRoundStatus.OPEN);
        ServiceResult<List<SelectionRound>> openResult = service.listOpenRounds("2026-2027-1",
                STARTS_AT.plusDays(1));

        assertEquals(StatusCode.OK, changeResult.getStatus());
        assertEquals(SelectionRoundStatus.OPEN, changeResult.getData().getStatus());
        assertEquals(1, openResult.getData().size());
    }

    @Test
    void updatesRoundTimeWindowWithoutChangingItsTypeOrStatus() {
        SelectionRound round = round("ROUND-INITIAL", SelectionRoundType.INITIAL,
                SelectionRoundStatus.OPEN);
        InMemorySelectionRoundService service = new InMemorySelectionRoundService(
                Arrays.asList(round));
        LocalDateTime changedStart = STARTS_AT.plusDays(2);
        LocalDateTime changedEnd = ENDS_AT.plusDays(2);

        ServiceResult<SelectionRound> result = service.updateTimeWindow("ROUND-INITIAL",
                changedStart, changedEnd);

        assertEquals(StatusCode.OK, result.getStatus());
        assertEquals(changedStart, result.getData().getStartsAt());
        assertEquals(changedEnd, result.getData().getEndsAt());
        assertEquals(SelectionRoundType.INITIAL, result.getData().getType());
        assertEquals(SelectionRoundStatus.OPEN, result.getData().getStatus());
    }

    @Test
    void rejectsInvalidOrUnknownManagementRequests() {
        InMemorySelectionRoundService service = new InMemorySelectionRoundService();

        assertEquals(StatusCode.BAD_REQUEST, service.create(null).getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.listByTerm(" ").getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.listOpenRounds("2026-2027-1", null).getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.changeStatus("ROUND-INITIAL", null).getStatus());
        assertEquals(StatusCode.NOT_FOUND, service.findById("UNKNOWN").getStatus());
        assertEquals(StatusCode.NOT_FOUND,
                service.changeStatus("UNKNOWN", SelectionRoundStatus.OPEN).getStatus());
    }

    private static SelectionRound round(String roundId, SelectionRoundType type,
            SelectionRoundStatus status) {
        return new SelectionRound(roundId, "2026-2027-1", type, STARTS_AT, ENDS_AT, status);
    }
}
