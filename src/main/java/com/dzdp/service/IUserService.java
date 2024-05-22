package com.dzdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzdp.service.dto.LoginFormDTO;
import com.dzdp.service.dto.Result;
import com.dzdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result logOut();
    
    Result sign();

    Result signCount();
}
