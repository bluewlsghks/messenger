package com.individual.messenger.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaidServicePolicyTest {
    @Test void legacyAiEnableFlagAndKeyCannotOverrideFreeOnlyPolicy() {
        OpenAiService ai = new OpenAiService("synthetic-key-never-sent", true, false);
        assertFalse(ai.isEnabled());
        assertThrows(IllegalStateException.class, () -> ai.reply(java.util.List.of(), "test"));
    }
    @Test void noPaidModeDoesNotRequireAnApiKey() {
        assertFalse(new OpenAiService("", true, false).isEnabled());
    }
    @Test void allowFlagAloneCannotEnableAi() {
        assertFalse(new OpenAiService("synthetic-key-never-sent", false, true).isEnabled());
    }
}
