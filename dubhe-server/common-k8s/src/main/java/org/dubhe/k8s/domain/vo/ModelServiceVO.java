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

package org.dubhe.k8s.domain.vo;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.dubhe.k8s.domain.PtBaseResult;
import org.dubhe.k8s.domain.resource.BizDeployment;
import org.dubhe.k8s.domain.resource.BizIngress;
import org.dubhe.k8s.domain.resource.BizSecret;
import org.dubhe.k8s.domain.resource.BizService;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class ModelServiceVO  extends PtBaseResult<ModelServiceVO> {
    private BizDeployment bizDeployment;

    public ModelServiceVO( BizDeployment bizDeployment){
        this.bizDeployment = bizDeployment;
    }
}
