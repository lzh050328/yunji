package com.hmall.trade.listener;

import com.hmall.common.constants.MqConstants;
import com.hmall.common.domain.mq.OrderCloseMessage;
import com.hmall.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Argument;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 订单超时关闭消息监听
 * <p>
 * 交换机为 x-delayed-message 类型（依赖 rabbitmq_delayed_message_exchange 插件），
 * 通过 x-delayed-type=direct 指定内部转发类型
 * <p>
 * 幂等方案：消息id 写入 Redis，已存在则直接返回；业务失败时删除标记以便重试
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCloseListener {

    private static final String IDEMPOTENT_KEY_PREFIX = "trade:order:close:";
    private static final Duration KEY_TTL = Duration.ofHours(24);

    private final IOrderService orderService;
    private final StringRedisTemplate redisTemplate;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MqConstants.TRADE_CLOSE_QUEUE, durable = "true"),
            exchange = @Exchange(name = MqConstants.TRADE_DELAY_EXCHANGE,
                    type = "x-delayed-message", durable = "true",
                    arguments = @Argument(name = "x-delayed-type", value = "direct")),
            key = MqConstants.ORDER_CLOSE_ROUTING_KEY
    ))
    public void listenOrderClose(@Payload OrderCloseMessage message,
                                 @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        if (message == null || message.getOrderId() == null) {
            log.warn("收到订单关闭消息但订单号为空，已忽略");
            return;
        }

        // 幂等标识：优先用消息id，没有则退化为订单号
        String idemKey = IDEMPOTENT_KEY_PREFIX + (messageId != null && !messageId.isEmpty()
                ? messageId : "order:" + message.getOrderId());

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(idemKey, "1", KEY_TTL);
        if (Boolean.FALSE.equals(acquired)) {
            log.info("订单 {} 的关闭消息已处理过，跳过（key：{}）", message.getOrderId(), idemKey);
            return;
        }

        try {
            log.info("开始处理订单超时关闭，订单号：{}，消息id：{}", message.getOrderId(), messageId);
            orderService.cancelOrder(message.getOrderId());
        } catch (Throwable e) {
            // 业务执行失败：释放幂等标记，让消息可以重试，否则订单永远卡在待付款
            redisTemplate.delete(idemKey);
            log.error("订单 {} 取消失败，已释放幂等标记以便重试", message.getOrderId(), e);
            throw e;
        }
    }
}
