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

package org.dubhe.data.service.impl;

import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.data.constant.AutoLabelModelServiceStatusEnum;
import org.dubhe.data.domain.dto.DataK8sDeploymentCallbackCreateDTO;
import org.dubhe.data.service.AutoLabelModelServiceService;
import org.dubhe.k8s.abstracts.AbstractDeploymentCallback;
import org.dubhe.k8s.domain.dto.BaseK8sDeploymentCallbackCreateDTO;
import org.dubhe.k8s.service.DeploymentCallbackAsyncService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service(value = "dataDeploymentAsyncService")
public class DataDeploymentAsyncServiceImpl extends AbstractDeploymentCallback implements DeploymentCallbackAsyncService {

    @Resource
    private AutoLabelModelServiceService autoLabelModelServiceService;

    @Override
    public <R extends BaseK8sDeploymentCallbackCreateDTO> boolean doCallback(int times, R k8sDeploymentCallbackCreateDTO) {
        // 强制转型
        DataK8sDeploymentCallbackCreateDTO req = (DataK8sDeploymentCallbackCreateDTO) k8sDeploymentCallbackCreateDTO;
        LogUtil.info(LogEnum.BIZ_DATASET, "Thread {} try {} time.Request: {}", Thread.currentThread(), times, req.toString());
        //在线服务回调
        return autoLabelModelServiceService.deploymentCallback(req);
    }

    @Override
    public <R extends BaseK8sDeploymentCallbackCreateDTO> void callbackFailed(int retryTimes, R k8sDeploymentCallbackCreateDTO) {
        DataK8sDeploymentCallbackCreateDTO req = (DataK8sDeploymentCallbackCreateDTO) k8sDeploymentCallbackCreateDTO;
        LogUtil.info(LogEnum.SERVING, "Thread {} try {} times FAILED! if you want to storage or send failed msg,please impl this.. Request: {}", Thread.currentThread(), retryTimes, req.toString());
    }
}
