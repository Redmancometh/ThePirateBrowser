package com.thepiratebrowser.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestGenerationTest {
    @Test
    void onlyLatestResultProducingRequestCanCompleteTheUi() {
        RequestGeneration requests = new RequestGeneration();

        long freeFormSearch = requests.begin();
        long monitoredSearch = requests.begin();

        assertFalse(requests.isCurrent(freeFormSearch));
        assertTrue(requests.isCurrent(monitoredSearch));
        assertTrue(requests.hasActiveRequest());

        requests.complete(freeFormSearch);
        assertTrue(requests.hasActiveRequest());
        requests.complete(monitoredSearch);
        assertFalse(requests.hasActiveRequest());
    }
}
