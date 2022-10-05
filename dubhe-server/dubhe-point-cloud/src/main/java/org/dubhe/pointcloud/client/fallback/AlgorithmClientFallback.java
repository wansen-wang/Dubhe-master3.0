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
package org.dubhe.pointcloud.client.fallback;

import org.dubhe.biz.base.dto.TrainAlgorithmSelectByIdDTO;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.base.vo.TrainAlgorithmQureyVO;
import org.dubhe.biz.dataresponse.factory.DataResponseFactory;
import org.dubhe.pointcloud.client.AlgorithmClient;
import org.springframework.stereotype.Component;

/**
 * @description 算法模块远程调用
 * @date 2022-04-22
 **/
@Component
public class AlgorithmClientFallback implements AlgorithmClient {
    @Override
    public DataResponseBody<TrainAlgorithmQureyVO> selectById(TrainAlgorithmSelectByIdDTO trainAlgorithmSelectByIdDTO) {
        return DataResponseFactory.failed("call dubhe-algorithm server getLabelGroup error");
    }
}
