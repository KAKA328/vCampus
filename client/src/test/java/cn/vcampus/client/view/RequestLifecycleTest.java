package cn.vcampus.client.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestLifecycleTest {
    @Test
    void olderRequestCannotReleaseBusyStateOfAChainedRefresh() {
        RequestLifecycle lifecycle = new RequestLifecycle();

        int firstRequest = lifecycle.begin();
        int refreshRequest = lifecycle.begin();

        assertFalse(lifecycle.isCurrent(firstRequest));
        assertTrue(lifecycle.isCurrent(refreshRequest));
    }
}
