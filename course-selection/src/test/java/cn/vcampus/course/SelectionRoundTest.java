package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SelectionRoundTest {

    @Test
    void storesRoundInformationAndRecognizesOpenTimeWindow() {
        LocalDateTime startsAt = LocalDateTime.of(2026, 9, 1, 8, 0);
        LocalDateTime endsAt = LocalDateTime.of(2026, 9, 7, 18, 0);
        SelectionRound round = new SelectionRound("ROUND-2026-INITIAL", "2026-2027-1",
                SelectionRoundType.INITIAL, startsAt, endsAt, SelectionRoundStatus.OPEN);

        assertEquals("ROUND-2026-INITIAL", round.getRoundId());
        assertEquals("2026-2027-1", round.getTerm());
        assertEquals(SelectionRoundType.INITIAL, round.getType());
        assertTrue(round.isOpenAt(startsAt));
        assertTrue(round.isOpenAt(endsAt));
        assertFalse(round.isOpenAt(startsAt.minusMinutes(1)));
        assertFalse(round.isOpenAt(endsAt.plusMinutes(1)));
    }

    @Test
    void closedOrDraftRoundDoesNotAcceptSelections() {
        LocalDateTime startsAt = LocalDateTime.of(2026, 9, 1, 8, 0);
        LocalDateTime endsAt = LocalDateTime.of(2026, 9, 7, 18, 0);
        SelectionRound draftRound = new SelectionRound("ROUND-2026-RETAKE", "2026-2027-1",
                SelectionRoundType.RETAKE, startsAt, endsAt, SelectionRoundStatus.DRAFT);

        assertFalse(draftRound.isOpenAt(startsAt.plusDays(1)));
    }

    @Test
    void rejectsInvalidRoundInformation() {
        LocalDateTime startsAt = LocalDateTime.of(2026, 9, 1, 8, 0);
        LocalDateTime endsAt = LocalDateTime.of(2026, 9, 7, 18, 0);

        assertThrows(IllegalArgumentException.class,
                () -> new SelectionRound("", "2026-2027-1", SelectionRoundType.INITIAL,
                        startsAt, endsAt, SelectionRoundStatus.OPEN));
        assertThrows(IllegalArgumentException.class,
                () -> new SelectionRound("ROUND-2026-INITIAL", "2026-2027-1",
                        SelectionRoundType.INITIAL, endsAt, startsAt, SelectionRoundStatus.OPEN));
        assertThrows(IllegalArgumentException.class,
                () -> new SelectionRound("ROUND-2026-INITIAL", "2026-2027-1",
                        SelectionRoundType.INITIAL, startsAt, endsAt, null));
    }
}
