package com.li.community.service;

import com.li.community.common.result.PageResult;
import com.li.community.model.dto.ActivityCreateDto;
import com.li.community.model.dto.ActivityVerifyDto;
import com.li.community.model.entity.BizActivity;
import com.li.community.model.entity.BizActivitySignup;
import com.li.community.model.vo.ActivityVo;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

public interface ActivityService {

    PageResult<ActivityVo> listActivities(String category, Integer status, String keyword, Long communityId, Long page, Long size);

    ActivityVo getActivityDetail(Long activityId);

    ActivityVo signupActivity(Long activityId);

    void cancelSignup(Long activityId);

    List<ActivityVo> getMySignups();

    BizActivitySignup verifySignup(ActivityVerifyDto dto);

    BizActivity createActivity(ActivityCreateDto dto);

    void updateActivityStatus(Long activityId, Integer status);

    void exportSignupsExcel(Long activityId, HttpServletResponse response);
}
