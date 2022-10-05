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
package org.dubhe.pointcloud.common;

import java.util.regex.Pattern;

/**
 * @description 常量类
 * @date 2021-11-30
 **/
public class Constant {
    /**
     * 整数匹配
     */
    public static final Pattern PATTERN_NUM = Pattern.compile("^[-\\+]?[\\d]*$");

    public static final String DATASET_FILE_FOLDER = "dataset/";

    public static final String POINT_CLOUD_FILE_FOLDER = "point-cloud";

    public static final String POINT_CLOUD_FOLDER = "point-cloud/";

    public static final String LABEL_2_FILE_FOLDER = "label_2/";

    public static final String PCD_FILE_HEAD = "# .PCD v0.7 - Point Cloud Data file format\n" +
            "VERSION 0.7\n" +
            "FIELDS x y z i\n" +
            "SIZE 4 4 4 4\n" +
            "TYPE F F F F\n" +
            "COUNT 1 1 1 1\n" +
            "WIDTH LENGTH\n" +
            "HEIGHT 1\n" +
            "VIEWPOINT 0 0 0 1 0 0 0\n" +
            "POINTS LENGTH\n" +
            "DATA binary\n";

    public static final String LENGTH = "LENGTH";

    public static final String FOLDER_BIN = "bin/";

    public static final String FOLDER_PCD = "pcd/";

    public static final String FILE_SUFFIX_BIN = ".bin";

    public static final String FILE_SUFFIX_PCD = ".pcd";

    public static final String FILE_SUFFIX_TXT = ".txt";

    public static final String FILE_TYPE_BIN = "bin";

    public static final String FILE_TYPE_PCD = "pcd";

    public static final String FILE_TYPE_TXT = "txt";

    public static final String DATASET_DIR_MOUNT = "/dataset_dir";

    public static final String RESULTS_DIR_MOUNT = "/results_dir";

    public static final String ALGORITHM_DIR_MOUNT = "/algorithm_dir";

    public static final String MODEL_DIR_MOUNT = "/model_dir";

    public static final String DATASET_DIR = "dataset_dir";

    public static final String RESULTS_DIR = "results_dir";

    public static final String MODEL_DIR = "model_dir";

    public static final String DATASET_ID = "dataset-id";

    public static final String LOG_COMMAND = "%s --%s=/dataset_dir/ --%s=/results_dir/ --%s=/model_dir/";

    public static final String COMMAND = "echo 'annotation mission begins... %s \r\n'&& cd /algorithm_dir; %s && echo 'the annotation mission is over' ";

    public static final Integer MARK_STR_LENGTH_WITH_OBJ = 9;

    public static final Integer MARK_STR_LENGTH_WITHOUT_OBJ = 8;

    public static final Integer MARK_STR_OBJ_INDEX = 8;

    public static final Integer MARK_STR_LABEL_INDEX = 0;

    public static int LIMIT_NUMBER = 10000;

    public static int LABEL_GROUP_POINT_CLOUD_TYPE = 5;

    public static int INSERT_BATCH_NUM = 1000;

    public static int MINIO_COPY_NUM = 1000;

    public static String VELODYNE = "velodyne";


}
