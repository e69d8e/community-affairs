package com.li.community.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AuthLoginDto {

    @NotBlank(message = "登录用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "请先完成滑块人机验证")
    private String captchaId;

    @NotNull(message = "滑块验证数据缺失，请重新验证")
    private Integer captchaX;
}
