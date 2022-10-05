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
package org.dubhe.pointcloud.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.dubhe.pointcloud.domain.entity.PcDatasetFile;
import org.dubhe.pointcloud.domain.vo.FileQueryInputVO;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * @description pc_dataset_file DAO
 * @date 2022-04-01
 **/
public interface PcDatasetFileMapper extends BaseMapper<PcDatasetFile> {
    void batchInsert(@Param("list") List<PcDatasetFile> list);

    List<PcDatasetFile> queryFileList(FileQueryInputVO fileQueryInputVO);

    int updateDeleteByBatchIds(@Param(Constants.COLLECTION) Collection<? extends Serializable> coll);

    /**
     * 批量修改文件状态
     * @param coll
     * @return
     */
    int updateDeleteByBatchDatasetIds(@Param(Constants.COLLECTION) Collection<? extends Serializable> coll);

    /**
     * 修改点云文件状态
     * @param datasetId
     * @param deleteFlag
     * @return
     */
    int updateStatusByDatasetId(@Param("datasetId") Long datasetId, @Param("deleteFlag") boolean deleteFlag);

    /**
     * 批量更新点云文件数据
     * @param pcDatasetFiles
     * @param datasetId
     */
    void updateBatchByEntityAndDatasetId(@Param("list") List<PcDatasetFile> pcDatasetFiles, @Param("datasetId") Long datasetId);


    /**
     * 根据数据集ID删除文件数据
     *
     * @param datasetId     数据集ID
     * @param limitNumber   删除数量
     * @return int 成功删除条数
     */
    @Delete("delete from pc_dataset_file where dataset_id = #{datasetId} limit #{limitNumber} ")
    int deleteByDatasetId(@Param("datasetId") Long datasetId, @Param("limitNumber") int limitNumber);

    /**
     * 创建分表
     * @param tableName 新表表名
     * @param oldTableName 模板表
     */
    void createNewTable(@Param("tableName") String tableName, @Param("oldTableName") String oldTableName);

    /**
     * 判断表是否存在
     * @param tableName 表名
     */
    int checkTableExist(@Param("tableName") String tableName);
}
