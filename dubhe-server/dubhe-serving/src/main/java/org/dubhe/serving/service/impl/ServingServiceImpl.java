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

package org.dubhe.serving.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.grpc.ManagedChannel;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections4.CollectionUtils;
import org.dubhe.biz.base.constant.NumberConstant;
import org.dubhe.biz.base.constant.StringConstant;
import org.dubhe.biz.base.constant.SymbolConstant;
import org.dubhe.biz.base.context.UserContext;
import org.dubhe.biz.base.dto.PtImageQueryUrlDTO;
import org.dubhe.biz.base.dto.PtModelBranchQueryByIdDTO;
import org.dubhe.biz.base.dto.PtModelInfoQueryByIdDTO;
import org.dubhe.biz.base.dto.PtModelStatusQueryDTO;
import org.dubhe.biz.base.dto.TrainAlgorithmSelectByIdDTO;
import org.dubhe.biz.base.enums.BizEnum;
import org.dubhe.biz.base.enums.DatasetTypeEnum;
import org.dubhe.biz.base.enums.ImageTypeEnum;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.service.UserContextService;
import org.dubhe.biz.base.utils.DateUtil;
import org.dubhe.biz.base.utils.ReflectionUtils;
import org.dubhe.biz.base.utils.StringUtils;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.base.vo.PtModelBranchQueryVO;
import org.dubhe.biz.base.vo.PtModelInfoQueryVO;
import org.dubhe.biz.base.vo.TrainAlgorithmQureyVO;
import org.dubhe.biz.db.utils.PageUtil;
import org.dubhe.biz.db.utils.WrapperHelp;
import org.dubhe.biz.file.api.FileStoreApi;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.permission.annotation.DataPermissionMethod;
import org.dubhe.biz.permission.base.BaseService;
import org.dubhe.biz.redis.utils.RedisUtils;
import org.dubhe.k8s.api.MetricsApi;
import org.dubhe.k8s.api.ModelServingApi;
import org.dubhe.k8s.api.NodeApi;
import org.dubhe.k8s.cache.ResourceCache;
import org.dubhe.k8s.domain.dto.PodQueryDTO;
import org.dubhe.k8s.domain.resource.BizServicePort;
import org.dubhe.k8s.domain.vo.ModelServingVO;
import org.dubhe.k8s.domain.vo.PodVO;
import org.dubhe.k8s.domain.vo.PtPodsVO;
import org.dubhe.k8s.enums.PodPhaseEnum;
import org.dubhe.k8s.enums.WatcherActionEnum;
import org.dubhe.k8s.service.PodService;
import org.dubhe.k8s.utils.K8sNameTool;
import org.dubhe.recycle.config.RecycleConfig;
import org.dubhe.recycle.domain.dto.RecycleCreateDTO;
import org.dubhe.recycle.domain.dto.RecycleDetailCreateDTO;
import org.dubhe.recycle.enums.RecycleModuleEnum;
import org.dubhe.recycle.enums.RecycleResourceEnum;
import org.dubhe.recycle.enums.RecycleTypeEnum;
import org.dubhe.recycle.service.RecycleService;
import org.dubhe.recycle.utils.RecycleTool;
import org.dubhe.serving.client.AlgorithmClient;
import org.dubhe.serving.client.ImageClient;
import org.dubhe.serving.client.ModelBranchClient;
import org.dubhe.serving.client.ModelInfoClient;
import org.dubhe.serving.constant.ServingConstant;
import org.dubhe.serving.dao.ServingInfoMapper;
import org.dubhe.serving.dao.ServingModelConfigMapper;
import org.dubhe.serving.domain.dto.PredictParamDTO;
import org.dubhe.serving.domain.dto.ServingInfoCreateDTO;
import org.dubhe.serving.domain.dto.ServingInfoDeleteDTO;
import org.dubhe.serving.domain.dto.ServingInfoDetailDTO;
import org.dubhe.serving.domain.dto.ServingInfoQueryDTO;
import org.dubhe.serving.domain.dto.ServingInfoUpdateDTO;
import org.dubhe.serving.domain.dto.ServingK8sDeploymentCallbackCreateDTO;
import org.dubhe.serving.domain.dto.ServingK8sPodCallbackCreateDTO;
import org.dubhe.serving.domain.dto.ServingModelConfigDTO;
import org.dubhe.serving.domain.dto.ServingStartDTO;
import org.dubhe.serving.domain.dto.ServingStopDTO;
import org.dubhe.serving.domain.entity.DataInfo;
import org.dubhe.serving.domain.entity.ServingInfo;
import org.dubhe.serving.domain.entity.ServingModelConfig;
import org.dubhe.serving.domain.vo.PredictParamVO;
import org.dubhe.serving.domain.vo.ServingConfigMetricsVO;
import org.dubhe.serving.domain.vo.ServingInfoCreateVO;
import org.dubhe.serving.domain.vo.ServingInfoDeleteVO;
import org.dubhe.serving.domain.vo.ServingInfoDetailVO;
import org.dubhe.serving.domain.vo.ServingInfoQueryVO;
import org.dubhe.serving.domain.vo.ServingInfoUpdateVO;
import org.dubhe.serving.domain.vo.ServingMetricsVO;
import org.dubhe.serving.domain.vo.ServingModelConfigVO;
import org.dubhe.serving.domain.vo.ServingPodMetricsVO;
import org.dubhe.serving.domain.vo.ServingStartVO;
import org.dubhe.serving.domain.vo.ServingStopVO;
import org.dubhe.serving.enums.ServingErrorEnum;
import org.dubhe.serving.enums.ServingRouteEventEnum;
import org.dubhe.serving.enums.ServingStatusEnum;
import org.dubhe.serving.enums.ServingTypeEnum;
import org.dubhe.serving.service.ServingLuaScriptService;
import org.dubhe.serving.service.ServingModelConfigService;
import org.dubhe.serving.service.ServingService;
import org.dubhe.serving.task.DeployServingAsyncTask;
import org.dubhe.serving.utils.GrpcClient;
import org.dubhe.serving.utils.ServingStatusDetailDescUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @description ??????????????????
 * @date 2020-08-25
 */
@Service
public class ServingServiceImpl implements ServingService {

    @Value("${serving.gateway.postfixUrl}")
    private String GATEWAY_URI_POSTFIX;
    @Value("${k8s.pod.metrics.grafanaUrl}")
    private String k8sPodMetricsGrafanaUrl;
    @Resource
    private ServingInfoMapper servingInfoMapper;
    @Resource
    private ServingModelConfigService servingModelConfigService;
    @Resource
    private ServingModelConfigMapper servingModelConfigMapper;
    @Resource
    private ModelInfoClient modelInfoClient;
    @Resource
    private ModelBranchClient modelBranchClient;
    @Resource
    private DeployServingAsyncTask deployServingAsyncTask;
    @Resource
    private ServingLuaScriptService servingLuaScriptService;
    @Resource
    private PodService podService;
    @Resource
    private MetricsApi metricsApi;
    @Resource(name = "hostFileStoreApiImpl")
    private FileStoreApi fileStoreApi;
    @Resource
    private K8sNameTool k8sNameTool;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserContextService userContextService;
    @Resource
    private GrpcClient grpcClient;
    @Resource
    private ImageClient imageClient;
    @Resource
    private AlgorithmClient algorithmClient;
    @Resource
    private RecycleConfig recycleConfig;
    @Resource
    private RecycleService recycleService;
    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private ResourceCache resourceCache;
    @Value("Task:Serving:" + "${spring.profiles.active}_serving_id_")
    private String servingIdPrefix;
    @Resource
    private ModelServingApi modelServingApi;
    @Resource
    private NodeApi nodeApi;

    private final static List<String> FILE_NAMES;

    static {
        FILE_NAMES = ReflectionUtils.getFieldNames(ServingInfoQueryVO.class);
    }

    /**
     * ??????????????????
     *
     * @param servingInfoQueryDTO ??????????????????
     * @return Map<String, Object>????????????????????????
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public Map<String, Object> query(ServingInfoQueryDTO servingInfoQueryDTO) {
        String name = servingInfoQueryDTO.getName();
        // ???????????????id????????????
        if (StringUtils.isNotBlank(name)) {
            // ????????????
            if (StringConstant.PATTERN_NUM.matcher(name).matches()) {
                servingInfoQueryDTO.setId(Long.parseLong(name));
                servingInfoQueryDTO.setName(null);
                Map<String, Object> map = queryServing(servingInfoQueryDTO);
                if (((List<ServingInfoQueryVO>) map.get(StringConstant.RESULT)).size() > NumberConstant.NUMBER_0) {
                    return map;
                } else {
                    servingInfoQueryDTO.setId(null);
                    servingInfoQueryDTO.setName(name);
                }
            }
        }
        return queryServing(servingInfoQueryDTO);
    }

    /**
     * ????????????????????????
     *
     * @param servingInfoQueryDTO ????????????
     * @return Map<String, Object> ????????????
     */
    public Map<String, Object> queryServing(ServingInfoQueryDTO servingInfoQueryDTO) {
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("???????????????????????????");
        }
        LogUtil.info(LogEnum.SERVING, "User {} queried online service list, with the query {}", user.getUsername(),
                JSONObject.toJSONString(servingInfoQueryDTO));
        QueryWrapper<ServingInfo> wrapper = WrapperHelp.getWrapper(servingInfoQueryDTO);
        if (!BaseService.isAdmin(user)) {
            wrapper.eq("create_user_id", user.getId());
        }
        Page page = new Page(
                null == servingInfoQueryDTO.getCurrent() ? NumberConstant.NUMBER_1 : servingInfoQueryDTO.getCurrent(),
                null == servingInfoQueryDTO.getSize() ? NumberConstant.NUMBER_10 : servingInfoQueryDTO.getSize());
        try {
            // ??????????????????????????????????????????????????????????????????????????????
            String column = servingInfoQueryDTO.getSort() != null && FILE_NAMES.contains(servingInfoQueryDTO.getSort())
                    ? StringUtils.humpToLine(servingInfoQueryDTO.getSort())
                    : "update_time";
            // ????????????
            boolean isAsc = !StringUtils.isBlank(servingInfoQueryDTO.getOrder())
                    && !StringUtils.equals(servingInfoQueryDTO.getOrder(), StringConstant.SORT_DESC);
            wrapper.orderBy(true, isAsc, column);
        } catch (Exception e) {
            LogUtil.error(LogEnum.SERVING,
                    "User queried online service with an exception, request info: {}???exception info: {}",
                    JSONObject.toJSONString(servingInfoQueryDTO), e);
            throw new BusinessException(ServingErrorEnum.INTERNAL_SERVER_ERROR);
        }
        IPage<ServingInfo> servingInfos = servingInfoMapper.selectPage(page, wrapper);
        List<ServingInfoQueryVO> queryList = servingInfos.getRecords().stream().map(servingInfo -> {
            ServingInfoQueryVO servingInfoQueryVO = new ServingInfoQueryVO();
            BeanUtils.copyProperties(servingInfo, servingInfoQueryVO);
            servingInfoQueryVO.setUrl(GATEWAY_URI_POSTFIX+"/"+servingInfo.getUuid());
            Map<String, String> statistics = servingLuaScriptService.countCallsByServingInfoId(servingInfo.getId());
            servingInfoQueryVO.setTotalNum(statistics.getOrDefault("callCount", SymbolConstant.ZERO));
            servingInfoQueryVO.setFailNum(statistics.getOrDefault("failedCount", SymbolConstant.ZERO));
            return servingInfoQueryVO;
        }).collect(Collectors.toList());
        LogUtil.info(LogEnum.SERVING, "User {} queried online service list, online service count = {}",
                user.getUsername(), queryList.size());
        return PageUtil.toPage(page, queryList);
    }

    /**
     * ????????????
     *
     * @param servingInfoCreateDTO ??????????????????
     * @return ServingInfoCreateVO ????????????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public ServingInfoCreateVO create(ServingInfoCreateDTO servingInfoCreateDTO) {
        UserContext user = userContextService.getCurUser();
        // ????????????
        if (user == null) {
            throw new BusinessException("???????????????????????????");
        }
        checkNameExist(servingInfoCreateDTO.getName(), user.getId());
        // gRPC?????????????????????
        if (NumberConstant.NUMBER_1 == servingInfoCreateDTO.getType()
                && servingInfoCreateDTO.getModelConfigList().size() > NumberConstant.NUMBER_1) {
            throw new BusinessException(ServingErrorEnum.GRPC_PROTOCOL_NOT_SUPPORTED);
        }
        ServingInfo servingInfo = buildServingInfo(servingInfoCreateDTO);
        List<ServingModelConfig> modelConfigList = insertServing(servingInfoCreateDTO, user, servingInfo);
        // ??????????????????
        String taskIdentify = resourceCache.getTaskIdentify(servingInfo.getId(), servingInfo.getName(), servingIdPrefix);
        deployServingAsyncTask.deployServing(user, servingInfo, modelConfigList, taskIdentify);
        return new ServingInfoCreateVO(servingInfo.getId(), servingInfo.getStatus());
    }

    /**
     * ???????????????????????????
     *
     * @param servingInfoCreateDTO ??????????????????????????????
     * @param user                 ????????????
     * @param servingInfo          ????????????????????????
     * @return List<ServingModelConfig> ????????????????????????????????????
     */
    @Transactional(rollbackFor = Exception.class)
    public List<ServingModelConfig> insertServing(ServingInfoCreateDTO servingInfoCreateDTO, UserContext user,
                                                  ServingInfo servingInfo) {
        int result = servingInfoMapper.insert(servingInfo);
        if (result < NumberConstant.NUMBER_1) {
            LogUtil.error(LogEnum.SERVING, "User {} failed saving service into into the database. Service name: {}",
                    user.getUsername(), servingInfoCreateDTO.getName());
            throw new BusinessException(ServingErrorEnum.INTERNAL_SERVER_ERROR);
        }
        return saveModelConfig(servingInfoCreateDTO.getModelConfigList(), servingInfo);
    }

    /**
     * ????????????????????????
     *
     * @param name   ????????????
     * @param userId ??????ID
     */
    public void checkNameExist(String name, Long userId) {
        LambdaQueryWrapper<ServingInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ServingInfo::getName, name);
        wrapper.eq(ServingInfo::getCreateUserId, userId);
        int count = servingInfoMapper.selectCount(wrapper);
        if (count > NumberConstant.NUMBER_0) {
            throw new BusinessException(ServingErrorEnum.SERVING_NAME_EXIST);
        }
    }

    /**
     * ??????????????????
     *
     * @param servingInfoCreateDTO ??????????????????
     * @return ServingInfo ????????????
     */
    public ServingInfo buildServingInfo(ServingInfoCreateDTO servingInfoCreateDTO) {
        ServingInfo servingInfo = new ServingInfo();
        BeanUtils.copyProperties(servingInfoCreateDTO, servingInfo);
        servingInfo.setUuid(StringUtils.getUUID());
        servingInfo.setStatus(ServingStatusEnum.IN_DEPLOYMENT.getStatus());
        int totalNode = servingInfoCreateDTO.getModelConfigList().stream()
                .mapToInt(ServingModelConfigDTO::getResourcesPoolNode).sum();
        servingInfo.setTotalNode(totalNode);
        return servingInfo;
    }

    /**
     * ??????????????????
     *
     * @param servingModelConfig ????????????
     */
    public void checkResourceType(ServingModelConfig servingModelConfig) {
        // oneflow ????????????cpu
        if (servingModelConfig.getFrameType() == NumberConstant.NUMBER_1
                && servingModelConfig.getResourcesPoolType() == NumberConstant.NUMBER_0) {
            throw new BusinessException(ServingErrorEnum.CPU_NOT_SUPPORTED_BY_ONEFLOW);
        }
    }

    /**
     * ??????????????????????????????
     *
     * @param modelConfigDTOList ??????????????????
     * @param servingInfo        ????????????
     * @return List<ServingModelConfig> ????????????????????????
     */
    List<ServingModelConfig> saveModelConfig(List<ServingModelConfigDTO> modelConfigDTOList, ServingInfo servingInfo) {
        List<ServingModelConfig> list = new ArrayList<>();
        if (CollectionUtils.isEmpty(modelConfigDTOList)) {
            throw new BusinessException(ServingErrorEnum.MODEL_CONFIG_NOT_EXIST);
        }
        // ??????4?????????????????????????????????id
        String deployId = StringUtils.getTimestamp();

        PtImageQueryUrlDTO ptImageQueryUrlDTO = new PtImageQueryUrlDTO();
        List<Integer> servingImageType = new ArrayList(){{
            add(ImageTypeEnum.SERVING.getType());
        }};
        ptImageQueryUrlDTO.setImageTypes(servingImageType);

        for (ServingModelConfigDTO servingModelConfigDTO : modelConfigDTOList) {
            ServingModelConfig servingModelConfig = new ServingModelConfig();
            BeanUtils.copyProperties(servingModelConfigDTO, servingModelConfig);
            servingModelConfig.setServingId(servingInfo.getId());
            servingModelConfig.setDeployId(deployId);
            PtModelInfoQueryVO ptModelInfoQueryVO = getPtModelInfo(servingModelConfig.getModelId());
            // ????????????
            if (ptModelInfoQueryVO.getFrameType() > NumberConstant.NUMBER_4) {
                throw new BusinessException(ServingErrorEnum.MODEL_FRAME_TYPE_NOT_SUPPORTED);
            }
            servingModelConfig.setFrameType(ptModelInfoQueryVO.getFrameType());
            servingModelConfig.setModelAddress(ptModelInfoQueryVO.getModelAddress());
            servingModelConfig.setModelName(ptModelInfoQueryVO.getName());
            checkResourceType(servingModelConfig);
            if (NumberConstant.NUMBER_0 == servingModelConfig.getModelResource()) {
                PtModelBranchQueryVO ptModelBranchQueryVO = getModelBranch(servingModelConfig.getModelBranchId());
                servingModelConfig.setModelAddress(ptModelBranchQueryVO.getModelAddress());
                servingModelConfig.setModelVersion(ptModelBranchQueryVO.getVersion());
            }

            ptImageQueryUrlDTO.setImageName(servingModelConfigDTO.getImageName());
            ptImageQueryUrlDTO.setImageTag(servingModelConfigDTO.getImageTag());
            DataResponseBody<String> dataResponseBody = imageClient.getImageUrl(ptImageQueryUrlDTO);
            if (!dataResponseBody.succeed()) {
                throw new BusinessException(ServingErrorEnum.CALL_IMAGE_SERVER_FAIL);
            }
            servingModelConfig
                    .setImage(dataResponseBody.getData());

            // ??????????????????????????????
            String path = k8sNameTool.getAbsolutePath(servingModelConfig.getModelAddress());
            if (!fileStoreApi.fileOrDirIsExist(path)) {
                throw new BusinessException(ServingErrorEnum.MODEL_FILE_NOT_EXIST);
            }
            // ????????????????????????????????????????????????????????????
            if (servingModelConfig.getUseScript()) {
                TrainAlgorithmQureyVO dataAlgorithm = getAlgorithm(servingModelConfig.getAlgorithmId());
                // ??????????????????????????????
                String scriptPath = k8sNameTool.getAbsolutePath(dataAlgorithm.getCodeDir());
                if (!fileStoreApi.fileOrDirIsExist(scriptPath)) {
                    throw new BusinessException(ServingErrorEnum.SCRIPT_NOT_EXIST);
                }
                servingModelConfig.setScriptPath(dataAlgorithm.getCodeDir());
            }

            if (servingModelConfigMapper.insert(servingModelConfig) < NumberConstant.NUMBER_1) {
                throw new BusinessException(ServingErrorEnum.INTERNAL_SERVER_ERROR);
            }
            list.add(servingModelConfig);
        }
        return list;
    }

    /**
     * ??????????????????
     *
     * @param algorithmId ??????id
     * @return ????????????
     */
    private TrainAlgorithmQureyVO getAlgorithm(Long algorithmId) {
        TrainAlgorithmSelectByIdDTO trainAlgorithmSelectByIdDTO = new TrainAlgorithmSelectByIdDTO();
        trainAlgorithmSelectByIdDTO.setId(algorithmId);
        DataResponseBody<TrainAlgorithmQureyVO> algorithmResponseBody = algorithmClient
                .selectById(trainAlgorithmSelectByIdDTO);
        ;
        if (!algorithmResponseBody.succeed()) {
            throw new BusinessException(ServingErrorEnum.CALL_ALGORITHM_SERVER_FAIL);
        }
        if (algorithmResponseBody.getData() == null) {
            throw new BusinessException(ServingErrorEnum.ALGORITHM_NOT_EXIST);
        }
        return algorithmResponseBody.getData();
    }

    /**
     * ??????????????????
     *
     * @param modelId ??????id
     * @return PtModelInfoQueryVO ????????????
     */
    private PtModelInfoQueryVO getPtModelInfo(Long modelId) {
        PtModelInfoQueryByIdDTO ptModelInfoQueryByIdDTO = new PtModelInfoQueryByIdDTO();
        ptModelInfoQueryByIdDTO.setId(modelId);
        DataResponseBody<PtModelInfoQueryVO> modelInfoPresetDataResponseBody = modelInfoClient
                .getByModelId(ptModelInfoQueryByIdDTO);
        PtModelInfoQueryVO ptModelInfoPresetQueryVO = null;
        if (modelInfoPresetDataResponseBody.succeed()) {
            ptModelInfoPresetQueryVO = modelInfoPresetDataResponseBody.getData();
        }
        if (ptModelInfoPresetQueryVO == null) {
            throw new BusinessException(ServingErrorEnum.MODEL_NOT_EXIST);
        }
        return ptModelInfoPresetQueryVO;
    }

    /**
     * ????????????????????????
     *
     * @param modelBranchId ????????????id
     * @return PtModelBranchQueryVO ??????????????????
     */
    public PtModelBranchQueryVO getModelBranch(Long modelBranchId) {
        PtModelBranchQueryByIdDTO ptModelBranchQueryByIdDTO = new PtModelBranchQueryByIdDTO();
        ptModelBranchQueryByIdDTO.setId(modelBranchId);
        DataResponseBody<PtModelBranchQueryVO> modelBranchQueryVODataResponseBody = modelBranchClient
                .getByBranchId(ptModelBranchQueryByIdDTO);
        PtModelBranchQueryVO ptModelBranchQueryVO = null;
        if (modelBranchQueryVODataResponseBody.succeed()) {
            ptModelBranchQueryVO = modelBranchQueryVODataResponseBody.getData();
        }
        if (ptModelBranchQueryVO == null) {
            throw new BusinessException(ServingErrorEnum.MODEL_NOT_EXIST);
        }
        return ptModelBranchQueryVO;
    }

    /**
     * ??????????????????
     *
     * @param servingInfoUpdateDTO ??????????????????
     * @return ServingInfoUpdateVO ????????????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public ServingInfoUpdateVO update(ServingInfoUpdateDTO servingInfoUpdateDTO) {
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("???????????????????????????");
        }
        ServingInfo servingInfo = checkServingInfoExist(servingInfoUpdateDTO.getId(), user.getId());
        checkRunningNode(servingInfo.getRunningNode());
        // ????????????????????????????????????????????????????????????
        if (!servingInfo.getName().equals(servingInfoUpdateDTO.getName())) {
            checkNameExist(servingInfoUpdateDTO.getName(), user.getId());
        }
        servingInfo.setName(servingInfoUpdateDTO.getName()).setDescription(servingInfoUpdateDTO.getDescription())
                .setType(servingInfoUpdateDTO.getType()).setStatus(ServingStatusEnum.IN_DEPLOYMENT.getStatus())
                .setUpdateTime(DateUtil.getCurrentTimestamp());
        servingInfo.setUuid(StringUtils.getUUID());
        int totalNode = servingInfoUpdateDTO.getModelConfigList().stream()
                .mapToInt(ServingModelConfigDTO::getResourcesPoolNode).sum();
        servingInfo.setTotalNode(totalNode);
        Set<Long> oldIds = servingModelConfigService.getIdsByServingId(servingInfoUpdateDTO.getId());
        // ?????????????????????????????????
        if (CollectionUtils.isNotEmpty(oldIds)) {
            List<ServingModelConfig> oldModelConfigList = servingModelConfigService.listByIds(oldIds);
            if (!servingModelConfigService.removeByIds(oldIds)) {
                LogUtil.error(LogEnum.SERVING,
                        "User {} modified online service model config but failed deleting online service model config. Model config ids={}",
                        user.getUsername(), oldIds);
                throw new BusinessException(ServingErrorEnum.INTERNAL_SERVER_ERROR);
            }
            servingInfo.setStatusDetail(SymbolConstant.BRACKETS);
            deployServingAsyncTask.deleteServing(servingInfo, oldModelConfigList);
            // ?????????????????????
            for (ServingModelConfig oldModelConfig : oldModelConfigList) {
                String recyclePath = k8sNameTool.getAbsolutePath(ServingConstant.ONLINE_ROOT_PATH + servingInfo.getCreateUserId() + File.separator + servingInfo.getId() + File.separator + oldModelConfig.getId());
                fileStoreApi.deleteDirOrFile(recyclePath);
            }

            // ??????????????????
            if (ServingTypeEnum.HTTP.getType().equals(servingInfo.getType())) {
                this.notifyUpdateServingRoute(Collections.emptyList(),
                        oldModelConfigList.stream().map(ServingModelConfig::getId).collect(Collectors.toList()));
            }
        }
        List<ServingModelConfig> modelConfigList = updateServing(servingInfoUpdateDTO, user, servingInfo);
        String taskIdentify = resourceCache.getTaskIdentify(servingInfo.getId(), servingInfo.getName(), servingIdPrefix);
        // ??????????????????
        deployServingAsyncTask.deployServing(user, servingInfo, modelConfigList, taskIdentify);
        return new ServingInfoUpdateVO(servingInfo.getId(), servingInfo.getStatus());
    }

    /**
     * ???????????????????????????
     *
     * @param servingInfoUpdateDTO ??????????????????
     * @param user                 ????????????
     * @param servingInfo          ??????????????????
     * @return List<ServingModelConfig> ????????????????????????
     */
    @Transactional(rollbackFor = Exception.class)
    public List<ServingModelConfig> updateServing(ServingInfoUpdateDTO servingInfoUpdateDTO, UserContext user,
                                                  ServingInfo servingInfo) {
        int result = servingInfoMapper.updateById(servingInfo);
        if (result < NumberConstant.NUMBER_1) {
            LogUtil.error(LogEnum.SERVING, "User {} failed deleting online service from the database, service id={}",
                    user.getUsername(), servingInfo.getId());
            throw new BusinessException(ServingErrorEnum.INTERNAL_SERVER_ERROR);
        }
        return saveModelConfig(servingInfoUpdateDTO.getModelConfigList(), servingInfo);
    }

    /**
     * ??????????????????????????????
     *
     * @param runningNode ???????????????
     */
    void checkRunningNode(Integer runningNode) {
        if (runningNode != null && runningNode > NumberConstant.NUMBER_0) {
            throw new BusinessException(ServingErrorEnum.OPERATION_NOT_ALLOWED);
        }
    }

    /**
     * ??????????????????????????????
     *
     * @param id     ??????id
     * @param userId ??????id
     * @return ServingInfo ??????????????????
     */
    ServingInfo checkServingInfoExist(Long id, Long userId) {
        ServingInfo servingInfo = servingInfoMapper.selectById(id);
        if (servingInfo == null) {
            throw new BusinessException(ServingErrorEnum.SERVING_INFO_ABSENT);
        } else {
            // ????????????????????????????????????????????????????????????????????????????????????
            if (!BaseService.isAdmin()) {
                if (!userId.equals(servingInfo.getCreateUserId())) {
                    throw new BusinessException(ServingErrorEnum.SERVING_INFO_ABSENT);
                }
            }
        }
        return servingInfo;
    }

    /**
     * ??????????????????
     *
     * @param servingInfoDeleteDTO ??????????????????
     * @return ServingInfoDeleteVO ?????????????????????id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServingInfoDeleteVO delete(ServingInfoDeleteDTO servingInfoDeleteDTO) {
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("???????????????????????????");
        }
        ServingInfo servingInfo = checkServingInfoExist(servingInfoDeleteDTO.getId(), user.getId());
        List<ServingModelConfig> modelConfigList = getModelConfigByServingId(servingInfo.getId());
        deployServingAsyncTask.deleteServing(servingInfo, modelConfigList);
        deleteServing(servingInfoDeleteDTO, user, servingInfo);
        String taskIdentify = (String) redisUtils.get(servingIdPrefix + String.valueOf(servingInfo.getId()));
        if (StringUtils.isNotEmpty(taskIdentify)) {
            redisUtils.del(taskIdentify, servingIdPrefix + String.valueOf(servingInfo.getId()));
        }
        Map<String, Object> map = new HashMap<>(NumberConstant.NUMBER_2);
        map.put("serving_id", servingInfo.getId());
        if (!servingModelConfigService.removeByMap(map)) {
            LogUtil.error(LogEnum.SERVING,
                    "User {} failed update online service in the database, service id={}, service name:{}",
                    user.getUsername(), servingInfoDeleteDTO.getId(), servingInfo.getName());
            throw new BusinessException(ServingErrorEnum.INTERNAL_SERVER_ERROR);
        }
        servingInfo.setStatusDetail(SymbolConstant.BRACKETS);
        // ??????????????????
        if (ServingTypeEnum.HTTP.getType().equals(servingInfo.getType())) {
            this.notifyUpdateServingRoute(Collections.emptyList(),
                    modelConfigList.stream().map(ServingModelConfig::getId).collect(Collectors.toList()));
        }
        //????????????????????????
        recycle(servingInfo, modelConfigList);
        return new ServingInfoDeleteVO(servingInfo.getId());
    }

    /**
     * ??????????????????????????????
     *
     * @param servingInfo ????????????
     * @param modelConfigList ??????????????????
     */
    private void recycle(ServingInfo servingInfo, List<ServingModelConfig> modelConfigList) {
        List<String> recyclePath = new ArrayList<>();
        List<String> modelConfigIds = new ArrayList<>();
        for (ServingModelConfig servingModelConfig : modelConfigList) {
            String path = k8sNameTool.getAbsolutePath(ServingConstant.ONLINE_ROOT_PATH + servingInfo.getCreateUserId() + File.separator + servingInfo.getId() + File.separator + servingModelConfig.getId());
            recyclePath.add(path);
            modelConfigIds.add(String.valueOf(servingModelConfig.getId()));
        }
        if (CollectionUtils.isNotEmpty(recyclePath) && CollectionUtils.isNotEmpty(modelConfigIds)) {
            createRecycleTask(servingInfo, String.join(SymbolConstant.COMMA, modelConfigIds), String.join(SymbolConstant.COMMA, recyclePath), true);
        }
    }

    /**
     * ???????????????????????????
     *
     * @param servingInfoDeleteDTO ????????????????????????
     * @param user                 ????????????
     * @param servingInfo          ??????????????????
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteServing(ServingInfoDeleteDTO servingInfoDeleteDTO, UserContext user, ServingInfo servingInfo) {
        int result = servingInfoMapper.deleteById(servingInfo.getId());
        if (result < NumberConstant.NUMBER_1) {
            LogUtil.error(LogEnum.SERVING,
                    "User {} failed deleting online service from the database, service id={}, service name:{}",
                    user.getUsername(), servingInfoDeleteDTO.getId(), servingInfo.getName());
            throw new BusinessException(ServingErrorEnum.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * ????????????????????????
     *
     * @param servingInfoDetailDTO ????????????????????????
     * @return ServingInfoDetailVO ??????????????????
     */
    @Override
    public ServingInfoDetailVO getDetail(ServingInfoDetailDTO servingInfoDetailDTO) {
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("???????????????????????????");
        }
        ServingInfo servingInfo = checkServingInfoExist(servingInfoDetailDTO.getId(), user.getId());
        Map<String, Object> map = new HashMap<>(NumberConstant.NUMBER_2);
        map.put("serving_id", servingInfo.getId());
        List<ServingModelConfig> servingModelConfigList = servingModelConfigService.listByMap(map);
        ServingInfoDetailVO servingInfoDetailVO = new ServingInfoDetailVO();
        BeanUtils.copyProperties(servingInfo, servingInfoDetailVO);
        List<ServingModelConfigVO> servingModelConfigVOList = new ArrayList<>();
        List<Long> configIdList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(servingModelConfigList)) {
            for (ServingModelConfig servingModelConfig : servingModelConfigList) {
                ServingModelConfigVO vo = new ServingModelConfigVO();
                BeanUtils.copyProperties(servingModelConfig, vo);
                // ??????????????????????????????id?????????????????????????????????
                if (servingModelConfig.getUseScript() && servingModelConfig.getAlgorithmId() != null) {
                    TrainAlgorithmQureyVO algorithmQueryVO = getAlgorithm(servingModelConfig.getAlgorithmId());
                    vo.setAlgorithmName(algorithmQueryVO.getAlgorithmName());
                }
                servingModelConfigVOList.add(vo);
                configIdList.add(servingModelConfig.getId());
            }
        }
        servingInfoDetailVO.setModelConfigList(servingModelConfigVOList);
        // ??????????????????
        Map<String, String> countCalls = servingLuaScriptService.countCalls(configIdList);
        servingInfoDetailVO.setTotalNum(countCalls.getOrDefault("callCount", SymbolConstant.ZERO));
        servingInfoDetailVO.setFailNum(countCalls.getOrDefault("failedCount", SymbolConstant.ZERO));
        return servingInfoDetailVO;
    }

    /**
     * ??????????????????
     *
     * @param servingStartDTO ??????????????????
     * @return ServingStartVO ?????????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServingStartVO start(ServingStartDTO servingStartDTO) {
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("???????????????????????????");
        }
        ServingInfo servingInfo = checkServingInfoExist(servingStartDTO.getId(), user.getId());
        checkRunningNode(servingInfo.getRunningNode());
        List<ServingModelConfig> modelConfigList = getModelConfigByServingId(servingInfo.getId());
        servingInfo.setUuid(StringUtils.getUUID());
        servingInfo.setStatus(ServingStatusEnum.IN_DEPLOYMENT.getStatus());
        //??????????????????????????????????????????
        servingInfo.setStatusDetail(SymbolConstant.BRACKETS);
        updateServingStart(user, servingInfo, modelConfigList);
        // ??????????????????
        String taskIdentify = resourceCache.getTaskIdentify(servingInfo.getId(), servingInfo.getName(), servingIdPrefix);
        deployServingAsyncTask.deployServing(user, servingInfo, modelConfigList, taskIdentify);
        return new ServingStartVO(servingInfo.getId(), servingInfo.getStatus());
    }

    /**
     * ???????????????????????????
     *
     * @param user        ????????????
     * @param servingInfo ??????????????????
     * @param modelConfigList ????????????????????????
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateServingStart(UserContext user, ServingInfo servingInfo, List<ServingModelConfig> modelConfigList) {
        int result = servingInfoMapper.updateById(servingInfo);
        if (result < NumberConstant.NUMBER_1) {
            LogUtil.error(LogEnum.SERVING, "User {} failed update online service in the database, service id={}",
                    user.getUsername(), servingInfo.getId());
            throw new BusinessException(ServingErrorEnum.INTERNAL_SERVER_ERROR);
        }
        for (ServingModelConfig servingModelConfig : modelConfigList) {
            servingModelConfig.setResourceInfo(SymbolConstant.BLANK);
            servingModelConfig.setUrl(SymbolConstant.BLANK);
            if (servingModelConfigMapper.updateById(servingModelConfig) < NumberConstant.NUMBER_1) {
                throw new BusinessException(ServingErrorEnum.DATABASE_ERROR);
            }
        }
    }

    /**
     * ??????????????????
     *
     * @param id    ????????????ID
     * @param url   ????????????
     * @param files ???????????????????????????
     * @return String ??????????????????
     */
    @Override
    public String predict(Long id, String url, MultipartFile[] files) {
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("???????????????????????????");
        }
        ServingInfo servingInfo = checkServingInfoExist(id, user.getId());
        // ????????????????????????????????????
        if (servingInfo.getRunningNode() == NumberConstant.NUMBER_0) {
            throw new BusinessException(ServingErrorEnum.SERVING_NOT_WORKING);
        }
        if (StringUtils.isBlank(url)) {
            throw new BusinessException(ServingErrorEnum.SERVING_NOT_WORKING);
        }
        // ??????????????????
        if (files == null || files.length == NumberConstant.NUMBER_0) {
            throw new BusinessException(ServingErrorEnum.PREDICT_DATA_EMPTY);
        }
        List<DataInfo> dataInfoList = new ArrayList<>();
        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename();
            // ?????????????????????
            if (fileName == null) {
                throw new BusinessException("?????????????????????");
            }
            String base64File;
            try {
                base64File = Base64.encodeBase64String(file.getBytes());
            } catch (Exception e) {
                throw new BusinessException(ServingErrorEnum.DATA_CONVERT_BASE64_FAIL);
            }
            if (StringUtils.isNotBlank(base64File)) {
                DataInfo dataInfo = new DataInfo();
                dataInfo.setDataName(fileName);
                dataInfo.setDataFile(base64File);
                dataInfoList.add(dataInfo);
            }
        }
        if (dataInfoList.isEmpty()) {
            throw new BusinessException(ServingErrorEnum.PREDICT_DATA_EMPTY);
        }
        ManagedChannel channel = grpcClient.getChannel(id, url);
        return GrpcClient.getResult(channel, dataInfoList).getJsonResult();
    }

    /**
     * ??????????????????
     *
     * @param servingStopDTO ??????????????????
     * @return ServingStopVO ?????????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServingStopVO stop(ServingStopDTO servingStopDTO) {
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("???????????????????????????");
        }
        ServingInfo servingInfo = checkServingInfoExist(servingStopDTO.getId(), user.getId());
        // ??????????????????????????????
        servingInfo.setStatus(ServingStatusEnum.STOP.getStatus());
        servingInfo.setRunningNode(NumberConstant.NUMBER_0);
        List<ServingModelConfig> modelConfigList = getModelConfigByServingId(servingInfo.getId());
        deployServingAsyncTask.deleteServing(servingInfo, modelConfigList);
        updateServingStop(user, servingInfo, modelConfigList);
        servingInfo.setStatusDetail(SymbolConstant.BRACKETS);
        // ??????????????????
        if (ServingTypeEnum.HTTP.getType().equals(servingInfo.getType())) {
            this.notifyUpdateServingRoute(Collections.emptyList(),
                    modelConfigList.stream().map(ServingModelConfig::getId).collect(Collectors.toList()));
        }
        return new ServingStopVO(servingInfo.getId(), servingInfo.getStatus());
    }

    /**
     * ???????????????????????????
     *
     * @param user            ????????????
     * @param servingInfo     ??????????????????
     * @param modelConfigList ??????????????????
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateServingStop(UserContext user, ServingInfo servingInfo, List<ServingModelConfig> modelConfigList) {
        int result = servingInfoMapper.updateById(servingInfo);
        if (result < NumberConstant.NUMBER_1) {
            LogUtil.error(LogEnum.SERVING,
                    "User {} failed stopping the online service and failed to update the service in the database. Service id={}, service name:{}, running node number:{}",
                    user.getUsername(), servingInfo.getId(), servingInfo.getName(), servingInfo.getRunningNode());
            throw new BusinessException(ServingErrorEnum.INTERNAL_SERVER_ERROR);
        }
        modelConfigList.forEach(servingModelConfig -> {
            servingModelConfig.setReadyReplicas(NumberConstant.NUMBER_0);
            servingModelConfigMapper.updateById(servingModelConfig);
        });
        if (ServingTypeEnum.GRPC.getType().equals(servingInfo.getType())) {
            GrpcClient.shutdownChannel(servingInfo.getId());
        }
    }

    /**
     * ??????????????????
     *
     * @param predictParamDTO ????????????????????????
     * @return PredictParamVO ????????????????????????
     */
    @Override
    public PredictParamVO getPredictParam(PredictParamDTO predictParamDTO) {
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("???????????????????????????");
        }
        ServingInfo servingInfo = checkServingInfoExist(predictParamDTO.getId(), user.getId());
        PredictParamVO predictParamVO = new PredictParamVO();
        LambdaQueryWrapper<ServingModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ServingModelConfig::getServingId, predictParamDTO.getId());
        List<ServingModelConfig> servingModelConfigList = servingModelConfigService.list(wrapper);
        if (CollectionUtils.isEmpty(servingModelConfigList)) {
            return predictParamVO;
        }
        // grpc??????
        if (ServingTypeEnum.GRPC.getType().equals(servingInfo.getType())) {
            predictParamVO.setRequestMethod("gRPC");
            String url = servingModelConfigList.get(0).getUrl();
            predictParamVO.setUrl(url);
            Map<String, String> inputs = new HashMap<>();
            inputs.put("DataRequest", "List<Data>");
            predictParamVO.setInputs(inputs);
            Map<String, Map<String, String>> other = new HashMap<>();
            Map<String, String> data = new HashMap<>();
            data.put("data_file", "String");
            data.put("data_name", "String");
            other.put("Data", data);
            Map<String, String> outputs = new HashMap<>();
            outputs.put("DataResponse", "String");
            predictParamVO.setOutputs(outputs);
            predictParamVO.setOther(other);
        } else if (ServingTypeEnum.HTTP.getType().equals(servingInfo.getType())) {
            String url = "http://" + GATEWAY_URI_POSTFIX
                    +"/"+ servingInfo.getUuid()+ ServingConstant.INFERENCE_INTERFACE_NAME;
            predictParamVO.setUrl(url);
            Map<String, String> inputs = new HashMap<>();
            inputs.put("files", "File");
            predictParamVO.setInputs(inputs);

            Map<String, String> outputs = new HashMap<>();
            outputs.put("label", "String");
            outputs.put("probability", "Float");
            predictParamVO.setOutputs(outputs);
            predictParamVO.setRequestMethod(SymbolConstant.POST);
        } else {
            throw new BusinessException(ServingErrorEnum.PROTOCOL_NOT_SUPPORTED);
        }
        return predictParamVO;
    }

    /**
     * ??????servingId????????????????????????
     *
     * @param servingId ????????????id
     * @return List<ServingModelConfig> ??????????????????
     */
    public List<ServingModelConfig> getModelConfigByServingId(Long servingId) {
        LambdaQueryWrapper<ServingModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ServingModelConfig::getServingId, servingId);
        return servingModelConfigService.list(wrapper);
    }

    /**
     * ????????????????????????
     *
     * @param id ??????????????????id
     * @return ServingMetricsVO ????????????????????????
     */
    @Override
    public ServingMetricsVO getMetricsDetail(Long id) {
        // ??????????????????????????????
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("???????????????????????????");
        }
        ServingInfoDetailDTO queryDTO = new ServingInfoDetailDTO();
        queryDTO.setId(id);
        ServingInfoDetailVO detail = this.getDetail(queryDTO);
        List<ServingConfigMetricsVO> configMetricsVOS = new ArrayList<>();
        for (ServingModelConfigVO servingModelConfigVO : detail.getModelConfigList()) {
            // ??????id??????????????????
            ServingConfigMetricsVO metricsTemp = new ServingConfigMetricsVO();
            BeanUtil.copyProperties(servingModelConfigVO, metricsTemp);
            // ????????????????????????
            List<PtPodsVO> podsVOS = metricsApi.getPodMetricsRealTime(
                    k8sNameTool.getNamespace(detail.getCreateUserId()),
                    k8sNameTool.generateResourceName(BizEnum.SERVING, servingModelConfigVO.getResourceInfo()));
            if (CollectionUtils.isNotEmpty(podsVOS)) {
                List<ServingPodMetricsVO> podMetricsVOS = podsVOS.stream().map(podsVO -> {
                    ServingPodMetricsVO servingPodMetricsVO = new ServingPodMetricsVO();
                    BeanUtil.copyProperties(podsVO, servingPodMetricsVO);
                    servingPodMetricsVO.setGrafanaUrl(k8sPodMetricsGrafanaUrl.concat(podsVO.getPodName()));
                    return servingPodMetricsVO;
                }).collect(Collectors.toList());
                metricsTemp.setPodList(podMetricsVOS);
            } else {
                metricsTemp.setPodList(Collections.emptyList());
            }
            // ??????????????????
            Map<String, String> statistics = servingLuaScriptService
                    .countCallsByServingConfigId(servingModelConfigVO.getId());
            metricsTemp.setTotalNum(statistics.getOrDefault("callCount", SymbolConstant.ZERO));
            metricsTemp.setFailNum(statistics.getOrDefault("failedCount", SymbolConstant.ZERO));
            configMetricsVOS.add(metricsTemp);
        }
        return new ServingMetricsVO().setId(id).setServingConfigList(configMetricsVOS);
    }

    /**
     * ???????????????????????????????????????
     *
     * @param saveIdList   ???????????????ID??????
     * @param deleteIdList ???????????????ID??????
     */
    @Override
    public void notifyUpdateServingRoute(List<Long> saveIdList, List<Long> deleteIdList) {
        String message = StringUtils.EMPTY;
        // ????????????????????????
        if (CollectionUtils.isNotEmpty(saveIdList)) {
            String idString = StringUtils.join(saveIdList.toArray(), SymbolConstant.COMMA);
            message = ServingRouteEventEnum.SAVE.getCode() + SymbolConstant.COLON + idString
                    + SymbolConstant.EVENT_SEPARATOR;
            LogUtil.info(LogEnum.SERVING, "Serving start success and seed notify {}", message);
        }
        // ????????????????????????
        if (CollectionUtils.isNotEmpty(deleteIdList)) {
            String idString = StringUtils.join(deleteIdList.toArray(), SymbolConstant.COMMA);
            message = message + ServingRouteEventEnum.DELETE.getCode() + SymbolConstant.COLON + idString;
            LogUtil.info(LogEnum.SERVING, "Serving stop success and seed notify {}", message);
        }
        if (StringUtils.isNotBlank(message)) {
            LogUtil.info(LogEnum.SERVING, "Start send message to stream with notify {}", message);
            StringRecord stringRecord = StreamRecords.string(Collections.singletonMap(ServingConstant.REDIS_GROUP, message))
                    .withStreamKey(ServingConstant.SERVING_STREAM);
            stringRedisTemplate.opsForStream().add(stringRecord);
        }
    }

    /**
     * ????????????????????????POD
     *
     * @param id ????????????id
     * @return List<PodVO> ??????POD??????
     */
    @Override
    public List<PodVO> getPods(Long id) {
        ServingModelConfig modelConfig = servingModelConfigService.getById(id);
        if (modelConfig == null) {
            return Collections.emptyList();
        }
        ServingInfo servingInfo = servingInfoMapper.selectById(modelConfig.getServingId());
        if (servingInfo == null) {
            throw new BusinessException(ServingErrorEnum.SERVING_INFO_ABSENT);
        }
        // ??????????????????????????????
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("???????????????????????????");
        }
        String namespace = k8sNameTool.getNamespace(servingInfo.getCreateUserId());
        List<PodVO> list = podService
                .getPods(new PodQueryDTO(namespace, k8sNameTool.generateResourceName(BizEnum.SERVING, modelConfig.getResourceInfo())));
        list.sort((pod1, pod2) -> {
            Integer pod1Index = Integer.parseInt(pod1.getDisplayName().substring(NumberConstant.NUMBER_3));
            Integer pod2Index = Integer.parseInt(pod2.getDisplayName().substring(NumberConstant.NUMBER_3));
            return pod1Index - pod2Index;
        });
        return list;
    }

    /**
     * ??????????????????
     *
     * @param req ??????????????????
     * @return boolean ????????????????????????
     */
    @Override
    public boolean servingDeploymentCallback(ServingK8sDeploymentCallbackCreateDTO req) {
        LogUtil.info(LogEnum.BIZ_K8S,"servingDeploymentCallback:{}", JSON.toJSONString(req));
        // ??????namespace???podName??????????????????
        String resourceInfo = k8sNameTool.getResourceInfoFromResourceName(BizEnum.SERVING, req.getResourceName());
        if (StringUtils.isBlank(resourceInfo)) {
            LogUtil.warn(LogEnum.SERVING, "Cannot find modelConfig ID! Request: {}", req.toString());
            return false;
        }
        String idStr = resourceInfo.substring(NumberConstant.NUMBER_4);
        ServingModelConfig servingModelConfig = servingModelConfigService.getById(Long.parseLong(idStr));
        if (Objects.isNull(servingModelConfig)) {
            LogUtil.warn(LogEnum.SERVING, "Cannot find modelConfig! Request: {}", req.toString());
            return false;
        }
        ServingInfo servingInfo = servingInfoMapper.selectById(servingModelConfig.getServingId());
        if (Objects.isNull(servingInfo)) {
            LogUtil.warn(LogEnum.SERVING, "Cannot find servingInfo! Request: {}", req.toString());
            return false;
        }
        // ?????????????????????????????????
        if (ServingStatusEnum.STOP.getStatus().equals(servingInfo.getStatus())) {
            return true;
        }
        // ????????????
        if (updateByCallback(req, servingModelConfig, servingInfo)) {
            return false;
        }
        // ????????????????????????
        if (WatcherActionEnum.ADDED.getAction().equals(req.getAction())
                && ServingTypeEnum.HTTP.getType().equals(servingInfo.getType())) {
            this.notifyUpdateServingRoute(Collections.singletonList(servingModelConfig.getId()),
                    Collections.emptyList());
        }
        // ????????????????????????
        if (WatcherActionEnum.DELETED.getAction().equals(req.getAction())
                && ServingTypeEnum.HTTP.getType().equals(servingInfo.getType())) {
            this.notifyUpdateServingRoute(Collections.emptyList(),
                    Collections.singletonList(servingModelConfig.getId()));
        }
        return true;
    }

    @Override
    public boolean servingPodCallback(int times, ServingK8sPodCallbackCreateDTO req) {
        // ??????namespace???podName??????????????????
        String resourceInfo = k8sNameTool.getResourceInfoFromResourceName(BizEnum.SERVING, req.getResourceName());
        if (StringUtils.isBlank(resourceInfo)) {
            LogUtil.warn(LogEnum.SERVING, "Cannot find modelConfig ID! Request: {}", Thread.currentThread(), times, req.toString());
            return false;
        }
        Long id = Long.parseLong(resourceInfo.substring(NumberConstant.NUMBER_4));
        ServingInfo servingInfo = servingInfoMapper.selectById(id);
        if (Objects.isNull(servingInfo)) {
            LogUtil.warn(LogEnum.SERVING, "Cannot find podServing! Request: {}", Thread.currentThread(), times, req.toString());
            return false;
        }
        //????????????????????????????????????????????????????????????????????????????????????
        String statusDetailKey = ServingStatusDetailDescUtil.getServingStatusDetailKey(ServingStatusDetailDescUtil.CONTAINER_INFORMATION, req.getPodName());
        //???????????????????????????delete????????????????????????????????????????????????????????????
        if (StringUtils.isEmpty(req.getMessages()) && !PodPhaseEnum.DELETED.getPhase().equals(req.getPhase())) {
            servingInfo.removeStatusDetail(statusDetailKey);
        } else {
            servingInfo.putStatusDetail(statusDetailKey, req.getMessages());
        }
        LogUtil.info(LogEnum.SERVING, "The callback serving message:{} ,req message:{}", servingInfo, req);
        return servingInfoMapper.updateStatusDetail(servingInfo.getId(), servingInfo.getStatusDetail()) < NumberConstant.NUMBER_1;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateByCallback(ServingK8sDeploymentCallbackCreateDTO req, ServingModelConfig servingModelConfig,
                                    ServingInfo servingInfo) {

        //??????url
        if (StringUtils.isEmpty(servingModelConfig.getUrl())){
            //??????
            ModelServingVO modelServingVO = modelServingApi.get(req.getNamespace(),req.getResourceName());
            if (ServingConstant.SUCCESS_CODE.equals(modelServingVO.getCode())) {
                // ??????pod?????????url??????????????????????????????
                List<BizServicePort> ports = modelServingVO.getBizService().getPorts();

                if (CollectionUtils.isNotEmpty(ports)) {
                    //????????????url
                    String url = "";
                    if (ports.get(NumberConstant.NUMBER_0).getNodePort() != null) {
                        url = nodeApi.getAvailableNodeIp() + ":" + ports.get(NumberConstant.NUMBER_0).getNodePort();
                    }
                    servingModelConfig.setUrl(url);
                }
            }
        }

        // ???????????????????????????????????????
        servingModelConfig.setReadyReplicas(req.getReadyReplicas());
        servingModelConfig.setResourceInfo(null);
        int result = servingModelConfigMapper.updateById(servingModelConfig);
        if (result < NumberConstant.NUMBER_1) {
            return true;
        }
        // ????????????????????????????????????
        ServingModelConfig another = servingModelConfigMapper.selectAnother(servingInfo.getId(),
                servingModelConfig.getId());
        if (Objects.nonNull(another)) {
            servingInfo.setRunningNode(req.getReadyReplicas() + another.getReadyReplicas());
        } else {
            servingInfo.setRunningNode(req.getReadyReplicas());
        }
        String uniqueName = ServingStatusDetailDescUtil.getUniqueName(servingModelConfig.getModelName(), servingModelConfig.getModelVersion());
        String statusDetailKey = ServingStatusDetailDescUtil.getServingStatusDetailKey(ServingStatusDetailDescUtil.CLOUD_SERVICE_UPDATE_EXCEPTION, uniqueName);

        servingInfo.removeStatusDetail(statusDetailKey);
        // ??????????????????????????????????????????0???????????????????????????
        if (ServingStatusEnum.WORKING.getStatus().equals(servingInfo.getStatus())
                && servingInfo.getRunningNode() == NumberConstant.NUMBER_0) {
            LogUtil.info(LogEnum.SERVING,
                    "Update to EXCEPTION status. The number of running node is {}. Current request: {}",
                    servingInfo.getRunningNode(), req);
            servingInfo.putStatusDetail(statusDetailKey, "????????????????????????0");
            servingInfo.setStatus(ServingStatusEnum.EXCEPTION.getStatus());
            // ??????????????????pod
            List<ServingModelConfig> deleteList = getModelConfigByServingId(servingInfo.getId());
            deployServingAsyncTask.deleteServing(servingInfo, deleteList);
        }
        if (servingInfo.getRunningNode() > NumberConstant.NUMBER_0) {
            LogUtil.info(LogEnum.SERVING,
                    "Update to WORKING status. The number of running node is {}, Current request: {}",
                    servingInfo.getRunningNode(), req);
            servingInfo.setStatus(ServingStatusEnum.WORKING.getStatus());
        }
        servingInfo.setUpdateTime(DateUtil.getCurrentTimestamp());
        return servingInfoMapper.updateById(servingInfo) < NumberConstant.NUMBER_1;
    }

    /**
     * ????????????????????????????????????
     *
     * @param servingId ????????????id
     * @return Map<String, List < ServingModelConfigVO>> ????????????????????????
     */
    @Override
    public Map<String, List<ServingModelConfigVO>> getRollbackList(Long servingId) {
        List<ServingModelConfig> servingModelConfigList = servingModelConfigMapper.getRollbackList(servingId);
        Map<String, List<ServingModelConfigVO>> map = new HashMap<>();
        if (CollectionUtils.isEmpty(servingModelConfigList)) {
            return map;
        }
        servingModelConfigList.forEach(servingModelConfig -> {
            if (!map.containsKey(servingModelConfig.getDeployId())) {
                ServingModelConfigVO servingModelConfigVO = new ServingModelConfigVO();
                BeanUtils.copyProperties(servingModelConfig, servingModelConfigVO);
                servingModelConfigVO.setDeployParams(servingModelConfig.getDeployParams());
                List<ServingModelConfigVO> list = new ArrayList<>();
                list.add(servingModelConfigVO);
                map.put(servingModelConfig.getDeployId(), list);
            } else {
                ServingModelConfigVO servingModelConfigVO = new ServingModelConfigVO();
                BeanUtils.copyProperties(servingModelConfig, servingModelConfigVO);
                servingModelConfigVO.setDeployParams(servingModelConfig.getDeployParams());
                List<ServingModelConfigVO> list = map.get(servingModelConfig.getDeployId());
                list.add(servingModelConfigVO);
            }
        });
        return map;
    }

    /**
     * ??????????????????????????????
     *
     * @param ptModelStatusQueryDTO ??????????????????
     * @return Boolean ???????????????true???????????????false???????????????
     */
    @Override
    public Boolean getServingModelStatus(PtModelStatusQueryDTO ptModelStatusQueryDTO) {
        if (ptModelStatusQueryDTO == null) {
            LogUtil.error(LogEnum.SERVING, "The ptModelStatusQueryDTO set is empty");
            throw new BusinessException("????????????");
        }

        if (CollectionUtils.isNotEmpty(ptModelStatusQueryDTO.getModelIds())
                && CollectionUtils.isNotEmpty(ptModelStatusQueryDTO.getModelBranchIds())) {
            LogUtil.error(LogEnum.SERVING, "The modelId and modelBranchId cannot be passed in at the same time");
            throw new BusinessException("modelId???ModelBranchId??????????????????");
        }

        QueryWrapper<ServingModelConfig> query = new QueryWrapper<>();
        if (CollectionUtils.isNotEmpty(ptModelStatusQueryDTO.getModelIds())) {
            query.in("model_id", ptModelStatusQueryDTO.getModelIds());
        } else if (CollectionUtils.isNotEmpty(ptModelStatusQueryDTO.getModelBranchIds())) {
            query.in("model_branch_id", ptModelStatusQueryDTO.getModelBranchIds());
        } else {
            LogUtil.error(LogEnum.SERVING, "The modelId and modelBranchId set is empty at the same time");
            throw new BusinessException("???????????????????????????");
        }
        List<ServingModelConfig> servingModelConfigs = servingModelConfigMapper.selectList(query);
        if (CollectionUtils.isNotEmpty(servingModelConfigs)) {
            for (ServingModelConfig servingModelConfig : servingModelConfigs) {
                ServingInfo servingInfo = servingInfoMapper.selectById(servingModelConfig.getServingId());
                if (StringUtils.equalsAny(servingInfo.getStatus(), ServingStatusEnum.IN_DEPLOYMENT.getStatus(),
                        ServingStatusEnum.WORKING.getStatus())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * serving????????????????????????
     *
     * @param dto ??????DTO??????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recycleRollback(RecycleCreateDTO dto) {
        if (StringUtils.isNotBlank(dto.getRemark())) {
            String[] modelConfigIds = dto.getRemark().split(SymbolConstant.COMMA);
            Long servingInfoId = null;
            for (String modelConfigId : modelConfigIds) {
                servingModelConfigMapper.updateStatusById(Long.valueOf(modelConfigId), false);
                if (servingInfoId == null) {
                    ServingModelConfig servingModelConfig = servingModelConfigMapper.selectById(modelConfigId);
                    servingInfoId = servingModelConfig.getServingId();
                }
            }
            if (servingInfoId != null) {
                servingInfoMapper.rollbackById(servingInfoId, false);
            }
        }
    }


    /**
     * ??????serving????????????
     *
     * @param servingInfo serving??????????????????
     * @param recyclePath  ??????????????????
     * @param isRollBack   ???????????????????????????
     */
    public void createRecycleTask(ServingInfo servingInfo, String modelConfigIds, String recyclePath, boolean isRollBack) {
        RecycleCreateDTO recycleCreateDTO = RecycleCreateDTO.builder()
                .recycleModule(RecycleModuleEnum.BIZ_SERVING.getValue())
                .recycleDelayDate(recycleConfig.getServingValid())  //??????3???
                .recycleNote(RecycleTool.generateRecycleNote("????????????????????????", servingInfo.getName(), servingInfo.getId()))
                .recycleCustom(RecycleResourceEnum.SERVING_RECYCLE_FILE.getClassName())
                .restoreCustom(RecycleResourceEnum.SERVING_RECYCLE_FILE.getClassName())
                .build();
        recycleCreateDTO.addRecycleDetailCreateDTO(RecycleDetailCreateDTO.builder()
                .recycleCondition(recyclePath)
                .recycleType(RecycleTypeEnum.FILE.getCode())
                .recycleNote(RecycleTool.generateRecycleNote("????????????????????????", servingInfo.getName(), servingInfo.getId()))
                .remark(modelConfigIds)
                .build()
        );
        //????????????????????????????????????deleted=0???
        if (isRollBack) {
            recycleCreateDTO.setRemark(modelConfigIds);
        }
        recycleService.createRecycleTask(recycleCreateDTO);
    }

}
