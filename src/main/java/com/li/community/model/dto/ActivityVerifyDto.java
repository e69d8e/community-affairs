package com.li.community.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ActivityVerifyDto {

    @NotBlank(message = "核销验证码不能为空")
    private String verifyCode;
}
