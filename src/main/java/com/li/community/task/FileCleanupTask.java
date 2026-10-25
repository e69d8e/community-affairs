package com.li.community.task;

import com.li.community.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 孤儿文件定时清理：删除桶内未被任何业务表引用、且超过保留宽限期的对象，
 * 避免用户上传后放弃提交表单（或业务记录删除后）遗留的垃圾文件无限累积。
 * 宽限期内不清理，防止正在编辑中的表单图片被误删。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileCleanupTask {

    private final FileService fileService;

    @Value("${file.cleanup.retention-hours:168}")
    private int retentionHours;

    @Scheduled(
            initialDelayString = "${file.cleanup.initial-delay-ms:60000}",
            fixedDelayString = "${file.cleanup.interval-ms:86400000}"
    )
    public void cleanupOrphanFiles() {
        try {
            int deleted = fileService.cleanupOrphanFiles(retentionHours);
            if (deleted > 0) {
                log.info("定时任务: 孤儿文件清理完成, 本次删除 {} 个未引用对象", deleted);
            }
        } catch (Exception e) {
            log.error("定时任务: 孤儿文件清理异常 -> {}", e.getMessage(), e);
        }
    }
}
