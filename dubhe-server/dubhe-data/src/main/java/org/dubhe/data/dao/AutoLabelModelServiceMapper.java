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
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.dubhe.data.domain.dto.AutoLabelModelServiceQueryDTO;
import org.dubhe.data.domain.entity.AutoLabelModelService;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author ${author}
 * @since 2022-05-25
 */
public interface AutoLabelModelServiceMapper extends BaseMapper<AutoLabelModelService> {

    int deleteByIds(@Param("ids") List<Long> ids);

    List<AutoLabelModelService> selectByIds(@Param("ids") List<Long> ids);

    int updataStatusById(@Param("id") Long id,@Param("status") Integer status);

    @Update("update auto_label_model_service set status = #{status} where id = #{modelServiceId}")
    void updateStatus(@Param("modelServiceId") Long modelServiceId, @Param("status") Integer status);
}
