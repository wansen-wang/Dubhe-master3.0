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
package org.dubhe.dcm.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.dubhe.biz.permission.annotation.DataPermissionMethod;
import org.dubhe.biz.base.constant.NumberConstant;
import org.dubhe.biz.base.enums.DatasetTypeEnum;
import org.dubhe.biz.base.enums.OperationTypeEnum;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.file.utils.MinioUtil;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.redis.utils.RedisUtils;
import org.dubhe.biz.statemachine.dto.StateChangeDTO;
import org.dubhe.cloud.authconfig.utils.JwtUtils;
import org.dubhe.data.constant.DataTaskTypeEnum;
import org.dubhe.data.constant.ErrorEnum;
import org.dubhe.data.constant.FileTypeEnum;
import org.dubhe.data.constant.TaskStatusEnum;
import org.dubhe.data.domain.entity.Task;
import org.dubhe.data.machine.constant.DataStateCodeConstant;
import org.dubhe.data.machine.enums.DataStateEnum;
import org.dubhe.data.service.AutoLabelModelServiceService;
import org.dubhe.data.service.TaskService;
import org.dubhe.data.util.TaskUtils;
import org.dubhe.dcm.constant.DcmConstant;
import org.dubhe.dcm.dao.DataMedicineFileMapper;
import org.dubhe.dcm.domain.dto.MedicineAnnotationDTO;
import org.dubhe.dcm.domain.dto.MedicineAutoAnnotationDTO;
import org.dubhe.dcm.domain.entity.DataMedicine;
import org.dubhe.dcm.domain.entity.DataMedicineFile;
import org.dubhe.dcm.domain.vo.ScheduleVO;
import org.dubhe.dcm.machine.constant.DcmDataStateMachineConstant;
import org.dubhe.dcm.machine.constant.DcmFileStateCodeConstant;
import org.dubhe.dcm.machine.constant.DcmFileStateMachineConstant;
import org.dubhe.dcm.machine.enums.DcmDataStateEnum;
import org.dubhe.dcm.machine.enums.DcmFileStateEnum;
import org.dubhe.dcm.machine.utils.DcmStateMachineUtil;
import org.dubhe.dcm.service.DataLesionSliceService;
import org.dubhe.dcm.service.DataMedicineFileService;
import org.dubhe.dcm.service.DataMedicineService;
import org.dubhe.dcm.service.MedicineAnnotationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;


/**
 * @description ???????????????????????????
 * @date 2020-11-16
 */
@Service
public class MedicineAnnotationServiceImpl implements MedicineAnnotationService {

    /**
     * ????????????
     */
    @Autowired
    private TaskService taskService;

    @Autowired
    private DataMedicineFileMapper dataMedicineFileMapper;

    /**
     * ?????????????????????
     */
    @Autowired
    private DataMedicineService dataMedicineService;

    /**
     * ???????????????????????????
     */
    @Autowired
    private DataMedicineFileService dataMedicineFileService;

    @Autowired
    private MinioUtil minioUtil;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private DataLesionSliceService dataLesionSliceService;

    @Autowired
    private TaskUtils taskUtils;

    /**
     * bucketName
     */
    @Value("${minio.bucketName}")
    private String bucketName;

    @Autowired
    private AutoLabelModelServiceService autoLabelModelServiceService;


    /**
     * ???????????????????????????????????????
     */
    private static final String MEDICINE_START_QUEUE = "dcm_processing_queue";
    /**
     * ???????????????????????????????????????
     */
    private static final String MEDICINE_FINISHED_QUEUE = "dcm_finished_queue";

    /**
     * ??????????????????
     *
     * @param medicineAutoAnnotationDTO ??????????????????DTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public void auto(MedicineAutoAnnotationDTO medicineAutoAnnotationDTO) {
        if (medicineAutoAnnotationDTO.getMedicalId() == null) {
            return;
        }
        // ??????????????????????????????
        if (ObjectUtil.isNull(medicineAutoAnnotationDTO.getModelServiceId())
                || ObjectUtil.isNull(autoLabelModelServiceService.getOneById(medicineAutoAnnotationDTO.getModelServiceId()))) {
            throw new BusinessException(ErrorEnum.MODEL_SERVER_NOT_EXIST);
        }
        dataMedicineService.checkPublic(medicineAutoAnnotationDTO.getMedicalId(), OperationTypeEnum.UPDATE);
        Long medicineId = medicineAutoAnnotationDTO.getMedicalId();
        DataMedicine dataMedicine = dataMedicineService.getDataMedicineById(medicineId);
        // ?????????????????????????????????????????????
        if (!DcmDataStateEnum.checkCurrentStatusWhetherToAutoLabel(dataMedicine.getStatus())) {
            throw new BusinessException(ErrorEnum.MEDICINE_AUTO_DATASET_ERROR);
        }
        QueryWrapper<DataMedicineFile> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .eq(DataMedicineFile::getMedicineId, medicineId)
                .in(DataMedicineFile::getStatus, DcmFileStateEnum.getFileStatusFromAutoLabelScreen(medicineAutoAnnotationDTO.getFileStatus()));
        Integer medicineFilesCount = dataMedicineFileService.getCountByMedicineId(queryWrapper);
        if (medicineFilesCount == 0) {
            throw new BusinessException(ErrorEnum.MEDICINE_AUTO_NO_LABEL_FILE);
        }
        Task task = Task.builder()
                .status(TaskStatusEnum.INIT.getValue())
                .datasetId(medicineId)
                .total(medicineFilesCount)
                .modelServiceId(medicineAutoAnnotationDTO.getModelServiceId())
                .type(DataTaskTypeEnum.MEDICINE_ANNOTATION.getValue())
                .labels("")
                .fileType(medicineAutoAnnotationDTO.getFileStatus())
                .build();
        taskService.createTask(task);
        DcmStateMachineUtil.stateChange(new StateChangeDTO() {{
            setObjectParam(new Object[]{dataMedicine});
            setStateMachineType(DcmDataStateMachineConstant.DCM_DATA_STATE_MACHINE);
            setEventMethodName(DcmDataStateMachineConstant.AUTO_ANNOTATION_SAVE_EVENT);
        }});
        modifyUpdataUserId(medicineAutoAnnotationDTO.getMedicalId());
    }

    /**
     * ????????????????????????
     *
     * @return boolean ???????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishAuto(JSONObject taskDetail) {
        JSONObject jsonObject = JSON.parseObject(taskDetail.get("object").toString(),JSONObject.class);
        if (ObjectUtil.isNotNull(taskDetail)) {
            Long taskId = taskDetail.getLong("taskId");
            JSONArray dcmsArray = taskDetail.getJSONArray("dcms");
            JSONArray jsonArray = taskDetail.getJSONArray("annotations");
            String[] dcms = dcmsArray.toArray(new String[dcmsArray.size()]);
            QueryWrapper<Task> taskQueryWrapper = new QueryWrapper<>();
            taskQueryWrapper.lambda().eq(Task::getId, taskId);
            Task task = taskService.selectOne(taskQueryWrapper);
            JSONArray annotationsArray = jsonObject.getJSONArray("annotations");
            for (int i =0;i<annotationsArray.size();i++){
                JSONObject oneAnnotation = annotationsArray.getJSONObject(i);
                Long medicineFileId = oneAnnotation.getLong("id");
                String medicineFileName = dataMedicineFileMapper.selectById(medicineFileId).getName();
                String oneAnnotationContent = oneAnnotation.getJSONArray("annotations").toJSONString();
                try {
                    minioUtil.writeString(bucketName, DcmConstant.DCM_ANNOTATION_PATH + task.getDatasetId() +"/annotation/" + medicineFileName + ".json", oneAnnotationContent);
                } catch (Exception e){
                    LogUtil.error(LogEnum.BIZ_DATASET, "write medicine annotation error:{}", e);
                }
            }
            List<Long> medicineFileIds = JSON.parseObject(taskDetail.getString("medicineFileIds"), ArrayList.class);
            DcmStateMachineUtil.stateChange(new StateChangeDTO() {{
                setObjectParam(new Object[]{medicineFileIds});
                setStateMachineType(DcmFileStateMachineConstant.DCM_FILE_STATE_MACHINE);
                setEventMethodName(DcmFileStateMachineConstant.AUTO_ANNOTATION_SAVE_EVENT);
            }});
            int finished = task.getFinished() + dcmsArray.size();
            if (task.getFinished() + dcmsArray.size() >= task.getTotal()) {
                QueryWrapper<DataMedicineFile> wrapper = new QueryWrapper<>();
                wrapper.lambda().eq(DataMedicineFile::getMedicineId, task.getDatasetId());
                List<DataMedicineFile> dataMedicineFiles = dataMedicineFileService.listFile(wrapper);
                List<DataMedicineFile> dataMedicineFilesOrderByAsc = dataMedicineFileService
                        .insertInstanceAndSort(dataMedicineFiles, task.getDatasetId());
                mergeAnnotation(task.getDatasetId(), dataMedicineFilesOrderByAsc);
                DataMedicine dataMedicine = dataMedicineService.getDataMedicineById(task.getDatasetId());
                DcmStateMachineUtil.stateChange(new StateChangeDTO() {{
                    setObjectParam(new Object[]{dataMedicine.getId()});
                    setStateMachineType(DcmDataStateMachineConstant.DCM_DATA_STATE_MACHINE);
                    setEventMethodName(DcmDataStateMachineConstant.AUTO_ANNOTATION_COMPLETE_EVENT);
                }});
            }
            task.setFinished(finished);
            taskService.updateByTaskId(task);
        }
    }

    /**
     * ????????????
     *
     * @param medicineAnnotationDTO ????????????DTO
     * @return ??????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public boolean save(MedicineAnnotationDTO medicineAnnotationDTO) {
        //????????????????????????fileID
        if (medicineAnnotationDTO.getType().equals(NumberConstant.NUMBER_0)&&CollectionUtils.isEmpty(medicineAnnotationDTO.getMedicalFiles())){
            return true;
        }
        //??????????????????????????????????????????????????????????????????
        DataMedicine medical = dataMedicineService.getDataMedicineById(medicineAnnotationDTO.getMedicalId());
        if (medical == null || medical.getDeleted()) {
            throw new BusinessException(ErrorEnum.DATAMEDICINE_ABSENT);
        } else if (DcmDataStateEnum.AUTOMATIC_LABELING_STATE.getCode().equals(medical.getStatus())) {
            throw new BusinessException(ErrorEnum.DATAMEDICINE_AUTOMATIC);
        }
        dataMedicineService.checkPublic(medicineAnnotationDTO.getMedicalId(),OperationTypeEnum.UPDATE);
        //????????????
        try {
            minioUtil.writeString(bucketName, DcmConstant.DCM_ANNOTATION_PATH + medical.getId() + DcmConstant.DCM_ANNOTATION, medicineAnnotationDTO.getAnnotations());
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_DATASET, "Medicine annotation is failed" + e.getMessage());
            return false;
        }
        //???????????????????????????????????????????????????
        DcmStateMachineUtil.stateChange(new StateChangeDTO() {{
            setObjectParam(new Object[]{medical});
            setStateMachineType(DcmDataStateMachineConstant.DCM_DATA_STATE_MACHINE);
            setEventMethodName(NumberConstant.NUMBER_1 == medicineAnnotationDTO.getType() ?
                    DcmDataStateMachineConstant.ANNOTATION_COMPLETE_EVENT : DcmDataStateMachineConstant.ANNOTATION_SAVE_EVENT);
        }});
        //?????????????????????????????????????????????????????????
        DcmStateMachineUtil.stateChange(new StateChangeDTO() {{
            setObjectParam(new Object[]{
                    NumberConstant.NUMBER_1 == medicineAnnotationDTO.getType() ?
                            dataMedicineFileMapper.selectList(new LambdaQueryWrapper<DataMedicineFile>() {{
                                eq(DataMedicineFile::getMedicineId, medical.getId());
                            }}).stream().map(DataMedicineFile::getId).collect(Collectors.toList()) :
                            dataMedicineFileMapper.selectList(new LambdaQueryWrapper<DataMedicineFile>() {{
                                in(DataMedicineFile::getSopInstanceUid, medicineAnnotationDTO.getMedicalFiles());
                            }}).stream().map(DataMedicineFile::getId).collect(Collectors.toList())
            });
            setStateMachineType(DcmFileStateMachineConstant.DCM_FILE_STATE_MACHINE);
            setEventMethodName(NumberConstant.NUMBER_1 == medicineAnnotationDTO.getType() ?
                    DcmFileStateMachineConstant.ANNOTATION_COMPLETE_EVENT : DcmFileStateMachineConstant.ANNOTATION_SAVE_EVENT);
        }});
        modifyUpdataUserId(medicineAnnotationDTO.getMedicalId());
        medical.setStop(false);
        dataMedicineService.updateByMedicineId(medical);
        return true;
    }

    /**
     * ??????????????????????????????
     *
     * @param ids ?????????????????????ID
     * @return ??????
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public Map<String, ScheduleVO> schedule(List<Long> ids) {
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorEnum.PARAM_ERROR);
        }
        List<Map<String, Object>> fileStatusCount = dataMedicineFileMapper.getFileStatusCount(ids);
        if (fileStatusCount.isEmpty()){
            return new HashMap<String, ScheduleVO>(ids.size()) {{
                ids.forEach(v -> {
                    ScheduleVO scheduleVO = new ScheduleVO(NumberConstant.NUMBER_0, NumberConstant.NUMBER_0, NumberConstant.NUMBER_0, NumberConstant.NUMBER_0);
                    //??????????????????
                    put(String.valueOf(v), scheduleVO);
                });

            }};
        }
        return new HashMap<String, ScheduleVO>(ids.size()) {{
            ids.forEach(v -> {
                ScheduleVO scheduleVO = new ScheduleVO(NumberConstant.NUMBER_0, NumberConstant.NUMBER_0, NumberConstant.NUMBER_0, NumberConstant.NUMBER_0);
                //??????????????????
                put(String.valueOf(v), scheduleVO);
                fileStatusCount.forEach(val -> {
                    if (v.equals(val.get(DcmConstant.MEDICINE_ID)) || v.equals(val.get(DcmConstant.MEDICINE_ID.toLowerCase()))) {
                        Integer count = Integer.valueOf(val.get(DcmConstant.COUNT).toString());
                        Object status = val.get(DcmConstant.STATUS);
                        if (status.equals(DcmFileStateCodeConstant.NOT_ANNOTATION_FILE_STATE)) {
                            scheduleVO.setUnfinished(count);
                        } else if (status.equals(DcmFileStateCodeConstant.ANNOTATION_COMPLETE_FILE_STATE)) {
                            scheduleVO.setFinished(count);
                        } else if (status.equals(DcmFileStateCodeConstant.AUTO_ANNOTATION_COMPLETE_FILE_STATE)) {
                            scheduleVO.setAutoFinished(count);
                        } else if (status.equals(DcmFileStateCodeConstant.ANNOTATION_FILE_STATE)) {
                            scheduleVO.setManualAnnotating(count);
                        }
                    }
                });
            });

        }};
    }

    /**
     * ????????????????????????JSON??????
     *
     * @param medicineId        ???????????????ID
     * @param dataMedicineFiles ???????????????????????????
     */
    @Override
    public void mergeAnnotation(Long medicineId, List<DataMedicineFile> dataMedicineFiles) {
        DataMedicine dataMedicine = dataMedicineService.getDataMedicineById(medicineId);
        String studyInstanceUID = dataMedicine.getStudyInstanceUid();
        String seriesInstanceUID = dataMedicine.getSeriesInstanceUid();
        JSONObject mergeJSONObject = new JSONObject();
        mergeJSONObject.put("StudyInstanceUID", studyInstanceUID);
        mergeJSONObject.put("seriesInstanceUID", seriesInstanceUID);
        JSONArray jsonArrayMerge = new JSONArray();
        dataMedicineFiles.forEach(dataMedicineFile -> {
            String targrtPath = StringUtils.substringBeforeLast(StringUtils.substringAfter(dataMedicineFile.getUrl(),
                    "/"), ".").replace("origin", "annotation") + ".json";
            InputStream inputStream = null;
            try {
                inputStream = minioUtil.getObjectInputStream(bucketName, targrtPath);
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(inputStream);
                Iterator<JsonNode> elements = jsonNode.elements();
                JSONArray jsonArray = new JSONArray();
                while (elements.hasNext()) {
                    JsonNode next = elements.next();
                    jsonArray.add(next.get("annotation").toString());
                }
                jsonArrayMerge.add(jsonArray);
            } catch (Exception e) {
                LogUtil.error(LogEnum.BIZ_DATASET, "get medicine annotation json failed, {}", e);
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException e) {
                    LogUtil.error(LogEnum.BIZ_DATASET, "close inputStream failed, {}", e);
                }
            }
        });
        JSONObject jsonObjectTemp = new JSONObject();
        jsonObjectTemp.put("annotation", jsonArrayMerge);
        String mergePointsString = jsonObjectTemp.getString("annotation").replace("\"", "");
        mergeJSONObject.put("annotation", mergePointsString);
        String mergePath = StringUtils.substringBeforeLast(StringUtils.substringAfter(dataMedicineFiles.get(0).getUrl()
                , "/"), "/").replace("origin", "annotation") + "/merge_annotation.json";
        try {
            minioUtil.writeString(bucketName, mergePath, mergeJSONObject.toJSONString());
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_DATASET, "write merge_annotation failed, {}", e);
        }
    }

    /**
     * ????????????????????????????????????????????????ID
     *
     * @param medicineId ???????????????ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void modifyUpdataUserId(Long medicineId){
        DataMedicine dataMedicine = dataMedicineService.getDataMedicineById(medicineId);
        dataMedicine.setUpdateUserId(JwtUtils.getCurUserId());
        dataMedicineService.updateByMedicineId(dataMedicine);
        dataMedicineFileService.updateUserIdByMedicineId(dataMedicine.getId());
    }

}
