package com.hmall.pay.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.api.trade.TradeClient;
import com.hmall.api.user.UserClient;
import com.hmall.common.constants.MqConstants;
import com.hmall.common.domain.mq.PaySuccessMessage;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.common.utils.UserContext;
import com.hmall.pay.domain.dto.PayApplyDTO;
import com.hmall.pay.domain.dto.PayOrderFormDTO;
import com.hmall.pay.domain.po.PayOrder;
import com.hmall.pay.enums.PayStatus;
import com.hmall.pay.mapper.PayOrderMapper;
import com.hmall.pay.service.IPayOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * <p>
 * 支付订单 服务实现类
 * </p>
 *
 * @author 黑马程序员
 * @since 2023-05-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayOrderServiceImpl extends ServiceImpl<PayOrderMapper, PayOrder> implements IPayOrderService {

    private final UserClient userClient;

    private final TradeClient tradeClient;

    /**
     * RabbitMQ 模板：支付成功后发送消息，由交易服务异步更新订单状态
     */
    private final RabbitTemplate rabbitTemplate;

    @Override
    public String applyPayOrder(PayApplyDTO applyDTO) {
        // 1.查询业务订单（远程调用 trade-service）
        var order = tradeClient.queryOrderById(applyDTO.getBizOrderNo());
        if (order == null) {
            throw new BizIllegalException("订单不存在");
        }
        // 2.校验订单归属，防止替他人订单付款
        if (!order.getUserId().equals(UserContext.getUser())) {
            throw new BizIllegalException("无权操作该订单");
        }
        // 3.校验订单状态，只有未付款订单可以发起支付
        if (!Integer.valueOf(1).equals(order.getStatus())) {
            throw new BizIllegalException("订单状态异常，无法发起支付");
        }
        // 4.支付金额以订单金额为准，忽略客户端传值，防止金额篡改
        applyDTO.setAmount(order.getTotalFee());
        // 5.幂等性校验
        var payOrder = checkIdempotent(applyDTO);
        // 6.返回结果
        return payOrder.getId().toString();
    }

    @Override
    public Integer queryStatusByBizOrderNo(Long bizOrderNo) {
        var po = queryByBizOrderNo(bizOrderNo);
        return po == null ? null : po.getStatus();
    }

    @Override
    @Transactional
    public void tryPayOrderByBalance(PayOrderFormDTO payOrderFormDTO) {
        // 1.查询支付单
        var po = getById(payOrderFormDTO.getId());
        // 1.1 支付单不存在
        if (po == null) {
            throw new BizIllegalException("支付单不存在");
        }
        // 1.2 校验支付单归属，防止使用他人支付单
        if (!po.getBizUserId().equals(UserContext.getUser())) {
            throw new BizIllegalException("无权操作该支付单");
        }
        // 2.判断状态
        if(!PayStatus.WAIT_BUYER_PAY.equalsValue(po.getStatus())){
            // 订单不是未支付，状态异常
            throw new BizIllegalException("交易已支付或关闭！");
        }
        // 3.尝试扣减余额（远程调用 user-service）
        userClient.deductMoney(payOrderFormDTO.getPw(), po.getAmount());
        // 4.修改支付单状态
        boolean success = markPayOrderSuccess(payOrderFormDTO.getId(), LocalDateTime.now());
        if (!success) {
            throw new BizIllegalException("交易已支付或关闭！");
        }
        // 5.发送支付成功消息到 pay.direct，由交易服务异步更新订单状态
        // 注意：此时余额已扣、支付单已置为成功，发送失败绝不能向上抛异常——
        // 一旦抛出会回滚本地事务，导致「钱扣了但支付单显示未支付」的资损问题
        try {
            // 消息体带上 userId：积分服务需要知道给谁加分
            var message = new PaySuccessMessage(po.getBizOrderNo(), po.getBizUserId());
            rabbitTemplate.convertAndSend(
                    MqConstants.PAY_DIRECT_EXCHANGE,
                    MqConstants.PAY_SUCCESS_ROUTING_KEY,
                    message
            );
            log.info("支付成功消息已发送，交换机：{}，路由键：{}，订单号：{}，用户：{}",
                    MqConstants.PAY_DIRECT_EXCHANGE, MqConstants.PAY_SUCCESS_ROUTING_KEY,
                    po.getBizOrderNo(), po.getBizUserId());
        } catch (Throwable e) {
            log.error("支付成功但消息发送失败，需人工补偿。支付单：{}，订单：{}",
                    po.getId(), po.getBizOrderNo(), e);
        }

        // ===== 原 Feign 同步调用，已由 MQ 异步消息替代，保留备查 =====
        // tradeClient.markOrderPaySuccess(po.getBizOrderNo());
    }

    public boolean markPayOrderSuccess(Long id, LocalDateTime successTime) {
        return lambdaUpdate()
                .set(PayOrder::getStatus, PayStatus.TRADE_SUCCESS.getValue())
                .set(PayOrder::getPaySuccessTime, successTime)
                .eq(PayOrder::getId, id)
                // 支付状态的乐观锁判断
                .in(PayOrder::getStatus, PayStatus.NOT_COMMIT.getValue(), PayStatus.WAIT_BUYER_PAY.getValue())
                .update();
    }


    private PayOrder checkIdempotent(PayApplyDTO applyDTO) {
        // 1.首先查询支付单
        var oldOrder = queryByBizOrderNo(applyDTO.getBizOrderNo());
        // 2.判断是否存在
        if (oldOrder == null) {
            // 不存在支付单，说明是第一次，写入新的支付单并返回
            var payOrder = buildPayOrder(applyDTO);
            payOrder.setPayOrderNo(IdWorker.getId());
            save(payOrder);
            return payOrder;
        }
        // 3.旧单已经存在，判断是否支付成功
        if (PayStatus.TRADE_SUCCESS.equalsValue(oldOrder.getStatus())) {
            // 已经支付成功，抛出异常
            throw new BizIllegalException("订单已经支付！");
        }
        // 4.旧单已经存在，判断是否已经关闭
        if (PayStatus.TRADE_CLOSED.equalsValue(oldOrder.getStatus())) {
            // 已经关闭，抛出异常
            throw new BizIllegalException("订单已关闭");
        }
        // 5.旧单已经存在，判断支付渠道是否一致
        if (!StringUtils.equals(oldOrder.getPayChannelCode(), applyDTO.getPayChannelCode())) {
            // 支付渠道不一致，需要重置数据，然后重新申请支付单
            var payOrder = buildPayOrder(applyDTO);
            payOrder.setId(oldOrder.getId());
            payOrder.setQrCodeUrl("");
            updateById(payOrder);
            payOrder.setPayOrderNo(oldOrder.getPayOrderNo());
            return payOrder;
        }
        // 6.旧单已经存在，且可能是未支付或未提交，且支付渠道一致
        // 6.1 若金额与订单实际金额不一致，以订单金额为准刷新，避免旧支付单残留错误金额
        if (!Objects.equals(oldOrder.getAmount(), applyDTO.getAmount())) {
            oldOrder.setAmount(applyDTO.getAmount());
            updateById(oldOrder);
        }
        // 6.2 直接返回旧数据
        return oldOrder;
    }

    private PayOrder buildPayOrder(PayApplyDTO payApplyDTO) {
        // 1.数据转换
        var payOrder = BeanUtils.toBean(payApplyDTO, PayOrder.class);
        // 2.初始化数据
        payOrder.setPayOverTime(LocalDateTime.now().plusMinutes(120L));
        payOrder.setStatus(PayStatus.WAIT_BUYER_PAY.getValue());
        payOrder.setBizUserId(UserContext.getUser());
        return payOrder;
    }
    public PayOrder queryByBizOrderNo(Long bizOrderNo) {
        return lambdaQuery()
                .eq(PayOrder::getBizOrderNo, bizOrderNo)
                .one();
    }
}
