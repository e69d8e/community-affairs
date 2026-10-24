package com.li.community.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReportSubmitDto {

    @NotBlank(message = "举报对象类型不能为空")
    private String targetType; // POST, COMMENT, USER

    @NotNull(message = "举报对象ID不能为空")
    private Long targetId;

    @NotBlank(message = "举报分类不能为空")
    private String reasonCategory;

    private String description;
    private String evidenceImages;
}
