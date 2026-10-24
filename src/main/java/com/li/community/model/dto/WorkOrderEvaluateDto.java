package com.li.community.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkOrderEvaluateDto {

    @NotNull(message = "工单ID不能为空")
    private Long orderId;

    @NotNull(message = "评分星级不能为空")
    @Min(value = 1, message = "最低为1星")
    @Max(value = 5, message = "最高为5星")
    private Integer ratingStar;

    private String ratingContent;
}
