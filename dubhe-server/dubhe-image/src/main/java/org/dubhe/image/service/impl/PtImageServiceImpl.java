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

package org.dubhe.image.service.impl;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.compress.utils.Lists;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.base.constant.NumberConstant;
import org.dubhe.biz.base.constant.ResponseCode;
import org.dubhe.biz.base.constant.StringConstant;
import org.dubhe.biz.base.context.DataContext;
import org.dubhe.biz.base.context.UserContext;
import org.dubhe.biz.base.enums.DatasetTypeEnum;
import org.dubhe.biz.base.enums.ImageSourceEnum;
import org.dubhe.biz.base.enums.ImageTypeEnum;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.service.UserContextService;
import org.dubhe.biz.base.utils.ReflectionUtils;
import org.dubhe.biz.base.utils.StringUtils;
import org.dubhe.biz.base.vo.PtImageVO;
import org.dubhe.biz.db.utils.PageUtil;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.permission.annotation.DataPermissionMethod;
import org.dubhe.biz.permission.base.BaseService;
import org.dubhe.image.dao.PtImageMapper;
import org.dubhe.image.domain.dto.PtImageDeleteDTO;
import org.dubhe.image.domain.dto.PtImageQueryDTO;
import org.dubhe.image.domain.dto.PtImageQueryImageDTO;
import org.dubhe.image.domain.dto.PtImageQueryNameDTO;
import org.dubhe.image.domain.dto.PtImageQueryUrlDTO;
import org.dubhe.image.domain.dto.PtImageSaveDTO;
import org.dubhe.image.domain.dto.PtImageUpdateDTO;
import org.dubhe.image.domain.entity.PtImage;
import org.dubhe.image.domain.vo.PtImageQueryVO;
import org.dubhe.image.service.PtImageService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * @description 镜像服务实现类
 * @date 2020-06-22
 */
@Service
public class PtImageServiceImpl implements PtImageService {

    @Autowired
    private PtImageMapper ptImageMapper;

    @Autowired
    private UserContextService userContextService;


    public final static List<String> FIELD_NAMES;

    static {
        FIELD_NAMES = ReflectionUtils.getFieldNames(PtImageQueryVO.class);
    }

    /**
     * 查询镜像
     *
     * @param ptImageQueryDTO 查询镜像条件
     * @return Map<String, Object>  返回镜像分页数据
     **/
    @Override
    //@DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public Map<String, Object> getImage(PtImageQueryDTO ptImageQueryDTO) {

        //从会话中获取用户信息
        UserContext user = userContextService.getCurUser();
        Page page = ptImageQueryDTO.toPage();

        QueryWrapper<PtImage> query = new QueryWrapper<>();
        query.eq("deleted", NumberConstant.NUMBER_0);

        if (ptImageQueryDTO.getImageResource() != null) {
            query.eq("image_resource", ptImageQueryDTO.getImageResource());
        }
        if (!BaseService.isAdmin(user)) {

            query.in("origin_user_id", user.getId(), 0L);
        }

        if (StringUtils.isNotEmpty(ptImageQueryDTO.getImageNameOrId())) {
            query.and(x -> x.eq("id", ptImageQueryDTO.getImageNameOrId()).or().like("image_name", ptImageQueryDTO.getImageNameOrId()));
        }


        //排序
        IPage<PtImage> ptImages;
        try {
            if (ptImageQueryDTO.getSort() != null && FIELD_NAMES.contains(ptImageQueryDTO.getSort())) {
                if (StringConstant.SORT_ASC.equalsIgnoreCase(ptImageQueryDTO.getOrder())) {
                    query.orderByAsc(StringUtils.humpToLine(ptImageQueryDTO.getSort()));
                } else {
                    query.orderByDesc(StringUtils.humpToLine(ptImageQueryDTO.getSort()));
                }
            } else {
                query.orderByDesc(StringConstant.ID);
            }
            if (CollectionUtil.isNotEmpty(ptImageQueryDTO.getImageTypes())) {
                ptImages = ptImageMapper.getImagesPageByImageTypes(page, query, ptImageQueryDTO.getImageTypes());
            } else {
                ptImages = ptImageMapper.getImagesPage(page, query);
            }
        } catch (Exception e) {
            LogUtil.error(LogEnum.IMAGE, "User {} query image list failed，exception {}", user.getId(), e);
            throw new BusinessException("查询镜像列表展示异常");
        }
        List<PtImageQueryVO> ptImageQueryResult = ptImages.getRecords().stream().map(x -> {
            PtImageQueryVO ptImageQueryVO = new PtImageQueryVO();
            BeanUtils.copyProperties(x, ptImageQueryVO);
            return ptImageQueryVO;
        }).collect(Collectors.toList());
        DataContext.remove();
        return PageUtil.toPage(page, ptImageQueryResult);
    }

    /**
     * 保存镜像信息
     *
     * @param ptImageSaveDTO 镜像信息DTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveImageInfo(PtImageSaveDTO ptImageSaveDTO) {
        UserContext user = userContextService.getCurUser();

        validImageInfo(ptImageSaveDTO.getImageUrl(), ptImageSaveDTO.getImageName(), ptImageSaveDTO.getImageTag());

        //普通用户不支持预置镜像信息的保存
        if (ImageSourceEnum.PRE.getCode().equals(ptImageSaveDTO.getImageResource()) &&
                !BaseService.isAdmin(user)) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED, "普通用户不支持保存预置镜像!");
        }

        //校验用户自定义镜像不能和预置镜像重名
        List<PtImage> resList = checkSaveImage(ptImageSaveDTO, null, ImageSourceEnum.PRE.getCode());
        if (CollUtil.isNotEmpty(resList)) {
            throw new BusinessException(ResponseCode.BADREQUEST, "不允许和预置镜像信息重复！");
        }

        //同一用户上传镜像的(userId+imageName+imageTag)存在的情况下是不能重复上传的
        List<PtImage> imageList = checkSaveImage(ptImageSaveDTO, user, ImageSourceEnum.MINE.getCode());
        if (CollUtil.isNotEmpty(imageList)) {
            throw new BusinessException(ResponseCode.BADREQUEST, "镜像信息已存在!");
        }


        //存储镜像信息
        PtImage ptImage = new PtImage();
        ptImage.setImageName(ptImageSaveDTO.getImageName())
                .setImageUrl(ptImageSaveDTO.getImageUrl())
                .setImageResource(ptImageSaveDTO.getImageResource())
                .setRemark(ptImageSaveDTO.getRemark())
                .setImageTag(ptImageSaveDTO.getImageTag())
                .setCreateUserId(user.getId());
        if (ImageSourceEnum.PRE.getCode().equals(ptImageSaveDTO.getImageResource())) {
            ptImage.setOriginUserId(MagicNumConstant.ZERO_LONG);
        } else {
            ptImage.setOriginUserId(user.getId());
        }

        ptImageMapper.insert(ptImage);

        for (Integer imageType : ptImageSaveDTO.getImageTypes()) {
            ptImageMapper.insertImageType(ptImage.getId(), imageType);
        }
    }

    /**
     * 获取镜像信息
     *
     * @param ptImageQueryImageDTO 查询条件
     * @return List<String>  通过imageName查询所含镜像版本信息
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public List<PtImage> searchImages(PtImageQueryImageDTO ptImageQueryImageDTO) {
        LambdaQueryWrapper<PtImage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PtImage::getImageName, ptImageQueryImageDTO.getImageName())
                .eq(PtImage::getDeleted, NumberConstant.NUMBER_0);
        List<PtImage> ptImages;
        if (ptImageQueryImageDTO.getImageResource() != null) {
            queryWrapper.eq(PtImage::getImageResource, ptImageQueryImageDTO.getImageResource());
        }
        if (CollectionUtil.isEmpty(ptImageQueryImageDTO.getImageTypes())) {
            ptImages = ptImageMapper.selectList(queryWrapper);
        } else {
            ptImages = ptImageMapper.getImagesByTypes(queryWrapper, ptImageQueryImageDTO.getImageTypes());
        }

        if (CollUtil.isEmpty(ptImages)) {
            throw new BusinessException(ResponseCode.BADREQUEST, "未查询到镜像信息!");
        }

        ptImages = ptImages.stream().collect(Collectors.collectingAndThen(Collectors.toCollection(() ->
                new TreeSet<>(Comparator.comparing(PtImage::getImageTag))), ArrayList::new));
        return ptImages;
    }


    /**
     * 删除镜像
     *
     * @param imageDeleteDTO 删除镜像条件参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public void deleteTrainImage(PtImageDeleteDTO imageDeleteDTO) {
        List<PtImage> imageList = ptImageMapper.selectList(new LambdaQueryWrapper<PtImage>()
                .in(PtImage::getId, imageDeleteDTO.getIds()));

        //删除本地镜像
        imageList.forEach(image -> {
            ptImageMapper.deleteById(image.getId());
        });
    }

    /**
     * 修改镜像信息
     *
     * @param imageUpdateDTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public void updateTrainImage(PtImageUpdateDTO imageUpdateDTO) {

        UserContext curUser = userContextService.getCurUser();
        PtImage image = ptImageMapper.selectById(imageUpdateDTO.getId());

        if (image == null) {
            throw new BusinessException("镜像不存在！");
        }

        validImageInfo(imageUpdateDTO.getImageUrl(), imageUpdateDTO.getImageName(), imageUpdateDTO.getImageTag());

        //非管理员禁止修改预置镜像
        if (ImageSourceEnum.PRE.getCode().equals(image.getImageResource()) && !BaseService.isAdmin(curUser)) {
            throw new BusinessException("非管理员无权限修改预置镜像信息");
        }
        image.setImageTypes(imageUpdateDTO.getImageTypes())
                .setImageName(imageUpdateDTO.getImageName())
                .setImageUrl(imageUpdateDTO.getImageUrl())
                .setImageTag(imageUpdateDTO.getImageTag())
                .setRemark(imageUpdateDTO.getRemark());
        ptImageMapper.updateById(image);
        List<Integer> imageTypes = ptImageMapper.selectImageType(image.getId());
        for (Integer imageType : imageUpdateDTO.getImageTypes()) {
            if (!CollectionUtil.contains(imageTypes, imageType)) {
                ptImageMapper.insertImageType(image.getId(), imageType);
            }
        }
        for (Integer imageType : imageTypes) {
            if (!CollectionUtil.contains(imageUpdateDTO.getImageTypes(), imageType)) {
                ptImageMapper.deleteImageType(image.getId(), imageType);
            }
        }
    }

    /**
     * 获取镜像名称列表
     *
     * @param ptImageQueryNameDTO 获取镜像名称列表查询条件
     * @return Set<String> 镜像列表
     */
    @Override
    public Set<String> getImageNameList(PtImageQueryNameDTO ptImageQueryNameDTO) {
        //从会话中获取用户信息
        UserContext user = userContextService.getCurUser();
        QueryWrapper<PtImage> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("deleted", NumberConstant.NUMBER_0);
        if (!BaseService.isAdmin(user)) {
            queryWrapper.in("origin_user_id", user.getId(), 0L);
        }
        if (ptImageQueryNameDTO.getImageResource() != null) {
            queryWrapper.eq("image_resource", ptImageQueryNameDTO.getImageResource());
        }
        List<PtImage> imageList = new ArrayList<>();
        if (CollectionUtil.isEmpty(ptImageQueryNameDTO.getImageTypes())) {
            imageList = ptImageMapper.selectList(queryWrapper);
        } else {
            imageList = ptImageMapper.getImagesByTypes(queryWrapper, ptImageQueryNameDTO.getImageTypes());
        }
        Set<String> imageNames = new HashSet<>();
        imageList.forEach(image -> {
            imageNames.add(image.getImageName());
        });
        return imageNames;
    }

    /**
     * 设置Notebook默认镜像
     *
     * @param id 镜像id
     */
    @Override
    public void updImageDefault(Long id) {
        UserContext user = userContextService.getCurUser();
        //notebook默认镜像只能由管理员设置
        if (!BaseService.isAdmin(user)) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED, "该用户无权限修改镜像状态!");
        }

        //校验id是否存在
        PtImage image = ptImageMapper.getImageById(id);
        if (image == null || !image.getImageTypes().contains(0)) {
            throw new BusinessException(ResponseCode.BADREQUEST, "该镜像不存在或镜像用途不支持!");
        }


        if (!image.getImageResource().equals(ImageSourceEnum.PRE.getCode())) {
            throw new BusinessException(ResponseCode.BADREQUEST, "非预制镜像不能设置为默认镜像!");
        }

        List<PtImage> defaultImages = ptImageMapper.getImageByDefault(1);
        List<Long> defaultImageIds = defaultImages.stream().map(defaultImage -> defaultImage.getId()).collect(Collectors.toList());
        //修改该notebook镜像为"默认镜像"
        UpdateWrapper<PtImage> updateWrapper = new UpdateWrapper<>();
        updateWrapper.in("id", defaultImageIds);
        updateWrapper.set("is_default", NumberConstant.NUMBER_0);
        ptImageMapper.update(null, updateWrapper);

        PtImage ptImage = new PtImage();
        ptImage.setId(id);
        ptImage.setIsDefault(NumberConstant.NUMBER_1);
        ptImageMapper.updateById(ptImage);
    }

    @Override
    public List<PtImage> getImageDefault() {
        return ptImageMapper.getImageByDefault(NumberConstant.NUMBER_1);
    }

    /**
     * 获取镜像URL
     *
     * @param imageQueryUrlDTO 查询镜像地址DTO
     * @return String 镜像url
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public String getImageUrl(PtImageQueryUrlDTO imageQueryUrlDTO) {
        LambdaQueryWrapper<PtImage> queryWrapper = new LambdaQueryWrapper<>();
        if (imageQueryUrlDTO.getImageResource() != null) {
            queryWrapper.eq(PtImage::getImageResource, imageQueryUrlDTO.getImageResource());
        }
        if (StrUtil.isNotEmpty(imageQueryUrlDTO.getImageName())) {
            queryWrapper.eq(PtImage::getImageName, imageQueryUrlDTO.getImageName());
        }
        if (StrUtil.isNotEmpty(imageQueryUrlDTO.getImageTag())) {
            queryWrapper.eq(PtImage::getImageTag, imageQueryUrlDTO.getImageTag());
        }
        if (imageQueryUrlDTO.getIsDefault() != null) {
            queryWrapper.eq(PtImage::getIsDefault, imageQueryUrlDTO.getIsDefault());
        }
        queryWrapper.eq(PtImage::getDeleted, NumberConstant.NUMBER_0);
        List<PtImage> imageList = new ArrayList<>();
        if (CollectionUtil.isNotEmpty(imageQueryUrlDTO.getImageTypes())) {
            imageList = ptImageMapper.getImagesByTypes(queryWrapper, imageQueryUrlDTO.getImageTypes());
        } else {
            imageList = ptImageMapper.selectList(queryWrapper);
        }

        if (CollUtil.isEmpty(imageList)) {
            throw new BusinessException("未查询到镜像信息");
        }
        String imageUrl = imageList.get(0).getImageUrl();
        DataContext.remove();
        return imageUrl;
    }

    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public List<PtImage> getTerminalImageList() {
        UserContext user = userContextService.getCurUser();
        LambdaQueryWrapper<PtImage> queryTerminalWrapper = new LambdaQueryWrapper<>();
        queryTerminalWrapper.eq(PtImage::getDeleted, NumberConstant.NUMBER_0);
        ;
        if (user != null && !BaseService.isAdmin()) {
            queryTerminalWrapper.and(wrapper -> wrapper.eq(PtImage::getCreateUserId, user.getId()).or().eq(PtImage::getImageResource, ImageSourceEnum.PRE.getCode()))
                    .and(wrapper -> wrapper.eq(PtImage::getDeleted, NumberConstant.NUMBER_0));
        }
        List<Integer> terminalImageType = new ArrayList<>();
        terminalImageType.add(ImageTypeEnum.TERMINAL.getType());
        List<PtImage> terminalImages = ptImageMapper.getImagesByTypes(queryTerminalWrapper, terminalImageType);

        List<PtImage> list = new ArrayList<>();
        if (CollUtil.isEmpty(terminalImages)) {
            return new ArrayList<>();
        }

        terminalImages.stream().forEach(ptImage -> {
            ptImage.setImageUrl(ptImage.getImageUrl());
            list.add(ptImage);
        });
        return list;
    }


    /**
     * @param ptImageSaveDTO 镜像保存逻辑校验
     * @param user             用户
     * @return List<PtImage>    镜像列表
     **/
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    private List<PtImage> checkSaveImage(PtImageSaveDTO ptImageSaveDTO, UserContext user, Integer source) {

        LambdaQueryWrapper<PtImage> queryWrapper = new LambdaQueryWrapper<>();

        queryWrapper.eq(PtImage::getImageUrl, ptImageSaveDTO.getImageUrl())
                .eq(PtImage::getImageResource, source);

        if (user != null) {
            queryWrapper.eq(PtImage::getCreateUserId, user.getId());
        }
        List<PtImage> imageList = ptImageMapper.selectList(queryWrapper);
        return imageList;
    }

    @Override
    public PtImageVO getById(Long id) {
        PtImage ptImage=ptImageMapper.getImageById(id);
        if(Objects.isNull(ptImage)){
            return null;
        }
        PtImageVO ptImageVO =new PtImageVO();
        ptImageVO.setName(ptImage.getImageName());
        ptImageVO.setId(ptImage.getId());
        ptImageVO.setImageUrl(ptImage.getImageUrl());
        return ptImageVO;
    }

    @Override
    public List<PtImageVO> listByIds(List<Long> ids) {
        List<PtImageVO> ptImageVOS = Lists.newArrayList();
        List<PtImage> ptImages = ptImageMapper.selectBatchIds(ids);

        ptImages.stream().forEach(
                ptImage -> {
                    PtImageVO ptImageVO = new PtImageVO();
                    ptImageVO.setId(ptImage.getId());
                    ptImageVO.setName(ptImage.getImageName());
                    ptImageVO.setTag(ptImage.getImageTag());
                    ptImageVOS.add(ptImageVO);
                }
        );
        return ptImageVOS;
    }

    /**
     *  校验镜像地址与镜像名称、tag是否匹配
     *
     * @param imageUrl 镜像地址
     * @param imageName 镜像名称
     * @param imageTag 镜像tag
     */
    private void validImageInfo(String imageUrl, String imageName, String imageTag) {
        // 标准镜像地址：镜像地址域名/命名空间/镜像名:镜像tag

        // 分解镜像地址
        String[] split = imageUrl.substring(imageUrl.lastIndexOf(StrUtil.SLASH) + 1).split(StrUtil.COLON);
        if (!split[0].equals(imageName)) {
            throw new BusinessException("镜像名称不匹配");
        }
        if (!split[1].equals(imageTag)) {
            throw new BusinessException("镜像版本号不匹配");
        }
    }
}
