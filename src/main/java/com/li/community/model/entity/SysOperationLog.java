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
@TableName("sys_operation_log")
public class SysOperationLog implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private String title;
    private String businessType;
    private String method;
    private String requestMethod;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long operatorId;
    private String operatorName;
    private String operatorRole;
    private String operUrl;
    private String operIp;
    private String operParam;
    private String jsonResult;
    private Integer status; // 1-成功, 0-异常
    private String errorMsg;
    private Long executionTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operTime;
}
