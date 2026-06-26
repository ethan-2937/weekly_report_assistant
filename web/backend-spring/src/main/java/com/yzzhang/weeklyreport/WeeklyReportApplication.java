package com.yzzhang.weeklyreport;

import com.yzzhang.weeklyreport.config.WeeklyReportProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(WeeklyReportProperties.class)
public class WeeklyReportApplication {
    public static void main(String[] args) {
        SpringApplication.run(WeeklyReportApplication.class, args);
    }
}
