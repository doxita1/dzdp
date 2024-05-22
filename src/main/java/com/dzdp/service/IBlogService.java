package com.dzdp.service;

import com.dzdp.service.dto.Result;
import com.dzdp.entity.Blog;
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

    Result queryblogByid(Long id);

    Result queryHotBlog(Integer current);

    Result likedBlog(Long id);

    Result queryblogLiked(Long id);

    Result saveBlog(Blog blog);

    Result queryFollowBlog(Long max, Integer offset);
}
