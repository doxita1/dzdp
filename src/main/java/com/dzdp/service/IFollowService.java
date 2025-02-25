package com.dzdp.service;

import com.dzdp.service.dto.Result;
import com.dzdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    Result isFollow(Long id);

    Result follow(Long id, Boolean isFollow);

    Result findFollowById(Long id);
}
