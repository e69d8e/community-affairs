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
@TableName("biz_chat_message")
public class BizChatMessage implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private String sessionId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long postId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long senderId;

    private String senderName;

    private String senderAvatar;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long receiverId;

    private String receiverName;

    private String receiverAvatar;

    private String content;

    private Integer isRead; // 0-未读, 1-已读

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
