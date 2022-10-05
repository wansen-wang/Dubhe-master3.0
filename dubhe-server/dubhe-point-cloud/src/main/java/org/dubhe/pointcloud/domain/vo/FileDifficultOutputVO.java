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

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @description 数据集难例查询数据返回
 * @date 2022-04-01
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileDifficultOutputVO {
    @ApiModelProperty("点云文件id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;
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
     * 是否难例
     */
    @ApiModelProperty("是否难例")
    private Boolean difficulty;
    /**
     * 数据集id
     */
    @ApiModelProperty("数据集id")
    private Long datasetId;
    /**
     * 文件url
     */
    @ApiModelProperty("文件url")
    private String url;
    /**
     * 标注状态
     */
    @ApiModelProperty("标注状态")
    private Integer markStatus;
    /**
     * 标注结果文件名称
     */
    @ApiModelProperty("标注结果文件名称")
    private String markFileName;
    /**
     * 标注结果文件url
     */
    @ApiModelProperty("标注结果文件url")
    private String markFileUrl;

    /**
     * 标注状态名称
     */
    @ApiModelProperty("标注状态名称")
    private String markStatusName;
    /**
     * 难例数量
     */
    @ApiModelProperty("难例数量")
    private Long difficultCount;

}
