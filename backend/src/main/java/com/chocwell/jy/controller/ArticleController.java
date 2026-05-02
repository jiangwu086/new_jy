package com.chocwell.jy.controller;

import com.chocwell.jy.service.ArticleService;
import com.chocwell.jy.util.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 文章/内容接口（小程序端，无需登录）
 */
@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    /**
     * 获取已发布文章列表
     * GET /api/v1/articles?districtCode=xxx&type=1
     * type: 1-政策说明, 2-理赔指南, 3-警示案例
     */
    @GetMapping
    public R<?> list(@RequestParam(required = false) String districtCode,
                     @RequestParam(required = false) Integer type) {
        return R.ok(articleService.listPublished(districtCode, type));
    }

    /**
     * 文章详情
     * GET /api/v1/articles/{id}
     */
    @GetMapping("/{id}")
    public R<?> detail(@PathVariable Long id) {
        return R.ok(articleService.getById(id));
    }
}
