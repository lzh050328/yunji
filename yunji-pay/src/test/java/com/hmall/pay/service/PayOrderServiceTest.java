package com.hmall.pay.service;

import com.hmall.common.utils.UserContext;
import com.hmall.pay.domain.dto.PayOrderFormDTO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class PayOrderServiceTest {

    @Resource
    private IPayOrderService payOrderService;

    @Test
    void tryPayOrderByBalance() {
        //多线程模拟并发
        var dto = PayOrderFormDTO.builder()
                .id(1658455441987891201L) // 该字段可以通过数据库中获取，获取方式见代码下方截图
                .pw("123") // 用户密码
                .build();

        for (int i = 0; i < 3; i++) {
            new Thread(() -> {
                UserContext.setUser(1L); // 设置用户id，模拟传递当前登录用户id
                payOrderService.tryPayOrderByBalance(dto);
                System.out.println("线程id = " + Thread.currentThread().getId());
            }).start();
        }

        //睡眠20秒，等待所有子线程的完成
        try {
            Thread.sleep(20000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}