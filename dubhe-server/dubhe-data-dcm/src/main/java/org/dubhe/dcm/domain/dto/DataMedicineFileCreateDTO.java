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
package org.dubhe.dcm.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.io.Serializable;

/**
 * @description 医学数据集文件DTO
 * @date 2020-12-11
 */
@Data
public class DataMedicineFileCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("dcm文件路径")
    @NotEmpty(message = "dcm文件路径不能为空")
    private String url;

    @ApiModelProperty("SOPInstanceUID")
    @NotEmpty(message = "SOPInstanceUID不能为空")
    @JsonProperty(value="SOPInstanceUID")
    private String SOPInstanceUID;

}
