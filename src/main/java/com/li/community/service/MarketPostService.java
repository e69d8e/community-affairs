package com.li.community.service;

import com.li.community.common.result.PageResult;
import com.li.community.model.dto.CommentCreateDto;
import com.li.community.model.dto.MarketPostCreateDto;
import com.li.community.model.entity.BizPostComment;
import com.li.community.model.vo.MarketPostVo;

public interface MarketPostService {

    PageResult<MarketPostVo> listPosts(Integer postType, Integer status, String keyword, Long page, Long size);

    MarketPostVo getPostDetail(Long postId);

    MarketPostVo publishPost(MarketPostCreateDto dto);

    void updatePostStatus(Long postId, Integer status);

    BizPostComment addComment(CommentCreateDto dto);

    boolean toggleLike(Long postId);

    boolean toggleFavorite(Long postId);

    PageResult<MarketPostVo> getMyPosts(Long page, Long size);

    PageResult<MarketPostVo> getMyFavorites(Long page, Long size);
}
