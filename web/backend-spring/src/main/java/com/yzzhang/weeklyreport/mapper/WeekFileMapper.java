package com.yzzhang.weeklyreport.mapper;

import com.yzzhang.weeklyreport.config.ProjectPathConfig;
import com.yzzhang.weeklyreport.util.WeekLabelUtils;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Repository
public class WeekFileMapper {
    private final ProjectPathConfig pathConfig;

    public WeekFileMapper(ProjectPathConfig pathConfig) {
        this.pathConfig = pathConfig;
    }

    public List<String> listWeekLabels() {
        Path output = pathConfig.outputRoot();
        if (!Files.isDirectory(output)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(output)) {
            return stream.filter(Files::isDirectory)
                .map(path -> path.getFileName().toString())
                .filter(WeekLabelUtils::isValid)
                .sorted(Comparator.reverseOrder())
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public Path weekDir(String week) {
        return pathConfig.outputRoot().resolve(week).normalize();
    }

    public Path submissionSummaryPath(String week) {
        return weekDir(week).resolve("summary").resolve("submission_check.md").normalize();
    }

    public Path managerReportPath(String week) {
        return weekDir(week).resolve("summary").resolve("manager_report.md").normalize();
    }

    public Path analysisInputPath(String week) {
        return weekDir(week).resolve("analysis").resolve("analysis_input.md").normalize();
    }

    public Path rawReportsPath(String week) {
        return weekDir(week).resolve("raw").resolve("reports.json").normalize();
    }

    public Path allReportsPath(String week) {
        return weekDir(week).resolve("raw").resolve("all_reports.json").normalize();
    }

    public Path contactsUsersPath() {
        return pathConfig.outputRoot().resolve("contacts").resolve("users.json").normalize();
    }

    public String readIfExists(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    public boolean exists(Path path) {
        return Files.exists(path);
    }

    public Optional<Instant> latestModified(String week) {
        Path dir = weekDir(week);
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                .map(path -> {
                    try {
                        return Files.getLastModifiedTime(path).toInstant();
                    } catch (IOException e) {
                        return Instant.EPOCH;
                    }
                })
                .max(Comparator.naturalOrder());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public String relativize(Path path) {
        try {
            return pathConfig.projectRoot().relativize(path).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return path.toString();
        }
    }
}
