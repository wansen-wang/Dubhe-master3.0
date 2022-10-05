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
package org.dubhe.pointcloud.machine.state.specific;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.file.utils.MinioUtil;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.pointcloud.common.Constant;
import org.dubhe.pointcloud.domain.entity.PcDataset;
import org.dubhe.pointcloud.domain.entity.PcDatasetFile;
import org.dubhe.pointcloud.enums.MarkStatusEnum;
import org.dubhe.pointcloud.enums.PcDatasetMachineStatusEnum;
import org.dubhe.pointcloud.machine.state.AbstractPcDatasetState;
import org.dubhe.pointcloud.service.FileService;
import org.dubhe.pointcloud.service.PcDatasetService;
import org.dubhe.pointcloud.util.PathUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;


/**
 * @description 自动标注中状态类
 * @date 2022-04-02
 */
@Component
public class PcDatasetAutoLabelingState extends AbstractPcDatasetState {

    @Autowired
    private PcDatasetService datasetsService;

    @Autowired
    private FileService fileService;

    @Resource
    private MinioUtil minioUtil;

    @Value("${minio.bucketName}")
    private String bucket;

    @Resource
    private PathUtil pathUtil;


    @Override
    public void autoLabelCompletePcDatasetEvent(Long datasetId) {
        datasetsService.updatePcDataset(new LambdaUpdateWrapper<PcDataset>()
                .eq(PcDataset::getId, datasetId)
                .set(PcDataset::getStatus, PcDatasetMachineStatusEnum.AUTO_LABEL_COMPLETE.getCode()));
        List<PcDatasetFile> pcDatasetFiles = fileService.selectList(
                new LambdaQueryWrapper<PcDatasetFile>()
                        .eq(PcDatasetFile::getDatasetId, datasetId));
        //有些标注未生成txt文件，需要从minio种获取已存在的.txt文件名称集合
        List<String> objectList = null;
        try {
            objectList = minioUtil.getObjects(bucket, pathUtil.getLabel2Url(datasetId));
        } catch (Exception e) {
            LogUtil.error(LogEnum.POINT_CLOUD, "Minio connection exception,error message:{}", e.getMessage());
            throw new BusinessException("Minio连接异常");
        }
        for (PcDatasetFile pcDatasetFile : pcDatasetFiles) {
            String markFileName = pcDatasetFile.getName().concat(Constant.FILE_SUFFIX_TXT);
            String markFileUrl = pathUtil.getDatasetLabel2FileUrl(datasetId, markFileName);
            if (objectList.contains(markFileUrl)) {
                pcDatasetFile.setMarkFileName(markFileName);
                pcDatasetFile.setMarkFileUrl(markFileUrl);
            }
            pcDatasetFile.setMarkStatus(MarkStatusEnum.AUTO_MARKED.getCode());
            pcDatasetFile.setMarkStatusName(MarkStatusEnum.AUTO_MARKED.getName());
        }
        //因大批量点云文件更新会进行SQL拼接超出指定长度，故每千条数据进行更新；1W数据大概10s
        Integer updateCount = 0;
        while (true) {
            List<PcDatasetFile> updatePcDatasetFiles = pcDatasetFiles.stream()
                    .skip(updateCount * MagicNumConstant.ONE_THOUSAND).limit(MagicNumConstant.ONE_THOUSAND).collect(Collectors.toList());
            if (updatePcDatasetFiles.size() == 0) {
                break;
            }
            updateCount++;
            fileService.updateBatchByEntityAndDatasetId(updatePcDatasetFiles, datasetId);
        }

    }

    @Override
    public void autoLabelStopPcDatasetEvent(Long datasetId) {
        datasetsService.updatePcDataset(new LambdaUpdateWrapper<PcDataset>()
                .eq(PcDataset::getId, datasetId)
                .set(PcDataset::getStatus, PcDatasetMachineStatusEnum.AUTO_LABEL_STOP.getCode()));
    }

    @Override
    public void autoLabelFailedPcDatasetEvent(Long datasetId, String statusDetail) {
        datasetsService.updatePcDataset(new LambdaUpdateWrapper<PcDataset>()
                .eq(PcDataset::getId, datasetId)
                .set(PcDataset::getStatus, PcDatasetMachineStatusEnum.AUTO_LABEL_FAILED.getCode())
                .set(PcDataset::getStatusDetail, statusDetail));
    }

    @Override
    public String currentStatus() {
        return PcDatasetMachineStatusEnum.AUTO_LABELING.getDesc();
    }
}
