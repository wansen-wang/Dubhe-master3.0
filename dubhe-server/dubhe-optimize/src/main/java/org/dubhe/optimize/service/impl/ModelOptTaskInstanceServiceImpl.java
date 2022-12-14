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

package org.dubhe.optimize.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.base.constant.NumberConstant;
import org.dubhe.biz.base.constant.SymbolConstant;
import org.dubhe.biz.base.context.UserContext;
import org.dubhe.biz.base.dto.PtModelStatusQueryDTO;
import org.dubhe.biz.base.enums.BizEnum;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.service.UserContextService;
import org.dubhe.biz.base.utils.DateUtil;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.base.vo.DictDetailVO;
import org.dubhe.biz.base.vo.DictVO;
import org.dubhe.biz.db.utils.PageUtil;
import org.dubhe.biz.db.utils.WrapperHelp;
import org.dubhe.biz.file.enums.BizPathEnum;
import org.dubhe.biz.file.utils.MinioUtil;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.redis.utils.RedisUtils;
import org.dubhe.k8s.api.ModelOptJobApi;
import org.dubhe.k8s.cache.ResourceCache;
import org.dubhe.k8s.domain.bo.PtModelOptimizationJobBO;
import org.dubhe.k8s.domain.dto.PodQueryDTO;
import org.dubhe.k8s.domain.resource.BizJob;
import org.dubhe.k8s.domain.vo.PodVO;
import org.dubhe.k8s.enums.K8sResponseEnum;
import org.dubhe.k8s.enums.PodPhaseEnum;
import org.dubhe.k8s.service.PodService;
import org.dubhe.k8s.utils.K8sNameTool;
import org.dubhe.optimize.client.DictClient;
import org.dubhe.optimize.constant.ModelOptConstant;
import org.dubhe.optimize.dao.ModelOptTaskInstanceMapper;
import org.dubhe.optimize.domain.dto.ModelOptTaskInstanceCancelDTO;
import org.dubhe.optimize.domain.dto.ModelOptTaskInstanceDeleteDTO;
import org.dubhe.optimize.domain.dto.ModelOptTaskInstanceDetailDTO;
import org.dubhe.optimize.domain.dto.ModelOptTaskInstanceQueryDTO;
import org.dubhe.optimize.domain.dto.ModelOptTaskInstanceResubmitDTO;
import org.dubhe.optimize.domain.dto.callback.ModelOptK8sPodCallbackCreateDTO;
import org.dubhe.optimize.domain.entity.ModelOptTaskInstance;
import org.dubhe.optimize.domain.vo.ModelOptResultQueryVO;
import org.dubhe.optimize.domain.vo.ModelOptTaskInstanceQueryVO;
import org.dubhe.optimize.enums.DistillCommandEnum;
import org.dubhe.optimize.enums.ModelOptErrorEnum;
import org.dubhe.optimize.enums.ModelOptInstanceStatusEnum;
import org.dubhe.optimize.enums.OptimizeTypeEnum;
import org.dubhe.optimize.service.ModelOptTaskInstanceService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @description ????????????????????????
 * @date 2020-05-22
 */
@Service
public class ModelOptTaskInstanceServiceImpl extends ServiceImpl<ModelOptTaskInstanceMapper, ModelOptTaskInstance> implements ModelOptTaskInstanceService {

    @Resource
    private ModelOptTaskInstanceMapper modelOptTaskInstanceMapper;

    @Resource
    private ModelOptJobApi modelOptJobApi;

    @Resource
    private DictClient dictClient;

    @Resource
    private K8sNameTool k8sNameTool;

    @Resource
    private PodService podService;

    @Resource
    private MinioUtil minioUtil;

    @Value("${minio.bucketName}")
    private String bucket;

    @Value("${optimize.image}")
    private String optimizeImage;

    @Resource
    private UserContextService userContextService;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private ResourceCache resourceCache;

    @Value("Task:ModelOpt:" + "${spring.profiles.active}_model_opt_id_")
    private String modelOptIdPrefix;

    /**
     * ??????????????????????????????????????????
     *
     * @param instanceQueryDTO ????????????
     * @return Map<String, Object> ????????????
     */
    @Override
    public Map<String, Object> queryAll(ModelOptTaskInstanceQueryDTO instanceQueryDTO) {
        instanceQueryDTO.timeConvert();
        QueryWrapper<ModelOptTaskInstance> wrapper = WrapperHelp.getWrapper(instanceQueryDTO);
        wrapper.orderByDesc("id");
        Page page = new Page(null == instanceQueryDTO.getCurrent() ? MagicNumConstant.ONE : instanceQueryDTO.getCurrent()
                , null == instanceQueryDTO.getSize() ? MagicNumConstant.TEN : instanceQueryDTO.getSize());
        IPage<ModelOptTaskInstance> modelOptTaskInstances = modelOptTaskInstanceMapper.selectPage(page, wrapper);
        List<ModelOptTaskInstanceQueryVO> list = modelOptTaskInstances.getRecords().stream().map(modelOptTaskInstance -> {
            ModelOptTaskInstanceQueryVO modelOptTaskInstanceVO = buildResult(modelOptTaskInstance);
            return modelOptTaskInstanceVO;
        }).collect(Collectors.toList());
        return PageUtil.toPage(modelOptTaskInstances, list);
    }

    /**
     * ??????????????????
     *
     * @param modelOptTaskInstance ??????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(ModelOptTaskInstance modelOptTaskInstance) {
        modelOptTaskInstance.setStatus(ModelOptInstanceStatusEnum.WAITING.getValue());
        modelOptTaskInstance.setStatusDetail(SymbolConstant.BRACKETS);
        if (StringUtils.isBlank(modelOptTaskInstance.getAlgorithmPath()) || StringUtils.isBlank(modelOptTaskInstance.getAlgorithmPath())) {
            LogUtil.error(LogEnum.MODEL_OPT, "??????????????????id={}?????????????????????????????????{}", modelOptTaskInstance.getId(), modelOptTaskInstance.getAlgorithmPath());
            throw new BusinessException(ModelOptErrorEnum.ALGORITHM_PATH_ERROR);
        }
        modelOptTaskInstanceMapper.insert(modelOptTaskInstance);
        if (!runTask(modelOptTaskInstance)) {
            LogUtil.error(LogEnum.MODEL_OPT, "?????????????????????????????????????????????id={}", modelOptTaskInstance.getStatus(), modelOptTaskInstance.getId());
            throw new BusinessException(ModelOptErrorEnum.CREATE_MODEL_INSTANCE_ERROR);
        }
    }

    /**
     * ????????????????????????
     *
     * @param resubmitDTO ??????????????????????????????
     */
    @Override
    public void resubmit(ModelOptTaskInstanceResubmitDTO resubmitDTO) {
        ModelOptTaskInstance modelOptTaskInstance = modelOptTaskInstanceMapper.selectById(resubmitDTO.getId());
        checkInstExist(modelOptTaskInstance);
        // ?????????????????????????????????????????????????????????
        if (checkUnfinishedInst(modelOptTaskInstance.getTaskId())) {
            LogUtil.error(LogEnum.MODEL_OPT, "??????????????????????????????:{}????????????????????????id={}", modelOptTaskInstance.getStatus(), resubmitDTO.getId());
            throw new BusinessException(ModelOptErrorEnum.MODEL_OPT_TASK_INSTANCE_EXIST);
        }
        ModelOptTaskInstance newInstance = rebuild(modelOptTaskInstance);
        create(newInstance);
    }

    /**
     * ????????????????????????
     *
     * @param cancelDTO ??????????????????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(ModelOptTaskInstanceCancelDTO cancelDTO) {
        ModelOptTaskInstance modelOptTaskInstance = modelOptTaskInstanceMapper.selectById(cancelDTO.getId());
        checkInstExist(modelOptTaskInstance);
        //???????????????????????????????????????
        if (!StringUtils.equalsAny(modelOptTaskInstance.getStatus(), ModelOptInstanceStatusEnum.WAITING.getValue(), ModelOptInstanceStatusEnum.RUNNING.getValue())) {
            LogUtil.error(LogEnum.MODEL_OPT, "??????????????????????????????:{}????????????????????????id={}", modelOptTaskInstance.getStatus(), cancelDTO.getId());
            throw new BusinessException(ModelOptErrorEnum.MODEL_OPT_TASK_INSTANCE_STATUS_ERROR);
        }
        UserContext curUser = userContextService.getCurUser();
        modelOptTaskInstance.setStatusDetail(SymbolConstant.BRACKETS);
        // ??????k8s??????????????????
        if (curUser == null) {
            throw new BusinessException("???????????????????????????");
        }
        try {
            String namespace = k8sNameTool.getNamespace(modelOptTaskInstance.getCreateUserId());
            modelOptJobApi.deleteByResourceName(namespace, k8sNameTool.generateResourceName(BizEnum.MODEL_OPT, cancelDTO.getId().toString()));
            modelOptTaskInstance.setStatus(ModelOptInstanceStatusEnum.CANCELED.getValue());
        } catch (KubernetesClientException e) {
            LogUtil.error(LogEnum.MODEL_OPT, "Failed to cancel task instance,exception: {}", e.getMessage());
            modelOptTaskInstance.setStatus(ModelOptInstanceStatusEnum.EXEC_FAILED.getValue());
            modelOptTaskInstance.putStatusDetail("??????????????????????????????", e.getMessage());
        }
        modelOptTaskInstance.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        modelOptTaskInstanceMapper.updateById(modelOptTaskInstance);
    }

    /**
     * ??????????????????????????????
     *
     * @param detailDTO ??????????????????????????????
     * @return ModelOptTaskInstanceVO ??????????????????
     */
    @Override
    public ModelOptTaskInstanceQueryVO getInstDetail(ModelOptTaskInstanceDetailDTO detailDTO) {
        ModelOptTaskInstance modelOptTaskInstance = modelOptTaskInstanceMapper.selectById(detailDTO.getId());
        checkInstExist(modelOptTaskInstance);
        return buildResult(modelOptTaskInstance);
    }

    /**
     * k8s????????????????????????
     *
     * @param req ????????????????????????????????????
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean modelOptCallBack(ModelOptK8sPodCallbackCreateDTO req) {
        String resourceInfo = k8sNameTool.getResourceInfoFromResourceName(BizEnum.MODEL_OPT, req.getResourceName());
        if (StringUtils.isBlank(resourceInfo)) {
            LogUtil.error(LogEnum.MODEL_OPT, "Can't resolve resourceName = {}", req.getResourceName());
            return false;
        }
        Long instId = Long.valueOf(resourceInfo);
        ModelOptTaskInstance modelOptTaskInstance = modelOptTaskInstanceMapper.selectById(instId);
        checkInstExist(modelOptTaskInstance);
        //???????????????????????????????????????????????????????????????
        if (ModelOptInstanceStatusEnum.COMPLETED.getValue().equals(modelOptTaskInstance.getStatus())
                || ModelOptInstanceStatusEnum.CANCELED.getValue().equals(modelOptTaskInstance.getStatus())) {
            return true;
        }
        if (PodPhaseEnum.RUNNING.getPhase().equals(req.getPhase())) {
            modelOptTaskInstance.setStatus(ModelOptInstanceStatusEnum.RUNNING.getValue());
            modelOptTaskInstance.setStartTime(DateUtil.getCurrentTimestamp());
        }
        if (PodPhaseEnum.FAILED.getPhase().equals(req.getPhase())) {
            modelOptTaskInstance.setStatus(ModelOptInstanceStatusEnum.EXEC_FAILED.getValue());
        }
        //???????????????????????????????????????
        if (PodPhaseEnum.DELETED.getPhase().equals(req.getPhase()) && !ModelOptInstanceStatusEnum.EXEC_FAILED.getValue().equals(modelOptTaskInstance.getStatus())) {
            modelOptTaskInstance.setStatus(ModelOptInstanceStatusEnum.CANCELED.getValue());
        }
        if (PodPhaseEnum.SUCCEEDED.getPhase().equals(req.getPhase())) {
            modelOptTaskInstance.setStatus(ModelOptInstanceStatusEnum.COMPLETED.getValue());
            modelOptTaskInstance.setEndTime(DateUtil.getCurrentTimestamp());
            if (StringUtils.isBlank(modelOptTaskInstance.getOptResultBefore())
                    || StringUtils.isBlank(modelOptTaskInstance.getOptResultAfter())) {
                try {
                    String optResultBefore = minioUtil.readString(bucket, modelOptTaskInstance.getOptResultJsonPathBefore() + ModelOptConstant.OPTIMIZE_JSON_NAME);
                    String optResultAfter = minioUtil.readString(bucket, modelOptTaskInstance.getOptResultJsonPathAfter() + ModelOptConstant.OPTIMIZE_JSON_NAME);
                    modelOptTaskInstance.setOptResultBefore(optResultBefore);
                    modelOptTaskInstance.setOptResultAfter(optResultAfter);
                } catch (Exception e) {
                    LogUtil.error(LogEnum.MODEL_OPT, "Read json file field by k8s callback???instance id={},Exception={}", instId, e);
                    modelOptTaskInstance.putStatusDetail("k8s????????????????????????", e.getMessage());
                }
            }
        }
        //???????????????????????????delete????????????????????????????????????????????????????????????
        if (StringUtils.isEmpty(req.getMessages()) && !PodPhaseEnum.DELETED.getPhase().equals(req.getPhase())) {
            modelOptTaskInstance.removeStatusDetail(req.getPodName());
        } else {
            modelOptTaskInstance.putStatusDetail(req.getPodName(), req.getMessages());
        }
        LogUtil.info(LogEnum.MODEL_OPT, "The callback optimize message={} ,req message={}", modelOptTaskInstance, req.getMessages());
        return updateById(modelOptTaskInstance);
    }

    /**
     * ??????K8S????????????????????????
     */
    @Override
    public void syncInstanceStatus() {
        // ????????????????????????????????????????????????
        List<ModelOptTaskInstance> taskInstanceList = modelOptTaskInstanceMapper.selectWaitingFor5MinutesInstances();
        // ??????????????????????????????????????????????????????
        for (ModelOptTaskInstance instance : taskInstanceList) {
            UserContext curUser = new UserContext();
            curUser.setId(instance.getCreateUserId());
            curUser.setUsername("????????????????????????????????????");
            String namespace = k8sNameTool.getNamespace(instance.getCreateUserId());
            String resourceName = k8sNameTool.generateResourceName(BizEnum.MODEL_OPT, instance.getId().toString());
            BizJob bizJob = modelOptJobApi.getWithResourceName(namespace, resourceName);
            if (K8sResponseEnum.NOT_FOUND.getCode().equals(bizJob.getCode())) {
                // k8s??????pod???????????????????????????
                ModelOptTaskInstance modelOptTaskInstance = modelOptTaskInstanceMapper.selectById(instance.getId());
                checkInstExist(modelOptTaskInstance);
                modelOptTaskInstanceMapper.markInstanceExecFailed(instance.getId());
            }
        }
    }

    /**
     * ??????????????????????????????
     *
     * @param modelOptTaskInstance ????????????????????????
     */
    public void checkInstExist(ModelOptTaskInstance modelOptTaskInstance) {
        if (modelOptTaskInstance == null) {
            throw new BusinessException(ModelOptErrorEnum.MODEL_OPT_TASK_INSTANCE_ABSENT);
        }
    }

    /**
     * ????????????????????????
     *
     * @param instance ????????????????????????
     * @return ??????????????????????????????
     */
    private ModelOptTaskInstance rebuild(ModelOptTaskInstance instance) {
        ModelOptTaskInstance newInstance = new ModelOptTaskInstance();
        newInstance.setTaskId(instance.getTaskId())
                .setTaskName(instance.getTaskName())
                .setIsBuiltIn(instance.getIsBuiltIn())
                .setModelId(instance.getModelId())
                .setModelName(instance.getModelName())
                .setModelAddress(instance.getModelAddress())
                .setAlgorithmId(instance.getAlgorithmId())
                .setAlgorithmType(instance.getAlgorithmType())
                .setAlgorithmName(instance.getAlgorithmName())
                .setAlgorithmPath(instance.getAlgorithmPath())
                .setDatasetName(instance.getDatasetName())
                .setDatasetPath(instance.getDatasetPath())
                .setCommand(instance.getCommand())
                .setParams(instance.getParams());
        return newInstance;
    }

    /**
     * @param instance ????????????
     * @return boolean ??????k8s????????????
     */
    private boolean runTask(ModelOptTaskInstance instance) {
        PtModelOptimizationJobBO jobBo = new PtModelOptimizationJobBO();
        UserContext curUser = userContextService.getCurUser();
        // ??????namespace,??????????????????
        if (curUser == null) {
            throw new BusinessException("???????????????????????????");
        }
        String namespace = k8sNameTool.getNamespace(instance.getCreateUserId());
        String command = getCommand(instance);
        LogUtil.info(LogEnum.MODEL_OPT, "??????{}????????????????????????????????????{}?????????id={}", curUser.getUsername(), command, instance.getId());
        String logPath = SymbolConstant.SLASH + BizPathEnum.MODEL_OPT.getBizPath() + SymbolConstant.SLASH + curUser.getId() + SymbolConstant.SLASH + instance.getTaskId() + SymbolConstant.SLASH + instance.getId() + ModelOptConstant.OPTIMIZE_LOG;
        String outputModelDir = SymbolConstant.SLASH + BizPathEnum.MODEL_OPT.getBizPath() + SymbolConstant.SLASH + curUser.getId() + SymbolConstant.SLASH + instance.getTaskId() + SymbolConstant.SLASH + instance.getId() + ModelOptConstant.OPTIMIZE_MODEL;
        String optResultJsonPathBefore = SymbolConstant.SLASH + BizPathEnum.MODEL_OPT.getBizPath() + SymbolConstant.SLASH + curUser.getId() + SymbolConstant.SLASH + instance.getTaskId() + SymbolConstant.SLASH + instance.getId() + ModelOptConstant.OPTIMIZE_JSON_BEFORE;
        String optResultJsonPathAfter = SymbolConstant.SLASH + BizPathEnum.MODEL_OPT.getBizPath() + SymbolConstant.SLASH + curUser.getId() + SymbolConstant.SLASH + instance.getTaskId() + SymbolConstant.SLASH + instance.getId() + ModelOptConstant.OPTIMIZE_JSON_AFTER;
        String taskIdentify = resourceCache.getTaskIdentify(instance.getTaskId(), instance.getTaskName(), modelOptIdPrefix);
        jobBo.setNamespace(namespace);
        jobBo.setName(k8sNameTool.generateResourceName(BizEnum.MODEL_OPT, instance.getId().toString()));
        jobBo.setCpuNum(ModelOptConstant.CPU_NUM);
        jobBo.setMemNum(ModelOptConstant.MEMORY_NUM);
        jobBo.setGpuNum(ModelOptConstant.GPU_NUM);
        jobBo.setCmdLines(Arrays.asList("-c", command));
        jobBo.putNfsMounts(ModelOptConstant.DATASET_MOUNT_PATH, k8sNameTool.getAbsolutePath(instance.getDatasetPath()));
        jobBo.putNfsMounts(ModelOptConstant.INPUT_MODEL_BEFORE_MOUNT_PATH, k8sNameTool.getAbsolutePath(instance.getModelAddress()));
        jobBo.putNfsMounts(ModelOptConstant.ALGORITHM_MOUNT_PATH, k8sNameTool.getAbsolutePath(instance.getAlgorithmPath()));
        jobBo.putNfsMounts(ModelOptConstant.OUTPUT_LOG_MOUNT_PATH, k8sNameTool.getAbsolutePath(logPath));
        jobBo.putNfsMounts(ModelOptConstant.OUTPUT_MODEL_MOUNT_PATH, k8sNameTool.getAbsolutePath(outputModelDir));
        jobBo.putNfsMounts(ModelOptConstant.OUTPUT_RESULT_BEFORE_MOUNT_PATH, k8sNameTool.getAbsolutePath(optResultJsonPathBefore));
        jobBo.putNfsMounts(ModelOptConstant.OUTPUT_RESULT_AFTER_MOUNT_PATH, k8sNameTool.getAbsolutePath(optResultJsonPathAfter));
        jobBo.setBusinessLabel(k8sNameTool.getPodLabel(BizEnum.MODEL_OPT));
        jobBo.setImage(optimizeImage);
        jobBo.setTaskIdentifyLabel(taskIdentify);

        // ??????k8s??????
        BizJob bizJob = modelOptJobApi.create(jobBo);
        if (null == bizJob || !bizJob.isSuccess()) {
            String message = null == bizJob ? "???????????????" : bizJob.getMessage();
            LogUtil.error(
                    LogEnum.MODEL_OPT,
                    "??????{}??????????????????????????????, k8s?????????????????????, ??????id={}, ???????????????{}, ??????????????????{}",
                    curUser.getUsername(), instance.getId(), instance, message);
            return false;
        }
        instance.setLogPath(logPath);
        instance.setOutputModelDir(outputModelDir);
        instance.setOptResultJsonPathBefore(optResultJsonPathBefore);
        instance.setOptResultJsonPathAfter(optResultJsonPathAfter);
        return updateById(instance);
    }

    /**
     * ?????????????????????????????????
     *
     * @param instance ??????????????????
     * @return ?????????????????????
     */
    private String getCommand(ModelOptTaskInstance instance) {
        String command = SymbolConstant.BLANK;
        if (instance.getIsBuiltIn()) {
            // ?????????????????????
            if (OptimizeTypeEnum.DISTILL.getType().equals(instance.getAlgorithmType())) {
                command = DistillCommandEnum.getCommandByName(instance.getAlgorithmName());
                if (command == null) {
                    throw new BusinessException("????????????????????????");
                }
                command = String.format(command, ModelOptConstant.DATASET_MOUNT_PATH,
                        ModelOptConstant.INPUT_MODEL_BEFORE_MOUNT_PATH,
                        ModelOptConstant.OUTPUT_LOG_MOUNT_PATH,
                        ModelOptConstant.OUTPUT_RESULT_BEFORE_MOUNT_PATH,
                        instance.getDatasetName(),
                        ModelOptConstant.DATASET_MOUNT_PATH,
                        ModelOptConstant.OUTPUT_MODEL_MOUNT_PATH + ModelOptConstant.OPTIMIZE_JSON_AFTER,
                        ModelOptConstant.OUTPUT_LOG_MOUNT_PATH,
                        ModelOptConstant.OUTPUT_RESULT_AFTER_MOUNT_PATH,
                        instance.getDatasetName());
            }
            if (OptimizeTypeEnum.SLIMMING.getType().equals(instance.getAlgorithmType())) {
                command = String.format(ModelOptConstant.OPT_START_SLIMMING_COMMAND,
                        ModelOptConstant.NEURONAL_PRUNING.equals(instance.getAlgorithmName()) ? "bn" : instance.getAlgorithmName(),
                        instance.getModelName(),
                        instance.getDatasetName(),
                        ModelOptConstant.DATASET_MOUNT_PATH,
                        ModelOptConstant.OUTPUT_MODEL_MOUNT_PATH,
                        ModelOptConstant.OUTPUT_LOG_MOUNT_PATH,
                        ModelOptConstant.OUTPUT_RESULT_BEFORE_MOUNT_PATH,
                        ModelOptConstant.OUTPUT_RESULT_AFTER_MOUNT_PATH);
            }
            if (OptimizeTypeEnum.QUANTIFY.getType().equals(instance.getAlgorithmType())) {
                command = String.format(ModelOptConstant.OPT_START_QUANTIFY_COMMAND, instance.getModelName(),
                        ModelOptConstant.INPUT_MODEL_BEFORE_MOUNT_PATH,
                        ModelOptConstant.DATASET_MOUNT_PATH,
                        ModelOptConstant.OUTPUT_LOG_MOUNT_PATH,
                        ModelOptConstant.OUTPUT_RESULT_BEFORE_MOUNT_PATH,
                        instance.getModelName(),
                        ModelOptConstant.INPUT_MODEL_BEFORE_MOUNT_PATH,
                        ModelOptConstant.DATASET_MOUNT_PATH,
                        ModelOptConstant.OUTPUT_LOG_MOUNT_PATH,
                        ModelOptConstant.OUTPUT_RESULT_AFTER_MOUNT_PATH);
            }
        } else {
            // ??????????????????????????????
            command = String.format(ModelOptConstant.MY_OPT_COMMAND,
                    ModelOptConstant.ALGORITHM_MOUNT_PATH,
                    instance.getCommand() + getParams(instance.getParams()));
        }
        return command;
    }

    /**
     * ??????????????????
     *
     * @return String ?????????????????????
     */
    public String getParams(JSONObject params) {
        StringBuilder paramStr = new StringBuilder();
        for (String key : params.keySet()) {
            if (Objects.nonNull(params.get(key))) {
                paramStr.append(" --").append(key).append(SymbolConstant.FLAG_EQUAL + SymbolConstant.APOSTROPHE).append(params.get(key)).append(SymbolConstant.APOSTROPHE);
            }
        }
        return paramStr.toString();
    }

    /**
     * ?????????????????????????????????????????????????????????
     *
     * @param taskId ??????id
     * @return Boolean ??????????????????????????????????????????
     */
    @Override
    public Boolean checkUnfinishedInst(Long taskId) {
        LambdaQueryWrapper<ModelOptTaskInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelOptTaskInstance::getTaskId, taskId)
                .in(ModelOptTaskInstance::getStatus, Arrays.asList(ModelOptInstanceStatusEnum.WAITING.getValue(), ModelOptInstanceStatusEnum.RUNNING.getValue()));
        List<ModelOptTaskInstance> list = modelOptTaskInstanceMapper.selectList(wrapper);
        return CollectionUtils.isNotEmpty(list);
    }

    /**
     * ????????????id????????????
     *
     * @param taskId ??????id
     * @return Integer ??????????????????
     */
    @Override
    public int deleteByTaskId(Long taskId) {
        LambdaQueryWrapper<ModelOptTaskInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelOptTaskInstance::getTaskId, taskId);
        String taskIdentify = (String) redisUtils.get(modelOptIdPrefix + String.valueOf(taskId));
        if (org.dubhe.biz.base.utils.StringUtils.isNotEmpty(taskIdentify)) {
            redisUtils.del(taskIdentify, modelOptIdPrefix + String.valueOf(taskId));
        }
        return modelOptTaskInstanceMapper.delete(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(ModelOptTaskInstanceDeleteDTO modelOptTaskInstanceDeleteDTO) {
        UserContext curUser = userContextService.getCurUser();
        if (curUser == null) {
            throw new BusinessException("???????????????????????????");
        }
        ModelOptTaskInstance modelOptTaskInstance = modelOptTaskInstanceMapper.selectById(modelOptTaskInstanceDeleteDTO.getId());
        checkInstExist(modelOptTaskInstance);
        if (StringUtils.equalsAny(modelOptTaskInstance.getStatus(), ModelOptInstanceStatusEnum.RUNNING.getValue(), ModelOptInstanceStatusEnum.WAITING.getValue())) {
            throw new BusinessException(ModelOptErrorEnum.MODEL_OPT_TASK_INSTANCE_STATUS_ERROR);
        }
        String namespace = k8sNameTool.getNamespace(modelOptTaskInstance.getCreateUserId());
        modelOptTaskInstanceMapper.deleteById(modelOptTaskInstanceDeleteDTO.getId());
        // ??????k8s??????????????????
        modelOptJobApi.deleteByResourceName(namespace,
                k8sNameTool.generateResourceName(BizEnum.MODEL_OPT, modelOptTaskInstanceDeleteDTO.getId().toString()));
    }

    /**
     * ???????????????????????????
     *
     * @param ptModelStatusQueryDTO ??????????????????DTO
     * @return Boolean ???????????????true???????????????false???????????????
     */
    @Override
    public Boolean getOptimizeModelStatus(PtModelStatusQueryDTO ptModelStatusQueryDTO) {
        if (ptModelStatusQueryDTO == null) {
            LogUtil.error(LogEnum.MODEL_OPT, "The ptModelStatusQueryDTO set is empty");
            throw new BusinessException("????????????");
        }

        if (CollectionUtils.isNotEmpty(ptModelStatusQueryDTO.getModelIds())
                && CollectionUtils.isNotEmpty(ptModelStatusQueryDTO.getModelBranchIds())) {
            LogUtil.error(LogEnum.MODEL_OPT, "The modelId and modelBranchId cannot be passed in at the same time");
            throw new BusinessException("modelId???ModelBranchId??????????????????");
        }
        LambdaQueryWrapper<ModelOptTaskInstance> wrapper = new LambdaQueryWrapper<>();
        if (CollectionUtils.isNotEmpty(ptModelStatusQueryDTO.getModelIds())) {
            wrapper.in(ModelOptTaskInstance::getModelId, ptModelStatusQueryDTO.getModelIds());
        } else if (CollectionUtils.isNotEmpty(ptModelStatusQueryDTO.getModelBranchIds())) {
            wrapper.in(ModelOptTaskInstance::getModelBranchId, ptModelStatusQueryDTO.getModelBranchIds());
        } else {
            LogUtil.error(LogEnum.MODEL_OPT, "The modelId, modelbranchId cannot set is empty in at the same time");
            throw new BusinessException("???????????????????????????");
        }
        List<ModelOptTaskInstance> instanceList = modelOptTaskInstanceMapper.selectList(wrapper);
        if (CollectionUtils.isNotEmpty(instanceList)) {
            for (ModelOptTaskInstance instance : instanceList) {
                if (StringUtils.equalsAny(instance.getStatus(), ModelOptInstanceStatusEnum.WAITING.getValue(), ModelOptInstanceStatusEnum.RUNNING.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * ??????????????????
     *
     * @param modelOptTaskInstance ??????????????????
     * @return ModelOptTaskInstanceQueryVO ????????????????????????
     */
    public ModelOptTaskInstanceQueryVO buildResult(ModelOptTaskInstance modelOptTaskInstance) {
        //????????????????????????????????????
        ModelOptTaskInstanceQueryVO modelOptTaskInstanceVO = new ModelOptTaskInstanceQueryVO();
        BeanUtils.copyProperties(modelOptTaskInstance, modelOptTaskInstanceVO);
        //??????????????????????????????
        UserContext curUser = userContextService.getCurUser();
        if (curUser == null) {
            throw new BusinessException("???????????????????????????");
        }
        String nameSpace = k8sNameTool.getNamespace(modelOptTaskInstance.getCreateUserId());
        List<PodVO> pods = podService.getPods(new PodQueryDTO(nameSpace, k8sNameTool.generateResourceName(BizEnum.MODEL_OPT, modelOptTaskInstance.getId().toString())));
        if (!CollectionUtils.isEmpty(pods)) {
            modelOptTaskInstanceVO.setPodName(pods.get(NumberConstant.NUMBER_0).getPodName());
        }
        modelOptTaskInstanceVO.setNamespace(nameSpace);
        if (StringUtils.isEmpty(modelOptTaskInstance.getOptResultBefore()) ||
                StringUtils.isEmpty(modelOptTaskInstance.getOptResultAfter())) {
            return modelOptTaskInstanceVO;
        }
        //????????????????????????????????????
        DataResponseBody<DictVO> dataResponseBody = dictClient.findDictByName(ModelOptConstant.TASK_RESULT_DICT_NAME);
        DictVO dictVO = null;
        if (dataResponseBody.succeed()) {
            dictVO = dataResponseBody.getData();
        }
        List<ModelOptResultQueryVO> resultList = new ArrayList<>();
        if (dictVO == null || CollectionUtils.isEmpty(dictVO.getDictDetails())) {
            LogUtil.error(LogEnum.MODEL_OPT, "The model optimization task result dictionary is empty. The opt_result dictionary is {}", JSONObject.toJSONString(dictVO));
            throw new BusinessException(ModelOptErrorEnum.MODEL_OPT_TASK_RESULT_DICT_EMPTY);
        }
        //???????????????????????????
        JSONObject before = JSONObject.parseObject(modelOptTaskInstance.getOptResultBefore());
        JSONObject after = JSONObject.parseObject(modelOptTaskInstance.getOptResultAfter());
        for (DictDetailVO dictDetail : dictVO.getDictDetails()) {
            if (StringUtils.isEmpty(dictDetail.getLabel()) || StringUtils.isEmpty(dictDetail.getValue())) {
                LogUtil.error(LogEnum.MODEL_OPT, "The model optimization task result dictionary details are empty.The opt_result dictionary details are {}", JSONObject.toJSONString(dictDetail));
                throw new BusinessException(ModelOptErrorEnum.MODEL_OPT_TASK_RESULT_DICT_EMPTY);
            }
            //????????????????????????
            ModelOptResultQueryVO modelOptResultVO = new ModelOptResultQueryVO();
            modelOptResultVO.setName(dictDetail.getLabel());
            //????????????????????????????????????
            String beforeStr = before.getOrDefault(dictDetail.getLabel(), SymbolConstant.ZERO).toString().replace(dictDetail.getValue(), SymbolConstant.BLANK);
            String afterStr = after.getOrDefault(dictDetail.getLabel(), SymbolConstant.ZERO).toString().replace(dictDetail.getValue(), SymbolConstant.BLANK);
            Float beforeValue = Float.parseFloat(beforeStr);
            Float afterValue = Float.parseFloat(afterStr);
            if (ModelOptConstant.ACCURACY.equals(dictDetail.getLabel())) {
                beforeValue = beforeValue * NumberConstant.NUMBER_100;
                afterValue = afterValue * NumberConstant.NUMBER_100;
            }
            modelOptResultVO.setBefore(beforeValue);
            modelOptResultVO.setAfter(afterValue);
            modelOptResultVO.setUnit(dictDetail.getValue());
            modelOptResultVO.setPositive(Integer.toString(afterValue.compareTo(beforeValue)));
            resultList.add(modelOptResultVO);
        }
        modelOptTaskInstanceVO.setOptResult(resultList);
        return modelOptTaskInstanceVO;
    }
}
