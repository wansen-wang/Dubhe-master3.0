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
package org.dubhe.pointcloud.domain.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * @description 数据集查询数据返回
 * @date 2022-04-01
 **/
@Data
public class DatasetQueryVO implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 数据集id
     */
    @ApiModelProperty("数据集id")
    private Long id;
    /**
     * 数据集名称
     */
    @ApiModelProperty("数据集名称")
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
    private Long labelGroupId;
    /**
     * 标签组名称
     */
    @ApiModelProperty("标签组名称")
    private String labelGroupName;
    /**
     * 标签组类型
     */
    @ApiModelProperty("标签组类型")
    private Integer labelGroupType;
    /**
     * 难例数
     */
    @ApiModelProperty("难例数")
    private Long difficultyCount;
    /**
     * 数据集状态
     */
    @ApiModelProperty("数据集状态")
    private Integer status;
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
    /**
     * 容器名称
     */
    @ApiModelProperty("资源名称")
    private String resourceName;
    /**
     * 状态详情
     */
    @ApiModelProperty("状态详情")
    private String statusDetail;
    /**
     * 更新时间
     */
    @ApiModelProperty("创建时间")
    private Timestamp createTime;
    /**
     * 更新时间
     */
    @ApiModelProperty("更新时间")
    private Timestamp updateTime;
    /**
     * 创建数据集用户id
     */
    @ApiModelProperty("创建数据集用户id")
    private Long createUserId;

}
