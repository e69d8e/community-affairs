package com.li.community.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "邻里私信会话列表项 VO")
public class ChatSessionVo implements Serializable {

    private String sessionId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long postId;

    private String postTitle;

    private BigDecimal postPrice;

    private String postCover;

    private Integer postType;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long otherUserId;

    private String otherUserName;

    private String otherUserAvatar;

    private Integer otherUserCreditScore;

    private String lastMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastTime;

    private Integer unreadCount;
}
