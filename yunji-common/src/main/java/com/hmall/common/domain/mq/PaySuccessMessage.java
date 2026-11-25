package com.hmall.common.domain.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 支付成功消息
 * <p>
 * 同时承载「订单号」与「付款用户id」：
 * 交易服务用订单号更新订单状态；积分服务用用户id加积分。
 * 带上 userId 是为了避免积分服务再反向调用交易服务查订单
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaySuccessMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 业务订单号
     */
    private Long orderId;

    /**
     * 付款用户id
     */
    private Long userId;
}
