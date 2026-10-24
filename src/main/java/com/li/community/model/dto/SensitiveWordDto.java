package com.li.community.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SensitiveWordDto {

    private Long id;

    @NotBlank(message = "敏感词内容不能为空")
    private String word;

    private String category; // POLITICS, VIOLENCE, VULGAR, FRAUD, AD
    private String action; // REJECT, REPLACE
    private Integer status; // 1-启用, 0-停用
}
