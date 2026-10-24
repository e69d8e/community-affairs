package com.li.community.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "私信消息 VO")
public class ChatMessageVo implements Serializable {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private String sessionId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long postId;

    private String postTitle;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long senderId;

    private String senderName;

    private String senderAvatar;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long receiverId;

    private String receiverName;

    private String receiverAvatar;

    private String content;

    private Integer isRead;

    private Boolean isSelf; // 是否是当前登录用户发送的

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
