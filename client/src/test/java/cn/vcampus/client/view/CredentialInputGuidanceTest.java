package cn.vcampus.client.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialInputGuidanceTest {
    @Test
    void guidanceExplainsVisibleInputRules() {
        assertTrue(CredentialInputGuidance.USER_ID_HINT.contains("1-32"));
        assertTrue(CredentialInputGuidance.USER_ID_HINT.contains("字母"));
        assertTrue(CredentialInputGuidance.USER_ID_HINT.contains("数字"));
        assertTrue(CredentialInputGuidance.USER_ID_HINT.contains("下划线"));

        assertTrue(CredentialInputGuidance.DISPLAY_NAME_HINT.contains("1-64"));
        assertTrue(CredentialInputGuidance.DISPLAY_NAME_HINT.contains("中文"));

        assertTrue(CredentialInputGuidance.PASSWORD_HINT.contains("6-16"));
    }
}
