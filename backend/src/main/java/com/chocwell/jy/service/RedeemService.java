package com.chocwell.jy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chocwell.jy.entity.PointsLog;
import com.chocwell.jy.entity.RedeemRecord;
import com.chocwell.jy.entity.SysUser;
import com.chocwell.jy.mapper.PointsLogMapper;
import com.chocwell.jy.mapper.RedeemMapper;
import com.chocwell.jy.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RedeemService {

    private final RedeemMapper redeemMapper;
    private final UserMapper userMapper;
    private final PointsLogMapper pointsLogMapper;

    /**
     * 管理员为用户核销积分
     */
    @Transactional(rollbackFor = Exception.class)
    public RedeemRecord redeem(Long userId, Long locationId, int points, Long operatorId, String remark) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        if (user.getTotalPoints() < points) {
            throw new IllegalStateException("用户积分不足（当前 " + user.getTotalPoints() + " 分）");
        }

        // 扣减积分
        userMapper.addPoints(userId, -points);

        // 写核销记录
        RedeemRecord record = new RedeemRecord();
        record.setUserId(userId);
        record.setLocationId(locationId);
        record.setPointsUsed(points);
        record.setOperatorId(operatorId);
        record.setRemark(remark);
        redeemMapper.insert(record);

        // 写积分流水
        PointsLog pointsLog = new PointsLog();
        pointsLog.setUserId(userId);
        pointsLog.setType(2); // 核销扣减
        pointsLog.setAmount(-points);
        pointsLog.setRelatedId(record.getId());
        pointsLog.setRemark("积分核销" + (remark != null ? " — " + remark : ""));
        pointsLogMapper.insert(pointsLog);

        return record;
    }

    /**
     * 查询用户核销历史
     */
    public List<RedeemRecord> history(Long userId) {
        return redeemMapper.selectList(
                new LambdaQueryWrapper<RedeemRecord>()
                        .eq(RedeemRecord::getUserId, userId)
                        .orderByDesc(RedeemRecord::getCreateTime));
    }

    /**
     * 管理后台：近期核销记录
     */
    public List<RedeemRecord> listRecent(Long locationId, int limit) {
        LambdaQueryWrapper<RedeemRecord> wrapper = new LambdaQueryWrapper<RedeemRecord>()
                .orderByDesc(RedeemRecord::getCreateTime)
                .last("LIMIT " + limit);
        if (locationId != null) wrapper.eq(RedeemRecord::getLocationId, locationId);
        return redeemMapper.selectList(wrapper);
    }
}
