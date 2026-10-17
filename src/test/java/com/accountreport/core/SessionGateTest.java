package com.accountreport.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class SessionGateTest
{
    @Test public void rejectsDelayedLoadAfterAccountSwitch()
    {
        SessionGate gate = new SessionGate();
        long first = gate.select(Fixtures.KEY);
        String other = Identity.key(9, "STANDARD"); long second = gate.select(other);
        assertFalse(gate.accepts(first, Fixtures.KEY));
        assertFalse(gate.accepts(second, Fixtures.KEY));
        assertTrue(gate.accepts(second, other));
    }
    @Test public void sameCharacterAfterHopCannotAcceptPreviousSessionCallback()
    {
        SessionGate gate = new SessionGate(); long old = gate.select(Fixtures.KEY);
        gate.clear(); long next = gate.select(Fixtures.KEY);
        assertFalse(gate.accepts(old, Fixtures.KEY));
        assertTrue(gate.accepts(next, Fixtures.KEY));
    }
    @Test public void unknownHashIsNeverUsedAsSharedIdentity()
    {
        try { Identity.key(-1, "STANDARD"); fail("Unknown identity accepted"); }
        catch (IllegalArgumentException expected) { }
    }
}
