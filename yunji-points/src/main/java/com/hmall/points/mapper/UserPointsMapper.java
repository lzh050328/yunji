package com.hmall.points.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmall.points.domain.po.UserPoints;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface UserPointsMapper extends BaseMapper<UserPoints> {

    /**
     * 原子累加积分，用户记录不存在时自动初始化
     * <p>
     * 用 ON DUPLICATE KEY UPDATE 一条 SQL 完成，避免"先查后改"的并发问题
     */
    @Insert("INSERT INTO user_points(user_id, points) VALUES(#{userId}, #{points}) " +
            "ON DUPLICATE KEY UPDATE points = points + #{points}")
    int addPoints(@Param("userId") Long userId, @Param("points") Integer points);
}
