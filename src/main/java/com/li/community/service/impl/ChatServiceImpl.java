package com.li.community.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.li.community.common.exception.BizException;
import com.li.community.common.exception.ErrorCode;
import com.li.community.common.utils.SecurityUtils;
import com.li.community.mapper.BizChatMessageMapper;
import com.li.community.mapper.BizMarketPostMapper;
import com.li.community.mapper.SysUserMapper;
import com.li.community.mapper.SysUserNoticeMapper;
import com.li.community.model.dto.ChatMessageDto;
import com.li.community.model.entity.BizChatMessage;
import com.li.community.model.entity.BizMarketPost;
import com.li.community.model.entity.SysUser;
import com.li.community.model.entity.SysUserNotice;
import com.li.community.model.vo.ChatMessageVo;
import com.li.community.model.vo.ChatSessionVo;
import com.li.community.service.ChatService;
import com.li.community.service.ModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final BizChatMessageMapper chatMessageMapper;
    private final BizMarketPostMapper marketPostMapper;
    private final SysUserMapper userMapper;
    private final SysUserNoticeMapper userNoticeMapper;
    private final ModerationService moderationService;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageVo sendMessage(ChatMessageDto dto) {
        Long senderId = SecurityUtils.getUserId();
        Long receiverId = dto.getReceiverId();

        if (senderId.equals(receiverId)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "无法给自己发送私信");
        }

        BizMarketPost post = marketPostMapper.selectById(dto.getPostId());
        if (post == null) {
            throw new BizException(ErrorCode.POST_NOT_FOUND, "关联的闲置互助信息不存在");
        }

        SysUser sender = userMapper.selectById(senderId);
        SysUser receiver = userMapper.selectById(receiverId);
        if (receiver == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "私信接收方用户不存在");
        }

        // 敏感词合规检测并脱敏
        String content = dto.getContent().trim();
        if (moderationService.containsSensitiveWord(content)) {
            content = moderationService.replaceSensitiveWords(content);
        }

        String sessionId = buildSessionId(dto.getPostId(), senderId, receiverId);

        BizChatMessage msg = BizChatMessage.builder()
                .id(IdUtil.getSnowflakeNextId())
                .sessionId(sessionId)
                .postId(dto.getPostId())
                .senderId(senderId)
                .senderName(sender != null ? sender.getRealName() : "邻里")
                .senderAvatar(sender != null ? sender.getAvatar() : "")
                .receiverId(receiverId)
                .receiverName(receiver.getRealName())
                .receiverAvatar(receiver.getAvatar())
                .content(content)
                .isRead(0)
                .createTime(LocalDateTime.now())
                .build();

        chatMessageMapper.insert(msg);

        ChatMessageVo vo = convertToVo(msg, senderId, post.getTitle());

        // 实时 WebSocket 投递延迟到事务提交后 (点对点定向通道与会话广播通道)
        Runnable push = () -> {
            try {
                messagingTemplate.convertAndSendToUser(receiverId.toString(), "/queue/chat", vo);
                messagingTemplate.convertAndSend("/topic/chat/" + sessionId, vo);
            } catch (Exception e) {
                log.warn("STOMP 消息推送异常: {}", e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push.run();
                }
            });
        } else {
            push.run();
        }

        // 写入站内信提醒
        try {
            userNoticeMapper.insert(SysUserNotice.builder()
                    .id(IdUtil.getSnowflakeNextId())
                    .userId(receiverId)
                    .noticeType("CHAT")
                    .title("【邻里私信】" + (sender != null ? sender.getRealName() : "邻里") + " 发来了咨询")
                    .summary("关于【" + post.getTitle() + "】：" + (content.length() > 30 ? content.substring(0, 30) + "..." : content))
                    .bizId(dto.getPostId())
                    .isRead(0)
                    .build());
        } catch (Exception e) {
            log.warn("写入私信站内信提醒失败: {}", e.getMessage());
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ChatMessageVo> getMessages(Long postId, Long otherUserId) {
        Long currentUserId = SecurityUtils.getUserId();
        String sessionId = buildSessionId(postId, currentUserId, otherUserId);

        BizMarketPost post = marketPostMapper.selectById(postId);
        String postTitle = post != null ? post.getTitle() : "";

        // 只取最近 200 条历史记录，避免长会话全量加载
        List<BizChatMessage> list = chatMessageMapper.selectList(
                new LambdaQueryWrapper<BizChatMessage>()
                        .eq(BizChatMessage::getSessionId, sessionId)
                        .orderByDesc(BizChatMessage::getCreateTime)
                        .last("LIMIT 200")
        );
        Collections.reverse(list);

        // 单条 UPDATE 批量将对方发给自己的未读消息标记为已读
        chatMessageMapper.update(null, new LambdaUpdateWrapper<BizChatMessage>()
                .set(BizChatMessage::getIsRead, 1)
                .eq(BizChatMessage::getSessionId, sessionId)
                .eq(BizChatMessage::getReceiverId, currentUserId)
                .eq(BizChatMessage::getIsRead, 0));
        list.forEach(m -> {
            if (m.getReceiverId().equals(currentUserId)) {
                m.setIsRead(1);
            }
        });

        return list.stream()
                .map(m -> convertToVo(m, currentUserId, postTitle))
                .collect(Collectors.toList());
    }

    @Override
    public List<ChatSessionVo> getMyChatSessions() {
        Long currentUserId = SecurityUtils.getUserId();

        // SQL GROUP BY 取每个会话最新一条消息与未读数，避免全量历史消息载入内存
        List<BizChatMessage> latestMessages = chatMessageMapper.selectLatestOfEachSession(currentUserId);
        if (latestMessages.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Integer> unreadMap = new HashMap<>();
        for (Map<String, Object> row : chatMessageMapper.selectUnreadCountBySession(currentUserId)) {
            unreadMap.put((String) row.get("session_id"),
                    ((Number) row.getOrDefault("unread", 0)).intValue());
        }

        // 批量加载会话对方用户与关联帖子
        Set<Long> userIds = new HashSet<>();
        Set<Long> postIds = new HashSet<>();
        for (BizChatMessage latest : latestMessages) {
            userIds.add(latest.getSenderId().equals(currentUserId) ? latest.getReceiverId() : latest.getSenderId());
            if (latest.getPostId() != null) postIds.add(latest.getPostId());
        }
        Map<Long, SysUser> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
        Map<Long, BizMarketPost> postMap = postIds.isEmpty() ? Map.of() :
                marketPostMapper.selectBatchIds(postIds).stream()
                        .collect(Collectors.toMap(BizMarketPost::getId, p -> p, (a, b) -> a));

        List<ChatSessionVo> sessionList = new ArrayList<>();
        Set<String> seenSessions = new HashSet<>();
        for (BizChatMessage latest : latestMessages) {
            // create_time 相同的并列最新消息去重，保留第一条
            if (!seenSessions.add(latest.getSessionId())) {
                continue;
            }

            Long otherUserId = latest.getSenderId().equals(currentUserId) ? latest.getReceiverId() : latest.getSenderId();
            SysUser otherUser = userMap.get(otherUserId);
            BizMarketPost post = postMap.get(latest.getPostId());

            String postCover = "";
            if (post != null && post.getImages() != null && !post.getImages().trim().isEmpty()) {
                try {
                    List<String> imgList = JSONUtil.toList(post.getImages(), String.class);
                    if (!imgList.isEmpty()) postCover = imgList.get(0);
                } catch (Exception ignored) {}
            }

            sessionList.add(ChatSessionVo.builder()
                    .sessionId(latest.getSessionId())
                    .postId(latest.getPostId())
                    .postTitle(post != null ? post.getTitle() : "未知物品")
                    .postPrice(post != null ? post.getPrice() : null)
                    .postCover(postCover)
                    .postType(post != null ? post.getPostType() : 1)
                    .otherUserId(otherUserId)
                    .otherUserName(otherUser != null ? otherUser.getRealName() : "邻里")
                    .otherUserAvatar(otherUser != null ? otherUser.getAvatar() : "")
                    .otherUserCreditScore(otherUser != null ? otherUser.getCreditScore() : 100)
                    .lastMessage(latest.getContent())
                    .lastTime(latest.getCreateTime())
                    .unreadCount(unreadMap.getOrDefault(latest.getSessionId(), 0))
                    .build());
        }

        // 按最新消息时间倒序排序
        sessionList.sort((a, b) -> b.getLastTime().compareTo(a.getLastTime()));
        return sessionList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSessionAsRead(String sessionId) {
        Long currentUserId = SecurityUtils.getUserId();
        // 单条 UPDATE 批量置为已读，替代逐条 updateById
        chatMessageMapper.update(null, new LambdaUpdateWrapper<BizChatMessage>()
                .set(BizChatMessage::getIsRead, 1)
                .eq(BizChatMessage::getSessionId, sessionId)
                .eq(BizChatMessage::getReceiverId, currentUserId)
                .eq(BizChatMessage::getIsRead, 0));
    }

    @Override
    public int getUnreadChatCount() {
        Long currentUserId = SecurityUtils.getUserId();
        Long count = chatMessageMapper.selectCount(
                new LambdaQueryWrapper<BizChatMessage>()
                        .eq(BizChatMessage::getReceiverId, currentUserId)
                        .eq(BizChatMessage::getIsRead, 0)
        );
        return count != null ? count.intValue() : 0;
    }

    private String buildSessionId(Long postId, Long u1, Long u2) {
        long min = Math.min(u1, u2);
        long max = Math.max(u1, u2);
        return "post_" + postId + "_" + min + "_" + max;
    }

    private ChatMessageVo convertToVo(BizChatMessage m, Long currentUserId, String postTitle) {
        return ChatMessageVo.builder()
                .id(m.getId())
                .sessionId(m.getSessionId())
                .postId(m.getPostId())
                .postTitle(postTitle)
                .senderId(m.getSenderId())
                .senderName(m.getSenderName())
                .senderAvatar(m.getSenderAvatar())
                .receiverId(m.getReceiverId())
                .receiverName(m.getReceiverName())
                .receiverAvatar(m.getReceiverAvatar())
                .content(m.getContent())
                .isRead(m.getIsRead())
                .isSelf(m.getSenderId().equals(currentUserId))
                .createTime(m.getCreateTime())
                .build();
    }
}
