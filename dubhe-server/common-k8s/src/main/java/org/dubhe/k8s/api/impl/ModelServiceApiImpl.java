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
package org.dubhe.k8s.api.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Maps;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.apache.commons.compress.utils.Lists;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.base.constant.SymbolConstant;
import org.dubhe.biz.file.api.FileStoreApi;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.k8s.api.ModelServiceApi;
import org.dubhe.k8s.api.ResourceIisolationApi;
import org.dubhe.k8s.api.ResourceQuotaApi;
import org.dubhe.k8s.api.VolumeApi;
import org.dubhe.k8s.constant.K8sParamConstants;
import org.dubhe.k8s.domain.PtBaseResult;
import org.dubhe.k8s.domain.bo.BuildFsVolumeBO;
import org.dubhe.k8s.domain.bo.ModelServiceBO;
import org.dubhe.k8s.domain.bo.ModelServingBO;
import org.dubhe.k8s.domain.resource.BizDeployment;
import org.dubhe.k8s.domain.vo.ModelServiceVO;
import org.dubhe.k8s.domain.vo.ModelServingVO;
import org.dubhe.k8s.domain.vo.VolumeVO;
import org.dubhe.k8s.enums.*;
import org.dubhe.k8s.utils.BizConvertUtils;
import org.dubhe.k8s.utils.K8sUtils;
import org.dubhe.k8s.utils.LabelUtils;
import org.dubhe.k8s.utils.YamlUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Resource;
import java.util.*;

public class ModelServiceApiImpl implements ModelServiceApi {

    @Autowired
    private ResourceQuotaApi resourceQuotaApi;

    @Autowired
    private ResourceIisolationApi resourceIisolationApi;

    @Resource(name = "hostFileStoreApiImpl")
    private FileStoreApi fileStoreApi;

    @Autowired
    private VolumeApi volumeApi;

    private KubernetesClient client;
    private K8sUtils k8sUtils;


    public ModelServiceApiImpl(K8sUtils k8sUtils) {
        this.k8sUtils = k8sUtils;
        this.client = k8sUtils.getClient();
    }


    @Override
    public ModelServiceVO create(ModelServiceBO bo) {
        //资源配额校验
        LimitsOfResourcesEnum limitsOfResources = resourceQuotaApi.reachLimitsOfResources(bo.getNamespace(), bo.getCpuNum(), bo.getMemNum(), bo.getGpuNum());
        if (!LimitsOfResourcesEnum.ADEQUATE.equals(limitsOfResources)) {
            return new ModelServiceVO().error(K8sResponseEnum.LACK_OF_RESOURCES.getCode(), limitsOfResources.getMessage());
        }
        LogUtil.info(LogEnum.BIZ_K8S, "Params of creating ModelServing--create:{}", bo);
        if (!fileStoreApi.createDirs(bo.getDirList().toArray(new String[MagicNumConstant.ZERO]))) {
            return new ModelServiceVO().error(K8sResponseEnum.INTERNAL_SERVER_ERROR.getCode(), K8sResponseEnum.INTERNAL_SERVER_ERROR.getMessage());
        }
        //存储卷构建
        VolumeVO volumeVO = volumeApi.buildFsVolumes(new BuildFsVolumeBO(bo.getNamespace(), bo.getResourceName(), bo.getFsMounts()));
        if (!K8sResponseEnum.SUCCESS.getCode().equals(volumeVO.getCode())) {
            return new ModelServiceVO().error(volumeVO.getCode(), volumeVO.getMessage());
        }

        //名称生成
        String deploymentName = StrUtil.format(K8sParamConstants.RESOURCE_NAME_TEMPLATE, bo.getResourceName(), RandomUtil.randomString(MagicNumConstant.EIGHT));

        //部署deployment
        Deployment deployment = buildDeployment(bo, volumeVO, deploymentName);
        LogUtil.info(LogEnum.BIZ_K8S, "Ready to deploy {}, yaml信息为{}", deploymentName, YamlUtils.dumpAsYaml(deployment));
        resourceIisolationApi.addIisolationInfo(deployment);
        Deployment deploymentResult = client.apps().deployments().inNamespace(bo.getNamespace()).create(deployment);

        return new ModelServiceVO(BizConvertUtils.toBizDeployment(deploymentResult));
    }

    /**
     * 构建Deployment
     *
     * @return Deployment
     */
    private Deployment buildDeployment(ModelServiceBO bo, VolumeVO volumeVO, String deploymentName) {
        Map<String, String> childLabels = LabelUtils.getChildLabels(bo.getResourceName(), deploymentName,
                K8sKindEnum.DEPLOYMENT.getKind(), bo.getBusinessLabel(), bo.getTaskIdentifyLabel());
        LabelSelector labelSelector = new LabelSelector();
        labelSelector.setMatchLabels(childLabels);
        return new DeploymentBuilder()
                .withNewMetadata()
                .withName(deploymentName)
                .addToLabels(LabelUtils.getBaseLabels(bo.getResourceName(), bo.getBusinessLabel()))
                .withNamespace(bo.getNamespace())
                .endMetadata()
                .withNewSpec()
                .withReplicas(bo.getReplicas())
                .withSelector(labelSelector)
                .withNewTemplate()
                .withNewMetadata()
                .withName(deploymentName)
                .addToLabels(childLabels)
                .withNamespace(bo.getNamespace())
                .endMetadata()
                .withNewSpec()
                .addToNodeSelector(K8sUtils.gpuSelector(bo.getGpuNum()))
                .addToContainers(buildContainer(bo, volumeVO, deploymentName))
                .addToVolumes(volumeVO.getVolumes().toArray(new Volume[0]))
                .withRestartPolicy(RestartPolicyEnum.ALWAYS.getRestartPolicy())
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

    /**
     * 构建 Container
     * @param bo
     * @param volumeVO
     * @param name
     * @return
     */
    private Container buildContainer(ModelServiceBO bo, VolumeVO volumeVO, String name) {
        Map<String, Quantity> resourcesLimitsMap = Maps.newHashMap();
        Optional.ofNullable(bo.getCpuNum()).ifPresent(v -> resourcesLimitsMap.put(K8sParamConstants.QUANTITY_CPU_KEY, new Quantity(v.toString(), K8sParamConstants.CPU_UNIT)));
        Optional.ofNullable(bo.getGpuNum()).ifPresent(v -> resourcesLimitsMap.put(K8sParamConstants.GPU_RESOURCE_KEY, new Quantity(v.toString())));
        Optional.ofNullable(bo.getMemNum()).ifPresent(v -> resourcesLimitsMap.put(K8sParamConstants.QUANTITY_MEMORY_KEY, new Quantity(v.toString(), K8sParamConstants.MEM_UNIT)));
        Container container = new ContainerBuilder()
                .withNewName(name)
                .withNewImage(bo.getImage())
                .withNewImagePullPolicy(ImagePullPolicyEnum.IFNOTPRESENT.getPolicy())
                .withVolumeMounts(volumeVO.getVolumeMounts())
                .withNewResources().addToLimits(resourcesLimitsMap).endResources()
                .build();
        if (bo.getCmdLines() != null) {
            container.setCommand(Arrays.asList(ShellCommandEnum.BIN_BANSH.getShell()));
            container.setArgs(bo.getCmdLines());
        }
        Probe livenessProbe = new Probe();
        ExecAction execAction = new ExecAction();
        List<String> commands = Lists.newArrayList();
        commands.add("test");
        commands.add("-e");
        commands.add("/tmp/.startup");
        execAction.setCommand(commands);
        livenessProbe.setExec(execAction);
        livenessProbe.setPeriodSeconds(3);
        livenessProbe.setSuccessThreshold(1);
        livenessProbe.setFailureThreshold(10);
//        container.setLivenessProbe(livenessProbe);
        return container;
    }

    @Override
    public PtBaseResult delete(String namespace, String resourceName) {
        try {
            LogUtil.info(LogEnum.BIZ_K8S, "delete model serving namespace:{} resourceName:{}",namespace,resourceName);
            DeploymentList deploymentList = client.apps().deployments().inNamespace(namespace).withLabels(LabelUtils.withEnvResourceName(resourceName)).list();
            if (deploymentList == null || deploymentList.getItems().size() == 0){
                return new PtBaseResult();
            }
            Boolean res = client.extensions().ingresses().inNamespace(namespace).withLabels(LabelUtils.withEnvResourceName(resourceName)).delete()
                    && client.services().inNamespace(namespace).withLabels(LabelUtils.withEnvResourceName(resourceName)).delete()
                    && client.apps().deployments().inNamespace(namespace).withLabels(LabelUtils.withEnvResourceName(resourceName)).delete()
                    && client.secrets().inNamespace(namespace).withLabels(LabelUtils.withEnvResourceName(resourceName)).delete();
            if (res) {
                return new PtBaseResult();
            } else {
                return K8sResponseEnum.REPEAT.toPtBaseResult();
            }
        } catch (KubernetesClientException e) {
            LogUtil.error(LogEnum.BIZ_K8S, "delete error:", e);
            return new PtBaseResult(String.valueOf(e.getCode()), e.getMessage());
        }
    }
}
