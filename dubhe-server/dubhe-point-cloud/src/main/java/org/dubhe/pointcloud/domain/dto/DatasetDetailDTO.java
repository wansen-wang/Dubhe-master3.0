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

import lombok.Data;

/**
 * @description 数据集详情DTO
 * @date 2022-05-23
 **/
@Data
public class DatasetDetailDTO {
    /**
     * 数据集id
     */
    private Long id;

    /**
     * 数据集名称
     */
    private String name;

    /**
     * 标签组名称
     */
    private String labelGroupName;

    /**
     * 描述
     */
    private String remark;

    /**
     * 标注范围-前
     */
    private Double scopeFront;
    /**
     * 标注范围-后
     */
    private Double scopeBehind;
    /**
     * 标注范围-左
     */
    private Double scopeLeft;
    /**
     * 标注范围-右
     */
    private Double scopeRight;

    /**
     * 数据集路径映射
     */
    private String datasetDirMapping;

    /**
     * 算法Id
     */
    private Long algorithmId;

    /**
     * 算法名称
     */
    private String algorithmName;

    /**
     * 算法来源
     */
    private Integer algorithmSource;

    /**
     * 模型id
     */
    private Long modelId;

    /**
     * 模型对应版本id
     */
    private Long modelBranchId;

    /**
     * 模型来源
     */
    private Integer modelResource;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 模型版本
     */
    private String modelVersion;

    /**
     * 镜像名称
     */
    private String imageName;

    /**
     * 镜像版本
     */
    private String imageTag;

    /**
     * 规格信息
     */
    private String poolSpecsInfo;

    /**
     * 节点个数
     */
    private Integer resourcesPoolNode;

    /**
     * 节点类型(0为CPU，1为GPU)
     */
    private Integer resourcesPoolType;

    /**
     * 资源规格
     */
    private String resourcesPoolSpecs;

    /**
     * 模型路径映射
     */
    private String modelDirMapping;

    /**
     * 结果输出路径映射
     */
    private String resultDirMapping;

    /**
     * 标注命令
     */
    private String command;
}
