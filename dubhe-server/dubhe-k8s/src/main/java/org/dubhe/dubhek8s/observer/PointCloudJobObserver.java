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
package org.dubhe.dubhek8s.observer;

import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.base.enums.BizEnum;
import org.dubhe.biz.base.utils.SpringContextHolder;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.dubhek8s.event.callback.PodCallback;
import org.dubhe.k8s.api.TrainJobApi;
import org.dubhe.k8s.constant.K8sLabelConstants;
import org.dubhe.k8s.domain.resource.BizPod;
import org.dubhe.k8s.enums.PodPhaseEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Observable;
import java.util.Observer;

/**
 * @description 观察者，处理点云pod变化
 * @date 2022-04-21
 **/
@Component
public class PointCloudJobObserver implements Observer {

    @Autowired
    private TrainJobApi trainJobApi;

    public PointCloudJobObserver(PodCallback podCallback) {
        podCallback.addObserver(this);
    }

    @Override
    public void update(Observable o, Object arg) {
        if (arg instanceof BizPod){
            BizPod pod = (BizPod) arg;
            boolean pointCloudSucceedOrFailed = (PodPhaseEnum.FAILED.getPhase().equals(pod.getPhase())
            || PodPhaseEnum.SUCCEEDED.getPhase().equals(pod.getPhase()))
                    && BizEnum.POINT_CLOUD.getBizCode().equals(pod.getBusinessLabel())
                    && SpringContextHolder.getActiveProfile().equals(pod.getLabel(K8sLabelConstants.PLATFORM_RUNTIME_ENV));
            if (pointCloudSucceedOrFailed){
                new Thread(() -> {
                    try {
                        Thread.sleep(MagicNumConstant.ONE_MINUTE);
                    }catch (InterruptedException e){
                        LogUtil.error(LogEnum.BIZ_K8S,"PointCloudObserver update error {}",e.getMessage());
                    }
                    LogUtil.warn(LogEnum.BIZ_K8S,"delete success or failed point cloud resourceName {};phase {};podName {}",pod.getLabel(K8sLabelConstants.BASE_TAG_SOURCE),pod.getPhase(),pod.getName());
                    trainJobApi.delete(pod.getNamespace(),pod.getLabel(K8sLabelConstants.BASE_TAG_SOURCE));
                }).start();
            }
        }

    }
}
