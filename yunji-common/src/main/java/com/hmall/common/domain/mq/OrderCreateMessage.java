package com.hmall.common.domain.mq;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 下单成功消息
 * <p>
 * 消息体必须自带用户信息：购物车服务在消费消息时没有 HTTP 请求上下文，
 * UserContext 为空，无法像同步调用那样从拦截器里取用户id
 */
@Data
public class OrderCreateMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 下单用户id
     */
    private Long userId;

    /**
     * 本次下单的商品明细
     */
    private List<OrderItem> items;

    /**
     * 下单商品项
     */
    @Data
    public static class OrderItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 商品id
         */
        private Long itemId;

        /**
         * 购买数量
         */
        private Integer num;
    }
}
