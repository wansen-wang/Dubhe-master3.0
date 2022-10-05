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

package org.dubhe.data.client;

import org.dubhe.biz.base.constant.ApplicationNameConst;
import org.dubhe.biz.base.dto.PtImageIdDTO;
import org.dubhe.biz.base.dto.PtImageIdsDTO;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.base.vo.PtImageVO;
import org.dubhe.data.client.fallback.ImageClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(value = ApplicationNameConst.SERVER_IMAGE, contextId = "imageClient", fallback = ImageClientFallback.class)
public interface ImageClient {

    /**
     * 根据镜像id获取镜像
     *
     * @param ptImageIdDTO
     * @return
     */
    @GetMapping(value = "/ptImage/byId")
    DataResponseBody<PtImageVO> getById(@SpringQueryMap PtImageIdDTO ptImageIdDTO);

    /**
     * 获取镜像URL
     *
     * @param ptImageIdsDTO
     * @return
     */
    @GetMapping(value = "/ptImage/listByIds")
    DataResponseBody<List<PtImageVO>> listByIds(@SpringQueryMap PtImageIdsDTO ptImageIdsDTO);

}