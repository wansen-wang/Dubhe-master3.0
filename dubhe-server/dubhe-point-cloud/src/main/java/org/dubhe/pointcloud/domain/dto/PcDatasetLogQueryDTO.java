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

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;
import org.dubhe.biz.db.base.BaseLogQuery;

import javax.validation.constraints.NotNull;

/**
 * @description 点云数据集查询DTO
 * @date 2022-05-12
 **/
@Data
@Accessors(chain = true)
public class PcDatasetLogQueryDTO extends BaseLogQuery {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("点云数据集id")
    @NotNull(message = "点云数据集id不能为空")
    private Long pcDatasetId;

}
