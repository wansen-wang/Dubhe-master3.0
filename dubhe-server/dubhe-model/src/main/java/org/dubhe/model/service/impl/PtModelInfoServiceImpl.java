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

package org.dubhe.model.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dubhe.biz.base.constant.SymbolConstant;
import org.dubhe.biz.base.context.UserContext;
import org.dubhe.biz.base.dto.PtModelInfoConditionQueryDTO;
import org.dubhe.biz.base.dto.PtModelInfoQueryByIdDTO;
import org.dubhe.biz.base.dto.PtModelStatusQueryDTO;
import org.dubhe.biz.base.dto.UserDTO;
import org.dubhe.biz.base.enums.DatasetTypeEnum;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.service.UserContextService;
import org.dubhe.biz.base.utils.PtModelUtil;
import org.dubhe.biz.base.utils.ReflectionUtils;
import org.dubhe.biz.base.utils.StringUtils;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.base.vo.PtModelInfoQueryVO;
import org.dubhe.biz.db.utils.PageUtil;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.permission.annotation.DataPermissionMethod;
import org.dubhe.biz.permission.base.BaseService;
import org.dubhe.cloud.authconfig.service.AdminClient;
import org.dubhe.cloud.remotecall.config.RestTemplateHolder;
import org.dubhe.model.constant.ModelConstants;
import org.dubhe.model.dao.PtModelBranchMapper;
import org.dubhe.model.dao.PtModelInfoMapper;
import org.dubhe.model.domain.dto.PtModelBranchCreateDTO;
import org.dubhe.model.domain.dto.PtModelInfoByResourceDTO;
import org.dubhe.model.domain.dto.PtModelInfoCreateDTO;
import org.dubhe.model.domain.dto.PtModelInfoDeleteDTO;
import org.dubhe.model.domain.dto.PtModelInfoPackageDTO;
import org.dubhe.model.domain.dto.PtModelInfoQueryDTO;
import org.dubhe.model.domain.dto.PtModelInfoUpdateDTO;
import org.dubhe.model.domain.dto.PtModelOptimizationCreateDTO;
import org.dubhe.model.domain.dto.ServingModelDTO;
import org.dubhe.model.domain.entity.PtModelBranch;
import org.dubhe.model.domain.entity.PtModelInfo;
import org.dubhe.model.domain.enums.ModelPackageEnum;
import org.dubhe.model.domain.enums.ModelResourceEnum;
import org.dubhe.model.domain.vo.PtModelInfoByResourceVO;
import org.dubhe.model.domain.vo.PtModelInfoCreateVO;
import org.dubhe.model.domain.vo.PtModelInfoDeleteVO;
import org.dubhe.model.domain.vo.PtModelInfoUpdateVO;
import org.dubhe.model.service.FileService;
import org.dubhe.model.service.PtModelBranchService;
import org.dubhe.model.service.PtModelInfoService;
import org.dubhe.model.service.PtModelStructureService;
import org.dubhe.model.utils.ModelStatusUtil;
import org.dubhe.recycle.config.RecycleConfig;
import org.dubhe.recycle.domain.dto.RecycleCreateDTO;
import org.dubhe.recycle.domain.dto.RecycleDetailCreateDTO;
import org.dubhe.recycle.enums.RecycleModuleEnum;
import org.dubhe.recycle.enums.RecycleResourceEnum;
import org.dubhe.recycle.enums.RecycleTypeEnum;
import org.dubhe.recycle.service.RecycleService;
import org.dubhe.recycle.utils.RecycleTool;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @description ????????????
 * @date 2020-03-24
 */
@Service
public class PtModelInfoServiceImpl implements PtModelInfoService {

    @Autowired
    private PtModelBranchMapper ptModelBranchMapper;

    @Autowired
    private PtModelInfoMapper ptModelInfoMapper;

    @Value("${model.measuring.url.package}")
    private String modelMeasuringUrlPackage;

    @Autowired
    private PtModelBranchService ptModelBranchService;

    @Autowired
    private FileService fileService;

    @Autowired
    private RecycleService recycleService;

    @Autowired
    private RecycleConfig recycleConfig;

    @Autowired
    private UserContextService userContextService;

    @Autowired
    private RestTemplateHolder restTemplateHolder;

    @Autowired
    private ModelStatusUtil ModelStatusUtil;
    
    @Autowired
    private PtModelStructureService ptModelStructureService;

    @Resource
    private AdminClient adminClient;

    public final static List<String> FIELD_NAMES;

    static {
        FIELD_NAMES = ReflectionUtils.getFieldNames(PtModelInfoQueryVO.class);
    }

    /**
     * ??????????????????
     *
     * @param ptModelInfoQueryDTO ????????????????????????
     * @return Map<String, Object> ????????????????????????
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public Map<String, Object> queryAll(PtModelInfoQueryDTO ptModelInfoQueryDTO) {
        Page page = ptModelInfoQueryDTO.toPage();
        QueryWrapper<PtModelInfo> wrapper = new QueryWrapper<>();
        String modelName = ptModelInfoQueryDTO.getName();
        if (!StringUtils.isEmpty(modelName)) {
            wrapper.and(qw -> qw.eq("id", modelName).or().like("name",
                    modelName));
        }
        ModelResourceEnum modelResourceEnum = ModelResourceEnum.get(ptModelInfoQueryDTO.getModelResource());
        wrapper.eq("model_resource", modelResourceEnum.getCode());

        Integer packaged = ptModelInfoQueryDTO.getPackaged();
        wrapper.eq(packaged != null, "packaged", packaged);

        Integer frameType = ptModelInfoQueryDTO.getFrameType();
        wrapper.eq(frameType != null, "frame_type", frameType);

        String modelType = ptModelInfoQueryDTO.getModelClassName();
        wrapper.eq(StringUtils.isNotEmpty(modelType), "model_type", modelType);

        Integer modelFormat = ptModelInfoQueryDTO.getModelType();
        wrapper.eq(modelFormat != null, "model_format", modelFormat);

        String orderField = FIELD_NAMES.contains(ptModelInfoQueryDTO.getSort())
                ? StringUtils.humpToLine(ptModelInfoQueryDTO.getSort())
                : PtModelUtil.ID;
        boolean isAsc = PtModelUtil.SORT_ASC.equalsIgnoreCase(ptModelInfoQueryDTO.getOrder());
        wrapper.orderBy(true, isAsc, orderField);

        Page<PtModelInfo> ptModelInfos = ptModelInfoMapper.selectPage(page, wrapper);

        Map<Long, String> idUserNameMap = new HashMap<>();
        List<Long> userIds = ptModelInfos.getRecords().stream().map(PtModelInfo::getCreateUserId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(userIds)) {
            DataResponseBody<List<UserDTO>> result = adminClient.getUserList(userIds);
            if (result.getData() != null) {
                idUserNameMap = result.getData().stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getUsername, (o, n) -> n));
            }
        }
        Map<Long, String> finalIdUserNameMap = idUserNameMap;

        List<PtModelInfoQueryVO> ptModelInfoQueryVOList = ptModelInfos.getRecords().stream().map(x -> {
            PtModelInfoQueryVO vo = new PtModelInfoQueryVO();
            BeanUtils.copyProperties(x, vo);
            //???????????????????????????(?????????tensorflow???oneflow,keras?????????savedmodel???pytorch??????pth,?????????????????????)
            boolean flag = (x.getFrameType() == PtModelUtil.NUMBER_ONE && x.getModelType() == PtModelUtil.NUMBER_ONE)
                    || (x.getFrameType() == PtModelUtil.NUMBER_TWO && x.getModelType() == PtModelUtil.NUMBER_ONE)
                    || (x.getFrameType() == PtModelUtil.NUMBER_FOUR && x.getModelType() == PtModelUtil.NUMBER_ONE)
                    || (x.getFrameType() == PtModelUtil.NUMBER_THREE && x.getModelType() == PtModelUtil.NUMBER_EIGHT);
            vo.setServingModel(flag);

            //??????????????????????????????
            if (BaseService.isAdmin(userContextService.getCurUser()) && x.getCreateUserId() != null) {
                vo.setCreateUserName(finalIdUserNameMap.getOrDefault(x.getCreateUserId(), null));
            }

            return vo;
        }).collect(Collectors.toList());
        return PageUtil.toPage(page, ptModelInfoQueryVOList);
    }

    /**
     * ??????
     *
     * @param ptModelInfoCreateDTO ????????????????????????
     * @return PtModelInfoCreateVO ????????????????????????VO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PtModelInfoCreateVO create(PtModelInfoCreateDTO ptModelInfoCreateDTO) {
        //??????????????????
        UserContext user = userContextService.getCurUser();
        //??????????????????
        QueryWrapper<PtModelInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("name", ptModelInfoCreateDTO.getName()).and(wrapper -> wrapper.eq("create_user_id", user.getId()).or().eq("origin_user_id", 0L));
        Integer countResult = ptModelInfoMapper.selectCount(queryWrapper);
        if (countResult > 0) {
            LogUtil.error(LogEnum.BIZ_MODEL, "The user {} fail to save model???the name of model is already exist !", user.getUsername());
            throw new BusinessException("?????????????????????");
        }

        boolean isAtlas = ModelResourceEnum.ATLAS.getCode().equals(ptModelInfoCreateDTO.getModelResource());
        if (isAtlas) {
            String sourcePath = ptModelInfoCreateDTO.getModelAddress();
            if (StringUtils.isBlank(sourcePath)) {
                LogUtil.error(LogEnum.BIZ_MODEL, "The user {} fail to save model???the address of model is blank !", user.getUsername());
                throw new BusinessException("???????????????????????????????????????????????????");
            }
            String targetPath = fileService.transfer(sourcePath, user);
            //??????????????????
            ptModelInfoCreateDTO.setModelAddress(targetPath);
            
            //??????????????????????????????
            ptModelStructureService.create(ptModelInfoCreateDTO.getStructName(),ptModelInfoCreateDTO.getJobType());
        }

        //??????????????????
        PtModelInfo ptModelInfo = new PtModelInfo();
        BeanUtils.copyProperties(ptModelInfoCreateDTO, ptModelInfo);

        if (ptModelInfoMapper.insert(ptModelInfo) < 1) {
            //??????????????????????????????????????????????????????????????????
            LogUtil.error(LogEnum.BIZ_MODEL, "The user {} failed to save the model and failed to insert the model management table", user.getUsername());
            throw new BusinessException("??????????????????");
        }

        //???????????????????????????????????????????????????
        if (!isAtlas && ptModelInfoCreateDTO.getModelAddress() != null) {
            PtModelBranchCreateDTO ptModelBranchCreateDTO = new PtModelBranchCreateDTO();
            BeanUtils.copyProperties(ptModelInfoCreateDTO, ptModelBranchCreateDTO);
            ptModelBranchCreateDTO.setParentId(ptModelInfo.getId());
            ptModelBranchService.create(ptModelBranchCreateDTO);
        }
        PtModelInfoCreateVO ptModelInfoCreateVO = new PtModelInfoCreateVO();
        ptModelInfoCreateVO.setId(ptModelInfo.getId());
        return ptModelInfoCreateVO;
    }

    /**
     * ??????
     *
     * @param ptModelInfoUpdateDTO ????????????????????????
     * @return PtModelInfoUpdateVO ????????????????????????VO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PtModelInfoUpdateVO update(PtModelInfoUpdateDTO ptModelInfoUpdateDTO) {
        //??????????????????
        UserContext user = userContextService.getCurUser();
        //??????????????????
        QueryWrapper<PtModelInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("name", ptModelInfoUpdateDTO.getName()).ne("id", ptModelInfoUpdateDTO.getId());
        Integer countResult = ptModelInfoMapper.selectCount(queryWrapper);
        if (countResult > 0) {
            throw new BusinessException("?????????????????????");
        }

        //????????????
        QueryWrapper<PtModelInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("id", ptModelInfoUpdateDTO.getId());
        if (ptModelInfoMapper.selectCount(wrapper) < 1) {
            LogUtil.error(LogEnum.BIZ_MODEL, "The user {} failed to modify the model and has no permission to modify the corresponding data in the model table", user.getUsername());
            throw new BusinessException("????????????ID????????????????????????");
        }

        //??????????????????
        PtModelInfo ptModelInfo = new PtModelInfo();
        BeanUtils.copyProperties(ptModelInfoUpdateDTO, ptModelInfo);
        if (ptModelInfoMapper.updateById(ptModelInfo) < 1) {
            //??????????????????????????????????????????????????????????????????
            LogUtil.error(LogEnum.BIZ_MODEL, "User {} failed to modify the model, failed to modify the model table", user.getUsername());
            throw new BusinessException("??????????????????");
        }

        PtModelInfoUpdateVO ptModelInfoUpdateVO = new PtModelInfoUpdateVO();
        ptModelInfoUpdateVO.setId(ptModelInfo.getId());
        return ptModelInfoUpdateVO;
    }

    /**
     * ????????????
     *
     * @param ptModelInfoDeleteDTO ????????????????????????
     * @return PtModelInfoDeleteVO ????????????????????????VO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PtModelInfoDeleteVO deleteAll(PtModelInfoDeleteDTO ptModelInfoDeleteDTO) {
        //??????????????????
        UserContext user = userContextService.getCurUser();
        //??????ids??????
        List<Long> ids = Arrays.stream(ptModelInfoDeleteDTO.getIds()).distinct().collect(Collectors.toList());

        //????????????
        QueryWrapper<PtModelInfo> query = new QueryWrapper<>();
        //????????????????????????????????????
        if (!BaseService.isAdmin(user)) {
            query.eq("model_resource", 0);
        }
        query.in("id", ids);
        if (ptModelInfoMapper.selectCount(query) < ids.size()) {
            LogUtil.error(LogEnum.BIZ_MODEL, "The user {} failed to delete the model list, and has no permission to delete the corresponding data in the model management table", user.getUsername());
            throw new BusinessException("??????????????????");
        }
        //???????????????????????????????????????????????????????????????????????????(??????????????????????????????????????????????????????????????????????????????)
        PtModelStatusQueryDTO ptModelStatusQueryDTO = new PtModelStatusQueryDTO();
        ptModelStatusQueryDTO.setModelIds(ids);
        ModelStatusUtil.queryModelStatus(user, ptModelStatusQueryDTO, ids);

        // 
        if (ptModelInfoMapper.deleteBatchIds(ids) < ids.size()) {
            //???????????????????????????,????????????????????????????????????
            LogUtil.error(LogEnum.BIZ_MODEL, "The user {} failed to delete the model list. The model management table deletion operation based on ID array {} failed", user.getUsername(), ids);
            throw new BusinessException("??????????????????");
        }

        QueryWrapper queryBranch = new QueryWrapper<>();
        queryBranch.in("parent_id", ids);

        List<PtModelBranch> ptModelBranches = ptModelBranchMapper.selectList(queryBranch);
        List<Long> branchlists = ptModelBranches.stream().map(x -> {
            return x.getId();
        }).collect(Collectors.toList());
        if (branchlists.size() > 0) {
            if (ptModelBranchMapper.deleteBatchIds(branchlists) < branchlists.size()) {
                LogUtil.error(LogEnum.BIZ_MODEL, "User {} failed to delete model version. Deleting model version table according to ID array {} failed", user.getUsername(), ids);
                throw new BusinessException("??????????????????");
            }
            //???????????????????????????????????????
            for (PtModelBranch ptModelBranch : ptModelBranches) {
                RecycleCreateDTO recycleCreateDTO = RecycleCreateDTO.builder()
                        .recycleModule(RecycleModuleEnum.BIZ_MODEL.getValue())
                        .recycleDelayDate(recycleConfig.getModelValid())
                        .recycleNote(RecycleTool.generateRecycleNote("??????????????????", ptModelBranch.getId()))
                        .remark(ptModelBranch.getId().toString())
                        .restoreCustom(RecycleResourceEnum.MODEL_RECYCLE_FILE.getClassName())
                        .build();
                recycleCreateDTO.addRecycleDetailCreateDTO(RecycleDetailCreateDTO.builder()
                        .recycleType(RecycleTypeEnum.FILE.getCode())
                        .recycleCondition(fileService.getAbsolutePath(ptModelBranch.getModelAddress()))
                        .recycleNote(RecycleTool.generateRecycleNote("??????????????????", ptModelBranch.getId()))
                        .build()
                );
                recycleService.createRecycleTask(recycleCreateDTO);
            }
        }

        //?????????????????????????????????id??????
        PtModelInfoDeleteVO ptModelInfoDeleteVO = new PtModelInfoDeleteVO();
        ptModelInfoDeleteVO.setIds(ptModelInfoDeleteDTO.getIds());
        return ptModelInfoDeleteVO;
    }

    /**
     * ????????????????????????????????????
     *
     * @param ptModelInfoByResourceDTO ??????????????????
     * @return PtModelInfoByResourceVO  ??????????????????VO
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public List<PtModelInfoByResourceVO> getModelByResource(PtModelInfoByResourceDTO ptModelInfoByResourceDTO) {

        LambdaQueryWrapper<PtModelInfo> query = new LambdaQueryWrapper<>();
        query.eq(PtModelInfo::getModelResource, ptModelInfoByResourceDTO.getModelResource())
                .eq(ModelPackageEnum.isValid(ptModelInfoByResourceDTO.getPackaged()), PtModelInfo::getPackaged, ptModelInfoByResourceDTO.getPackaged())
                .isNotNull(PtModelInfo::getModelAddress)
                .ne(PtModelInfo::getModelAddress, SymbolConstant.BLANK).orderByDesc(PtModelInfo::getId);

        List<PtModelInfo> ptModelInfos = ptModelInfoMapper.selectList(query);
        ArrayList<PtModelInfoByResourceVO> ptModelInfoByResourceVOS = new ArrayList<>();

        ptModelInfos.forEach(ptModelInfo -> {
            PtModelInfoByResourceVO ptModelInfoByResourceVO = new PtModelInfoByResourceVO();
            BeanUtil.copyProperties(ptModelInfo, ptModelInfoByResourceVO);
            ptModelInfoByResourceVO.setUrl(ptModelInfo.getModelAddress());
            ptModelInfoByResourceVOS.add(ptModelInfoByResourceVO);
        });

        return ptModelInfoByResourceVOS;
    }

    /**
     * ????????????id??????????????????
     *
     * @param ptModelInfoQueryByIdDTO ????????????id??????????????????????????????
     * @return PtModelBranchQueryByIdVO ????????????id??????????????????????????????
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public PtModelInfoQueryVO queryByModelId(PtModelInfoQueryByIdDTO ptModelInfoQueryByIdDTO) {
        PtModelInfo ptModelInfo = ptModelInfoMapper.selectById(ptModelInfoQueryByIdDTO.getId());
        if (ptModelInfo == null) {
            return null;
        }
        PtModelInfoQueryVO ptModelInfoQueryByIdVO = new PtModelInfoQueryVO();
        BeanUtils.copyProperties(ptModelInfo, ptModelInfoQueryByIdVO);
        return ptModelInfoQueryByIdVO;
    }

    /**
     * ??????????????????????????????????????????
     *
     * @param ptModelInfoConditionQueryDTO ????????????
     * @return List<PtModelInfoQueryVO> ??????????????????
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public List<PtModelInfoQueryVO> getConditionQuery(PtModelInfoConditionQueryDTO ptModelInfoConditionQueryDTO) {
        LambdaQueryWrapper<PtModelInfo> query = new LambdaQueryWrapper<>();
        query.eq(PtModelInfo::getModelResource, ptModelInfoConditionQueryDTO.getModelResource())
                .in(PtModelInfo::getId, ptModelInfoConditionQueryDTO.getIds())
                .isNotNull(PtModelInfo::getModelAddress)
                .ne(PtModelInfo::getModelAddress, SymbolConstant.BLANK);
        List<PtModelInfo> modelInfoList = ptModelInfoMapper.selectList(query);
        List<PtModelInfoQueryVO> modelInfoQueryList = modelInfoList.stream().map(x -> {
                    PtModelInfoQueryVO ptModelInfoQueryVO = new PtModelInfoQueryVO();
                    BeanUtils.copyProperties(x, ptModelInfoQueryVO);
                    return ptModelInfoQueryVO;
                }
        ).collect(Collectors.toList());
        return modelInfoQueryList;
    }

    /**
     * ????????????????????????
     *
     * @param ptModelInfoPackageDTO ??????????????????DTO
     * @param ptModelInfo           ????????????
     * @return ????????????
     */
    private JSONObject buildPackageAtlasModelParams(PtModelInfoPackageDTO ptModelInfoPackageDTO, PtModelInfo ptModelInfo) {
        //????????????
        JSONObject metadata = new JSONObject();
        JSONObject params = new JSONObject();
        JSONObject input = new JSONObject();
        input.put(ModelConstants.SIZE, ptModelInfoPackageDTO.getSize());
        input.put(ModelConstants.RANGE, ptModelInfoPackageDTO.getRange());
        input.put(ModelConstants.SPACE, ptModelInfoPackageDTO.getRange());

        JSONObject normalize = new JSONObject();
        input.put(ModelConstants.NORMALIZE, normalize);
        normalize.put(ModelConstants.STD, ptModelInfoPackageDTO.getStd());
        normalize.put(ModelConstants.MEAN, ptModelInfoPackageDTO.getMean());

        metadata.put(ModelConstants.NAME, ptModelInfoPackageDTO.getName());
        metadata.put(ModelConstants.URL, ptModelInfoPackageDTO.getUrl());
        metadata.put(ModelConstants.TASK, ptModelInfoPackageDTO.getTask());
        metadata.put(ModelConstants.INPUT, input);
        metadata.put(ModelConstants.DATASET, ptModelInfoPackageDTO.getDataset());

        JSONObject entryArgs = new JSONObject();
        entryArgs.put(ModelConstants.PRETRAINED, ptModelInfoPackageDTO.getEntryPretrained());
        entryArgs.put(ModelConstants.NUM_CLASSES, ptModelInfoPackageDTO.getEntryNumClasses());
        metadata.put(ModelConstants.ENTRY_ARGS, entryArgs);

        JSONObject otherMetadata = new JSONObject();
        otherMetadata.put(ModelConstants.NUM_CLASSES, ptModelInfoPackageDTO.getOtherNumClasses());
        metadata.put(ModelConstants.OTHER_METADATA, otherMetadata);

        params.put(ModelConstants.METADATA, metadata);


        //??????????????????
        params.put(ModelConstants.CKPT, ptModelInfo.getModelAddress());
        params.put(ModelConstants.ENTRY_NAME, ptModelInfoPackageDTO.getEntryName());
        params.put(ModelConstants.README, ptModelInfoPackageDTO.getReadme());
        return params;
    }

    /**
     * ?????????????????????
     *
     * @param ptModelInfoPackageDTO ????????????
     * @return Boolean              ???????????? true ?????? false ??????
     */
    @Override
    public String packageAtlasModel(PtModelInfoPackageDTO ptModelInfoPackageDTO) {
        //??????????????????
        UserContext user = userContextService.getCurUser();
        Long modelId = ptModelInfoPackageDTO.getId();
        PtModelInfo ptModelInfo = validateAtlasModel(modelId);

        JSONObject params = buildPackageAtlasModelParams(ptModelInfoPackageDTO, ptModelInfo);

        JSONArray jsonArray = new JSONArray();

        jsonArray.add(params);

        RestTemplate restTemplate = restTemplateHolder.getRestTemplate();

        LogUtil.error(LogEnum.BIZ_MODEL, "?????????????????????????????????url:{},jsonArray{}", modelMeasuringUrlPackage, jsonArray);
        DataResponseBody<List<?>> result = restTemplate.postForObject(modelMeasuringUrlPackage, jsonArray, DataResponseBody.class);

        if (result == null || !result.succeed()) {
            LogUtil.error(LogEnum.BIZ_MODEL, "?????????{}????????????????????????????????????????????????????????????????????????{}", user.getUsername(), result);
            throw new BusinessException("?????????????????????????????????");
        }
        if (CollectionUtils.isEmpty(result.getData())) {
            LogUtil.error(LogEnum.BIZ_MODEL, "?????????{}?????????????????????,????????????????????????????????????", user.getUsername());
            throw new BusinessException("????????????????????????????????????");
        }

        PtModelInfo updateEntity = new PtModelInfo();
        updateEntity.setId(ptModelInfoPackageDTO.getId());
        String savePath = result.getData().get(0) + "";
        //????????????????????????
        updateEntity.setModelAddress(savePath);
        updateEntity.setPackaged(ModelPackageEnum.PACKAGED.getCode());
        params.put(ModelConstants.SAVE_PATH, savePath);
        updateEntity.setTags(params.toJSONString());

        int res = ptModelInfoMapper.updateById(updateEntity);
        if (res < 1) {
            LogUtil.error(LogEnum.BIZ_MODEL, "?????????{}?????????????????????,??????????????????????????????????????????", user.getUsername());
            throw new BusinessException("?????????????????????");
        }

        return result.getMsg();
    }

    /**
     * ??????????????????
     *
     * @param modelId ??????id
     */
    private PtModelInfo validateAtlasModel(Long modelId) {
        UserContext user = userContextService.getCurUser();
        //???????????????????????????
        PtModelInfo ptModelInfo = ptModelInfoMapper.selectById(modelId);

        if (null == ptModelInfo) {
            LogUtil.error(LogEnum.BIZ_MODEL, "?????????{}???????????????????????????,???ID???{}????????????????????????", user.getUsername(), modelId);
            throw new BusinessException("???????????????");
        }

        if (!ModelResourceEnum.ATLAS.getCode().equals(ptModelInfo.getModelResource())) {
            LogUtil.error(LogEnum.BIZ_MODEL, "?????????{}????????????????????????????????????????????????????????????", user.getUsername());
            throw new BusinessException("??????????????????????????????");
        }
        if (ModelPackageEnum.PACKAGED.getCode().equals(ptModelInfo.getPackaged())) {
            LogUtil.error(LogEnum.BIZ_MODEL, "?????????{}?????????????????????????????????????????????????????????????????????????????????", user.getUsername());
            throw new BusinessException("?????????????????????");
        }
        return ptModelInfo;
    }

    /**
     * ????????????????????????
     *
     * @param ptModelOptimizationCreateDTO ??????????????????????????????
     * @return PtModelInfoByResourceVO ?????????????????????????????????
     */
    @Override
    public PtModelInfoByResourceVO modelOptimizationUploadModel(PtModelOptimizationCreateDTO ptModelOptimizationCreateDTO) {
        PtModelInfoCreateDTO ptModelInfoCreateDTO = new PtModelInfoCreateDTO();
        ptModelInfoCreateDTO.setName(ptModelOptimizationCreateDTO.getName()).setModelAddress(ptModelOptimizationCreateDTO.getPath()).setModelSource(PtModelUtil.NUMBER_ZERO).setFrameType(PtModelUtil.NUMBER_ONE).setModelType(PtModelUtil.NUMBER_ONE).setModelDescription("????????????????????????");
        PtModelInfoCreateVO ptModelInfoCreateVO = create(ptModelInfoCreateDTO);
        PtModelInfo ptModelInfo = ptModelInfoMapper.selectById(ptModelInfoCreateVO.getId());
        PtModelInfoByResourceVO ptModelInfoByResourceVO = new PtModelInfoByResourceVO();
        BeanUtil.copyProperties(ptModelInfo, ptModelInfoByResourceVO);
        return ptModelInfoByResourceVO;
    }

    /**
     * ??????????????????????????????
     * ?????????tensorflow???oneflow,keras?????????savedmodel???pytorch??????pth
     * @return ????????????????????????????????????
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public List<PtModelInfoQueryVO> getServingModel(ServingModelDTO servingModelDTO) {
        UserContext user = userContextService.getCurUser();
        QueryWrapper<PtModelInfo> query = new QueryWrapper<>();
        query.eq("model_resource", servingModelDTO.getModelResource()).eq("model_format", PtModelUtil.NUMBER_ONE).in("frame_type", PtModelUtil.NUMBER_ONE, PtModelUtil.NUMBER_TWO, PtModelUtil.NUMBER_FOUR)
                .isNotNull("model_version").ne("model_version", "");
        query.or(qw -> qw.eq("model_resource", servingModelDTO.getModelResource()).eq("model_format", PtModelUtil.NUMBER_EIGHT).eq("frame_type", PtModelUtil.NUMBER_THREE)
                .isNotNull("model_version").ne("model_version", ""));
        query.orderByDesc("id");
        List<PtModelInfo> ptModelInfos = null;
        try {
            ptModelInfos = ptModelInfoMapper.selectList(query);
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_MODEL, "Exception for user {} to query the details of the model that can provide serving, because:{}", user.getUsername(), e);
            throw new BusinessException("??????????????????");
        }
        if (CollectionUtils.isEmpty(ptModelInfos)) {
            return null;
        }
        List<PtModelInfoQueryVO> servingModelList = ptModelInfos.stream().map(x -> {
            PtModelInfoQueryVO vo = new PtModelInfoQueryVO();
            BeanUtils.copyProperties(x, vo);
            return vo;
        }).collect(Collectors.toList());
        return servingModelList;
    }

    @Override
    public PtModelInfoQueryVO getAtlasModels(String name) {

        List<PtModelInfo> ptModelInfos = ptModelInfoMapper.selectList(new LambdaQueryWrapper<PtModelInfo>()
                .eq(PtModelInfo::getName, name)
                .eq(PtModelInfo::getModelResource, ModelResourceEnum.ATLAS.getCode()));
        PtModelInfoQueryVO ptModelInfoQueryVO = new PtModelInfoQueryVO();

        if (CollUtil.isNotEmpty(ptModelInfos)) {
            BeanUtils.copyProperties(ptModelInfos.get(0), ptModelInfoQueryVO);
        }
        return ptModelInfoQueryVO;
    }

}
