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
package org.dubhe.data.dao;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dubhe.data.domain.entity.PcDataset;

/**
 * @description 点云数据集
 * @date 2022-04-01
 **/
public interface PcDatasetMapper extends BaseMapper<PcDataset> {
    /**
     * 根据标签组ID查询关联的点云数据集数量
     *
     * @param labelGroupId 标签组ID
     * @return int 数量
     */
    @Select("SELECT count(1) FROM pc_dataset where label_group_id = #{labelGroupId}")
    int getCountPCByLabelGroupId(@Param("labelGroupId") Long labelGroupId);
}
