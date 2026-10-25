package com.li.community.controller;

import com.li.community.common.result.Result;
import com.li.community.service.FileService;
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

    private final FileService fileService;

    @Operation(summary = "多媒体与图片上传接口 (MinIO 私有化对象存储, SHA-256 内容级秒传去重)")
    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "community") String folder) {
        Map<String, Object> data = fileService.upload(file, folder);
        return Result.success(Boolean.TRUE.equals(data.get("instant")) ? "秒传成功（服务器已存在相同文件）" : "上传成功", data);
    }

    @Operation(summary = "文件秒传预检 (前端上传前先查 SHA-256, 命中直接复用已有 URL 免传输)")
    @GetMapping("/upload/check")
    public Result<Map<String, Object>> checkInstantUpload(@RequestParam("sha256") String sha256) {
        String url = fileService.checkInstant(sha256);
        if (url != null) {
            return Result.success("秒传命中", Map.of("exists", true, "url", url));
        }
        return Result.success("未命中，请正常上传", Map.of("exists", false));
    }
}
