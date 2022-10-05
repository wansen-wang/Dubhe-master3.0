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
package org.dubhe.pointcloud.task;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.dubhe.biz.base.constant.NumberConstant;
import org.dubhe.biz.base.enums.BizEnum;
import org.dubhe.biz.base.enums.ResourcesPoolTypeEnum;
import org.dubhe.biz.base.utils.StringUtils;
import org.dubhe.biz.file.api.FileStoreApi;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.statemachine.dto.StateChangeDTO;
import org.dubhe.k8s.api.TrainJobApi;
import org.dubhe.k8s.domain.PtBaseResult;
import org.dubhe.k8s.domain.bo.PtJupyterJobBO;
import org.dubhe.k8s.domain.bo.PtMountDirBO;
import org.dubhe.k8s.domain.vo.PtJupyterJobVO;
import org.dubhe.k8s.utils.K8sNameTool;
import org.dubhe.pointcloud.common.Constant;
import org.dubhe.pointcloud.dao.PcDatasetMapper;
import org.dubhe.pointcloud.domain.dto.PcDatasetRunParamDTO;
import org.dubhe.pointcloud.domain.entity.PcDataset;
import org.dubhe.pointcloud.machine.constant.PcDatasetEventMachineConstant;
import org.dubhe.pointcloud.util.StateMachineUtil;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @description 异步任务调度
 * @date 2022-04-02
 */
@Component
public class DeployAsyncTask {


    @Resource
    private PcDatasetMapper pcDatasetMapper;

    @Resource
    private K8sNameTool k8sNameTool;


    @Resource(name = "hostFileStoreApiImpl")
    private FileStoreApi fileStoreApi;

    @Resource
    private TrainJobApi trainJobApi;

    @Async("pointCloudExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void deployPod(PcDatasetRunParamDTO pcDatasetRunParamDTO) {
        Map<String, String> map = new HashMap<>(NumberConstant.NUMBER_6);
        //extraLabelMap 需要匹配正则 [a-z0-9]([-a-z0-9]*[a-z0-9])?
        map.put("dataset-id", String.valueOf(pcDatasetRunParamDTO.getDatasetId()));

        //若首次运行，输出结果集路径不存在，需要先创建
        if (!fileStoreApi.fileOrDirIsExist(pcDatasetRunParamDTO.getResultsDir())) {
            boolean dirCreateResult = fileStoreApi.createDir(pcDatasetRunParamDTO.getResultsDir());
            LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} .The result of create result directory is :{}", pcDatasetRunParamDTO.getDatasetId(), dirCreateResult);
        }

        PtJupyterJobBO ptJupyterJobBO = new PtJupyterJobBO()
                .setNamespace(k8sNameTool.getNamespace(pcDatasetRunParamDTO.getCreateUserId()))
                .setName(k8sNameTool.generateResourceName(BizEnum.POINT_CLOUD, pcDatasetRunParamDTO.getResourceInfo()))
                .setGpuNum(pcDatasetRunParamDTO.getGpuNum())
                .setCpuNum(pcDatasetRunParamDTO.getCpuNum())
                .setUseGpu(ResourcesPoolTypeEnum.isGpuCode(pcDatasetRunParamDTO.getResourcesPoolType()))
                .setMemNum(pcDatasetRunParamDTO.getMemNum())
                .setCmdLines(Arrays.asList("-c", pcDatasetRunParamDTO.getCommand()))
                .setFsMounts(new HashMap<String, PtMountDirBO>(NumberConstant.NUMBER_6) {{
                    put(Constant.ALGORITHM_DIR_MOUNT, new PtMountDirBO(pcDatasetRunParamDTO.getAlgorithmDir()));
                    put(Constant.MODEL_DIR_MOUNT, new PtMountDirBO(pcDatasetRunParamDTO.getModelDir()));
                    put(Constant.DATASET_DIR_MOUNT, new PtMountDirBO(pcDatasetRunParamDTO.getDatasetDir()));
                    put(Constant.RESULTS_DIR_MOUNT, new PtMountDirBO(pcDatasetRunParamDTO.getResultsDir()));
                }})
                .setImage(pcDatasetRunParamDTO.getImage())
                .setBusinessLabel(k8sNameTool.getPodLabel(BizEnum.POINT_CLOUD))
                .setTaskIdentifyLabel(pcDatasetRunParamDTO.getTaskIdentify())
                .setExtraLabelMap(map);
        PtJupyterJobVO ptJupyterJobVO = trainJobApi.create(ptJupyterJobBO);

        if (ptJupyterJobVO.isSuccess()) {
            LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {}.Create job success,resource name :{}", pcDatasetRunParamDTO.getDatasetId(), ptJupyterJobBO.getName());
            //记录k8s资源信息，并记录 trial 实验开始时间
            pcDatasetMapper.update(null, new LambdaUpdateWrapper<PcDataset>()
                    .eq(PcDataset::getId, pcDatasetRunParamDTO.getDatasetId())
                    .set(PcDataset::getResourceName, ptJupyterJobBO.getName()));

            LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {}.Job result :{}", pcDatasetRunParamDTO.getDatasetId(), JSON.toJSONString(ptJupyterJobVO));
        } else {
            String statusDetail = StringUtils.putIntoJsonStringMap("启动失败", ptJupyterJobVO.getMessage(), null);
            StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{pcDatasetRunParamDTO.getDatasetId(), statusDetail}, PcDatasetEventMachineConstant.PC_DATASET_MACHINE,
                    PcDatasetEventMachineConstant.AUTO_LABEL_FAILED_PC_DATASET_EVENT));
            LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} .Failed to start job.Error message: {}", pcDatasetRunParamDTO.getDatasetId(), ptJupyterJobVO.getMessage());
        }

    }


    /**
     * @param pcDataset
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public PtBaseResult deleteJob(PcDataset pcDataset) {
        String namespace = k8sNameTool.getNamespace(pcDataset.getCreateUserId());
        String resourceName = pcDataset.getResourceName();
        Boolean result = false;
        //三次重试均反馈失败则给予删除失败结果
        int tryTime = 1;
        while (!result) {
            //重试三次
            if (tryTime <= 3) {
                result = trainJobApi.delete(namespace, resourceName);
                if (result) {
                    //删除任务调用成功
                    LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} .The dataset job began to delete.namespace:{},resource name :{}", pcDataset.getId(), namespace, resourceName);
                    return new PtBaseResult("200", "success to delete job");
                }
                tryTime++;
            } else {
                LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} .Failed to delete job.", pcDataset.getId());
                break;
            }
        }
        return new PtBaseResult("1000", "failed to delete job");
    }


}
