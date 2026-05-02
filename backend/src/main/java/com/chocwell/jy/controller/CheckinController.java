package com.chocwell.jy.controller;

import com.chocwell.jy.interceptor.UserAuthInterceptor;
import com.chocwell.jy.service.CheckinService;
import com.chocwell.jy.service.OssService;
import com.chocwell.jy.util.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 打卡相关接口（小程序端）
 * 所有接口需要 Bearer token，由 UserAuthInterceptor 验证
 */
@RestController
@RequestMapping("/api/v1/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;
    private final OssService     ossService;

    /**
     * 扫码进入打卡页时的初始化检查
     * GET /api/v1/checkin/init?locationId=xxx
     */
    @GetMapping("/init")
    public R<?> initCheckin(@RequestParam Long locationId,
                            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(UserAuthInterceptor.USER_ID_KEY);
        Map<String, Object> data = checkinService.initCheck(userId, locationId);
        return R.ok(data);
    }

    /**
     * 提交打卡
     * POST /api/v1/checkin/submit
     * body: { locationId, longitude, latitude, photoUrl }
     */
    @PostMapping("/submit")
    public R<?> submitCheckin(@RequestBody Map<String, Object> body,
                              HttpServletRequest request) {
        Long userId    = (Long) request.getAttribute(UserAuthInterceptor.USER_ID_KEY);
        Long locationId = Long.valueOf(body.get("locationId").toString());
        double lng     = Double.parseDouble(body.get("longitude").toString());
        double lat     = Double.parseDouble(body.get("latitude").toString());
        String photo   = (String) body.get("photoUrl");

        Map<String, Object> data = checkinService.submit(userId, locationId, lng, lat, photo);
        return R.ok(data);
    }

    /**
     * 用户打卡历史
     * GET /api/v1/checkin/history
     */
    @GetMapping("/history")
    public R<?> history(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(UserAuthInterceptor.USER_ID_KEY);
        return R.ok(checkinService.history(userId));
    }

    /**
     * 上传打卡照片（小程序端）
     * POST /api/v1/checkin/upload-photo  multipart/form-data  file=...
     * 走 OssService：配了阿里云 OSS 就上传到云端，未配置走本地 /app/uploads
     * 返回：{ url: "https://..." 或 "/api/v1/files/{filename}" }
     */
    @PostMapping("/upload-photo")
    public R<?> uploadPhoto(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return R.fail("照片为空");
        String ct = file.getContentType() != null ? file.getContentType() : "";
        if (!ct.startsWith("image/")) return R.fail("只支持上传图片文件");
        return R.ok(Map.of("url", ossService.upload(file, "checkin")));
    }
}
