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
package org.dubhe.train.client.fallback;

import org.dubhe.biz.base.dto.TrainAlgorithmSelectAllBatchIdDTO;
import org.dubhe.biz.base.dto.TrainAlgorithmSelectAllByIdDTO;
import org.dubhe.biz.base.dto.TrainAlgorithmSelectByIdDTO;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.base.vo.TrainAlgorithmQureyVO;
import org.dubhe.biz.dataresponse.factory.DataResponseFactory;
import org.dubhe.train.client.AlgorithmClient;

/**
 * @description 算法管理远程调用熔断类
 * @date 2020-12-21
 */
public class AlgorithmClientFallback implements AlgorithmClient {

    @Override
    public DataResponseBody selectAllById(TrainAlgorithmSelectAllByIdDTO trainAlgorithmSelectAllByIdDTO) {
        return DataResponseFactory.failed("call dubhe-algorithm server selectAllById error");
    }

    @Override
    public DataResponseBody selectById(TrainAlgorithmSelectByIdDTO trainAlgorithmSelectByIdDTO) {
        return DataResponseFactory.failed("call dubhe-algorithm server selectById error");
    }

    @Override
    public DataResponseBody selectAllBatchIds(TrainAlgorithmSelectAllBatchIdDTO trainAlgorithmSelectAllBatchIdDTO) {
        return DataResponseFactory.failed("call dubhe-algorithm server selectAllBatchIds error");
    }

    @Override
    public DataResponseBody<TrainAlgorithmQureyVO> findAlgorithmByName(String name) {
        return DataResponseFactory.failed("call dubhe-algorithm server findAlgorithmByName error");
    }
}
