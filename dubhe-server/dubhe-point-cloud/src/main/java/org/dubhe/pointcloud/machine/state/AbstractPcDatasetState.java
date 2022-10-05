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
package org.dubhe.pointcloud.machine.state;


/**
 * @description 状态抽象类
 * @date 2022-04-02
 */
public abstract class AbstractPcDatasetState {

    /**
     * 导入中事件
     * @param datasetId 数据集id
     */
    public void importingPcDatasetEvent(Long datasetId) {
    }


    /**
     * 未标记事件
     * @param datasetId
     */
    public void unlabelledPcDatasetEvent(Long datasetId) {
    }


    /**
     * 上传已标注的数据集完成事件
     * @param datasetId
     */
    public void importPcDatasetWithLabelEvent(Long datasetId) {
    }


    /**
     * 标注中事件
     * @param datasetId
     */
    public void labelingPcDatasetEvent(Long datasetId) {
    }


    /**
     * 自动标注中事件
     * @param datasetId
     */
    public void autoLabelingPcDatasetEvent(Long datasetId) {
    }


    /**
     * 自动标注失败
     * @param datasetId
     * @param statusDetail
     */
    public void autoLabelFailedPcDatasetEvent(Long datasetId, String statusDetail) {
    }


    /**
     * 自动标注停止事件
     * @param datasetId
     */
    public void autoLabelStopPcDatasetEvent(Long datasetId) {
    }


    /**
     * 自动标注完成事件
     * @param datasetId
     */
    public void autoLabelCompletePcDatasetEvent(Long datasetId) {
    }


    /**
     * 已发布事件
     * @param datasetId
     */
    public void publishedPcDatasetEvent(Long datasetId) {
    }


    /**
     * 难例发布中事件
     */
    public void difficultCasePublishingEvent(Long datasetId) {
    }


    /**
     * 难例事件发布失败事件
     */
    public void difficultCaseFailedToPublishEvent(Long datasetId, String statusDetail) {
    }


    /**
     * 获取当前数据集状态
     * @return
     */
    public String currentStatus() {
        return null;
    }

}
