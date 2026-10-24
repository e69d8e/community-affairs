package com.li.community.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkOrderSubmitDto {

    @NotNull(message = "工单类型不能为空")
    private Integer orderType; // 1-报修, 2-投诉建议

    @NotBlank(message = "业务分类不能为空")
    private String category;

    private Long communityId;
    private Long buildingId;
    private Long houseId;

    @NotBlank(message = "工单标题简述不能为空")
    private String title;

    @NotBlank(message = "详细情况描述不能为空")
    private String content;

    private String images; // JSON 图片 URL 数组
    private Integer isUrgent; // 0-普通, 1-紧急
}
