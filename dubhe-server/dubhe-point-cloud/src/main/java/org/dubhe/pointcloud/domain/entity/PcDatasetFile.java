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
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;
import org.dubhe.biz.db.entity.BaseEntity;

/**
 * @description pc_dataset_file表实体类
 * @date 2022-04-01
 **/
@Data
@Accessors(chain = true)
@TableName(value = "pc_dataset_file", autoResultMap = true)
public class PcDatasetFile extends BaseEntity {
    /**
     * 主键
     */
    @TableId(value = "id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;
    /**
     * 文件名
     */
    @TableField(value = "name")
    private String name;
    /**
     * 文件类型
     */
    @TableField(value = "file_type")
    private String fileType;
    /**
     * 是否难例
     */
    @TableField(value = "difficulty")
    private Boolean difficulty;
    /**
     * 数据集id
     */
    @TableField(value = "dataset_id")
    private Long datasetId;
    /**
     * 文件url
     */
    @TableField(value = "url")
    private String url;
    /**
     * 标注状态
     */
    @TableField(value = "mark_status")
    private Integer markStatus;
    /**
     * 标注结果文件名称
     */
    @TableField(value = "mark_file_name")
    private String markFileName;
    /**
     * 标注结果文件url
     */
    @TableField(value = "mark_file_url")
    private String markFileUrl;

    /**
     * 标注状态名称
     */
    @TableField(exist = false)
    private String markStatusName;
}
