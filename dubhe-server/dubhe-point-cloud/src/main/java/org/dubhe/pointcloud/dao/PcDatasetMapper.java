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
import org.dubhe.pointcloud.domain.dto.DatasetDetailDTO;
import org.dubhe.pointcloud.domain.entity.PcDataset;

import java.io.Serializable;
import java.util.Collection;

/**
 * @description 点云数据集
 * @date 2022-04-01
 **/
public interface PcDatasetMapper extends BaseMapper<PcDataset> {
    /**
     * 批量修改数据集存在状态
     * @param ids 数据集id集合
     * @param deleteFlag 数据集存在状态
     * @return int
     */
    int updateStatusByBatchIds(@Param(Constants.COLLECTION) Collection<? extends Serializable> ids, @Param("deleteFlag") boolean deleteFlag);

    /**
     * 修改数据集状态
     * @param id 数据集id
     * @param deleteFlag 数据集存在状态
     * @return int
     */
    int updateStatusById(@Param("id") Long id, @Param("deleteFlag") boolean deleteFlag);

    /**
     * 删除点云数据
     * @param id 数据集id
     * @return int
     */
    @Delete("delete  from  pc_dataset where id = #{id}")
    int deleteInfoById(@Param("id") Long id);

    /**
     * 获取自动标注详情
     * @param id 数据集id
     * @param createUserId 用户id
     * @return DatasetDetailDTO
     */
    DatasetDetailDTO getDetails(@Param("id") Long id, @Param("createUserId") Long createUserId);
}
