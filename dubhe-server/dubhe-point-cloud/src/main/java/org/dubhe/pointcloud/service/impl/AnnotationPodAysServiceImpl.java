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
package org.dubhe.pointcloud.service.impl;


import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.k8s.abstracts.AbstractPodCallback;
import org.dubhe.k8s.domain.dto.BaseK8sPodCallbackCreateDTO;
import org.dubhe.k8s.service.PodCallbackAsyncService;
import org.dubhe.pointcloud.domain.dto.AnnotationK8sPodCallbackCreateDTO;
import org.dubhe.pointcloud.service.PcDatasetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @description 点云模块-k8s回调处理类
 * @date 2022-04-02
 */
@Service(value = "annotationAsyncService")
public class AnnotationPodAysServiceImpl extends AbstractPodCallback implements PodCallbackAsyncService {

    @Autowired
    private PcDatasetService pcDatasetService;

    @Override
    public <R extends BaseK8sPodCallbackCreateDTO> boolean doCallback(int times, R k8sPodCallbackCreateDTO) {
        AnnotationK8sPodCallbackCreateDTO req = (AnnotationK8sPodCallbackCreateDTO) k8sPodCallbackCreateDTO;
        LogUtil.info(LogEnum.POINT_CLOUD, "Thread {} try {} time.Request: {}", Thread.currentThread(), times, req.toString());
        return pcDatasetService.annotationPodCallback(times, req);
    }

    @Override
    public <R extends BaseK8sPodCallbackCreateDTO> void callbackFailed(int retryTimes, R k8sPodCallbackCreateDTO) {
        AnnotationK8sPodCallbackCreateDTO req = (AnnotationK8sPodCallbackCreateDTO) k8sPodCallbackCreateDTO;
        LogUtil.info(LogEnum.POINT_CLOUD, "Thread {} try {} times FAILED! If you want to storage or send failed msg, please impl this.. Request: {}", Thread.currentThread(), retryTimes, req.toString());
    }
}
