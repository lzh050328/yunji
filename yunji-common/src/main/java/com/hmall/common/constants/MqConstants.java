package com.hmall.common.constants;

/**
 * 消息队列常量
 * <p>
 * 交换机、队列、路由键统一在此定义，禁止在业务代码中硬编码字符串
 */
public final class MqConstants {

    private MqConstants() {
    }

    /**
     * 支付成功交换机（direct 类型）
     */
    public static final String PAY_DIRECT_EXCHANGE = "pay.direct";

    /**
     * 支付成功队列
     */
    public static final String PAY_SUCCESS_QUEUE = "pay.success.queue";

    /**
     * 支付成功路由键
     */
    public static final String PAY_SUCCESS_ROUTING_KEY = "pay.success";

    /**
     * 支付成功积分队列：积分服务消费，用于给用户加积分
     * <p>
     * 与 pay.success.queue 绑定同一个 routingKey，direct 交换机会把消息同时投递到两个队列，
     * 两个消费者各收一份、互不抢夺
     */
    public static final String PAY_POINTS_QUEUE = "pay.points.queue";

    /* ==================== 下单成功（topic） ==================== */

    /**
     * 交易交换机（topic 类型）
     */
    public static final String TRADE_TOPIC_EXCHANGE = "trade.topic";

    /**
     * 购物车清理队列
     */
    public static final String CART_CLEAR_QUEUE = "cart.clear.queue";

    /**
     * 下单成功路由键
     */
    public static final String ORDER_CREATE_ROUTING_KEY = "order.create";

    /* ==================== 订单超时取消（延迟消息） ==================== */

    /**
     * 订单延迟交换机（x-delayed-message 类型，依赖 rabbitmq_delayed_message_exchange 插件）
     */
    public static final String TRADE_DELAY_EXCHANGE = "trade.delay.direct";

    /**
     * 订单关闭队列：接收延迟到期的订单号，由交易服务判断是否需要取消
     */
    public static final String TRADE_CLOSE_QUEUE = "trade.close.queue";

    /**
     * 订单关闭路由键
     */
    public static final String ORDER_CLOSE_ROUTING_KEY = "order.close";

    /**
     * 延迟时长：30 分 30 秒（毫秒）
     * <p>
     * 业务超时是 30 分钟，这里多留 30 秒，避免用户在 30 分钟临界点刚支付成功、
     * 而支付消息还在途中时订单被误取消
     */
    public static final long ORDER_TIMEOUT_DELAY_MS = 30 * 60 * 1000L + 30 * 1000L;
}
