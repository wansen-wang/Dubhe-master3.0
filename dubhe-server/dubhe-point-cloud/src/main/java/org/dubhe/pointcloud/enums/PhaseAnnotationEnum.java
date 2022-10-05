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
package org.dubhe.pointcloud.enums;


import lombok.Getter;
import org.dubhe.biz.base.utils.StringUtils;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.statemachine.dto.StateChangeDTO;
import org.dubhe.pointcloud.domain.dto.AnnotationK8sPodCallbackCreateDTO;
import org.dubhe.pointcloud.domain.entity.PcDataset;
import org.dubhe.pointcloud.machine.constant.PcDatasetEventMachineConstant;
import org.dubhe.pointcloud.util.StateMachineUtil;


/**
 * @description 容器状态枚举类
 * @date 2022-04-02
 */
@Getter
public enum PhaseAnnotationEnum {
    /**
     * 运行成功
     */
    SUCCESS_COMPLETE_ANNOTATION("succeeded", PcDatasetEventMachineConstant.AUTO_LABEL_COMPLETE_PC_DATASET_EVENT) {
        @Override
        public void machineStatusMethod(AnnotationK8sPodCallbackCreateDTO req, PcDataset pcDataset) {
            if (PcDatasetMachineStatusEnum.AUTO_LABEL_COMPLETE.getCode().equals(pcDataset.getStatus())) {
                LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {}.The dataset is completed.", pcDataset.getId());
                return;
            }
            //数据集自动标注运行成功，状态变更为自动标注完成
            StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{pcDataset.getId()}, PcDatasetEventMachineConstant.PC_DATASET_MACHINE,
                    PcDatasetEventMachineConstant.AUTO_LABEL_COMPLETE_PC_DATASET_EVENT));

        }
    },
    /**
     * 运行中
     */
    RUNNING_LABELING_ANNOTATION("running", PcDatasetEventMachineConstant.AUTO_LABELING_PC_DATASET_EVENT) {
        @Override
        public void machineStatusMethod(AnnotationK8sPodCallbackCreateDTO req, PcDataset pcDataset) {
            //数据集自动标注运行中，状态变更为自动标注中
            StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{pcDataset.getId()}, PcDatasetEventMachineConstant.PC_DATASET_MACHINE,
                    PcDatasetEventMachineConstant.AUTO_LABELING_PC_DATASET_EVENT));
        }
    },
    /**
     * 运行失败
     */
    FAILED_ANNOTATION("failed", PcDatasetEventMachineConstant.AUTO_LABEL_FAILED_PC_DATASET_EVENT) {
        @Override
        public void machineStatusMethod(AnnotationK8sPodCallbackCreateDTO req, PcDataset pcDataset) {
            //数据集自动标注运行失败，记录失败信息，并变更状态为自动标注失败
            String message = StringUtils.isNoneBlank(req.getMessages()) ? req.getMessages() : "算法异常";
            pcDataset.putStatusDetail(req.getPhase(), message);
            StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{pcDataset.getId(), pcDataset.getStatusDetail()}, PcDatasetEventMachineConstant.PC_DATASET_MACHINE,
                    PcDatasetEventMachineConstant.AUTO_LABEL_FAILED_PC_DATASET_EVENT));
        }
    },
    /**
     * 删除成功
     */
    DELETED_ANNOTATION("deleted", PcDatasetEventMachineConstant.AUTO_LABEL_FAILED_PC_DATASET_EVENT) {
        @Override
        public void machineStatusMethod(AnnotationK8sPodCallbackCreateDTO req, PcDataset pcDataset) {

            //若数据集状态为停止，则pod被删除后返回不需要变更为运行失败
            if (!PcDatasetMachineStatusEnum.AUTO_LABEL_STOP.getCode().equals(pcDataset.getStatus())) {
                //若其他状态下pod异常被删除，数据集状态变更为运行失败
                StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{pcDataset.getId(), pcDataset.getStatusDetail()}, PcDatasetEventMachineConstant.PC_DATASET_MACHINE,
                        PcDatasetEventMachineConstant.AUTO_LABEL_FAILED_PC_DATASET_EVENT));
            } else {
                LogUtil.info(LogEnum.POINT_CLOUD, "The dataset with id {}.The dataset status:{}", pcDataset.getId(), PcDatasetMachineStatusEnum.getDesc(pcDataset.getStatus()));
            }
        }
    },
    /**
     * 未知异常
     */
    UNKNOWN_ANNOTATION("unknown", PcDatasetEventMachineConstant.AUTO_LABEL_FAILED_PC_DATASET_EVENT) {
        @Override
        public void machineStatusMethod(AnnotationK8sPodCallbackCreateDTO req, PcDataset pcDataset) {
            //数据集自动标注运行失败，记录失败信息，并变更状态为自动标注失败
            String message = StringUtils.isNoneBlank(req.getMessages()) ? req.getMessages() : "pod未知异常";
            pcDataset.putStatusDetail(req.getPhase(), message);
            //数据集自动标注运行出现未知异常，变更状态为自动标注失败
            StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{pcDataset.getId(), pcDataset.getStatusDetail()}, PcDatasetEventMachineConstant.PC_DATASET_MACHINE,
                    PcDatasetEventMachineConstant.AUTO_LABEL_FAILED_PC_DATASET_EVENT));
        }
    },
    /**
     * 容器创建中
     */
    PENDING_ANNOTATION("pending", null) {
        @Override
        public void machineStatusMethod(AnnotationK8sPodCallbackCreateDTO req, PcDataset pcDataset) {
            //pending状态下没有不需要记录操作
        }
    };

    private String phase;
    private String datasetEvent;

    PhaseAnnotationEnum(String phase, String datasetEvent) {
        this.phase = phase;
        this.datasetEvent = datasetEvent;
    }


    public static PhaseAnnotationEnum getPhaseAnnotationEnum(String phase) {
        for (PhaseAnnotationEnum phaseAnnotationEnum : PhaseAnnotationEnum.values()) {
            if (phaseAnnotationEnum.getPhase().equals(phase)) {
                return phaseAnnotationEnum;
            }
        }
        return null;
    }

    public abstract void machineStatusMethod(AnnotationK8sPodCallbackCreateDTO req, PcDataset pcDataset);
}
