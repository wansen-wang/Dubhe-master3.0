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

/**
 * @description 文件信息
 * @date 2021-12-30
 **/
@Data
public class FileUploadSaveInputVO {
    /**
     * 数据集id
     */
    @ApiModelProperty("数据集id")
    private Long datasetId;

    /**
     * 文件名
     */
    @ApiModelProperty("文件名")
    private String name;

    /**
     * 文件类型
     */
    @ApiModelProperty("文件类型")
    private String fileType;

    /**
     * 文件路径
     */
    @ApiModelProperty("文件路径")
    private String key;
}
