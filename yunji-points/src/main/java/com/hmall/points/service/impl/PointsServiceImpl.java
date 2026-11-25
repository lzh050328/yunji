package com.hmall.points.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmall.points.domain.po.PointsRecord;
import com.hmall.points.domain.po.UserPoints;
import com.hmall.points.mapper.PointsRecordMapper;
import com.hmall.points.mapper.UserPointsMapper;
import com.hmall.points.service.IPointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointsServiceImpl implements IPointsService {

    /**
     * 每笔支付成功赠送的积分
     */
    public static final int POINTS_PER_ORDER = 2;

    private final UserPointsMapper userPointsMapper;
    private final PointsRecordMapper pointsRecordMapper;

    @Override
    @Transactional
    public int addPointsForPaySuccess(Long userId, Long orderId) {
        // 1.幂等校验：同一订单只发一次积分
        var count = pointsRecordMapper.selectCount(
                new LambdaQueryWrapper<PointsRecord>().eq(PointsRecord::getOrderId, orderId));
        if (count != null && count > 0) {
            log.info("订单 {} 的积分已发放过，跳过，用户：{}", orderId, userId);
            return 0;
        }

        // 2.写积分流水（order_id 唯一索引是最后一道防线）
        var record = new PointsRecord();
        record.setUserId(userId);
        record.setOrderId(orderId);
        record.setPoints(POINTS_PER_ORDER);
        pointsRecordMapper.insert(record);

        // 3.原子累加积分，用户首次获得积分时自动建记录
        userPointsMapper.addPoints(userId, POINTS_PER_ORDER);

        log.info("积分发放成功，用户：{}，订单：{}，+{} 分", userId, orderId, POINTS_PER_ORDER);
        return POINTS_PER_ORDER;
    }

    @Override
    public UserPoints getByUserId(Long userId) {
        var up = userPointsMapper.selectOne(
                new LambdaQueryWrapper<UserPoints>().eq(UserPoints::getUserId, userId));
        if (up == null) {
            var empty = new UserPoints();
            empty.setUserId(userId);
            empty.setPoints(0);
            return empty;
        }
        return up;
    }
}
