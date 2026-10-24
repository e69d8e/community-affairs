package com.li.community.controller;

import com.li.community.common.result.Result;
import com.li.community.model.vo.DashboardMetricsVo;
import com.li.community.model.vo.ScreenDataVo;
import com.li.community.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "09. 数据看板与 16:9 全屏指挥驾驶舱模块")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "管理端工作台首页日常统计卡片指标")
    @GetMapping("/dashboard/metrics")
    public Result<DashboardMetricsVo> getMetrics() {
        return Result.success(dashboardService.getMetrics());
    }

    @Operation(summary = "16:9 沉浸式科技感全屏指挥驾驶舱聚合数据包")
    @GetMapping("/screen/data")
    public Result<ScreenDataVo> getScreenData() {
        return Result.success(dashboardService.getScreenData());
    }
}
