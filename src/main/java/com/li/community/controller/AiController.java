package com.li.community.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.community.ai.service.AiAssistantService;
import com.li.community.common.result.PageResult;
import com.li.community.common.result.Result;
import com.li.community.common.utils.SecurityUtils;
import com.li.community.model.dto.AiOrderParseDto;
import com.li.community.model.dto.AiOrderTriageDto;
import com.li.community.model.vo.AiChatRecordVo;
import com.li.community.model.vo.AiOrderParseVo;
import com.li.community.model.vo.AiOrderTriageVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "11. 社区 AI 智能化服务中枢")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiAssistantService aiAssistantService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "AI 社区百事通：流式 SSE 问答接口")
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @RequestParam("prompt") String prompt,
            @RequestParam(value = "history", required = false) String historyJson,
            @RequestParam(value = "communityId", required = false) Long communityId) {

        List<Map<String, String>> history = Collections.emptyList();
        if (historyJson != null && !historyJson.isBlank()) {
            try {
                history = objectMapper.readValue(historyJson, new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("反序列化 history 上下文失败: {}", e.getMessage());
            }
        }

        Long userId = SecurityUtils.isLoggedIn() ? SecurityUtils.getUserId() : null;
        return aiAssistantService.chatStream(prompt, history, communityId, userId);
    }

    @Operation(summary = "AI 社区百事通：分页查询当前用户历史对话记录")
    @GetMapping("/chat/history")
    public Result<PageResult<AiChatRecordVo>> getChatHistory(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "15") Long size) {
        Long userId = SecurityUtils.isLoggedIn() ? SecurityUtils.getUserId() : null;
        return Result.success(aiAssistantService.getChatHistory(userId, page, size));
    }

    @Operation(summary = "AI 社区百事通：清空当前用户的全部历史对话记录")
    @DeleteMapping("/chat/history")
    public Result<Void> clearChatHistory() {
        Long userId = SecurityUtils.isLoggedIn() ? SecurityUtils.getUserId() : null;
        aiAssistantService.clearChatHistory(userId);
        return Result.success("历史会话已清空", null);
    }

    @Operation(summary = "居民端：口语化报修一键结构化整理")
    @PostMapping("/order/parse")
    public Result<AiOrderParseVo> parseOrder(@Valid @RequestBody AiOrderParseDto dto) {
        return Result.success("AI 结构化提炼完成", aiAssistantService.parseOrder(dto));
    }

    @Operation(summary = "管理端：工单初审 AI 智能分诊与派工建议研判")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COMMUNITY_ADMIN', 'PROPERTY_ADMIN', 'GRID_OFFICER')")
    @PostMapping("/admin/order/triage")
    public Result<AiOrderTriageVo> triageOrder(@Valid @RequestBody AiOrderTriageDto dto) {
        return Result.success("AI 智能分诊研判完成", aiAssistantService.triageOrder(dto));
    }
}
