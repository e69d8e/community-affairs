package com.li.community.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件上传服务：SHA-256 内容指纹秒传去重 + 孤儿文件清理
 */
public interface FileService {

    /**
     * 上传文件。先按内容 SHA-256 查 sys_file 登记表，命中直接复用已有对象（秒传），
     * 未命中才真正写入 MinIO 并登记。
     *
     * @return url - 对象访问 URL；instant - 是否秒传命中（未实际传输存储）
     */
    Map<String, Object> upload(MultipartFile file, String folder);

    /**
     * 秒传预检：按 SHA-256 查询服务器是否已存在相同内容文件
     *
     * @return 命中返回已有文件 URL，未命中返回 null
     */
    String checkInstant(String sha256);

    /**
     * 清理孤儿文件：扫描桶内对象，删除未被任何业务表引用且超出保留宽限期的对象，
     * 并同步清理 sys_file 登记表中已失效的记录。
     *
     * @param retentionHours 未引用文件保留宽限期（小时），宽限期内不清理（可能正被表单编辑引用）
     * @return 删除的对象数量
     */
    int cleanupOrphanFiles(int retentionHours);
}
