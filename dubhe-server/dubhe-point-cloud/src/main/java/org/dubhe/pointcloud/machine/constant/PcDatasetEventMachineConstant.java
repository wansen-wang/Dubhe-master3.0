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
package org.dubhe.pointcloud.machine.constant;

/**
 * @description 状态机常量类
 * @date 2022-04-02
 */
public class PcDatasetEventMachineConstant {
    /**
     * 数据集状态机
     */
    private PcDatasetEventMachineConstant() {
    }

    public static final String PC_DATASET_MACHINE = "pcDatasetMachine";
    /**
     * 导入中事件
     */
    public static final String IMPORTING_PC_DATASET_EVENT = "importingPcDatasetEvent";
    /**
     * 未标注事件（文件上传成功）
     */
    public static final String UNLABELLED_PC_DATASET_EVENT = "unlabelledPcDatasetEvent";
    /**
     * 自动标注事件
     */
    public static final String AUTO_LABELING_PC_DATASET_EVENT = "autoLabelingPcDatasetEvent";
    /**
     * 自动标注停止事件
     */
    public static final String AUTO_LABEL_STOP_PC_DATASET_EVENT = "autoLabelStopPcDatasetEvent";
    /**
     * 自动标注失败事件
     */
    public static final String AUTO_LABEL_FAILED_PC_DATASET_EVENT = "autoLabelFailedPcDatasetEvent";
    /**
     * 自动标注完成事件
     */
    public static final String AUTO_LABEL_COMPLETE_PC_DATASET_EVENT = "autoLabelCompletePcDatasetEvent";
    /**
     * 标注中事件
     */
    public static final String LABELING_PC_DATASET_EVENT = "labelingPcDatasetEvent";
    /**
     * 已发布事件
     */
    public static final String PUBLISHED_PC_DATASET_EVENT = "publishedPcDatasetEvent";
    /**
     * 难例发布中事件
     */
    public static final String DIFFICULT_CASE_PUBLISHING_EVENT = "difficultCasePublishingEvent";
    /**
     * 难例事件发布失败事件
     */
    public static final String DIFFICULT_CASE_FAILED_TO_PUBLISH_EVENT = "difficultCaseFailedToPublishEvent";


}
