package com.chocwell.jy.controller;

import com.chocwell.jy.service.LocationService;
import com.chocwell.jy.util.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 打卡点位接口（小程序端，无需登录）
 */
@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    /**
     * 获取营业中的点位列表
     * GET /api/v1/locations?districtCode=xxx
     */
    @GetMapping
    public R<?> list(@RequestParam(required = false) String districtCode) {
        return R.ok(locationService.listActive(districtCode));
    }

    /**
     * 获取单个点位详情
     * GET /api/v1/locations/{id}
     */
    @GetMapping("/{id}")
    public R<?> detail(@PathVariable Long id) {
        return R.ok(locationService.getById(id));
    }
}
