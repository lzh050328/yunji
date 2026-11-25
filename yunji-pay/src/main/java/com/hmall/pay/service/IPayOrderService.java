package com.hmall.pay.service;

import com.hmall.pay.domain.dto.PayApplyDTO;
import com.hmall.pay.domain.dto.PayOrderFormDTO;
import com.hmall.pay.domain.po.PayOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 支付订单 服务类
 * </p>
 *
 * @author 黑马程序员
 * @since 2023-05-16
 */
public interface IPayOrderService extends IService<PayOrder> {

    String applyPayOrder(PayApplyDTO applyDTO);

    void tryPayOrderByBalance(PayOrderFormDTO payOrderFormDTO);

    /**
     * 按业务订单号查询支付单状态（供交易服务判断订单能否取消）
     *
     * @return 0-未提交 1-待支付 2-已关闭 3-支付成功；null 表示还没有支付单
     */
    Integer queryStatusByBizOrderNo(Long bizOrderNo);
}
