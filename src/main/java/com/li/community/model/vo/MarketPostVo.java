package com.li.community.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.li.community.model.entity.BizPostComment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketPostVo implements Serializable {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private Integer postType;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long communityId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;
    private String authorName;
    private String authorAvatar;
    private Integer authorCreditScore;

    private String title;
    private String description;
    private BigDecimal price;
    private Integer tradeType;
    private Integer deliveryType;
    private String tradeAddress;
    private String contactType;
    private String contactValue;
    private String images;
    private Integer status;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private Boolean hasLiked;
    private Boolean hasFavorited;

    private List<CommentVo> comments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommentVo implements Serializable {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long id;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long userId;
        private String userName;
        private String userAvatar;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long parentId;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long replyToUserId;
        private String replyToUserName;
        private String content;
        private Integer likeCount;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;
    }
}
