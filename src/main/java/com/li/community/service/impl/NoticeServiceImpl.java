package com.li.community.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.li.community.common.exception.BizException;
import com.li.community.common.exception.ErrorCode;
import com.li.community.common.result.PageResult;
import com.li.community.common.security.CommunityScopeResolver;
import com.li.community.common.utils.SecurityUtils;
import com.li.community.mapper.BizResidentHouseMapper;
import com.li.community.mapper.BizHouseMapper;
import com.li.community.mapper.SysNoticeMapper;
import com.li.community.mapper.SysUserMapper;
import com.li.community.mapper.SysUserNoticeMapper;
import com.li.community.model.document.SearchItemDoc;
import com.li.community.model.dto.NoticeCreateDto;
import com.li.community.model.entity.*;
import com.li.community.repository.SearchItemElasticsearchRepository;
import com.li.community.service.NoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {

    private final SysNoticeMapper noticeMapper;
    private final SysUserNoticeMapper userNoticeMapper;
    private final SysUserMapper userMapper;
    private final BizResidentHouseMapper residentHouseMapper;
    private final BizHouseMapper houseMapper;
    private final SearchItemElasticsearchRepository searchRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final CommunityScopeResolver scopeResolver;

    @Override
    public PageResult<SysNotice> listNotices(Integer noticeType, String keyword, Long communityId, Long page, Long size) {
        Page<SysNotice> pageParam = new Page<>(page != null ? page : 1, size != null ? size : 10);
        Long communityScope = scopeResolver.resolvePublicScope(communityId);
        String kw = keyword != null ? keyword.trim() : null;
        boolean hasKw = kw != null && !kw.isEmpty();

        Page<SysNotice> result = noticeMapper.selectPage(
                pageParam,
                new LambdaQueryWrapper<SysNotice>()
                        .eq(communityScope != null, SysNotice::getCommunityId, communityScope)
                        .eq(noticeType != null, SysNotice::getNoticeType, noticeType)
                        .eq(SysNotice::getStatus, 1) // 仅已发布
                        .and(hasKw, w -> w.like(SysNotice::getTitle, kw).or().like(SysNotice::getContent, kw))
                        .orderByDesc(SysNotice::getIsTop)
                        .orderByDesc(SysNotice::getPublishTime)
        );
        return PageResult.of(result);
    }

    @Override
    public SysNotice getNoticeDetail(Long noticeId) {
        SysNotice notice = noticeMapper.selectById(noticeId);
        if (notice == null || (notice.getStatus() != null && notice.getStatus() != 1)) {
            throw new BizException(ErrorCode.NOT_FOUND, "公告不存在");
        }

        // 浏览量 SQL 原子自增，避免并发读改写丢计数
        noticeMapper.update(null, new LambdaUpdateWrapper<SysNotice>()
                .setSql("view_count = view_count + 1")
                .eq(SysNotice::getId, noticeId));
        notice.setViewCount(notice.getViewCount() + 1);

        return notice;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysNotice publishNotice(NoticeCreateDto dto) {
        Long publisherId = SecurityUtils.getUserId();
        String publisherName = SecurityUtils.getUsername();

        long noticeId = IdUtil.getSnowflakeNextId();
        SysNotice notice = SysNotice.builder()
                .id(noticeId)
                .title(dto.getTitle())
                .noticeType(dto.getNoticeType())
                .communityId(scopeResolver.resolveForCreate(dto.getCommunityId()))
                .targetScope(dto.getTargetScope() != null ? dto.getTargetScope() : 1)
                .targetBuildingIds(cleanJson(dto.getTargetBuildingIds()))
                .isTop(dto.getIsTop() != null ? dto.getIsTop() : 0)
                .content(dto.getContent())
                .publishTime(LocalDateTime.now())
                .publisherId(publisherId)
                .publisherName(publisherName)
                .viewCount(0)
                .status(dto.getStatus() != null ? dto.getStatus() : 1)
                .build();

        noticeMapper.insert(notice);

        // 送达站内信至相关居民
        deliverNoticesToResidents(notice);

        // WS 应急广播与 ES 索引延迟到事务提交后执行，避免回滚后居民收到已回滚的公告
        runAfterCommit(() -> {
            // 如果是突发应急广播 (noticeType == 4)，向全社区在线客户端执行 STOMP 广播
            if (notice.getNoticeType() != null && notice.getNoticeType() == 4) {
                try {
                    messagingTemplate.convertAndSend(
                            "/topic/emergency",
                            Map.of(
                                    "id", notice.getId(),
                                    "title", notice.getTitle(),
                                    "content", notice.getContent(),
                                    "publishTime", notice.getPublishTime().toString()
                            )
                    );
                    log.info("已通过 WebSocket STOMP 发送全社区突发应急通告广播: {}", notice.getTitle());
                } catch (Exception e) {
                    log.warn("发送应急广播异常: {}", e.getMessage());
                }
            }

            // 同步索引至 Elasticsearch
            try {
                SearchItemDoc doc = SearchItemDoc.builder()
                        .id("NOTICE_" + noticeId)
                        .itemType("NOTICE")
                        .targetId(noticeId)
                        .title(notice.getTitle())
                        .content(notice.getContent().replaceAll("<[^>]*>", "")) // 去除 HTML 标签后分词
                        .category(String.valueOf(notice.getNoticeType()))
                        .communityId(notice.getCommunityId())
                        .publishTime(new Date())
                        .extraInfo(notice.getNoticeType() == 4 ? "【应急预警】" : "社区通告")
                        .build();
                searchRepository.save(doc);
            } catch (Exception e) {
                log.warn("同步公告至 ES 失败: {}", e.getMessage());
            }
        });

        return notice;
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void deliverNoticesToResidents(SysNotice notice) {
        Set<Long> targetUserIds = new HashSet<>();

        if (notice.getTargetScope() == 1) {
            // 全区广播：给本小区所有启用居民发送（只查 ID 列），避免跨小区误发
            List<SysUser> allUsers = userMapper.selectList(
                    new LambdaQueryWrapper<SysUser>()
                            .select(SysUser::getId)
                            .eq(SysUser::getStatus, 1)
                            .eq(SysUser::getCommunityId, notice.getCommunityId()));
            for (SysUser u : allUsers) {
                targetUserIds.add(u.getId());
            }
        } else {
            // 定向楼栋推送：一次 JOIN 查出绑定居民
            if (notice.getTargetBuildingIds() != null && !notice.getTargetBuildingIds().isEmpty()) {
                List<Long> bldIds = JSONUtil.toList(notice.getTargetBuildingIds(), Long.class);
                targetUserIds.addAll(residentHouseMapper.selectUserIdsByBuildingIds(bldIds));
            }
        }

        if (targetUserIds.isEmpty()) {
            return;
        }

        // 摘要只计算一次，批量插入站内信
        String plain = notice.getContent().replaceAll("<[^>]*>", "");
        String summary = plain.substring(0, Math.min(60, plain.length())) + "...";
        String noticeType = notice.getNoticeType() == 4 ? "EMERGENCY" : "NOTICE";

        List<SysUserNotice> notices = new ArrayList<>(targetUserIds.size());
        for (Long uid : targetUserIds) {
            notices.add(SysUserNotice.builder()
                    .id(IdUtil.getSnowflakeNextId())
                    .userId(uid)
                    .noticeId(notice.getId())
                    .noticeType(noticeType)
                    .title(notice.getTitle())
                    .summary(summary)
                    .bizId(notice.getId())
                    .isRead(0)
                    .build());
        }
        // 批量插入，避免大社区全量用户逐条 INSERT 长事务
        Db.saveBatch(notices, 500);
    }

    @Override
    public PageResult<SysUserNotice> getMyUserNotices(Long page, Long size) {
        Long userId = SecurityUtils.getUserId();
        Page<SysUserNotice> pageParam = new Page<>(page != null ? page : 1, size != null ? size : 10);
        Page<SysUserNotice> result = userNoticeMapper.selectPage(
                pageParam,
                new LambdaQueryWrapper<SysUserNotice>()
                        .eq(SysUserNotice::getUserId, userId)
                        .orderByDesc(SysUserNotice::getCreateTime)
        );
        return PageResult.of(result);
    }

    @Override
    public void markNoticeRead(Long id) {
        Long userId = SecurityUtils.getUserId();
        SysUserNotice notice = userNoticeMapper.selectById(id);
        if (notice != null && notice.getUserId().equals(userId)) {
            notice.setIsRead(1);
            notice.setReadTime(LocalDateTime.now());
            userNoticeMapper.updateById(notice);
        }
    }

    @Override
    public void markAllRead() {
        Long userId = SecurityUtils.getUserId();
        userNoticeMapper.update(
                null,
                new LambdaUpdateWrapper<SysUserNotice>()
                        .set(SysUserNotice::getIsRead, 1)
                        .set(SysUserNotice::getReadTime, LocalDateTime.now())
                        .eq(SysUserNotice::getUserId, userId)
                        .eq(SysUserNotice::getIsRead, 0)
        );
    }

    @Override
    public Long getUnreadNoticeCount() {
        Long userId = SecurityUtils.getUserId();
        return userNoticeMapper.countUnread(userId);
    }

    private String cleanJson(String str) {
        if (str == null || str.trim().isEmpty() || "null".equalsIgnoreCase(str.trim()) || "\"\"".equals(str.trim())) {
            return null;
        }
        return str.trim();
    }
}
