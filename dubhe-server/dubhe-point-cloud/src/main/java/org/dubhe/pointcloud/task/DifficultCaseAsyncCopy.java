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
package org.dubhe.pointcloud.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.commons.lang3.StringUtils;
import org.dubhe.biz.base.constant.SymbolConstant;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.file.utils.MinioUtil;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.statemachine.dto.StateChangeDTO;
import org.dubhe.pointcloud.common.Constant;
import org.dubhe.pointcloud.dao.PcDatasetFileMapper;
import org.dubhe.pointcloud.dao.PcDatasetMapper;
import org.dubhe.pointcloud.domain.entity.PcDataset;
import org.dubhe.pointcloud.domain.entity.PcDatasetFile;
import org.dubhe.pointcloud.machine.constant.PcDatasetEventMachineConstant;
import org.dubhe.pointcloud.util.PathUtil;
import org.dubhe.pointcloud.util.StateMachineUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @description 异步难例文件复制
 * @date 2022-05-07
 **/
@Component
public class DifficultCaseAsyncCopy {
    /**
     * MinIO工具类
     */
    @Resource
    private MinioUtil minioUtil;

    @Resource
    PathUtil pathUtil;

    @Value("${minio.bucketName}")
    private String bucketName;

    @Resource
    private PcDatasetFileMapper pcDatasetFileMapper;

    @Autowired
    private DataSourceTransactionManager dataSourceTransactionManager;


    @Autowired
    private TransactionDefinition transactionDefinition;

    @Resource
    private PcDatasetMapper pcDatasetMapper;


    @Resource(name = "pointCloudExecutor")
    private Executor pointCloudExecutor;

    /**
     * 复制点云难例相关文件
     * @param pcDatasetFiles 点云难例文件
     * @param casePcDataset  难例数据集id
     * @param datasetId 原数据集id
     */
    @Async("pointCloudExecutor")
    public void copyDifficultCase(List<PcDatasetFile> pcDatasetFiles, PcDataset casePcDataset, Long datasetId) {

        insertDifficultCaseFile(pcDatasetFiles, casePcDataset);

        List<PcDatasetFile> entityList = pcDatasetFileMapper.selectList(new LambdaQueryWrapper<PcDatasetFile>()
                .eq(PcDatasetFile::getDatasetId, datasetId)
                .eq(PcDatasetFile::getDifficulty, Boolean.TRUE));
        //若难例已自动标注过的txt文件
        List<String> txtFileUrls = entityList.stream().filter(pcDatasetFile -> StringUtils.isNotBlank(pcDatasetFile.getMarkFileUrl())).map(PcDatasetFile::getMarkFileUrl).collect(Collectors.toList());

        //获取难例的pcd文件
        List<String> pcdFileUrls = entityList.stream().map(PcDatasetFile::getUrl).collect(Collectors.toList());

        //获取难例的bin文件
        String binFileUrl = pathUtil.getDatasetFileFolderUrl(datasetId, Constant.VELODYNE);

        String pcdFileUrl = StringUtils.substringBeforeLast(pcdFileUrls.stream().findFirst().get(), SymbolConstant.SLASH);

        List<String> binFileUrls = entityList.stream().map(pcDatasetFile -> pcDatasetFile.getUrl().replace(pcdFileUrl, binFileUrl)
                .replace(Constant.FILE_SUFFIX_PCD, Constant.FILE_SUFFIX_BIN)).collect(Collectors.toList());

        //将txt文件，pcd文件，bin文件存入映射，并生成相对应的难例数据集目录
        Map<String, List<String>> fileFolderMap = new HashMap<>();
        fileFolderMap.put(pathUtil.getPcdFileUrl(casePcDataset.getId(), SymbolConstant.BLANK), pcdFileUrls);
        fileFolderMap.put(pathUtil.getDatasetFileFolderUrl(casePcDataset.getId(), Constant.VELODYNE), binFileUrls);
        if (!CollectionUtils.isEmpty(txtFileUrls)) {
            fileFolderMap.put(pathUtil.getLabel2Url(casePcDataset.getId()), txtFileUrls);
        }

        minioCopyFile(casePcDataset, datasetId, binFileUrl, fileFolderMap);
        StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{casePcDataset.getId()}, PcDatasetEventMachineConstant.PC_DATASET_MACHINE,
                PcDatasetEventMachineConstant.LABELING_PC_DATASET_EVENT));


    }

    /**
     * mino难例复制实现
     * @param casePcDataset
     * @param datasetId
     * @param binFileUrl
     * @param fileFolderMap
     */
    private void minioCopyFile(PcDataset casePcDataset, Long datasetId, String binFileUrl, Map<String, List<String>> fileFolderMap) {
        try {
            //查询数据集文件 获取数据集目录下，当前数据集id所有对象文件
            List<String> objectList = minioUtil.getObjects(bucketName, pathUtil.getDatasetUrl(datasetId));
            //剔除对象文件，获取文件夹集合
            Set<String> objectSet = objectList.stream().map(sourceObjectName -> StringUtils.substringBeforeLast(sourceObjectName, SymbolConstant.SLASH)).collect(Collectors.toSet());

            //剔除pcd文件路径和bin文件路径；因这两个路径下的文件过多，需要进行单独处理，仅对难例文件进行复制
            Map<String, String> objectMap = objectSet.stream().filter(objectStr -> (!objectStr.contains(pathUtil.getLabel2Url(datasetId)) && !objectStr.contains(binFileUrl)))
                    .collect(Collectors.toMap(Function.identity(), objectStr -> objectStr.replace(pathUtil.getDatasetUrl(datasetId), pathUtil.getDatasetUrl(casePcDataset.getId()))));

            //复制
            for (Map.Entry<String, String> entry : objectMap.entrySet()) {
                List<String> objects = minioUtil.getObjects(bucketName, entry.getKey());
                copyMultiThread(objects, entry.getValue());
            }

            //难例文件复制
            for (Map.Entry<String, List<String>> entry : fileFolderMap.entrySet()) {
                copyMultiThread(entry.getValue(), entry.getKey());
            }


        } catch (Exception e) {
            casePcDataset.putStatusDetail("难例发布异常", "minio连接异常");
            StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{casePcDataset.getId(), casePcDataset.getStatusDetail()}, PcDatasetEventMachineConstant.PC_DATASET_MACHINE,
                    PcDatasetEventMachineConstant.DIFFICULT_CASE_FAILED_TO_PUBLISH_EVENT));
            LogUtil.error(LogEnum.POINT_CLOUD, "Minio connection exception,error message:{}", e.getMessage());
            throw new BusinessException("Minio连接异常");
        }
    }

    /**
     * 生成难例数据集文件
     * @param pcDatasetFiles
     * @param casePcDataset
     */
    private void insertDifficultCaseFile(List<PcDatasetFile> pcDatasetFiles, PcDataset casePcDataset) {
        pcDatasetFiles.forEach(file -> {
            file.setDatasetId(casePcDataset.getId());
            file.setUrl(pathUtil.getPcdFileUrl(casePcDataset.getId(), file.getName().concat(Constant.FILE_SUFFIX_PCD)));
            if (StringUtils.isNotBlank(file.getMarkFileUrl())) {
                file.setMarkFileUrl(pathUtil.getDatasetLabel2FileUrl(casePcDataset.getId(), file.getMarkFileName()));
            }
            file.setId(null);
        });
        TransactionStatus transactionStatus = dataSourceTransactionManager.getTransaction(transactionDefinition);
        try {
            //更新数据集路径
            pcDatasetMapper.update(null, new LambdaUpdateWrapper<PcDataset>()
                    .eq(PcDataset::getId, casePcDataset.getId())
                    .set(PcDataset::getUrl, pathUtil.getDatasetUrl(casePcDataset.getId())));
            //因数据量大可能导致拼接的SQL过长导致执行失败
            Integer insertCount = 0;
            while (true) {
                List<PcDatasetFile> updatePcDatasetFiles = pcDatasetFiles.stream()
                        .skip(insertCount * Constant.INSERT_BATCH_NUM).limit(Constant.INSERT_BATCH_NUM).collect(Collectors.toList());
                if (updatePcDatasetFiles.size() == 0) {
                    break;
                }
                insertCount++;
                pcDatasetFileMapper.batchInsert(updatePcDatasetFiles);
            }
            dataSourceTransactionManager.commit(transactionStatus);
        } catch (Exception e) {
            dataSourceTransactionManager.rollback(transactionStatus);
            LogUtil.error(LogEnum.POINT_CLOUD, "Failed to copy difficult case files,error message:{}", e.getMessage());
            throw new BusinessException("复制点云难例文件失败");
        }
    }

    /**
     * 多线程minio难例文件复制
     * @param sourFiles
     * @param targetFile
     */
    public void copyMultiThread(List<String> sourFiles, String targetFile) {
        try {
            int copyThreadNum = sourFiles.size() / Constant.MINIO_COPY_NUM + 1;
            for (int i = 0; i < copyThreadNum; i++) {
                List<String> sourFileList = sourFiles.stream().skip(i * Constant.MINIO_COPY_NUM).limit(Constant.MINIO_COPY_NUM).collect(Collectors.toList());
                if (CollectionUtils.isEmpty(sourFileList)) {
                    break;
                }
                pointCloudExecutor.execute(() -> minioUtil.copyObject(bucketName, sourFileList, targetFile));
            }
        } catch (Exception e) {
            LogUtil.error(LogEnum.POINT_CLOUD, "Minio connection exception,error message:{}", e.getMessage());
            throw new BusinessException("Minio连接异常");
        }


    }


}
