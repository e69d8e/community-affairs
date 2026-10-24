package com.li.community.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkOrderHandleDto {

    @NotNull(message = "工单ID不能为空")
    private Long orderId;

    @NotBlank(message = "处理结果说明不能为空")
    private String handleResult;

    private String handleImages; // 处理后凭证图片 JSON 数组
}
