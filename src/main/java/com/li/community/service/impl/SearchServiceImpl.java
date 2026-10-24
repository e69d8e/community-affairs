package com.li.community.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.community.common.constant.RedisKeyConstant;
import com.li.community.common.result.PageResult;
import com.li.community.mapper.BizActivityMapper;
import com.li.community.mapper.BizMarketPostMapper;
import com.li.community.mapper.SysNoticeMapper;
import com.li.community.model.document.SearchItemDoc;
import com.li.community.model.entity.BizActivity;
import com.li.community.model.entity.BizMarketPost;
import com.li.community.model.entity.SysNotice;
import com.li.community.repository.SearchItemElasticsearchRepository;
import com.li.community.service.SearchService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final SearchItemElasticsearchRepository searchRepository;
    private final SysNoticeMapper noticeMapper;
    private final BizActivityMapper activityMapper;
    private final BizMarketPostMapper marketPostMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @PostConstruct
    public void initSync() {
        // 项目启动自愈：异步执行，避免全量 DB 扫描 + ES 写入阻塞应用启动
        Thread initializer = new Thread(() -> {
            try {
                long count = searchRepository.count();
                if (count == 0) {
                    log.info("检测到 Elasticsearch 索引库为空，开始执行全量历史数据初始化同步...");
                    syncAllToEs();
                }
            } catch (Exception e) {
                log.warn("检查或初始化 ES 索引异常 (请确保 ES 容器已启动): {}", e.getMessage());
            }
        }, "es-init-sync");
        initializer.setDaemon(true);
        initializer.start();
    }

    @Override
    public PageResult<SearchItemDoc> search(String keyword, String itemType, Long page, Long size) {
        int pageIndex = (page != null && page > 0) ? (page.intValue() - 1) : 0;
        int pageSize = (size != null && size > 0) ? size.intValue() : 10;
        Pageable pageable = PageRequest.of(pageIndex, pageSize);

        // 记录热搜词频
        if (keyword != null && !keyword.trim().isEmpty()) {
            try {
                redisTemplate.opsForZSet().incrementScore(RedisKeyConstant.SEARCH_HOT_WORDS, keyword.trim(), 1);
            } catch (Exception ignored) {}
        }

        try {
            Page<SearchItemDoc> esResult;
            if (itemType != null && !itemType.trim().isEmpty()) {
                esResult = searchRepository.findByItemTypeAndTitleContaining(itemType.trim(), keyword != null ? keyword : "", pageable);
            } else if (keyword != null && !keyword.trim().isEmpty()) {
                esResult = searchRepository.findByTitleContainingOrContentContaining(keyword, keyword, pageable);
            } else {
                esResult = searchRepository.findAll(pageable);
            }

            return PageResult.of(
                    esResult.getContent(),
                    esResult.getTotalElements(),
                    (long) pageSize,
                    (long) (pageIndex + 1)
            );
        } catch (Exception e) {
            log.warn("ES 全文检索降级查询: {}", e.getMessage());
            // 降级 fallback: 从 MySQL 简单模糊查询
            return PageResult.of(Collections.emptyList(), 0L, (long) pageSize, (long) (pageIndex + 1));
        }
    }

    @Override
    public void syncAllToEs() {
        try {
            List<SearchItemDoc> docs = new ArrayList<>();

            // 1. 同步公告
            List<SysNotice> notices = noticeMapper.selectList(new LambdaQueryWrapper<SysNotice>().eq(SysNotice::getStatus, 1));
            for (SysNotice n : notices) {
                docs.add(SearchItemDoc.builder()
                        .id("NOTICE_" + n.getId())
                        .itemType("NOTICE")
                        .targetId(n.getId())
                        .title(n.getTitle())
                        .content(n.getContent().replaceAll("<[^>]*>", ""))
                        .category(String.valueOf(n.getNoticeType()))
                        .communityId(n.getCommunityId())
                        .publishTime(n.getPublishTime() != null ? DateUtil.date(n.getPublishTime()) : new Date())
                        .extraInfo(n.getNoticeType() == 4 ? "【应急预警】" : "社区通告")
                        .build());
            }

            // 2. 同步活动
            List<BizActivity> activities = activityMapper.selectList(new LambdaQueryWrapper<BizActivity>().eq(BizActivity::getStatus, 1));
            for (BizActivity a : activities) {
                docs.add(SearchItemDoc.builder()
                        .id("ACTIVITY_" + a.getId())
                        .itemType("ACTIVITY")
                        .targetId(a.getId())
                        .title(a.getTitle())
                        .content(a.getDescription())
                        .category(a.getCategory())
                        .communityId(a.getCommunityId())
                        .coverImage(a.getCoverImage())
                        .publishTime(a.getStartTime() != null ? DateUtil.date(a.getStartTime()) : new Date())
                        .extraInfo(a.getLocation())
                        .build());
            }

            // 3. 同步二手闲置与互助
            List<BizMarketPost> posts = marketPostMapper.selectList(new LambdaQueryWrapper<BizMarketPost>().ne(BizMarketPost::getStatus, 4));
            for (BizMarketPost p : posts) {
                docs.add(SearchItemDoc.builder()
                        .id("MARKET_" + p.getId())
                        .itemType("MARKET")
                        .targetId(p.getId())
                        .title(p.getTitle())
                        .content(p.getDescription())
                        .category(String.valueOf(p.getPostType()))
                        .communityId(p.getCommunityId())
                        .coverImage(p.getImages())
                        .publishTime(p.getCreateTime() != null ? DateUtil.date(p.getCreateTime()) : new Date())
                        .extraInfo(p.getPrice().compareTo(BigDecimal.ZERO) == 0 ? "免费/互助" : "¥" + p.getPrice())
                        .build());
            }

            searchRepository.saveAll(docs);
            log.info("全量同步数据至 Elasticsearch 完成，共导入文档数: {}", docs.size());
        } catch (Exception e) {
            log.error("同步数据至 ES 失败: ", e);
        }
    }

    @Override
    public List<String> getHotSearchWords() {
        try {
            Set<Object> set = redisTemplate.opsForZSet().reverseRange(RedisKeyConstant.SEARCH_HOT_WORDS, 0, 9);
            if (set != null && !set.isEmpty()) {
                return set.stream().map(Object::toString).collect(Collectors.toList());
            }
        } catch (Exception ignored) {}
        return List.of("台风预警", "供水清洗", "电梯维保", "百家宴", "急救培训", "推车", "多肉");
    }
}
