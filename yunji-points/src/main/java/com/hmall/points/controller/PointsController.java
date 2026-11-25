package com.hmall.points.controller;

import com.hmall.common.utils.UserContext;
import com.hmall.points.domain.po.UserPoints;
import com.hmall.points.service.IPointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/points")
@RequiredArgsConstructor
public class PointsController {

    private final IPointsService pointsService;

    /**
     * 查询当前登录用户的积分
     * <p>
     * 用户身份由网关鉴权后经 user-info 头传入
     */
    @GetMapping
    public UserPoints queryMyPoints() {
        return pointsService.getByUserId(UserContext.getUser());
    }
}
