package com.yzzhang.weeklyreport.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

class MarkdownTableFilter {
    String filterByHeader(String content, String headerKeyword, Predicate<String> visibleRow) {
        List<String> source = lines(content);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < source.size(); i++) {
            if (source.get(i).contains(headerKeyword)) {
                builder.append(filterRows(tableAfter(source, i + 1), visibleRow));
            }
        }
        return builder.toString();
    }

    String filterIdentityByHeader(
        String content,
        String headerKeyword,
        BiPredicate<String, String> visibleIdentity
    ) {
        List<String> source = lines(content);
        for (int i = 0; i < source.size(); i++) {
            if (source.get(i).contains(headerKeyword)) {
                return filterIdentityRows(tableAfter(source, i + 1), visibleIdentity);
            }
        }
        return "";
    }

    String filterIdentitySection(
        String content,
        String sectionHeading,
        BiPredicate<String, String> visibleIdentity
    ) {
        List<String> source = lines(content);
        for (int i = 0; i < source.size(); i++) {
            String line = source.get(i);
            if (line.matches("^##\\s+.+") && line.contains(sectionHeading)) {
                return filterIdentityRows(tableAfter(source, i + 1), visibleIdentity);
            }
        }
        return "";
    }

    String filterRows(List<String> table, Predicate<String> visibleRow) {
        if (table.size() < 2) {
            return "";
        }
        StringBuilder builder = new StringBuilder(table.get(0)).append('\n').append(table.get(1)).append('\n');
        int count = 0;
        for (int i = 2; i < table.size(); i++) {
            if (visibleRow.test(table.get(i))) {
                builder.append(table.get(i)).append('\n');
                count += 1;
            }
        }
        return count == 0 ? "" : builder.append('\n').toString();
    }

    private String filterIdentityRows(
        List<String> table,
        BiPredicate<String, String> visibleIdentity
    ) {
        if (table.size() < 2) {
            return "";
        }
        List<String> headers = markdownCells(table.get(0));
        int userIdIndex = headers.indexOf("userid");
        StringBuilder builder = new StringBuilder(table.get(0)).append('\n').append(table.get(1)).append('\n');
        int count = 0;
        for (int i = 2; i < table.size(); i++) {
            List<String> cells = markdownCells(table.get(i));
            String name = cells.isEmpty() ? "" : cells.get(0);
            String userId = userIdIndex >= 0 && userIdIndex < cells.size() ? cells.get(userIdIndex) : "";
            if (visibleIdentity.test(name, userId)) {
                builder.append(table.get(i)).append('\n');
                count += 1;
            }
        }
        return count == 0 ? "" : builder.append('\n').toString();
    }

    private List<String> tableAfter(List<String> source, int start) {
        int index = start;
        while (index < source.size() && !source.get(index).trim().startsWith("|")) {
            if (source.get(index).matches("^##\\s+.+")) {
                return List.of();
            }
            index += 1;
        }
        List<String> table = new ArrayList<>();
        while (index < source.size() && source.get(index).trim().startsWith("|")) {
            table.add(source.get(index));
            index += 1;
        }
        return table;
    }

    private List<String> markdownCells(String row) {
        String text = row.trim();
        if (text.startsWith("|")) {
            text = text.substring(1);
        }
        if (text.endsWith("|")) {
            text = text.substring(0, text.length() - 1);
        }
        return List.of(text.split("\\|", -1)).stream().map(String::trim).toList();
    }

    private List<String> lines(String content) {
        return List.of(content.replace("\r\n", "\n").split("\n", -1));
    }
}
