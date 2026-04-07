package com.karim.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SwaggerConfig
 *
 * Configures SpringDoc OpenAPI (Swagger UI) for the Supermarket App.
 *
 * Access after running:
 *   Swagger UI  → http://localhost:8080/swagger-ui/index.html
 *   OpenAPI JSON→ http://localhost:8080/v3/api-docs
 *
 * How to test protected endpoints:
 *   1. Call POST /api/auth/login → copy the accessToken
 *   2. Click "Authorize" button (top right of Swagger UI)
 *   3. Paste:  Bearer <your-access-token>
 *   4. All JWT-protected endpoints will now include the header
 *
 * Required dependency in pom.xml:
 *   <dependency>
 *       <groupId>org.springdoc</groupId>
 *       <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
 *       <version>2.5.0</version>
 *   </dependency>
 */
@Configuration
public class SwaggerConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(serverList())
                .addSecurityItem(globalSecurityRequirement())
                .components(securityComponents());
    }

    // ----------------------------------------------------------------
    // API METADATA
    // ----------------------------------------------------------------
    private Info apiInfo() {
        return new Info()
                .title("Supermarket App API")
                .description("""
                        REST API for the Supermarket App.
                        
                        **Authentication:**
                        - Public endpoints (register, login, etc.) require no token.
                        - Protected endpoints require a JWT access token.
                        - Click **Authorize** → paste `Bearer <access_token>` to authenticate.
                        
                        **Roles:**
                        | Role     | Access                                      |
                        |----------|---------------------------------------------|
                        | CLIENT   | Browse, order, pay, return, chat            |
                        | DELIVERY | Manage assignments, rides, earnings         |
                        | ADMIN    | Full access to all endpoints                |
                        """)
                .version("v1.0.0")
                .contact(new Contact()
                        .name("Karim")
                        .email("karim@supermarket.com"))
                .license(new License()
                        .name("Private")
                        .url("#"));
    }

    // ----------------------------------------------------------------
    // SERVERS — local dev. Add staging/prod URLs here later.
    // ----------------------------------------------------------------
    private List<Server> serverList() {
        Server local = new Server();
        local.setUrl("http://localhost:" + serverPort);
        local.setDescription("Local Development");
        return List.of(local);
    }

    // ----------------------------------------------------------------
    // GLOBAL SECURITY REQUIREMENT
    // Applies "bearerAuth" to every endpoint by default.
    // Public endpoints override this with @SecurityRequirements({}) in their controller.
    // ----------------------------------------------------------------
    private SecurityRequirement globalSecurityRequirement() {
        return new SecurityRequirement().addList(SECURITY_SCHEME_NAME);
    }

    // ----------------------------------------------------------------
    // SECURITY SCHEME DEFINITION
    // Tells Swagger UI to show an "Authorize" button that sends:
    //   Authorization: Bearer <token>
    // ----------------------------------------------------------------
    private Components securityComponents() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .name(SECURITY_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Paste your access token here (without the 'Bearer ' prefix — Swagger adds it).");

        return new Components()
                .addSecuritySchemes(SECURITY_SCHEME_NAME, bearerScheme);
    }
}