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
package org.dubhe.model.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dubhe.model.domain.entity.PtModelStructure;

import java.util.List;

/**
 * @description 模型结构管理
 * @date 2020-04-02
 */
public interface PtModelStructureMapper extends BaseMapper<PtModelStructure> {

	@Select("select struct_name from pt_model_structure where job_type=${jobType} and deleted=0 ")
	List<String> queryStructListByType(@Param("jobType") String jobType);
}
