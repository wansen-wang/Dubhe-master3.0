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
package org.dubhe.model.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;
import org.dubhe.biz.db.entity.BaseEntity;

import javax.validation.constraints.NotNull;

/**
 * @description 炼制模型结构管理
 * @date 2020-03-24
 */
@Data
@Accessors(chain = true)
@TableName("pt_model_structure")
public class PtModelStructure extends BaseEntity {

	/**
	 * 主键ID
	 */
	@TableId(value = "id", type = IdType.AUTO)
	@NotNull(groups = {Update.class})
	private Long id;

	/**
	 * 模型结构名称
	 */
	@TableField(value = "struct_name")
	private String structName;

	/**
	 * 模型重组任务类型（1：单任务，2多任务）
	 */
	@TableField(value = "job_type")
	private Integer jobType;
}
