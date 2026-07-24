package com.thepiratebrowser.android;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LatestRequestGateTest {
    @Test
    public void onlyNewestRequestCanUpdateTheUi() {
        LatestRequestGate gate = new LatestRequestGate();
        LatestRequestGate.Ticket older = gate.begin("older");
        LatestRequestGate.Ticket newer = gate.begin("newer");

        assertFalse(gate.accept(older));
        assertTrue(gate.accept(newer));
    }

    @Test
    public void destroyedActivityRejectsOutstandingCallbacks() {
        LatestRequestGate gate = new LatestRequestGate();
        LatestRequestGate.Ticket ticket = gate.begin("query");

        gate.destroy();

        assertFalse(gate.accept(ticket));
    }
}
