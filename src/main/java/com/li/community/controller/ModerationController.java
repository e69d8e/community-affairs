package com.li.community.controller;

import com.li.community.common.annotation.LogOperation;
import com.li.community.common.result.PageResult;
import com.li.community.common.result.Result;
import com.li.community.model.dto.ReportSubmitDto;
import com.li.community.model.dto.SensitiveWordDto;
import com.li.community.model.entity.BizReport;
import com.li.community.model.entity.BizSensitiveWord;
import com.li.community.service.ModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "06. 内容合规、敏感词与违规举报模块")
@RestController
@RequestMapping("/api/moderation")
@RequiredArgsConstructor
public class ModerationController {

    private final ModerationService moderationService;

    @Operation(summary = "居民提交违规或矛盾纠纷举报")
    @PostMapping("/report/submit")
    public Result<Void> submitReport(@Valid @RequestBody ReportSubmitDto dto) {
        moderationService.submitReport(dto);
        return Result.success("举报已受理，网格员将尽快核实处理", null);
    }

    @Operation(summary = "管理端获取举报处理工单列表 (非平台管理员仅本人小区数据)")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COMMUNITY_ADMIN', 'GRID_OFFICER')")
    @GetMapping("/admin/report/list")
    public Result<PageResult<BizReport>> listReports(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long communityId,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size) {
        return Result.success(moderationService.listReports(status, communityId, page, size));
    }

    @Operation(summary = "网格员/管理员核查举报并做出裁决 (下架/驳回)")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COMMUNITY_ADMIN', 'GRID_OFFICER')")
    @PutMapping("/admin/report/handle")
    @LogOperation(title = "举报裁决处理", businessType = "AUDIT")
    public Result<Void> handleReport(@RequestBody Map<String, Object> params) {
        Long reportId = Long.parseLong(params.get("reportId").toString());
        Integer status = Integer.parseInt(params.get("status").toString());
        String remark = params.get("remark") != null ? params.get("remark").toString() : "";
        moderationService.handleReport(reportId, status, remark);
        return Result.success("举报处理完毕", null);
    }

    @Operation(summary = "系统管理员查询敏感词库")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @GetMapping("/admin/word/list")
    public Result<PageResult<BizSensitiveWord>> listWords(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size) {
        return Result.success(moderationService.listWords(keyword, page, size));
    }

    @Operation(summary = "系统管理员添加或编辑敏感词")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PostMapping("/admin/word/save")
    @LogOperation(title = "维护敏感词库", businessType = "INSERT")
    public Result<Void> saveWord(@Valid @RequestBody SensitiveWordDto dto) {
        moderationService.saveWord(dto);
        return Result.success("敏感词保存成功并已实时同步前缀树", null);
    }

    @Operation(summary = "系统管理员删除敏感词")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @DeleteMapping("/admin/word/delete/{id}")
    @LogOperation(title = "删除敏感词", businessType = "DELETE")
    public Result<Void> deleteWord(@PathVariable Long id) {
        moderationService.deleteWord(id);
        return Result.success("删除成功", null);
    }
}
