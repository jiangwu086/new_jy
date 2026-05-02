package com.chocwell.jy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chocwell.jy.entity.GiftItem;
import com.chocwell.jy.entity.PointsLog;
import com.chocwell.jy.entity.RedeemRecord;
import com.chocwell.jy.entity.SysUser;
import com.chocwell.jy.mapper.GiftMapper;
import com.chocwell.jy.mapper.PointsLogMapper;
import com.chocwell.jy.mapper.RedeemMapper;
import com.chocwell.jy.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class GiftService {

    private final GiftMapper      giftMapper;
    private final UserMapper      userMapper;
    private final RedeemMapper    redeemMapper;
    private final PointsLogMapper pointsLogMapper;
    private final WxService       wxService;

    // ─── 小程序端：礼品列表 ──────────────────────────────────

    /**
     * 获取用户可见的礼品列表：所有上架礼品（平台 + 全部机构）
     * orgId 参数保留兼容旧调用，实际不再用于过滤
     * TTL 5 分钟（见 RedisConfig）
     */
    @Cacheable(value = "gifts", key = "'_all'")
    public List<GiftItem> listForUser(Long orgId) {
        return giftMapper.selectList(
                new LambdaQueryWrapper<GiftItem>()
                        .eq(GiftItem::getStatus, 1)
                        .orderByDesc(GiftItem::getSortOrder)
                        .orderByDesc(GiftItem::getCreateTime));
    }

    // ─── 小程序端：在线兑换 ──────────────────────────────────

    /**
     * 用户在线兑换礼品：
     * 1. 库存原子扣减（-1 = 不限库存跳过）
     * 2. 用户积分扣减
     * 3. 写核销记录 + 积分流水
     */
    @Transactional(rollbackFor = Exception.class)
    public RedeemRecord exchange(Long userId, Long giftId) {
        GiftItem gift = giftMapper.selectById(giftId);
        if (gift == null || gift.getStatus() == 0) {
            throw new IllegalArgumentException("礼品不存在或已下架");
        }

        // 扣减库存（有限库存时原子操作）
        if (gift.getStock() != -1) {
            int affected = giftMapper.decreaseStock(giftId);
            if (affected == 0) {
                throw new IllegalStateException("该礼品库存不足");
            }
        }

        // 检查并扣减用户积分
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        int cost = gift.getPointsCost();
        if (user.getTotalPoints() < cost) {
            throw new IllegalStateException("积分不足（需要 " + cost + " 分，当前 " + user.getTotalPoints() + " 分）");
        }
        userMapper.addPoints(userId, -cost);

        // 写核销记录（线上兑换 location_id 为 null）
        RedeemRecord record = new RedeemRecord();
        record.setUserId(userId);
        // locationId 可为 null（线上兑换），DB 列已设为可空
        record.setPointsUsed(cost);
        record.setOperatorId(null);     // 用户自助兑换
        record.setGiftId(giftId);
        record.setGiftName(gift.getName());
        record.setRemark("在线兑换 — " + gift.getName());
        redeemMapper.insert(record);

        // 写积分流水：type=4 在线兑换礼品（区别于 type=2 线下核销）
        PointsLog log = new PointsLog();
        log.setUserId(userId);
        log.setType(4);
        log.setAmount(-cost);
        log.setRelatedId(record.getId());
        log.setRemark("在线兑换礼品 — " + gift.getName());
        pointsLogMapper.insert(log);

        // 异步推送订阅消息（事务提交后执行，失败不回滚）
        final String openid    = user.getOpenid();
        final String giftName  = gift.getName();
        final int    finalCost = cost;
        final String timeStr   = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        CompletableFuture.runAsync(() ->
            wxService.notifyGiftExchange(openid, giftName, finalCost, timeStr)
        );

        return record;
    }

    // ─── 管理端：礼品 CRUD ───────────────────────────────────

    /** 平台管理：获取所有礼品（含机构礼品） */
    public List<GiftItem> listAll() {
        return giftMapper.selectList(
                new LambdaQueryWrapper<GiftItem>().orderByDesc(GiftItem::getCreateTime));
    }

    /** 机构管理：获取本机构礼品（不含平台礼品） */
    public List<GiftItem> listByOrg(Long orgId) {
        return giftMapper.selectList(
                new LambdaQueryWrapper<GiftItem>()
                        .eq(GiftItem::getOrgId, orgId)
                        .orderByDesc(GiftItem::getCreateTime));
    }

    /** 保存礼品后清除 gifts 缓存 */
    @CacheEvict(value = "gifts", allEntries = true)
    public GiftItem save(GiftItem gift, Long orgId) {
        // orgId 不为 null 时强制绑定到机构（防止越权）
        if (orgId != null) gift.setOrgId(orgId);
        if (gift.getId() == null) {
            giftMapper.insert(gift);
        } else {
            // 机构管理员只能修改自己机构的礼品
            if (orgId != null) {
                GiftItem exist = giftMapper.selectById(gift.getId());
                if (exist == null || !orgId.equals(exist.getOrgId())) {
                    throw new IllegalArgumentException("无权操作此礼品");
                }
            }
            giftMapper.updateById(gift);
        }
        return gift;
    }

    /** 删除礼品后清除 gifts 缓存 */
    @CacheEvict(value = "gifts", allEntries = true)
    public void delete(Long id, Long orgId) {
        if (orgId != null) {
            GiftItem exist = giftMapper.selectById(id);
            if (exist == null || !orgId.equals(exist.getOrgId())) {
                throw new IllegalArgumentException("无权操作此礼品");
            }
        }
        giftMapper.deleteById(id);
    }
}
