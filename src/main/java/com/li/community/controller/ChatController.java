package com.li.community.controller;

import com.li.community.common.result.Result;
import com.li.community.model.dto.ChatMessageDto;
import com.li.community.model.vo.ChatMessageVo;
import com.li.community.model.vo.ChatSessionVo;
import com.li.community.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "09. 邻里闲置互助私信沟通模块")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "发送私信消息")
    @PostMapping("/send")
    public Result<ChatMessageVo> sendMessage(@Valid @RequestBody ChatMessageDto dto) {
        return Result.success("消息发送成功", chatService.sendMessage(dto));
    }

    @Operation(summary = "查询某特定帖子下的私信历史记录")
    @GetMapping("/messages")
    public Result<List<ChatMessageVo>> getMessages(
            @RequestParam Long postId,
            @RequestParam Long otherUserId) {
        return Result.success(chatService.getMessages(postId, otherUserId));
    }

    @Operation(summary = "获取当前用户的所有私信会话列表")
    @GetMapping("/sessions")
    public Result<List<ChatSessionVo>> getMyChatSessions() {
        return Result.success(chatService.getMyChatSessions());
    }

    @Operation(summary = "标记某会话所有消息为已读")
    @PutMapping("/read")
    public Result<Void> markSessionAsRead(@RequestBody Map<String, String> params) {
        String sessionId = params.get("sessionId");
        if (sessionId != null) {
            chatService.markSessionAsRead(sessionId);
        }
        return Result.success("已标记为已读", null);
    }

    @Operation(summary = "获取当前用户的未读私信总数")
    @GetMapping("/unread-count")
    public Result<Integer> getUnreadChatCount() {
        return Result.success(chatService.getUnreadChatCount());
    }
}
