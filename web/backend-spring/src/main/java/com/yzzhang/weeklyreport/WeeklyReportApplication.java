package com.yzzhang.weeklyreport;

import com.yzzhang.weeklyreport.config.WeeklyReportProperties;
import com.yzzhang.weeklyreport.config.SubmissionReminderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({WeeklyReportProperties.class, SubmissionReminderProperties.class})
public class WeeklyReportApplication {
    public static void main(String[] args) {
        SpringApplication.run(WeeklyReportApplication.class, args);
    }
}
