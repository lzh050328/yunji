package com.hmall;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 积分服务
 * <p>
 * 放在 com.hmall 包下，才能扫描到 hm-common 里的公共配置类
 */
@MapperScan("com.hmall.points.mapper")
@SpringBootApplication
public class PointsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PointsServiceApplication.class, args);
    }
}
