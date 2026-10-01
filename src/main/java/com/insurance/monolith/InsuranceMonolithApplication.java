package com.insurance.monolith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Single entry point for the merged monolith.
 *
 * Replaces the 12 per-microservice @SpringBootApplication classes
 * (agent, analytics, audit, auth, claims, customer, document,
 * notification, payment, policy, product, risk) and removes Eureka
 * service discovery / the API gateway entirely — everything now runs
 * in one JVM behind one embedded servlet container, so there is
 * nothing left to discover or route between.
 *
 * Every former microservice's code lives under its own sub-package of
 * com.insurance.* (com.insurance.policy, com.insurance.claims, ...),
 * unchanged, so component scanning from the com.insurance root picks
 * up every controller/service/repository/entity from every module.
 */
@SpringBootApplication(scanBasePackages = "com.insurance")
@EntityScan(basePackages = "com.insurance")
@EnableJpaRepositories(basePackages = "com.insurance")
@EnableAsync
@EnableScheduling
@EnableCaching
public class InsuranceMonolithApplication {
    public static void main(String[] args) {
        SpringApplication.run(InsuranceMonolithApplication.class, args);
    }
}
