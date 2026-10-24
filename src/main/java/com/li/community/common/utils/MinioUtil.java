package com.li.community.common.utils;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.IdUtil;
import com.li.community.common.exception.BizException;
import com.li.community.common.exception.ErrorCode;
import io.minio.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Date;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioUtil {

    private final MinioClient minioClient;

    // 允许上传的扩展名 -> 固定 Content-Type（不信任客户端声明的类型）
    private static final Map<String, String> ALLOWED_EXTENSIONS = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "pdf", "application/pdf"
    );

    // 允许的业务目录，防止任意构造对象路径
    private static final Set<String> ALLOWED_FOLDERS = Set.of(
            "community", "avatar", "order", "market", "activity", "house-cert", "notice"
    );

    @Value("${minio.bucket-name:community-files}")
    private String defaultBucket;

    @Value("${minio.endpoint:http://127.0.0.1:9000}")
    private String endpoint;

    /**
     * 浏览器访问文件的公开地址（用于拼接入库的文件 URL）。
     * 容器化部署时 API 调用走内网地址（如 http://ca-minio:9000），
     * 而浏览器无法解析内网服务名，需配置为宿主机可达地址（如 http://<服务器IP>:9000）；
     * 留空时回退为 endpoint 本身（本地开发场景两者一致）。
     */
    @Value("${minio.public-endpoint:}")
    private String publicEndpoint;

    @PostConstruct
    public void initBucket() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(defaultBucket).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(defaultBucket).build());
                log.info("MinIO 初始化成功创建存储桶: {}", defaultBucket);

                // 设置公开只读访问策略，便于前端图片展示（不放行 ListBucket，避免匿名枚举对象）
                String policy = """
                        {
                          "Version": "2012-10-17",
                          "Statement": [
                            {
                              "Effect": "Allow",
                              "Principal": {"AWS": ["*"]},
                              "Action": ["s3:GetBucketLocation"],
                              "Resource": ["arn:aws:s3:::%s"]
                            },
                            {
                              "Effect": "Allow",
                              "Principal": {"AWS": ["*"]},
                              "Action": ["s3:GetObject"],
                              "Resource": ["arn:aws:s3:::%s/*"]
                            }
                          ]
                        }
                        """.formatted(defaultBucket, defaultBucket);

                minioClient.setBucketPolicy(
                        SetBucketPolicyArgs.builder().bucket(defaultBucket).config(policy).build()
                );
            }
        } catch (Exception e) {
            log.error("MinIO 存储桶初始化检查异常: {}", e.getMessage());
        }
    }

    public String uploadFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        String safeFolder = (folder == null || folder.isBlank()) ? "community" : folder.trim();
        if (!ALLOWED_FOLDERS.contains(safeFolder)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_SUPPORTED, "不支持的上传目录: " + safeFolder);
        }
        String originalFilename = file.getOriginalFilename();
        String ext = FileNameUtil.extName(originalFilename).toLowerCase();
        String contentType = ALLOWED_EXTENSIONS.get(ext);
        if (contentType == null) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_SUPPORTED, "仅支持 jpg/jpeg/png/webp/pdf 格式文件");
        }
        try {
            String datePath = DateUtil.format(new Date(), "yyyy/MM/dd");
            String newFileName = safeFolder + "/" + datePath + "/" + IdUtil.fastSimpleUUID() + "." + ext;

            InputStream inputStream = file.getInputStream();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(defaultBucket)
                            .object(newFileName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(contentType)
                            .build()
            );

            // 返回外部访问 URL（容器部署时 publicEndpoint 为宿主机可达地址）
            return publicBase() + "/" + defaultBucket + "/" + newFileName;
        } catch (Exception e) {
            log.error("MinIO 文件上传失败: ", e);
            throw new BizException(ErrorCode.FILE_UPLOAD_ERROR, "文件上传失败: " + e.getMessage());
        }
    }

    public void removeFile(String fileUrl) {
        try {
            String objectName = extractObjectName(fileUrl);
            if (objectName != null) {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(defaultBucket)
                                .object(objectName)
                                .build()
                );
            }
        } catch (Exception e) {
            log.warn("MinIO 删除文件异常: {}", e.getMessage());
        }
    }

    private String publicBase() {
        return (publicEndpoint == null || publicEndpoint.isBlank()) ? endpoint : publicEndpoint;
    }

    /**
     * 从文件 URL 中提取对象名，兼容 endpoint 与 public-endpoint 两种前缀
     */
    private String extractObjectName(String fileUrl) {
        if (fileUrl == null) {
            return null;
        }
        for (String base : new String[]{endpoint, publicEndpoint}) {
            if (base != null && !base.isBlank()) {
                String prefix = base + "/" + defaultBucket + "/";
                if (fileUrl.startsWith(prefix)) {
                    return fileUrl.substring(prefix.length());
                }
            }
        }
        return null;
    }
}
