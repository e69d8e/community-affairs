package com.li.community.service;

import com.li.community.common.result.PageResult;
import com.li.community.model.dto.CommunitySaveDto;
import com.li.community.model.entity.BizCommunity;

public interface CommunityService {

    PageResult<BizCommunity> listCommunities(String keyword, Long page, Long size);

    void createCommunity(CommunitySaveDto dto);

    void updateCommunity(CommunitySaveDto dto);

    void updateCommunityStatus(Long communityId, Integer status);
}
