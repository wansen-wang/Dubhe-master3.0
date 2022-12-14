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

package org.dubhe.serving.task;

import io.fabric8.kubernetes.client.KubernetesClientException;
import org.apache.commons.collections4.CollectionUtils;
import org.dubhe.biz.base.constant.NumberConstant;
import org.dubhe.biz.base.constant.SymbolConstant;
import org.dubhe.biz.base.context.UserContext;
import org.dubhe.biz.base.enums.BizEnum;
import org.dubhe.biz.base.enums.ResourcesPoolTypeEnum;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.utils.StringUtils;
import org.dubhe.biz.file.api.FileStoreApi;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.k8s.api.DistributeTrainApi;
import org.dubhe.k8s.api.ModelServingApi;
import org.dubhe.k8s.api.NodeApi;
import org.dubhe.k8s.api.TrainJobApi;
import org.dubhe.k8s.domain.PtBaseResult;
import org.dubhe.k8s.domain.bo.DistributeTrainBO;
import org.dubhe.k8s.domain.bo.ModelServingBO;
import org.dubhe.k8s.domain.bo.PtJupyterJobBO;
import org.dubhe.k8s.domain.bo.PtMountDirBO;
import org.dubhe.k8s.domain.resource.BizDistributeTrain;
import org.dubhe.k8s.domain.resource.BizServicePort;
import org.dubhe.k8s.domain.vo.ModelServingVO;
import org.dubhe.k8s.domain.vo.PtJupyterJobVO;
import org.dubhe.k8s.utils.K8sNameTool;
import org.dubhe.serving.client.MailClient;
import org.dubhe.serving.constant.ServingConstant;
import org.dubhe.serving.dao.BatchServingMapper;
import org.dubhe.serving.dao.ServingInfoMapper;
import org.dubhe.serving.domain.entity.BatchServing;
import org.dubhe.serving.domain.entity.ServingInfo;
import org.dubhe.serving.domain.entity.ServingModelConfig;
import org.dubhe.serving.enums.ServingErrorEnum;
import org.dubhe.serving.enums.ServingFrameTypeEnum;
import org.dubhe.serving.enums.ServingStatusEnum;
import org.dubhe.serving.enums.ServingTypeEnum;
import org.dubhe.serving.service.ServingModelConfigService;
import org.dubhe.serving.utils.GrpcClient;
import org.dubhe.serving.utils.ServingStatusDetailDescUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;


/**
 * @description ????????????????????????
 * @date 2020-09-02
 */
@Component
public class DeployServingAsyncTask {

    @Resource
    private ServingInfoMapper servingInfoMapper;

    @Resource
    private BatchServingMapper batchServingMapper;

    @Resource
    private ServingModelConfigService servingModelConfigService;

    @Resource
    private ModelServingApi modelServingApi;

     @Resource
    private NodeApi nodeApi;

    @Resource
    private K8sNameTool k8sNameTool;

    @Resource
    private DistributeTrainApi distributeTrainApi;

    @Resource
    private TrainJobApi trainJobApi;

    @Resource
    private MailClient mailClient;

    @Resource
    private GrpcClient grpcClient;

    @Resource(name = "hostFileStoreApiImpl")
    private FileStoreApi fileStoreApi;

    /**
     * Service????????????
     */
    @Value("${serving.sourcePath}")
    private String sourcePath;

    private final static String TRUE = "True";

    private final static String FALSE = "False";

    /**
     * ??????pod???????????????
     *
     * @param user            ????????????
     * @param servingInfo     ??????????????????
     * @param modelConfigList ????????????????????????????????????
     */
    @Async("servingExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void deployServing(UserContext user, ServingInfo servingInfo, List<ServingModelConfig> modelConfigList, String taskIdentify) {
        boolean flag = false;
        //?????????????????????????????????????????????????????????????????????
        servingInfo.removeStatusDetail(ServingStatusDetailDescUtil.getServingStatusDetailKey(ServingStatusDetailDescUtil.CONTAINER_DEPLOYMENT_EXCEPTION, servingInfo.getName()));
        for (ServingModelConfig servingModelConfig : modelConfigList) {
            try {
                ModelServingBO bo = buildModelServingBO(user, servingInfo, servingModelConfig, taskIdentify);
                if (bo == null) {
                    LogUtil.error(LogEnum.SERVING, "User {} build the parameter failed.The id of servingModelConfig is {}", user.getUsername(), servingModelConfig.getId());
                    continue;
                }
                //??????pod
                ModelServingVO modelServingVO = modelServingApi.create(bo);
                //??????????????????????????????????????????
                String uniqueName = ServingStatusDetailDescUtil.getUniqueName(servingModelConfig.getModelName(), servingModelConfig.getModelVersion());
                String statusDetailKey = ServingStatusDetailDescUtil.getServingStatusDetailKey(ServingStatusDetailDescUtil.CONTAINER_DEPLOYMENT_EXCEPTION, uniqueName);
                if (ServingConstant.SUCCESS_CODE.equals(modelServingVO.getCode())) {
                    // ??????pod?????????url??????????????????????????????
                    List<BizServicePort> ports = modelServingVO.getBizService().getPorts();

                    if (CollectionUtils.isNotEmpty(ports)) {
                        //????????????url
                        String url = "";
                        if (ports.get(NumberConstant.NUMBER_0).getNodePort() != null){
                            url = nodeApi.getAvailableNodeIp()+":"+ports.get(NumberConstant.NUMBER_0).getNodePort();
                        }
                        servingModelConfig.setUrl(url);
                        flag = true;
                        servingInfo.removeStatusDetail(statusDetailKey);
                        if (servingModelConfigService.updateById(servingModelConfig)) {
                            LogUtil.info(LogEnum.SERVING, "User {} deploy the model SUCCESS. servingModelConfigId = {}, resourceInfo = {}", user.getUsername(), servingModelConfig.getId(), servingModelConfig.getResourceInfo());
                        } else {
                            LogUtil.error(LogEnum.SERVING, "User {} failed saving online service model config. Database update FAILED, service model config id={}, resourceInfo = {}", user.getUsername(), servingModelConfig.getId(), servingModelConfig.getResourceInfo());
                            // ???????????????????????????pod??????????????????????????????????????????????????????????????????pod
                            if (StringUtils.isNotBlank(servingModelConfig.getUrl())) {
                                flag = false;
                                // ??????????????????pod
                                List<ServingModelConfig> deleteList = new ArrayList<>();
                                deleteList.add(servingModelConfig);
                                deleteServing(servingInfo, deleteList);
                            }
                        }
                    } else {
                        servingInfo.putStatusDetail(statusDetailKey, "pod?????????url??????");
                    }
                } else {
                    LogUtil.error(LogEnum.SERVING, "User {} failed saving online service model config. service model config id={}, error message: {}", user.getUsername(), servingModelConfig.getId(), modelServingVO.getMessage());
                    servingInfo.putStatusDetail(statusDetailKey, modelServingVO.getMessage());
                }
            } catch (Exception e) {
                String statusDetailKey = ServingStatusDetailDescUtil.getServingStatusDetailKey(ServingStatusDetailDescUtil.CONTAINER_DEPLOYMENT_EXCEPTION, servingInfo.getName());
                servingInfo.putStatusDetail(statusDetailKey, e.getMessage());
                LogUtil.error(LogEnum.SERVING, "User {} create serving failed.The name of serving is {}", user.getUsername(), servingInfo.getName(), e);
            }
        }

        //??????????????????
        if (!flag) {
            servingInfo.setStatus(ServingStatusEnum.EXCEPTION.getStatus());

        }
        //grpc??????????????????????????????
        if (ServingTypeEnum.GRPC.getType().equals(servingInfo.getType())) {
            String url = modelConfigList.get(0).getUrl();
            if (StringUtils.isNotBlank(url)) {
                grpcClient.createChannel(servingInfo.getId(), url, user);
            }
        }
        int result = servingInfoMapper.updateById(servingInfo);
        if (result < NumberConstant.NUMBER_1) {
            LogUtil.error(LogEnum.SERVING, "User {} failed to update the servingInfo table.The name of serving is {}", user.getUsername(), servingInfo.getName());
        }
    }

    /**
     * ????????????????????????
     *
     * @param user               ????????????
     * @param servingInfo        ??????????????????
     * @param servingModelConfig ??????????????????????????????
     * @return ModelServingBO ?????????????????????
     */
    private ModelServingBO buildModelServingBO(UserContext user, ServingInfo servingInfo, ServingModelConfig servingModelConfig, String taskIdentify) {
        ModelServingBO bo = new ModelServingBO();
        //????????????
        if (ServingTypeEnum.GRPC.getType().equals(servingInfo.getType())) {
            bo.setGrpcPort(ServingConstant.POD_LISTENING_POD);
        } else {
            bo.setHttpPort(ServingConstant.POD_LISTENING_POD);
        }
        //????????????????????????????????????????????????????????????
        String scriptName = ServingConstant.HTTP_SCRIPT;
        if (ServingTypeEnum.GRPC.getType().equals(servingInfo.getType())) {
            scriptName = ServingConstant.GRPC_SCRIPT;
        }
        String platform = ServingFrameTypeEnum.getFrameName(servingModelConfig.getFrameType());
        // ????????????
        String command = String.format(ServingConstant.SERVING_COMMAND, ServingConstant.DUBHE_SERVING_PATH, scriptName, platform, ServingConstant.MODEL_PATH,
                ResourcesPoolTypeEnum.isGpuCode(servingModelConfig.getResourcesPoolType()) ? TRUE : FALSE, servingModelConfig.getUseScript() ? TRUE : FALSE) + servingModelConfig.getDeployParam();
        String resourceInfo = StringUtils.getRandomString() + servingModelConfig.getId();
        servingModelConfig.setResourceInfo(resourceInfo);
        String targetPath = k8sNameTool.getAbsolutePath(ServingConstant.ONLINE_ROOT_PATH + servingInfo.getCreateUserId() + File.separator + servingInfo.getId() + File.separator + servingModelConfig.getId() + File.separator);
        String servingPath = getServingSourcePath(user, targetPath, servingModelConfig.getUseScript(), servingModelConfig.getScriptPath());
        if (StringUtils.isBlank(servingPath)) {
            return null;
        }
        bo.setNamespace(k8sNameTool.getNamespace(servingInfo.getCreateUserId()))
                .setResourceName(k8sNameTool.generateResourceName(BizEnum.SERVING, servingModelConfig.getResourceInfo()))
                .setReplicas(servingModelConfig.getResourcesPoolNode())
                .setGpuNum(servingModelConfig.getGpuNum())
                .setCpuNum(servingModelConfig.getCpuNum())
                .setMemNum(servingModelConfig.getMemNum())
                .setImage(servingModelConfig.getImage())
                .setCmdLines(Arrays.asList("-c", command))
                .setFsMounts(new HashMap<String, PtMountDirBO>(NumberConstant.NUMBER_4) {{
                    put(ServingConstant.MODEL_PATH, new PtMountDirBO(k8sNameTool.getAbsolutePath(servingModelConfig.getModelAddress())));
                    put(ServingConstant.DUBHE_SERVING_PATH, new PtMountDirBO(servingPath));
                }})
                .setTaskIdentifyLabel(taskIdentify);
        bo.setBusinessLabel(k8sNameTool.getPodLabel(BizEnum.SERVING));
        return bo;
    }

    /**
     * ??????Serving??????????????????
     *
     * @param user        ????????????
     * @param targetPath   ????????????
     * @param isUseScript ??????????????????
     * @param scriptPath  ????????????
     * @return
     */
    private String getServingSourcePath(UserContext user, String targetPath, boolean isUseScript, String scriptPath) {
        //??????????????????????????????????????????????????????
        String servingPath = k8sNameTool.getAbsolutePath(sourcePath);
        //??????????????????????????????,?????????dubhe_Serving???????????????????????????????????????????????????
        if (isUseScript) {
            boolean servingCopy = fileStoreApi.copyDir(fileStoreApi.getRootDir() + fileStoreApi.getBucket() + sourcePath, targetPath);
            if (!servingCopy) {
                LogUtil.info(LogEnum.SERVING, "User {} failed to copy the source", user.getUsername());
                return null;
            }
            //?????????????????????????????????????????????dubhe_serving/service/???????????????????????????common_inference_service.py
            //????????????????????????python????????????list?????????????????????????????????
            List<String> pyPathList = fileStoreApi.filterFileSuffix(fileStoreApi.getRootDir() + fileStoreApi.getBucket() + scriptPath, ServingConstant.PY_SUFFIX);
            if (CollectionUtils.isNotEmpty(pyPathList)) {
                LogUtil.info(LogEnum.SERVING, "User {} copy the inference script, source:{}, target:{}", user.getUsername(), pyPathList.get(0), targetPath + ServingConstant.INFERENCE_SCRIPT_PATH);
                boolean scriptCopy = fileStoreApi.copyFileAndRename(pyPathList.get(0), targetPath + ServingConstant.INFERENCE_SCRIPT_PATH);
                if (!scriptCopy) {
                    LogUtil.info(LogEnum.SERVING, "User {} failed to copy the inference script", user.getUsername());
                    return null;
                }
            } else {
                LogUtil.info(LogEnum.SERVING, "User {} failed to copy the inference script, script {} not exist", user.getUsername(), pyPathList);
                return null;
            }
            servingPath = fileStoreApi.formatPath(targetPath + getSourceName(sourcePath));
        }
        return servingPath;
    }

    /**
     * ??????pod
     *
     * @param servingInfo     ??????????????????
     * @param modelConfigList ????????????????????????????????????
     */
    @Async("servingExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void deleteServing(ServingInfo servingInfo, List<ServingModelConfig> modelConfigList) {
        boolean flag = true;
        try {
            for (ServingModelConfig servingModelConfig : modelConfigList) {

                String namespace = k8sNameTool.getNamespace(servingInfo.getCreateUserId());
                String resourceName = k8sNameTool.generateResourceName(BizEnum.SERVING, servingModelConfig.getResourceInfo());
                LogUtil.info(LogEnum.SERVING, "Delete the service, namespace:{}, resourceName:{}", namespace, resourceName);

                String uniqueName = ServingStatusDetailDescUtil.getUniqueName(servingModelConfig.getModelName(), servingModelConfig.getModelVersion());
                String statusDetailKey = ServingStatusDetailDescUtil.getServingStatusDetailKey(ServingStatusDetailDescUtil.CONTAINER_DELETION_EXCEPTION, uniqueName);

                PtBaseResult ptBaseResult = modelServingApi.delete(namespace, resourceName);
                if (ServingConstant.SUCCESS_CODE.equals(ptBaseResult.getCode())) {
                    LogUtil.info(LogEnum.SERVING, "Delete the service SUCCESS, namespace:{}, resourceName:{}", namespace, resourceName);
                } else {
                    LogUtil.error(LogEnum.SERVING, "Delete the service FAILED, namespace:{}, resourceName:{},error message:{}", namespace, resourceName,ptBaseResult.getMessage());
                    servingInfo.putStatusDetail(statusDetailKey, ptBaseResult.getMessage());
                    flag = false;
                }

            }
        } catch (KubernetesClientException e) {
            servingInfo.putStatusDetail(ServingStatusDetailDescUtil.getServingStatusDetailKey(ServingStatusDetailDescUtil.CONTAINER_DELETION_EXCEPTION, servingInfo.getName()), e.getMessage());
            LogUtil.error(LogEnum.SERVING, "An Exception occurred. Service id={}, service name:{}???exception :{}", servingInfo.getId(), servingInfo.getName(), e);
        }
        if (!flag) {
            servingInfo.setStatus(ServingStatusEnum.EXCEPTION.getStatus());
            LogUtil.error(LogEnum.SERVING, "An Exception occurred when stopping the service, service name:{}", servingInfo.getName());
        }
        LogUtil.info(LogEnum.SERVING, "Stopped the service with SUCCESS, service name:{}", servingInfo.getName());
        //grpc????????????????????????
        if (ServingTypeEnum.GRPC.getType().equals(servingInfo.getType())) {
            GrpcClient.shutdownChannel(servingInfo.getId());
        }
        int result = servingInfoMapper.updateById(servingInfo);
        if (result < NumberConstant.NUMBER_1) {
            LogUtil.error(LogEnum.SERVING, "FAILED stopping the online service. Database update FAILED. Service id={}, service name:{}???service status:{}", servingInfo.getId(), servingInfo.getName(), servingInfo.getStatus());
            throw new BusinessException(ServingErrorEnum.DATABASE_ERROR);
        }
    }

    /**
     * ????????????????????????
     *
     * @param user         ????????????
     * @param batchServing ??????????????????
     */
    @Async("servingExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void deployBatchServing(UserContext user, BatchServing batchServing, String taskIdentify) {
        if (batchServing.getResourcesPoolNode() == NumberConstant.NUMBER_1) {
            //?????????
            PtJupyterJobBO ptJupyterJobBO = buildJobBo(user, batchServing, taskIdentify);
            if (ptJupyterJobBO != null) {
                PtJupyterJobVO vo = trainJobApi.create(ptJupyterJobBO);
                //????????????????????????
                String statusDetailKey = ServingStatusDetailDescUtil.getServingStatusDetailKey(ServingStatusDetailDescUtil.DEPLOYMENT_OF_BATCH_SERVICE_SINGLE_NODE_EXCEPTION, batchServing.getName());
                if (vo.isSuccess()) {
                    LogUtil.info(LogEnum.SERVING, "User {} deployed batch service with SUCCESS. Service name???{}", user.getUsername(), batchServing.getName());
                    batchServing.removeStatusDetail(statusDetailKey);
                    batchServingMapper.updateById(batchServing);
                    return;
                } else {
                    LogUtil.error(LogEnum.SERVING, "User {} failed deploying batch service. Service name ={}, error message: {}", user.getUsername(), batchServing.getName(), vo.getMessage());
                    batchServing.putStatusDetail(statusDetailKey, vo.getMessage());
                }

            }

        } else {
            //??????????????????
            DistributeTrainBO distributeTrainBO = buildDistributeTrainBO(user, batchServing, taskIdentify);
            if (distributeTrainBO != null) {
                BizDistributeTrain distribute = distributeTrainApi.create(distributeTrainBO);

                String statusDetailKey = ServingStatusDetailDescUtil.getServingStatusDetailKey(ServingStatusDetailDescUtil.DEPLOYMENT_OF_BATCH_SERVICE_MULTI_NODE_EXCEPTION, batchServing.getName());
                if (distribute.isSuccess()) {
                    LogUtil.info(LogEnum.SERVING, "User {} deployed batching service with SUCCESS. Service name???{}", user.getUsername(), batchServing.getName());
                    batchServing.removeStatusDetail(statusDetailKey);
                    batchServingMapper.updateById(batchServing);
                    return;
                } else {
                    LogUtil.error(LogEnum.SERVING, "User {} failed deploying batch service. Service name ={}, error message: {}", user.getUsername(), batchServing.getName(), distribute.getMessage());
                    batchServing.putStatusDetail(statusDetailKey, distribute.getMessage());
                }
            }
        }
        batchServing.setStatus(ServingStatusEnum.EXCEPTION.getStatus());
        batchServingMapper.updateById(batchServing);
        LogUtil.error(LogEnum.SERVING, "User {} FAILED to deploy batch service. Service name???{}", user.getUsername(), batchServing.getName());
    }

    /**
     * ????????????????????????
     *
     * @param user         ????????????
     * @param batchServing ??????????????????
     */
    @Async("servingExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatchServing(UserContext user, BatchServing batchServing, String resourceInfo) {
        try {
            String namespace = k8sNameTool.getNamespace(batchServing.getCreateUserId());
            String resourceName = k8sNameTool.generateResourceName(BizEnum.BATCH_SERVING, resourceInfo);
            if (batchServing.getResourcesPoolNode() == NumberConstant.NUMBER_1) {
                if (trainJobApi.delete(namespace, resourceName)) {
                    return;
                }
            } else {
                PtBaseResult result = distributeTrainApi.deleteByResourceName(namespace, resourceName);
                //????????????????????????
                String statusDetailKey = ServingStatusDetailDescUtil.getServingStatusDetailKey(ServingStatusDetailDescUtil.BULK_SERVICE_DELETE_EXCEPTION, batchServing.getName());
                if (ServingConstant.SUCCESS_CODE.equals(result.getCode())) {
                    batchServing.removeStatusDetail(statusDetailKey);
                    return;
                }
                batchServing.putStatusDetail(statusDetailKey, result.getMessage());
            }
        } catch (KubernetesClientException e) {
            batchServing.putStatusDetail(ServingStatusDetailDescUtil.getServingStatusDetailKey(ServingStatusDetailDescUtil.BULK_SERVICE_DELETE_EXCEPTION, batchServing.getName()), e.getMessage());
            LogUtil.error(LogEnum.SERVING, "An Exception occurred. BatchServing id={}, batchServing name:{}???exception :{}", batchServing.getId(), batchServing.getName(), e);
        }
        batchServing.setStatus(ServingStatusEnum.EXCEPTION.getStatus());
        batchServingMapper.updateById(batchServing);
        LogUtil.error(LogEnum.SERVING, "User {} FAILED to delete batch service. Service name???{}", user.getUsername(), batchServing.getName());
    }

    /**
     * ???????????????????????????
     *
     * @param user         ????????????
     * @param batchServing ??????????????????
     * @return PtJupyterJobBO ?????????????????????
     */
    public PtJupyterJobBO buildJobBo(UserContext user, BatchServing batchServing, String taskIdentify) {
        String platform = ServingFrameTypeEnum.getFrameName(batchServing.getFrameType());
        if (batchServing.getUseScript()) {
            platform = SymbolConstant.BLANK;
        }
        String command = String.format(ServingConstant.BATCH_COMMAND, ServingConstant.DUBHE_SERVING_PATH, platform, ServingConstant.MODEL_PATH,
                ServingConstant.INPUT_PATH, ServingConstant.OUTPUT_PATH, FALSE, ResourcesPoolTypeEnum.isGpuCode(batchServing.getResourcesPoolType()) ? TRUE : FALSE, batchServing.getUseScript() ? TRUE : FALSE) + batchServing.getDeployParam();
        String resourceInfo = StringUtils.getRandomString() + batchServing.getId();
        batchServing.setResourceInfo(resourceInfo);
        String targetPath = k8sNameTool.getAbsolutePath(ServingConstant.BATCH_ROOT_PATH + batchServing.getCreateUserId() + File.separator + batchServing.getId() + File.separator);
        String servingPath = getServingSourcePath(user, targetPath, batchServing.getUseScript(), batchServing.getScriptPath());
        PtJupyterJobBO bo = new PtJupyterJobBO()
                .setNamespace(k8sNameTool.getNamespace(batchServing.getCreateUserId()))
                .setName(k8sNameTool.generateResourceName(BizEnum.BATCH_SERVING, batchServing.getResourceInfo()))
                .setCpuNum(batchServing.getCpuNum())
                .setGpuNum(batchServing.getGpuNum())
                .setUseGpu(ResourcesPoolTypeEnum.isGpuCode(batchServing.getResourcesPoolType()))
                .setMemNum(batchServing.getMemNum())
                .setCmdLines(Arrays.asList("-c", command))
                .setFsMounts(new HashMap<String, PtMountDirBO>(NumberConstant.NUMBER_6) {{
                    put(ServingConstant.MODEL_PATH, new PtMountDirBO(k8sNameTool.getAbsolutePath(batchServing.getModelAddress())));
                    put(ServingConstant.INPUT_PATH, new PtMountDirBO(k8sNameTool.getAbsolutePath(batchServing.getInputPath())));
                    put(ServingConstant.OUTPUT_PATH, new PtMountDirBO(k8sNameTool.getAbsolutePath(batchServing.getOutputPath())));
                    put(ServingConstant.DUBHE_SERVING_PATH, new PtMountDirBO(servingPath));
                }})
                .setImage(batchServing.getImage())
                .setBusinessLabel(k8sNameTool.getPodLabel(BizEnum.BATCH_SERVING))
                .setTaskIdentifyLabel(taskIdentify);
        return bo;
    }

    /**
     * ???????????????????????????
     *
     * @param user         ????????????
     * @param batchServing ??????????????????
     * @return DistributeTrainBO ?????????????????????
     */
    public DistributeTrainBO buildDistributeTrainBO(UserContext user, BatchServing batchServing, String taskIdentify) {
        String platform = ServingFrameTypeEnum.getFrameName(batchServing.getFrameType());
        if (batchServing.getUseScript()) {
            platform = SymbolConstant.BLANK;
        }
        String command = String.format(ServingConstant.BATCH_COMMAND, ServingConstant.DUBHE_SERVING_PATH, platform, ServingConstant.MODEL_PATH, ServingConstant.INPUT_PATH,
                ServingConstant.OUTPUT_PATH, TRUE, ResourcesPoolTypeEnum.isGpuCode(batchServing.getResourcesPoolType()) ? TRUE : FALSE,
                batchServing.getUseScript() ? TRUE : FALSE) + batchServing.getDeployParam();
        String resourceInfo = StringUtils.getRandomString() + batchServing.getId();
        batchServing.setResourceInfo(resourceInfo);
        String targetPath = k8sNameTool.getAbsolutePath(ServingConstant.BATCH_ROOT_PATH + batchServing.getCreateUserId() + File.separator + batchServing.getId() + File.separator);
        String servingPath = getServingSourcePath(user, targetPath, batchServing.getUseScript(), batchServing.getScriptPath());
        if (StringUtils.isBlank(servingPath)) {
            return null;
        }
        DistributeTrainBO bo = new DistributeTrainBO()
                .setName(k8sNameTool.generateResourceName(BizEnum.BATCH_SERVING, batchServing.getResourceInfo()))
                .setNamespace(k8sNameTool.getNamespace(batchServing.getCreateUserId()))
                .setSize(batchServing.getResourcesPoolNode())
                .setImage(batchServing.getImage())
                .setMemNum(batchServing.getMemNum())
                .setCpuNum(batchServing.getCpuNum())
                .setGpuNum(batchServing.getGpuNum())
                .setMasterCmd(command)
                .setSlaveCmd(command)
                .setBusinessLabel(k8sNameTool.getPodLabel(BizEnum.BATCH_SERVING))
                .setFsMounts(new HashMap<String, PtMountDirBO>(NumberConstant.NUMBER_6) {{
                    put(ServingConstant.MODEL_PATH, new PtMountDirBO(k8sNameTool.getAbsolutePath(batchServing.getModelAddress())));
                    put(ServingConstant.INPUT_PATH, new PtMountDirBO(k8sNameTool.getAbsolutePath(batchServing.getInputPath())));
                    put(ServingConstant.OUTPUT_PATH, new PtMountDirBO(k8sNameTool.getAbsolutePath(batchServing.getOutputPath())));
                    put(ServingConstant.DUBHE_SERVING_PATH, new PtMountDirBO(servingPath));
                }})
                .setTaskIdentifyLabel(taskIdentify);
        return bo;
    }

    /**
     * ????????????????????????????????????????????????
     *
     * @param receiverMailAddress ????????????
     * @param id                  ????????????ID
     */
    @Async("servingExecutor")
    public void asyncSendServingMail(String receiverMailAddress, Long id) {
        try {
            final StringBuffer sb = new StringBuffer();
            sb.append("<h2>" + "?????????").append(receiverMailAddress).append("?????????</h2>");
            sb.append("<p style='text-align: center; font-size: 24px; font-weight: bold'>ID??????" + id + "????????????????????????????????????????????????</p>");
            mailClient.sendHtmlMail(receiverMailAddress, "??????????????????", sb.toString());
        } catch (Exception e) {
            LogUtil.error(LogEnum.SERVING, "UserServiceImpl sendMail error , param:{} error:{}", receiverMailAddress, e);
            throw new BusinessException(ServingErrorEnum.ERROR_SYSTEM);
        }
    }

    /**
     * ??????????????????
     *
     * @param sourcePath ????????????
     * @return String ????????????
     */
    private String getSourceName(String sourcePath) {
        if (StringUtils.isBlank(sourcePath)) {
            LogUtil.error(LogEnum.SERVING, "The serving source path is null");
        }
        int index = sourcePath.lastIndexOf(File.separator);
        return sourcePath.substring(index + 1);
    }
}
