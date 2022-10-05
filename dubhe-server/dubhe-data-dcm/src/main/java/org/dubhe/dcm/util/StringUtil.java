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

package org.dubhe.dcm.util;

import org.dubhe.dcm.constant.DcmConstant;

/**
 * @description 字符串处理工具类
 * @date 2020-11-11
 */
public class StringUtil {

    /**
     * 获取医学数据集存储路径
     *
     * @param dataMedicineId 医学数据集ID
     * @return
     */
    public static String getDcmUrl(Long dataMedicineId) {
        return String.format(DcmConstant.DCM_DATA_URL, dataMedicineId);
    }

}
