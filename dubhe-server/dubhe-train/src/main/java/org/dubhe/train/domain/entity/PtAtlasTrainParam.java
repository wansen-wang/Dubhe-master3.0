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
package org.dubhe.train.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.train.utils.TrainUtil;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.Pattern;
import java.io.Serializable;

/**
 * @description 炼知重组训练参数
 * @date 2022-06-15
 */
@Data
@Accessors(chain = true)
@TableName(value = "pt_atlas_train_param", autoResultMap = true)
public class PtAtlasTrainParam implements Serializable {
	private static final long serialVersionUID = 1L;
	/**
	 * 主键id
	 */
	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	@TableField("train_job_id")
	private Long trainJobId;

	@TableField("dataset_id")
	private Long datasetId;

	@TableField("dataset_type")
	private String datasetType;

	@TableField("data_source_name")
	private String dataSourceName;

	@TableField("data_source_path")
	private String dataSourcePath;
	
	@TableField("dataset_version")
	private String datasetVersion;

	@TableField("teacher_model_struct")
	private String teacherModelStruct;
	
	@TableField("teacher_model_name")
	private String teacherModelName;
	
	@TableField("teacher_model_path")
	private String teacherModelPath;
}
