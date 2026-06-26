package com.yzzhang.weeklyreport.config;

import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class ProjectPathConfig {
    private final WeeklyReportProperties properties;

    public ProjectPathConfig(WeeklyReportProperties properties) {
        this.properties = properties;
    }

    public Path projectRoot() {
        String configured = properties.getProjectRoot();
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (userDir.endsWith(Path.of("web", "backend-spring"))) {
            return userDir.resolve("../..").normalize();
        }
        return userDir;
    }

    public Path outputRoot() {
        return projectRoot().resolve("output").normalize();
    }

    public Path frontendDist() {
        String configured = properties.getFrontendDist();
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        return projectRoot().resolve("web").resolve("frontend").resolve("dist").normalize();
    }

    public String pythonBin() {
        String configured = properties.getPythonBin();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return isWindows() ? "py" : "python3";
    }

    public boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
