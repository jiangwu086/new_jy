package com.chocwell.jy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chocwell.jy.entity.SysConfig;
import com.chocwell.jy.mapper.ConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConfigService {

    private final ConfigMapper configMapper;

    /**
     * 按 key 获取配置值（字符串）
     */
    public String get(String key) {
        String val = configMapper.getValueByKey(key);
        return val != null ? val : "";
    }

    /**
     * 按 key 获取配置值（整型）
     */
    public int getInt(String key, int defaultVal) {
        String val = configMapper.getValueByKey(key);
        try {
            return val != null ? Integer.parseInt(val.trim()) : defaultVal;
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    /**
     * 更新配置
     */
    public void set(String key, String value) {
        SysConfig cfg = new SysConfig();
        cfg.setConfigValue(value);
        configMapper.update(cfg, new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key));
    }

    /**
     * 获取全部配置（管理后台用）
     */
    public List<SysConfig> listAll() {
        return configMapper.selectList(null);
    }
}
