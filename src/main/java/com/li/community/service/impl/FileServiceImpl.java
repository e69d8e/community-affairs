package com.li.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.community.common.exception.BizException;
import com.li.community.common.exception.ErrorCode;
import com.li.community.common.utils.MinioUtil;
import com.li.community.common.utils.SecurityUtils;
import com.li.community.mapper.SysFileMapper;
import com.li.community.model.entity.SysFile;
import com.li.community.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final MinioUtil minioUtil;
    private final SysFileMapper sysFileMapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 业务库中所有可能引用文件 URL 的列（含 JSON 数组与富文本，统一用正则提取 URL）。
     * 清理孤儿文件时全量扫描比对，被任何行引用（含逻辑删除行）的对象一律保留。
     */
    private static final String[][] REFERENCE_COLUMNS = {
            {"sys_user", "avatar"},
            {"biz_activity", "cover_image"},
            {"biz_market_post", "images"},
            {"biz_work_order", "images"},
            {"biz_work_order", "handle_images"},
            {"biz_report", "evidence_images"},
            {"biz_order_log", "evidence_images"},
            {"biz_resident_house", "cert_material"},
            {"biz_chat_message", "sender_avatar"},
            {"biz_chat_message", "receiver_avatar"},
            {"sys_notice", "content"},
    };

    /** 从 JSON 数组 / 富文本 / 普通字符串中提取 http(s) URL，遇引号、空白、尖括号截断 */
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>\\\\]+");

    @Override
    public Map<String, Object> upload(MultipartFile file, String folder) {
        String hash = sha256Hex(file);
        SysFile existing = findByHash(hash);
        if (existing != null) {
            log.info("文件秒传命中: hash={}, 复用对象 {}", hash, existing.getObjectName());
            return Map.of("url", existing.getFileUrl(), "instant", true);
        }

        String fileUrl = minioUtil.uploadFile(file, folder);
        String objectName = minioUtil.extractObjectName(fileUrl);
        SysFile record = SysFile.builder()
                .fileHash(hash)
                .folder(folder)
                .objectName(objectName)
                .fileUrl(fileUrl)
                .originalName(file.getOriginalFilename() == null ? "" : file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .uploadUserId(SecurityUtils.getUserId())
                .build();
        try {
            sysFileMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发上传相同内容：先入库者胜出，删除本次刚写入的重复对象并复用已有 URL
            SysFile winner = findByHash(hash);
            try {
                minioUtil.removeObject(objectName);
            } catch (Exception ex) {
                log.warn("并发秒传去重失败，重复对象保留: {} -> {}", objectName, ex.getMessage());
            }
            if (winner != null) {
                return Map.of("url", winner.getFileUrl(), "instant", true);
            }
            throw new BizException(ErrorCode.FILE_UPLOAD_ERROR, "文件登记冲突，请重试");
        }
        return Map.of("url", fileUrl, "instant", false);
    }

    @Override
    public String checkInstant(String sha256) {
        if (sha256 == null || !sha256.matches("(?i)[0-9a-f]{64}")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "sha256 参数须为 64 位十六进制字符串");
        }
        SysFile existing = findByHash(sha256.toLowerCase());
        return existing != null ? existing.getFileUrl() : null;
    }

    @Override
    public int cleanupOrphanFiles(int retentionHours) {
        Set<String> referencedUrls = collectReferencedUrls();
        Instant threshold = Instant.now().minus(Duration.ofHours(retentionHours));

        List<MinioUtil.ObjectInfo> objects;
        try {
            objects = minioUtil.listBucketObjects();
        } catch (Exception e) {
            log.error("孤儿文件清理: 列举 MinIO 对象失败, 本轮跳过 -> {}", e.getMessage());
            return 0;
        }

        Map<String, SysFile> registry = new HashMap<>();
        for (SysFile row : sysFileMapper.selectList(null)) {
            registry.put(row.getObjectName(), row);
        }

        int deleted = 0;
        Set<String> liveObjectNames = new HashSet<>();
        for (MinioUtil.ObjectInfo info : objects) {
            liveObjectNames.add(info.objectName());
            if (referencedUrls.contains(minioUtil.publicUrlOf(info.objectName()))) {
                continue;
            }
            SysFile row = registry.get(info.objectName());
            // 已登记对象按登记时间算宽限期，历史未登记对象按对象最后修改时间算
            Instant effectiveTime = row != null && row.getCreateTime() != null
                    ? row.getCreateTime().atZone(ZoneId.systemDefault()).toInstant()
                    : info.lastModified();
            if (effectiveTime.isAfter(threshold)) {
                continue;
            }
            try {
                minioUtil.removeObject(info.objectName());
                if (row != null) {
                    sysFileMapper.deleteById(row.getId());
                }
                deleted++;
                log.info("孤儿文件清理: 删除未引用对象 {} (超过 {} 小时宽限期)", info.objectName(), retentionHours);
            } catch (Exception e) {
                log.warn("孤儿文件清理: 删除对象失败 {} -> {}", info.objectName(), e.getMessage());
            }
        }

        // 桶里已不存在（人工删除等）但登记表仍残留的记录一并清掉
        int staleRegistry = 0;
        for (SysFile row : registry.values()) {
            if (!liveObjectNames.contains(row.getObjectName())) {
                sysFileMapper.deleteById(row.getId());
                staleRegistry++;
            }
        }
        log.info("孤儿文件清理完成: 桶内 {} 个对象, 删除 {} 个未引用且超宽限期对象, 清理失效登记 {} 条",
                objects.size(), deleted, staleRegistry);
        return deleted;
    }

    private SysFile findByHash(String hash) {
        return sysFileMapper.selectOne(
                new LambdaQueryWrapper<SysFile>().eq(SysFile::getFileHash, hash)
        );
    }

    private Set<String> collectReferencedUrls() {
        Set<String> urls = new HashSet<>();
        for (String[] column : REFERENCE_COLUMNS) {
            List<String> values = jdbcTemplate.queryForList(
                    "SELECT " + column[1] + " FROM " + column[0], String.class);
            for (String value : values) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                Matcher matcher = URL_PATTERN.matcher(value);
                while (matcher.find()) {
                    urls.add(matcher.group());
                }
            }
        }
        return urls;
    }

    private String sha256Hex(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                digest.update(buffer, 0, len);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new BizException(ErrorCode.FILE_UPLOAD_ERROR, "计算文件指纹失败: " + e.getMessage());
        }
    }
}
