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
package org.dubhe.data.domain.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;
import org.dubhe.biz.base.utils.StringUtils;
import org.dubhe.biz.db.entity.BaseEntity;

/**
 * @description 点云数据集
 * @date 2022-04-01
 **/
@Data
@Accessors(chain = true)
@TableName(value = "pc_dataset", autoResultMap = true)
public class PcDataset extends BaseEntity {
    /**
     * 主键id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /**
     * 数据集名称
     */
    @TableField(value = "name")
    private String name;
    /**
     * 标签组id
     */
    @TableField(value = "label_group_id")
    private Long labelGroupId;
    /**
     * 难例数
     */
    @TableField(value = "difficulty_count")
    private Long difficultyCount;
    /**
     * 文件数
     */
    @TableField(value = "file_count")
    private Long fileCount;
    /**
     *数据集状态
     */
    @TableField(value = "status")
    private Integer status;
    /**
     * 数据集描述
     */
    @TableField(value = "remark")
    private String remark;
    /**
     * 标注范围-前
     */
    @TableField("scope_front")
    private Double scopeFront;
    /**
     * 标注范围-后
     */
    @TableField("scope_behind")
    private Double scopeBehind;
    /**
     * 标注范围-左
     */
    @TableField("scope_left")
    private Double scopeLeft;
    /**
     * 标注范围-右
     */
    @TableField("scope_right")
    private Double scopeRight;
    /**
     * 资源名称
     */
    @TableField("resource_name")
    private String resourceName;
    /**
     * 数据集存储路径
     */
    @TableField("url")
    private String url;


    /**
     * 状态对应的详情信息
     */
    @TableField(value = "status_detail")
    private String statusDetail;

    /**
     * put 键值
     *
     * @param key 键
     * @param value 值
     */
    public void putStatusDetail(String key, String value) {
        statusDetail = StringUtils.putIntoJsonStringMap(key, value, statusDetail);
    }

    /**
     * 移除 键值
     *
     * @param key 键
     */
    public void removeStatusDetail(String key) {
        statusDetail = StringUtils.removeFromJsonStringMap(key, statusDetail);
    }
}
