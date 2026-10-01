package com.insurance.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

// NOTE: the security bean that used to live here (a permissive "anyRequest().permitAll()"
// filter chain) was removed as part of Phase 7 RBAC enforcement. notification-service was
// missed entirely during Phase 1's sweep — it had spring-boot-starter-security on the
// classpath but zero real authorization enforcement until now, exactly the same landmine
// class Phase 2 found and fixed in analytics-service's AppConfig.java. Real enforcement now
// lives in config/SecurityConfig.java + security/HeaderAuthFilter.java.
@Configuration
public class AppConfig {

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(30);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("notif-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}

