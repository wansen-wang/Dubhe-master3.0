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

import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.base.vo.LabelGroupBaseVO;
import org.dubhe.biz.dataresponse.factory.DataResponseFactory;
import org.dubhe.pointcloud.client.DataClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * @description 数据模块远程调用熔断类
 * @date 2022-04-18
 **/
@Component
public class DataClientFallback implements DataClient {
    @Override
    public DataResponseBody queryLabelGroup(Long labelGroupId) {

        return DataResponseFactory.failed("call dubhe-data server queryLabelGroup error");
    }

    @Override
    public DataResponseBody<List<LabelGroupBaseVO>> queryLabelGroupList(Set<Long> labelGroupIds) {
        return DataResponseFactory.failed("call dubhe-data server getLabelGroup error");
    }
}
