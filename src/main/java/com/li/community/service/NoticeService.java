package com.li.community.service;

import com.li.community.common.result.PageResult;
import com.li.community.model.dto.NoticeCreateDto;
import com.li.community.model.entity.SysNotice;
import com.li.community.model.entity.SysUserNotice;

import java.util.Map;

public interface NoticeService {

    PageResult<SysNotice> listNotices(Integer noticeType, Long communityId, Long page, Long size);

    SysNotice getNoticeDetail(Long noticeId);

    SysNotice publishNotice(NoticeCreateDto dto);

    PageResult<SysUserNotice> getMyUserNotices(Long page, Long size);

    void markNoticeRead(Long id);

    void markAllRead();

    Long getUnreadNoticeCount();
}
