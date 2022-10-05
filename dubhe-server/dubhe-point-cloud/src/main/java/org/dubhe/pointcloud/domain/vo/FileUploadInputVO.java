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

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @description 文件上传入参
 * @date 2021-12-23
 **/
@Data
public class FileUploadInputVO {

    /**
     * 数据集id
     */
    @ApiModelProperty("数据集id")
    @NotNull(message = "数据集id不能为空")
    private Long datasetId;
    /**
     * 点云文件夹url
     */
    @ApiModelProperty("点云文件夹url")
    @NotBlank(message = "文件夹路径不能为空")
    private String folderUrl;
}
