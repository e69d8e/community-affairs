package com.li.community.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityVo implements Serializable {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private String title;
    private String coverImage;
    private String category;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long communityId;
    private String location;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signupStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signupEndTime;

    private Integer maxParticipants;
    private Integer currentParticipants;
    private Integer status;
    private String organizer;
    private String contactPhone;
    private String description;

    // 当前登录用户的报名与签到状态
    private Boolean hasSignedUp;
    private Integer mySignupStatus; // 1-已报, 2-已签, 3-已取消
    private String myVerifyCode;
    private String myQrCodeBase64;
}
