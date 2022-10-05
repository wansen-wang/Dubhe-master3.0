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
package org.dubhe.pointcloud.util;

import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * @description 获取文件路径
 * @date 2022-04-15
 **/
@Component
@Getter
public class PathUtil {

    public static final String DATASET_FILE_URL = "point-cloud/dataset/%s/";

    public static final String DATASET_LABEL2_URL = "point-cloud/dataset/%s/label_2";

    public static final String DATASET_LABEL2_TXT_URL = "point-cloud/dataset/%s/label_2/%s";

    public static final String POINT_CLOUD_PCD_URL = "point-cloud/pcd/%s";

    public static final String PCD_DATASET_FILE_URL = "point-cloud/pcd/%s/%s";

    public static final String MODEL_FILE_NAME = "point-cloud/model/%s";

    public static final String DATASET_FILE_FOLDER_URL = "point-cloud/dataset/%s/%s";

    /**
     * 获取标记文件
     *
     * @param datasetId
     * @param markFileName
     * @return
     */
    public String getDatasetLabel2FileUrl(Long datasetId, String markFileName) {
        return String.format(DATASET_LABEL2_TXT_URL, datasetId, markFileName);
    }

    /**
     * 获取pcd文件
     *
     * @param datasetId
     * @param fileName
     * @return
     */
    public String getPcdFileUrl(Long datasetId, String fileName) {
        return String.format(PCD_DATASET_FILE_URL, datasetId, fileName);
    }

    /**
     * 获取数据集label2路径
     *
     * @param datasetId
     * @return
     */
    public String getLabel2Url(Long datasetId) {
        return String.format(DATASET_LABEL2_URL, datasetId);
    }

    /**
     * 获取数据集文件路径
     *
     * @param datasetId
     * @return
     */
    public String getDatasetUrl(Long datasetId) {
        return String.format(DATASET_FILE_URL, datasetId);
    }

    /**
     * 获取模型文件名称
     * @param fileName
     * @return
     */
    public String getModelFile(String fileName) {
        return String.format(MODEL_FILE_NAME, fileName);
    }

    /**
     * 获取数据集下文件夹路径
     * @param datasetId
     * @param fileFolder
     * @return
     */
    public String getDatasetFileFolderUrl(Long datasetId, String fileFolder) {
        return String.format(DATASET_FILE_FOLDER_URL, datasetId, fileFolder);
    }
}
