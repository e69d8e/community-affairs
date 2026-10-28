package com.li.community.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "AI 流式问答请求参数")
public class AiChatStreamDto {

    @NotBlank(message = "咨询问题不能为空")
    @Schema(description = "用户当前输入提问", example = "你好，请问办理居住证明需要带哪些材料？")
    private String prompt;

    @Schema(description = "历史上下文消息列表 [{\"role\": \"user\"|\"assistant\", \"content\": \"...\"}]")
    private List<Map<String, String>> history;

    @Schema(description = "所属小区 ID (用于检索本小区专属通知与办事网格)")
    private Long communityId;
}
