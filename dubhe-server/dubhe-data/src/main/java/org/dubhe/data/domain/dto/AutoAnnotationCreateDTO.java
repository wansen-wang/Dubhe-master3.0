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

package org.dubhe.data.domain.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dubhe.biz.base.annotation.EnumValue;
import org.dubhe.data.constant.Constant;
import org.dubhe.data.constant.FileTypeEnum;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * @description 自动标注保存
 * @date 2020-04-20
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AutoAnnotationCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("数据集ID")
    @NotNull(message = "自动标注的数据集不能为空")
    private Long[] datasetIds;

    /**
     * 任务类型
     */
    private Integer type;

    /**
     * 模型服务id
     */
    @NotNull(message = "模型服务不能为空")
    private Long modelServiceId;

    /**
     * 文件状态 400-全部 304-无标注 303-有标注
     */
    @NotNull(message = "文件标注信息筛选不能空")
    @EnumValue(enumClass = FileTypeEnum.class, enumMethod = "autoFileStatusIsValid",
            message = Constant.ANNOTATE_FILE_TYPE_RULE, groups = AutoAnnotationCreateDTO.Create.class)
    private Integer fileStatus;

    public @interface Create {
    }

}
