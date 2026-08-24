package cn.vcampus.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class UserTest {
    @Test
    void userRejectsBlankIdAndPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> new User("", "", "Demo", Role.STUDENT));
    }
}
