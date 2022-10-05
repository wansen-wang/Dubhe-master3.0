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

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 * @description 点云数据集创建
 * @date 2022-04-01
 **/
@Data
public class PcDatasetCreateDTO {
    /**
     * 数据集名称
     */
    @ApiModelProperty("数据集名称")
    @NotEmpty(message = "数据集名称不能为空")
    private String name;
    /**
     * 数据集描述
     */
    @ApiModelProperty("数据集描述")
    private String remark;
    /**
     * 标签组id
     */
    @ApiModelProperty("标签组id")
    @NotNull(message = "标签组id不能为空")
    private Long labelGroupId;
    /**
     * 标注范围-前
     */
    @ApiModelProperty("标注范围-前")
    private Double scopeFront;
    /**
     * 标注范围-后
     */
    @ApiModelProperty("标注范围-后")
    private Double scopeBehind;
    /**
     * 标注范围-左
     */
    @ApiModelProperty("标注范围-左")
    private Double scopeLeft;
    /**
     * 标注范围-右
     */
    @ApiModelProperty("标注范围-右")
    private Double scopeRight;
}
