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

package org.dubhe.data.domain.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import org.dubhe.biz.base.dto.UserSmallDTO;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
public class AutoLabelModelServiceVO implements Serializable {

    @ApiModelProperty("模型服务ID")
    private Long id;
    @ApiModelProperty("服务名称")
    private String name;
    @ApiModelProperty(value = "模型类型")
    private Integer modelType;
    @ApiModelProperty("模型服务描述")
    private String desc;
    @ApiModelProperty("模型名称")
    private String model;
    @ApiModelProperty("镜像名称")
    private String image;
    @ApiModelProperty("算法名称")
    private String algorithm;
    @ApiModelProperty(value = "状态 101-启动中 102-运行中 103-启动失败 104-停止中 105-已停止")
    private Integer status;
    @ApiModelProperty(value = "节点类型 0-cpu 1-gpu")
    private Integer resourcesPoolType;
    @ApiModelProperty(value = "节点规格")
    private String resourcesPoolSpecs;
    @ApiModelProperty(value = "服务数量")
    private Integer instanceNum;
    @ApiModelProperty("创建时间")
    private Date createTime;
    @ApiModelProperty("创建人")
    private UserSmallDTO createUser;

}
