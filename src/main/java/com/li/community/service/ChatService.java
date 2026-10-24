package com.li.community.service;

import com.li.community.model.dto.ChatMessageDto;
import com.li.community.model.vo.ChatMessageVo;
import com.li.community.model.vo.ChatSessionVo;

import java.util.List;

public interface ChatService {

    /**
     * 发送私信
     */
    ChatMessageVo sendMessage(ChatMessageDto dto);

    /**
     * 查询某特定帖子下的双方私信历史
     */
    List<ChatMessageVo> getMessages(Long postId, Long otherUserId);

    /**
     * 查询当前用户的所有私信会话列表
     */
    List<ChatSessionVo> getMyChatSessions();

    /**
     * 标记某会话所有消息为已读
     */
    void markSessionAsRead(String sessionId);

    /**
     * 获取当前用户的未读私信总数
     */
    int getUnreadChatCount();
}
