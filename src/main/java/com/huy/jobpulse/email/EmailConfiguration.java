package com.huy.jobpulse.email;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfiguration {
    @Bean("taskScheduler")
    ThreadPoolTaskScheduler taskScheduler() { return scheduler("jobpulse-scheduling-"); }
    @Bean("emailTaskScheduler")
    ThreadPoolTaskScheduler emailTaskScheduler() { return scheduler("jobpulse-email-"); }
    private static ThreadPoolTaskScheduler scheduler(String prefix) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(prefix);
        return scheduler;
    }
}
