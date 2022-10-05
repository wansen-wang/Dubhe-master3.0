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

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.base.constant.NumberConstant;
import org.dubhe.biz.base.enums.OperationTypeEnum;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.statemachine.dto.StateChangeDTO;
import org.dubhe.data.constant.*;
import org.dubhe.data.dao.TaskMapper;
import org.dubhe.data.domain.bo.EnhanceTaskSplitBO;
import org.dubhe.data.domain.dto.AutoAnnotationCreateDTO;
import org.dubhe.data.domain.entity.*;
import org.dubhe.data.machine.constant.DataStateCodeConstant;
import org.dubhe.data.machine.constant.DataStateMachineConstant;
import org.dubhe.data.machine.enums.DataStateEnum;
import org.dubhe.data.machine.utils.StateIdentifyUtil;
import org.dubhe.data.machine.utils.StateMachineUtil;
import org.dubhe.data.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @description 标注任务信息服务实现类
 * @date 2020-04-10
 */
@Service
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task> implements TaskService {

    private static final Set<Integer> NOT_AUTO_ANNOTATE = new HashSet<Integer>() {{
        add(DataStateCodeConstant.AUTOMATIC_LABELING_STATE);
        add(DataStateCodeConstant.TARGET_FOLLOW_STATE);
        add(DataStateCodeConstant.SAMPLING_STATE);
        add(DataStateCodeConstant.STRENGTHENING_STATE);
        add(DataStateCodeConstant.IN_THE_IMPORT_STATE);
        add(DataStateCodeConstant.NOT_SAMPLED_STATE);
    }};

    @Autowired
    private FileService fileService;
    @Autowired
    @Lazy
    private DatasetServiceImpl datasetService;
    @Autowired
    private DatasetLabelService datasetLabelService;
    @Autowired
    private DatasetVersionFileService datasetVersionFileService;
    @Autowired
    private StateIdentifyUtil stateIdentify;
    @Autowired
    private LabelGroupService labelGroupService;

    @Autowired
    private AutoLabelModelServiceService autoLabelModelServiceService;

    /**
     * 十分钟(单位ms)
     */
    private final static Long FAIL_TIME = 600000L;

    /**
     * 暂时只支持数据集的提交
     *
     * @param autoAnnotationCreateDTO 自动标注dto
     * @return List<Long> 自动标注生成的父任务id列表
     */
    @Override
    public List<Long> auto(AutoAnnotationCreateDTO autoAnnotationCreateDTO) {
        // 检查模型服务是否存在，以及模型是否运行中
        AutoLabelModelService autoLabelModelService = autoLabelModelServiceService.getOneById(autoAnnotationCreateDTO.getModelServiceId());
        if (ObjectUtil.isNull(autoLabelModelService) || !AutoLabelModelServiceStatusEnum.checkAvailable(autoLabelModelService.getStatus())) {
            throw new BusinessException(ErrorEnum.MODEL_SERVER_NOT_AVAILABLE);
        }
        return create(autoAnnotationCreateDTO);
    }

    /**
     * 暂时只支持数据集的提交
     *
     * @param autoAnnotationCreateDTO 自动标注dto
     * @return List<Long> 自动标注生成的父任务id列表
     */
    public List<Long> create(AutoAnnotationCreateDTO autoAnnotationCreateDTO) {
        List<Long> result = new ArrayList<>();
        Arrays.stream(autoAnnotationCreateDTO.getDatasetIds()).forEach(aLong -> {
            Dataset dataset = datasetService.getOneById(aLong);
            // 如果选择了有标注信息或者全部文件时，则需要清理已有标注信息
            if (Arrays.asList(FileTypeEnum.All.getValue(), FileTypeEnum.HAVE_ANNOTATION.getValue()).contains(autoAnnotationCreateDTO.getFileStatus())) {
                clearAnnotation(dataset);
            }
            result.add(create(aLong, autoAnnotationCreateDTO));
        });
        return result;
    }

    /**
     * 清理标注信息
     *
     * @param dataset 数据集
     */
    public void clearAnnotation(Dataset dataset) {
        //改数据集相关状态
        StateMachineUtil.stateChange(new StateChangeDTO() {{
            setStateMachineType(DataStateMachineConstant.DATA_STATE_MACHINE);
            setEventMethodName(DataStateMachineConstant.DATA_DELETE_ANNOTATING_EVENT);
            setObjectParam(new Object[]{dataset.getId().intValue()});
        }});

        //根据当前数据集ID修改Changed字段为改变
        datasetVersionFileService.updateChanged(dataset.getId(), dataset.getCurrentVersionName());
    }

    /**
     * 暂时一个任务只包含一个数据集
     * 只对未标注和手动标注中的数据集进行标注
     * 只对未标注状态的文件进行标注
     * 如果待标注的文件为空，则抛异常
     *
     * @param datasetId 数据集id
     * @param autoAnnotationCreateDTO      标注类型
     * @return Long 父任务id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long datasetId, AutoAnnotationCreateDTO autoAnnotationCreateDTO) {
        if (datasetId == null) {
            return MagicNumConstant.ZERO_LONG;
        }

        Dataset dataset = datasetService.getById(datasetId);
        if (!Objects.isNull(dataset) && AnnotateTypeEnum.SEMANTIC_CUP.getValue().compareTo(dataset.getAnnotateType()) == 0) {
            throw new BusinessException(AnnotateTypeEnum.SEMANTIC_CUP.getMsg() + ErrorEnum.DATASET_NOT_ANNOTATION);

        }
        datasetService.checkPublic(dataset, OperationTypeEnum.UPDATE);
        if (dataset == null || NOT_AUTO_ANNOTATE.contains(dataset.getStatus())) {
            throw new BusinessException(ErrorEnum.AUTO_DATASET_ERROR);
        }
        List<Long> datasetIds = Arrays.asList(datasetId);
        Integer filesCount = datasetVersionFileService.getFileCountByDatasetIdAndAnnotationStatus(datasetId, dataset.getCurrentVersionName(),
                FileTypeEnum.getStatus(autoAnnotationCreateDTO.getFileStatus()));
        if (filesCount < NumberConstant.NUMBER_1) {
            throw new BusinessException(ErrorEnum.AUTO_FILE_EMPTY);
        }

        if (DatatypeEnum.TEXT.getValue().compareTo(dataset.getDataType()) == 0 &&
                !labelGroupService.isAnnotationByGroupId(dataset.getLabelGroupId())) {
            throw new BusinessException(ErrorEnum.LABEL_PREPARE_IS_TXT);
        }
        List<Label> labels = datasetLabelService.listLabelByDatasetId(datasetId);
        if (CollectionUtils.isEmpty(labels) ||
                CollectionUtils.isEmpty(labels.stream().filter(label -> (!label.getType().equals(DatasetLabelEnum.CUSTOM))).collect(Collectors.toList()))) {
            throw new BusinessException(ErrorEnum.AUTO_LABEL_EMPTY_ERROR);
        }
        List<String> labelNames = new ArrayList<>();
        labels.forEach(label -> {
            labelNames.add(label.getName());
        });
        Integer dataType = dataset.getDataType();
        Integer taskType = DataTaskTypeEnum.ANNOTATION.getValue();
        // 如果是数据集是文本类型，执行的任务 = 标注类型
        if (DatatypeEnum.TEXT.getValue().equals(dataType)) {
            taskType = DataTaskTypeEnum.TEXT_CLASSIFICATION.getValue();
        }
        Task task = Task.builder()
                .status(TaskStatusEnum.INIT.getValue())
                .datasets(JSON.toJSONString(datasetIds))
                .files(JSON.toJSONString(Collections.EMPTY_LIST))
                .dataType(dataset.getDataType())
                .labels(JSON.toJSONString(labelNames))
                .annotateType(dataset.getAnnotateType())
                .finished(MagicNumConstant.ZERO)
                .total(filesCount)
                .datasetId(datasetId)
                .type(taskType)
                .fileType(autoAnnotationCreateDTO.getFileStatus())
                .build();
        if(autoAnnotationCreateDTO.getModelServiceId() != null){
            task.setModelServiceId(autoAnnotationCreateDTO.getModelServiceId());
        }
        baseMapper.insert(task);

        //嵌入状态机
        StateMachineUtil.stateChange(new StateChangeDTO() {{
            setStateMachineType(DataStateMachineConstant.DATA_STATE_MACHINE);
            setEventMethodName(DataStateMachineConstant.DATA_AUTO_ANNOTATIONS_EVENT);
            setObjectParam(new Object[]{dataset.getId().intValue()});
        }});
        return task.getId();
    }

    /**
     * 目标跟踪
     * @param dataset
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void track(Dataset dataset, Long modelServiceId) {
        //目标追踪中
        //嵌入数据集状态机
        StateMachineUtil.stateChange(new StateChangeDTO() {{
            setObjectParam(new Object[]{dataset});
            setEventMethodName(DataStateMachineConstant.DATA_TRACK_EVENT);
            setStateMachineType(DataStateMachineConstant.DATA_STATE_MACHINE);
        }});
        Task task = Task.builder().total(NumberConstant.NUMBER_1)
                .datasetId(dataset.getId())
                .type(DataTaskTypeEnum.TARGET_TRACK.getValue())
                .modelServiceId(modelServiceId)
                .labels("").build();
        baseMapper.insert(task);
    }

    /**
     * 任务开始
     *
     * @param dataset 数据集
     */
    public void start(Dataset dataset) {
        if (dataset == null) {
            return;
        }
        datasetService.transferStatus(dataset, DataStateEnum.AUTOMATIC_LABELING_STATE);
    }

    /**
     * 任务失败
     *
     * @param task 只能有id，或者其它字段与数据库保持一致，否则会被写入数据库
     */
    public void fail(Task task) {
        task.setStatus(TaskStatusEnum.FAIL.getValue());
        getBaseMapper().updateById(task);

        List<Long> datasetIds = JSON.parseArray(task.getDatasets(), Long.class);
        if (CollectionUtils.isEmpty(datasetIds)) {
            return;
        }
        datasetIds.forEach(i -> {
                    datasetService.updateStatus(i,
                            stateIdentify.getStatusForRollback(i, datasetService.getById(i).getCurrentVersionName())
                    );
                }
        );
    }

    /**
     * 完成任务的文件
     *
     * @param enhanceTaskSplitBO       任务
     * @param fileNum                  文件数量
     */
    @Override
    public void finishTaskFile(EnhanceTaskSplitBO enhanceTaskSplitBO, Integer fileNum) {
        Task task = baseMapper.selectById(enhanceTaskSplitBO.getId());
        if (task == null) {
            return;
        }
        getBaseMapper().finishFileNum(enhanceTaskSplitBO.getId(), fileNum);
        task = baseMapper.selectById(enhanceTaskSplitBO.getId());
        if (task.getFinished() >= task.getTotal()) {
            //嵌入数据集状态机
            StateMachineUtil.stateChange(new StateChangeDTO() {{
                setObjectParam(new Object[]{datasetService.getOneById(enhanceTaskSplitBO.getDatasetId())});
                setEventMethodName(DataStateMachineConstant.DATA_ENHANCE_FINISH_EVENT);
                setStateMachineType(DataStateMachineConstant.DATA_STATE_MACHINE);
            }});
        }
    }

    /**
     * 完成文件
     *
     * @param taskId       任务id
     * @param filesCount   完成的文件数量
     * @param dataset      数据集
     * @return ture or false
     */
    @Override
    public boolean finishFile(Long taskId, Integer filesCount, Dataset dataset) {
        getBaseMapper().finishFile(taskId, filesCount);
        Task task = baseMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorEnum.TASK_ABSENT);
        }
        if (task.getFinished() >= task.getTotal()) {
            task.setStatus(TaskStatusEnum.FINISHED.getValue());
            getBaseMapper().updateById(task);
            //嵌入数据集状态机
            StateMachineUtil.stateChange(new StateChangeDTO() {{
                setObjectParam(new Object[]{dataset});
                setEventMethodName(DataStateMachineConstant.DATA_DO_FINISH_AUTO_ANNOTATION_EVENT);
                setStateMachineType(DataStateMachineConstant.DATA_STATE_MACHINE);
            }});
            return true;
        }
        return false;
    }


    /**
     * 获取一个需要执行的任务
     *
     * @return 任务
     */
    @Override
    public Task getOnePendingTask() {
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", MagicNumConstant.ZERO);
        queryWrapper.ne("type", MagicNumConstant.FIVE);
        queryWrapper.last("limit 1");
        return baseMapper.selectOne(queryWrapper);
    }

    /**
     * 更新任务状态
     *
     * @param taskId        任务ID
     * @param sourceStatus  原状态
     * @param targetStatus  目的状态
     * @return 更新数量
     */
    @Override
    public int updateTaskStatus(Long taskId, Integer sourceStatus, Integer targetStatus) {
        UpdateWrapper<Task> updateWrapper = new UpdateWrapper<>();
        Task task = new Task();
        task.setStatus(targetStatus);
        updateWrapper.eq("id", taskId).eq("status", sourceStatus);
        return baseMapper.update(task, updateWrapper);
    }

    /**
     * 创建一个任务
     *
     * @param task 任务实体
     */
    @Override
    public void createTask(Task task) {
        baseMapper.insert(task);
    }

    /**
     * 完成任务
     *
     * @param id      任务ID
     * @param fileNum 本次完成文件数
     * @return Boolean 是否完成
     */
    @Override
    public Boolean finishTask(Long id, Integer fileNum) {
        baseMapper.finishFileNum(id, fileNum);
        Task task = baseMapper.selectById(id);
        if (task.getTotal() > task.getFinished() + task.getFailed()) {
            return false;
        }
        updateTaskStatus(id, 2, 3);
        return true;
    }

    /**
     * 获取任务详情
     *
     * @param id 任务ID
     * @return 任务
     */
    @Override
    public Task detail(Long id) {
        return baseMapper.selectById(id);
    }

    /**
     * 获取执行中的任务
     *
     * @param datasetId 任务ID
     * @param type      任务类型
     * @return 任务列表
     */
    @Override
    public List<Task> getExecutingTask(Long datasetId, Integer type) {
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dataset_id", datasetId);
        queryWrapper.eq("type", type);
        return baseMapper.selectList(queryWrapper);
    }

    /**
     * 设置任务总数属性
     *
     * @param taskId 任务ID
     * @param total  总数
     */
    @Override
    public void setTaskTotal(Long taskId, Integer total) {
        UpdateWrapper<Task> updateWrapper = new UpdateWrapper<>();
        Task task = new Task();
        task.setTotal(total);
        updateWrapper.eq("id", taskId);
        baseMapper.update(task, updateWrapper);
    }

    /**
     * 获取一个任务
     *
     * @param taskQueryWrapper 条件搜索任务
     * @return 任务
     */
    @Override
    public Task selectOne(QueryWrapper<Task> taskQueryWrapper) {
        return baseMapper.selectOne(taskQueryWrapper);
    }

    /**
     * 更新任务
     *
     * @param task 任务详情
     */
    @Override
    public void updateByTaskId(Task task) {
        baseMapper.updateById(task);
    }

    @Override
    public List<Task> selectByQueryWrapper(QueryWrapper<Task> queryWrapper) {
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public void taskStop(Long taskId) {
        baseMapper.taskStop(taskId);
    }

    @Override
    public List<Task> selectRunningTask(Long datasetId) {
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(true, Task::getStatus, MagicNumConstant.TWO)
                .ne(true, Task::getType, MagicNumConstant.SIX)
                .eq(Task::getDatasetId, datasetId)
                .orderByDesc(Task::getId);
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public Task selectRunningDcmTask(Long datasetId) {
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(true, Task::getStatus, MagicNumConstant.TWO)
                .eq(true, Task::getType, MagicNumConstant.SIX)
                .eq(Task::getDatasetId, datasetId)
                .orderByDesc(Task::getId)
                .last(" limit 1");
        return baseMapper.selectOne(queryWrapper);
    }

    @Override
    public Task getOneNeedStopTask() {
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", MagicNumConstant.TWO)
                .eq("stop", MagicNumConstant.ONE)
                .orderByDesc("id")
                .last(" limit 1");
        return baseMapper.selectOne(queryWrapper);
    }

    @Override
    public Long selectTaskId(Long datasetId,Integer datasetStatus) {
        return baseMapper.selectTaskId(datasetId,datasetStatus);
    }

    @Override
    public Long selectDcmTaskId(Long datasetId,Integer datasetStatus) {
        return baseMapper.selectDcmTaskId(datasetId,datasetStatus);
    }

    @Override
    public Long selectStopTaskId(Long taskId,Long datasetId,Integer datasetStatus) {
        return baseMapper.selectStopTaskId(taskId,datasetId,datasetStatus);
    }

    @Override
    public Long selectDcmStopTaskId(Long taskId,Long datasetId,Integer datasetStatus) {
        return baseMapper.selectDcmStopTaskId(taskId,datasetId,datasetStatus);
    }

    @Override
    public boolean isStop(Long id) {
        return getById(id).isStop();
    }
}
