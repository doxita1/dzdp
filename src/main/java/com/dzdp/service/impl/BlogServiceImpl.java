package com.dzdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dzdp.service.dto.Result;
import com.dzdp.service.dto.ScrollResult;
import com.dzdp.service.dto.UserDTO;
import com.dzdp.entity.Blog;
import com.dzdp.entity.User;
import com.dzdp.mapper.BlogMapper;
import com.dzdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzdp.service.IUserService;
import com.dzdp.utils.SystemConstants;
import com.dzdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.dzdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private IBlogService blogService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryblogByid(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("博客不存在");
        }
        queryBlogUser(blog);
        blogIsLiked(blog);
        return Result.ok(blog);

    }


    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            blogIsLiked(blog);

        });
        return Result.ok(records);
    }

    @Override
    public Result likedBlog(Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 没有登录的情况
            return Result.ok();
        }
        // 获取用户id
        Long userId = user.getId();
        // 判断用户是否点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.
                opsForZSet().score(key, userId.toString());
        // 没有点赞, 存入redis中, 并且修改数据库
        if (score == null) {

            boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
            // 添加成功, 存入redis
            if (success) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 点赞了, 修改数据库,并且从redis中移除该用户信息,
            boolean success = update().setSql("liked = liked - 1").eq("id", id).update();
            if (success) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();

    }

    @Override
    public Result queryblogLiked(Long id) {
        String key = BLOG_LIKED_KEY + id;
        // 拿到用户信息
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        //用户id列表
        if (top5 == null) {
            return Result.ok();
        }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());

        // 拼接sql语句
        String userIds = StrUtil.join(",", ids);

        try {
            List<UserDTO> userDTOS = userService.query()
                    .in("id", ids)
                    .last("ORDER BY FIELD(id," + userIds + ")")
                    .list()
                    .stream()
                    .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                    .collect(Collectors.toList());

            return Result.ok(userDTOS);
        } catch (Exception e) {
            return Result.ok(Collections.emptyList());
        }
    }

    @Override
    @Transactional
    public Result saveBlog(Blog blog) {

        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSaved = save(blog);
        if (!isSaved) {
            return Result.fail("保存失败");
        }

        String followers = FOLLOWERS + blog.getUserId();
        //找到粉丝
        Set<String> members = stringRedisTemplate.opsForSet().members(followers);
        if (members == null) {
            // 没有粉丝
            return Result.ok(blog.getId());
        }
        // 有粉丝,给每个粉丝推送
        List<Long> ids = members.stream().map(Long::valueOf)
                .collect(Collectors.toList());

        for (Long aLong : ids) {
            String message = FEED_KEY + aLong;
            stringRedisTemplate.opsForZSet().
                    add(message, blog.getId().toString(), System.currentTimeMillis());
        }

        return Result.ok(blog.getId());
    }

    @Override
    public Result queryFollowBlog(Long max, Integer offset) {
        // 拿到当前用户
        Long userId = UserHolder.getUser().getId();
        // 拿到推送
        String feedKey = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().
                reverseRangeByScoreWithScores(feedKey, 0, max, offset, 3);

        // 推送判空
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }

        long minId = 0;
        int minCount = 1;
        List<Long> idList = new ArrayList<>(typedTuples.size());

        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            idList.add(Long.valueOf(typedTuple.getValue()));
            long value = typedTuple.getScore().longValue();

            if (value == minId) {
                minCount++;
            } else {
                minId = value;
                minCount = 1;
            }
        }
        String idStr = StrUtil.join(",", idList);
        List<Blog> blogs = query().in("id", idList)
                .last("ORDER BY FIELD(id," + idStr + ")").list();

        blogs.forEach(blog -> {
            queryBlogUser(blog);
            blogIsLiked(blog);
        });

        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(minCount);
        scrollResult.setMinTime(minId);

        return Result.ok(scrollResult);
    }

    // 完善blog信息
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    // 判断isliked字段
    private void blogIsLiked(Blog blog) {
        Long userId = UserHolder.getUser().getId();
        Double score = stringRedisTemplate.
                opsForZSet().score(BLOG_LIKED_KEY + blog.getId(), userId.toString());
        if (score != null) {
            blog.setIsLike(true);
        }
    }


}
