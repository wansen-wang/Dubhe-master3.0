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
package org.dubhe.train.domain.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bouncycastle.jcajce.provider.symmetric.GOST28147;
import org.dubhe.train.domain.entity.PtAtlasTrainParam;

import java.io.Serializable;
import java.util.List;

/**
 * @description 默认炼知重组任务数据
 * @date 2022-06-23
 */
@Data
@Accessors(chain = true)
public class DefaultAtlasTrainParamsVo implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty("训练作业名不能为空")
	private String trainName;

	@ApiModelProperty("算法id")
	private Long algorithmId;

	@ApiModelProperty("算法名称")
	private String algorithmName;

	@ApiModelProperty("镜像名称")
	private String imageName;

	@ApiModelProperty("镜像tag")
	private String imageTag;

	@ApiModelProperty("算法运行命令")
	private String runCommand;

	@ApiModelProperty("模型类型")
	private String modelResource;

	@ApiModelProperty("学生模型结构名")
	private String studentModelStruct;

	@ApiModelProperty("教师模型/数据集信息集合")
	private List<PtAtlasTrainParam> baseAtlasParams;

	@ApiModelProperty("重组任务类型(1：单任务，2：多任务)")
	private Integer jobType;


}
