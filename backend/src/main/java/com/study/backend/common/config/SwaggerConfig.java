package com.study.backend.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("SPA Board API")
                .description("게시판 서비스 REST API 문서")
                .version("v1.0"))
            .addSecurityItem(new SecurityRequirement().addList("cookieAuth"))
            .components(new Components()
                .addSecuritySchemes("cookieAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.COOKIE)
                    .name("token")));
    }
}
