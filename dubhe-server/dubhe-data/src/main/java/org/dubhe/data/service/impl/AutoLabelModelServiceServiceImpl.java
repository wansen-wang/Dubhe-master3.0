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

package org.dubhe.data.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.compress.utils.Lists;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.base.context.UserContext;
import org.dubhe.biz.base.dto.PtImageIdDTO;
import org.dubhe.biz.base.dto.PtImageIdsDTO;
import org.dubhe.biz.base.dto.PtModelBranchQueryByIdDTO;
import org.dubhe.biz.base.dto.PtModelBranchQueryByIdsDTO;
import org.dubhe.biz.base.dto.TrainAlgorithmSelectAllBatchIdDTO;
import org.dubhe.biz.base.dto.TrainAlgorithmSelectByIdDTO;
import org.dubhe.biz.base.dto.UserDTO;
import org.dubhe.biz.base.dto.UserSmallDTO;
import org.dubhe.biz.base.enums.BizEnum;
import org.dubhe.biz.base.enums.ResourcesPoolTypeEnum;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.service.UserContextService;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.base.vo.PtImageVO;
import org.dubhe.biz.base.vo.PtModelBranchQueryVO;
import org.dubhe.biz.base.vo.TrainAlgorithmQureyVO;
import org.dubhe.biz.db.utils.PageUtil;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.permission.base.BaseService;
import org.dubhe.biz.redis.utils.RedisUtils;
import org.dubhe.biz.statemachine.dto.StateChangeDTO;
import org.dubhe.cloud.authconfig.service.AdminClient;
import org.dubhe.data.client.AlgorithmClient;
import org.dubhe.data.client.ImageClient;
import org.dubhe.data.client.ModelClient;
import org.dubhe.data.config.DataRedisConfig;
import org.dubhe.data.constant.AutoLabelModelServiceStatusEnum;
import org.dubhe.data.constant.Constant;
import org.dubhe.data.constant.ErrorEnum;
import org.dubhe.data.dao.AutoLabelModelServiceMapper;
import org.dubhe.data.domain.dto.AutoLabelModelServiceCreateDTO;
import org.dubhe.data.domain.dto.AutoLabelModelServiceQueryDTO;
import org.dubhe.data.domain.dto.AutoLabelModelServiceUpdateDTO;
import org.dubhe.data.domain.dto.DataK8sDeploymentCallbackCreateDTO;
import org.dubhe.data.domain.entity.AutoLabelModelService;
import org.dubhe.data.domain.vo.AutoLabelModelServicePodVO;
import org.dubhe.data.domain.vo.AutoLabelModelServiceVO;
import org.dubhe.data.machine.constant.ModelStateMachineConstant;
import org.dubhe.data.machine.utils.StateMachineUtil;
import org.dubhe.data.service.AutoLabelModelServiceService;
import org.dubhe.k8s.api.ModelServiceApi;
import org.dubhe.k8s.api.PodApi;
import org.dubhe.k8s.cache.ResourceCache;
import org.dubhe.k8s.domain.bo.ModelServiceBO;
import org.dubhe.k8s.domain.bo.PtMountDirBO;
import org.dubhe.k8s.domain.resource.BizContainerStatus;
import org.dubhe.k8s.domain.resource.BizPod;
import org.dubhe.k8s.enums.PodPhaseEnum;
import org.dubhe.k8s.utils.K8sNameTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.dubhe.data.constant.Constant.SORT_ASC;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author ${author}
 * @since 2022-05-25
 */
@Service
public class AutoLabelModelServiceServiceImpl extends ServiceImpl<AutoLabelModelServiceMapper, AutoLabelModelService> implements AutoLabelModelServiceService, IService<AutoLabelModelService> {

    @Resource
    private ModelClient modelClient;

    @Resource
    private AlgorithmClient algorithmClient;

    @Resource
    private ImageClient imageClient;

    @Resource
    private AdminClient adminClient;

    @Resource
    private ModelServiceApi modelServiceApi;

    @Resource
    private K8sNameTool k8sNameTool;

    @Resource
    private UserContextService userContextService;

    @Resource
    private DataRedisConfig dataRedisConfig;

    @Resource
    private PodApi podApi;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private ResourceCache resourceCache;

    @Value("Task:AutoLabelModelService:"+"${spring.profiles.active}_autoLabelModelService_id_")
    private String autoLabelModelServiceIdPrefix;

    @Override
    public Long create(AutoLabelModelServiceCreateDTO autoLabelModelServiceCreateDTO) {

        //校验镜像是否存在
        checkImage(autoLabelModelServiceCreateDTO.getImageId());

        //校验模型
        checkModel(autoLabelModelServiceCreateDTO.getModelBranchId());

        //校验算法是否存在
        checkAlgorithm(autoLabelModelServiceCreateDTO.getAlgorithmId());
        Long userId = checkUser();
        String taskIdentify = org.dubhe.biz.base.utils.StringUtils.getUUID();

        AutoLabelModelService autoLabelModelService = insert(autoLabelModelServiceCreateDTO, userId, taskIdentify);

        createK8sDeployment(autoLabelModelService.getId(), taskIdentify);
        return autoLabelModelService.getId();
    }

    @Override
    public boolean update(AutoLabelModelServiceUpdateDTO autoLabelModelServiceUpdateDTO) {
        AutoLabelModelService autoLabelModelService = baseMapper.selectById(autoLabelModelServiceUpdateDTO);
        if (ObjectUtil.isNull(autoLabelModelService)) {
            throw new BusinessException(ErrorEnum.MODEL_SERVER_NOT_EXIST);
        }
        // 模型服务的状态只有是已停止、启动失败的情况下才允许修改
        if (!Arrays.asList(AutoLabelModelServiceStatusEnum.STOPED.getValue(),
                AutoLabelModelServiceStatusEnum.START_FAILED.getValue()).contains(autoLabelModelService.getStatus())) {
            throw new BusinessException(ErrorEnum.MODEL_SERVER_CURRENT_STATUS_NOT_UPDATE);
        }
        // 检查模型镜像算法等
        //校验镜像是否存在
        checkImage(autoLabelModelServiceUpdateDTO.getImageId());
        //校验模型
        checkModel(autoLabelModelServiceUpdateDTO.getModelBranchId());
        //校验算法是否存在
        checkAlgorithm(autoLabelModelServiceUpdateDTO.getAlgorithmId());
        try {
            baseMapper.updateById(autoLabelModelServiceUpdateDTO.toAutoLabelModelService());
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorEnum.MODEL_NAME_DUPLICATED_ERROR);
        }
        return true;
    }

    @Override
    public AutoLabelModelService detail(Long modelServiceId) {
        return baseMapper.selectById(modelServiceId);
    }

    private void createK8sDeployment(Long modelServiceId, String taskIdentify) {
        AutoLabelModelService autoLabelModelService = baseMapper.selectById(modelServiceId);

        PtImageVO ptImageVO = checkImage(autoLabelModelService.getImageId());
        TrainAlgorithmQureyVO trainAlgorithmQureyVO = checkAlgorithm(autoLabelModelService.getAlgorithmId());
        PtModelBranchQueryVO ptModelBranchQueryVO = checkModel(autoLabelModelService.getModelBranchId());
        //创建pod
        ModelServiceBO modelServiceBO = new ModelServiceBO();
        modelServiceBO.setNamespace(k8sNameTool.getNamespace(autoLabelModelService.getCreateUserId()));
        modelServiceBO.setResourceName(k8sNameTool.generateResourceName(BizEnum.DATA, autoLabelModelService.getId().toString()));
        modelServiceBO.setBusinessLabel(k8sNameTool.getPodLabel(BizEnum.DATA));
        modelServiceBO.setReplicas(autoLabelModelService.getInstanceNum());
        modelServiceBO.setImage(ptImageVO.getImageUrl());
        modelServiceBO.setTaskIdentifyLabel(taskIdentify);

        String command = String.format(Constant.MODEL_SERVICE_COMMAND, modelServiceId,
                ResourcesPoolTypeEnum.isGpuCode(autoLabelModelService.getResourcesPoolType()) ? Constant.TRUE : Constant.FALSE,
                dataRedisConfig.getHost(), dataRedisConfig.getPort(), dataRedisConfig.getDatabase(), dataRedisConfig.getPassword());
        modelServiceBO.setCmdLines(Arrays.asList("-c", command));

        Map<String, PtMountDirBO> fsMounts = new HashMap<>();
        fsMounts.put(Constant.DATA_PROCESS_NFS_PATH, new PtMountDirBO(Constant.DATA_PROCESS_HOST_NFS_PATH));
        fsMounts.put(Constant.DATA_PROCESS_PATH, new PtMountDirBO(Constant.DATA_PROCESS_HOST_PATH));
        String algorithmPath = String.format(Constant.DATA_PROCESS_ALGORITHM_PATH, modelServiceId);
        fsMounts.put(algorithmPath, new PtMountDirBO(k8sNameTool.getAbsolutePath(trainAlgorithmQureyVO.getCodeDir())));
        if(Objects.nonNull(ptModelBranchQueryVO)){
            fsMounts.put(algorithmPath + "/model",
                    new PtMountDirBO(k8sNameTool.getAbsolutePath(ptModelBranchQueryVO.getModelAddress())));
        }

        modelServiceBO.setFsMounts(fsMounts);

        Integer cpuNum = JSONObject.parseObject(autoLabelModelService.getResourcesPoolSpecs().replace("\\", "")).getInteger("cpuNum");
        modelServiceBO.setCpuNum(cpuNum * MagicNumConstant.ONE_THOUSAND);
        Integer memNum = JSONObject.parseObject(autoLabelModelService.getResourcesPoolSpecs().replace("\\", "")).getInteger("memNum");
        modelServiceBO.setMemNum(memNum);
        if (autoLabelModelService.getResourcesPoolType().equals(ResourcesPoolTypeEnum.GPU.getCode())) {
            Integer gpuNum = JSONObject.parseObject(autoLabelModelService.getResourcesPoolSpecs().replace("\\", "")).getInteger("gpuNum");
            modelServiceBO.setGpuNum(gpuNum);
        }

        modelServiceApi.create(modelServiceBO);
    }

    private Long checkUser() {
        UserContext user = userContextService.getCurUser();
        // 参数校验
        if (user == null) {
            throw new BusinessException("当前用户信息已失效");
        } else {
            return user.getId();
        }

    }

    /**
     * 校验镜像
     *
     * @param imageId 镜像id
     */
    private PtImageVO checkImage(Long imageId) {
        PtImageIdDTO ptImageIdDTO = new PtImageIdDTO();
        ptImageIdDTO.setId(imageId);
        //算法路径获取
        DataResponseBody<PtImageVO> dataResponseBody = imageClient.getById(ptImageIdDTO);

        PtImageVO ptImageVO = null;
        if (dataResponseBody.succeed()) {
            ptImageVO = dataResponseBody.getData();
        }
        if (Objects.isNull(ptImageVO)) {
            throw new BusinessException(ErrorEnum.ALGORITHM_NOT_EXIST);
        }
        return ptImageVO;
    }

    /**
     * 校验算法
     *
     * @param algorithmId 算法id
     */
    private TrainAlgorithmQureyVO checkAlgorithm(Long algorithmId) {
        TrainAlgorithmSelectByIdDTO trainAlgorithmSelectByIdDTO = new TrainAlgorithmSelectByIdDTO();
        trainAlgorithmSelectByIdDTO.setId(algorithmId);
        //算法路径获取
        DataResponseBody<TrainAlgorithmQureyVO> dataResponseBody = algorithmClient.selectById(trainAlgorithmSelectByIdDTO);

        TrainAlgorithmQureyVO trainAlgorithmQureyVO = null;
        if (dataResponseBody.succeed()) {
            trainAlgorithmQureyVO = dataResponseBody.getData();
        }
        if (Objects.isNull(trainAlgorithmQureyVO)) {
            throw new BusinessException(ErrorEnum.ALGORITHM_NOT_EXIST);
        }
        return trainAlgorithmQureyVO;
    }

    /**
     * 校验模型信息
     *
     * @param modelBranchId 模型版本id
     */
    private PtModelBranchQueryVO checkModel(Long modelBranchId) {
        if(Objects.isNull(modelBranchId)){
            return null;
        }
        PtModelBranchQueryByIdDTO ptModelBranchQueryByIdDTO = new PtModelBranchQueryByIdDTO();
        ptModelBranchQueryByIdDTO.setId(modelBranchId);
        DataResponseBody<PtModelBranchQueryVO> modelInfoPresetDataResponseBody = modelClient
                .getByBranchId(ptModelBranchQueryByIdDTO);
        PtModelBranchQueryVO PtModelBranchQueryVO = null;
        if (modelInfoPresetDataResponseBody.succeed()) {
            PtModelBranchQueryVO = modelInfoPresetDataResponseBody.getData();
        }
        if (Objects.isNull(PtModelBranchQueryVO)) {
            throw new BusinessException(ErrorEnum.MODEL_NOT_EXIST);
        }
        return PtModelBranchQueryVO;
    }


    private AutoLabelModelService insert(AutoLabelModelServiceCreateDTO autoLabelModelServiceCreateDTO, Long createUserId, String taskIdentify) {
        AutoLabelModelService autoLabelModelService = AutoLabelModelService.builder()
                .name(autoLabelModelServiceCreateDTO.getName())
                .modelType(autoLabelModelServiceCreateDTO.getModelType())
                .modelBranchId(autoLabelModelServiceCreateDTO.getModelBranchId())
                .modelParentId(autoLabelModelServiceCreateDTO.getModelParentId())
                .imageName(autoLabelModelServiceCreateDTO.getImageName())
                .imageId(autoLabelModelServiceCreateDTO.getImageId())
                .algorithmId(autoLabelModelServiceCreateDTO.getAlgorithmId())
                .remark(autoLabelModelServiceCreateDTO.getDesc())
                .resourcesPoolSpecs(autoLabelModelServiceCreateDTO.getResourcesPoolSpecs())
                .resourcesPoolType(autoLabelModelServiceCreateDTO.getResourcesPoolType())
                .instanceNum(autoLabelModelServiceCreateDTO.getInstanceNum())
                .status(AutoLabelModelServiceStatusEnum.STARTING.getValue())
                .build();
        autoLabelModelService.setCreateTime(new Timestamp(System.currentTimeMillis()));
        autoLabelModelService.setCreateUserId(createUserId);
        try {
            baseMapper.insert(autoLabelModelService);;
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorEnum.MODEL_NAME_DUPLICATED_ERROR);
        }
        resourceCache.addTaskCache(taskIdentify,autoLabelModelService.getId(), autoLabelModelService.getName(), autoLabelModelServiceIdPrefix);
        return autoLabelModelService;
    }

    @Override
    public void delete(List<Long> ids) {
        //校验是否可以删除
        List<AutoLabelModelService> autoLabelModelServices = baseMapper.selectByIds(ids);
        for (AutoLabelModelService autoLabelModelService : autoLabelModelServices) {
            if (!(autoLabelModelService.getStatus().equals(AutoLabelModelServiceStatusEnum.START_FAILED.getValue())
                    || autoLabelModelService.getStatus().equals(AutoLabelModelServiceStatusEnum.STOPED.getValue()))) {
                throw new BusinessException(ErrorEnum.MODEL_SERVICE_CANNOT_DELETE);
            }
            String taskIdentify = (String) redisUtils.get(autoLabelModelServiceIdPrefix + String.valueOf(autoLabelModelService.getId()));
            if (org.dubhe.biz.base.utils.StringUtils.isNotEmpty(taskIdentify)){
                redisUtils.del(taskIdentify, autoLabelModelServiceIdPrefix + String.valueOf(autoLabelModelService.getId()));
            }
        }

        baseMapper.deleteByIds(ids);
    }

    @Override
    public Map<String, Object> list(Page<AutoLabelModelService> page, AutoLabelModelServiceQueryDTO autoLabelModelServiceQueryDTO) {
        return queryModelServices(page, autoLabelModelServiceQueryDTO);
    }

    private Map<String, Object> queryModelServices(Page<AutoLabelModelService> page, AutoLabelModelServiceQueryDTO autoLabelModelServiceQueryDTO) {
        List<AutoLabelModelServiceVO> modelServiceVOS = new ArrayList<>();

        //查询条件组装
        QueryWrapper<AutoLabelModelService> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .eq(Objects.nonNull(autoLabelModelServiceQueryDTO.getModelType()), AutoLabelModelService::getModelType
                        , autoLabelModelServiceQueryDTO.getModelType())
                .eq(Objects.nonNull(autoLabelModelServiceQueryDTO.getStatus()), AutoLabelModelService::getStatus
                        , autoLabelModelServiceQueryDTO.getStatus());
        if (Objects.nonNull(autoLabelModelServiceQueryDTO.getSearchContent())) {
            queryWrapper.lambda().and(wrapper -> wrapper.eq(org.dubhe.data.util.StringUtils.isDigit(autoLabelModelServiceQueryDTO.getSearchContent()),
                    AutoLabelModelService::getId, autoLabelModelServiceQueryDTO.getSearchContent()).or()
                    .like(true, AutoLabelModelService::getName, autoLabelModelServiceQueryDTO.getSearchContent()));
        }
        //排序条件
        if (StringUtils.isNotEmpty(autoLabelModelServiceQueryDTO.getOrder()) && StringUtils.isNotEmpty(autoLabelModelServiceQueryDTO.getSort())) {
            queryWrapper.orderBy(
                    true,
                    SORT_ASC.equals(autoLabelModelServiceQueryDTO.getOrder().toLowerCase()),
                    org.dubhe.biz.base.utils.StringUtils.humpToLine(autoLabelModelServiceQueryDTO.getSort())
            );
        } else {
            queryWrapper.orderByDesc("create_time");
        }
        //获取用户信息
        UserContext user = userContextService.getCurUser();
        if (!BaseService.isAdmin(user)) {
            queryWrapper.lambda().eq(AutoLabelModelService::getCreateUserId, user.getId());
        }
        page = baseMapper.selectPage(page, queryWrapper);

        if (CollectionUtils.isEmpty(page.getRecords())) {
            return PageUtil.toPage(page, modelServiceVOS);
        }

        modelServiceVOS = buildModelServiceVO(page.getRecords());
        return PageUtil.toPage(page, modelServiceVOS);
    }

    private List<AutoLabelModelServiceVO> buildModelServiceVO(List<AutoLabelModelService> autoLabelModelServices) {
        List<AutoLabelModelServiceVO> modelServiceVOS = new ArrayList<>();
        Map<Long, PtModelBranchQueryVO> modelMap = mapModeInfo(autoLabelModelServices);
        Map<Long, String> imageNameMap = mapImage(autoLabelModelServices);
        Map<Long, UserDTO> userMap = mapUser(autoLabelModelServices);
        Map<Long, String> algorithmNameMap = mapAlgorithm(autoLabelModelServices);

        for (AutoLabelModelService autoLabelModelService : autoLabelModelServices) {
            AutoLabelModelServiceVO autoLabelModelServiceVO = AutoLabelModelServiceVO.builder()
                    .id(autoLabelModelService.getId())
                    .name(autoLabelModelService.getName())
                    .modelType(autoLabelModelService.getModelType())
                    .desc(autoLabelModelService.getRemark())
                    .instanceNum(autoLabelModelService.getInstanceNum())
                    .resourcesPoolSpecs(autoLabelModelService.getResourcesPoolSpecs())
                    .resourcesPoolType(autoLabelModelService.getResourcesPoolType())
                    .status(autoLabelModelService.getStatus())
                    .createTime(autoLabelModelService.getCreateTime())
                    .image(imageNameMap.get(autoLabelModelService.getImageId()))
                    .algorithm(algorithmNameMap.get(autoLabelModelService.getAlgorithmId()))
                    .build();
            PtModelBranchQueryVO ptModelBranchQueryVO = modelMap.get(autoLabelModelService.getModelBranchId());
            if (Objects.nonNull(ptModelBranchQueryVO)) {
                autoLabelModelServiceVO.setModel(ptModelBranchQueryVO.getName() + ":" + ptModelBranchQueryVO.getVersion());
            }

            UserDTO userDTO = userMap.get(autoLabelModelService.getCreateUserId());
            if (Objects.nonNull(userDTO)) {
                UserSmallDTO userSmallDTO = new UserSmallDTO();
                userSmallDTO.setUsername(userDTO.getUsername());
                userSmallDTO.setNickName(userSmallDTO.getNickName());
                autoLabelModelServiceVO.setCreateUser(userSmallDTO);
            }

            modelServiceVOS.add(autoLabelModelServiceVO);
        }
        return modelServiceVOS;
    }

    private Map<Long, UserDTO> mapUser(List<AutoLabelModelService> autoLabelModelServices) {
        List<Long> userIds = autoLabelModelServices.stream()
                .map(AutoLabelModelService::getCreateUserId)
                .collect(Collectors.toList());

        DataResponseBody<List<UserDTO>> modelBranchResponse = adminClient.getUserList(userIds);
        List<UserDTO> userDTOList = Lists.newArrayList();
        if (modelBranchResponse.succeed()) {
            userDTOList = modelBranchResponse.getData();
        }

        Map<Long, UserDTO> userMap = new HashMap<>();

        if (CollectionUtils.isEmpty(userDTOList)) {
            return userMap;
        }
        userMap = userDTOList.stream()
                .collect(Collectors.toMap(UserDTO::getId, Function.identity()));
        return userMap;
    }

    private Map<Long, String> mapAlgorithm(List<AutoLabelModelService> autoLabelModelServices) {
        Set<Long> algorithmIds = autoLabelModelServices.stream()
                .map(AutoLabelModelService::getAlgorithmId)
                .collect(Collectors.toSet());

        TrainAlgorithmSelectAllBatchIdDTO trainAlgorithmSelectAllBatchIdDTO = new TrainAlgorithmSelectAllBatchIdDTO();
        trainAlgorithmSelectAllBatchIdDTO.setIds(algorithmIds);
        DataResponseBody<List<TrainAlgorithmQureyVO>> algorithmResponse = algorithmClient.selectAllBatchIds(trainAlgorithmSelectAllBatchIdDTO);
        List<TrainAlgorithmQureyVO> algorithmQureyVOS = Lists.newArrayList();
        if (algorithmResponse.succeed()) {
            algorithmQureyVOS = algorithmResponse.getData();
        }

        Map<Long, String> algorithmNameMap = new HashMap<>();

        if (CollectionUtils.isEmpty(algorithmQureyVOS)) {
            return algorithmNameMap;
        }
        algorithmNameMap = algorithmQureyVOS.stream()
                .collect(Collectors.toMap(TrainAlgorithmQureyVO::getId, TrainAlgorithmQureyVO::getAlgorithmName));
        return algorithmNameMap;
    }

    private Map<Long, String> mapImage(List<AutoLabelModelService> autoLabelModelServices) {
        List<Long> imageIds = autoLabelModelServices.stream()
                .map(AutoLabelModelService::getImageId)
                .collect(Collectors.toList());

        PtImageIdsDTO ptImageIdsDTO = new PtImageIdsDTO();
        ptImageIdsDTO.setIds(imageIds);
        DataResponseBody<List<PtImageVO>> modelBranchResponse = imageClient.listByIds(ptImageIdsDTO);
        List<PtImageVO> imageVOList = Lists.newArrayList();
        if (modelBranchResponse.succeed()) {
            imageVOList = modelBranchResponse.getData();
        }

        Map<Long, String> imageNameMap = new HashMap<>();

        if (CollectionUtils.isEmpty(imageVOList)) {
            return imageNameMap;
        }
        imageNameMap = imageVOList.stream()
                .collect(Collectors.toMap(PtImageVO::getId, ptImageVO -> ptImageVO.getName() + ":" + ptImageVO.getTag()));
        return imageNameMap;
    }

    private Map<Long, PtModelBranchQueryVO> mapModeInfo(List<AutoLabelModelService> autoLabelModelServices) {
        Map<Long, PtModelBranchQueryVO> modelBranchQueryVOMap = new HashMap<>();
        List<Long> modelBranchIds = autoLabelModelServices.stream()
                .filter(autoLabelModelService -> ObjectUtil.isNotNull(autoLabelModelService.getModelBranchId()))
                .map(AutoLabelModelService::getModelBranchId)
                .collect(Collectors.toList());
        if (CollectionUtil.isEmpty(modelBranchIds)) {
            return modelBranchQueryVOMap;
        }
        PtModelBranchQueryByIdsDTO ptModelBranchQueryByIdsDTO = new PtModelBranchQueryByIdsDTO();
        ptModelBranchQueryByIdsDTO.setIds(modelBranchIds);
        DataResponseBody<List<PtModelBranchQueryVO>> modelBranchResponse = modelClient.listByBranchIds(ptModelBranchQueryByIdsDTO);
        List<PtModelBranchQueryVO> ptModelBranchQueryVOList = Lists.newArrayList();
        if (modelBranchResponse.succeed()) {
            ptModelBranchQueryVOList = modelBranchResponse.getData();
        }
        if (CollectionUtils.isEmpty(ptModelBranchQueryVOList)) {
            return modelBranchQueryVOMap;
        }
        modelBranchQueryVOMap = ptModelBranchQueryVOList.stream()
                .collect(Collectors.toMap(PtModelBranchQueryVO::getId, Function.identity()));
        return modelBranchQueryVOMap;
    }

    private void deleteK8sDeployment(Long modelServiceId) {
        AutoLabelModelService autoLabelModelService = baseMapper.selectById(modelServiceId);
        String namespace = k8sNameTool.getNamespace(autoLabelModelService.getCreateUserId());
        String resourceName = k8sNameTool.generateResourceName(BizEnum.DATA, autoLabelModelService.getId().toString());
        modelServiceApi.delete(namespace, resourceName);
    }

    @Override
    public boolean deploymentCallback(DataK8sDeploymentCallbackCreateDTO req) {
        //避免跟状态机状态冲突，sleep 2s
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        String modelServiceIdStr = k8sNameTool.getResourceInfoFromResourceName(BizEnum.DATA, req.getResourceName());
        if (org.dubhe.biz.base.utils.StringUtils.isBlank(modelServiceIdStr)) {
            LogUtil.warn(LogEnum.BIZ_DATASET, "Cannot find modelServiceIdStr! Request: {}", req.toString());
            return false;
        }
        Long modelServiceId = Long.valueOf(modelServiceIdStr);
        AutoLabelModelService modelService = baseMapper.selectById(modelServiceId);
        //只需要监听启动中状态
        if(!modelService.getStatus().equals(AutoLabelModelServiceStatusEnum.STARTING.getValue())){
            return true;
        }
        if(req.getReplicas().intValue()==0){
            return true;
        }

        List<BizPod> bizPodList = listPod(modelService);
        int  pendingCount = countPodPhaseCount(bizPodList);;

        if (req.getReplicas().equals(req.getReadyReplicas())) {
            StateMachineUtil.stateChange(new StateChangeDTO() {{
                setObjectParam(new Object[]{modelService.getId()});
                setEventMethodName(ModelStateMachineConstant.START_MODEL_SERVICE_FINISH);
                setStateMachineType(ModelStateMachineConstant.MODEL_STATE_MACHINE);
            }});
        } else if (pendingCount > 0) {
            StateMachineUtil.stateChange(new StateChangeDTO() {{
                setObjectParam(new Object[]{modelService.getId()});
                setEventMethodName(ModelStateMachineConstant.START_MODEL_SERVICE);
                setStateMachineType(ModelStateMachineConstant.MODEL_STATE_MACHINE);
            }});
        } else {
            StateMachineUtil.stateChange(new StateChangeDTO() {{
                setObjectParam(new Object[]{modelService.getId()});
                setEventMethodName(ModelStateMachineConstant.START_MODEL_SERVICE_FAIL);
                setStateMachineType(ModelStateMachineConstant.MODEL_STATE_MACHINE);
            }});
        }
        LogUtil.info(LogEnum.BIZ_DATASET, "req{} bizPod: {}", req.toString(),bizPodList);
        return true;
    }

    private Integer countPodPhaseCount(List<BizPod> bizPodList) {
        int pendingCount=0;
        if (CollectionUtils.isEmpty(bizPodList)) {
            return pendingCount;
        }

        for (BizPod bizPod : bizPodList) {
            if (bizPod.getPhase().equals(PodPhaseEnum.RUNNING.getPhase())) {
                boolean ready=true;
                for(BizContainerStatus bizContainerStatus :bizPod.getContainerStatuses()){
                    if(!bizContainerStatus.getReady()){
                        ready=false;
                        break;
                    }
                }
                if(!ready){
                    pendingCount++;
                }

            } else if (bizPod.getPhase().equals(PodPhaseEnum.PENDING.getPhase())) {
                pendingCount++;
            }
        }
        return pendingCount;
    }

    @Override
    public void startService(Long modelServiceId) {
        //重新启动 删除已有启动失败deployment
        deleteK8sDeployment(modelServiceId);
        AutoLabelModelService modelService = baseMapper.selectById(modelServiceId);
        String taskIdentify = resourceCache.getTaskIdentify(modelServiceId, modelService.getName(), autoLabelModelServiceIdPrefix);
        createK8sDeployment(modelServiceId, taskIdentify);
        StateMachineUtil.stateChange(new StateChangeDTO() {{
            setObjectParam(new Object[]{modelService.getId()});
            setEventMethodName(ModelStateMachineConstant.START_MODEL_SERVICE);
            setStateMachineType(ModelStateMachineConstant.MODEL_STATE_MACHINE);
        }});
    }

    @Override
    public void stopService(Long modelServiceId) {
        deleteK8sDeployment(modelServiceId);
        StateMachineUtil.stateChange(new StateChangeDTO() {{
            setObjectParam(new Object[]{modelServiceId});
            setEventMethodName(ModelStateMachineConstant.STOP_MODEL_SERVICE_FINISH);
            setStateMachineType(ModelStateMachineConstant.MODEL_STATE_MACHINE);
        }});
    }

    @Override
    public List<AutoLabelModelServiceVO> runningModelList(Integer modelType) {
        QueryWrapper<AutoLabelModelService> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(AutoLabelModelService::getModelType, modelType)
                .eq(AutoLabelModelService::getStatus, AutoLabelModelServiceStatusEnum.RUNNING.getValue());
        //获取用户信息
        UserContext user = userContextService.getCurUser();
        if (!BaseService.isAdmin(user)) {
            queryWrapper.lambda().eq(AutoLabelModelService::getCreateUserId, user.getId());
        }
        List<AutoLabelModelService> autoLabelModelServices = baseMapper.selectList(queryWrapper);
        if(CollectionUtils.isEmpty(autoLabelModelServices)){
            throw new BusinessException(ErrorEnum.MODEL_TYPE_NOT_EXIST);
        }
        return buildModelServiceVO(autoLabelModelServices);
    }

    private List<BizPod> listPod(AutoLabelModelService autoLabelModelService) {
        String namespace = k8sNameTool.getNamespace(autoLabelModelService.getCreateUserId());
        String resourceName = k8sNameTool.generateResourceName(BizEnum.DATA, autoLabelModelService.getId().toString());
        List<BizPod> bizPodList = podApi.getListByResourceName(namespace, resourceName);

        return bizPodList;
    }

    @Override
    public List<AutoLabelModelServicePodVO> listPods(Long modelServiceId) {
        List<AutoLabelModelServicePodVO>  autoLabelModelServicePodVOList =Lists.newArrayList();
        AutoLabelModelService autoLabelModelService=baseMapper.selectById(modelServiceId);
        List<BizPod> bizPodList = listPod(autoLabelModelService);
        if(CollectionUtils.isEmpty(bizPodList)){
            return autoLabelModelServicePodVOList;
        }
        String namespace = k8sNameTool.generateNamespace(autoLabelModelService.getCreateUserId());

        for(BizPod bizPod :bizPodList){
            AutoLabelModelServicePodVO autoLabelModelServicePodVO = AutoLabelModelServicePodVO.builder()
                    .namespace(namespace)
                    .podName(bizPod.getName())
                    .build();
            autoLabelModelServicePodVOList.add(autoLabelModelServicePodVO);
        }
        return autoLabelModelServicePodVOList;
    }

    @Override
    public AutoLabelModelService getOneById(Long id) {
        return baseMapper.selectById(id);
    }

}
