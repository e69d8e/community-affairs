package com.li.community.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "发送私信 DTO")
public class ChatMessageDto {

    @NotNull(message = "关联闲置/互助帖子ID不能为空")
    @Schema(description = "关联帖子ID")
    private Long postId;

    @NotNull(message = "接收方用户ID不能为空")
    @Schema(description = "接收方用户ID")
    private Long receiverId;

    @NotBlank(message = "私信内容不能为空")
    @Size(max = 500, message = "私信内容最多500字")
    @Schema(description = "私信内容")
    private String content;
}
