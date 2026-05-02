package com.chocwell.jy.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 文件存储服务
 *
 * 模式说明：
 *   1. 配置 app.oss.enabled=true 且凭证有效 → 走阿里云 OSS，返回完整 https URL
 *   2. 否则 fallback 到本地 /app/uploads，返回相对路径 /api/v1/files/{filename}
 *
 * 业务代码（CheckinController.uploadPhoto / AdminController.upload）只需调
 * ossService.upload(file, prefix) 拿到 url 写库即可，不关心底层存储。
 */
@Slf4j
@Service
public class OssService {

    private static final String LOCAL_DIR = "/app/uploads";
    private static final String LOCAL_URL_PREFIX = "/api/v1/files/";

    @Value("${app.oss.enabled:false}")
    private boolean enabled;

    @Value("${app.oss.endpoint:}")
    private String endpoint;

    @Value("${app.oss.access-key-id:}")
    private String accessKeyId;

    @Value("${app.oss.access-key-secret:}")
    private String accessKeySecret;

    @Value("${app.oss.bucket-name:}")
    private String bucketName;

    @Value("${app.oss.domain:}")
    private String customDomain;

    private volatile OSS ossClient;

    /**
     * 上传 MultipartFile，返回可访问的 URL（OSS 完整地址 或 本地相对路径）
     * @param file   上传的文件
     * @param prefix 文件名前缀（如 "checkin"、"avatar"、"gift"），用于归类
     */
    public String upload(MultipartFile file, String prefix) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : ".jpg";
        String filename = (prefix == null || prefix.isBlank() ? "file" : prefix)
                          + "_" + UUID.randomUUID().toString().replace("-", "") + ext;

        if (isOssActive()) {
            return uploadToOss(file, filename);
        }
        return uploadToLocal(file, filename);
    }

    /** OSS 是否真正可用（启用 + 凭证非占位符） */
    public boolean isOssActive() {
        return enabled
            && accessKeyId != null && !accessKeyId.isBlank() && !accessKeyId.startsWith("your_")
            && accessKeySecret != null && !accessKeySecret.isBlank() && !accessKeySecret.startsWith("your_")
            && bucketName != null && !bucketName.isBlank() && !bucketName.startsWith("your_")
            && endpoint != null && !endpoint.isBlank();
    }

    // ─── 本地存储 ──────────────────────────────────────────────

    private String uploadToLocal(MultipartFile file, String filename) throws IOException {
        Path dir = Paths.get(LOCAL_DIR);
        Files.createDirectories(dir);
        Path target = dir.resolve(filename);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        log.debug("[OSS][local] 文件保存到本地 {}", target);
        return LOCAL_URL_PREFIX + filename;
    }

    // ─── OSS 存储 ──────────────────────────────────────────────

    private String uploadToOss(MultipartFile file, String filename) throws IOException {
        OSS client = getOrCreateClient();
        ObjectMetadata meta = new ObjectMetadata();
        if (file.getContentType() != null) meta.setContentType(file.getContentType());
        meta.setContentLength(file.getSize());

        try (InputStream in = file.getInputStream()) {
            client.putObject(bucketName, filename, in, meta);
        }
        String url = buildOssUrl(filename);
        log.info("[OSS] 文件已上传 {}", url);
        return url;
    }

    private String buildOssUrl(String filename) {
        if (customDomain != null && !customDomain.isBlank()) {
            String d = customDomain.endsWith("/") ? customDomain.substring(0, customDomain.length() - 1) : customDomain;
            return d + "/" + filename;
        }
        return "https://" + bucketName + "." + endpoint + "/" + filename;
    }

    private OSS getOrCreateClient() {
        OSS c = ossClient;
        if (c == null) {
            synchronized (this) {
                c = ossClient;
                if (c == null) {
                    c = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
                    ossClient = c;
                }
            }
        }
        return c;
    }

    @PreDestroy
    public void shutdown() {
        if (ossClient != null) {
            try { ossClient.shutdown(); } catch (Exception ignore) {}
        }
    }
}
