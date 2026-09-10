package cn.vcampus.client.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginFrameTest {
    @Test
    void forcedPasswordChangeRequiresMatchingSixToSixteenCharacterPasswords() {
        assertEquals("新密码不能为空", LoginFrame.validateForcedPassword("", ""));
        assertEquals("新密码需为 6-16 位", LoginFrame.validateForcedPassword("12345", "12345"));
        assertEquals("两次输入的新密码不一致", LoginFrame.validateForcedPassword("123456", "654321"));
        assertEquals("", LoginFrame.validateForcedPassword("New123", "New123"));
    }

    @Test
    void loginAttemptGateAllowsOnlyOneRequestAndOneMainWindow() {
        LoginFrame.LoginAttemptGate gate = new LoginFrame.LoginAttemptGate();

        assertTrue(gate.begin());
        assertTrue(gate.isInProgress());
        assertFalse(gate.begin());
        gate.finish();
        assertFalse(gate.isInProgress());
        assertTrue(gate.begin());
        assertTrue(gate.openMainOnce());
        assertTrue(gate.isMainOpened());
        assertFalse(gate.begin());
        assertFalse(gate.openMainOnce());
    }
}
