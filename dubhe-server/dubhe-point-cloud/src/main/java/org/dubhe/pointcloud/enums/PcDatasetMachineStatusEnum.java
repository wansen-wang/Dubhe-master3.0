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

import java.util.Objects;


/**
 * @description 状态机枚举类
 * @date 2022-04-02
 */
@Getter
public enum PcDatasetMachineStatusEnum {
    /**
     * 未采样
     */
    NOT_SAMPLED(1001, "pcDatasetNotSampledState", "未采样"),
    /**
     *导入中
     */
    IMPORTING(1002, "pcDatasetImportingState", "导入中"),
    /**
     * 未标注
     */
    UNLABELLED(1003, "pcDatasetUnlabelledState", "未标注"),
    /**
     * 自动标注中
     */
    AUTO_LABELING(1004, "pcDatasetAutoLabelingState", "自动标注中"),
    /**
     * 自动标注停止
     */
    AUTO_LABEL_STOP(1005, "pcDatasetAutoLabelStopState", "自动标注停止"),
    /**
     * 自动标注失败
     */
    AUTO_LABEL_FAILED(1006, "pcDatasetAutoLabelFailedState", "自动标注失败"),
    /**
     * 标注中
     */
    LABELING(1007, "pcDatasetLabelingState", "标注中"),
    /**
     * 自动标注完成
     */
    AUTO_LABEL_COMPLETE(1008, "pcDatasetAutoLabelCompleteState", "自动标注完成"),
    /**
     * 难例发布中
     */
    DIFFICULT_CASE_PUBLISHING(1009, "pcDatasetDifficultCasePublishingState", "难例发布中"),
    /**
     * 难例发布失败
     */
    DIFFICULT_CASE_FAILED_TO_PUBLISH(1010, "pcDatasetDifficultCaseFailedToPublishState", "难例发布失败"),
    /**
     * 已发布
     */
    PUBLISHED(1011, "pcDatasetPublishedState", "已发布"),
    ;

    private Integer code;
    private String statusMachine;
    private String desc;

    PcDatasetMachineStatusEnum(Integer code, String statusMachine, String desc) {
        this.code = code;
        this.statusMachine = statusMachine;
        this.desc = desc;
    }

    /**
     * 根据code获取状态值
     * @param code
     * @return
     */
    public static String getStatusMachine(Integer code) {
        if (!Objects.isNull(code)) {
            for (PcDatasetMachineStatusEnum pcDatasetMachineStatusEnum : PcDatasetMachineStatusEnum.values()) {
                if (pcDatasetMachineStatusEnum.code.equals(code)) {
                    return pcDatasetMachineStatusEnum.getStatusMachine();
                }
            }
        }
        return null;
    }

    /**
     *
     * @param code
     * @return
     */
    public static boolean checkCodeExist(Integer code) {
        if (!Objects.isNull(code)) {
            for (PcDatasetMachineStatusEnum pcDatasetMachineStatusEnum : PcDatasetMachineStatusEnum.values()) {
                if (pcDatasetMachineStatusEnum.code.equals(code)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String getDesc(Integer code) {
        if (!Objects.isNull(code)) {
            for (PcDatasetMachineStatusEnum pcDatasetMachineStatusEnum : PcDatasetMachineStatusEnum.values()) {
                if (pcDatasetMachineStatusEnum.code.equals(code)) {
                    return pcDatasetMachineStatusEnum.getDesc();
                }
            }
        }
        return null;
    }

}
