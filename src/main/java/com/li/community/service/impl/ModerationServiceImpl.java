package com.li.community.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.community.common.exception.BizException;
import com.li.community.common.exception.ErrorCode;
import com.li.community.common.result.PageResult;
import com.li.community.common.utils.DfaFilterUtil;
import com.li.community.common.utils.SecurityUtils;
import com.li.community.mapper.BizMarketPostMapper;
import com.li.community.mapper.BizPostCommentMapper;
import com.li.community.mapper.BizReportMapper;
import com.li.community.mapper.BizSensitiveWordMapper;
import com.li.community.mapper.SysUserMapper;
import com.li.community.mapper.SysUserNoticeMapper;
import com.li.community.model.dto.ReportSubmitDto;
import com.li.community.model.dto.SensitiveWordDto;
import com.li.community.model.entity.*;
import com.li.community.repository.SearchItemElasticsearchRepository;
import com.li.community.service.ModerationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationServiceImpl implements ModerationService {

    private final BizSensitiveWordMapper sensitiveWordMapper;
    private final BizReportMapper reportMapper;
    private final BizMarketPostMapper marketPostMapper;
    private final BizPostCommentMapper postCommentMapper;
    private final SysUserMapper userMapper;
    private final SysUserNoticeMapper userNoticeMapper;
    private final SearchItemElasticsearchRepository searchRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private final DfaFilterUtil dfaFilterUtil = new DfaFilterUtil();

    @PostConstruct
    public void init() {
        reloadDfaTree();
    }

    @Override
    public synchronized void reloadDfaTree() {
        try {
            List<BizSensitiveWord> list = sensitiveWordMapper.selectList(
                    new LambdaQueryWrapper<BizSensitiveWord>().eq(BizSensitiveWord::getStatus, 1)
            );
            Set<String> words = list.stream().map(BizSensitiveWord::getWord).collect(Collectors.toSet());
            dfaFilterUtil.buildTree(words);
            log.info("DFA 敏感词前缀树初始化加载完成，当前有效敏感词汇数: {}", words.size());
        } catch (Exception e) {
            log.warn("加载敏感词树异常: {}", e.getMessage());
        }
    }

    @Override
    public boolean containsSensitiveWord(String text) {
        return dfaFilterUtil.contains(text);
    }

    @Override
    public String replaceSensitiveWords(String text) {
        return dfaFilterUtil.replace(text, '*');
    }

    @Override
    public Set<String> findSensitiveWords(String text) {
        return dfaFilterUtil.findSensitiveWords(text);
    }

    @Override
    public PageResult<BizSensitiveWord> listWords(String keyword, Long page, Long size) {
        Page<BizSensitiveWord> pageParam = new Page<>(page != null ? page : 1, size != null ? size : 10);
        Page<BizSensitiveWord> result = sensitiveWordMapper.selectPage(
                pageParam,
                new LambdaQueryWrapper<BizSensitiveWord>()
                        .like(keyword != null && !keyword.isEmpty(), BizSensitiveWord::getWord, keyword)
                        .orderByDesc(BizSensitiveWord::getCreateTime)
        );
        return PageResult.of(result);
    }

    @Override
    public void saveWord(SensitiveWordDto dto) {
        if (dto.getId() == null) {
            // 新增
            Long count = sensitiveWordMapper.selectCount(
                    new LambdaQueryWrapper<BizSensitiveWord>().eq(BizSensitiveWord::getWord, dto.getWord().trim())
            );
            if (count > 0) {
                throw new BizException(ErrorCode.BAD_REQUEST, "该敏感词已存在于词库中");
            }
            BizSensitiveWord word = BizSensitiveWord.builder()
                    .id(IdUtil.getSnowflakeNextId())
                    .word(dto.getWord().trim())
                    .category(dto.getCategory() != null ? dto.getCategory() : "VULGAR")
                    .action(dto.getAction() != null ? dto.getAction() : "REJECT")
                    .status(dto.getStatus() != null ? dto.getStatus() : 1)
                    .build();
            sensitiveWordMapper.insert(word);
        } else {
            // 更新
            BizSensitiveWord word = sensitiveWordMapper.selectById(dto.getId());
            if (word != null) {
                word.setWord(dto.getWord().trim());
                if (dto.getCategory() != null) word.setCategory(dto.getCategory());
                if (dto.getAction() != null) word.setAction(dto.getAction());
                if (dto.getStatus() != null) word.setStatus(dto.getStatus());
                sensitiveWordMapper.updateById(word);
            }
        }
        reloadDfaTree();
    }

    @Override
    public void deleteWord(Long id) {
        sensitiveWordMapper.deleteById(id);
        reloadDfaTree();
    }

    @Override
    public void submitReport(ReportSubmitDto dto) {
        Long reporterId = SecurityUtils.getUserId();

        BizReport report = BizReport.builder()
                .id(IdUtil.getSnowflakeNextId())
                .targetType(dto.getTargetType())
                .targetId(dto.getTargetId())
                .reporterId(reporterId)
                .reasonCategory(dto.getReasonCategory())
                .description(dto.getDescription() != null ? dto.getDescription() : "")
                .evidenceImages(cleanJson(dto.getEvidenceImages()))
                .status(0) // 0-待核查
                .build();

        reportMapper.insert(report);
    }

    @Override
    public PageResult<BizReport> listReports(Integer status, Long page, Long size) {
        Page<BizReport> pageParam = new Page<>(page != null ? page : 1, size != null ? size : 10);
        Page<BizReport> result = reportMapper.selectPage(
                pageParam,
                new LambdaQueryWrapper<BizReport>()
                        .eq(status != null, BizReport::getStatus, status)
                        .orderByDesc(BizReport::getCreateTime)
        );
        return PageResult.of(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleReport(Long reportId, Integer status, String remark) {
        if (status == null || (status != 1 && status != 2)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法的举报处理结果");
        }
        Long handlerId = SecurityUtils.getUserId();
        BizReport report = reportMapper.selectById(reportId);
        if (report == null) throw new BizException(ErrorCode.NOT_FOUND, "举报记录不存在");

        // 条件更新保证幂等：仅待核查(0)状态可处理，防止重复处罚、重复扣分
        int updated = reportMapper.update(null, new LambdaUpdateWrapper<BizReport>()
                .set(BizReport::getStatus, status) // 1-违规下架, 2-驳回
                .set(BizReport::getHandlerId, handlerId)
                .set(BizReport::getHandleRemark, remark != null ? remark : "")
                .set(BizReport::getHandleTime, LocalDateTime.now())
                .eq(BizReport::getId, reportId)
                .eq(BizReport::getStatus, 0));
        if (updated == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该举报已被处理，请勿重复操作");
        }

        // 如果判定属实违规，执行封禁并扣分
        if (status == 1) {
            Long targetUserId = null;
            String targetTitle = "";
            Long bannedPostId = null;

            if ("POST".equalsIgnoreCase(report.getTargetType())) {
                BizMarketPost post = marketPostMapper.selectById(report.getTargetId());
                if (post != null) {
                    marketPostMapper.update(null, new LambdaUpdateWrapper<BizMarketPost>()
                            .set(BizMarketPost::getStatus, 4) // 4-违规封禁下架
                            .eq(BizMarketPost::getId, post.getId()));
                    bannedPostId = post.getId();
                    targetUserId = post.getUserId();
                    targetTitle = post.getTitle();
                }
            } else if ("COMMENT".equalsIgnoreCase(report.getTargetType())) {
                BizPostComment comment = postCommentMapper.selectById(report.getTargetId());
                if (comment != null) {
                    postCommentMapper.update(null, new LambdaUpdateWrapper<BizPostComment>()
                            .set(BizPostComment::getStatus, 3) // 3-已删除
                            .eq(BizPostComment::getId, comment.getId()));
                    // 评论下架同步回减帖子评论数，保持计数与明细一致
                    marketPostMapper.update(null, new LambdaUpdateWrapper<BizMarketPost>()
                            .setSql("comment_count = comment_count - 1")
                            .eq(BizMarketPost::getId, comment.getPostId())
                            .apply("comment_count > 0"));
                    targetUserId = comment.getUserId();
                    targetTitle = comment.getContent();
                }
            }

            // 扣减违规发布者 10 点社区信用分（SQL 原子自减，下限 0）并下发警示通知
            if (targetUserId != null) {
                userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                        .setSql("credit_score = GREATEST(0, credit_score - 10)")
                        .eq(SysUser::getId, targetUserId));

                userNoticeMapper.insert(SysUserNotice.builder()
                        .id(IdUtil.getSnowflakeNextId())
                        .userId(targetUserId)
                        .noticeType("MODERATION")
                        .title("违规内容下架及信用分扣减告诫")
                        .summary("您发布的内容【" + targetTitle + "】经邻里举报与网格员核实，存在违规内容已被下架，已扣减10点社区互助信用分。")
                        .isRead(0)
                        .build());

                // ES 删除与 WS 推送延迟到事务提交后，避免回滚后产生脏数据/误报
                Long finalTargetUserId = targetUserId;
                Long finalBannedPostId = bannedPostId;
                runAfterCommit(() -> {
                    if (finalBannedPostId != null) {
                        try {
                            searchRepository.deleteById("MARKET_" + finalBannedPostId);
                        } catch (Exception ignored) {}
                    }
                    try {
                        messagingTemplate.convertAndSendToUser(
                                finalTargetUserId.toString(),
                                "/queue/notify",
                                Map.of(
                                        "type", "MODERATION",
                                        "title", "合规风控提醒",
                                        "summary", "您发布的内容因违规已被下架处理，请自觉维护社区良好风尚。"
                                )
                        );
                    } catch (Exception ignored) {}
                });
            }
        }
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

    private String cleanJson(String str) {
        if (str == null || str.trim().isEmpty() || "null".equalsIgnoreCase(str.trim()) || "\"\"".equals(str.trim())) {
            return null;
        }
        return str.trim();
    }
}
