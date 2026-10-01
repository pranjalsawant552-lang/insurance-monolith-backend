package com.insurance.auth.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.*;
import io.swagger.v3.oas.annotations.security.*;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * MONOLITH CONVERSION NOTE: was 12 separate @OpenAPIDefinition classes, one
 * per former microservice, each declaring its own "bearerAuth" security
 * scheme and its own service-specific title/servers block. Since every
 * module's controllers are now scanned into ONE Springdoc instance backing
 * ONE Swagger UI (/api/v1/swagger-ui.html — see application.yml's
 * springdoc.* properties for the shared title/description), keeping all 12
 * would not just have collided on the default Spring bean name (all 12
 * classes were literally named OpenApiConfig) — it would have produced 12
 * conflicting @SecurityScheme(name = "bearerAuth") definitions for a single
 * OpenAPI document, which Springdoc can't merge sensibly either. This is
 * the one surviving copy, updated to describe the whole platform.
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Insurance Platform API",
        version = "v1",
        description = "Merged monolith covering every module: auth, customer, agent, policy, "
                + "claims, payment, product, risk/fraud, document ingestion, notification, "
                + "audit, and analytics. All endpoints share one JWT bearer scheme.",
        contact = @Contact(name = "Platform Team", email = "platform@insurance.com"),
        license = @License(name = "Proprietary")
    ),
    servers = {
        @Server(url = "http://localhost:8080/api/v1", description = "Local development"),
        @Server(url = "https://api.insurance.com/api/v1", description = "Production")
    },
    security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "Enter the JWT access token obtained from /auth/login"
)
public class OpenApiConfig {}
