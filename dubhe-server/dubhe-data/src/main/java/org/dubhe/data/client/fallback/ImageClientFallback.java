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

package org.dubhe.data.client.fallback;

import org.dubhe.biz.base.dto.PtImageIdDTO;
import org.dubhe.biz.base.dto.PtImageIdsDTO;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.base.vo.PtImageVO;
import org.dubhe.biz.dataresponse.factory.DataResponseFactory;
import org.dubhe.data.client.ImageClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ImageClientFallback implements ImageClient {

    @Override
    public DataResponseBody<PtImageVO> getById(PtImageIdDTO ptImageIdDTO) {
        return DataResponseFactory.failed("call dubhe-image server getById error ");
    }

    @Override
    public DataResponseBody<List<PtImageVO>> listByIds(PtImageIdsDTO ptImageIdsDTO) {
        return DataResponseFactory.failed("call dubhe-image server listByIds error ");
    }
}
