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
package org.dubhe.pointcloud.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.pointcloud.domain.dto.FileDifficultCasePublishDTO;
import org.dubhe.pointcloud.domain.dto.FileDoneInputDTO;
import org.dubhe.pointcloud.domain.dto.FileMarkDifficultDTO;
import org.dubhe.pointcloud.domain.entity.PcDatasetFile;
import org.dubhe.pointcloud.domain.vo.FileQueryInputVO;
import org.dubhe.pointcloud.domain.vo.FileUploadSaveInputVO;
import org.dubhe.pointcloud.domain.vo.MarkSaveInputVO;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @description 文件处理服务
 * @date 2022-03-31
 **/
public interface FileService {

    /**
     * 单个文件上传
     * @param file
     * @param key
     * @return
     */
    void upload(File file, String key);

    /**
     * 获取文件内容
     * @param key 文件名
     * @return
     */
    String getFileContext(String key);

    /**
     * 获取文件列表
     * @param fileQueryInputVO
     * @return 文件列表
     */
    Map<String, Object> queryFileList(FileQueryInputVO fileQueryInputVO);

    /**
     * 标注完成
     * @param fileDoneInputDTO 标注完成接口入参
     */
    DataResponseBody done(FileDoneInputDTO fileDoneInputDTO);

    /**
     * 点云难例标记
     * @param fileMarkDifficultDTO
     * @return DataResponseBody
     */
    DataResponseBody mark(FileMarkDifficultDTO fileMarkDifficultDTO);

    /**
     * 点云文件难例发布
     * @param fileDifficultCasePublishDTO
     * @return
     */
    DataResponseBody publishDifficultCase(FileDifficultCasePublishDTO fileDifficultCasePublishDTO);

    /**
     * 点云文件删除
     * @param ids
     * @return
     */
    DataResponseBody delete(Set<Long> ids);


    /**
     * 点云文件解析
     * @param id 文件id
     * @return
     */
    Map<String, Object> info(Long id, Long datasetId);

    /**
     * 点云文件上传验证
     * @param id 数据集id
     * @return 文件存储服务器连接信息
     */
    Map<String, Object> valid(Long id);

    /**
     * 点云文件入库保存
     * @param list
     */
    void uploadSave(List<FileUploadSaveInputVO> list, Map<String, String> labelFileMap);

    /**
     * 标注信息保存
     * @param markSaveInputVO
     */
    void markSave(MarkSaveInputVO markSaveInputVO);

    /**
     * 根据数据集id查询文件集合
     * @param lambdaQueryWrapper
     * @return
     */
    List<PcDatasetFile> selectList(LambdaQueryWrapper<PcDatasetFile> lambdaQueryWrapper);

    /**
     *
     */
    void updateBatchByEntityAndDatasetId(List<PcDatasetFile> pointCloudFilesEntities, Long datasetId);

    /**
     * 创建表结构
     * @param tableName
     */
    void createNewTable(String tableName);

    /**
     * 确认表是否存在
     * @param tableName
     * @return
     */
    boolean checkTableExist(String tableName);
}
