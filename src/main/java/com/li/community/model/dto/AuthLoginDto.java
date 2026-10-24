package com.li.community.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthLoginDto {

    @NotBlank(message = "登录用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
