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
package org.dubhe.image.domain.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.List;

/**
 * @description 查询镜像
 * @date 2021-07-07
 */
@Data
@Accessors(chain = true)
public class PtImageQueryImageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "镜像名称", required = true)
    @NotBlank(message = "源镜像名称不能为空")
    private String imageName;

    @ApiModelProperty(value = "镜像用途(0:notebook , 1:train , 2:serving, 3:terminal, 4:point-cloud)", required = true)
    List<Integer> imageTypes;

    @ApiModelProperty(value = "镜像来源(0为我的镜像, 1为预置镜像)")
    private Integer imageResource;
}