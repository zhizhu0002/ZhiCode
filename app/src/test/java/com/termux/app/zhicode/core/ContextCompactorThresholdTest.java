package com.termux.app.zhicode.core;

import static org.junit.Assert.assertEquals;

import com.termux.app.zhicode.model.SessionConfig;
import org.junit.Test;

public final class ContextCompactorThresholdTest {

    @Test
    public void defaultAutoCompactThresholdIs60Percent() {
        SessionConfig config = new SessionConfig();
        config.contextWindowTokens = 128_000;
        assertEquals(0.60, config.autoCompactRatio, 0.0001);
        assertEquals(76_800, ContextCompactor.autoCompactThreshold(config));
    }

    @Test
    public void nullConfigUses60PercentDefault() {
        assertEquals(76_800, ContextCompactor.autoCompactThreshold(null));
    }
}
