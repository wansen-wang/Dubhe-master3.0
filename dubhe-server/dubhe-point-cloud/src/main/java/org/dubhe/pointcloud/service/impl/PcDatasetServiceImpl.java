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
package org.dubhe.pointcloud.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.base.constant.NumberConstant;
import org.dubhe.biz.base.constant.StringConstant;
import org.dubhe.biz.base.constant.SymbolConstant;
import org.dubhe.biz.base.context.UserContext;
import org.dubhe.biz.base.dto.PtImageQueryUrlDTO;
import org.dubhe.biz.base.dto.PtModelBranchQueryByIdDTO;
import org.dubhe.biz.base.dto.PtModelInfoQueryByIdDTO;
import org.dubhe.biz.base.dto.TrainAlgorithmSelectByIdDTO;
import org.dubhe.biz.base.enums.ImageTypeEnum;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.service.UserContextService;
import org.dubhe.biz.base.utils.DateUtil;
import org.dubhe.biz.base.utils.ReflectionUtils;
import org.dubhe.biz.base.utils.StringUtils;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.base.vo.LabelGroupBaseVO;
import org.dubhe.biz.base.vo.PtModelBranchQueryVO;
import org.dubhe.biz.base.vo.PtModelInfoQueryVO;
import org.dubhe.biz.base.vo.TrainAlgorithmQureyVO;
import org.dubhe.biz.dataresponse.factory.DataResponseFactory;
import org.dubhe.biz.db.base.PageQueryBase;
import org.dubhe.biz.db.utils.PageUtil;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.permission.base.BaseService;
import org.dubhe.biz.statemachine.dto.StateChangeDTO;
import org.dubhe.k8s.api.LogMonitoringApi;
import org.dubhe.k8s.api.PodApi;
import org.dubhe.k8s.cache.ResourceCache;
import org.dubhe.k8s.domain.PtBaseResult;
import org.dubhe.k8s.domain.bo.LogMonitoringBO;
import org.dubhe.k8s.domain.dto.PodQueryDTO;
import org.dubhe.k8s.domain.resource.BizPod;
import org.dubhe.k8s.domain.vo.LogMonitoringVO;
import org.dubhe.k8s.domain.vo.PodVO;
import org.dubhe.k8s.enums.K8sResponseEnum;
import org.dubhe.k8s.service.PodService;
import org.dubhe.k8s.utils.K8sNameTool;
import org.dubhe.pointcloud.client.AlgorithmClient;
import org.dubhe.pointcloud.client.DataClient;
import org.dubhe.pointcloud.client.ImageClient;
import org.dubhe.pointcloud.client.ModelClient;
import org.dubhe.pointcloud.common.Constant;
import org.dubhe.pointcloud.dao.PcAnnotationDetailMapper;
import org.dubhe.pointcloud.dao.PcDatasetFileMapper;
import org.dubhe.pointcloud.dao.PcDatasetMapper;
import org.dubhe.pointcloud.domain.dto.AnnotationK8sPodCallbackCreateDTO;
import org.dubhe.pointcloud.domain.dto.DatasetDetailDTO;
import org.dubhe.pointcloud.domain.dto.PcDatasetAutoDTO;
import org.dubhe.pointcloud.domain.dto.PcDatasetCreateDTO;
import org.dubhe.pointcloud.domain.dto.PcDatasetLogQueryDTO;
import org.dubhe.pointcloud.domain.dto.PcDatasetQueryDTO;
import org.dubhe.pointcloud.domain.dto.PcDatasetRunParamDTO;
import org.dubhe.pointcloud.domain.dto.PcDatasetUpdateDTO;
import org.dubhe.pointcloud.domain.entity.PcAnnotationDetail;
import org.dubhe.pointcloud.domain.entity.PcDataset;
import org.dubhe.pointcloud.domain.vo.DatasetLogQueryVO;
import org.dubhe.pointcloud.domain.vo.DatasetQueryVO;
import org.dubhe.pointcloud.domain.vo.PcDatasetTrainVO;
import org.dubhe.pointcloud.enums.ErrorEnum;
import org.dubhe.pointcloud.enums.PcDatasetMachineStatusEnum;
import org.dubhe.pointcloud.enums.PhaseAnnotationEnum;
import org.dubhe.pointcloud.machine.constant.PcDatasetEventMachineConstant;
import org.dubhe.pointcloud.service.PcDatasetService;
import org.dubhe.pointcloud.task.DeployAsyncTask;
import org.dubhe.pointcloud.util.CommonUtil;
import org.dubhe.pointcloud.util.PathUtil;
import org.dubhe.pointcloud.util.StateMachineUtil;
import org.dubhe.recycle.config.RecycleConfig;
import org.dubhe.recycle.domain.dto.RecycleCreateDTO;
import org.dubhe.recycle.domain.dto.RecycleDetailCreateDTO;
import org.dubhe.recycle.enums.RecycleModuleEnum;
import org.dubhe.recycle.enums.RecycleResourceEnum;
import org.dubhe.recycle.enums.RecycleTypeEnum;
import org.dubhe.recycle.service.RecycleService;
import org.dubhe.recycle.utils.RecycleTool;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @description ????????????????????????
 * @date 2022-04-01
 **/
@Service
public class PcDatasetServiceImpl implements PcDatasetService {

    @Resource
    private UserContextService userContextService;

    @Resource
    private PcDatasetMapper pcDatasetMapper;

    @Resource
    private PcDatasetFileMapper pcDatasetFileMapper;

    @Resource
    private DeployAsyncTask deployAsyncTask;

    @Resource
    private ImageClient imageClient;

    @Resource
    private ModelClient modelClient;

    @Resource
    private AlgorithmClient algorithmClient;

    @Resource
    private K8sNameTool k8sNameTool;

    @Autowired
    private PodService podService;

    @Resource
    private ResourceCache resourceCache;

    @Resource
    private RecycleConfig recycleConfig;


    @Value("Task:POINT_CLOUD:" + "${spring.profiles.active}_pc_dataset_id_")
    private String pcDatasetIdPrefix;

    @Value("Annotation:POINT_CLOUD:" + "${spring.profiles.active}_user_id:%s_dataset_id:%s")
    private String pointCloudAnnotationPrefix;


    @Resource
    private RecycleService recycleService;

    @Resource
    private PathUtil pathUtil;

    @Resource
    private DataClient dataClient;

    @Resource
    private LogMonitoringApi logMonitoringApi;

    @Autowired
    private PodApi podApi;

    @Resource
    private PcAnnotationDetailMapper pcAnnotationDetailMapper;
    @Autowired
    private RedissonClient redissonClient;


    public final static List<String> FILED_MANES;

    static {
        FILED_MANES = ReflectionUtils.getFieldNames(PcDatasetQueryDTO.class);
    }

    @Override
    public Map<String, Object> query(PcDatasetQueryDTO pcDatasetQueryDTO) {
        UserContext user = userContextService.getCurUser();
        QueryWrapper<PcDataset> wrapper = new QueryWrapper<>();
        if (!CollectionUtils.isEmpty(pcDatasetQueryDTO.getStatus())) {
            wrapper.in("status", pcDatasetQueryDTO.getStatus());
        }
        if (!BaseService.isAdmin(user)) {
            wrapper.eq("create_user_id", user.getId());
        }
        //?????????????????????
        if (StringUtils.isEmpty(pcDatasetQueryDTO.getName())) {
            return queryDatasets(pcDatasetQueryDTO, wrapper);
        }
        //????????????????????????
        if (StringConstant.PATTERN_NUMBER.matcher(pcDatasetQueryDTO.getName()).matches()) {
            if (pcDatasetQueryDTO.getName().length() > String.valueOf(Long.MAX_VALUE).length()) {
                throw new BusinessException("??????id??????????????????");
            }
            wrapper.and(QueryWrapper -> QueryWrapper.eq("id", Long.valueOf(pcDatasetQueryDTO.getName()))
                    .or()
                    .or().like("lower(name)", pcDatasetQueryDTO.getName().toLowerCase()));
        } else {
            // ?????????????????????????????????, ?????????????????????????????????
            String lowerName = CommonUtil.escapeChar(pcDatasetQueryDTO.getName().toLowerCase());
            wrapper.and(QueryWrapper -> QueryWrapper.apply("lower(name) like '%" + lowerName + "%' escape '/' "));
        }
        return queryDatasets(pcDatasetQueryDTO, wrapper);
    }

    /** ???????????????
     * @param pcDatasetQueryDTO ?????????????????????
     * @param wrapper
     * @return Map<String, Object>
     */
    public Map<String, Object> queryDatasets(PcDatasetQueryDTO pcDatasetQueryDTO, QueryWrapper<PcDataset> wrapper) {
        Page page = new Page(null == pcDatasetQueryDTO.getCurrent() ? MagicNumConstant.ONE : pcDatasetQueryDTO.getCurrent(),
                null == pcDatasetQueryDTO.getSize() ? MagicNumConstant.TEN : pcDatasetQueryDTO.getSize());
        wrapper = CommonUtil.getSortWrapper(pcDatasetQueryDTO, wrapper, FILED_MANES);
        IPage<PcDataset> datasetsIPage = pcDatasetMapper.selectPage(page, wrapper);
        //???????????????
        List<DatasetQueryVO> queryVOList = datasetsIPage.getRecords().stream().map(pcDataset -> {
            DatasetQueryVO queryVO = new DatasetQueryVO();
            BeanUtils.copyProperties(pcDataset, queryVO);
            return queryVO;
        }).collect(Collectors.toList());
        //???????????????????????????????????????
        if (CollectionUtils.isEmpty(queryVOList)) {
            return PageUtil.toPage(datasetsIPage, queryVOList);
        }
        return PageUtil.toPage(datasetsIPage, queryLabelGroupData(queryVOList));
    }

    /**
     * ?????????????????????
     * @param queryVOList ???????????????
     */
    private List<DatasetQueryVO> queryLabelGroupData(List<DatasetQueryVO> queryVOList) {
        Set<Long> labelGroupIds = queryVOList.stream().map(DatasetQueryVO::getLabelGroupId).collect(Collectors.toSet());
        DataResponseBody<List<LabelGroupBaseVO>> dataResponseBody = dataClient.queryLabelGroupList(labelGroupIds);
        if (!dataResponseBody.succeed()) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset group with ids {} does not exist,error message:{}", labelGroupIds, dataResponseBody.getMsg());
            throw new BusinessException(dataResponseBody.getMsg());
        }
        Map<Long, LabelGroupBaseVO> labelGroupIdBaseVOMap = dataResponseBody.getData().stream().collect(Collectors.toMap(LabelGroupBaseVO::getId, labelGroupBaseVO -> labelGroupBaseVO));
        for (DatasetQueryVO queryVO : queryVOList) {
            LabelGroupBaseVO labelGroupBaseVO = labelGroupIdBaseVOMap.getOrDefault(queryVO.getLabelGroupId(), null);
            if (Objects.isNull(labelGroupBaseVO)) {
                LogUtil.error(LogEnum.POINT_CLOUD, "The dataset group with id {} does not exist", queryVO.getLabelGroupId());
                continue;
            }
            if (!Objects.equals(labelGroupBaseVO.getLabelGroupType(), Constant.LABEL_GROUP_POINT_CLOUD_TYPE)) {
                LogUtil.error(LogEnum.POINT_CLOUD, "The dataset group with id {} does not point cloud group", queryVO.getLabelGroupId());
                continue;
            }
            queryVO.setLabelGroupName(labelGroupBaseVO.getName());
            queryVO.setLabelGroupType(labelGroupBaseVO.getType());
        }
        return queryVOList;
    }

    @Override
    public DataResponseBody create(PcDatasetCreateDTO pcDatasetCreateDTO) {
        //???????????????????????????
        checkNameExist(pcDatasetCreateDTO.getName());
        //???????????????????????????
        checkLabelGroupExist(pcDatasetCreateDTO.getLabelGroupId());
        PcDataset pcDataset = new PcDataset();
        BeanUtils.copyProperties(pcDatasetCreateDTO, pcDataset);
        UserContext user = userContextService.getCurUser();
        pcDataset.setCreateUserId(user.getId());
        pcDatasetMapper.insert(pcDataset);
        return DataResponseFactory.success(pcDataset);
    }

    /**
     * ????????????????????????
     * @param name ???????????????
     */
    private void checkNameExist(String name) {
        List<PcDataset> datasetsList = pcDatasetMapper.selectList(new LambdaQueryWrapper<PcDataset>().eq(PcDataset::getName, name));
        if (!CollectionUtils.isEmpty(datasetsList)) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset with name {} already exists in the database", name);
            throw new BusinessException(ErrorEnum.DUPLICATE_DATASET_NAME);
        }
    }

    /**
     * ????????????????????????
     * @param labelGroupId ?????????id
     */
    private LabelGroupBaseVO checkLabelGroupExist(Long labelGroupId) {
        DataResponseBody<List<LabelGroupBaseVO>> dataResponseBody = dataClient.queryLabelGroupList(Collections.singleton(labelGroupId));
        if (!dataResponseBody.succeed()) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset group with id {} does not exist,error message:{}", labelGroupId, dataResponseBody.getMsg());
            throw new BusinessException(dataResponseBody.getMsg());
        }
        List<LabelGroupBaseVO> labelGroupBaseVOList = dataResponseBody.getData();
        LabelGroupBaseVO labelGroupBaseVO = labelGroupBaseVOList.stream().findFirst().get();
        if (!Objects.equals(labelGroupBaseVO.getLabelGroupType(), Constant.LABEL_GROUP_POINT_CLOUD_TYPE)) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset group with id {} does not point cloud group", labelGroupId);
            throw new BusinessException("??????????????????");
        }
        return labelGroupBaseVO;
    }

    /**
     * ???????????????????????????
     * @param datasetId ?????????id
     * @return ?????????
     */
    private PcDataset checkDatasetsExist(Long datasetId) {
        LambdaQueryWrapper<PcDataset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PcDataset::getId, datasetId);
        UserContext user = userContextService.getCurUser();
        if (!BaseService.isAdmin(user)) {
            wrapper.eq(PcDataset::getCreateUserId, user.getId());
        }
        PcDataset pcDataset = pcDatasetMapper.selectOne(wrapper);
        if (Objects.isNull(pcDataset)) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset with id {} does not exist", datasetId);
            throw new BusinessException(ErrorEnum.DATASET_DOES_NOT_EXIST_ERROR);
        }
        return pcDataset;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataResponseBody update(PcDatasetUpdateDTO pcDatasetUpdateDTO) {
        //???????????????????????????
        PcDataset pcDataset = checkDatasetsExist(pcDatasetUpdateDTO.getId());
        //????????????????????????
        if (!pcDataset.getName().equals(pcDatasetUpdateDTO.getName())) {
            //???????????????????????????????????????
            checkNameExist(pcDatasetUpdateDTO.getName());
        }
        // ????????????????????????????????????????????????????????????????????????
        if (!pcDatasetUpdateDTO.getLabelGroupId().equals(pcDataset.getLabelGroupId())
                && !(PcDatasetMachineStatusEnum.UNLABELLED.getCode().equals(pcDataset.getStatus())
                || PcDatasetMachineStatusEnum.NOT_SAMPLED.getCode().equals(pcDataset.getStatus())
                || PcDatasetMachineStatusEnum.IMPORTING.getCode().equals(pcDataset.getStatus()))) {
            throw new BusinessException(ErrorEnum.OPERATION_LABEL_GROUP_NOT_ALLOWED_IN_STATE);
        }
        //????????????????????????????????????
        if (PcDatasetMachineStatusEnum.AUTO_LABELING.getCode().equals(pcDataset.getStatus())) {
            throw new BusinessException(ErrorEnum.AUTO_LABELING_NOT_UPDATE_DATASETS);
        }
        // ??????????????????????????????????????????????????????
        if (!pcDataset.getLabelGroupId().equals(pcDatasetUpdateDTO.getLabelGroupId())) {
            checkLabelGroupExist(pcDatasetUpdateDTO.getLabelGroupId());
            pcDataset.setLabelGroupId(pcDatasetUpdateDTO.getLabelGroupId());
        }
        BeanUtils.copyProperties(pcDatasetUpdateDTO, pcDataset);
        pcDatasetMapper.updateById(pcDataset);
        return DataResponseFactory.success(pcDataset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataResponseBody delete(Set<Long> ids) {
        List<PcDataset> datasetsList = pcDatasetMapper.selectBatchIds(ids);
        if (CollectionUtils.isEmpty(datasetsList)) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The  datasets with ids {} does not exist.", ids);
            throw new BusinessException(ErrorEnum.DATASET_DOES_NOT_EXIST_ERROR);
        }
        if (datasetsList.size() != ids.size()) {
            LogUtil.warn(LogEnum.POINT_CLOUD, "The datasets with ids {} requested to be deleted and the actual number of IDs are incorrect", ids);
        }
        List<Integer> statusList = datasetsList.stream().map(PcDataset::getStatus).distinct()
                .filter(status -> PcDatasetMachineStatusEnum.AUTO_LABELING.getCode().equals(status)
                        || PcDatasetMachineStatusEnum.IMPORTING.getCode().equals(status))
                .collect(Collectors.toList());
        if (CollectionUtil.isNotEmpty(statusList)) {
            throw new BusinessException("??????????????????????????????????????????");
        }
        //?????????????????????????????????
        pcDatasetMapper.updateStatusByBatchIds(ids, Boolean.TRUE);
        //?????????????????????????????????????????????
        datasetsList.forEach(pcDataset -> pcDatasetFileMapper.updateStatusByDatasetId(pcDataset.getId(), Boolean.TRUE));
        datasetsList.forEach(this::createRecycleTask);
        return DataResponseFactory.success();

    }

    /**
     * ??????????????????
     * @param pcDataset ?????????????????????
     */
    private void createRecycleTask(PcDataset pcDataset) {
        List<RecycleDetailCreateDTO> detailList = new ArrayList<>();

        //mnio ?????????????????????
        String datasetDir = k8sNameTool.getAbsolutePath(pcDataset.getUrl());
        if (StringUtils.isNotBlank(datasetDir)) {
            detailList.add(RecycleDetailCreateDTO.builder()
                    .recycleCondition(datasetDir)
                    .recycleType(RecycleTypeEnum.FILE.getCode())
                    .recycleNote(RecycleTool.generateRecycleNote("3D?????? minio ?????????????????????", pcDataset.getId()))
                    .build());
        }


        if (pcDataset.getFileCount() > NumberConstant.NUMBER_0) {

            //minio ??????????????????
            detailList.add(RecycleDetailCreateDTO
                    .builder()
                    .recycleCondition(k8sNameTool.getAbsolutePath(pathUtil.getPcdFileUrl(pcDataset.getId(), StringUtils.EMPTY)))
                    .recycleNote(RecycleTool.generateRecycleNote("3D?????? minio ??????????????????", pcDataset.getId()))
                    .build());
        }
        //?????????????????????
        detailList.add(RecycleDetailCreateDTO.builder()
                .recycleCondition(pcDataset.getId().toString())
                .recycleType(RecycleTypeEnum.TABLE_DATA.getCode())
                .recycleNote(RecycleTool.generateRecycleNote("3D?????? ?????????DB ??????????????????", pcDataset.getId()))
                .build());

        RecycleCreateDTO recycleCreateDTO = RecycleCreateDTO.builder()
                .recycleModule(RecycleModuleEnum.BIZ_POINT_CLOUD.getValue())
                .recycleDelayDate(recycleConfig.getPointCloudValid())  //??????3???
                .recycleNote(RecycleTool.generateRecycleNote("??????3D?????????????????????", pcDataset.getName(), pcDataset.getId()))
                .recycleCustom(RecycleResourceEnum.PC_DATASET_RECYCLE_FILE.getClassName())
                .restoreCustom(RecycleResourceEnum.PC_DATASET_RECYCLE_FILE.getClassName())
                .remark(String.valueOf(pcDataset.getId()))
                .detailList(detailList)
                .build();
        recycleService.createRecycleTask(recycleCreateDTO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataResponseBody publish(Long id) {
        //?????????????????????
        PcDataset pcDataset = checkDatasetsExist(id);
        //????????????
        StateMachineUtil.stateChange(new StateChangeDTO(
                new Object[]{id},
                PcDatasetEventMachineConstant.PC_DATASET_MACHINE,
                PcDatasetEventMachineConstant.PUBLISHED_PC_DATASET_EVENT));
        DatasetQueryVO datasetQueryVO = new DatasetQueryVO();
        BeanUtils.copyProperties(pcDataset, datasetQueryVO);
        return DataResponseFactory.success(datasetQueryVO);
    }

    @Override
    public PcDataset selectById(Long datasetId) {
        return pcDatasetMapper.selectById(datasetId);
    }

    @Override
    public int updatePcDataset(LambdaUpdateWrapper<PcDataset> lambdaUpdateWrapper) {
        return pcDatasetMapper.update(null, lambdaUpdateWrapper);
    }

    @Override
    public boolean annotationPodCallback(int times, AnnotationK8sPodCallbackCreateDTO req) {

        Map<String, String> labels = req.getLables();
        Long datasetId = Long.parseLong(labels.get(Constant.DATASET_ID));
        PcDataset pcDataset = pcDatasetMapper.selectById(datasetId);
        if (Objects.isNull(pcDataset)) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset with id {} cannot be found! Request: {} Times: {}  req: {}", datasetId, Thread.currentThread(), times, req.toString());
            return false;
        }
        LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} ,req msg: {}", datasetId, req);

        //pod????????????????????????pod????????????????????????????????????????????????????????????
        if (PcDatasetMachineStatusEnum.AUTO_LABEL_STOP.getCode().equals(pcDataset.getStatus()) && !PhaseAnnotationEnum.DELETED_ANNOTATION.getPhase().equals(req.getPhase())) {
            deployAsyncTask.deleteJob(pcDataset);
            LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id{}.The dataset has been suspended,delete the running job,freeing up resource", datasetId);
            return true;
        }
        PhaseAnnotationEnum phaseAnnotationEnum = PhaseAnnotationEnum.getPhaseAnnotationEnum(req.getPhase().toLowerCase());
        if (Objects.isNull(phaseAnnotationEnum)) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset with id {}. Can not find annotation phase :{} in Enums", datasetId, req.getPhase().toLowerCase());
            return false;
        }
        phaseAnnotationEnum.machineStatusMethod(req, pcDataset);
        return true;

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataResponseBody autoAnnotation(PcDatasetAutoDTO pcDatasetAutoDTO) {
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("???????????????????????????");
        }
        PcDataset pcDataset = checkDatasetsExist(pcDatasetAutoDTO.getDatasetId());
        RLock lock = redissonClient.getLock(StringUtils.join(pointCloudAnnotationPrefix, user.getId(), pcDataset.getId()));
        try {
            if (lock.tryLock(3, 5, TimeUnit.SECONDS)) {
                PcAnnotationDetail entity = getPcAnnotationDetail(pcDatasetAutoDTO);
                entity.setCreateUserId(user.getId());
                //???????????????????????????????????????
                StateMachineUtil.stateChange(new StateChangeDTO(
                        new Object[]{pcDatasetAutoDTO.getDatasetId()},
                        PcDatasetEventMachineConstant.PC_DATASET_MACHINE,
                        PcDatasetEventMachineConstant.AUTO_LABELING_PC_DATASET_EVENT));

                //????????????????????????
                PcDatasetRunParamDTO pcDatasetRunParamDTO = getPcDatasetRunParamDTO(pcDatasetAutoDTO, pcDataset, entity);

                //??????????????????????????????????????????commit??????????????????????????????k8s?????????????????????
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                    @Override
                    public void afterCommit() {
                        deployAsyncTask.deployPod(pcDatasetRunParamDTO);
                    }
                });

                // ????????????????????????
                if (pcAnnotationDetailMapper.selectById(entity.getDatasetId()) != null) {
                    pcAnnotationDetailMapper.updateById(entity);
                } else {
                    pcAnnotationDetailMapper.insert(entity);
                }
            }
        } catch (Exception e) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset with id {}. Failed to lock,error message:{}", pcDatasetAutoDTO.getDatasetId(), e.getMessage());
            throw new BusinessException("??????????????????");
        }finally {
            lock.unlock();
        }

        return DataResponseFactory.success();
    }


    /**
     * ????????????????????????????????????
     * @param pcDatasetAutoDTO
     * @return
     */
    private PcAnnotationDetail getPcAnnotationDetail(PcDatasetAutoDTO pcDatasetAutoDTO) {
        PcAnnotationDetail entity = new PcAnnotationDetail();

        entity.setDatasetId(pcDatasetAutoDTO.getDatasetId());
        entity.setDatasetDirMapping(pcDatasetAutoDTO.getDatasetDirMapping());
        entity.setAlgorithmId(pcDatasetAutoDTO.getAlgorithmId());
        entity.setAlgorithmSource(pcDatasetAutoDTO.getAlgorithmSource());
        entity.setModelId(pcDatasetAutoDTO.getModelId());
        entity.setModelBranchId(pcDatasetAutoDTO.getModelBranchId());
        entity.setModelResource(pcDatasetAutoDTO.getModelResource());
        entity.setPoolSpecsInfo(pcDatasetAutoDTO.getPoolSpecsInfo());
        entity.setResourcesPoolNode(pcDatasetAutoDTO.getResourcesPoolNode());
        entity.setResourcesPoolType(pcDatasetAutoDTO.getResourcesPoolType());
        entity.setResourcesPoolSpecs(pcDatasetAutoDTO.getResourcesPoolSpecs());
        entity.setModelDirMapping(pcDatasetAutoDTO.getModelDirMapping());
        entity.setResultDirMapping(pcDatasetAutoDTO.getResultDirMapping());
        entity.setCommand(pcDatasetAutoDTO.getCommand());
        entity.setCreateTime(DateUtil.getCurrentTimestamp());
        entity.setUpdateTime(DateUtil.getCurrentTimestamp());

        return entity;
    }

    /**
     * ??????????????????????????????
     * @param pcDatasetAutoDTO ??????????????????
     * @param pcDataset ???????????????
     * @return PcDatasetRunParamDTO
     */
    private PcDatasetRunParamDTO getPcDatasetRunParamDTO(PcDatasetAutoDTO pcDatasetAutoDTO, PcDataset pcDataset, PcAnnotationDetail entity) {
        PcDatasetRunParamDTO pcDatasetRunParamDTO = new PcDatasetRunParamDTO();
        BeanUtils.copyProperties(pcDatasetAutoDTO, pcDatasetRunParamDTO);

        String commandShow = String.format(Constant.LOG_COMMAND, pcDatasetAutoDTO.getCommand(),
                StringUtils.isBlank(pcDatasetAutoDTO.getDatasetDirMapping()) ? Constant.DATASET_DIR : pcDatasetAutoDTO.getDatasetDirMapping(),
                StringUtils.isBlank(pcDatasetAutoDTO.getResultDirMapping()) ? Constant.RESULTS_DIR : pcDatasetAutoDTO.getResultDirMapping(),
                StringUtils.isBlank(pcDatasetAutoDTO.getModelDirMapping()) ? Constant.MODEL_DIR : pcDatasetAutoDTO.getModelDirMapping());

        TrainAlgorithmQureyVO algorithmQureyVO = getAlgorithmDir(pcDatasetAutoDTO.getAlgorithmId());
        entity.setAlgorithmName(algorithmQureyVO.getAlgorithmName());
        pcDatasetRunParamDTO.setImage(getImageUrl(pcDatasetAutoDTO))
                //??????????????????
                .setModelDir(k8sNameTool.getAbsolutePath(getModelBranchAddress(pcDatasetAutoDTO.getModelResource(), pcDatasetAutoDTO.getModelId(), pcDatasetAutoDTO.getModelBranchId(), entity)))
                //?????????????????????
                .setDatasetDir(k8sNameTool.getAbsolutePath(pcDataset.getUrl()))
                //???????????????????????????
                .setResultsDir(k8sNameTool.getAbsolutePath(pathUtil.getLabel2Url(pcDatasetAutoDTO.getDatasetId())))
                //??????????????????
                .setAlgorithmDir(k8sNameTool.getAbsolutePath(algorithmQureyVO.getCodeDir()))
                //????????????????????????
                .setCommand(String.format(Constant.COMMAND, commandShow, commandShow))
                //????????????????????????
                .setResourceInfo(pcDatasetAutoDTO.getDatasetId() + SymbolConstant.HYPHEN + StringUtils.getRandomString())
                //????????????
                .setTaskIdentify(resourceCache.getTaskIdentify(pcDatasetAutoDTO.getDatasetId(), pcDataset.getName(), pcDatasetIdPrefix))
                .setCreateUserId(pcDataset.getCreateUserId());

        entity.setImageName(pcDatasetAutoDTO.getImageName());
        entity.setImageTag(pcDatasetAutoDTO.getImageTag());
        return pcDatasetRunParamDTO;
    }

    /**
     * ??????????????????
     * @param algorithmId ??????id
     * @return String
     */
    private TrainAlgorithmQureyVO getAlgorithmDir(Long algorithmId) {
        TrainAlgorithmSelectByIdDTO trainAlgorithmSelectByIdDTO = new TrainAlgorithmSelectByIdDTO();
        trainAlgorithmSelectByIdDTO.setId(algorithmId);
        //??????????????????
        DataResponseBody<TrainAlgorithmQureyVO> dataResponseBody = algorithmClient.selectById(trainAlgorithmSelectByIdDTO);
        if (!dataResponseBody.succeed()) {
            throw new BusinessException(ErrorEnum.CALL_ALGORITHM_SERVER_FAIL);
        }
        return dataResponseBody.getData();
    }

    /**
     * ??????????????????
     * @param pcDatasetAutoDTO ????????????????????????
     * @return String
     */
    private String getImageUrl(PcDatasetAutoDTO pcDatasetAutoDTO) {
        PtImageQueryUrlDTO ptImageQueryUrlDTO = new PtImageQueryUrlDTO();
        List<Integer> pointCloudImageType = new ArrayList() {{
            add(ImageTypeEnum.POINT_CLOUD.getType());
        }};
        ptImageQueryUrlDTO.setImageTypes(pointCloudImageType);
        ptImageQueryUrlDTO.setImageName(pcDatasetAutoDTO.getImageName());
        ptImageQueryUrlDTO.setImageTag(pcDatasetAutoDTO.getImageTag());
        DataResponseBody<String> dataResponseBody = imageClient.getImageUrl(ptImageQueryUrlDTO);

        if (!dataResponseBody.succeed()) {
            throw new BusinessException(ErrorEnum.CALL_IMAGE_SERVER_FAIL);
        }
        return dataResponseBody.getData();
    }

    /**
     * ??????????????????
     * @param modelId ??????id
     * @return PtModelInfoQueryVO ????????????
     */
    private PtModelInfoQueryVO checkPtModelInfo(Long modelId) {
        PtModelInfoQueryByIdDTO ptModelInfoQueryByIdDTO = new PtModelInfoQueryByIdDTO();
        ptModelInfoQueryByIdDTO.setId(modelId);
        DataResponseBody<PtModelInfoQueryVO> modelInfoPresetDataResponseBody = modelClient
                .getByModelId(ptModelInfoQueryByIdDTO);
        PtModelInfoQueryVO ptModelInfoPresetQueryVO = null;
        if (modelInfoPresetDataResponseBody.succeed()) {
            ptModelInfoPresetQueryVO = modelInfoPresetDataResponseBody.getData();
        }
        if (ptModelInfoPresetQueryVO == null) {
            throw new BusinessException(ErrorEnum.MODEL_NOT_EXIST);
        }
        // ????????????
        if (ptModelInfoPresetQueryVO.getFrameType() > NumberConstant.NUMBER_4) {
            throw new BusinessException(ErrorEnum.MODEL_FRAME_TYPE_NOT_SUPPORTED);
        }
        return ptModelInfoPresetQueryVO;

    }

    /**
     * ????????????????????????
     * @param modelResource ???????????? 0??????????????? ???1???????????????
     * @param modelId ??????id
     * @param modelBranchId ????????????id
     * @return
     */
    public String getModelBranchAddress(Integer modelResource, Long modelId, Long modelBranchId, PcAnnotationDetail entity) {
        //????????????
        PtModelInfoQueryVO ptModelInfoQueryVO = checkPtModelInfo(modelId);
        entity.setModelName(ptModelInfoQueryVO.getName());
        if (NumberConstant.NUMBER_1 == modelResource) {
            entity.setModelVersion(ptModelInfoQueryVO.getVersion());
            return ptModelInfoQueryVO.getModelAddress();
        }
        PtModelBranchQueryByIdDTO ptModelBranchQueryByIdDTO = new PtModelBranchQueryByIdDTO();
        ptModelBranchQueryByIdDTO.setId(modelBranchId);
        DataResponseBody<PtModelBranchQueryVO> modelBranchQueryVODataResponseBody = modelClient.getByBranchId(ptModelBranchQueryByIdDTO);
        PtModelBranchQueryVO ptModelBranchQueryVO = null;
        if (modelBranchQueryVODataResponseBody.succeed()) {
            ptModelBranchQueryVO = modelBranchQueryVODataResponseBody.getData();
        }
        if (ptModelBranchQueryVO == null) {
            throw new BusinessException(ErrorEnum.MODEL_NOT_EXIST);
        }
        entity.setModelVersion(ptModelBranchQueryVO.getVersion());
        return ptModelBranchQueryVO.getModelAddress();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataResponseBody stopAutoAnnotation(Long id) {
        PcDataset pcDataset = checkDatasetsExist(id);
        StateMachineUtil.stateChange(new StateChangeDTO(
                new Object[]{id},
                PcDatasetEventMachineConstant.PC_DATASET_MACHINE,
                PcDatasetEventMachineConstant.AUTO_LABEL_STOP_PC_DATASET_EVENT
        ));
        PtBaseResult ptBaseResult = deployAsyncTask.deleteJob(pcDataset);
        if (ptBaseResult.isSuccess()) {
            LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {}. Success to stop dataset annotation,error message:{}", ptBaseResult.getMessage());
            return DataResponseFactory.success();
        } else {
            //??????job??????????????????????????????????????????
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset with id {}. Failed to stop dataset annotation,error message:{}", ptBaseResult.getMessage());
            throw new BusinessException("????????????????????????");
        }
    }

    @Override
    public List<DatasetQueryVO> queryByIds(Set<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return new ArrayList<>();
        }
        List<PcDataset> pcDatasetList = pcDatasetMapper.selectBatchIds(ids);
        List<DatasetQueryVO> queryVOList = pcDatasetList.stream().map(datasets -> {
            DatasetQueryVO queryVO = new DatasetQueryVO();
            BeanUtils.copyProperties(datasets, queryVO);
            return queryVO;
        }).collect(Collectors.toList());
        //?????????????????????
        return queryLabelGroupData(queryVOList);
    }


    @Override
    public void recycleRollback(RecycleCreateDTO dto) {
        List<RecycleDetailCreateDTO> detailList = dto.getDetailList();
        for (RecycleDetailCreateDTO recycleDetailCreateDTO : detailList) {
            if (!Objects.isNull(recycleDetailCreateDTO) &&
                    RecycleTypeEnum.TABLE_DATA.getCode().compareTo(recycleDetailCreateDTO.getRecycleType()) == 0) {
                Long pcDatasetId = Long.valueOf(recycleDetailCreateDTO.getRecycleCondition());
                //?????????????????????
                pcDatasetMapper.updateStatusById(pcDatasetId, Boolean.FALSE);
                //??????????????????
                pcDatasetFileMapper.updateStatusByDatasetId(pcDatasetId, Boolean.FALSE);
            }
        }
    }

    @Override
    public void deleteInfoByById(Long pcDatasetId) {
        pcDatasetMapper.deleteInfoById(pcDatasetId);
    }

    @Override
    public DatasetLogQueryVO getDatasetLog(PcDatasetLogQueryDTO pcDatasetLogQueryDTO) {
        PcDataset pcDataset = pcDatasetMapper.selectById(pcDatasetLogQueryDTO.getPcDatasetId());
        if (null == pcDataset) {
            LogUtil.error(LogEnum.POINT_CLOUD, "It is illegal  to look up the dataset log with id as {}", pcDatasetLogQueryDTO.getPcDatasetId());
            throw new BusinessException(ErrorEnum.DATASET_DOES_NOT_EXIST_ERROR);
        }
        String bizPodName = getBizPodName(pcDataset);
        if (Objects.isNull(bizPodName)) {
            return new DatasetLogQueryVO();
        }
        /** ????????????????????? **/
        Integer startLine = null == pcDatasetLogQueryDTO.getStartLine() ? MagicNumConstant.ONE : pcDatasetLogQueryDTO.getStartLine();
        /** ????????????????????? **/
        Integer lines = null == pcDatasetLogQueryDTO.getLines() ? MagicNumConstant.ONE : pcDatasetLogQueryDTO.getLines();
        /** ????????????es????????? **/
        LogMonitoringBO logMonitoringBo = new LogMonitoringBO();
        logMonitoringBo.setNamespace(k8sNameTool.getNamespace(pcDataset.getCreateUserId()))
                .setResourceName(pcDataset.getResourceName())
                .setPodName(bizPodName);
        LogMonitoringVO logMonitoringVO = logMonitoringApi.searchLogByResName(startLine, lines, logMonitoringBo);
        List<String> list = logMonitoringVO.getLogs();
        if (CollectionUtils.isEmpty(list)) {
            return new DatasetLogQueryVO()
                    .setContent(list)
                    .setStartLine(startLine)
                    .setEndLine(startLine - 1)
                    .setLines(0)
                    .setLogCount(0L);
        }
        Long logCount = logMonitoringApi.searchLogCountByPodName(logMonitoringBo);
        DatasetLogQueryVO datasetLogQueryVO = new DatasetLogQueryVO()
                .setContent(logMonitoringVO.getLogs())
                .setStartLine(startLine)
                .setEndLine(startLine + logMonitoringVO.getTotalLogs() - 1)
                .setLines(logMonitoringVO.getTotalLogs())
                .setLogCount(logCount);
        return datasetLogQueryVO;
    }

    @Override
    public List<PodVO> getPods(Long id) {
        PcDataset pcDataset = pcDatasetMapper.selectById(id);
        if (pcDataset == null) {
            return Collections.emptyList();
        }
        String nameSpace = k8sNameTool.generateNamespace(pcDataset.getCreateUserId());
        return podService.getPods(new PodQueryDTO(nameSpace, pcDataset.getResourceName()));
    }

    /**
     * ??????podName
     * @param pcDataset
     * @return
     */
    private String getBizPodName(PcDataset pcDataset) {
        BizPod bizPod = podApi.getWithResourceName(k8sNameTool.getNamespace(pcDataset.getCreateUserId()), pcDataset.getResourceName());
        if (K8sResponseEnum.NOT_FOUND.getCode().equals(bizPod.getCode())) {
            Set<String> podNameSet = resourceCache.getPodNameByResourceName(k8sNameTool.getNamespace(pcDataset.getCreateUserId()), pcDataset.getResourceName());
            if (CollectionUtils.isEmpty(podNameSet)) {
                LogUtil.error(LogEnum.POINT_CLOUD, "It is illegal  to look up pod log with resource name as {}", pcDataset.getResourceName());
                return null;
            }
            return podNameSet.stream().findFirst().get();

        }
        return bizPod.getName();
    }

    @Override
    public DatasetDetailDTO getDetails(Long id) {
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("???????????????????????????");
        }

        DatasetDetailDTO result = pcDatasetMapper.getDetails(id, BaseService.isAdmin(user) ? null : user.getId());
        if (null == result) {
            throw new BusinessException("?????????????????????????????????");
        }

        return result;
    }

    @Override
    public Map<String, Object> getTrainList(PageQueryBase pageQueryBase) {
        Page page = new Page(null == pageQueryBase.getCurrent() ? MagicNumConstant.ONE : pageQueryBase.getCurrent(),
                null == pageQueryBase.getSize() ? MagicNumConstant.TEN : pageQueryBase.getSize());
        QueryWrapper<PcDataset> wrapper = new QueryWrapper<>();
        //????????????????????????????????????
        wrapper.eq("status",PcDatasetMachineStatusEnum.PUBLISHED.getCode());
        wrapper = CommonUtil.getSortWrapper(pageQueryBase, wrapper, FILED_MANES);
        IPage<PcDataset> datasetsIPage = pcDatasetMapper.selectPage(page, wrapper);
        //???????????????
        List<PcDatasetTrainVO> queryVOList = datasetsIPage.getRecords().stream().map(pcDataset -> new PcDatasetTrainVO()
                .setId(pcDataset.getId())
                .setName(pcDataset.getName())
                .setUrl(pcDataset.getUrl())).collect(Collectors.toList());
        return PageUtil.toPage(datasetsIPage,queryVOList);
    }
}
