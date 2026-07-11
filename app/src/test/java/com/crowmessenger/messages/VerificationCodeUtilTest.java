package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VerificationCodeUtilTest {
    @Test
    public void findCode_extractsCommonVerificationCodes() {
        assertEquals("483921", VerificationCodeUtil.findCode("Your verification code is 483921."));
        assertEquals("7714", VerificationCodeUtil.findCode("7714 is your sign-in code"));
    }

    @Test
    public void findCode_ignoresUnrelatedNumbersAndYears() {
        assertEquals("", VerificationCodeUtil.findCode("Call me at 5551234567"));
        assertEquals("", VerificationCodeUtil.findCode("Your verification happened in 2026"));
    }
}
