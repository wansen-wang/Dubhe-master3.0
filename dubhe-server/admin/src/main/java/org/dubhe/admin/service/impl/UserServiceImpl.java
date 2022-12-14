/**
 * Copyright 2020 Tianshu AI Platform. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =============================================================
 */
package org.dubhe.admin.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.RSA;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.dubhe.admin.client.AuthServiceClient;
import org.dubhe.admin.client.ResourceQuotaClient;
import org.dubhe.admin.dao.*;
import org.dubhe.admin.domain.dto.*;
import org.dubhe.admin.domain.entity.Role;
import org.dubhe.admin.domain.entity.User;
import org.dubhe.admin.domain.entity.UserAvatar;
import org.dubhe.admin.domain.entity.UserConfig;
import org.dubhe.admin.domain.entity.UserRole;
import org.dubhe.admin.domain.vo.EmailVo;
import org.dubhe.admin.domain.vo.UserConfigCreateVO;
import org.dubhe.admin.domain.vo.UserConfigVO;
import org.dubhe.admin.domain.vo.UserVO;
import org.dubhe.admin.enums.UserMailCodeEnum;
import org.dubhe.admin.event.EmailEventPublisher;
import org.dubhe.admin.service.UserService;
import org.dubhe.admin.service.convert.TeamConvert;
import org.dubhe.admin.service.convert.UserConvert;
import org.dubhe.biz.base.constant.AuthConst;
import org.dubhe.biz.base.constant.ResponseCode;
import org.dubhe.biz.base.constant.UserConstant;
import org.dubhe.biz.base.context.EncryptVisUser;
import org.dubhe.biz.base.context.UserContext;
import org.dubhe.biz.base.dto.*;
import org.dubhe.biz.base.enums.BaseErrorCodeEnum;
import org.dubhe.biz.base.enums.SwitchEnum;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.exception.CaptchaException;
import org.dubhe.biz.base.utils.DateUtil;
import org.dubhe.biz.base.utils.Md5Util;
import org.dubhe.biz.base.utils.RSAUtil;
import org.dubhe.biz.base.utils.RandomUtil;
import org.dubhe.biz.base.utils.RsaEncrypt;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.dataresponse.factory.DataResponseFactory;
import org.dubhe.biz.db.utils.PageUtil;
import org.dubhe.biz.db.utils.WrapperHelp;
import org.dubhe.biz.file.utils.DubheFileUtil;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.permission.annotation.DataPermissionMethod;
import org.dubhe.biz.redis.utils.RedisUtils;
import org.dubhe.cloud.authconfig.dto.JwtUserDTO;
import org.dubhe.cloud.authconfig.factory.PasswordEncoderFactory;
import org.dubhe.cloud.authconfig.utils.JwtUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cglib.beans.BeanMap;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @description Demo?????????????????????
 * @date 2020-11-26
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Value("${rsa.private_key}")
    private String privateKey;

    @Value("${initial_password}")
    private String initialPassword;

    @Value("${user.config.notebook-delay-delete-time}")
    private Integer defaultNotebookDelayDeleteTime;

    @Value("${user.config.cpu-limit}")
    private Integer cpuLimit;

    @Value("${user.config.memory-limit}")
    private Integer memoryLimit;

    @Value("${user.config.gpu-limit}")
    private Integer gpuLimit;

    @Value("${vis.public_key}")
    private String visPublicKey;

    @Autowired
    private UserMapper userMapper;


    @Resource
    private RoleMapper roleMapper;

    @Autowired
    private MenuMapper menuMapper;

    @Autowired
    private TeamMapper teamMapper;

    @Autowired
    private UserConvert userConvert;

    @Autowired
    private TeamConvert teamConvert;

    @Autowired
    private UserAvatarMapper userAvatarMapper;


    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private EmailEventPublisher publisher;

    @Autowired
    private AuthServiceClient authServiceClient;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private UserConfigMapper userConfigMapper;

    @Autowired
    ResourceQuotaClient resourceQuotaClient;


    /**
     * ???????????? true:??????debug false:??????debug
     */
    @Value("${debug.flag}")
    private Boolean debugFlag;

    private final String LOCK_SEND_CODE = "LOCK_SEND_CODE";

    /**
     * ????????????????????????
     *
     * @param criteria ????????????
     * @param page     ??????????????????
     * @return java.lang.Object ????????????????????????
     */
    @Override
    public Object queryAll(UserQueryDTO criteria, Page page) {
        if (criteria.getRoleId() == null) {
            IPage<User> users = userMapper.selectCollPage(page, WrapperHelp.getWrapper(criteria));
            return PageUtil.toPage(users, userConvert::toDto);
        } else {
            IPage<User> users = userMapper.selectCollPageByRoleId(page, WrapperHelp.getWrapper(criteria), criteria.getRoleId());
            return PageUtil.toPage(users, userConvert::toDto);
        }
    }

    /**
     * ??????????????????
     *
     * @param criteria ??????????????????
     * @return java.util.List<org.dubhe.domain.dto.UserDTO> ????????????????????????
     */
    @Override
    public List<UserDTO> queryAll(UserQueryDTO criteria) {
        List<User> users = userMapper.selectCollList(WrapperHelp.getWrapper(criteria));
        return userConvert.toDto(users);
    }

    /**
     * ????????????ID??????????????????
     *
     * @param userId ??????ID
     * @return java.util.List<org.dubhe.domain.dto.TeamDTO> ??????????????????
     */
    @Override
    public List<TeamDTO> queryTeams(Long userId) {

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getId, userId)
                        .eq(User::getDeleted, SwitchEnum.getBooleanValue(SwitchEnum.OFF.getValue()))
        );
        List teamList = teamMapper.findByUserId(user.getId());
        return teamConvert.toDto(teamList);
    }

    /**
     * ??????ID??????????????????
     *
     * @param id id
     * @return org.dubhe.domain.dto.UserDTO ????????????????????????
     */
    @Override
    public UserDTO findById(long id) {
        User user = userMapper.selectCollById(id);
        return userConvert.toDto(user);
    }


    /**
     * ????????????
     *
     * @param resources ??????????????????
     * @return org.dubhe.domain.dto.UserDTO ????????????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDTO create(UserCreateDTO resources) {
        PasswordEncoder passwordEncoder = PasswordEncoderFactory.getPasswordEncoder();
        if (!Objects.isNull(userMapper.findByUsername(resources.getUsername()))) {
            throw new BusinessException("??????????????????");
        }
        if (userMapper.findByEmail(resources.getEmail()) != null) {
            throw new BusinessException("???????????????");
        }
        resources.setPassword(passwordEncoder.encode(initialPassword));

        User user = User.builder().build();
        BeanUtils.copyProperties(resources, user);

        userMapper.insert(user);
        for (Role role : resources.getRoles()) {
            roleMapper.tiedUserRole(user.getId(), role.getId());
        }
        UserConfigDTO userConfigDTO = new UserConfigDTO();
        userConfigDTO.setUserId(user.getId());
        userConfigDTO.setCpuLimit(cpuLimit);
        userConfigDTO.setMemoryLimit(memoryLimit);
        userConfigDTO.setGpuLimit(gpuLimit);
        DataResponseBody dataResponseBody = resourceQuotaClient.updateResourceQuota(userConfigDTO);
        if (!dataResponseBody.succeed()){
            throw new BusinessException("????????????????????????");
        }
        return userConvert.toDto(user);
    }


    /**
     * ????????????
     *
     * @param resources ????????????????????????
     * @return org.dubhe.domain.dto.UserDTO ????????????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDTO update(UserUpdateDTO resources) {

        //???????????????????????????
        checkIsAdmin(resources.getId());

        User user = userMapper.selectCollById(resources.getId());
        User userTmp = userMapper.findByUsername(resources.getUsername());
        if (userTmp != null && !user.equals(userTmp)) {
            throw new BusinessException("??????????????????");
        }
        userTmp = userMapper.findByEmail(resources.getEmail());
        if (userTmp != null && !user.equals(userTmp)) {
            throw new BusinessException("???????????????");
        }
        roleMapper.untiedUserRoleByUserId(user.getId());
        for (Role role : resources.getRoles()) {
            roleMapper.tiedUserRole(user.getId(), role.getId());
        }
        user.setUsername(resources.getUsername());
        user.setEmail(resources.getEmail());
        user.setEnabled(resources.getEnabled());
        user.setRoles(resources.getRoles());
        user.setPhone(resources.getPhone());
        user.setNickName(resources.getNickName());
        user.setRemark(resources.getRemark());
        user.setSex(resources.getSex());
        userMapper.updateById(user);
        return userConvert.toDto(user);
    }


    /**
     * ????????????????????????
     *
     * @param ids ??????ID??????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Set<Long> ids) {
        if (!CollectionUtils.isEmpty(ids)) {
            Long adminId = Long.valueOf(UserConstant.ADMIN_USER_ID);
            if (ids.contains(adminId)) {
                throw new BusinessException(BaseErrorCodeEnum.SYSTEM_USER_CANNOT_DELETE);
            }
            ids.forEach(id -> {
                userMapper.updateById(
                        User.builder()
                                .id(id)
                                .deleted(SwitchEnum.getBooleanValue(SwitchEnum.ON.getValue()))
                                .build());
            });
        }
    }


    /**
     * ????????????????????????????????????
     *
     * @param userName ????????????
     * @return org.dubhe.domain.dto.UserDTO ????????????????????????
     */
    @Override
    public UserDTO findByName(String userName) {
        User user = userMapper.findByUsername(userName);
        if (user == null) {
            LogUtil.error(LogEnum.SYS_ERR, "UserServiceImpl findByName user is null");
            throw new BusinessException("user not found");
        }
        UserDTO dto = new UserDTO();
        BeanUtils.copyProperties(user, dto);
        List<Role> roles = roleMapper.findRolesByUserId(user.getId());
        if (!CollectionUtils.isEmpty(roles)) {
            dto.setRoles(roles.stream().map(a -> {
                SysRoleDTO sysRoleDTO = new SysRoleDTO();
                sysRoleDTO.setId(a.getId());
                sysRoleDTO.setName(a.getName());
                return sysRoleDTO;
            }).collect(Collectors.toList()));
        }
        //??????????????????
        SysUserConfigDTO sysUserConfigDTO = getUserConfig(user.getId());
        dto.setUserConfig(sysUserConfigDTO);
        return dto;

    }

    private SysUserConfigDTO getUserConfig(Long userId) {
        UserConfig userConfig = userConfigMapper.selectOne(new QueryWrapper<>(new UserConfig().setUserId(userId)));
        SysUserConfigDTO sysUserConfigDTO= new SysUserConfigDTO();
        if (userConfig == null){
            return sysUserConfigDTO.setCpuLimit(cpuLimit).setMemoryLimit(memoryLimit)
                        .setGpuLimit(gpuLimit).setNotebookDelayDeleteTime(defaultNotebookDelayDeleteTime);
        }
        BeanUtils.copyProperties(userConfig, sysUserConfigDTO);
        return sysUserConfigDTO;
    }


    /**
     * ??????????????????????????????
     *
     * @param resources ????????????????????????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCenter(UserCenterUpdateDTO resources) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getId, resources.getId())
                        .eq(User::getDeleted, SwitchEnum.getBooleanValue(SwitchEnum.OFF.getValue()))
        );
        user.setNickName(resources.getNickName());
        user.setRemark(resources.getRemark());
        user.setPhone(resources.getPhone());
        user.setSex(resources.getSex());
        userMapper.updateById(user);
        if (user.getUserAvatar() != null) {
            if (user.getUserAvatar().getId() != null) {
                userAvatarMapper.updateById(user.getUserAvatar());
            } else {
                userAvatarMapper.insert(user.getUserAvatar());
            }
        }
    }


    /**
     * ??????????????????
     *
     * @param username ??????
     * @param pass     ??????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePass(String username, String pass) {
        userMapper.updatePass(username, pass, new Date());
    }


    /**
     * ??????????????????
     *
     * @param realName ??????
     * @param path     ????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAvatar(String realName, String path) {
        User user = userMapper.findByUsername(JwtUtils.getCurUser().getUsername());
        UserAvatar userAvatar = user.getUserAvatar();
        UserAvatar newAvatar = new UserAvatar(userAvatar, realName, path, null);

        if (newAvatar.getId() != null) {
            userAvatarMapper.updateById(newAvatar);
        } else {
            userAvatarMapper.insert(newAvatar);
        }
        user.setAvatarId(newAvatar.getId());
        user.setUserAvatar(newAvatar);
        userMapper.updateById(user);
    }

    /**
     * ??????????????????
     *
     * @param username ????????????
     * @param email    ????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEmail(String username, String email) {
        userMapper.updateEmail(username, email);
    }


    /**
     * ??????????????????
     *
     * @param queryAll ??????????????????
     * @param response ??????http??????
     */
    @Override
    public void download(List<UserDTO> queryAll, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (UserDTO userDTO : queryAll) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("?????????", userDTO.getUsername());
            map.put("??????", userDTO.getEmail());
            map.put("??????", userDTO.getEnabled() ? "??????" : "??????");
            map.put("????????????", userDTO.getPhone());
            map.put("???????????????????????????", userDTO.getLastPasswordResetTime());
            map.put("????????????", userDTO.getCreateTime());
            list.add(map);
        }
        DubheFileUtil.downloadExcel(list, response);
    }


    /**
     * ????????????ID??????
     *
     * @param id ??????ID
     * @return java.util.Set<java.lang.String> ????????????
     */
    @Override
    public Set<String> queryPermissionByUserId(Long id) {
        return userMapper.queryPermissionByUserId(id);
    }

    /**
     * ??????????????????
     *
     * @param userRegisterDTO ????????????????????????
     * @return org.dubhe.base.DataResponseBody ?????????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataResponseBody userRegister(UserRegisterDTO userRegisterDTO) {
        PasswordEncoder passwordEncoder = PasswordEncoderFactory.getPasswordEncoder();
        //??????????????????
        checkoutUserInfo(userRegisterDTO);
        String encode = passwordEncoder.encode(RsaEncrypt.decrypt(userRegisterDTO.getPassword(), privateKey));
        try {
            User newUser = User.builder()
                    .email(userRegisterDTO.getEmail())
                    .enabled(true)
                    .nickName(userRegisterDTO.getNickName())
                    .password(encode)
                    .phone(userRegisterDTO.getPhone())
                    .sex(SwitchEnum.ON.getValue().compareTo(userRegisterDTO.getSex()) == 0 ? UserConstant.SEX_MEN : UserConstant.SEX_WOMEN)
                    .username(userRegisterDTO.getUsername()).build();

            //????????????????????????
            userMapper.insert(newUser);

            //????????????????????????
            userRoleMapper.insert(UserRole.builder().roleId((long) UserConstant.REGISTER_ROLE_ID).userId(newUser.getId()).build());

        } catch (Exception e) {
            LogUtil.error(LogEnum.SYS_ERR, "UserServiceImpl userRegister error , param:{} error:{}", JSONObject.toJSONString(userRegisterDTO), e);
            throw new BusinessException(BaseErrorCodeEnum.ERROR_SYSTEM.getCode(), BaseErrorCodeEnum.ERROR_SYSTEM.getMsg());
        }

        return new DataResponseBody();
    }


    /**
     * ??????code??????????????????
     *
     * @param userRegisterMailDTO ??????????????????????????????
     * @return org.dubhe.base.DataResponseBody ???????????????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataResponseBody getCodeBySentEmail(UserRegisterMailDTO userRegisterMailDTO) {
        String email = userRegisterMailDTO.getEmail();

        User dbUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
                .eq(User::getDeleted, SwitchEnum.getBooleanValue(SwitchEnum.OFF.getValue()))
        );
        //???????????????????????????type : 1 ???????????? 2 ???????????? ???
        Boolean isRegisterOrUpdate = UserMailCodeEnum.REGISTER_CODE.getValue().compareTo(userRegisterMailDTO.getType()) == 0 ||
                UserMailCodeEnum.MAIL_UPDATE_CODE.getValue().compareTo(userRegisterMailDTO.getType()) == 0;
        if (!Objects.isNull(dbUser) && isRegisterOrUpdate) {
            LogUtil.error(LogEnum.SYS_ERR, "UserServiceImpl dbUser already register , dbUser:{} ", JSONObject.toJSONString(dbUser));
            throw new BusinessException(BaseErrorCodeEnum.SYSTEM_USER_EMAIL_ALREADY_EXISTS.getCode(),
                    BaseErrorCodeEnum.SYSTEM_USER_EMAIL_ALREADY_EXISTS.getMsg());
        }


        //????????????????????????
        limitSendEmail(email);

        try {
            synchronized (LOCK_SEND_CODE) {
                //????????????????????????
                String code = RandomUtil.randomCode();
                //??????????????????
                publisher.sentEmailEvent(
                        EmailDTO.builder()
                                .code(code)
                                .subject(UserMailCodeEnum.getEnumValue(userRegisterMailDTO.getType()).getDesc())
                                .type(userRegisterMailDTO.getType())
                                .receiverMailAddress(email).build());
                //redis????????????????????????
                redisUtils.hset(
                        getSendEmailCodeRedisKeyByType(userRegisterMailDTO.getType()).concat(email),
                        email,
                        EmailVo.builder().code(code).email(email).build(),
                        UserConstant.DATE_SECOND);
            }

        } catch (Exception e) {
            redisUtils.hdel(UserConstant.USER_EMAIL_REGISTER.concat(email), email);
            LogUtil.error(LogEnum.SYS_ERR, "UserServiceImpl getCodeBySentEmail error , param:{} error:{}", email, e);
            throw new BusinessException(BaseErrorCodeEnum.ERROR_SYSTEM.getCode(), BaseErrorCodeEnum.ERROR_SYSTEM.getMsg());
        }
        return new DataResponseBody();
    }


    /**
     * ????????????
     *
     * @param userEmailUpdateDTO ????????????????????????
     * @return org.dubhe.base.DataResponseBody ???????????????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataResponseBody resetEmail(UserEmailUpdateDTO userEmailUpdateDTO) {
        //??????????????????
        User dbUser = checkoutEmailInfoByReset(userEmailUpdateDTO);

        try {
            //??????????????????
            userMapper.updateById(
                    User.builder()
                            .id(dbUser.getId())
                            .email(userEmailUpdateDTO.getEmail()).build());
        } catch (Exception e) {
            LogUtil.error(LogEnum.SYS_ERR, "UserServiceImpl update email error , email:{} error:{}", userEmailUpdateDTO.getEmail(), e);
            throw new BusinessException(BaseErrorCodeEnum.ERROR_SYSTEM.getCode(),
                    BaseErrorCodeEnum.ERROR_SYSTEM.getMsg());
        }

        return new DataResponseBody();
    }


    /**
     * ??????????????????
     *
     * @return java.util.Map<java.lang.String, java.lang.Object> ?????????????????????
     */
    @Override
    public Map<String, Object> userinfo() {
        JwtUserDTO curUser = JwtUtils.getCurUser();
        if (Objects.isNull(curUser)) {
            throw new BusinessException(BaseErrorCodeEnum.SYSTEM_USER_IS_NOT_EXISTS.getCode()
                    , BaseErrorCodeEnum.SYSTEM_USER_IS_NOT_EXISTS.getMsg());
        }

        //??????????????????????????????
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, curUser.getCurUserId())
                        .eq(UserRole::getRoleId, Long.parseLong(String.valueOf(UserConstant.ADMIN_ROLE_ID)))
        );
        UserVO vo = UserVO.builder()
                .email(curUser.getUser().getEmail())
                .password(Md5Util.createMd5(Md5Util.createMd5(curUser.getUsername()).concat(initialPassword)))
                .username(curUser.getUsername())
                .is_staff(!CollectionUtils.isEmpty(userRoles) ? true : false).build();

        return BeanMap.create(vo);
    }


    /**
     * ??????????????????
     *
     * @param userResetPasswordDTO ????????????????????????
     * @return org.dubhe.base.DataResponseBody ?????????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataResponseBody resetPassword(UserResetPasswordDTO userResetPasswordDTO) {
        PasswordEncoder passwordEncoder = PasswordEncoderFactory.getPasswordEncoder();
        //?????? ???????????? ??? ?????????
        checkoutEmailAndCode(userResetPasswordDTO.getCode(), userResetPasswordDTO.getEmail(), UserConstant.USER_EMAIL_RESET_PASSWORD);

        User dbUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, userResetPasswordDTO.getEmail())
                .eq(User::getDeleted, SwitchEnum.getBooleanValue(SwitchEnum.OFF.getValue()))
        );
        if (Objects.isNull(dbUser)) {
            throw new BusinessException(BaseErrorCodeEnum.SYSTEM_USER_EMAIL_NOT_EXISTS.getCode()
                    , BaseErrorCodeEnum.SYSTEM_USER_EMAIL_NOT_EXISTS.getMsg());
        }

        //????????????
        String encode = passwordEncoder.encode(RsaEncrypt.decrypt(userResetPasswordDTO.getPassword(), privateKey));
        try {
            userMapper.updateById(User.builder().id(dbUser.getId()).password(encode).build());
        } catch (Exception e) {
            throw new BusinessException(BaseErrorCodeEnum.ERROR_SYSTEM.getCode()
                    , BaseErrorCodeEnum.ERROR_SYSTEM.getMsg());
        }
        return new DataResponseBody();
    }


    /**
     * ??????
     *
     * @param authUserDTO ??????????????????
     */
    @Override
    @DataPermissionMethod
    public DataResponseBody<Map<String, Object>> login(AuthUserDTO authUserDTO) {
        if (!debugFlag) {
            validateCode(authUserDTO.getCode(), authUserDTO.getUuid());
        }
        String password = null;
        try {
            RSA rsa = new RSA(privateKey, null);
            password = new String(rsa.decrypt(authUserDTO.getPassword(), KeyType.PrivateKey));
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_SYS, "rsa ??????????????????, originPassword:{} , ??????:{}????????????{}", authUserDTO.getPassword(), KeyType.PrivateKey, e);
            throw new BusinessException("?????????????????????");
        }

        Map<String, String> params = new HashMap<>();
        params.put("grant_type", "password");
        params.put("username", authUserDTO.getUsername());
        params.put("client_id", AuthConst.CLIENT_ID);
        params.put("client_secret", AuthConst.CLIENT_SECRET);
        params.put("password", password);
        params.put("scope", "all");
        DataResponseBody<Oauth2TokenDTO> restResult = authServiceClient.postAccessToken(params);
        Map<String, Object> authInfo = new HashMap<>(3);
        if (ResponseCode.SUCCESS.compareTo(restResult.getCode()) == 0 && !Objects.isNull(restResult.getData())) {
            Oauth2TokenDTO userDto = restResult.getData();
            UserDTO user = findByName(authUserDTO.getUsername());
            Set<String> permissions = this.queryPermissionByUserId(user.getId());
            if (CollUtil.isEmpty(permissions)) {
                throw new BusinessException(BaseErrorCodeEnum.SYSTEM_ROLE_NOT_EXISTS);
            }
            // ?????? token ??? ????????????
            authInfo.put("token", userDto.getTokenHead() + userDto.getToken());
            authInfo.put("user", user);
            authInfo.put("permissions", permissions);
        }
        return DataResponseFactory.success(authInfo);
    }


    /**
     * ????????????
     *
     * @param accessToken token
     */
    @Override
    public DataResponseBody logout(String accessToken) {
        return authServiceClient.logout(accessToken);
    }

    /**
     * ????????????????????????????????????
     *
     * @param nickName ????????????
     * @return org.dubhe.domain.dto.UserDTO ????????????DTO
     */
    @Override
    public List<UserDTO> findByNickName(String nickName) {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .like(User::getNickName, nickName == null ? StrUtil.EMPTY : nickName));

        return userConvert.toDto(users);
    }

    /**
     * ????????????id????????????????????????
     *
     * @param ids ??????id??????
     * @return org.dubhe.domain.dto.UserDTO ????????????DTO??????
     */
    @Override
    public List<UserDTO> getUserList(List<Long> ids) {
        List<User> users = userMapper.selectBatchIds(ids);
        return userConvert.toDto(users);
    }

    /**
     * ???????????? ID ??????????????????
     *
     * @param userId ?????? ID
     * @return org.dubhe.admin.domain.vo.UserConfigVO ???????????? VO
     */
    @Override
    public UserConfigVO findUserConfig(Long userId) {
        // ??????????????????
        UserConfig userConfig = userConfigMapper.selectOne(new QueryWrapper<>(new UserConfig().setUserId(userId)));
        UserConfigVO userConfigVO = new UserConfigVO();
        // ????????????????????????????????????
        if (userConfig == null){
            return userConfigVO.setUserId(userId).setCpuLimit(cpuLimit).setMemoryLimit(memoryLimit)
                    .setGpuLimit(gpuLimit).setNotebookDelayDeleteTime(defaultNotebookDelayDeleteTime);
        }
       // ?????????????????? VO
        BeanUtils.copyProperties(userConfig, userConfigVO);
        return userConfigVO;
    }

    /**
     * ???????????????????????????
     *
     * @param userConfigDTO ????????????
     * @return org.dubhe.admin.domain.vo.UserConfigCreateVO ???????????? VO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserConfigCreateVO createOrUpdateUserConfig(UserConfigDTO userConfigDTO) {
        DataResponseBody dataResponseBody = resourceQuotaClient.updateResourceQuota(userConfigDTO);
        if (!dataResponseBody.succeed()){
            throw new BusinessException("????????????????????????");
        }
        UserConfig userConfig = new UserConfig();
        BeanUtils.copyProperties(userConfigDTO, userConfig);
        userConfigMapper.insertOrUpdate(userConfig);
        // ?????????????????? VO
        UserConfigCreateVO userConfigCreateVO = new UserConfigCreateVO().setId(userConfig.getId());
        return userConfigCreateVO;
    }

    @Override
    public String encryptUserForVis(Authentication authentication) {
        String encodeStr = "";
        try {
            JwtUserDTO jwtUser = (JwtUserDTO) authentication.getPrincipal();
            EncryptVisUser visUser = new EncryptVisUser();
            BeanUtils.copyProperties(jwtUser.getUser(), visUser);
            encodeStr = RSAUtil.publicEncrypt(JSONObject.toJSONString(visUser), RSAUtil.getPublicKey(visPublicKey));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        }
        return encodeStr;
    }


    /**
     * ???????????????
     *
     * @param loginCaptcha  ???????????????
     * @param uuid          ?????????redis-key
     */
    private void validateCode(String loginCaptcha, String uuid) {
        // ??????????????????
        if (loginCaptcha == null || "".equals(loginCaptcha)) {
            throw new CaptchaException("???????????????");
        }
        String sessionCaptcha = (String) redisUtils.get(uuid);

        if (!loginCaptcha.equalsIgnoreCase(sessionCaptcha)) {
            throw new CaptchaException("???????????????");
        }

    }

    /**
     * ??????????????????????????????
     *
     * @param userEmailUpdateDTO ??????????????????????????????
     */
    private User checkoutEmailInfoByReset(UserEmailUpdateDTO userEmailUpdateDTO) {
        PasswordEncoder passwordEncoder = PasswordEncoderFactory.getPasswordEncoder();
        String email = userEmailUpdateDTO.getEmail();
        //?????????????????????
        checkIsAdmin(userEmailUpdateDTO.getUserId());

        //??????????????????????????????
        User dbUser = userMapper.selectCollById(userEmailUpdateDTO.getUserId());
        if (ObjectUtil.isNull(dbUser)) {
            LogUtil.error(LogEnum.SYS_ERR, "UserServiceImpl dbUser is null , userId:{}", userEmailUpdateDTO.getUserId());
            throw new BusinessException(BaseErrorCodeEnum.SYSTEM_USER_IS_NOT_EXISTS.getCode(),
                    BaseErrorCodeEnum.SYSTEM_USER_IS_NOT_EXISTS.getMsg());
        }
        //????????????????????????
        String decryptPassword = RsaEncrypt.decrypt(userEmailUpdateDTO.getPassword(), privateKey);
        if (!passwordEncoder.matches(decryptPassword, dbUser.getPassword())) {
            LogUtil.error(LogEnum.SYS_ERR, "UserServiceImpl password error , webPassword:{}, dbPassword:{} ",
                    userEmailUpdateDTO.getPassword(), dbUser.getPassword());
            throw new BusinessException(BaseErrorCodeEnum.SYSTEM_USER_EMAIL_PASSWORD_ERROR.getCode(),
                    BaseErrorCodeEnum.SYSTEM_USER_EMAIL_PASSWORD_ERROR.getMsg());
        }

        //?????????????????????
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, userEmailUpdateDTO.getEmail()));
        if (!ObjectUtil.isNull(user)) {
            LogUtil.error(LogEnum.SYS_ERR, "UserServiceImpl Email already exists , email:{} ", userEmailUpdateDTO.getEmail());
            throw new BusinessException(BaseErrorCodeEnum.SYSTEM_USER_EMAIL_ALREADY_EXISTS.getCode(),
                    BaseErrorCodeEnum.SYSTEM_USER_EMAIL_ALREADY_EXISTS.getMsg());
        }

        //?????? ???????????? ??? ?????????
        checkoutEmailAndCode(userEmailUpdateDTO.getCode(), email, UserConstant.USER_EMAIL_UPDATE);

        return dbUser;
    }


    /**
     * ??????????????????
     *
     * @param receiverMailAddress ?????????????????????
     */
    private void limitSendEmail(final String receiverMailAddress) {
        double count = redisUtils.hincr(UserConstant.USER_EMAIL_LIMIT_COUNT.concat(receiverMailAddress), receiverMailAddress, 1);
        if (count > UserConstant.COUNT_SENT_EMAIL) {
            LogUtil.error(LogEnum.SYS_ERR, "Email verification code cannot exceed three times , error:{}", UserConstant.COUNT_SENT_EMAIL);
            throw new BusinessException(BaseErrorCodeEnum.SYSTEM_USER_EMAIL_CODE_CANNOT_EXCEED_TIMES.getCode(),
                    BaseErrorCodeEnum.SYSTEM_USER_EMAIL_CODE_CANNOT_EXCEED_TIMES.getMsg());
        } else {
            // ???????????????????????????
            String concat = UserConstant.USER_EMAIL_LIMIT_COUNT.concat(receiverMailAddress);

            Long secondsNextEarlyMorning = DateUtil.getSecondTime();

            redisUtils.expire(concat, secondsNextEarlyMorning);
        }
    }


    /**
     * ??????????????????
     *
     * @param userRegisterDTO ????????????????????????
     */
    private void checkoutUserInfo(UserRegisterDTO userRegisterDTO) {
        //?????????????????????
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, userRegisterDTO.getUsername()));
        if (!ObjectUtil.isNull(user)) {
            LogUtil.error(LogEnum.SYS_ERR, "UserServiceImpl username already exists , username:{} ", userRegisterDTO.getUsername());
            throw new BusinessException(BaseErrorCodeEnum.SYSTEM_USERNAME_ALREADY_EXISTS.getCode(),
                    BaseErrorCodeEnum.SYSTEM_USERNAME_ALREADY_EXISTS.getMsg());
        }
        //?????? ???????????? ??? ?????????
        checkoutEmailAndCode(userRegisterDTO.getCode(), userRegisterDTO.getEmail(), UserConstant.USER_EMAIL_REGISTER);
    }

    /**
     * ?????? ???????????? ??? ?????????
     *
     * @param code ?????????
     * @param email ??????
     * @param codeRedisKey redis-key
     */
    private void checkoutEmailAndCode(String code, String email, String codeRedisKey) {
        //???????????????????????????
        Object emailVoObj = redisUtils.hget(codeRedisKey.concat(email), email);
        if (Objects.isNull(emailVoObj)) {
            LogUtil.error(LogEnum.SYS_ERR, "UserServiceImpl emailVo already expired , email:{} ", email);
            throw new BusinessException(BaseErrorCodeEnum.SYSTEM_USER_REGISTER_EMAIL_INFO_EXPIRED.getCode(),
                    BaseErrorCodeEnum.SYSTEM_USER_REGISTER_EMAIL_INFO_EXPIRED.getMsg());
        }

        //????????????????????????
        EmailVo emailVo = (EmailVo) emailVoObj;
        if (!email.equals(emailVo.getEmail()) || !code.equals(emailVo.getCode())) {
            LogUtil.error(LogEnum.SYS_ERR, "UserServiceImpl email or code error , email:{} code:{}", email, code);
            throw new BusinessException(BaseErrorCodeEnum.SYSTEM_USER_EMAIL_OR_CODE_ERROR.getCode(),
                    BaseErrorCodeEnum.SYSTEM_USER_EMAIL_OR_CODE_ERROR.getMsg());
        }
    }


    /**
     * ?????? ????????????code ??? redis key
     *
     * @param type ??????????????????
     */
    private String getSendEmailCodeRedisKeyByType(Integer type) {
        String typeKey = null;
        if (UserMailCodeEnum.REGISTER_CODE.getValue().compareTo(type) == 0) {
            typeKey = UserConstant.USER_EMAIL_REGISTER;
        } else if (UserMailCodeEnum.MAIL_UPDATE_CODE.getValue().compareTo(type) == 0) {
            typeKey = UserConstant.USER_EMAIL_UPDATE;
        } else if (UserMailCodeEnum.FORGET_PASSWORD.getValue().compareTo(type) == 0) {
            typeKey = UserConstant.USER_EMAIL_RESET_PASSWORD;
        } else {
            typeKey = UserConstant.USER_EMAIL_OTHER;
        }
        return typeKey;
    }


    /**
     * ???????????????????????????
     *
     * @param userId ??????ID
     */
    private void checkIsAdmin(Long userId) {
        //???????????????????????????
        if (UserConstant.ADMIN_USER_ID == userId.intValue() &&
                UserConstant.ADMIN_USER_ID != JwtUtils.getCurUserId().intValue()) {
            throw new BusinessException(BaseErrorCodeEnum.SYSTEM_USER_CANNOT_UPDATE_ADMIN);
        }
    }


    /**
     * ?????????????????????????????????
     *
     * @param username ????????????
     * @return ????????????
     */
    @Override
    public DataResponseBody<UserContext> findUserByUsername(String username) {
        User user = userMapper.findByUsername(username);
        if (Objects.isNull(user)) {
            LogUtil.error(LogEnum.SYS_ERR, "UserServiceImpl findUserByUsername user is null {}");
            throw new BusinessException("?????????????????????!");
        }
        UserContext dto = new UserContext();
        BeanUtils.copyProperties(user, dto);
        if (user.getUserAvatar() != null && user.getUserAvatar().getPath() != null) {
            dto.setUserAvatarPath(user.getUserAvatar().getPath());
        }
        List<Role> roles = roleMapper.selectRoleByUserId(user.getId());
        if (!CollectionUtils.isEmpty(roles)) {

            List<Long> roleIds = roles.stream().map(a -> a.getId()).collect(Collectors.toList());
            //??????????????????
            List<SysPermissionDTO> permissions = menuMapper.selectPermissionByRoleIds(roleIds);
            //??????????????????
            List<SysPermissionDTO> authList = permissionMapper.selectPermissinByRoleIds(roleIds);
            permissions.addAll(authList);
            Map<Long, List<SysPermissionDTO>> permissionMap = new HashMap<>(permissions.size());
            if (!CollectionUtils.isEmpty(permissions)) {
                permissionMap = permissions.stream().collect(Collectors.groupingBy(SysPermissionDTO::getRoleId));
            }

            Map<Long, List<SysPermissionDTO>> finalPermissionMap = permissionMap;
            List<SysRoleDTO> roleDTOS = roles.stream().map(a -> {
                SysRoleDTO sysRoleDTO = new SysRoleDTO();
                BeanUtils.copyProperties(a, sysRoleDTO);
                List<SysPermissionDTO> sysPermissionDTOS = finalPermissionMap.get(a.getId());
                sysRoleDTO.setPermissions(sysPermissionDTOS);
                return sysRoleDTO;
            }).collect(Collectors.toList());
            dto.setRoles(roleDTOS);
        }
        //??????????????????
        SysUserConfigDTO sysUserConfigDTO = getUserConfig(user.getId());
        dto.setUserConfig(sysUserConfigDTO);
        return DataResponseFactory.success(dto);
    }
}
