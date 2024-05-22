package com.dzdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dzdp.service.dto.Result;
import com.dzdp.service.dto.UserDTO;
import com.dzdp.entity.Follow;
import com.dzdp.mapper.FollowMapper;
import com.dzdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzdp.service.IUserService;
import com.dzdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.dzdp.utils.RedisConstants.FOLLOWERS;
import static com.dzdp.utils.RedisConstants.FOLLOWS;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    
    // 尝试关注用户
    @Override
    public Result follow(Long id, Boolean isFollow) {
        
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        
        Long userId = user.getId();
        // 关注列表
        String follows = FOLLOWS + userId;
        // 粉丝列表
        String followers = FOLLOWERS + id;
        // 用isfollow判断是否关注了该用户
        if (BooleanUtil.isFalse(isFollow)) {
            // 取关
            boolean removed = remove(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowUserId, userId)
                    .eq(Follow::getUserId, id));
            if (removed) {
                stringRedisTemplate.opsForSet().remove(follows, id.toString());
                stringRedisTemplate.opsForSet().remove(followers, userId.toString());
            }
            
        } else {
            // 关注
            Follow follow = new Follow();
            follow.setFollowUserId(userId);
            follow.setUserId(id);
            follow.setCreateTime(LocalDateTime.now());
            boolean isSuccess = save(follow);
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(follows, id.toString());
                stringRedisTemplate.opsForSet().add(followers, userId.toString());
            }
        }
        return Result.ok();
    }
    
    @Override
    public Result findFollowById(Long id) {
        Long userid = UserHolder.getUser().getId();
        String key1 = FOLLOWS + id;
        String key2 = FOLLOWS + userid;
        
        Set<String> allFollows = stringRedisTemplate.opsForSet().intersect(key2, key1);
        if (allFollows.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        
        List<Long> ids = allFollows
                .stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        
        if (ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        
        List<UserDTO> users = userService
                .listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        
        return Result.ok(users);
    }
    
    
    @Override
    public Result isFollow(Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        Long userId = user.getId();
        Integer count = query().eq("follow_user_id", userId)
                .eq("user_id", id).count();
        return Result.ok(count > 0);
    }
    
    
}
