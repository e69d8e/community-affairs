package com.li.community.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("biz_order_log")
public class BizOrderLog implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long orderId;

    private String actionType; // SUBMIT, REVIEW_PASS, REVIEW_REJECT, DISPATCH, START_HANDLE, COMPLETE, EVALUATE
    private Integer fromStatus;
    private Integer toStatus;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long operatorId;

    private String operatorName;
    private String operatorRole;
    private String remark;
    private String evidenceImages;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
