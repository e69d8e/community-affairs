package com.li.community.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "管理端工单 AI 智能分诊研判入参")
public class AiOrderTriageDto {

    @NotNull(message = "工单ID不能为空")
    @Schema(description = "工单ID")
    private Long orderId;

    @Schema(description = "工单标题")
    private String title;

    @Schema(description = "工单类型：1-报修, 2-投诉建议")
    private Integer orderType;

    @Schema(description = "分类")
    private String category;

    @Schema(description = "申报详情正文")
    private String content;

    @Schema(description = "现场照片URL列表")
    private List<String> images;

    @Schema(description = "当前紧急度：0-普通, 1-紧急")
    private Integer isUrgent;
}
