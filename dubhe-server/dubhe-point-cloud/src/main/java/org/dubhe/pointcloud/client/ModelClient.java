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
package org.dubhe.pointcloud.client;

import org.dubhe.biz.base.constant.ApplicationNameConst;
import org.dubhe.biz.base.dto.PtModelBranchQueryByIdDTO;
import org.dubhe.biz.base.dto.PtModelInfoQueryByIdDTO;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.base.vo.PtModelBranchQueryVO;
import org.dubhe.biz.base.vo.PtModelInfoQueryVO;
import org.dubhe.pointcloud.client.fallback.ModelClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @description 模型版本远程服务调用类
 * @date 2021-01-19
 */
@FeignClient(value = ApplicationNameConst.SERVER_MODEL, contextId = "modelBranchClient", fallback = ModelClientFallback.class)
public interface ModelClient {

    /**
     * 根据模型版本id查询模型版本详情
     *
     * @param ptModelBranchQueryByIdDTO 模型版本详情查询条件
     * @return PtModelBranchQueryByIdVO 模型版本详情
     */
    @GetMapping("/ptModelBranch/byBranchId")
    DataResponseBody<PtModelBranchQueryVO> getByBranchId(@SpringQueryMap PtModelBranchQueryByIdDTO ptModelBranchQueryByIdDTO);

    @GetMapping("/ptModelInfo/byModelId")
    DataResponseBody<PtModelInfoQueryVO> getByModelId(@SpringQueryMap PtModelInfoQueryByIdDTO ptModelInfoQueryByIdDTO);
}
