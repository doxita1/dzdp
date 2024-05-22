package com.dzdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzdp.service.dto.LoginFormDTO;
import com.dzdp.service.dto.Result;
import com.dzdp.service.dto.UserDTO;
import com.dzdp.entity.User;
import com.dzdp.mapper.UserMapper;
import com.dzdp.service.IUserService;
import com.dzdp.utils.RegexUtils;
import com.dzdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.dzdp.utils.RedisConstants.*;
import static com.dzdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    
    
    public static final String SECRET_KEY = "my secret";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号正确否
        if (RegexUtils.isPhoneInvalid(phone)) {
            //如不正确, 报错
            return Result.fail("手机号格式错误");
        }
        // 生成验证码
        String code = RandomUtil.randomNumbers(6);
//        // 将验证码写入session中
//        session.setAttribute("code",code);
        // 将验证码存到redis中
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 发送验证码
        log.info("发送了验证码:{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.重新校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如不正确, 报错
            return Result.fail("手机号格式错误");
        }
        // 3.校验验证码
        String code = loginForm.getCode();
//        String code1 = (String) session.getAttribute("code");
        // 4.从redis中读出验证码
        String code1 = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        // 5.不正确报错

        if (!code.equals(code1)) {
            return Result.fail("验证码错误");
        }
        // 6.根据手机号查找用户是否存在
//        User one = lambdaQuery().eq(User::getPhone, phone).one();
        User one = query().eq("phone", phone).one();
        // 7.不存在就创键新用户
        if (one == null) {
            one = createUser(phone);
        }
        // 得到DTO对象
        UserDTO userDTO = BeanUtil.copyProperties(one, UserDTO.class);
        
        // 将DTO存入Map中
        Map<String, Object> objectMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create() // copyOptions用来修改转换时的属性, 下面将字段的值全部设置为string类型
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldname,fieldvalue)-> fieldvalue.toString()));
        // 存入redis中
        
        JWTSigner signer = JWTSignerUtil.hs256(SECRET_KEY.getBytes());
        // 自定义的有效负载
        // 设置过期时间
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR, 1); // 1小时后过期
        
        // 生成 JWT
        String token = JWT.create()
                .addPayloads(objectMap)
                .setExpiresAt(calendar.getTime()) // 设置过期时间
                .sign(signer); // 签名
        
        // TODO 保存用户信息到redis中
//        String token = UUID.randomUUID(true).toString();
        String userToken = LOGIN_USER_KEY + token;
        
        stringRedisTemplate.opsForHash().putAll(userToken, objectMap);
        // TODO 设置token有效期
        stringRedisTemplate.expire(userToken,LOGIN_USER_TTL,TimeUnit.MINUTES);
//        // 保存到session中
//        session.setAttribute("user", BeanUtil.copyProperties(one, UserDTO.class));
        return Result.ok(token);
    }
    
    @Override
    public Result logOut() {
        UserHolder.removeUser();
        return Result.ok();
    }
    
    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyy/MM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.写入Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    // 计算本月连续签到天数
    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyy/MM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        List<Long> longs = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                .valueAt(0));

        if(longs == null || longs.isEmpty()){
            return Result.ok(0);
        }

        // 只进行了get操作, get(0)就是当前天数
        Long signedDay = longs.get(0);

        if (signedDay == null || signedDay ==0){
            return Result.ok(0);
        }
        int count = 0;
        //计算本月连续签到天数
        while((signedDay & 1) != 0){
            count++;
            signedDay = signedDay>>>1;
        }

        return Result.ok(count);
    }

    private User createUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 保存到数据库
        save(user);
        return user;
    }
}
