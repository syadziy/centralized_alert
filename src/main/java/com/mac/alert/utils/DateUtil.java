package com.mac.alert.utils;

import java.sql.Timestamp;
import java.time.Instant;

public class DateUtil {

    public static Timestamp toTimestamp(Instant instant) {
        return instant == null
                ? null
                : Timestamp.from(instant);
    }
}
