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

package org.dubhe.data.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.dataresponse.factory.DataResponseFactory;
import org.dubhe.data.domain.dto.DataK8sDeploymentCallbackCreateDTO;
import org.dubhe.k8s.service.DeploymentCallbackAsyncService;
import org.dubhe.k8s.utils.K8sCallBackTool;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

import static org.dubhe.biz.base.constant.StringConstant.K8S_CALLBACK_PATH_DEPLOYMENT;

@Api(tags = "k8s回调：deployment")
@RestController
@RequestMapping(K8S_CALLBACK_PATH_DEPLOYMENT)
public class K8sCallbackDeploymentController {

    @Resource(name = "dataDeploymentAsyncService")
    private DeploymentCallbackAsyncService dataDeploymentAsyncService;

    /**
     * 模型部署在线服务异步回调
     *
     * @param k8sToken
     * @param dataK8sDeploymentCallbackCreateDTO
     * @return
     */
    @PostMapping(value = "/data")
    @ApiOperation("模型服务 deployment 回调")
    public DataResponseBody servingDeploymentCallBack(@ApiParam(type = "head") @RequestHeader(name = K8sCallBackTool.K8S_CALLBACK_TOKEN) String k8sToken,
                                               @Validated @RequestBody DataK8sDeploymentCallbackCreateDTO dataK8sDeploymentCallbackCreateDTO) {
        dataDeploymentAsyncService.deploymentCallBack(dataK8sDeploymentCallbackCreateDTO);
        return DataResponseFactory.success("模型服务异步回调中");
    }
}
