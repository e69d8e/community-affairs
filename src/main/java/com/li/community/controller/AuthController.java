package com.li.community.controller;

import com.li.community.common.result.Result;
import com.li.community.model.dto.AuthLoginDto;
import com.li.community.model.dto.AuthRegisterDto;
import com.li.community.model.vo.LoginVo;
import com.li.community.model.vo.UserVo;
import com.li.community.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "01. 认证与会话模块")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户登录 (五大角色统一认证入口)")
    @PostMapping("/login")
    public Result<LoginVo> login(@Valid @RequestBody AuthLoginDto dto) {
        return Result.success("登录成功", authService.login(dto));
    }

    @Operation(summary = "普通居民在线注册")
    @PostMapping("/register")
    public Result<LoginVo> register(@Valid @RequestBody AuthRegisterDto dto) {
        return Result.success("注册成功", authService.register(dto));
    }

    @Operation(summary = "用户登出退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success("登出成功", null);
    }

    @Operation(summary = "获取当前登录用户详情与关联房产")
    @GetMapping("/current")
    public Result<UserVo> getCurrentUser() {
        return Result.success(authService.getCurrentUser());
    }
}
