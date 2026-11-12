package com.li.community.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AuthRegisterDto {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$", message = "用户名需为3-20位字母、数字或下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    @NotBlank(message = "手机号码不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入有效的11位手机号码")
    private String phone;

    @NotNull(message = "请选择要加入的小区")
    private Long communityId;

    @NotBlank(message = "请先完成滑块人机验证")
    private String captchaId;

    @NotNull(message = "滑块验证数据缺失，请重新验证")
    private Integer captchaX;
}
