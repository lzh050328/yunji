package com.hmall.common.domain.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 订单超时关闭消息
 * <p>
 * 下单后延迟投递，到期时由交易服务判断订单是否仍未支付
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCloseMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 业务订单号
     */
    private Long orderId;
}
