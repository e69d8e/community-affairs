package com.li.community.controller;

import com.li.community.common.annotation.LogOperation;
import com.li.community.common.result.PageResult;
import com.li.community.common.result.Result;
import com.li.community.model.dto.NoticeCreateDto;
import com.li.community.model.entity.SysNotice;
import com.li.community.model.entity.SysUserNotice;
import com.li.community.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "07. 社区公告、通知与实时消息中心")
@RestController
@RequestMapping("/api/notice")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @Operation(summary = "公开/居民浏览社区公告列表 (登录后默认本人小区; 匿名需携带 communityId)")
    @GetMapping("/list")
    public Result<PageResult<SysNotice>> listNotices(
            @RequestParam(required = false) Integer noticeType,
            @RequestParam(required = false) Long communityId,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size) {
        return Result.success(noticeService.listNotices(noticeType, communityId, page, size));
    }

    @Operation(summary = "获取公告详情 (自动增加阅读浏览量)")
    @GetMapping("/detail/{id}")
    public Result<SysNotice> getNoticeDetail(@PathVariable Long id) {
        return Result.success(noticeService.getNoticeDetail(id));
    }

    @Operation(summary = "管理端发布公告 (若为应急预警则瞬时全区 WebSocket 广播)")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COMMUNITY_ADMIN', 'PROPERTY_ADMIN')")
    @PostMapping("/admin/publish")
    @LogOperation(title = "发布通知通告", businessType = "INSERT", saveParam = true)
    public Result<SysNotice> publishNotice(@Valid @RequestBody NoticeCreateDto dto) {
        return Result.success("公告已发布并送达居民端", noticeService.publishNotice(dto));
    }

    @Operation(summary = "居民个人站内信列表")
    @GetMapping("/my-inbox")
    public Result<PageResult<SysUserNotice>> getMyUserNotices(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size) {
        return Result.success(noticeService.getMyUserNotices(page, size));
    }

    @Operation(summary = "标记单条站内信已读")
    @PutMapping("/read/{id}")
    public Result<Void> markNoticeRead(@PathVariable Long id) {
        noticeService.markNoticeRead(id);
        return Result.success();
    }

    @Operation(summary = "一键标记所有站内信已读")
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        noticeService.markAllRead();
        return Result.success("已全部标为已读", null);
    }

    @Operation(summary = "查询个人未读消息数量 (红点提示)")
    @GetMapping("/unread-count")
    public Result<Long> getUnreadNoticeCount() {
        return Result.success(noticeService.getUnreadNoticeCount());
    }
}
