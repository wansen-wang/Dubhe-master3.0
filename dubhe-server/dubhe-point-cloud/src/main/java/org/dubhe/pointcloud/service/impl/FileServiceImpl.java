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
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.dubhe.biz.base.context.UserContext;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.service.UserContextService;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.dataresponse.factory.DataResponseFactory;
import org.dubhe.biz.file.utils.MinioUtil;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.permission.base.BaseService;
import org.dubhe.biz.statemachine.dto.StateChangeDTO;
import org.dubhe.pointcloud.common.Constant;
import org.dubhe.pointcloud.dao.PcDatasetFileMapper;
import org.dubhe.pointcloud.dao.PcDatasetMapper;
import org.dubhe.pointcloud.domain.dto.FileDifficultCasePublishDTO;
import org.dubhe.pointcloud.domain.dto.FileDoneInputDTO;
import org.dubhe.pointcloud.domain.dto.FileMarkDifficultDTO;
import org.dubhe.pointcloud.domain.dto.MarkObjMsgDTO;
import org.dubhe.pointcloud.domain.entity.PcDataset;
import org.dubhe.pointcloud.domain.entity.PcDatasetFile;
import org.dubhe.pointcloud.domain.vo.FileDifficultOutputVO;
import org.dubhe.pointcloud.domain.vo.FileQueryInputVO;
import org.dubhe.pointcloud.domain.vo.FileUploadSaveInputVO;
import org.dubhe.pointcloud.domain.vo.MarkSaveInputVO;
import org.dubhe.pointcloud.enums.ErrorEnum;
import org.dubhe.pointcloud.enums.MarkStatusEnum;
import org.dubhe.pointcloud.enums.PcDatasetMachineStatusEnum;
import org.dubhe.pointcloud.machine.constant.PcDatasetEventMachineConstant;
import org.dubhe.pointcloud.service.FileService;
import org.dubhe.pointcloud.task.DifficultCaseAsyncCopy;
import org.dubhe.pointcloud.util.PathUtil;
import org.dubhe.pointcloud.util.StateMachineUtil;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @description 文件处理服务实现类
 * @date 2022-04-01
 **/
@Service
public class FileServiceImpl implements FileService {
    private final static Logger logger = LoggerFactory.getLogger(FileServiceImpl.class);

    /**
     * MinIO工具类
     */
    @Resource
    private MinioUtil minioUtil;

    @Resource
    private PcDatasetFileMapper pcDatasetFileMapper;

    @Resource
    private PcDatasetMapper pcDatasetMapper;

    @Resource
    private UserContextService userContextService;

    @Resource
    private DifficultCaseAsyncCopy difficultCaseAsyncCopy;

    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    @Value("${minio.accessKey}")
    private String accessKey;
    @Value("${minio.secretKey}")
    private String secretKey;
    @Value("${minio.url}")
    private String url;
    @Value("${minio.bucketName}")
    private String bucketName;

    @Resource
    PathUtil pathUtil;

    @Override
    public void upload(File file, String key) {

    }

    @Override
    public String getFileContext(String url) {
        try {
            return minioUtil.readString(bucketName, url);
        } catch (Exception e) {
            LogUtil.error(LogEnum.POINT_CLOUD, "读取文件内容失败.Error message :{}", e.getMessage());
            throw new BusinessException("读取文件内容失败");
        }
    }

    @Override
    public Map<String, Object> queryFileList(FileQueryInputVO fileQueryInputVO) {
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("当前用户信息已失效");
        }
        LogUtil.info(LogEnum.POINT_CLOUD, "User {} queried file list, with the query {}", user.getUsername(),
                JSONObject.toJSONString(fileQueryInputVO));

        if (!BaseService.isAdmin(user)) {
            fileQueryInputVO.setCreateUserId(user.getId());
        }
        List<PcDatasetFile> entityList = pcDatasetFileMapper.queryFileList(fileQueryInputVO);

        Map<String, Object> result = new HashMap<>();
        result.put("fileCount", entityList.size());
        int markedCount = 0;
        for (PcDatasetFile entity : entityList) {
            if (entity.getMarkStatus() != null && entity.getMarkStatus().equals(MarkStatusEnum.MANUAL_MARKED.getCode())) {
                markedCount++;
            }
            entity.setMarkStatusName(MarkStatusEnum.getMarkStatusName(entity.getMarkStatus()));
        }

        result.put("markedCount", markedCount);
        result.put("fileList", entityList);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataResponseBody done(FileDoneInputDTO fileDoneInputDTO) {
        PcDatasetFile entity = pcDatasetFileMapper.selectOne(
                new LambdaQueryWrapper<PcDatasetFile>()
                        .eq(PcDatasetFile::getDatasetId, fileDoneInputDTO.getDatasetId())
                        .eq(PcDatasetFile::getId, fileDoneInputDTO.getFileId())
        );
        if (Objects.isNull(entity)) {
            LogUtil.error(LogEnum.POINT_CLOUD, "Failed to find point cloud file where  file id={}", fileDoneInputDTO.getFileId());
            throw new BusinessException("未找到该文件");
        }
        // 更新标注状态为标注完成或手动标注中
        Integer markStatus = fileDoneInputDTO.getDoneStatus() ? MarkStatusEnum.MANUAL_MARKED.getCode() : MarkStatusEnum.MANUAL_MARKING.getCode();
        entity.setMarkStatus(markStatus);
        StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{entity.getDatasetId()}, PcDatasetEventMachineConstant.PC_DATASET_MACHINE,
                PcDatasetEventMachineConstant.LABELING_PC_DATASET_EVENT));

        pcDatasetFileMapper.update(entity,
                new LambdaQueryWrapper<PcDatasetFile>()
                        .eq(PcDatasetFile::getDatasetId, entity.getDatasetId())
                        .eq(PcDatasetFile::getId, entity.getId())
        );
        entity.setMarkStatusName(MarkStatusEnum.getMarkStatusName(markStatus));
        return DataResponseFactory.success(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataResponseBody mark(FileMarkDifficultDTO fileMarkDifficultDTO) {
        PcDatasetFile entity = pcDatasetFileMapper.selectOne(
                new LambdaQueryWrapper<PcDatasetFile>()
                        .eq(PcDatasetFile::getId, fileMarkDifficultDTO.getFileId())
                        .eq(PcDatasetFile::getDatasetId, fileMarkDifficultDTO.getId())
        );
        if (Objects.isNull(entity)) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset file with id {} cannot be found", fileMarkDifficultDTO.getFileId());
            throw new BusinessException("未找到该文件");
        }
        PcDataset pcDataset = pcDatasetMapper.selectById(fileMarkDifficultDTO.getId());

        if (Objects.isNull(pcDataset)) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset  with id {} cannot be found", fileMarkDifficultDTO.getId());
            throw new BusinessException("未找到该数据集");
        }
        //在数据集为 手动标注种才能标记文件为难例
        if (!PcDatasetMachineStatusEnum.LABELING.getCode().equals(pcDataset.getStatus())) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset  with id {}.Difficult cases cannot be marked in the non-marking state where status ={}",
                    pcDataset.getId(), PcDatasetMachineStatusEnum.getDesc(pcDataset.getStatus()));
            throw new BusinessException("非标注中状态不能进行标记难例");
        }

        FileDifficultOutputVO fileDifficultOutputVO = new FileDifficultOutputVO();
        //若标识一致，不用变更难例数量
        if (fileMarkDifficultDTO.getDifficulty().equals(entity.getDifficulty())) {
            getFileDifficultOutputVO(entity, fileDifficultOutputVO, pcDataset.getDifficultyCount());
            return DataResponseFactory.success(fileDifficultOutputVO);
        }
        entity.setDifficulty(fileMarkDifficultDTO.getDifficulty());

        pcDatasetFileMapper.update(entity,
                new LambdaQueryWrapper<PcDatasetFile>()
                        .eq(PcDatasetFile::getDatasetId, entity.getDatasetId())
                        .eq(PcDatasetFile::getId, entity.getId())
        );
        //数据集难例数量增加或减少
        Long difficultCount = fileMarkDifficultDTO.getDifficulty() ? pcDataset.getDifficultyCount() + 1L :
                pcDataset.getDifficultyCount() - 1L;
        if (pcDataset.getFileCount() < difficultCount) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset  with id {}.The number of difficult cases is more than the number of documents ", pcDataset.getId());
            throw new BusinessException("难例数量异常");
        }
        pcDataset.setDifficultyCount(difficultCount);
        pcDatasetMapper.updateById(pcDataset);
        getFileDifficultOutputVO(entity, fileDifficultOutputVO, difficultCount);
        return DataResponseFactory.success(fileDifficultOutputVO);
    }

    /**
     * 返回难例文件数据
     * @param entity
     * @param fileDifficultOutputVO
     * @param difficultyCount
     */
    private void getFileDifficultOutputVO(PcDatasetFile entity, FileDifficultOutputVO fileDifficultOutputVO, Long difficultyCount) {
        BeanUtils.copyProperties(entity, fileDifficultOutputVO);
        fileDifficultOutputVO.setDifficultCount(difficultyCount);
        fileDifficultOutputVO.setMarkStatusName(MarkStatusEnum.getMarkStatusName(fileDifficultOutputVO.getMarkStatus()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataResponseBody publishDifficultCase(FileDifficultCasePublishDTO fileDifficultCasePublishDTO) {
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("当前用户信息已失效");
        }
        List<PcDatasetFile> entityList = pcDatasetFileMapper.selectList(
                new LambdaQueryWrapper<PcDatasetFile>()
                        .eq(PcDatasetFile::getDatasetId, fileDifficultCasePublishDTO.getDatasetId())
                        .eq(PcDatasetFile::getDifficulty, Boolean.TRUE));
        if (CollectionUtil.isEmpty(entityList)) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset with id {}.Failed to find dataset file.", fileDifficultCasePublishDTO.getDatasetId());
            throw new BusinessException("未获取到难例文件");
        }
        List<PcDataset> datasetsList = pcDatasetMapper.selectList(
                new LambdaQueryWrapper<PcDataset>()
                        .eq(PcDataset::getName, fileDifficultCasePublishDTO.getName()));
        if (!CollectionUtils.isEmpty(datasetsList)) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset with id {} that duplicate name ", fileDifficultCasePublishDTO.getDatasetId());
            throw new BusinessException(ErrorEnum.DUPLICATE_DATASET_NAME);
        }
        PcDataset pcDataset = pcDatasetMapper.selectById(fileDifficultCasePublishDTO.getDatasetId());

        if (!pcDataset.getLabelGroupId().equals(fileDifficultCasePublishDTO.getLabelGroupId())) {
            throw new BusinessException("标签组不能变更");
        }
        //非标注中状态不能进行难例发布
        if (!PcDatasetMachineStatusEnum.LABELING.getCode().equals(pcDataset.getStatus())) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset with id {}. Difficult cases cannot be published in the non-marking state where  status ={}",
                    fileDifficultCasePublishDTO.getDatasetId(), PcDatasetMachineStatusEnum.getDesc(pcDataset.getStatus()));
            throw new BusinessException("非标注中状态不能进行难例发布");
        }
        PcDataset casePcDataset = getDifficultCaseDataset(fileDifficultCasePublishDTO, entityList, user);

        pcDatasetMapper.insert(casePcDataset);
        // 复制文件
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                difficultCaseAsyncCopy.copyDifficultCase(entityList, casePcDataset, pcDataset.getId());
            }
        });

        return new DataResponseBody("难例发布成功");
    }


    /**
     * 组装拼接难例数据集参数
     * @param casePublishDTO
     * @param entityList
     */
    private PcDataset getDifficultCaseDataset(FileDifficultCasePublishDTO casePublishDTO, List<PcDatasetFile> entityList, UserContext user) {
        PcDataset pcDataset = new PcDataset()
                .setLabelGroupId(casePublishDTO.getLabelGroupId())
                .setName(casePublishDTO.getName())
                .setRemark(casePublishDTO.getRemark())
                .setDifficultyCount((long) entityList.size())
                .setFileCount((long) entityList.size())
                .setStatus(PcDatasetMachineStatusEnum.DIFFICULT_CASE_PUBLISHING.getCode());
        pcDataset.setCreateUserId(user.getId());
        pcDataset.setUpdateUserId(user.getId());
        return pcDataset;
    }

    @Override
    public DataResponseBody delete(Set<Long> ids) {
        return null;
    }

    @Override
    public Map<String, Object> info(Long id, Long datasetId) {
        Map<String, Object> result = new HashMap<>();
        PcDatasetFile entity = pcDatasetFileMapper.selectOne(
                new LambdaQueryWrapper<PcDatasetFile>()
                        .eq(PcDatasetFile::getDatasetId, datasetId)
                        .eq(PcDatasetFile::getId, id)
        );
        if (entity == null || StringUtils.isEmpty(entity.getMarkFileUrl())) {
            logger.info("未找到该文件的标注信息");
            return null;
        }

        String context = getFileContext(entity.getMarkFileUrl());
        if (StringUtils.isBlank(context)) {
            return result;
        }
        String[] markArr = context.split("\\n");
        List<String> markInfoList = new ArrayList<>();

        try {
            Map<String, Integer> countMap = new HashMap<>();
            List<MarkObjMsgDTO> markObjMsgDTOList = new ArrayList<>();
            for (String markInfo : markArr) {
                String[] markInfoArr = markInfo.split(" ");
                String label = markInfoArr[0].toString();
                countMap.merge(label, 1, Integer::sum);
                markInfoList.add(markInfo);
            }
            countMap.forEach((key, value) -> {
                MarkObjMsgDTO dto = new MarkObjMsgDTO();
                dto.setLabel(key);
                dto.setCount(value);
                markObjMsgDTOList.add(dto);
            });
            if (markObjMsgDTOList.size() > 0) {
                result.put("objMsgList", markObjMsgDTOList);
            }
            if (markInfoList.size() > 0) {
                result.put("markInfoList", markInfoList);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new BusinessException("解析出错，标注结果文件不符合格式要求");
        }

        return result;
    }

    @Override
    public Map<String, Object> valid(Long id) {
        UserContext user = userContextService.getCurUser();
        if (user == null) {
            throw new BusinessException("当前用户信息已失效");
        }

        Map<String, Object> result = new HashMap<>();
        // 查询数据集id是否存在，并验证状态，未上传才可进行上传，否则抛出异常
        LambdaQueryWrapper<PcDataset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PcDataset::getId, id);
        if (!BaseService.isAdmin(user)) {
            wrapper.eq(PcDataset::getCreateUserId, user.getId());
        }
        PcDataset datasets = pcDatasetMapper.selectOne(wrapper);
        if (Objects.isNull(datasets)) {
            throw new BusinessException("数据集不存在或无权限");
        }

        if (!PcDatasetMachineStatusEnum.NOT_SAMPLED.getCode().equals(datasets.getStatus()) && !PcDatasetMachineStatusEnum.IMPORTING.getCode().equals(datasets.getStatus())) {
            throw new BusinessException("当前状态下不能上传文件");
        }
        StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{id}, PcDatasetEventMachineConstant.PC_DATASET_MACHINE,
                PcDatasetEventMachineConstant.IMPORTING_PC_DATASET_EVENT));

        // 返回文件存储服务器相关变量
        result.put("accessKey", accessKey);
        result.put("secretKey", secretKey);
        result.put("bucketName", bucketName);
        result.put("url", url);
        result.put("uploadFolder", Constant.POINT_CLOUD_FOLDER + Constant.DATASET_FILE_FOLDER);
        result.put("uploadPCDFolder", Constant.POINT_CLOUD_FOLDER + Constant.FOLDER_PCD);
        result.put("pcdFileHead", Constant.PCD_FILE_HEAD);
        return result;
    }

    @Override
    public void uploadSave(List<FileUploadSaveInputVO> list, Map<String, String> labelFileMap) {
        if (list.size() == 0) {
            throw new BusinessException("错误，对象为空");
        }

        Long datasetId = list.stream().map(FileUploadSaveInputVO::getDatasetId).findFirst().get();
        LambdaQueryWrapper<PcDataset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PcDataset::getId, datasetId);
        UserContext user = userContextService.getCurUser();
        if (!BaseService.isAdmin(user)) {
            wrapper.eq(PcDataset::getCreateUserId, user.getId());
        }
        PcDataset datasets = pcDatasetMapper.selectOne(wrapper);
        if (Objects.isNull(datasets)) {
            throw new BusinessException("数据集不存在或无权限");
        }

        List<PcDatasetFile> pcdFiles = new ArrayList<>();

        for (FileUploadSaveInputVO vo : list) {
            PcDatasetFile entity = new PcDatasetFile();
            entity.setName(vo.getName().replace(Constant.FILE_SUFFIX_BIN, "").replace(Constant.FILE_SUFFIX_PCD, ""));
            entity.setMarkStatus(MarkStatusEnum.UN_MARK.getCode());
            entity.setUrl(vo.getKey());
            entity.setFileType(vo.getFileType());
            entity.setDifficulty(false);
            entity.setDatasetId(vo.getDatasetId());
            entity.setCreateUserId(userContextService.getCurUserId());
            String markFileUrl = labelFileMap.get(vo.getName().substring(0, vo.getName().lastIndexOf(".")));
            if (markFileUrl != null) {
                entity.setMarkStatus(MarkStatusEnum.AUTO_MARKED.getCode());
                entity.setMarkFileUrl(markFileUrl);
                entity.setMarkFileName(markFileUrl.substring(markFileUrl.lastIndexOf("/") + 1));
            }
            pcdFiles.add(entity);
        }

        batchInsert(pcdFiles);

        //数据集文件数量记录
        datasets.setFileCount((long) pcdFiles.size());
        datasets.setUrl(pathUtil.getDatasetUrl(datasetId));
        pcDatasetMapper.updateById(datasets);
        //更新数据集表状态为未标注（已上传）
        if (labelFileMap.size() == 0) {
            StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{datasetId}, PcDatasetEventMachineConstant.PC_DATASET_MACHINE,
                    PcDatasetEventMachineConstant.UNLABELLED_PC_DATASET_EVENT));
        } else {
            StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{datasetId}, PcDatasetEventMachineConstant.PC_DATASET_MACHINE,
                    PcDatasetEventMachineConstant.AUTO_LABEL_COMPLETE_PC_DATASET_EVENT));
        }
    }

    private void batchInsert(List<PcDatasetFile> pcdFiles) {
        SqlSession session = sqlSessionTemplate.getSqlSessionFactory().openSession(ExecutorType.BATCH);
        try {
            for (int i = 0; i < pcdFiles.size(); i++) {
                pcDatasetFileMapper.insert(pcdFiles.get(i));
                if (i % Constant.INSERT_BATCH_NUM == 0 || i == pcdFiles.size() - 1) {
                    session.commit();
                    session.clearCache();
                }
            }
        } catch (Exception e) {
            session.rollback();
        } finally {
            session.close();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSave(MarkSaveInputVO markSaveInputVO) {
        PcDatasetFile entity = pcDatasetFileMapper.selectOne(new LambdaQueryWrapper<PcDatasetFile>()
                .eq(PcDatasetFile::getDatasetId, markSaveInputVO.getDatasetId())
                .eq(PcDatasetFile::getId, markSaveInputVO.getFileId()));
        if (ObjectUtils.isEmpty(entity)) {
            throw new BusinessException("根据Id无法找到该文件信息");
        }

        if (markSaveInputVO.getMarkInfoNew() == null || markSaveInputVO.getMarkInfoNew().size() == 0) {
            return;
        }

        StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{entity.getDatasetId()}, PcDatasetEventMachineConstant.PC_DATASET_MACHINE,
                PcDatasetEventMachineConstant.LABELING_PC_DATASET_EVENT));

        entity.setMarkStatus(MarkStatusEnum.MANUAL_MARKING.getCode());
        pcDatasetFileMapper.update(entity,
                new LambdaQueryWrapper<PcDatasetFile>()
                        .eq(PcDatasetFile::getDatasetId, entity.getDatasetId())
                        .eq(PcDatasetFile::getId, entity.getId())
        );
        try {
            // 如果标注文件不存在，则新建
            if (StringUtils.isEmpty(entity.getMarkFileUrl())) {
                String fileName = entity.getName() + Constant.FILE_SUFFIX_TXT;
                String path = pathUtil.getDatasetLabel2FileUrl(entity.getDatasetId(), fileName);
                entity.setMarkFileName(fileName);
                entity.setMarkFileUrl(path);
            } else {// 如果标注文件存在，则先删除
                minioUtil.del(bucketName, entity.getMarkFileUrl());
            }

            minioUtil.writeString(bucketName, entity.getMarkFileUrl(), getContext(markSaveInputVO.getMarkInfoNew()));

        } catch (Exception e) {
            e.printStackTrace();
            throw new BusinessException("保存标注结果出错");
        }
    }


    // 入参组装成文件内容
    private String getContext(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String str : list) {
            sb.append(str).append("\n");
        }
        return sb.toString();
    }

    @Override
    public List<PcDatasetFile> selectList(LambdaQueryWrapper<PcDatasetFile> lambdaQueryWrapper) {
        return pcDatasetFileMapper.selectList(lambdaQueryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBatchByEntityAndDatasetId(List<PcDatasetFile> pointCloudFilesEntities, Long datasetId) {
        pcDatasetFileMapper.updateBatchByEntityAndDatasetId(pointCloudFilesEntities, datasetId);
    }

    @Override
    public void createNewTable(String tableName) {
        String oldTableName = tableName.substring(0, tableName.lastIndexOf("_"));
        pcDatasetFileMapper.createNewTable(tableName, oldTableName);
    }

    @Override
    public boolean checkTableExist(String tableName) {
        try {
            pcDatasetFileMapper.checkTableExist(tableName);
            return true;
        } catch (Exception e) {
            LogUtil.info(LogEnum.POINT_CLOUD, "表不存在,异常信息：{}", e.getMessage());
            return false;
        }
    }


}
