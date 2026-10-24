package com.li.community.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActivityCreateDto {

    @NotBlank(message = "活动主题不能为空")
    private String title;

    private String coverImage;

    @NotBlank(message = "活动分类不能为空")
    private String category;

    private Long communityId;

    @NotBlank(message = "活动开展地点不能为空")
    private String location;

    @NotNull(message = "活动开始时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @NotNull(message = "活动结束时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @NotNull(message = "报名开启时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signupStartTime;

    @NotNull(message = "报名截止时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signupEndTime;

    @NotNull(message = "报名人数上限不能为空")
    @Min(value = 1, message = "报名人数上限至少为1人")
    private Integer maxParticipants;

    private String organizer;
    private String contactPhone;

    @NotBlank(message = "活动详情图文介绍不能为空")
    private String description;
}
