package com.chocwell.jy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chocwell.jy.entity.PointsLog;
import com.chocwell.jy.entity.SysAdmin;
import com.chocwell.jy.entity.SysUser;
import com.chocwell.jy.mapper.AdminMapper;
import com.chocwell.jy.mapper.PointsLogMapper;
import com.chocwell.jy.mapper.UserMapper;
import com.chocwell.jy.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminMapper adminMapper;
    private final UserMapper userMapper;
    private final PointsLogMapper pointsLogMapper;
    private final JwtUtil jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ─── 管理员登录 ───────────────────────────────────────────

    /**
     * 管理员账密登录，返回 token
     */
    public Map<String, Object> login(String username, String password) {
        SysAdmin admin = adminMapper.findByUsername(username);
        if (admin == null || admin.getStatus() == 0) {
            throw new IllegalArgumentException("账号不存在或已禁用");
        }
        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new IllegalArgumentException("密码错误");
        }

        // 更新最后登录时间
        SysAdmin update = new SysAdmin();
        update.setId(admin.getId());
        update.setLastLoginTime(LocalDateTime.now());
        adminMapper.updateById(update);

        String token = jwtUtil.generateAdmin(admin.getId(), admin.getRole());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("adminId", admin.getId());
        data.put("username", admin.getUsername());
        data.put("realName", admin.getRealName());
        data.put("role", admin.getRole());
        data.put("districtCode", admin.getDistrictCode());
        return data;
    }

    // ─── 用户管理 ────────────────────────────────────────────

    public Page<SysUser> pageUsers(int pageNum, int pageSize, String keyword) {
        Page<SysUser> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .orderByDesc(SysUser::getCreateTime);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(SysUser::getPhone, keyword)
                    .or().like(SysUser::getRealName, keyword);
        }
        return userMapper.selectPage(page, wrapper);
    }

    public SysUser getUser(Long userId) {
        return userMapper.selectById(userId);
    }

    /**
     * 管理员手动调整用户积分（type=3 系统调整），同步写积分流水
     */
    public void adjustPoints(Long userId, int delta, String remark) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        if (user.getTotalPoints() + delta < 0) {
            throw new IllegalStateException("积分调整后不能为负数");
        }
        userMapper.addPoints(userId, delta);

        PointsLog log = new PointsLog();
        log.setUserId(userId);
        log.setType(3); // 系统调整
        log.setAmount(delta);
        log.setRemark(remark != null ? remark : "管理员手动调整");
        pointsLogMapper.insert(log);
    }

    // ─── 子管理员管理（仅超管） ──────────────────────────────

    public void createAdmin(SysAdmin admin, String rawPassword) {
        SysAdmin exists = adminMapper.findByUsername(admin.getUsername());
        if (exists != null) throw new IllegalArgumentException("用户名已存在");
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        admin.setStatus(1);
        adminMapper.insert(admin);
    }

    public void updateAdminStatus(Long adminId, int status) {
        SysAdmin update = new SysAdmin();
        update.setId(adminId);
        update.setStatus(status);
        adminMapper.updateById(update);
    }

    public void resetPassword(Long adminId, String newPassword) {
        SysAdmin update = new SysAdmin();
        update.setId(adminId);
        update.setPasswordHash(passwordEncoder.encode(newPassword));
        adminMapper.updateById(update);
    }
}
