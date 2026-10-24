package com.li.community.controller;

import com.li.community.common.result.Result;
import com.li.community.common.utils.MinioUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "10. 通用公共模块 (文件上传与系统服务)")
@RestController
@RequestMapping("/api/common")
@RequiredArgsConstructor
public class CommonController {

    private final MinioUtil minioUtil;

    @Operation(summary = "多媒体与图片上传接口 (MinIO 私有化对象存储)")
    @PostMapping("/upload")
    public Result<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "community") String folder) {
        String fileUrl = minioUtil.uploadFile(file, folder);
        return Result.success("上传成功", Map.of("url", fileUrl));
    }
}
