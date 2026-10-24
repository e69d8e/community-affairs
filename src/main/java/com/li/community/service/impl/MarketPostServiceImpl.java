package com.li.community.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.community.common.exception.BizException;
import com.li.community.common.exception.ErrorCode;
import com.li.community.common.result.PageResult;
import com.li.community.common.utils.SecurityUtils;
import com.li.community.mapper.BizMarketPostMapper;
import com.li.community.mapper.BizPostCommentMapper;
import com.li.community.mapper.BizPostFavoriteMapper;
import com.li.community.mapper.BizPostLikeMapper;
import com.li.community.mapper.SysUserMapper;
import com.li.community.model.document.SearchItemDoc;
import com.li.community.model.dto.CommentCreateDto;
import com.li.community.model.dto.MarketPostCreateDto;
import com.li.community.model.entity.BizMarketPost;
import com.li.community.model.entity.BizPostComment;
import com.li.community.model.entity.BizPostFavorite;
import com.li.community.model.entity.BizPostLike;
import com.li.community.model.entity.SysUser;
import com.li.community.model.vo.MarketPostVo;
import com.li.community.repository.SearchItemElasticsearchRepository;
import com.li.community.service.MarketPostService;
import com.li.community.service.ModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketPostServiceImpl implements MarketPostService {

    private final BizMarketPostMapper marketPostMapper;
    private final BizPostCommentMapper postCommentMapper;
    private final BizPostFavoriteMapper postFavoriteMapper;
    private final BizPostLikeMapper postLikeMapper;
    private final SysUserMapper userMapper;
    private final ModerationService moderationService;
    private final SearchItemElasticsearchRepository searchRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final Set<String> ADMIN_ROLES = Set.of(
            "ROLE_SYSTEM_ADMIN", "ROLE_COMMUNITY_ADMIN", "ROLE_PROPERTY_ADMIN", "ROLE_GRID_OFFICER");

    // 帖子状态: 1-展示中 2-已交易 3-已下架 4-平台封禁
    private static final Set<Integer> POST_STATUSES = Set.of(1, 2, 3, 4);

    @Override
    public PageResult<MarketPostVo> listPosts(Integer postType, Integer status, String keyword, Long page, Long size) {
        Page<BizMarketPost> pageParam = new Page<>(page != null ? page : 1, size != null ? size : 10);

        Page<BizMarketPost> result = marketPostMapper.selectPage(
                pageParam,
                new LambdaQueryWrapper<BizMarketPost>()
                        .eq(postType != null, BizMarketPost::getPostType, postType)
                        .eq(status != null, BizMarketPost::getStatus, status)
                        .ne(status == null, BizMarketPost::getStatus, 4) // 默认过滤封禁违规
                        .and(keyword != null && !keyword.trim().isEmpty(), w ->
                                w.like(BizMarketPost::getTitle, keyword.trim())
                                        .or()
                                        .like(BizMarketPost::getDescription, keyword.trim()))
                        .orderByDesc(BizMarketPost::getCreateTime)
        );

        Long currentUserId = null;
        try {
            currentUserId = SecurityUtils.getUserId();
        } catch (Exception ignored) {}

        List<MarketPostVo> voList = convertToVoList(result.getRecords(), currentUserId, false);

        return PageResult.of(voList, result.getTotal(), result.getSize(), result.getCurrent());
    }

    @Override
    public MarketPostVo getPostDetail(Long postId) {
        BizMarketPost post = marketPostMapper.selectById(postId);
        if (post == null) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }

        Long currentUserId = null;
        try {
            currentUserId = SecurityUtils.getUserId();
        } catch (Exception ignored) {}

        // 封禁帖仅发布者与管理员可见，避免绕过列表直接访问详情
        boolean owner = currentUserId != null && currentUserId.equals(post.getUserId());
        if (post.getStatus() != null && post.getStatus() == 4 && !owner && !isAdminUser()) {
            throw new BizException(ErrorCode.POST_NOT_FOUND, "该帖子已被下架");
        }

        // 浏览量 SQL 原子自增，避免并发读改写丢计数
        marketPostMapper.update(null, new LambdaUpdateWrapper<BizMarketPost>()
                .setSql("view_count = view_count + 1")
                .eq(BizMarketPost::getId, postId));
        post.setViewCount(post.getViewCount() + 1);

        return convertToVoList(List.of(post), currentUserId, true).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MarketPostVo publishPost(MarketPostCreateDto dto) {
        Long userId = SecurityUtils.getUserId();
        SysUser user = userMapper.selectById(userId);

        // DFA 敏感词安全过滤拦截
        if (moderationService.containsSensitiveWord(dto.getTitle()) ||
                moderationService.containsSensitiveWord(dto.getDescription())) {
            Set<String> badWords = new HashSet<>();
            badWords.addAll(moderationService.findSensitiveWords(dto.getTitle()));
            badWords.addAll(moderationService.findSensitiveWords(dto.getDescription()));
            throw new BizException(ErrorCode.CONTENT_SENSITIVE_REJECT, "发布内容包含违禁敏感词汇 " + badWords + "，已被系统安全拦截");
        }

        long postId = IdUtil.getSnowflakeNextId();
        BizMarketPost post = BizMarketPost.builder()
                .id(postId)
                .postType(dto.getPostType())
                .communityId(101L)
                .userId(userId)
                .title(dto.getTitle())
                .description(dto.getDescription())
                .price(dto.getPrice() != null ? dto.getPrice() : BigDecimal.ZERO)
                .tradeType(dto.getTradeType() != null ? dto.getTradeType() : 1)
                .deliveryType(dto.getDeliveryType() != null ? dto.getDeliveryType() : 1)
                .tradeAddress(dto.getTradeAddress() != null ? dto.getTradeAddress() : "")
                .contactType(dto.getContactType() != null ? dto.getContactType() : "站内私信")
                .contactValue(dto.getContactValue() != null ? dto.getContactValue() : (user != null ? user.getPhone() : ""))
                .images(cleanJson(dto.getImages()))
                .status(1) // 1-展示中
                .viewCount(0)
                .likeCount(0)
                .commentCount(0)
                .build();

        marketPostMapper.insert(post);

        // 奖励发布居民 2 点信用分（SQL 原子自增）
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .setSql("credit_score = credit_score + 2")
                .eq(SysUser::getId, userId));

        // 同步索引到 Elasticsearch（事务提交后执行，避免回滚后残留脏文档）
        syncPostToEsAfterCommit(post);

        return convertToVoList(List.of(post), userId, false).get(0);
    }

    /**
     * 事务提交后同步帖子至 ES；帖子下架/恢复时同样复用
     */
    private void syncPostToEsAfterCommit(BizMarketPost post) {
        Runnable sync = () -> {
            try {
                if (post.getStatus() != null && post.getStatus() == 1) {
                    searchRepository.save(SearchItemDoc.builder()
                            .id("MARKET_" + post.getId())
                            .itemType("MARKET")
                            .targetId(post.getId())
                            .title(post.getTitle())
                            .content(post.getDescription())
                            .category(String.valueOf(post.getPostType()))
                            .communityId(post.getCommunityId())
                            .coverImage(post.getImages())
                            .publishTime(new Date())
                            .extraInfo(post.getPrice() != null && post.getPrice().compareTo(BigDecimal.ZERO) == 0 ? "免费/互助" : "¥" + post.getPrice())
                            .build());
                } else {
                    // 非展示状态从全局搜索移除，避免搜到已交易/下架/封禁帖
                    searchRepository.deleteById("MARKET_" + post.getId());
                }
            } catch (Exception e) {
                log.warn("同步帖子至 ES 失败: {}", e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sync.run();
                }
            });
        } else {
            sync.run();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePostStatus(Long postId, Integer status) {
        if (status == null || !POST_STATUSES.contains(status)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法的帖子状态值");
        }
        Long userId = SecurityUtils.getUserId();
        BizMarketPost post = marketPostMapper.selectById(postId);
        if (post == null) throw new BizException(ErrorCode.POST_NOT_FOUND);

        // 仅发布者本人或管理员可变更流转状态；封禁态(4)仅管理员可设置
        boolean admin = isAdminUser();
        if (!post.getUserId().equals(userId) && !admin) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (status == 4 && !admin) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权封禁帖子");
        }

        marketPostMapper.update(null, new LambdaUpdateWrapper<BizMarketPost>()
                .set(BizMarketPost::getStatus, status)
                .eq(BizMarketPost::getId, postId));
        post.setStatus(status);

        // 状态变化同步 ES：非展示态移出全局搜索，恢复展示则重建索引
        syncPostToEsAfterCommit(post);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizPostComment addComment(CommentCreateDto dto) {
        Long userId = SecurityUtils.getUserId();

        // 敏感词安全过滤拦截
        if (moderationService.containsSensitiveWord(dto.getContent())) {
            throw new BizException(ErrorCode.CONTENT_SENSITIVE_REJECT, "评论包含违规词汇，已被安全拦截");
        }

        BizMarketPost post = marketPostMapper.selectById(dto.getPostId());
        if (post == null) throw new BizException(ErrorCode.POST_NOT_FOUND);

        BizPostComment comment = BizPostComment.builder()
                .id(IdUtil.getSnowflakeNextId())
                .postId(dto.getPostId())
                .userId(userId)
                .parentId(dto.getParentId())
                .replyToUserId(dto.getReplyToUserId())
                .content(dto.getContent())
                .status(1)
                .likeCount(0)
                .build();

        postCommentMapper.insert(comment);

        // 帖子评论数 SQL 原子自增
        marketPostMapper.update(null, new LambdaUpdateWrapper<BizMarketPost>()
                .setSql("comment_count = comment_count + 1")
                .eq(BizMarketPost::getId, dto.getPostId()));

        return comment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleLike(Long postId) {
        Long userId = SecurityUtils.getUserId();
        BizMarketPost post = marketPostMapper.selectById(postId);
        if (post == null) throw new BizException(ErrorCode.POST_NOT_FOUND);

        String likeKey = "community:market:likes:" + postId;

        BizPostLike existingLike = postLikeMapper.selectOne(
                new LambdaQueryWrapper<BizPostLike>()
                        .eq(BizPostLike::getUserId, userId)
                        .eq(BizPostLike::getPostId, postId)
        );

        if (existingLike != null) {
            // 已点赞 -> 取消点赞
            postLikeMapper.deleteById(existingLike.getId());
            marketPostMapper.update(null, new LambdaUpdateWrapper<BizMarketPost>()
                    .setSql("like_count = like_count - 1")
                    .eq(BizMarketPost::getId, postId)
                    .apply("like_count > 0"));
            try {
                redisTemplate.opsForSet().remove(likeKey, userId.toString());
            } catch (Exception ignored) {}
            return false;
        } else {
            // 未点赞 -> 仅允许点赞一次；并发双击触发唯一键冲突时幂等视为已点赞
            try {
                postLikeMapper.insert(BizPostLike.builder()
                        .id(IdUtil.getSnowflakeNextId())
                        .userId(userId)
                        .postId(postId)
                        .build());
            } catch (DuplicateKeyException e) {
                return true;
            }
            marketPostMapper.update(null, new LambdaUpdateWrapper<BizMarketPost>()
                    .setSql("like_count = like_count + 1")
                    .eq(BizMarketPost::getId, postId));
            try {
                redisTemplate.opsForSet().add(likeKey, userId.toString());
            } catch (Exception ignored) {}
            return true;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleFavorite(Long postId) {
        Long userId = SecurityUtils.getUserId();
        // 收藏目标必须存在且处于可见状态
        BizMarketPost post = marketPostMapper.selectById(postId);
        if (post == null || (post.getStatus() != null && post.getStatus() == 4)) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }

        BizPostFavorite fav = postFavoriteMapper.selectOne(
                new LambdaQueryWrapper<BizPostFavorite>()
                        .eq(BizPostFavorite::getUserId, userId)
                        .eq(BizPostFavorite::getPostId, postId)
        );

        if (fav != null) {
            postFavoriteMapper.deleteById(fav.getId());
            return false;
        } else {
            // 并发重复收藏触发唯一键冲突时幂等视为已收藏
            try {
                postFavoriteMapper.insert(BizPostFavorite.builder()
                        .id(IdUtil.getSnowflakeNextId())
                        .userId(userId)
                        .postId(postId)
                        .build());
            } catch (DuplicateKeyException e) {
                return true;
            }
            return true;
        }
    }

    @Override
    public PageResult<MarketPostVo> getMyPosts(Long page, Long size) {
        Long userId = SecurityUtils.getUserId();
        Page<BizMarketPost> pageParam = new Page<>(page != null ? page : 1, size != null ? size : 10);
        Page<BizMarketPost> result = marketPostMapper.selectPage(
                pageParam,
                new LambdaQueryWrapper<BizMarketPost>()
                        .eq(BizMarketPost::getUserId, userId)
                        .orderByDesc(BizMarketPost::getCreateTime)
        );

        List<MarketPostVo> list = convertToVoList(result.getRecords(), userId, false);

        return PageResult.of(list, result.getTotal(), result.getSize(), result.getCurrent());
    }

    @Override
    public PageResult<MarketPostVo> getMyFavorites(Long page, Long size) {
        Long userId = SecurityUtils.getUserId();
        Page<BizPostFavorite> pageParam = new Page<>(page != null ? page : 1, size != null ? size : 10);
        Page<BizPostFavorite> favResult = postFavoriteMapper.selectPage(
                pageParam,
                new LambdaQueryWrapper<BizPostFavorite>()
                        .eq(BizPostFavorite::getUserId, userId)
                        .orderByDesc(BizPostFavorite::getCreateTime)
        );
        if (favResult.getRecords().isEmpty()) {
            return PageResult.of(new ArrayList<>(), favResult.getTotal(), favResult.getSize(), favResult.getCurrent());
        }

        // 批量加载收藏的帖子，避免逐条回表
        Map<Long, BizMarketPost> postMap = marketPostMapper.selectBatchIds(
                        favResult.getRecords().stream().map(BizPostFavorite::getPostId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(BizMarketPost::getId, p -> p, (a, b) -> a));

        List<BizMarketPost> posts = favResult.getRecords().stream()
                .map(BizPostFavorite::getPostId)
                .map(postMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<MarketPostVo> list = convertToVoList(posts, userId, false);
        list.forEach(vo -> vo.setHasFavorited(true));

        return PageResult.of(list, favResult.getTotal(), favResult.getSize(), favResult.getCurrent());
    }

    private boolean isAdminUser() {
        String role = SecurityUtils.getRole();
        return role != null && ADMIN_ROLES.contains(role);
    }

    /**
     * 批量转换 VO：作者、当前用户的点赞/收藏状态均一次 IN 查询获取，避免每帖 3 次回表（N+1）
     */
    private List<MarketPostVo> convertToVoList(List<BizMarketPost> posts, Long currentUserId, boolean loadComments) {
        if (posts == null || posts.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> postIds = posts.stream().map(BizMarketPost::getId).collect(Collectors.toSet());

        Map<Long, SysUser> authorMap = userMapper.selectBatchIds(
                        posts.stream().map(BizMarketPost::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

        Set<Long> favoritedPostIds = Set.of();
        Set<Long> likedPostIds = Set.of();
        if (currentUserId != null) {
            favoritedPostIds = postFavoriteMapper.selectList(
                            new LambdaQueryWrapper<BizPostFavorite>()
                                    .eq(BizPostFavorite::getUserId, currentUserId)
                                    .in(BizPostFavorite::getPostId, postIds))
                    .stream().map(BizPostFavorite::getPostId).collect(Collectors.toSet());
            likedPostIds = postLikeMapper.selectList(
                            new LambdaQueryWrapper<BizPostLike>()
                                    .eq(BizPostLike::getUserId, currentUserId)
                                    .in(BizPostLike::getPostId, postIds))
                    .stream().map(BizPostLike::getPostId).collect(Collectors.toSet());
        }

        List<MarketPostVo.CommentVo> commentVoList =
                (loadComments && posts.size() == 1) ? loadCommentVos(posts.get(0).getId()) : Collections.emptyList();

        final Set<Long> finalFavIds = favoritedPostIds;
        final Set<Long> finalLikedIds = likedPostIds;
        return posts.stream().map(post -> {
            SysUser author = authorMap.get(post.getUserId());
            return MarketPostVo.builder()
                    .id(post.getId())
                    .postType(post.getPostType())
                    .communityId(post.getCommunityId())
                    .userId(post.getUserId())
                    .authorName(author != null ? author.getRealName() : "热心邻里")
                    .authorAvatar(author != null ? author.getAvatar() : "")
                    .authorCreditScore(author != null ? author.getCreditScore() : 100)
                    .title(post.getTitle())
                    .description(post.getDescription())
                    .price(post.getPrice())
                    .tradeType(post.getTradeType())
                    .deliveryType(post.getDeliveryType())
                    .tradeAddress(post.getTradeAddress())
                    .contactType(post.getContactType())
                    .contactValue(post.getContactValue())
                    .images(post.getImages())
                    .status(post.getStatus())
                    .viewCount(post.getViewCount())
                    .likeCount(post.getLikeCount())
                    .commentCount(post.getCommentCount())
                    .createTime(post.getCreateTime())
                    .hasLiked(finalLikedIds.contains(post.getId()))
                    .hasFavorited(finalFavIds.contains(post.getId()))
                    .comments(commentVoList)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * 详情页评论列表：评论人批量一次查询
     */
    private List<MarketPostVo.CommentVo> loadCommentVos(Long postId) {
        List<BizPostComment> comments = postCommentMapper.selectList(
                new LambdaQueryWrapper<BizPostComment>()
                        .eq(BizPostComment::getPostId, postId)
                        .eq(BizPostComment::getStatus, 1)
                        .orderByAsc(BizPostComment::getCreateTime)
        );
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = comments.stream()
                .flatMap(c -> Stream.concat(Stream.of(c.getUserId()),
                        c.getReplyToUserId() != null ? Stream.of(c.getReplyToUserId()) : Stream.empty()))
                .collect(Collectors.toSet());
        Map<Long, SysUser> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

        return comments.stream().map(c -> {
            SysUser commenter = userMap.get(c.getUserId());
            SysUser replyTo = c.getReplyToUserId() != null ? userMap.get(c.getReplyToUserId()) : null;
            return MarketPostVo.CommentVo.builder()
                    .id(c.getId())
                    .userId(c.getUserId())
                    .userName(commenter != null ? commenter.getRealName() : "邻里")
                    .userAvatar(commenter != null ? commenter.getAvatar() : "")
                    .parentId(c.getParentId())
                    .replyToUserId(c.getReplyToUserId())
                    .replyToUserName(replyTo != null ? replyTo.getRealName() : "")
                    .content(c.getContent())
                    .likeCount(c.getLikeCount())
                    .createTime(c.getCreateTime())
                    .build();
        }).collect(Collectors.toList());
    }

    private String cleanJson(String str) {
        if (str == null || str.trim().isEmpty() || "null".equalsIgnoreCase(str.trim()) || "\"\"".equals(str.trim())) {
            return null;
        }
        return str.trim();
    }
}
