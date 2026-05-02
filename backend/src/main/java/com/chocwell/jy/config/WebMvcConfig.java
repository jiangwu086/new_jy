package com.chocwell.jy.config;

import com.chocwell.jy.interceptor.AdminAuthInterceptor;
import com.chocwell.jy.interceptor.OrgAuthInterceptor;
import com.chocwell.jy.interceptor.UserAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc 配置：注册拦截器 + 全局 CORS
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final UserAuthInterceptor  userAuthInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;
    private final OrgAuthInterceptor   orgAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 用户端鉴权（排除登录接口 + Swagger UI）
        registry.addInterceptor(userAuthInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/auth/**",
                        "/api/v1/admin/**",
                        "/api/v1/org/**",
                        "/api/v1/articles",        // 文章列表无需登录
                        "/api/v1/articles/*",
                        "/api/v1/locations",       // 点位列表无需登录
                        "/api/v1/locations/*",     // 点位详情无需登录
                        "/api/v1/gifts",           // 礼品列表无需登录，兑换(/exchange)需要
                        "/api/v1/files/**"         // 上传文件（图片）无需登录
                );

        // 平台管理端鉴权（排除 Swagger）
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/v1/admin/**")
                .excludePathPatterns(
                        "/api/v1/admin/login",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                );

        // 机构端鉴权（排除 Swagger）
        registry.addInterceptor(orgAuthInterceptor)
                .addPathPatterns("/api/v1/org/**")
                .excludePathPatterns(
                        "/api/v1/org/login",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
