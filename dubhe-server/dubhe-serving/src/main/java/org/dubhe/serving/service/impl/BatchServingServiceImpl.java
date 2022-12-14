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


import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.collections4.CollectionUtils;
import org.dubhe.biz.base.constant.NumberConstant;
import org.dubhe.biz.base.constant.StringConstant;
import org.dubhe.biz.base.constant.SymbolConstant;
import org.dubhe.biz.base.context.UserContext;
import org.dubhe.biz.base.dto.*;
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
import org.dubhe.biz.file.utils.MinioUtil;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.permission.annotation.DataPermissionMethod;
import org.dubhe.biz.permission.base.BaseService;
import org.dubhe.biz.redis.utils.RedisUtils;
import org.dubhe.cloud.authconfig.service.AdminClient;
import org.dubhe.k8s.cache.ResourceCache;
import org.dubhe.k8s.domain.dto.PodQueryDTO;
import org.dubhe.k8s.domain.vo.PodVO;
import org.dubhe.k8s.enums.PodPhaseEnum;
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
import org.dubhe.serving.dao.BatchServingMapper;
import org.dubhe.serving.domain.dto.*;
import org.dubhe.serving.domain.entity.BatchServing;
import org.dubhe.serving.domain.vo.*;
import org.dubhe.serving.enums.ServingErrorEnum;
import org.dubhe.serving.enums.ServingStatusEnum;
import org.dubhe.serving.service.BatchServingService;
import org.dubhe.serving.task.DeployServingAsyncTask;
import org.dubhe.serving.utils.ServingStatusDetailDescUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * @description ??????????????????
 * @date 2020-08-26
 */
@Service
public class BatchServingServiceImpl extends ServiceImpl<BatchServingMapper, BatchServing> implements BatchServingService {

    @Resource
    private BatchServingMapper batchServingMapper;
    @Resource
    private ModelBranchClient modelBranchClient;
    @Resource
    private ModelInfoClient modelInfoClient;
    @Resource
    private AdminClient adminClient;
    @Resource
    private DeployServingAsyncTask deployServingAsyncTask;
    @Resource(name = "hostFileStoreApiImpl")
    private FileStoreApi fileStoreApi;
    @Resource
    private MinioUtil minioUtil;
    @Resource
    private K8sNameTool k8sNameTool;
    @Resource
    private PodService podService;
    @Resource
    private RecycleService recycleService;
    @Resource
    private UserContextService userContextService;
    @Value("${minio.bucketName}")
    private String bucketName;
    @Resource
    private RecycleConfig recycleConfig;
    @Resource
    private ImageClient imageClient;
    @Resource
    private AlgorithmClient algorithmClient;
    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private ResourceCache resourceCache;
    @Value("Task:BatchServing:"+"${spring.profiles.active}_batch_serving_id_")
    private String batchServingIdPrefix;

    private final static List<String> FILE_NAMES;

    static {
        FILE_NAMES = ReflectionUtils.getFieldNames(BatchServingQueryVO.class);
    }

    /**
     * ??????????????????
     *
     * @param batchServingQueryDTO ????????????????????????
     * @return Map<String, Object> ????????????????????????????????????
     */
    @Override
    public Map<String, Object> query(BatchServingQueryDTO batchServingQueryDTO) {
        String name = batchServingQueryDTO.getName();
        //?????????????????????id????????????
        if (StringUtils.isNotBlank(name)) {
            //????????????
            if (StringConstant.PATTERN_NUM.matcher(name).matches()) {
                batchServingQueryDTO.setId(Long.parseLong(name));
                batchServingQueryDTO.setName(null);
                Map<String, Object> map = queryBatchServing(batchServingQueryDTO);
                if (((List<ServingInfoQueryVO>) map.get(StringConstant.RESULT)).size() > NumberConstant.NUMBER_0) {
                    return map;
                } else {
                    batchServingQueryDTO.setId(null);
                    batchServingQueryDTO.setName(name);
                }
            }
        }
        return queryBatchServing(batchServingQueryDTO);
    }

    /**
     * ??????????????????
     *
     * @param batchServingQueryDTO ????????????????????????
     * @return Map<String, Object> ????????????????????????????????????
     */
    public Map<String, Object> queryBatchServing(BatchServingQueryDTO batchServingQueryDTO) {
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("???????????????????????????");
        }
        LogUtil.info(LogEnum.SERVING, "User {} queried the online service list with the query of{}", user.getUsername(), JSONObject.toJSONString(batchServingQueryDTO));

        QueryWrapper<BatchServing> wrapper = WrapperHelp.getWrapper(batchServingQueryDTO);
        //?????????????????????????????????
        if (!BaseService.isAdmin(user)) {
            wrapper.eq("create_user_id", user.getId());
        }
        Page page = new Page(null == batchServingQueryDTO.getCurrent() ? NumberConstant.NUMBER_1 : batchServingQueryDTO.getCurrent(),
                null == batchServingQueryDTO.getSize() ? NumberConstant.NUMBER_10 : batchServingQueryDTO.getSize());
        try {
            //??????????????????????????????????????????????????????????????????????????????
            String column = batchServingQueryDTO.getSort() != null && FILE_NAMES.contains(batchServingQueryDTO.getSort()) ? StringUtils.humpToLine(batchServingQueryDTO.getSort()) : "update_time";
            //????????????
            boolean isAsc = StringUtils.isBlank(batchServingQueryDTO.getOrder()) || StringUtils.equals(batchServingQueryDTO.getOrder(), StringConstant.SORT_DESC) ? false : true;
            wrapper.orderBy(true, isAsc, column);
        } catch (Exception e) {
            LogUtil.error(LogEnum.SERVING, "Query online service with an exception, query info:{}???exception info:{}", JSONObject.toJSONString(batchServingQueryDTO), e);
            throw new BusinessException(ServingErrorEnum.INTERNAL_SERVER_ERROR);
        }
        IPage<BatchServing> batchServings = batchServingMapper.selectPage(page, wrapper);
        List<BatchServingQueryVO> queryList = batchServings.getRecords().stream().map(batchServing -> {
            BatchServingQueryVO batchServingQueryVO = new BatchServingQueryVO();
            BeanUtils.copyProperties(batchServing, batchServingQueryVO);
            return batchServingQueryVO;
        }).collect(Collectors.toList());
        LogUtil.info(LogEnum.SERVING, "User {} queried batching service list, the number of batching service is {}", user.getUsername(), queryList.size());
        return PageUtil.toPage(page, queryList);
    }

    /**
     * ??????????????????
     *
     * @param batchServingCreateDTO ????????????????????????
     * @return BatchServingCreateVO ?????????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public BatchServingCreateVO create(BatchServingCreateDTO batchServingCreateDTO) {
        UserContext user = userContextService.getCurUser();
        //????????????
        if (user == null) {
            throw new BusinessException("???????????????????????????");
        }
        checkNameExist(batchServingCreateDTO.getName(), user.getId());
        BatchServing batchServing = new BatchServing();
        BeanUtils.copyProperties(batchServingCreateDTO, batchServing);

        String imageUrl = getImageUrl(batchServingCreateDTO.getImageName(), batchServingCreateDTO.getImageTag());
        batchServing.setImage(imageUrl);
        PtModelInfoQueryVO ptModelInfoQueryVO = getPtModelInfo(batchServingCreateDTO.getModelId());
        batchServing.setModelAddress(ptModelInfoQueryVO.getModelAddress());
        if (ptModelInfoQueryVO.getFrameType() > NumberConstant.NUMBER_4) {
            throw new BusinessException(ServingErrorEnum.MODEL_FRAME_TYPE_NOT_SUPPORTED);
        }
        checkScriptPath(batchServing);
        batchServing.setFrameType(ptModelInfoQueryVO.getFrameType());
        checkResourceType(batchServing.getFrameType(), batchServingCreateDTO.getResourcesPoolType());
        checkInputExist(batchServingCreateDTO.getInputPath());
        if (NumberConstant.NUMBER_0 == batchServing.getModelResource()) {
            PtModelBranchQueryVO ptModelBranchQueryVO = getModelBranch(batchServing.getModelBranchId());
            batchServing.setModelAddress(ptModelBranchQueryVO.getModelAddress());
        }
        checkModelAddress(batchServing.getModelAddress());
        batchServing.setStatus(ServingStatusEnum.IN_DEPLOYMENT.getStatus());
        String outputPath = ServingConstant.OUTPUT_NFS_PATH + user.getId() + File.separator + StringUtils.getTimestamp() + File.separator;
        batchServing.setOutputPath(outputPath);
        saveBatchServing(user, batchServing);
        String taskIdentify = resourceCache.getTaskIdentify(batchServing.getId(), batchServing.getName(), batchServingIdPrefix);
        deployServingAsyncTask.deployBatchServing(user, batchServing, taskIdentify);
        return new BatchServingCreateVO(batchServing.getId(), batchServing.getStatus());
    }

    /**
     * ????????????????????????
     *
     * @param batchServing ??????????????????
     */
    private void checkScriptPath(BatchServing batchServing) {
        //????????????????????????????????????????????????????????????
        if (batchServing.getUseScript() && batchServing.getAlgorithmId() != null) {
            TrainAlgorithmQureyVO dataAlgorithm = getAlgorithm(batchServing.getAlgorithmId());
            //??????????????????????????????
            String scriptPath = k8sNameTool.getAbsolutePath(dataAlgorithm.getCodeDir());
            if (!fileStoreApi.fileOrDirIsExist(scriptPath)) {
                throw new BusinessException(ServingErrorEnum.SCRIPT_NOT_EXIST);
            }
            batchServing.setScriptPath(dataAlgorithm.getCodeDir());
        }
    }

    /**
     * ?????????????????????????????????
     *
     * @param user         ????????????
     * @param batchServing ??????????????????
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveBatchServing(UserContext user, BatchServing batchServing) {
        if (!save(batchServing)) {
            LogUtil.error(LogEnum.SERVING, "User {} failed to save the batching service info to the database, service name???{}", user.getUsername(), batchServing.getName());
            throw new BusinessException(ServingErrorEnum.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * ??????????????????
     *
     * @param frameType        ????????????
     * @param resourcePoolType ????????????
     */
    public void checkResourceType(Integer frameType, Integer resourcePoolType) {
        // oneflow ????????????cpu
        if (NumberConstant.NUMBER_1 == frameType && NumberConstant.NUMBER_0 == resourcePoolType) {
            throw new BusinessException(ServingErrorEnum.CPU_NOT_SUPPORTED_BY_ONEFLOW);
        }
    }

    /**
     * ??????serving????????????
     *
     * @param batchServing serving????????????
     * @param recyclePath  ??????????????????
     * @param isRollBack   ???????????????????????????
     */
    public void createRecycleTask(BatchServing batchServing, String recyclePath, boolean isRollBack) {
        RecycleCreateDTO recycleCreateDTO = RecycleCreateDTO.builder()
                .recycleModule(RecycleModuleEnum.BIZ_SERVING.getValue())
                .recycleDelayDate(recycleConfig.getServingValid())  //??????3???
                .recycleNote(RecycleTool.generateRecycleNote("????????????????????????", batchServing.getName(), batchServing.getId()))
                .recycleCustom(RecycleResourceEnum.BATCH_SERVING_RECYCLE_FILE.getClassName())
                .restoreCustom(RecycleResourceEnum.BATCH_SERVING_RECYCLE_FILE.getClassName())
                .build();
        recycleCreateDTO.addRecycleDetailCreateDTO(RecycleDetailCreateDTO.builder()
                .recycleCondition(recyclePath)
                .recycleType(RecycleTypeEnum.FILE.getCode())
                .recycleNote(RecycleTool.generateRecycleNote("????????????????????????", batchServing.getName(), batchServing.getId()))
                .remark(String.valueOf(batchServing.getId()))
                .build()
        );
        //????????????????????????????????????deleted=0???
        if (isRollBack) {
            recycleCreateDTO.setRemark(String.valueOf(batchServing.getId()));
        }
        recycleService.createRecycleTask(recycleCreateDTO);
    }

    /**
     * ????????????????????????
     *
     * @param name   ????????????
     * @param userId ??????ID
     */
    public void checkNameExist(String name, Long userId) {
        LambdaQueryWrapper<BatchServing> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BatchServing::getName, name);
        wrapper.eq(BatchServing::getCreateUserId, userId);
        int count = batchServingMapper.selectCount(wrapper);
        if (count > NumberConstant.NUMBER_0) {
            throw new BusinessException(ServingErrorEnum.SERVING_NAME_EXIST);
        }
    }

    /**
     * ??????????????????????????????
     *
     * @param modelAddress ????????????
     */
    public void checkModelAddress(String modelAddress) {
        String path = k8sNameTool.getAbsolutePath(modelAddress);
        if (!fileStoreApi.fileOrDirIsExist(path)) {
            throw new BusinessException(ServingErrorEnum.MODEL_FILE_NOT_EXIST);
        }
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
        DataResponseBody<PtModelInfoQueryVO> modelInfoPresetDataResponseBody = modelInfoClient.getByModelId(ptModelInfoQueryByIdDTO);
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
     * ??????????????????
     *
     * @param batchServingUpdateDTO ????????????????????????
     * @return BatchServingUpdateVO ?????????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public BatchServingUpdateVO update(BatchServingUpdateDTO batchServingUpdateDTO) {
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("???????????????????????????");
        }
        BatchServing batchServing = checkBatchServingExist(batchServingUpdateDTO.getId(), user.getId());
        checkBatchServingStatus(batchServing.getStatus());
        batchServing.setStatusDetail(SymbolConstant.BRACKETS);
        deployServingAsyncTask.deleteBatchServing(user, batchServing, batchServing.getResourceInfo());
        //????????????????????????????????????????????????????????????
        if (!batchServing.getInputPath().equals(batchServingUpdateDTO.getInputPath())) {
            createRecycleTask(batchServing, k8sNameTool.getAbsolutePath(batchServing.getInputPath()), true);
        }
        //??????????????????
        PtModelInfoQueryVO ptModelInfoQueryVO = getPtModelInfo(batchServingUpdateDTO.getModelId());
        batchServing.setModelAddress(ptModelInfoQueryVO.getModelAddress());
        if (ptModelInfoQueryVO.getFrameType() > NumberConstant.NUMBER_4) {
            throw new BusinessException(ServingErrorEnum.MODEL_FRAME_TYPE_NOT_SUPPORTED);
        }
        batchServing.setFrameType(ptModelInfoQueryVO.getFrameType());
        batchServing.setModelAddress(ptModelInfoQueryVO.getModelAddress());
        if (NumberConstant.NUMBER_0 == batchServing.getModelResource()) {
            PtModelBranchQueryVO ptModelBranchQueryVO = getModelBranch(batchServing.getModelBranchId());
            batchServing.setModelAddress(ptModelBranchQueryVO.getModelAddress());
        }
        BeanUtils.copyProperties(batchServingUpdateDTO, batchServing);

        String imageUrl = getImageUrl(batchServingUpdateDTO.getImageName(), batchServingUpdateDTO.getImageTag());
        batchServing.setImage(imageUrl);
        checkScriptPath(batchServing);
        checkResourceType(batchServing.getFrameType(), batchServing.getResourcesPoolType());
        batchServing.setStatus(ServingStatusEnum.IN_DEPLOYMENT.getStatus());
        batchServing.setUpdateTime(DateUtil.getCurrentTimestamp());
        String outputPath = ServingConstant.OUTPUT_NFS_PATH + user.getId() + File.separator + StringUtils.getTimestamp() + File.separator;
        batchServing.setOutputPath(outputPath);
        updateBatchServing(user, batchServing);
        String taskIdentify = resourceCache.getTaskIdentify(batchServing.getId(), batchServing.getName(), batchServingIdPrefix);
        deployServingAsyncTask.deployBatchServing(user, batchServing, taskIdentify);
        return new BatchServingUpdateVO(batchServing.getId(), batchServing.getStatus());
    }

    /**
     * ????????????url
     *
     * @param imageName ????????????
     * @param imageTag ????????????
     * @return ??????url
     */
    private String getImageUrl(String imageName, String imageTag) {
        PtImageQueryUrlDTO ptImageQueryUrlDTO = new PtImageQueryUrlDTO();
        List<Integer> servingImageType = new ArrayList(){{
            add(ImageTypeEnum.SERVING.getType());
        }};
        ptImageQueryUrlDTO.setImageTypes(servingImageType);
        ptImageQueryUrlDTO.setImageName(imageName);
        ptImageQueryUrlDTO.setImageTag(imageTag);
        DataResponseBody<String> dataResponseBody = imageClient.getImageUrl(ptImageQueryUrlDTO);
        if (!dataResponseBody.succeed()) {
            throw new BusinessException(ServingErrorEnum.CALL_IMAGE_SERVER_FAIL);
        }
        if (StringUtils.isBlank(dataResponseBody.getData())) {
            throw new BusinessException(ServingErrorEnum.IMAGE_NOT_EXIST);
        }
        return dataResponseBody.getData();
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
        DataResponseBody<TrainAlgorithmQureyVO> algorithmResponseBody = algorithmClient.selectById(trainAlgorithmSelectByIdDTO);
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
     * ?????????????????????????????????
     *
     * @param user         ????????????
     * @param batchServing ??????????????????
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateBatchServing(UserContext user, BatchServing batchServing) {
        int result = batchServingMapper.updateById(batchServing);
        if (result < 1) {
            LogUtil.error(LogEnum.SERVING, "User {} failed modifying the batching service in the database, service id={}", user.getUsername(), batchServing.getId());
            throw new BusinessException(ServingErrorEnum.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * ??????????????????????????????
     *
     * @param id     ????????????id
     * @param userId ??????id
     * @return BatchServing ??????????????????
     */
    BatchServing checkBatchServingExist(Long id, Long userId) {
        BatchServing batchServing = batchServingMapper.selectById(id);
        if (batchServing == null) {
            throw new BusinessException(ServingErrorEnum.SERVING_INFO_ABSENT);
        } else {
            //????????????????????????????????????????????????????????????????????????????????????
            if (!BaseService.isAdmin()) {
                if (!userId.equals(batchServing.getCreateUserId())) {
                    throw new BusinessException(ServingErrorEnum.SERVING_INFO_ABSENT);
                }
            }
        }
        return batchServing;
    }

    /**
     * ?????????????????????????????????????????????
     *
     * @param status ??????????????????
     */
    void checkBatchServingStatus(String status) {
        if (ServingStatusEnum.WORKING.getStatus().equals(status) ||
                ServingStatusEnum.IN_DEPLOYMENT.getStatus().equals(status)) {
            throw new BusinessException(ServingErrorEnum.OPERATION_NOT_ALLOWED);
        }
    }

    /**
     * ??????????????????????????????
     *
     * @param inputPath ????????????
     */
    void checkInputExist(String inputPath) {
        if (!fileStoreApi.fileOrDirIsExist(k8sNameTool.getAbsolutePath(inputPath))) {
            throw new BusinessException(ServingErrorEnum.INPUT_FILE_NOT_EXIST);
        }
    }

    /**
     * ??????????????????
     *
     * @param batchServingDeleteDTO ????????????????????????
     * @return BatchServingDeleteVO ?????????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchServingDeleteVO delete(BatchServingDeleteDTO batchServingDeleteDTO) {
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("???????????????????????????");
        }
        BatchServing batchServing = checkBatchServingExist(batchServingDeleteDTO.getId(), user.getId());
        checkBatchServingStatus(batchServing.getStatus());
        deleteBatchServing(batchServingDeleteDTO, user);
        String taskIdentify = (String) redisUtils.get(batchServingIdPrefix + String.valueOf(batchServing.getId()));
        if (StringUtils.isNotEmpty(taskIdentify)){
            redisUtils.del(taskIdentify, batchServingIdPrefix + String.valueOf(batchServing.getId()));
        }
        String sourcePath = k8sNameTool.getAbsolutePath(ServingConstant.BATCH_ROOT_PATH + batchServing.getCreateUserId() + File.separator + batchServing.getId() + File.separator);
        String recyclePath = k8sNameTool.getAbsolutePath(batchServing.getInputPath()) + StrUtil.COMMA + k8sNameTool.getAbsolutePath(batchServing.getOutputPath()) + StrUtil.COMMA + sourcePath;
        createRecycleTask(batchServing, recyclePath, true);
        return new BatchServingDeleteVO(batchServing.getId());
    }

    /**
     * ?????????????????????????????????
     *
     * @param batchServingDeleteDTO ??????????????????
     * @param user                  ????????????
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatchServing(BatchServingDeleteDTO batchServingDeleteDTO, UserContext user) {
        if (!removeById(batchServingDeleteDTO.getId())) {
            LogUtil.error(LogEnum.SERVING, "User {} failed deleting the batching service in the database, service id={}", user.getUsername(), batchServingDeleteDTO.getId());
            throw new BusinessException(ServingErrorEnum.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * ??????????????????
     *
     * @param batchServingStartDTO ????????????????????????
     * @return BatchServingStartVO ?????????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchServingStartVO start(BatchServingStartDTO batchServingStartDTO) {
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("???????????????????????????");
        }
        BatchServing batchServing = checkBatchServingExist(batchServingStartDTO.getId(), user.getId());
        if (StringUtils.equalsAny(batchServing.getStatus(), ServingStatusEnum.IN_DEPLOYMENT.getStatus(), ServingStatusEnum.WORKING.getStatus(), ServingStatusEnum.COMPLETED.getStatus())) {
            LogUtil.error(LogEnum.SERVING, "User {} failed starting the batching service, service id={}, service name:{}, service status???{}", user.getUsername(), batchServing.getId(), batchServing.getName(), batchServing.getStatus());
            throw new BusinessException(ServingErrorEnum.OPERATION_NOT_ALLOWED);
        }
        //??????????????????????????????????????????
        createRecycleTask(batchServing, k8sNameTool.getAbsolutePath(batchServing.getOutputPath()), true);
        batchServing.setProgress(SymbolConstant.ZERO);
        batchServing.setStatus(ServingStatusEnum.IN_DEPLOYMENT.getStatus());
        //????????????????????????
        String outputPath = ServingConstant.OUTPUT_NFS_PATH + user.getId() + File.separator + StringUtils.getTimestamp() + File.separator;
        batchServing.setOutputPath(outputPath);
        //????????????????????????????????????
        batchServing.setStatusDetail(SymbolConstant.BRACKETS);
        updateBatchServing(user, batchServing);
        String taskIdentify = resourceCache.getTaskIdentify(batchServing.getId(), batchServing.getName(), batchServingIdPrefix);
        deployServingAsyncTask.deployBatchServing(user, batchServing, taskIdentify);
        return new BatchServingStartVO(batchServing.getId(), batchServing.getStatus(), batchServing.getProgress());
    }

    /**
     * ??????????????????
     *
     * @param batchServingStopDTO ????????????????????????
     * @return BatchServingStopVO ?????????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchServingStopVO stop(BatchServingStopDTO batchServingStopDTO) {
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("???????????????????????????");
        }
        BatchServing batchServing = checkBatchServingExist(batchServingStopDTO.getId(), user.getId());
        if (ServingStatusEnum.STOP.getStatus().equals(batchServing.getStatus()) || ServingStatusEnum.EXCEPTION.equals(batchServing.getStatus())) {
            LogUtil.error(LogEnum.SERVING, "The service is not running, user {} failed stopping the service. Service id={}, service name:{}, service status:{}",
                    user.getUsername(), batchServing.getId(), batchServing.getName(), batchServing.getStatus());
            throw new BusinessException(ServingErrorEnum.OPERATION_NOT_ALLOWED);
        }
        deployServingAsyncTask.deleteBatchServing(user, batchServing, batchServing.getResourceInfo());
        batchServing.setStatus(ServingStatusEnum.STOP.getStatus());
        updateBatchServing(user, batchServing);
        return new BatchServingStopVO(batchServing.getId(), batchServing.getStatus());
    }

    /**
     * ????????????????????????
     *
     * @param batchServingDetailDTO ????????????????????????
     * @return BatchServingDetailVO ????????????????????????
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public BatchServingDetailVO getDetail(BatchServingDetailDTO batchServingDetailDTO) {
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("???????????????????????????");
        }
        BatchServing batchServing = checkBatchServingExist(batchServingDetailDTO.getId(), user.getId());
        BatchServingDetailVO batchServingDetailVO = new BatchServingDetailVO();
        BeanUtils.copyProperties(batchServing, batchServingDetailVO);
        PtModelInfoQueryVO ptModelInfoQueryVO = getPtModelInfo(batchServingDetailVO.getModelId());
        batchServingDetailVO.setModelName(ptModelInfoQueryVO.getName());
        batchServingDetailVO.setModelAddress(ptModelInfoQueryVO.getModelAddress());
        if (NumberConstant.NUMBER_0 == batchServing.getModelResource()) {
            PtModelBranchQueryVO ptModelBranchQueryVO = getModelBranch(batchServingDetailVO.getModelBranchId());
            batchServingDetailVO.setModelVersion(ptModelBranchQueryVO.getVersion());
        }
        //??????????????????????????????
        if (batchServing.getStatus().equals(ServingStatusEnum.WORKING.getStatus())) {
            String progress = queryProgressByMinIO(batchServing);
            batchServingDetailVO.setProgress(progress);
        }
        //??????????????????????????????id?????????????????????????????????
        if (batchServing.getUseScript() && batchServing.getAlgorithmId() != null) {
            TrainAlgorithmQureyVO dataAlgorithm = getAlgorithm(batchServing.getAlgorithmId());
            batchServingDetailVO.setAlgorithmName(dataAlgorithm.getAlgorithmName());
        }
        return batchServingDetailVO;
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
        DataResponseBody<PtModelBranchQueryVO> modelBranchQueryVODataResponseBody = modelBranchClient.getByBranchId(ptModelBranchQueryByIdDTO);
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
     * @param times ??????????????????
     * @param req   ??????????????????
     * @return boolean ??????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchServingCallback(int times, BatchServingK8sPodCallbackCreateDTO req) {

        // ??????namespace???podName??????????????????
        String resourceInfo = k8sNameTool.getResourceInfoFromResourceName(BizEnum.BATCH_SERVING, req.getResourceName());
        if (StringUtils.isBlank(resourceInfo)) {
            LogUtil.warn(LogEnum.SERVING, "Cannot find modelConfig ID! Request: {}", Thread.currentThread(), times, req.toString());
            return false;
        }
        Long id = Long.parseLong(resourceInfo.substring(NumberConstant.NUMBER_4));
        BatchServing batchServing = batchServingMapper.selectById(id);
        if (Objects.isNull(batchServing)) {
            LogUtil.warn(LogEnum.SERVING, "Cannot find batchServing! Request: {}", Thread.currentThread(), times, req.toString());
            return false;
        }
        // ????????????????????????????????????????????????slave??????????????????????????????????????????????????????????????????????????????????????????
        if (req.getPodName().contains(ServingConstant.SLAVE_POD) && !ServingStatusEnum.IN_DEPLOYMENT.getStatus().equals(batchServing.getStatus())) {
            return true;
        }
        // ??????????????????????????????????????????
        if (ServingStatusEnum.COMPLETED.getStatus().equals(batchServing.getStatus())) {
            return true;
        }
        if (PodPhaseEnum.PENDING.getPhase().equals(req.getPhase())) {
            batchServing.setStatus(ServingStatusEnum.IN_DEPLOYMENT.getStatus());
        }
        if (PodPhaseEnum.RUNNING.getPhase().equals(req.getPhase())) {
            //?????????????????????????????????????????????????????????
            if (ServingStatusEnum.IN_DEPLOYMENT.getStatus().equals(batchServing.getStatus())) {
                batchServing.setStartTime(DateUtil.getCurrentTimestamp());
            }
            batchServing.setStatus(ServingStatusEnum.WORKING.getStatus());
        }
        if (PodPhaseEnum.SUCCEEDED.getPhase().equals(req.getPhase())) {
            batchServing.setEndTime(DateUtil.getCurrentTimestamp());
            batchServing.setStatus(ServingStatusEnum.COMPLETED.getStatus());
            batchServing.setProgress(String.valueOf(NumberConstant.NUMBER_100));
            // ??????????????????????????????????????????
            DataResponseBody<UserDTO> userDTODataResponseBody = adminClient.getUsers(batchServing.getCreateUserId());
            if (userDTODataResponseBody.succeed() && userDTODataResponseBody.getData() != null) {
                deployServingAsyncTask.asyncSendServingMail(userDTODataResponseBody.getData().getEmail(), batchServing.getId());
            }
        }
        if (PodPhaseEnum.FAILED.getPhase().equals(req.getPhase())) {
            String progress = queryProgressByMinIO(batchServing);
            batchServing.setProgress(progress);
            batchServing.setStatus(ServingStatusEnum.EXCEPTION.getStatus());
        }
        //????????????????????????????????????????????????
        if (PodPhaseEnum.DELETED.getPhase().equals(req.getPhase()) && !ServingStatusEnum.EXCEPTION.getStatus().equals(batchServing.getStatus())) {
            String progress = queryProgressByMinIO(batchServing);
            batchServing.setProgress(progress);
            batchServing.setStatus(ServingStatusEnum.STOP.getStatus());
        }
        if (PodPhaseEnum.UNKNOWN.getPhase().equals(req.getPhase())) {
            batchServing.setStatus(ServingStatusEnum.UNKNOWN.getStatus());
        }

        //????????????????????????????????????????????????????????????????????????????????????
        String statusDetailKey = ServingStatusDetailDescUtil.getServingStatusDetailKey(ServingStatusDetailDescUtil.BULK_SERVICE_CONTAINER_INFORMATION, req.getPodName());
        //???????????????????????????delete????????????????????????????????????????????????????????????
        if (StringUtils.isEmpty(req.getMessages()) && !PodPhaseEnum.DELETED.getPhase().equals(req.getPhase())) {
            batchServing.removeStatusDetail(statusDetailKey);
        } else {
            batchServing.putStatusDetail(statusDetailKey, req.getMessages());
        }
        LogUtil.info(LogEnum.SERVING, "The callback batch serving message: {} ,req message: {}", batchServing, req);
        return updateById(batchServing);
    }

    /**
     * ????????????????????????POD
     *
     * @param id ????????????id
     * @return List<PodVO> ??????POD??????
     */
    @Override
    public List<PodVO> getPods(Long id) {
        BatchServing batchServing = batchServingMapper.selectById(id);
        if (batchServing == null) {
            return Collections.emptyList();
        }
        //??????????????????????????????
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("???????????????????????????");
        }
        String namespace = k8sNameTool.getNamespace(batchServing.getCreateUserId());
        return podService.getPods(new PodQueryDTO(namespace, k8sNameTool.generateResourceName(BizEnum.BATCH_SERVING, batchServing.getResourceInfo())));
    }

    /**
     * ?????????????????????????????????
     *
     * @param id ????????????id
     * @return BatchServingQueryVO ??????????????????
     */
    @Override
    public BatchServingQueryVO queryStatusAndProgress(Long id) {
        BatchServing batchServing = batchServingMapper.selectById(id);
        if (Objects.isNull(batchServing)) {
            throw new BusinessException(ServingErrorEnum.SERVING_INFO_ABSENT);
        }
        String progress = queryProgressByMinIO(batchServing);
        return BatchServingQueryVO.builder()
                .id(id)
                .name(batchServing.getName())
                .description(batchServing.getDescription())
                .status(batchServing.getStatus())
                .statusDetail(batchServing.getStatusDetail())
                .progress(progress)
                .startTime(batchServing.getStartTime())
                .endTime(batchServing.getEndTime())
                .outputPath(batchServing.getOutputPath())
                .build();
    }

    /**
     * ??????minio??????????????????
     *
     * @param batchServing ??????????????????
     * @return String ??????????????????
     */
    private String queryProgressByMinIO(BatchServing batchServing) {
        DecimalFormat df = new DecimalFormat(String.valueOf(NumberConstant.NUMBER_0));
        int inputCount = queryCount(batchServing.getInputPath());
        int outputCount = queryCount(batchServing.getOutputPath());
        String progress = String.valueOf(NumberConstant.NUMBER_0);
        if (inputCount != NumberConstant.NUMBER_0) {
            progress = df.format((float) outputCount / inputCount * NumberConstant.NUMBER_100);
        }
        return progress;
    }

    /**
     * ??????minio??????????????????
     *
     * @param path ????????????
     * @return int ??????????????????
     */
    private int queryCount(String path) {
        try {
            if (!fileStoreApi.fileOrDirIsExist(k8sNameTool.getAbsolutePath(path))) {
                return 0;
            }
            return minioUtil.getCount(bucketName, path);
        } catch (Exception e) {
            LogUtil.error(LogEnum.SERVING, "query count failed by path in minio: {}, exception: {}", path, e);
        }
        return NumberConstant.NUMBER_0;
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

        if (CollectionUtils.isNotEmpty(ptModelStatusQueryDTO.getModelIds()) && CollectionUtils.isNotEmpty(ptModelStatusQueryDTO.getModelBranchIds())) {
            LogUtil.error(LogEnum.SERVING, "The modelId and modelBranchId cannot be passed in at the same time");
            throw new BusinessException("modelId???ModelBranchId??????????????????");
        }

        QueryWrapper<BatchServing> query = new QueryWrapper<>();
        if (CollectionUtils.isNotEmpty(ptModelStatusQueryDTO.getModelIds())) {
            query.in("model_id", ptModelStatusQueryDTO.getModelIds());
        } else if (CollectionUtils.isNotEmpty(ptModelStatusQueryDTO.getModelBranchIds())) {
            query.in("model_branch_id", ptModelStatusQueryDTO.getModelBranchIds());
        } else {
            LogUtil.error(LogEnum.SERVING, "The modelId and modelBranchId set is empty at the same time");
            throw new BusinessException("???????????????????????????");
        }
        List<BatchServing> batchServings = batchServingMapper.selectList(query);
        for (BatchServing batchServing : batchServings) {
            if (StringUtils.equalsAny(batchServing.getStatus(), ServingStatusEnum.IN_DEPLOYMENT.getStatus(), ServingStatusEnum.WORKING.getStatus())) {
                return true;
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
        if (StrUtil.isNotBlank(dto.getRemark())) {
            batchServingMapper.updateStatusById(Long.valueOf(dto.getRemark()), false);
        }
    }
}
