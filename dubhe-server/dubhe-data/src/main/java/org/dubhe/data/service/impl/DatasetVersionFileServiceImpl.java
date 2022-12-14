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
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang.ArrayUtils;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.base.constant.NumberConstant;
import org.dubhe.biz.base.service.UserContextService;
import org.dubhe.biz.base.utils.StringUtils;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.statemachine.dto.StateChangeDTO;
import org.dubhe.data.constant.*;
import org.dubhe.data.dao.DatasetVersionFileMapper;
import org.dubhe.data.domain.bo.FileUploadBO;
import org.dubhe.data.domain.dto.DatasetVersionFileDTO;
import org.dubhe.data.domain.entity.*;
import org.dubhe.data.machine.constant.FileStateCodeConstant;
import org.dubhe.data.machine.constant.FileStateMachineConstant;
import org.dubhe.data.machine.utils.StateMachineUtil;
import org.dubhe.data.service.DataFileAnnotationService;
import org.dubhe.data.service.DatasetService;
import org.dubhe.data.service.DatasetVersionFileService;
import org.dubhe.data.service.FileService;
import org.dubhe.data.util.GeneratorKeyUtil;
import org.dubhe.data.util.FileUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static org.dubhe.data.constant.Constant.DEFAULT_VERSION;

/**
 * @description ??????????????????????????? ???????????????
 * @date 2020-05-14
 */
@Service
public class DatasetVersionFileServiceImpl extends ServiceImpl<DatasetVersionFileMapper, DatasetVersionFile>
        implements DatasetVersionFileService, IService<DatasetVersionFile> {

    @Resource
    private DatasetVersionFileMapper datasetVersionFileMapper;

    @Resource
    @Lazy
    private DatasetService datasetService;

    @Resource
    @Lazy
    private FileService fileService;

    @Autowired
    private GeneratorKeyUtil generatorKeyUtil;

    @Autowired
    private DataFileAnnotationService dataFileAnnotationService;

    @Autowired
    private DataFileAnnotationServiceImpl dataFileAnnotationServiceImpl;

    @Autowired
    private UserContextService userContextService;


    /**
     * ???????????????????????????????????????
     *
     * @param datasetId   ?????????id
     * @param versionName ????????????
     * @return List<DatasetVersionFile> ??????????????????
     */
    @Override
    public List<DatasetVersionFile> findByDatasetIdAndVersionName(Long datasetId, String versionName) {
        return datasetVersionFileMapper.findByDatasetIdAndVersionName(datasetId, versionName);
    }

    /**
     * ?????????????????????????????????
     *
     * @param data ??????????????????
     */
    @Override
    public void insertList(List<DatasetVersionFile> data) {
        LogUtil.debug(LogEnum.BIZ_DATASET, "save dataset version files start, file size {}", data.size());
        Long start = System.currentTimeMillis();
        data.stream().forEach(datasetVersionFile -> {
            if (null == datasetVersionFile.getAnnotationStatus()) {
                datasetVersionFile.setAnnotationStatus(FileStateCodeConstant.NOT_ANNOTATION_FILE_STATE);
            }
        });
        Queue<Long> dataFileIds = generatorKeyUtil.getSequenceByBusinessCode(Constant.DATA_VERSION_FILE, data.size());
        for (DatasetVersionFile datasetVersionFile : data) {
            datasetVersionFile.setId(dataFileIds.poll());
        }
        datasetVersionFileMapper.saveList(data);
        LogUtil.debug(LogEnum.BIZ_DATASET, "save dataset version files end, times {}" , (System.currentTimeMillis() - start));
    }

    /**
     * ????????????????????????
     *
     * @param datasetId     ?????????id
     * @param versionSource ?????????
     * @param versionTarget ????????????
     */
    @Override
    public void newShipVersionNameChange(Long datasetId, String versionSource, String versionTarget) {
        List<DataFileAnnotation> dataFileAnnotations = dataFileAnnotationService.getAnnotationByVersion(datasetId,versionSource,
                MagicNumConstant.ZERO);
        datasetVersionFileMapper.newShipVersionNameChange(datasetId, versionSource, versionTarget);
        List<DataFileAnnotation> updateAnnotations = new ArrayList<>();
        dataFileAnnotations.stream().filter(dataFileAnnotation -> dataFileAnnotation.getStatus().equals(MagicNumConstant.ZERO))
                .forEach(dataFileAnnotation -> {
                    dataFileAnnotation.setStatus(MagicNumConstant.TWO);
                    dataFileAnnotation.setInvariable(MagicNumConstant.ONE);
                    updateAnnotations.add(dataFileAnnotation);
                });
        dataFileAnnotationService.updateDataFileAnnotations(updateAnnotations);
    }

    /**
     * ????????????????????????
     *
     * @param datasetId   ?????????id
     * @param versionName ????????????
     * @param fileIds     ??????id??????
     */
    @Override
    public void deleteShip(Long datasetId, String versionName, List<Long> fileIds) {
        //???????????????????????????
        List<DatasetVersionFile> files = datasetVersionFileMapper.selectByDatasetIdAndVersionNameAndFileIds(datasetId, versionName, fileIds);
        if (!CollectionUtils.isEmpty(files) && Objects.isNull(versionName)) {
            List<Long> ids = files.stream().map(a -> a.getId()).collect(Collectors.toList());
            dataFileAnnotationService.updateStatusByVersionIds(datasetId,ids, true);
        }
        datasetVersionFileMapper.updateStatusByFileIdAndDatasetId(datasetId, versionName, fileIds);
    }


    /**
     * ???????????????????????????????????????
     *
     * @param datasetId    ?????????id
     * @param versionName  ????????????
     * @param fileId       ??????id
     * @param sourceStatus ?????????
     * @param targetStatus ????????????
     * @return int         ?????????????????????
     */
    @Override
    public int updateAnnotationStatus(Long datasetId, String versionName, Set<Long> fileId, Integer sourceStatus, Integer targetStatus) {
        //?????????????????????????????????????????????
        DatasetVersionFile versionFile = StringUtils.isBlank(versionName) ? null : getFirstByDatasetIdAndVersionNum(datasetId, versionName, null);

        UpdateWrapper<DatasetVersionFile> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("dataset_id", datasetId);
        updateWrapper.in("file_id", fileId);
        DatasetVersionFile datasetVersionFile = new DatasetVersionFile() {{
            setAnnotationStatus(targetStatus);
        }};
        if (StringUtils.isNotEmpty(versionName)) {
            updateWrapper.eq("version_name", versionName);
        }
        if (sourceStatus != null) {
            updateWrapper.eq("annotation_status", sourceStatus);
        }
        if (versionFile != null) {
            datasetVersionFile.setChanged(Constant.CHANGED);
        }
        //=======================???????????????===================================//
        datasetVersionFile = baseMapper.selectOne(updateWrapper);
        if (versionFile != null) {
            datasetVersionFile.setChanged(Constant.CHANGED);
        }
        //?????????????????????
        StateChangeDTO stateChangeDTO = new StateChangeDTO();
        //????????????????????????????????????????????????
        Object[] objects = new Object[1];
        objects[0] = datasetVersionFile;
        stateChangeDTO.setObjectParam(objects);
        stateChangeDTO.setStateMachineType(FileStateMachineConstant.FILE_STATE_MACHINE);
        //??????????????????????????????
        if (targetStatus.equals(FileStateCodeConstant.MANUAL_ANNOTATION_FILE_STATE)) {
            stateChangeDTO.setEventMethodName(FileStateMachineConstant.FILE_MANUAL_ANNOTATION_SAVE_EVENT);
        }
        if (targetStatus.equals(FileStateCodeConstant.ANNOTATION_COMPLETE_FILE_STATE)) {
            stateChangeDTO.setEventMethodName(FileStateMachineConstant.FILE_SAVE_COMPLETE_EVENT);
        }
        //=======================???????????????===================================//
        StateMachineUtil.stateChange(stateChangeDTO);
        return 0;
    }

    /**
     * ??????????????????????????????????????????(????????????)
     *
     * @param datasetId                 ??????id
     * @param versionName               ????????????
     * @return List<DatasetVersionFile> ??????????????????
     */
    @Override
    public List<DatasetVersionFile> getFilesByDatasetIdAndVersionName(Long datasetId, String versionName) {
        QueryWrapper<DatasetVersionFile> queryWrapper = new QueryWrapper();
        queryWrapper.eq("dataset_id", datasetId);
        if (StringUtils.isNotEmpty(versionName)) {
            queryWrapper.eq("version_name", versionName);
        }
        queryWrapper.notIn("status", MagicNumConstant.ONE);
        return baseMapper.selectList(queryWrapper);
    }


    /**
     * ?????????????????????
     *
     * @param datasetId   ?????????id
     * @param versionName ????????????
     * @param status      ??????
     * @param offset      ?????????
     * @param limit       ?????????
     * @param order       ????????????
     * @return ???????????????????????????
     */
    @Override
    public List<DatasetVersionFileDTO> getListByDatasetIdAndAnnotationStatus(Long datasetId, String versionName, Integer[] status, Long offset, Integer limit, String orderByName, String order, Long[] labelId) {
        order = Objects.isNull(order) ? "asc" : order;
        Dataset oneById = datasetService.getOneById(datasetId);
        List<DataFileAnnotation> labelIdByDatasetIdAndVersionId = new ArrayList<>();
        if(!ArrayUtils.isEmpty(labelId) || (DatatypeEnum.AUDIO.getValue().equals(oneById.getDataType()) &&
                !status[0].equals(FileTypeEnum.UNFINISHED_FILE.getValue()) && !status[0].equals(FileTypeEnum.UNFINISHED.getValue())) &&
                !oneById.getAnnotateType().equals(AnnotateTypeEnum.SPEECH_RECOGNITION.getValue())){
            labelIdByDatasetIdAndVersionId = dataFileAnnotationService.getLabelIdByDatasetIdAndVersionId(labelId, datasetId,offset,limit, oneById.getCurrentVersionName());
            if(labelIdByDatasetIdAndVersionId.isEmpty()){
                return null;
            }
        }
        List<DatasetVersionFileDTO> idByDatasetIdAndAnnotationStatus = datasetVersionFileMapper.getIdByDatasetIdAndAnnotationStatus(
                datasetId, versionName, status==null?null:new HashSet<Integer>(Arrays.asList(status)),
                orderByName, offset, limit, order, labelIdByDatasetIdAndVersionId,oneById.getAnnotateType());
        for(int i=0;i<labelIdByDatasetIdAndVersionId.size();i++){
            for (int j=0;j<idByDatasetIdAndAnnotationStatus.size();j++){
                if(idByDatasetIdAndAnnotationStatus.get(j).getId().equals(labelIdByDatasetIdAndVersionId.get(i).getVersionFileId())){
                    idByDatasetIdAndAnnotationStatus.get(j).setLabelId(new Long[]{labelIdByDatasetIdAndVersionId.get(i).getLabelId()});
                    idByDatasetIdAndAnnotationStatus.get(j).setPrediction(labelIdByDatasetIdAndVersionId.get(i).getPrediction());
                }
            }
        }
        return idByDatasetIdAndAnnotationStatus;
    }

    /**
     * ??????????????????????????????????????????
     *
     * @param datasetId           ?????????id
     * @param versionName         ????????????
     * @param status              ??????
     * @return DatasetVersionFile ??????????????????
     */
    @Override
    public DatasetVersionFile getFirstByDatasetIdAndVersionNum(Long datasetId, String versionName, Collection<Integer> status) {
        QueryWrapper<DatasetVersionFile> queryWrapper = buildQueryWrapperWithDatasetIdVersionNameAndStatus(datasetId, versionName, status);
        queryWrapper.orderByAsc("id");
        queryWrapper.last(" limit 1");
        return baseMapper.selectOne(queryWrapper);
    }

    public QueryWrapper<DatasetVersionFile> buildQueryWrapperWithDatasetIdVersionNameAndStatus(Long datasetId, String versionName, Collection<Integer> status) {
        QueryWrapper<DatasetVersionFile> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dataset_id", datasetId);
        if (!CollectionUtils.isEmpty(status)) {
            queryWrapper.in("annotation_status", status);
        }
        if (StringUtils.isNotEmpty(versionName)) {
            queryWrapper.eq("version_name", versionName);
        }
        queryWrapper.ne("status",MagicNumConstant.ONE);
        return queryWrapper;
    }

    @Override
    public Integer getFileCountByDatasetIdAndAnnotationStatus(Long datasetId, String versionName, Collection<Integer> status) {
        QueryWrapper<DatasetVersionFile> queryWrapper = buildQueryWrapperWithDatasetIdVersionNameAndStatus(datasetId, versionName, status);
        return baseMapper.selectCount(queryWrapper);
    }

    /**
     * ??????????????????????????????id??????????????????
     *
     * @param datasetVersionFiles ???????????????????????????
     * @return List<File> ????????????
     */
    @Override
    public List<File> getFileListByVersionFileList(List<DatasetVersionFile> datasetVersionFiles) {
        QueryWrapper<File> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(File::getDatasetId, datasetVersionFiles.get(0).getDatasetId())
                .in(File::getId, datasetVersionFiles.stream().map(DatasetVersionFile::getFileId).collect(Collectors.toList()))
                .eq(File::getFileType, DatatypeEnum.IMAGE)
                .orderByAsc(File::getId);
        return fileService.listFile(wrapper);
    }

    /**
     * ??????????????????????????????????????????
     *
     * @param datasetId   ?????????id
     * @param versionName ??????????????????
     * @return List<File> ??????????????????
     */
    @Override
    public List<Integer> getFileStatusListByDatasetAndVersion(Long datasetId, String versionName) {
        if (datasetId == null) {
            LogUtil.error(LogEnum.BIZ_DATASET, "datasetId isEmpty");
            return null;
        }
        return datasetVersionFileMapper.findFileStatusListByDatasetAndVersion(datasetId, versionName);
    }

    /**
     * ????????????
     *
     * @param dataset ???????????????
     */
    @Override
    public void rollbackDataset(Dataset dataset) {
        if (StringUtils.isNoneBlank(dataset.getCurrentVersionName()) && isNeedToRollback(dataset)) {
            doRollback(dataset);
        }
    }

    /**
     * ???????????????????????????????????????
     *
     * @param dataset        ????????????????????????
     * @return boolean ???????????????????????????
     */
    @Override
    public boolean isNeedToRollback(Dataset dataset) {
        LambdaQueryWrapper<DatasetVersionFile> isChanged = new LambdaQueryWrapper<DatasetVersionFile>()
                .eq(DatasetVersionFile::getDatasetId, dataset.getId())
                .eq(DatasetVersionFile::getVersionName, dataset.getCurrentVersionName())
                .eq(DatasetVersionFile::getChanged, Constant.CHANGED);
        return baseMapper.selectCount(isChanged) > 0;
    }


    /**
     * ??????????????????????????????????????????????????????????????????
     *
     * @param id           ?????????id
     * @param versionName  ?????????
     */
    @Override
    public void updateChanged(Long id, String versionName) {
        baseMapper.updateChanged(id, versionName);
    }

    /**
     * ???????????????
     *
     * @param dataset ????????????????????????
     * @return boolean ???????????????????????????
     */
    public void doRollback(Dataset dataset) {
        //???????????????????????????????????????????????????
        datasetVersionFileMapper.rollbackFileAndAnnotationStatus(dataset.getId(), dataset.getCurrentVersionName(), Constant.CHANGED);
    }

    /**
     * ????????????????????????????????????????????????
     *
     * @param dataset    ???????????????
     * @return ??????????????????
     */
    @Override
    public Integer getSourceFileCount(Dataset dataset) {
        return datasetVersionFileMapper.getSourceFileCount(dataset);
    }

    /**
     * ?????????????????????????????????
     *
     * @param datasetId   ?????????id
     * @param versionName ????????????
     * @return List<DatasetVersionFile> ??????????????????
     */
    @Override
    public List<DatasetVersionFile> getNeedEnhanceFilesByDatasetIdAndVersionName(Long datasetId, String versionName) {
        return baseMapper.getNeedEnhanceFilesByDatasetIdAndVersionName(datasetId, versionName);
    }

    /**
     * ????????????????????????????????????
     *
     * @param datasetId   ?????????id
     * @param versionName ????????????
     * @param fileId      ??????id
     * @return List<File> ??????????????????
     */
    @Override
    public List<File> getEnhanceFileList(Long datasetId, String versionName, Long fileId) {
        return baseMapper.getEnhanceFileList(datasetId, versionName, fileId);
    }

    /**
     * ????????????????????????
     * @param datasetId   ?????????id
     * @param versionName ????????????
     * @return Integer    ??????????????????????????????
     */
    @Override
    public Integer getEnhanceFileCount(Long datasetId, String versionName) {
        return baseMapper.getEnhanceFileCount(datasetId, versionName);
    }

    /**
     * ????????????????????????????????????????????????
     *
     * @param datasetId   ?????????id
     * @param versionName ?????????????????????
     * @return ??????????????????????????????????????????
     */
    @Override
    public Integer getImageCountsByDatasetIdAndVersionName(Long datasetId, String versionName) {
        QueryWrapper<DatasetVersionFile> datasetVersionFileQueryWrapper = new QueryWrapper<>();
        datasetVersionFileQueryWrapper.eq("dataset_id", datasetId)
                .eq("version_name", versionName);
        return baseMapper.selectCount(datasetVersionFileQueryWrapper);
    }


    /**
     * ???????????????????????????????????????
     *
     * @param offset       ?????????
     * @param pageSize     ?????????
     * @param datasetId    ?????????ID
     * @param versionName  ?????????????????????
     * @return ???????????????????????????
     */
    @Override
    public List<DatasetVersionFile> getPages(int offset, int pageSize, Long datasetId, String versionName) {
        QueryWrapper<DatasetVersionFile> datasetVersionFileQueryWrapper = new QueryWrapper<>();
        datasetVersionFileQueryWrapper.eq("dataset_id", datasetId);
        if (StringUtils.isNotEmpty(versionName)) {
            datasetVersionFileQueryWrapper.eq("version_name", versionName);
        }
        datasetVersionFileQueryWrapper.last("limit " + offset + "," + pageSize);
        return baseMapper.selectList(datasetVersionFileQueryWrapper);
    }


    /**
     * ???????????????????????????????????????
     *
     * @param queryWrapper ????????????
     * @return Integer ?????????????????????????????????
     */
    @Override
    public Integer getFileCountByDatasetIdAndVersion(LambdaQueryWrapper<DatasetVersionFile> queryWrapper) {
        return baseMapper.selectCount(queryWrapper);
    }

    /**
     * ???????????????????????????????????????
     *
     * @param datasetId   ?????????ID
     * @param versionName ?????????????????????
     * @return Map ?????????????????????????????????
     */
    @Override
    public Map<Integer, Integer> getDatasetVersionFileCount(Long datasetId, String versionName) {
        return baseMapper.getDatasetVersionFileCount(datasetId, versionName);
    }

    /**
     * ???????????????????????????????????????
     *
     * @param datasetVersion ???????????????
     * @return ?????????????????????
     */
    @Override
    public Integer selectDatasetVersionFileCount(DatasetVersion datasetVersion) {
        return baseMapper.selectCount(new LambdaQueryWrapper<DatasetVersionFile>() {{
            eq(DatasetVersionFile::getDatasetId, datasetVersion.getDatasetId());
            eq(DatasetVersionFile::getVersionName, datasetVersion.getVersionName());
            ne(DatasetVersionFile::getStatus, NumberConstant.NUMBER_0);
            ne(DatasetVersionFile::getStatus, NumberConstant.NUMBER_1);
        }});
    }

    /**
     * ??????offset
     *
     * @param datasetId ?????????id
     * @param fileId    ??????id
     * @param type      ???????????????
     * @return Integer ?????????offset
     */
    @Override
    public Integer getOffset(Long fileId, Long datasetId, Integer[] type, Long[] labelIds) {
        Dataset dataset = datasetService.getOneById(datasetId);
        DatasetVersionFile datasetVersionFile = baseMapper.selectOne(new LambdaQueryWrapper<DatasetVersionFile>() {{
            eq(DatasetVersionFile::getDatasetId, dataset.getId());
            if (StringUtils.isBlank(dataset.getCurrentVersionName())) {
                isNull(DatasetVersionFile::getVersionName);
            } else {
                eq(DatasetVersionFile::getVersionName, datasetService.getOneById(datasetId).getCurrentVersionName());
            }
            eq(DatasetVersionFile::getFileId, fileId);
        }});
        if (ObjectUtil.isNull(datasetVersionFile)) {
            return 0;
        }
        Set<Integer> annotationStatus = null;
        if (type != null && type.length > 0) {
            annotationStatus = new HashSet<>();
            for (Integer in : type) {
                annotationStatus.addAll(FileTypeEnum.getStatus(in));
            }
        }
        return baseMapper.getOffset(datasetId, annotationStatus, dataset.getCurrentVersionName(),
                datasetVersionFile.getId(), labelIds);
    }


    /**
     * ??????????????????????????????????????????
     *
     * @param eq ??????
     * @return long ??????????????????
     */
    @Override
    public long selectCount(LambdaQueryWrapper<DatasetVersionFile> eq) {
        return baseMapper.selectCount(eq);
    }

    /**
     * ?????????????????????????????????
     *
     * @param datasetId    ?????????ID
     * @param versionName  ????????????
     * @param fileId       ??????ID
     * @return ??????????????????
     */
    @Override
    public DatasetVersionFile getDatasetVersionFile(Long datasetId, String versionName, Long fileId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<DatasetVersionFile>() {{
            eq(DatasetVersionFile::getDatasetId, datasetId);
            if (StringUtils.isBlank(versionName)) {
                isNull(DatasetVersionFile::getVersionName);
            } else {
                eq(DatasetVersionFile::getVersionName, versionName);
            }
            eq(DatasetVersionFile::getFileId, fileId);
        }});
    }

    /**
     * ????????????????????????????????????
     *
     * @param datasetId  ?????????ID
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteAnnotating(Long datasetId) {
        Dataset dataset = datasetService.getOneById(datasetId);
        datasetVersionFileMapper.update(
                new DatasetVersionFile() {{
                    setAnnotationStatus(FileStateCodeConstant.NOT_ANNOTATION_FILE_STATE);
                    setChanged(Constant.CHANGED);
                }},
                new UpdateWrapper<DatasetVersionFile>()
                        .lambda()
                        .eq(DatasetVersionFile::getDatasetId, dataset.getId())
                        .eq(dataset.getCurrentVersionName() != null, DatasetVersionFile::getVersionName, dataset.getCurrentVersionName())
        );
    }


    /**
     * ???????????????????????????ID
     *
     * @param id                    ?????????ID
     * @param currentVersionName    ????????????
     * @param fileIds               ??????id
     * @return ???????????????????????????
     */
    @Override
    public List<DatasetVersionFile> getVersionFileByDatasetAndFile(Long id, String currentVersionName, Set<Long> fileIds) {
        return datasetVersionFileMapper.selectList(new LambdaQueryWrapper<DatasetVersionFile>() {{
                                                       eq(DatasetVersionFile::getDatasetId, id);
                                                       if (StringUtils.isBlank(currentVersionName)) {
                                                           isNull(DatasetVersionFile::getVersionName);
                                                       } else {
                                                           eq(DatasetVersionFile::getVersionName, currentVersionName);
                                                       }
                                                       in(DatasetVersionFile::getFileId, fileIds);
                                                   }}
        );
    }

    /**
     * ??????????????????
     *
     * @param datasetVersionFile ???????????????????????????
     */
    @Override
    public void updateStatusById(DatasetVersionFile datasetVersionFile) {
        datasetVersionFileMapper.updateAnnotationStatusById(datasetVersionFile.getAnnotationStatus(), datasetVersionFile.getDatasetId(), datasetVersionFile.getId());
    }


    /**
     * ????????????????????????????????????????????????
     *
     * @param datasetId             ?????????ID
     * @param currentVersionName    ??????????????????
     * @param status                ??????
     * @param labelIds               ??????ID
     * @return ????????????
     */
    @Override
    public int selectFileListTotalCount(Long datasetId, String currentVersionName, Integer[] status, Long[] labelIds) {
        Set<Integer> objects = new HashSet<>();
        if(ArrayUtil.isEmpty(status)){
            objects = FileTypeEnum.getStatus(NumberConstant.NUMBER_0);
        } else {
            for(Integer sta : status){
                objects.addAll(FileTypeEnum.getStatus(sta));
            }
        }

        List<Long> versionId = null;
        if(!ArrayUtils.isEmpty(labelIds)){
            versionId = baseMapper.findByDatasetIdAndVersionNameAndStatus(datasetId,currentVersionName, labelIds);
        }
        return datasetVersionFileMapper.selectFileListTotalCount(datasetId, currentVersionName, objects, versionId);
    }


    /**
     * ???????????????ID????????????????????????????????????
     *
     * @param datasetId     ?????????ID
     * @param versionName   ????????????
     * @return ???????????????????????????
     */
    @Override
    public List<DatasetVersionFile> getDatasetVersionFileByDatasetIdAndVersion(Long datasetId, String versionName) {
        return baseMapper.findByDatasetIdAndVersionName(datasetId, versionName);
    }


    /**
     * ????????????????????????
     *
     * @param originDataset ??????????????????
     * @param targetDataset ?????????????????????
     * @param versionFiles  ?????????????????????
     * @param files         ?????????????????????
     */
    @Override
    public void backupDatasetVersionFileDataByDatasetId(Dataset originDataset, Dataset targetDataset, List<DatasetVersionFile> versionFiles, List<File> files) {
        Map<String, Long> fileNameMap = files.stream().collect(Collectors.toMap(File::getName, File::getId));
        Queue<Long> dataFileIds = generatorKeyUtil.getSequenceByBusinessCode(Constant.DATA_VERSION_FILE, versionFiles.size());
        for (DatasetVersionFile f : versionFiles) {
            f.setId(dataFileIds.poll());
            f.setFileId(fileNameMap.get(f.getFileName()));
            f.setVersionName(DEFAULT_VERSION);
            f.setDatasetId(targetDataset.getId());
            f.setFileName(f.getFileName());
        }
        List<List<DatasetVersionFile>> splitVersionFiles = CollectionUtil.split(versionFiles, MagicNumConstant.FOUR_THOUSAND);
        splitVersionFiles.forEach(splitVersionFile->baseMapper.insertBatch(splitVersionFile));
    }

    /**
     * ??????id??????????????????id
     *
     * @param datasetId         ?????????id
     * @param fileIds           ??????id
     * @return List<Long>       ????????????id
     */
    @Override
    public List<Long> getVersionFileIdsByFileIds(Long datasetId, List<Long> fileIds) {
        return baseMapper.getVersionFileIdsByFileIds(datasetId, fileIds);
    }

    /**
     * ??????????????????id
     *
     * @param datasetId         ?????????id
     * @param fileName            ????????????
     * @param versionName       ????????????
     */
    @Override
    public Long getVersionFileIdByFileName(Long datasetId, String fileName, String versionName) {
        return baseMapper.getVersionFileIdByFileName(datasetId, fileName, versionName);
    }

    /**
     * ??????????????????????????????
     * @param datasetId         ?????????id
     * @return List<FileUploadBO>
     */
    @Override
    public List<FileUploadBO> getFileUploadContent(Long datasetId, List<Long> fileIds) {
        return baseMapper.getFileUploadContent(datasetId,fileIds);
    }

    @Override
    public Long getVersionFileCountByStatusVersionAndLabelId(Long datasetId, Set<Integer> annotationStatus, String versionName, List<Long> labelIds) {
        List<Long> versionId = null;
        if(CollectionUtil.isNotEmpty(labelIds)){
            Long[] labelArr = new Long[labelIds.size()];
            labelIds.toArray(labelArr);
            versionId = baseMapper.findByDatasetIdAndVersionNameAndStatus(datasetId, versionName, labelArr);
            if (CollectionUtil.isEmpty(versionId)) {
                return 0L;
            }
        }
        int count = datasetVersionFileMapper.selectFileListTotalCount(datasetId, versionName, annotationStatus, versionId);
        return Long.valueOf(count);
    }

    /**
     * ?????????????????????
     *
     * @param datasetId         ?????????id
     * @param fileId            ??????id
     */
    @Override
    public void deleteByFileId(Long datasetId, Long fileId) {
        Dataset dataset = datasetService.getOneById(datasetId);
        Long versionFileId = datasetVersionFileMapper.getVersionFileIdByFileName(datasetId, fileService.get(fileId, datasetId).getName()
                , dataset.getCurrentVersionName());
        QueryWrapper<DataFileAnnotation> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(DataFileAnnotation::getDatasetId, datasetId).eq(DataFileAnnotation::getVersionFileId, versionFileId);
        dataFileAnnotationServiceImpl.getBaseMapper().delete(queryWrapper);
    }
}