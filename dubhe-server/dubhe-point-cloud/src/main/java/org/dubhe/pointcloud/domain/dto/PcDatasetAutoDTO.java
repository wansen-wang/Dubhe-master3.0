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
package org.dubhe.pointcloud.domain.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.dubhe.biz.base.constant.NumberConstant;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * @description 点云数据集自动标注
 * @date 2022-04-01
 **/
@Data
public class PcDatasetAutoDTO {
    @ApiModelProperty(value = "datasetId", required = true)
    @NotNull(message = "数据集id不能为空")
    private Long datasetId;

    @ApiModelProperty(value = "modelId", required = true)
    @NotNull(message = "模型id不能未空")
    private Long modelId;

    @ApiModelProperty(value = "modelBranchId")
    private Long modelBranchId;

    @ApiModelProperty(value = "imageName", required = true)
    @NotNull(message = "镜像名称不能为空")
    @Size(max = 200, message = "镜像名称长度超过200")
    private String imageName;

    @ApiModelProperty(value = "imageTag", required = true)
    @NotNull(message = "镜像标签不能为空")
    @Size(max = 200, message = "镜像标签长度超过200")
    private String imageTag;

    @ApiModelProperty(value = "command", required = true)
    @NotNull(message = "标注命令不能为空")
    private String command;

    @ApiModelProperty(value = "节点类型(0为CPU，1为GPU)", required = true)
    @Min(value = NumberConstant.NUMBER_0, message = "节点类型错误")
    @Max(value = NumberConstant.NUMBER_1, message = "节点类型错误")
    @NotNull(message = "节点类型不能为空")
    private Integer resourcesPoolType;

    @ApiModelProperty(value = "节点规格", required = true)
    @NotNull(message = "节点规格不能为空")
    private String resourcesPoolSpecs;

    @ApiModelProperty(value = "规格信息", required = true)
    @NotNull(message = "规格信息不能为空")
    private String poolSpecsInfo;

    @ApiModelProperty(value = "节点个数", required = true)
    @NotNull(message = "节点个数不能为空")
    private Integer resourcesPoolNode;

    @ApiModelProperty(value = "算法id", required = true)
    @NotNull(message = "算法id不能为空")
    private Long algorithmId;

    @ApiModelProperty(value = "算法来源", required = true)
    @NotNull(message = "算法来源不能为空")
    private Integer algorithmSource;

    @ApiModelProperty(value = "模型来源:0-我的模型，1-预置模型", required = true)
    @NotNull(message = "模型来源不能为空")
    @Min(value = NumberConstant.NUMBER_0, message = "模型来源错误")
    @Max(value = NumberConstant.NUMBER_1, message = "模型来源错误")
    private Integer modelResource;

    @ApiModelProperty(value = "模型路径映射")
    private String modelDirMapping;

    @ApiModelProperty(value = "数据集路径映射")
    private String datasetDirMapping;

    @ApiModelProperty(value = "结果输出路径映射")
    private String resultDirMapping;
}
