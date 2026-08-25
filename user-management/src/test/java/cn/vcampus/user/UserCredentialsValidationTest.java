package cn.vcampus.user;

import cn.vcampus.common.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserCredentialsValidationTest {
    @Test
    void userIdAllowsLettersDigitsAndUnderscoreWithinThirtyTwoCharacters() {
        assertDoesNotThrow(() -> new UserCredentials("_09024429", "abc123", "何锦恒", Role.STUDENT.name()));
    }

    @Test
    void userIdRejectsChineseCharactersAndOverlongValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new UserCredentials("用户001", "abc123", "何锦恒", Role.STUDENT.name()));
        assertThrows(IllegalArgumentException.class,
                () -> new UserCredentials("abcdefghijklmnopqrstuvwxyz1234567", "abc123", "何锦恒", Role.STUDENT.name()));
    }

    @Test
    void displayNameAcceptsChineseButRejectsOverlongValues() {
        String overlongName = "一二三四五六七八九十"
                + "一二三四五六七八九十"
                + "一二三四五六七八九十"
                + "一二三四五六七八九十"
                + "一二三四五六七八九十"
                + "一二三四五六七八九十"
                + "一二三四五";

        assertDoesNotThrow(() -> new UserCredentials("u100", "abc123", "何锦恒", Role.STUDENT.name()));
        assertThrows(IllegalArgumentException.class,
                () -> new UserCredentials("u101", "abc123", overlongName, Role.STUDENT.name()));
    }
}
