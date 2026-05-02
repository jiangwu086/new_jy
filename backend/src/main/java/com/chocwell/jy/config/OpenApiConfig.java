package com.chocwell.jy.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 3 配置
 * 文档访问地址：http://localhost:8080/swagger-ui/index.html
 * JSON 规范地址：http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("新就业形态劳动者安全警示服务 API")
                        .description("""
                                后端接口文档。
                                - 小程序用户端接口路径：`/api/v1/`（需 Bearer token，登录相关除外）
                                - 平台管理端接口路径：`/api/v1/admin/`
                                - 机构端接口路径：`/api/v1/org/`
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("Chocwell").email("dev@chocwell.com")))
                // 全局 Bearer Token 认证方案
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
