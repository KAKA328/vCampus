package cn.vcampus.client.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoginFrameTest {
    @Test
    void forcedPasswordChangeRequiresMatchingSixToSixteenCharacterPasswords() {
        assertEquals("新密码不能为空", LoginFrame.validateForcedPassword("", ""));
        assertEquals("新密码需为 6-16 位", LoginFrame.validateForcedPassword("12345", "12345"));
        assertEquals("两次输入的新密码不一致", LoginFrame.validateForcedPassword("123456", "654321"));
        assertEquals("", LoginFrame.validateForcedPassword("New123", "New123"));
    }
}
