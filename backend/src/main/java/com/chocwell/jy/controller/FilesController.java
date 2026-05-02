package com.chocwell.jy.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 静态文件服务（上传的图片等）
 * GET /api/v1/files/{filename}
 * 无需登录（图片 URL 需要对小程序端公开）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/files")
public class FilesController {

    private static final String UPLOAD_DIR = "/app/uploads";

    @GetMapping("/{filename:.+}")
    public void serveFile(@PathVariable String filename, HttpServletResponse response) {
        Path uploadDir = Paths.get(UPLOAD_DIR);
        Path file;
        try {
            file = uploadDir.resolve(filename).normalize();
        } catch (Exception e) {
            log.warn("[files] 非法文件名 filename={}", filename, e);
            safeSendError(response, HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // 防路径穿越
        if (!file.startsWith(uploadDir)) {
            log.warn("[files] 路径穿越尝试 filename={} resolved={}", filename, file);
            safeSendError(response, HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // 文件不存在 → 详细日志，便于定位
        if (!Files.exists(file)) {
            log.warn("[files] 文件不存在 path={}", file);
            safeSendError(response, HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (!Files.isReadable(file)) {
            log.error("[files] 文件存在但不可读（权限问题？）path={}", file);
            safeSendError(response, HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            log.error("[files] 读取文件大小失败 path={}", file, e);
            safeSendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        // 写响应头（在写 body 之前，否则会报 IllegalStateException）
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(guessContentType(filename));
        response.setContentLengthLong(size);
        response.setHeader("Cache-Control", "public, max-age=604800, immutable");

        try (OutputStream out = response.getOutputStream()) {
            Files.copy(file, out);
            out.flush();
        } catch (IOException e) {
            // 客户端断开 / 写入中断都会落到这里；记日志即可，header 已写不能再 sendError
            log.warn("[files] 写出文件中断 path={} reason={}", file, e.getMessage());
        } catch (Exception e) {
            log.error("[files] 写出文件未知异常 path={}", file, e);
        }
    }

    /** 按文件后缀返回 mime 类型，覆盖常见图片格式 */
    private String guessContentType(String filename) {
        String name = filename == null ? "" : filename.toLowerCase();
        if (name.endsWith(".png"))  return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".gif"))  return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".svg"))  return "image/svg+xml";
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    /** 包装 sendError，避免响应已写后再次抛 IOException 导致 500 */
    private void safeSendError(HttpServletResponse response, int code) {
        try {
            if (!response.isCommitted()) response.sendError(code);
        } catch (IOException ignore) { /* 客户端断开时无需处理 */ }
    }
}
