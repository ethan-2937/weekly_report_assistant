package com.yzzhang.weeklyreport.util;

import java.util.ArrayList;
import java.util.List;

public final class CsvUtils {
    private CsvUtils() {
    }

    public static List<String> parseLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == ',') {
                values.add(current.toString());
                current.setLength(0);
            } else if (c == '"') {
                quoted = true;
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        return values;
    }

    public static String stripBom(String value) {
        return value != null && value.startsWith("\ufeff") ? value.substring(1) : value;
    }
}
