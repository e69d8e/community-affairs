package com.li.community.controller;

import com.li.community.common.annotation.LogOperation;
import com.li.community.common.result.PageResult;
import com.li.community.common.result.Result;
import com.li.community.model.dto.CommunitySaveDto;
import com.li.community.model.entity.BizCommunity;
import com.li.community.service.CommunityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "12. 小区信息管理模块 (多社区)")
@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @Operation(summary = "系统管理员分页查询小区列表")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @GetMapping("/admin/list")
    public Result<PageResult<BizCommunity>> listCommunities(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size) {
        return Result.success(communityService.listCommunities(keyword, page, size));
    }

    @Operation(summary = "系统管理员新增小区")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @LogOperation(title = "小区信息管理", businessType = "INSERT")
    @PostMapping("/admin/create")
    public Result<Void> createCommunity(@Valid @RequestBody CommunitySaveDto dto) {
        communityService.createCommunity(dto);
        return Result.success("小区创建成功", null);
    }

    @Operation(summary = "系统管理员编辑小区基础信息")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @LogOperation(title = "小区信息管理", businessType = "UPDATE")
    @PutMapping("/admin/update")
    public Result<Void> updateCommunity(@Valid @RequestBody CommunitySaveDto dto) {
        communityService.updateCommunity(dto);
        return Result.success("小区信息更新成功", null);
    }

    @Operation(summary = "系统管理员启用/停用小区")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @LogOperation(title = "小区状态变更", businessType = "UPDATE")
    @PutMapping("/admin/status")
    public Result<Void> updateCommunityStatus(@RequestBody Map<String, Object> params) {
        Long communityId = Long.parseLong(params.get("communityId").toString());
        Integer status = Integer.parseInt(params.get("status").toString());
        communityService.updateCommunityStatus(communityId, status);
        return Result.success("小区运营状态已变更", null);
    }
}
