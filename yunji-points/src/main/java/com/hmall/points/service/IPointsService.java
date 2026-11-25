package com.hmall.points.service;

import com.hmall.points.domain.po.UserPoints;

public interface IPointsService {

    /**
     * 支付成功后赠送积分（幂等：同一订单只加一次）
     *
     * @param userId  付款用户id
     * @param orderId 业务订单号
     * @return 本次实际增加的积分，0 表示重复消息被跳过
     */
    int addPointsForPaySuccess(Long userId, Long orderId);

    /**
     * 查询用户积分，不存在时返回 0 分
     */
    UserPoints getByUserId(Long userId);
}
