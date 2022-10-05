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
package org.dubhe.pointcloud.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;
import org.dubhe.biz.db.entity.BaseEntity;

/**
 * @description pc_annotation_detail表实体类
 * @date 2022-05-23
 **/
@Data
@Accessors(chain = true)
@TableName(value = "pc_annotation_detail", autoResultMap = true)
public class PcAnnotationDetail extends BaseEntity {

    /**
     * 数据集id
     */
    @TableId(value = "dataset_id")
    private Long datasetId;

    /**
     * 数据集路径映射
     */
    @TableField(value = "dataset_dir_mapping")
    private String datasetDirMapping;

    /**
     * 算法Id
     */
    @TableField(value = "algorithm_id")
    private Long algorithmId;

    /**
     * 算法名称
     */
    @TableField(value = "algorithm_name")
    private String algorithmName;

    /**
     * 算法来源
     */
    @TableField(value = "algorithm_source")
    private Integer algorithmSource;

    /**
     * 模型id
     */
    @TableField(value = "model_id")
    private Long modelId;

    /**
     * 模型对应版本id
     */
    @TableField(value = "model_branch_id")
    private Long modelBranchId;

    /**
     * 模型来源
     */
    @TableField(value = "model_resource")
    private Integer modelResource;

    /**
     * 模型名称
     */
    @TableField(value = "model_name")
    private String modelName;

    /**
     * 模型版本
     */
    @TableField(value = "model_version")
    private String modelVersion;

    /**
     * 镜像名称
     */
    @TableField(value = "image_name")
    private String imageName;

    /**
     * 镜像版本
     */
    @TableField(value = "image_tag")
    private String imageTag;

    /**
     * 规格信息
     */
    @TableField(value = "pool_specs_info")
    private String poolSpecsInfo;

    /**
     * 节点个数
     */
    @TableField(value = "resources_pool_node")
    private Integer resourcesPoolNode;

    /**
     * 节点类型(0为CPU，1为GPU)
     */
    @TableField(value = "resources_pool_type")
    private Integer resourcesPoolType;

    /**
     * 资源规格
     */
    @TableField(value = "resources_pool_specs")
    private String resourcesPoolSpecs;

    /**
     * 模型路径映射
     */
    @TableField(value = "model_dir_mapping")
    private String modelDirMapping;

    /**
     * 结果输出路径映射
     */
    @TableField(value = "result_dir_mapping")
    private String resultDirMapping;

    /**
     * 标注命令
     */
    @TableField(value = "command")
    private String command;
}
