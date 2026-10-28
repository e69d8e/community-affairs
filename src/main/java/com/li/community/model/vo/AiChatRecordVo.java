package com.li.community.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRecordVo implements Serializable {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /**
     * user / assistant
     */
    private String role;

    /**
     * 消息正文
     */
    private String content;

    /**
     * 业务直达卡片对象
     */
    private Map<String, Object> card;

    /**
     * 友好时间展示 (HH:mm)
     */
    private String time;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
