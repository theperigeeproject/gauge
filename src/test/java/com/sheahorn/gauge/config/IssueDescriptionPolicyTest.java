package com.sheahorn.gauge.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IssueDescriptionPolicyTest {

    private IssueDescriptionPolicy policy(boolean allowLong) {
        IssueDescriptionPolicy policy = new IssueDescriptionPolicy();
        policy.allowLong = allowLong;
        return policy;
    }

    @Test
    void defaultCapIsFourThousand() {
        IssueDescriptionPolicy policy = policy(false);
        assertEquals(4000, policy.maxLength());
        policy.validate("x".repeat(4000));
    }

    @Test
    void rejectsJustOverTheDefaultCap() {
        IssueDescriptionPolicy policy = policy(false);
        var ex = assertThrows(RuntimeException.class, () -> policy.validate("x".repeat(4001)));
        assertTrue(ex.getMessage().contains("4000"), ex.getMessage());
    }

    @Test
    void nullDescriptionIsAllowed() {
        assertDoesNotThrow(() -> policy(false).validate(null));
    }

    @Test
    void allowLongLiftsCapToHardLimit() {
        IssueDescriptionPolicy policy = policy(true);
        assertEquals(IssueDescriptionPolicy.HARD_MAX_LENGTH, policy.maxLength());
        policy.validate("x".repeat(50_000));
    }

    @Test
    void hardLimitStillEnforcedWhenAllowLong() {
        IssueDescriptionPolicy policy = policy(true);
        assertThrows(RuntimeException.class,
            () -> policy.validate("x".repeat(IssueDescriptionPolicy.HARD_MAX_LENGTH + 1)));
    }
}
