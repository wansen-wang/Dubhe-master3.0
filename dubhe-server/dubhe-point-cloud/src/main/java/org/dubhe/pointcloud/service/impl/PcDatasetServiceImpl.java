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
 * @description 点云数据集实现类
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
        //若查询条件为空
        if (StringUtils.isEmpty(pcDatasetQueryDTO.getName())) {
            return queryDatasets(pcDatasetQueryDTO, wrapper);
        }
        //若查询条件是数字
        if (StringConstant.PATTERN_NUMBER.matcher(pcDatasetQueryDTO.getName()).matches()) {
            if (pcDatasetQueryDTO.getName().length() > String.valueOf(Long.MAX_VALUE).length()) {
                throw new BusinessException("查询id超过最大长度");
            }
            wrapper.and(QueryWrapper -> QueryWrapper.eq("id", Long.valueOf(pcDatasetQueryDTO.getName()))
                    .or()
                    .or().like("lower(name)", pcDatasetQueryDTO.getName().toLowerCase()));
        } else {
            // 匹配搜索条件会有下划线, 百分号，斜杠等进行转义
            String lowerName = CommonUtil.escapeChar(pcDatasetQueryDTO.getName().toLowerCase());
            wrapper.and(QueryWrapper -> QueryWrapper.apply("lower(name) like '%" + lowerName + "%' escape '/' "));
        }
        return queryDatasets(pcDatasetQueryDTO, wrapper);
    }

    /** 数据集查询
     * @param pcDatasetQueryDTO 数据集查询条件
     * @param wrapper
     * @return Map<String, Object>
     */
    public Map<String, Object> queryDatasets(PcDatasetQueryDTO pcDatasetQueryDTO, QueryWrapper<PcDataset> wrapper) {
        Page page = new Page(null == pcDatasetQueryDTO.getCurrent() ? MagicNumConstant.ONE : pcDatasetQueryDTO.getCurrent(),
                null == pcDatasetQueryDTO.getSize() ? MagicNumConstant.TEN : pcDatasetQueryDTO.getSize());
        wrapper = CommonUtil.getSortWrapper(pcDatasetQueryDTO, wrapper, FILED_MANES);
        IPage<PcDataset> datasetsIPage = pcDatasetMapper.selectPage(page, wrapper);
        //查询数据集
        List<DatasetQueryVO> queryVOList = datasetsIPage.getRecords().stream().map(pcDataset -> {
            DatasetQueryVO queryVO = new DatasetQueryVO();
            BeanUtils.copyProperties(pcDataset, queryVO);
            return queryVO;
        }).collect(Collectors.toList());
        //若查询未存在数据，直接返回
        if (CollectionUtils.isEmpty(queryVOList)) {
            return PageUtil.toPage(datasetsIPage, queryVOList);
        }
        return PageUtil.toPage(datasetsIPage, queryLabelGroupData(queryVOList));
    }

    /**
     * 获取标签组数据
     * @param queryVOList 数据集集合
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
        //数据集是否重名校验
        checkNameExist(pcDatasetCreateDTO.getName());
        //标签组是否存在校验
        checkLabelGroupExist(pcDatasetCreateDTO.getLabelGroupId());
        PcDataset pcDataset = new PcDataset();
        BeanUtils.copyProperties(pcDatasetCreateDTO, pcDataset);
        UserContext user = userContextService.getCurUser();
        pcDataset.setCreateUserId(user.getId());
        pcDatasetMapper.insert(pcDataset);
        return DataResponseFactory.success(pcDataset);
    }

    /**
     * 确认名称是否重复
     * @param name 数据集名称
     */
    private void checkNameExist(String name) {
        List<PcDataset> datasetsList = pcDatasetMapper.selectList(new LambdaQueryWrapper<PcDataset>().eq(PcDataset::getName, name));
        if (!CollectionUtils.isEmpty(datasetsList)) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset with name {} already exists in the database", name);
            throw new BusinessException(ErrorEnum.DUPLICATE_DATASET_NAME);
        }
    }

    /**
     * 确认标签组是存在
     * @param labelGroupId 标签组id
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
            throw new BusinessException("非点云标签组");
        }
        return labelGroupBaseVO;
    }

    /**
     * 确认数据集是否存在
     * @param datasetId 数据集id
     * @return 数据集
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
        //校验数据集是否存在
        PcDataset pcDataset = checkDatasetsExist(pcDatasetUpdateDTO.getId());
        //校验名称是否修改
        if (!pcDataset.getName().equals(pcDatasetUpdateDTO.getName())) {
            //校验修改后的名称是否有重复
            checkNameExist(pcDatasetUpdateDTO.getName());
        }
        // 只有未采样，导入中，未标注的数据集才能修改标签组
        if (!pcDatasetUpdateDTO.getLabelGroupId().equals(pcDataset.getLabelGroupId())
                && !(PcDatasetMachineStatusEnum.UNLABELLED.getCode().equals(pcDataset.getStatus())
                || PcDatasetMachineStatusEnum.NOT_SAMPLED.getCode().equals(pcDataset.getStatus())
                || PcDatasetMachineStatusEnum.IMPORTING.getCode().equals(pcDataset.getStatus()))) {
            throw new BusinessException(ErrorEnum.OPERATION_LABEL_GROUP_NOT_ALLOWED_IN_STATE);
        }
        //自动标注中不能修改数据集
        if (PcDatasetMachineStatusEnum.AUTO_LABELING.getCode().equals(pcDataset.getStatus())) {
            throw new BusinessException(ErrorEnum.AUTO_LABELING_NOT_UPDATE_DATASETS);
        }
        // 数据集标签组变更，校验标签组是否存在
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
            throw new BusinessException("自动标注中的数据集不能被删除");
        }
        //变更数据集为已删除状态
        pcDatasetMapper.updateStatusByBatchIds(ids, Boolean.TRUE);
        //变更数据集点云文件为已删除状态
        datasetsList.forEach(pcDataset -> pcDatasetFileMapper.updateStatusByDatasetId(pcDataset.getId(), Boolean.TRUE));
        datasetsList.forEach(this::createRecycleTask);
        return DataResponseFactory.success();

    }

    /**
     * 实验文件回收
     * @param pcDataset 回收的实验对象
     */
    private void createRecycleTask(PcDataset pcDataset) {
        List<RecycleDetailCreateDTO> detailList = new ArrayList<>();

        //mnio 数据集文件回收
        String datasetDir = k8sNameTool.getAbsolutePath(pcDataset.getUrl());
        if (StringUtils.isNotBlank(datasetDir)) {
            detailList.add(RecycleDetailCreateDTO.builder()
                    .recycleCondition(datasetDir)
                    .recycleType(RecycleTypeEnum.FILE.getCode())
                    .recycleNote(RecycleTool.generateRecycleNote("3D点云 minio 数据集文件回收", pcDataset.getId()))
                    .build());
        }


        if (pcDataset.getFileCount() > NumberConstant.NUMBER_0) {

            //minio 点云文件回收
            detailList.add(RecycleDetailCreateDTO
                    .builder()
                    .recycleCondition(k8sNameTool.getAbsolutePath(pathUtil.getPcdFileUrl(pcDataset.getId(), StringUtils.EMPTY)))
                    .recycleNote(RecycleTool.generateRecycleNote("3D点云 minio 点云文件回收", pcDataset.getId()))
                    .build());
        }
        //数据库数据删除
        detailList.add(RecycleDetailCreateDTO.builder()
                .recycleCondition(pcDataset.getId().toString())
                .recycleType(RecycleTypeEnum.TABLE_DATA.getCode())
                .recycleNote(RecycleTool.generateRecycleNote("3D点云 数据集DB 数据文件回收", pcDataset.getId()))
                .build());

        RecycleCreateDTO recycleCreateDTO = RecycleCreateDTO.builder()
                .recycleModule(RecycleModuleEnum.BIZ_POINT_CLOUD.getValue())
                .recycleDelayDate(recycleConfig.getPointCloudValid())  //默认3天
                .recycleNote(RecycleTool.generateRecycleNote("删除3D点云数据集文件", pcDataset.getName(), pcDataset.getId()))
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
        //数据集存在判断
        PcDataset pcDataset = checkDatasetsExist(id);
        //状态变更
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

        //pod删除时会返回当前pod状态，在停止标注状态下需要对数据进行处理
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
            throw new BusinessException("当前用户信息已失效");
        }
        PcDataset pcDataset = checkDatasetsExist(pcDatasetAutoDTO.getDatasetId());
        RLock lock = redissonClient.getLock(StringUtils.join(pointCloudAnnotationPrefix, user.getId(), pcDataset.getId()));
        try {
            if (lock.tryLock(3, 5, TimeUnit.SECONDS)) {
                PcAnnotationDetail entity = getPcAnnotationDetail(pcDatasetAutoDTO);
                entity.setCreateUserId(user.getId());
                //数据集状态变更为自动标注中
                StateMachineUtil.stateChange(new StateChangeDTO(
                        new Object[]{pcDatasetAutoDTO.getDatasetId()},
                        PcDatasetEventMachineConstant.PC_DATASET_MACHINE,
                        PcDatasetEventMachineConstant.AUTO_LABELING_PC_DATASET_EVENT));

                //组装自动标注条件
                PcDatasetRunParamDTO pcDatasetRunParamDTO = getPcDatasetRunParamDTO(pcDatasetAutoDTO, pcDataset, entity);

                //方法自动标注状态变更的大事务commit后再执行异步方法申请k8s资源执行任务。
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                    @Override
                    public void afterCommit() {
                        deployAsyncTask.deployPod(pcDatasetRunParamDTO);
                    }
                });

                // 自动标注参数入库
                if (pcAnnotationDetailMapper.selectById(entity.getDatasetId()) != null) {
                    pcAnnotationDetailMapper.updateById(entity);
                } else {
                    pcAnnotationDetailMapper.insert(entity);
                }
            }
        } catch (Exception e) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset with id {}. Failed to lock,error message:{}", pcDatasetAutoDTO.getDatasetId(), e.getMessage());
            throw new BusinessException("自动标注异常");
        }finally {
            lock.unlock();
        }

        return DataResponseFactory.success();
    }


    /**
     * 组装数据集详细信息实体类
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
     * 获取自动标注运行条件
     * @param pcDatasetAutoDTO 自动标注参数
     * @param pcDataset 数据集名称
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
                //模型路径获取
                .setModelDir(k8sNameTool.getAbsolutePath(getModelBranchAddress(pcDatasetAutoDTO.getModelResource(), pcDatasetAutoDTO.getModelId(), pcDatasetAutoDTO.getModelBranchId(), entity)))
                //数据集路径获取
                .setDatasetDir(k8sNameTool.getAbsolutePath(pcDataset.getUrl()))
                //输出结果集路径获取
                .setResultsDir(k8sNameTool.getAbsolutePath(pathUtil.getLabel2Url(pcDatasetAutoDTO.getDatasetId())))
                //算法路径获取
                .setAlgorithmDir(k8sNameTool.getAbsolutePath(algorithmQureyVO.getCodeDir()))
                //运行命令拼接组装
                .setCommand(String.format(Constant.COMMAND, commandShow, commandShow))
                //资源信息唯一标识
                .setResourceInfo(pcDatasetAutoDTO.getDatasetId() + SymbolConstant.HYPHEN + StringUtils.getRandomString())
                //身份标识
                .setTaskIdentify(resourceCache.getTaskIdentify(pcDatasetAutoDTO.getDatasetId(), pcDataset.getName(), pcDatasetIdPrefix))
                .setCreateUserId(pcDataset.getCreateUserId());

        entity.setImageName(pcDatasetAutoDTO.getImageName());
        entity.setImageTag(pcDatasetAutoDTO.getImageTag());
        return pcDatasetRunParamDTO;
    }

    /**
     * 获取算法路径
     * @param algorithmId 算法id
     * @return String
     */
    private TrainAlgorithmQureyVO getAlgorithmDir(Long algorithmId) {
        TrainAlgorithmSelectByIdDTO trainAlgorithmSelectByIdDTO = new TrainAlgorithmSelectByIdDTO();
        trainAlgorithmSelectByIdDTO.setId(algorithmId);
        //算法路径获取
        DataResponseBody<TrainAlgorithmQureyVO> dataResponseBody = algorithmClient.selectById(trainAlgorithmSelectByIdDTO);
        if (!dataResponseBody.succeed()) {
            throw new BusinessException(ErrorEnum.CALL_ALGORITHM_SERVER_FAIL);
        }
        return dataResponseBody.getData();
    }

    /**
     * 获取镜像路径
     * @param pcDatasetAutoDTO 镜像路径查询条件
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
     * 获取模型信息
     * @param modelId 模型id
     * @return PtModelInfoQueryVO 模型信息
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
        // 校验框架
        if (ptModelInfoPresetQueryVO.getFrameType() > NumberConstant.NUMBER_4) {
            throw new BusinessException(ErrorEnum.MODEL_FRAME_TYPE_NOT_SUPPORTED);
        }
        return ptModelInfoPresetQueryVO;

    }

    /**
     * 获取模型版本信息
     * @param modelResource 模型来源 0：我的模型 ，1：预置模型
     * @param modelId 模型id
     * @param modelBranchId 模型版本id
     * @return
     */
    public String getModelBranchAddress(Integer modelResource, Long modelId, Long modelBranchId, PcAnnotationDetail entity) {
        //校验模型
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
            //删除job失败，状态回退到停止标注之前
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset with id {}. Failed to stop dataset annotation,error message:{}", ptBaseResult.getMessage());
            throw new BusinessException("停止自动标注失败");
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
        //获取标签组信息
        return queryLabelGroupData(queryVOList);
    }


    @Override
    public void recycleRollback(RecycleCreateDTO dto) {
        List<RecycleDetailCreateDTO> detailList = dto.getDetailList();
        for (RecycleDetailCreateDTO recycleDetailCreateDTO : detailList) {
            if (!Objects.isNull(recycleDetailCreateDTO) &&
                    RecycleTypeEnum.TABLE_DATA.getCode().compareTo(recycleDetailCreateDTO.getRecycleType()) == 0) {
                Long pcDatasetId = Long.valueOf(recycleDetailCreateDTO.getRecycleCondition());
                //还原数据集状态
                pcDatasetMapper.updateStatusById(pcDatasetId, Boolean.FALSE);
                //还原文件状态
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
        /** 查询日志起始行 **/
        Integer startLine = null == pcDatasetLogQueryDTO.getStartLine() ? MagicNumConstant.ONE : pcDatasetLogQueryDTO.getStartLine();
        /** 查询日志总行数 **/
        Integer lines = null == pcDatasetLogQueryDTO.getLines() ? MagicNumConstant.ONE : pcDatasetLogQueryDTO.getLines();
        /** 拼接请求es的参数 **/
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
     * 获取podName
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
            throw new BusinessException("当前用户信息已失效");
        }

        DatasetDetailDTO result = pcDatasetMapper.getDetails(id, BaseService.isAdmin(user) ? null : user.getId());
        if (null == result) {
            throw new BusinessException("无法找到对应数据集信息");
        }

        return result;
    }

    @Override
    public Map<String, Object> getTrainList(PageQueryBase pageQueryBase) {
        Page page = new Page(null == pageQueryBase.getCurrent() ? MagicNumConstant.ONE : pageQueryBase.getCurrent(),
                null == pageQueryBase.getSize() ? MagicNumConstant.TEN : pageQueryBase.getSize());
        QueryWrapper<PcDataset> wrapper = new QueryWrapper<>();
        //筛选状态是已发布的数据集
        wrapper.eq("status",PcDatasetMachineStatusEnum.PUBLISHED.getCode());
        wrapper = CommonUtil.getSortWrapper(pageQueryBase, wrapper, FILED_MANES);
        IPage<PcDataset> datasetsIPage = pcDatasetMapper.selectPage(page, wrapper);
        //查询数据集
        List<PcDatasetTrainVO> queryVOList = datasetsIPage.getRecords().stream().map(pcDataset -> new PcDatasetTrainVO()
                .setId(pcDataset.getId())
                .setName(pcDataset.getName())
                .setUrl(pcDataset.getUrl())).collect(Collectors.toList());
        return PageUtil.toPage(datasetsIPage,queryVOList);
    }
}
