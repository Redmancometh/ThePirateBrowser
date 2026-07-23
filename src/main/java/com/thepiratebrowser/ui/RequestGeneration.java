package com.thepiratebrowser.ui;

final class RequestGeneration {
    private long current;
    private long active;

    long begin() {
        active = ++current;
        return active;
    }

    boolean isCurrent(long request) {
        return request == current;
    }

    boolean hasActiveRequest() {
        return active != 0;
    }

    void complete(long request) {
        if (isCurrent(request)) {
            active = 0;
        }
    }
}
