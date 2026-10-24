package com.li.community.controller;

import com.li.community.common.annotation.LogOperation;
import com.li.community.common.result.PageResult;
import com.li.community.common.result.Result;
import com.li.community.model.dto.UserUpdateDto;
import com.li.community.model.vo.UserVo;
import com.li.community.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "11. 用户中心与 RBAC 权限管理模块")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "用户修改个人信息资料")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody UserUpdateDto dto) {
        userService.updateProfile(dto);
        return Result.success("个人资料更新成功", null);
    }

    @Operation(summary = "用户修改登录密码")
    @PutMapping("/password")
    public Result<Void> updatePassword(@RequestBody Map<String, String> params) {
        String oldPassword = params.get("oldPassword");
        String newPassword = params.get("newPassword");
        userService.updatePassword(oldPassword, newPassword);
        return Result.success("密码修改成功，请重新登录", null);
    }

    @Operation(summary = "系统管理员查询用户列表 (支持按小区过滤, 不传小区查全部)")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @GetMapping("/admin/list")
    public Result<PageResult<UserVo>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Long communityId,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size) {
        return Result.success(userService.listUsers(keyword, role, communityId, page, size));
    }

    @Operation(summary = "系统管理员启用/停用用户账号")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PutMapping("/admin/status")
    @LogOperation(title = "用户状态变更", businessType = "UPDATE")
    public Result<Void> updateUserStatus(@RequestBody Map<String, Object> params) {
        Long userId = Long.parseLong(params.get("userId").toString());
        Integer status = Integer.parseInt(params.get("status").toString());
        userService.updateUserStatus(userId, status);
        return Result.success("账号状态已变更", null);
    }

    @Operation(summary = "系统管理员分配用户角色")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PutMapping("/admin/role")
    @LogOperation(title = "用户角色分配", businessType = "UPDATE")
    public Result<Void> updateUserRole(@RequestBody Map<String, Object> params) {
        Long userId = Long.parseLong(params.get("userId").toString());
        String role = params.get("role").toString();
        userService.updateUserRole(userId, role);
        return Result.success("角色权限更新成功", null);
    }
}
