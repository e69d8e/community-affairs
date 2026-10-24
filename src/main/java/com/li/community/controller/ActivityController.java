package com.li.community.controller;

import com.li.community.common.annotation.LogOperation;
import com.li.community.common.result.PageResult;
import com.li.community.common.result.Result;
import com.li.community.model.dto.ActivityCreateDto;
import com.li.community.model.dto.ActivityVerifyDto;
import com.li.community.model.entity.BizActivity;
import com.li.community.model.entity.BizActivitySignup;
import com.li.community.model.vo.ActivityVo;
import com.li.community.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "04. 社区活动与高并发报名核销模块")
@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @Operation(summary = "公开/居民查询活动列表")
    @GetMapping("/list")
    public Result<PageResult<ActivityVo>> listActivities(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size) {
        return Result.success(activityService.listActivities(category, status, page, size));
    }

    @Operation(summary = "获取活动详情 (含当前用户报名状态)")
    @GetMapping("/detail/{id}")
    public Result<ActivityVo> getActivityDetail(@PathVariable Long id) {
        return Result.success(activityService.getActivityDetail(id));
    }

    @Operation(summary = "居民在线报名 (Redis Lua 原子防超卖)")
    @PostMapping("/signup")
    @LogOperation(title = "活动报名", businessType = "INSERT")
    public Result<ActivityVo> signupActivity(@RequestParam Long activityId) {
        return Result.success("报名成功！已下发入场签到凭证与动态二维码", activityService.signupActivity(activityId));
    }

    @Operation(summary = "居民取消报名 (回补名额)")
    @PostMapping("/cancel")
    public Result<Void> cancelSignup(@RequestParam Long activityId) {
        activityService.cancelSignup(activityId);
        return Result.success("取消报名成功", null);
    }

    @Operation(summary = "居民查询本人报名的全部活动及核销凭证")
    @GetMapping("/my-signups")
    public Result<List<ActivityVo>> getMySignups() {
        return Result.success(activityService.getMySignups());
    }

    @Operation(summary = "现场网格员/管理员扫码或输码核销签到")
    @PostMapping("/admin/verify")
    @LogOperation(title = "活动签到核销", businessType = "UPDATE")
    public Result<BizActivitySignup> verifySignup(@Valid @RequestBody ActivityVerifyDto dto) {
        return Result.success("核销成功，已登记签到！", activityService.verifySignup(dto));
    }

    @Operation(summary = "发布新社区文体公益活动")
    @PostMapping("/admin/create")
    @LogOperation(title = "发布社区活动", businessType = "INSERT")
    public Result<BizActivity> createActivity(@Valid @RequestBody ActivityCreateDto dto) {
        return Result.success("活动发布成功", activityService.createActivity(dto));
    }

    @Operation(summary = "管理员更新活动状态 (取消/完结)")
    @PutMapping("/admin/status")
    public Result<Void> updateActivityStatus(@RequestBody Map<String, Object> params) {
        Long activityId = Long.parseLong(params.get("activityId").toString());
        Integer status = Integer.parseInt(params.get("status").toString());
        activityService.updateActivityStatus(activityId, status);
        return Result.success("状态更新成功", null);
    }

    @Operation(summary = "导出活动已报名居民花名册 Excel")
    @GetMapping("/admin/export/{activityId}")
    @LogOperation(title = "导出活动花名册", businessType = "EXPORT")
    public void exportSignupsExcel(@PathVariable Long activityId, HttpServletResponse response) {
        activityService.exportSignupsExcel(activityId, response);
    }
}
