package com.chocwell.jy.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * 全局 Jackson 配置
 * 修复：前端传空字符串 "" 到 BigDecimal 字段时 Jackson 报 InvalidFormatException 的问题
 * （机构地点 longitude / latitude 为空时不填写，应转换成 null 而不是报错）
 *
 * 注意：必须使用 modulesToInstall() 而非 modules()。
 * builder.modules(x) 会设置 findWellKnownModules=false，导致 JavaTimeModule 被
 * 覆盖，HTTP 响应序列化 LocalDateTime 时报 InvalidDefinitionException。
 * modulesToInstall() 在追加自定义模块的同时保留 Spring Boot 的自动模块注册。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            // 自定义模块：空字符串 → null BigDecimal
            SimpleModule module = new SimpleModule();
            module.addDeserializer(BigDecimal.class, new SafeBigDecimalDeserializer());

            // 显式注册 JavaTimeModule，防止被覆盖
            // featuresToDisable 确保 LocalDateTime 序列化为 ISO-8601 字符串而非时间戳
            builder.modulesToInstall(new JavaTimeModule(), module);
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }

    /** 空字符串 → null，正常数字 → BigDecimal */
    static class SafeBigDecimalDeserializer extends NumberDeserializers.BigDecimalDeserializer {
        @Override
        public BigDecimal deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            String text = p.getText();
            if (text == null || text.trim().isEmpty()) return null;
            return super.deserialize(p, ctx);
        }
    }
}
