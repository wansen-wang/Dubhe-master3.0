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

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * @description 标注信息保存接口入参
 * @date 2022-01-11
 **/
@Data
public class MarkSaveInputVO {
    /**
     * 数据集id
     */
    @ApiModelProperty("数据集id")
    @NotNull(message = "数据集id")
    private Long datasetId;
    /**
     * 文件id
     */
    @ApiModelProperty("文件id")
    @NotNull
    private Long fileId;

    /**
     * 标注内容
     */
    @ApiModelProperty("原标注内容")
    private List<String> markInfoOld;

    /**
     * 标注内容
     */
    @ApiModelProperty("新标注内容")
    private List<String> markInfoNew;
}
