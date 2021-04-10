package com.shiliu.dragon.controller;

import com.shiliu.dragon.common.cache.SessionCache;
import com.shiliu.dragon.common.utils.JsonUtil;
import com.shiliu.dragon.dao.UserDao;
import com.shiliu.dragon.model.user.User;
import com.shiliu.dragon.security.validate.code.SmsResponse;
import com.shiliu.dragon.security.validate.code.ValidateCode;
import com.shiliu.dragon.security.validate.code.ValidateCodeException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.List;

/**
 * @author ouyangchao
 * @createTime
 * @description
 */

@Transactional
@RestController
@RequestMapping("/dragon/user")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserDao userDao;

    @PostMapping("/register")
    public String register(@RequestBody String userContext ){
        logger.info("Begin register " + userContext);
        User user = JsonUtil.readValue(userContext,User.class);
        try {
            validUser(user);
        } catch (ServletRequestBindingException e) {
            logger.error("Check smsCode ServletRequestBindingException ",e);
            return JsonUtil.toJson(SmsResponse.SMSNOTEXIST);
        } catch (ValidateCodeException e) {
            logger.error("Check smsCode IOException ",e);
            return e.getMessage();
        }
        userDao.addUser(user);
        logger.info("Add user {} success",user.getMobile());
        //注册用户
        return JsonUtil.toJson(UserResponse.REGISTER_SUCCESS);
    }

    @GetMapping("/{id}")
    public String queryUserById(@PathVariable(name = "id") String id){
        try{
            logger.info("begin query user " + id);
            isValidMobile(id);
            User user = userDao.queryUserById(id);
            if(user != null){
                UserResponse userResponse = UserResponse.QUERY_USER_SUCCESS;
                userResponse.setMessage(JsonUtil.toJson(user));
                logger.info("Query user {} success",id);
                return JsonUtil.toJson(userResponse);
            }
            logger.warn("User {} not exit",id);
        }catch (ValidateCodeException e){
            logger.error("Quesy user with ValidateCodeException ",e);
        }
        return JsonUtil.toJson(UserResponse.USER_NOT_EXIST);
    }

    @GetMapping
    public String query(HttpServletRequest request){
        int offset = request.getParameter("offset") == null?0:Integer.parseInt(request.getParameter("offset"));
        int limit = request.getParameter("pageSize") == null?10:Integer.parseInt(request.getParameter("pageSize"));
        logger.info("Begin quesy user offset {} pageSize {}",offset, limit);
        List<User> users = userDao.queryUsers(offset,limit);
        UserResponse userResponse = UserResponse.QUERY_USER_SUCCESS;
        if(users == null || users.isEmpty()){
            logger.warn("No condition users");
            userResponse.setMessage(JsonUtil.toJson(Collections.EMPTY_LIST));
        }else {
            userResponse.setMessage(JsonUtil.toJson(users));
        }
        return JsonUtil.toJson(userResponse);
    }

    /**
     * 查询全部用户
     * @return
     */


    /**
     * 校验sm是否合法
     * @param user
     * @return
     * @throws ServletRequestBindingException
     * @throws ValidateCodeException
     */
    private boolean validUser(User user) throws ServletRequestBindingException, ValidateCodeException {
        //请求参数值
        if(user == null){
            throw new ValidateCodeException(JsonUtil.toJson(SmsResponse.SMSISEMPTY));
        }
        try{
            isValidMobile(user.getMobile());
            isValidSMS(user);
            isValidPwd(user);
            isvalidName(user);
        }catch (ValidateCodeException e){
            logger.warn("ValidateCodeException ",e);
            throw e;
        }
        return true;
    }

    private boolean isvalidName(User user){
        if(StringUtils.isBlank(user.getUserName())){
            throw new ValidateCodeException(JsonUtil.toJson(UserResponse.USERNAME_ISEMPTY));
        }
        return true;
    }

    /**
     * 校验密码是否合法
     * @param user
     * @return
     * @throws ValidateCodeException
     */
    private boolean isValidPwd(User user) throws ValidateCodeException{
		/*if(!REGEX_PASSWORD_STRONG.matches(user.getPassword())){
			throw new ValidateCodeException(JsonUtil.toJson(UserResponse.PASSWORD_RULE_NOTSATISFIED));
		}*/
        if(StringUtils.trim(user.getPassword()).length() < 8){
            throw new ValidateCodeException(JsonUtil.toJson(UserResponse.PASSWORD_RULE_NOTSATISFIED));
        }
        if(!user.getPassword().equals(user.getRepassword())){
            throw new ValidateCodeException(JsonUtil.toJson(UserResponse.PASSWORD_REPEAT));
        }
        return true;
    }

    /**
     * 校验电话号码
     * @param mobile
     * @return
     * @throws ValidateCodeException
     */
    private boolean isValidMobile(String mobile) throws ValidateCodeException{
        if(mobile !=null && mobile.length() == 13){
            return true;
        }
        throw  new ValidateCodeException(JsonUtil.toJson(UserResponse.INVALIDMOBILE));
    }

    /**
     * 校验短信验证码
     * @param user
     * @return
     * @throws ValidateCodeException
     */
    private boolean isValidSMS(User user) throws ValidateCodeException{
        String smsInRequest = user.getSmsCode();
        if (StringUtils.isBlank(smsInRequest)) {
            throw new ValidateCodeException(JsonUtil.toJson(SmsResponse.SMSISEMPTY));
        }
		//系统生成值
		ValidateCode codeInSession = (ValidateCode) SessionCache.getValueFromCache(user.getMobile());
		//验证码不存在
		if (codeInSession == null) {
			throw new ValidateCodeException(JsonUtil.toJson(SmsResponse.SMSNOTEXIST));
		}
		//验证码已经过期
		if (codeInSession.isExpired()) {
			SessionCache.removeFromCache(user.getMobile());
			throw new ValidateCodeException(JsonUtil.toJson(SmsResponse.SMSEXPIRED));
		}
		//验证码不匹配
		if (!StringUtils.equals(codeInSession.getCode(), smsInRequest)) {
			throw new ValidateCodeException(JsonUtil.toJson(SmsResponse.SMSUNCORRECT));
		}
        return true;
    }
}
