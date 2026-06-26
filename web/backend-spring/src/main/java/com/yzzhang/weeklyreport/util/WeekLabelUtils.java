package com.yzzhang.weeklyreport.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WeekLabelUtils {
    private static final Pattern WEEK_PATTERN = Pattern.compile("\\d{4}-W\\d{2}");

    private WeekLabelUtils() {
    }

    public static boolean isValid(String week) {
        return week != null && WEEK_PATTERN.matcher(week).matches();
    }

    public static Optional<String> lastWeekLabelIn(String text) {
        Matcher matcher = WEEK_PATTERN.matcher(text == null ? "" : text);
        String last = null;
        while (matcher.find()) {
            last = matcher.group();
        }
        return Optional.ofNullable(last);
    }
}
