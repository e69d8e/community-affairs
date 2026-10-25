package com.li.community.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件秒传登记表：以内容 SHA-256 指纹去重（秒传），并作为孤儿文件清理的登记依据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_file")
public class SysFile implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /** 文件内容 SHA-256 指纹（全局唯一，秒传命中依据） */
    private String fileHash;

    /** 业务目录（avatar/order/market/community 等） */
    private String folder;

    /** MinIO 对象名（bucket 内相对路径） */
    private String objectName;

    /** 对象公开访问 URL（入库引用的就是它） */
    private String fileUrl;

    private String originalName;

    private String contentType;

    /** 文件大小（字节） */
    private Long fileSize;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long uploadUserId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
