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
package org.dubhe.train.client;

import org.dubhe.biz.base.constant.ApplicationNameConst;
import org.dubhe.biz.base.dto.PtModelInfoConditionQueryDTO;
import org.dubhe.biz.base.dto.PtModelInfoQueryByIdDTO;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.base.vo.PtModelInfoQueryVO;
import org.dubhe.train.client.fallback.ModelInfoClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * @description 模型管理远程服务调用接口
 * @date 2020-12-21
 */
@FeignClient(value = ApplicationNameConst.SERVER_MODEL, contextId = "modelInfoClient", fallback = ModelInfoClientFallback.class)
public interface ModelInfoClient {
    /**
     * 根据模型id查询模型详情
     *
     * @param ptModelInfoQueryByIdDTO 模型详情查询条件
     * @return PtModelBranchQueryByIdVO 模型详情
     */
    @GetMapping("/ptModelInfo/byModelId")
    DataResponseBody<PtModelInfoQueryVO> getByModelId(@SpringQueryMap PtModelInfoQueryByIdDTO ptModelInfoQueryByIdDTO);

    /**
     * 根据模型条件查询模型详情列表
     * @param ptModelInfoConditionQueryDTO 模型详情列表查询条件
     * @return List<PtModelInfoQueryVO> 模型详情列表
     */
    @GetMapping("/ptModelInfo/conditionQuery")
    DataResponseBody<List<PtModelInfoQueryVO>> getConditionQuery(@SpringQueryMap PtModelInfoConditionQueryDTO ptModelInfoConditionQueryDTO);

    @GetMapping("/ptModelInfo/atlasModel")
    DataResponseBody<PtModelInfoQueryVO> getAtlasModels(@RequestParam String name);


}
