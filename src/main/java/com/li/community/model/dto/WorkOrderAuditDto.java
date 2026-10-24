package com.li.community.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkOrderAuditDto {

    @NotNull(message = "工单ID不能为空")
    private Long orderId;

    @NotNull(message = "审核结果不能为空")
    private Boolean pass; // true: 通过并派单, false: 驳回

    private String remark;

    // 审核通过时指派维修/处理人
    private Long handlerId;
    private String handlerName;
    private String handlerPhone;
}
