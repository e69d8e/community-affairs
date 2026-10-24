package com.li.community.controller;

import com.li.community.common.annotation.LogOperation;
import com.li.community.common.result.PageResult;
import com.li.community.common.result.Result;
import com.li.community.model.dto.CommentCreateDto;
import com.li.community.model.dto.MarketPostCreateDto;
import com.li.community.model.entity.BizPostComment;
import com.li.community.model.vo.MarketPostVo;
import com.li.community.service.MarketPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "05. 邻里互助与二手闲置集市模块")
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketPostController {

    private final MarketPostService marketPostService;

    @Operation(summary = "公开/居民浏览闲置与互助列表 (登录后默认本人小区; 匿名需携带 communityId)")
    @GetMapping("/list")
    public Result<PageResult<MarketPostVo>> listPosts(
            @RequestParam(required = false) Integer postType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long communityId,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size) {
        return Result.success(marketPostService.listPosts(postType, status, keyword, communityId, page, size));
    }

    @Operation(summary = "获取帖子详情 (含全部评论与收藏点赞状态)")
    @GetMapping("/detail/{id}")
    public Result<MarketPostVo> getPostDetail(@PathVariable Long id) {
        return Result.success(marketPostService.getPostDetail(id));
    }

    @Operation(summary = "居民发布闲置转让/求助信息 (自动触发 DFA 敏感词过滤)")
    @PostMapping("/publish")
    @LogOperation(title = "发布闲置互助", businessType = "INSERT", saveParam = true)
    public Result<MarketPostVo> publishPost(@Valid @RequestBody MarketPostCreateDto dto) {
        return Result.success("发布成功", marketPostService.publishPost(dto));
    }

    @Operation(summary = "发布者/管理员变更流转状态 (如锁定/结题)")
    @PutMapping("/status")
    public Result<Void> updatePostStatus(@RequestBody Map<String, Object> params) {
        Long postId = Long.parseLong(params.get("postId").toString());
        Integer status = Integer.parseInt(params.get("status").toString());
        marketPostService.updatePostStatus(postId, status);
        return Result.success("状态流转更新成功", null);
    }

    @Operation(summary = "发表邻里互动评论 (自动触发敏感词过滤)")
    @PostMapping("/comment")
    public Result<BizPostComment> addComment(@Valid @RequestBody CommentCreateDto dto) {
        return Result.success("评论发表成功", marketPostService.addComment(dto));
    }

    @Operation(summary = "为帖子点赞")
    @PostMapping("/like/{id}")
    public Result<Boolean> toggleLike(@PathVariable Long id) {
        return Result.success(marketPostService.toggleLike(id));
    }

    @Operation(summary = "收藏/取消收藏帖子")
    @PostMapping("/favorite/{id}")
    public Result<Boolean> toggleFavorite(@PathVariable Long id) {
        boolean favorited = marketPostService.toggleFavorite(id);
        return Result.success(favorited ? "收藏成功" : "已取消收藏", favorited);
    }

    @Operation(summary = "居民获取本人发布的闲置互助列表")
    @GetMapping("/my-posts")
    public Result<PageResult<MarketPostVo>> getMyPosts(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size) {
        return Result.success(marketPostService.getMyPosts(page, size));
    }

    @Operation(summary = "居民获取本人收藏列表")
    @GetMapping("/my-favorites")
    public Result<PageResult<MarketPostVo>> getMyFavorites(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size) {
        return Result.success(marketPostService.getMyFavorites(page, size));
    }
}
