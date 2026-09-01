package com.epiproxy.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentMessageTest {

    @Test
    void rejectsNullSourceAgent() {
        assertThrows(IllegalArgumentException.class, () -> new AgentMessage(null, "target", "payload", 1));
    }

    @Test
    void rejectsBlankSourceAgent() {
        assertThrows(IllegalArgumentException.class, () -> new AgentMessage("  ", "target", "payload", 1));
    }

    @Test
    void rejectsNullTargetAgent() {
        assertThrows(IllegalArgumentException.class, () -> new AgentMessage("source", null, "payload", 1));
    }

    @Test
    void rejectsNullPayload() {
        assertThrows(NullPointerException.class, () -> new AgentMessage("source", "target", null, 1));
    }

    @Test
    void allowsEmptyPayload() {
        AgentMessage msg = new AgentMessage("source", "target", "", 1);
        assertEquals("", msg.payload());
    }

    @Test
    void rejectsOutOfRangeTrustTier() {
        assertThrows(IllegalArgumentException.class, () -> new AgentMessage("source", "target", "payload", 4));
        assertThrows(IllegalArgumentException.class, () -> new AgentMessage("source", "target", "payload", -1));
    }

    @Test
    void defaultsTrustTierToTwo() {
        AgentMessage msg = new AgentMessage("source", "target", "payload");
        assertEquals(2, msg.trustTier());
    }
}
