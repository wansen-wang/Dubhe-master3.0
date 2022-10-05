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
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.base.vo.LabelGroupBaseVO;
import org.dubhe.pointcloud.client.fallback.DataClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Set;

/**
 * @description Data远程服务调用类
 * @date 2022-04-18
 **/
@FeignClient(value = ApplicationNameConst.SERVER_DATA, contextId = "dataClient", fallback = DataClientFallback.class)
public interface DataClient {
    /**
     * 获取标签组基本信息
     * @param labelGroupId 标签组id
     * @return DataResponseBody
     */
    @GetMapping("/labelGroup/{labelGroupId}")
    DataResponseBody queryLabelGroup(@PathVariable(name = "labelGroupId") Long labelGroupId);

    /**
     * 获取标签组基本信息集合
     * @param labelGroupIds 标签组id集合
     * @return DataResponseBody
     */
    @PostMapping("/labelGroup/queryLabelGroupList")
    DataResponseBody<List<LabelGroupBaseVO>> queryLabelGroupList(@RequestBody Set<Long> labelGroupIds);
}
