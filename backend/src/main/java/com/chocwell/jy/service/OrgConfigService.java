package com.chocwell.jy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chocwell.jy.entity.OrgConfig;
import com.chocwell.jy.mapper.OrgConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 机构级配置服务（org_config）
 *
 * 取值优先级：
 *   1. 机构在 org_config 中显式配置的值
 *   2. 全局 sys_config 的同名 key
 *   3. 代码默认值（仅在调用 getInt 时通过 defaultVal 兜底）
 *
 * orgId 为 null 时直接走全局，方便平台公共点位复用。
 */
@Service
@RequiredArgsConstructor
public class OrgConfigService {

    private final OrgConfigMapper orgConfigMapper;
    private final ConfigService   configService;   // 全局 sys_config

    /** 当前支持机构级覆盖的 key 白名单 */
    public static final List<String> SUPPORTED_KEYS = List.of(
            "points_per_checkin",
            "max_checkins_per_period",
            "current_period_type"
    );

    /** 取字符串配置（先机构后全局） */
    public String get(Long orgId, String key) {
        if (orgId != null) {
            String v = orgConfigMapper.getValue(orgId, key);
            if (v != null && !v.isBlank()) return v;
        }
        return configService.get(key);
    }

    /** 取整数配置 */
    public int getInt(Long orgId, String key, int defaultVal) {
        String val = get(orgId, key);
        try {
            return (val == null || val.isBlank()) ? defaultVal : Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    /**
     * 列出某机构当前的有效配置 + 全局默认值
     * 返回结构便于前端表单展示：
     *   [
     *     { key: "points_per_checkin", orgValue: "20" or null, globalValue: "10", effective: "20" },
     *     ...
     *   ]
     */
    public List<Map<String, Object>> listEffective(Long orgId) {
        // 取该机构所有 org_config 项 → 转 Map<key, value>
        List<OrgConfig> orgRows = orgConfigMapper.selectList(
                new LambdaQueryWrapper<OrgConfig>().eq(OrgConfig::getOrgId, orgId));
        Map<String, String> orgMap = new HashMap<>();
        for (OrgConfig c : orgRows) orgMap.put(c.getConfigKey(), c.getConfigValue());

        return SUPPORTED_KEYS.stream().map(key -> {
            Map<String, Object> m = new LinkedHashMap<>();
            String orgVal = orgMap.get(key);
            String globalVal = configService.get(key);
            m.put("key",         key);
            m.put("orgValue",    orgVal);                                 // 机构未配置则 null
            m.put("globalValue", globalVal);
            m.put("effective",   (orgVal != null && !orgVal.isBlank()) ? orgVal : globalVal);
            return m;
        }).toList();
    }

    /**
     * 设置/清空 机构配置。
     * value 为 null 或空字符串视为「清空 → 回归全局默认」，会删除对应行。
     */
    public void set(Long orgId, String key, String value) {
        if (!SUPPORTED_KEYS.contains(key)) {
            throw new IllegalArgumentException("不支持的配置项：" + key);
        }
        OrgConfig exist = orgConfigMapper.selectOne(
                new LambdaQueryWrapper<OrgConfig>()
                        .eq(OrgConfig::getOrgId, orgId)
                        .eq(OrgConfig::getConfigKey, key));

        if (value == null || value.isBlank()) {
            // 清空：删除该行，让查询回退到全局
            if (exist != null) orgConfigMapper.deleteById(exist.getId());
            return;
        }

        if (exist == null) {
            OrgConfig row = new OrgConfig();
            row.setOrgId(orgId);
            row.setConfigKey(key);
            row.setConfigValue(value);
            orgConfigMapper.insert(row);
        } else {
            exist.setConfigValue(value);
            orgConfigMapper.updateById(exist);
        }
    }
}
