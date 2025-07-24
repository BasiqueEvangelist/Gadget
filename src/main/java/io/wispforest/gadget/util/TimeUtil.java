package io.wispforest.gadget.util;

import org.apache.commons.lang3.time.DurationFormatUtils;

public final class TimeUtil {
    private TimeUtil() {

    }

    public static String toHMS(long millis) {
        if (millis < 0) {
            return "-" + DurationFormatUtils.formatDurationHMS(-millis);
        } else {
            return DurationFormatUtils.formatDurationHMS(millis);
        }
    }
}
