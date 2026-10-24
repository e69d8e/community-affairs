package com.li.community.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NoticeCreateDto {

    @NotBlank(message = "公告标题不能为空")
    private String title;

    @NotNull(message = "公告类型不能为空")
    private Integer noticeType; // 1-政策宣传, 2-便民提示, 3-物业通知, 4-突发应急广播

    private Long communityId;
    private Integer targetScope; // 1-全区广播, 2-指定楼栋
    private String targetBuildingIds; // JSON 数组如 "[201, 202]"
    private Integer isTop; // 1-置顶, 0-常规

    @NotBlank(message = "公告内容不能为空")
    private String content;

    private Integer status; // 1-正式发布, 0-草稿
}
