package com.hmall.points.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分变动流水
 * <p>
 * order_id 建了唯一索引，作为消息幂等的兜底
 */
@Data
@TableName("points_record")
public class PointsRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 业务订单号，唯一
     */
    private Long orderId;

    /**
     * 本次变动积分
     */
    private Integer points;

    private LocalDateTime createTime;
}
