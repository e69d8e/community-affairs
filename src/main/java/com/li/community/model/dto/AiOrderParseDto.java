package com.li.community.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "居民口语化报修 AI 智能结构化解析入参")
public class AiOrderParseDto {

    @NotBlank(message = "报修描述不能为空")
    @Schema(description = "居民口语化长文本描述", example = "我家阳台顶上的水管突然爆开了，水一直在往外喷，洗衣机插座都快被淋到了，特别危险赶紧来人！")
    private String rawText;
}
