package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 查询博文详情
     * @param id
     * @return
     */
    Result queryBlogId(Long id);

    /**
     * 点赞
     * @param id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 查询最热
     * @param current
     * @return
     */
    Result queryHotBlog(Integer current);

    /**
     * 查询点赞
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);

    /**
     * 保存
     * @param blog
     * @return
     */
    Result saveBlog(Blog blog);

    /**
     * 查询笔记推送
     * @param max
     * @param offset
     * @return
     */
    Result queryBlogOfFollow(Long max, Integer offset);
}
