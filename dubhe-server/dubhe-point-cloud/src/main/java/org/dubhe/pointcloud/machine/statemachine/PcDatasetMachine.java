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
package org.dubhe.pointcloud.machine.statemachine;


import lombok.Data;
import org.dubhe.biz.base.utils.SpringContextHolder;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.statemachine.exception.StateMachineException;
import org.dubhe.pointcloud.domain.entity.PcDataset;
import org.dubhe.pointcloud.enums.PcDatasetMachineStatusEnum;
import org.dubhe.pointcloud.machine.state.AbstractPcDatasetState;
import org.dubhe.pointcloud.machine.state.specific.PcDatasetAutoLabelCompleteState;
import org.dubhe.pointcloud.machine.state.specific.PcDatasetAutoLabelFailedState;
import org.dubhe.pointcloud.machine.state.specific.PcDatasetAutoLabelStopState;
import org.dubhe.pointcloud.machine.state.specific.PcDatasetAutoLabelingState;
import org.dubhe.pointcloud.machine.state.specific.PcDatasetDifficultCaseFailedToPublishState;
import org.dubhe.pointcloud.machine.state.specific.PcDatasetDifficultCasePublishingState;
import org.dubhe.pointcloud.machine.state.specific.PcDatasetImportingState;
import org.dubhe.pointcloud.machine.state.specific.PcDatasetLabelingState;
import org.dubhe.pointcloud.machine.state.specific.PcDatasetNotSampledState;
import org.dubhe.pointcloud.machine.state.specific.PcDatasetPublishedState;
import org.dubhe.pointcloud.machine.state.specific.PcDatasetUnlabelledState;
import org.dubhe.pointcloud.service.PcDatasetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;


/**
 * @description 点云数据集状态机
 * @date 2022-04-02
 */
@Data
@Component
public class PcDatasetMachine extends AbstractPcDatasetState implements Serializable {

    /**
     * 内存中的状态机
     */
    private AbstractPcDatasetState memoryPcDatasetState;

    @Autowired
    private PcDatasetService pcDatasetService;

    @Autowired
    private PcDatasetNotSampledState pcDatasetNotSampledState;

    @Autowired
    private PcDatasetImportingState pcDatasetImportingState;

    @Autowired
    private PcDatasetUnlabelledState pcDatasetUnlabelledState;

    @Autowired
    private PcDatasetAutoLabelingState pcDatasetAutoLabelingState;

    @Autowired
    private PcDatasetLabelingState pcDatasetLabelingState;

    @Autowired
    private PcDatasetDifficultCaseFailedToPublishState pcDatasetDifficultCaseFailedToPublishState;

    @Autowired
    private PcDatasetDifficultCasePublishingState pcDatasetDifficultCasePublishingState;

    @Autowired
    private PcDatasetAutoLabelStopState pcDatasetAutoLabelStopState;

    @Autowired
    private PcDatasetAutoLabelFailedState pcDatasetAutoLabelFailedState;

    @Autowired
    private PcDatasetAutoLabelCompleteState pcDatasetAutoLabelCompleteState;

    @Autowired
    private PcDatasetPublishedState pcDatasetPublishedState;


    /**
     * 初始化状态机的状态
     *
     * @param datasetId
     * @return PcDataset
     */
    public PcDataset initMemoryPcDatasetState(Long datasetId) {
        if (datasetId == null) {
            LogUtil.error(LogEnum.POINT_CLOUD, "Dataset id does not exist.");
            throw new StateMachineException("未找到数据集id");
        }
        PcDataset pcDataset = pcDatasetService.selectById(datasetId);
        if (pcDataset == null || pcDataset.getStatus() == null) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset with id {} is not found.", datasetId);
            throw new StateMachineException("未找到数据集");
        }
        memoryPcDatasetState = SpringContextHolder.getBean(PcDatasetMachineStatusEnum.getStatusMachine(pcDataset.getStatus()));
        return pcDataset;
    }


    @Override
    public void importingPcDatasetEvent(Long datasetId) {
        initMemoryPcDatasetState(datasetId);
        if (memoryPcDatasetState == pcDatasetImportingState) {
            LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} is currently being imported, no need to change.", datasetId);
            return;
        }
        if (memoryPcDatasetState != pcDatasetNotSampledState) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset with id {} is currently {} state and cannot be changed to being imported.", datasetId, memoryPcDatasetState.currentStatus());
            throw new StateMachineException("当前数据集状态非未采样状态，不能变更为导入中状态");
        }
        LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} is currently {}.", datasetId, memoryPcDatasetState.currentStatus());
        memoryPcDatasetState.importingPcDatasetEvent(datasetId);
    }

    @Override
    public void unlabelledPcDatasetEvent(Long datasetId) {
        initMemoryPcDatasetState(datasetId);
        if (memoryPcDatasetState == pcDatasetUnlabelledState) {
            LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} is currently unlabelled, no need to change.", datasetId);
            return;
        }
        if (memoryPcDatasetState != pcDatasetImportingState) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset with id {} is currently {} state and cannot be changed to an unlabelled state", datasetId, memoryPcDatasetState.currentStatus());
            throw new StateMachineException("当前数据集状态异常，不能变更为未标注状态");
        }
        LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} is currently {}.", datasetId, memoryPcDatasetState.currentStatus());
        memoryPcDatasetState.unlabelledPcDatasetEvent(datasetId);
    }

    @Override
    public void autoLabelingPcDatasetEvent(Long datasetId) {
        initMemoryPcDatasetState(datasetId);
        if (memoryPcDatasetState == pcDatasetAutoLabelingState) {
            LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} is currently auto labeling, no need to change.", datasetId);
            return;
        }
        if (memoryPcDatasetState == pcDatasetAutoLabelStopState ||
                memoryPcDatasetState == pcDatasetUnlabelledState ||
                memoryPcDatasetState == pcDatasetAutoLabelCompleteState ||
                memoryPcDatasetState == pcDatasetAutoLabelFailedState) {
            LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} is currently {}.", datasetId, memoryPcDatasetState.currentStatus());
            memoryPcDatasetState.autoLabelingPcDatasetEvent(datasetId);
        } else {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset with id {} is currently {} state and cannot be changed to an auto labelling state", datasetId, memoryPcDatasetState.currentStatus());
            throw new StateMachineException("当前数据集状态异常，不能变更为自动标注中状态");
        }

    }

    @Override
    public void autoLabelFailedPcDatasetEvent(Long datasetId, String statusDetail) {
        initMemoryPcDatasetState(datasetId);
        if (memoryPcDatasetState == pcDatasetAutoLabelFailedState) {
            LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} is currently failed, no need to change.", datasetId);
            return;
        }
        if (memoryPcDatasetState == pcDatasetAutoLabelingState) {
            LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} is currently {}.", datasetId, memoryPcDatasetState.currentStatus());
            memoryPcDatasetState.autoLabelFailedPcDatasetEvent(datasetId, statusDetail);
        } else {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset with id {} is currently {} state and cannot be changed to an auto failed state", datasetId, memoryPcDatasetState.currentStatus());
            throw new StateMachineException("当前数据集状态异常，不能变更为自动标注失败状态");
        }
    }

    @Override
    public void autoLabelCompletePcDatasetEvent(Long datasetId) {
        initMemoryPcDatasetState(datasetId);
        if (memoryPcDatasetState == pcDatasetAutoLabelCompleteState) {
            LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} is currently completed, no need to change.", datasetId);
            return;
        }
        if (memoryPcDatasetState == pcDatasetAutoLabelingState) {
            LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} is currently {}.", datasetId, memoryPcDatasetState.currentStatus());
            memoryPcDatasetState.autoLabelCompletePcDatasetEvent(datasetId);
        } else if (memoryPcDatasetState == pcDatasetImportingState) {
            LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} is currently {}.", datasetId, memoryPcDatasetState.currentStatus());
            memoryPcDatasetState.importPcDatasetWithLabelEvent(datasetId);
        } else {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset with id {} is currently {} state and cannot be changed to an auto label complete state", datasetId, memoryPcDatasetState.currentStatus());
            throw new StateMachineException("当前数据集状态异常，不能变更为自动标注完成的状态");
        }

    }

    @Override
    public void autoLabelStopPcDatasetEvent(Long datasetId) {
        initMemoryPcDatasetState(datasetId);
        if (memoryPcDatasetState == pcDatasetAutoLabelStopState) {
            LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} is currently auto stopping, no need to change.", datasetId);
            return;
        }
        if (memoryPcDatasetState != pcDatasetAutoLabelingState) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset with id {} is currently {} state and cannot be changed to an auto label stop state", datasetId, memoryPcDatasetState.currentStatus());
            throw new StateMachineException("当前数据集状态异常，不能变更为自动标注停止的状态");
        }
        LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} is currently {}.", datasetId, memoryPcDatasetState.currentStatus());
        memoryPcDatasetState.autoLabelStopPcDatasetEvent(datasetId);
    }

    @Override
    public void labelingPcDatasetEvent(Long datasetId) {
        initMemoryPcDatasetState(datasetId);
        if (memoryPcDatasetState == pcDatasetLabelingState) {
            LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} is currently labeling, no need to change.", datasetId);
            return;
        }
        if (memoryPcDatasetState == pcDatasetUnlabelledState ||
                memoryPcDatasetState == pcDatasetAutoLabelCompleteState ||
                memoryPcDatasetState == pcDatasetAutoLabelFailedState ||
                memoryPcDatasetState == pcDatasetAutoLabelStopState ||
                memoryPcDatasetState == pcDatasetDifficultCasePublishingState) {
            LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} is currently {}.", datasetId, memoryPcDatasetState.currentStatus());
            memoryPcDatasetState.labelingPcDatasetEvent(datasetId);
        } else {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset with id {} is currently {} state and cannot be changed to the status in the annotation", datasetId, memoryPcDatasetState.currentStatus());
            throw new StateMachineException("当前数据集状态异常，不能变更为标注中的状态");
        }
    }

    @Override
    public void difficultCasePublishingEvent(Long datasetId) {
        initMemoryPcDatasetState(datasetId);
        if (memoryPcDatasetState == pcDatasetDifficultCasePublishingState) {
            LogUtil.info(LogEnum.POINT_CLOUD, "The hard-to-label dataset with id {} is currently publishing, no need to change.", datasetId);
            return;
        }
        if (memoryPcDatasetState != pcDatasetLabelingState) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The hard-to-label dataset with id {} is currently {} state and cannot be changed to a publishing state", datasetId, memoryPcDatasetState.currentStatus());
            throw new StateMachineException("当前数据集状态异常，不能变更为难例发布中的状态");
        }
        LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} is currently {}.", datasetId, memoryPcDatasetState.currentStatus());
        memoryPcDatasetState.difficultCasePublishingEvent(datasetId);
    }

    @Override
    public void difficultCaseFailedToPublishEvent(Long datasetId, String statusDetail) {
        initMemoryPcDatasetState(datasetId);
        if (memoryPcDatasetState == pcDatasetDifficultCaseFailedToPublishState) {
            LogUtil.info(LogEnum.POINT_CLOUD, "The hard-to-label dataset with id {}  current failed to publish,need to change.", datasetId);
        }
        if (memoryPcDatasetState != pcDatasetDifficultCasePublishingState) {
            LogUtil.error(LogEnum.POINT_CLOUD, "The hard-to-label dataset with id {} currently {} state and cannot to be changed to a publishing state.", datasetId, memoryPcDatasetState.currentStatus());
        }
        LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} is currently {}.", datasetId, memoryPcDatasetState.currentStatus());
        memoryPcDatasetState.difficultCaseFailedToPublishEvent(datasetId, statusDetail);
    }

    @Override
    public void publishedPcDatasetEvent(Long datasetId) {
        initMemoryPcDatasetState(datasetId);
        if (memoryPcDatasetState == pcDatasetPublishedState) {
            LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} is currently published, no need to change.", datasetId);
            throw new StateMachineException("当前数据集已经是发布状态，不能继续发布");
        }
        if (memoryPcDatasetState == pcDatasetAutoLabelCompleteState
                || memoryPcDatasetState == pcDatasetLabelingState) {
            LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {} is currently {}.", datasetId, memoryPcDatasetState.currentStatus());
            memoryPcDatasetState.publishedPcDatasetEvent(datasetId);
        } else {
            LogUtil.error(LogEnum.POINT_CLOUD, "The dataset with id {} is currently {} state and cannot be changed to the published state", memoryPcDatasetState.currentStatus(), datasetId);
            throw new StateMachineException("当前数据集状态异常，不能变更为已发布的状态");
        }

    }

}
