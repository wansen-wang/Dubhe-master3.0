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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.file.utils.MinioUtil;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.cloud.authconfig.utils.JwtUtils;
import org.dubhe.data.machine.constant.DataStateCodeConstant;
import org.dubhe.dcm.constant.DcmConstant;
import org.dubhe.dcm.dao.DataMedicineFileMapper;
import org.dubhe.dcm.domain.dto.DataMedicineFileCreateDTO;
import org.dubhe.dcm.domain.entity.DataMedicine;
import org.dubhe.dcm.domain.entity.DataMedicineFile;
import org.dubhe.dcm.machine.enums.DcmFileStateEnum;
import org.dubhe.dcm.service.DataMedicineFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * @description ????????????????????????????????????
 * @date 2020-11-12
 */
@Service
public class DataMedicineFileServiceImpl extends ServiceImpl<DataMedicineFileMapper, DataMedicineFile> implements DataMedicineFileService {

    @Autowired
    private MinioUtil minioUtil;

    @Value("${minio.bucketName}")
    private String bucketName;

    /**
     * ???????????????????????????????????????
     *
     * @param dataMedicineFileCreateDTO ????????????
     * @param dataMedicine    ???????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(List<DataMedicineFileCreateDTO> dataMedicineFileCreateDTO, DataMedicine dataMedicine) {
        List<DataMedicineFile> dataMedicineFiles = new ArrayList<>();
        for (DataMedicineFileCreateDTO dataMedicineFileCreate : dataMedicineFileCreateDTO) {
            DataMedicineFile dataMedicineFile = new DataMedicineFile();
            String urlname = dataMedicineFileCreate.getUrl().substring(dataMedicineFileCreate.getUrl().lastIndexOf(DcmConstant.DCM_FILE_SEPARATOR) + MagicNumConstant.ONE);
            String name = urlname.substring(MagicNumConstant.ZERO, urlname.indexOf("."));
            dataMedicineFile.setName(name);
            dataMedicineFile.setStatus(DataStateCodeConstant.NOT_ANNOTATION_STATE);
            dataMedicineFile.setMedicineId(dataMedicine.getId());
            dataMedicineFile.setUrl(dataMedicineFileCreate.getUrl());
            dataMedicineFile.setOriginUserId(dataMedicine.getCreateUserId());
            dataMedicineFile.setCreateUserId(dataMedicine.getCreateUserId());
            dataMedicineFile.setUpdateUserId(dataMedicine.getCreateUserId());
            dataMedicineFile.setSopInstanceUid(dataMedicineFileCreate.getSOPInstanceUID());
            dataMedicineFiles.add(dataMedicineFile);
        }
        baseMapper.saveBatch(dataMedicineFiles);
    }

    /**
     * ????????????????????????
     *
     * @param wrapper ????????????
     * @return List<DataMedicineFile> ??????????????????
     */
    @Override
    public List<DataMedicineFile> listFile(QueryWrapper<DataMedicineFile> wrapper) {
        return list(wrapper);
    }


    /**
     * ?????????????????????????????????
     *
     * @param queryWrapper
     * @return Integer ???????????????????????????
     */
    @Override
    public Integer getCountByMedicineId(QueryWrapper<DataMedicineFile> queryWrapper) {
        return baseMapper.selectCount(queryWrapper);
    }

    /**
     * ?????????????????????????????????
     *
     * @param dataMedicineFiles ??????????????????
     * @param medicineId        ???????????????ID
     * @return List<DataMedicineFile> ??????????????????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DataMedicineFile> insertInstanceAndSort(List<DataMedicineFile> dataMedicineFiles, Long medicineId) {
        AtomicBoolean positionFlag = new AtomicBoolean(false);
        dataMedicineFiles.forEach(dataMedicineFile -> {
            Attributes attributes = null;
            DicomInputStream dicomInputStream = null;
            InputStream inputStream = null;
            String targetPath = StringUtils.substringAfter(dataMedicineFile.getUrl(), "/");
            try {
                inputStream = minioUtil.getObjectInputStream(bucketName, targetPath);
                dicomInputStream = new DicomInputStream(inputStream);
                attributes = dicomInputStream.readDataset(-1, -1);
                int instanceNumber = Integer.parseInt(attributes.getString(Tag.InstanceNumber));
                String sopInstanceUid = attributes.getString(Tag.SOPInstanceUID);
                if (attributes.getString(Tag.ImagePositionPatient) != null) {
                    String imagePositionPatientString = attributes.getString(Tag.ImagePositionPatient, 2);
                    double imagePositionPatient = Double.parseDouble(imagePositionPatientString);
                    dataMedicineFile.setImagePositionPatient(imagePositionPatient);
                    positionFlag.set(true);
                }
                dataMedicineFile.setInstanceNumber(instanceNumber);
                dataMedicineFile.setSopInstanceUid(sopInstanceUid);
                baseMapper.updateById(dataMedicineFile);
            } catch (Exception e) {
                LogUtil.error(LogEnum.BIZ_DATASET, "get dicomInputStream failed, {}", e);
            } finally {
                try {
                    if(!Objects.isNull(dicomInputStream)){
                        dicomInputStream.close();
                    }
                    if(!Objects.isNull(inputStream)){
                        inputStream.close();
                    }
                } catch (IOException e) {
                    LogUtil.error(LogEnum.BIZ_DATASET, "close inputStream failed, {}", e);
                }
            }
        });
        QueryWrapper<DataMedicineFile> wrapper = new QueryWrapper<>();
        if (positionFlag.get()) {
            wrapper.lambda().eq(DataMedicineFile::getMedicineId, medicineId)
                    .orderByAsc(DataMedicineFile::getImagePositionPatient);
        } else {
            wrapper.lambda().eq(DataMedicineFile::getMedicineId, medicineId)
                    .orderByAsc(DataMedicineFile::getInstanceNumber);
        }
        return listFile(wrapper);
    }

    /**
     * ???????????????ID
     *
     * @param medicineId ???????????????id
     */
    @Override
    public void updateUserIdByMedicineId(Long medicineId) {
        baseMapper.updateUserIdByMedicineId(medicineId,JwtUtils.getCurUserId());
    }

    /**
     * ?????????????????????Id????????????
     *
     * @param id         ???????????????Id
     * @param deleteFlag ????????????
     */
    @Override
    public void updateStatusById(Long id, Boolean deleteFlag) {
        baseMapper.updateStatusById(id,deleteFlag);
    }

    /**
     * ?????????????????????Id????????????
     *
     * @param datasetId         ???????????????Id
     */
    @Override
    public void deleteByDatasetId(Long datasetId) {
        baseMapper.deleteByDatasetId(datasetId);
    }

    @Override
    public List<Integer> getFileStatusListByDataset(Long datasetId) {
        if (datasetId == null) {
            LogUtil.error(LogEnum.BIZ_DATASET, "datasetId isEmpty");
            return null;
        }
        return baseMapper.getFileStatusListByDataset(datasetId);
    }

    @Override
    public void deleteAnnotation(Long datasetId) {
        baseMapper.cleanAnnotation(datasetId);
    }

}
