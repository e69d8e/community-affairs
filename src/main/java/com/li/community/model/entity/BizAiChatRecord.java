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
@TableName("biz_ai_chat_record")
public class BizAiChatRecord implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;

    /**
     * 对话角色: user / assistant
     */
    private String role;

    /**
     * 对话内容
     */
    private String content;

    /**
     * 业务直达卡片 JSON
     */
    private String cardJson;

    /**
     * 关联小区ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long communityId;

    /**
     * 逻辑删除: 0-未删, 1-已删
     */
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
