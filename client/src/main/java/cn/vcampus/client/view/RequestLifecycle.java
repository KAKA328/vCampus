package cn.vcampus.client.view;

/** Tracks the newest asynchronous request so chained refreshes do not flicker. */
final class RequestLifecycle {
    private int currentRequest;

    int begin() {
        return ++currentRequest;
    }

    boolean isCurrent(int request) {
        return request == currentRequest;
    }
}
