package com.chocwell.jy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chocwell.jy.entity.Article;
import com.chocwell.jy.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleMapper articleMapper;

    /**
     * 小程序端：获取已发布文章列表（按 districtCode 过滤 + 全域文章）
     * 缓存 key：articles::{districtCode}_{type}
     * TTL 5 分钟（见 RedisConfig）
     */
    @Cacheable(value = "articles",
               key = "(#districtCode != null && !#districtCode.isBlank() ? #districtCode : '_all') + '_' + (#type != null ? #type : '0')")
    public List<Article> listPublished(String districtCode, Integer type) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, 1);

        // 传入了区划码才做区划过滤；否则返回全部已发布文章（全域可见）
        if (districtCode != null && !districtCode.isBlank()) {
            wrapper.and(w -> w.isNull(Article::getDistrictCode)
                    .or(c -> c.eq(Article::getDistrictCode, ""))
                    .or(c -> c.eq(Article::getDistrictCode, districtCode)));
        }

        if (type != null) {
            wrapper.eq(Article::getType, type);
        }
        wrapper.orderByDesc(Article::getSortOrder).orderByDesc(Article::getCreateTime);
        return articleMapper.selectList(wrapper);
    }

    /**
     * 管理后台：分页查询（不缓存，管理端读最新数据）
     */
    public Page<Article> pageAdmin(int pageNum, int pageSize, Integer type, String keyword) {
        Page<Article> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .orderByDesc(Article::getCreateTime);
        if (type != null) wrapper.eq(Article::getType, type);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Article::getTitle, keyword);
        }
        return articleMapper.selectPage(page, wrapper);
    }

    public Article getById(Long id) {
        return articleMapper.selectById(id);
    }

    /** 保存后清除 articles 缓存 */
    @CacheEvict(value = "articles", allEntries = true)
    public void save(Article article) {
        if (article.getId() == null) {
            articleMapper.insert(article);
        } else {
            articleMapper.updateById(article);
        }
    }

    /** 删除后清除 articles 缓存 */
    @CacheEvict(value = "articles", allEntries = true)
    public void delete(Long id) {
        articleMapper.deleteById(id);
    }
}
