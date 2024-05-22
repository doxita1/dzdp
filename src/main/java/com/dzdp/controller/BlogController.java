package com.dzdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dzdp.service.dto.Result;
import com.dzdp.service.dto.UserDTO;
import com.dzdp.entity.Blog;
import com.dzdp.service.IBlogService;
import com.dzdp.utils.SystemConstants;
import com.dzdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;


    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        // 修改点赞数量
        return blogService.likedBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {

        return blogService.queryHotBlog(current);
    }

    @GetMapping("/{id}")
    public Result queryBlog(@PathVariable("id") Long id){
        return blogService.queryblogByid(id);
    }

    @GetMapping("/likes/{id}")
    public Result queryLikedBlog(@PathVariable("id") Long id){
        return blogService.queryblogLiked(id);
    }

    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current",defaultValue = "1") Integer current,
            @RequestParam("id") Long id
    ){
        Page<Blog> blogs = blogService.query().eq("user_id", id)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = blogs.getRecords();
        return Result.ok(records);

    }


    @GetMapping("/of/follow")
    public Result queryFollowBlog(@RequestParam("lastId") Long max,
                                  @RequestParam(value = "offset",defaultValue = "0") Integer offset){
        return blogService.queryFollowBlog(max,offset);
    }
}
