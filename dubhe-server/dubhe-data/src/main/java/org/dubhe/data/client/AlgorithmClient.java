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
import org.dubhe.biz.base.dto.TrainAlgorithmSelectAllBatchIdDTO;
import org.dubhe.biz.base.dto.TrainAlgorithmSelectByIdDTO;
import org.dubhe.biz.base.dto.TrainAlgorithmSelectByNameDTO;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.base.vo.TrainAlgorithmQureyVO;
import org.dubhe.data.client.fallback.AlgorithmClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(value = ApplicationNameConst.SERVER_ALGORITHM,contextId = "algorithmClient",fallback = AlgorithmClientFallback.class)
public interface AlgorithmClient {

    /**
     * 根据算法id查询算法
     * @param trainAlgorithmSelectByIdDTO 算法查询参数
     * @return DataResponseBody
     */
    @GetMapping("/algorithms/selectById")
    DataResponseBody<TrainAlgorithmQureyVO> selectById(@SpringQueryMap TrainAlgorithmSelectByIdDTO trainAlgorithmSelectByIdDTO);

    /**
     * 根据算法名称模糊查询算法id
     * @param trainAlgorithmSelectByNameDTO 算法查询参数
     * @return DataResponseBody
     */
    @GetMapping("/algorithms/listIdByName")
    DataResponseBody<List<Long>> listIdByName(@SpringQueryMap TrainAlgorithmSelectByNameDTO trainAlgorithmSelectByNameDTO);



    /**
     * 根据算法id查询算法
     * @param trainAlgorithmSelectAllBatchIdDTO 算法查询参数
     * @return DataResponseBody
     */
    @GetMapping("/algorithms/selectAllBatchIds")
    DataResponseBody<List<TrainAlgorithmQureyVO>> selectAllBatchIds(@SpringQueryMap TrainAlgorithmSelectAllBatchIdDTO trainAlgorithmSelectAllBatchIdDTO);

}