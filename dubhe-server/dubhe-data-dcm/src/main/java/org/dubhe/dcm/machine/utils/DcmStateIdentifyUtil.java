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

package org.dubhe.dcm.machine.utils;

import org.dubhe.data.domain.entity.Dataset;
import org.dubhe.data.machine.enums.DataStateEnum;
import org.dubhe.dcm.machine.enums.DcmDataStateEnum;
import org.dubhe.dcm.machine.utils.identify.data.DcmDataHub;
import org.dubhe.dcm.machine.utils.identify.setting.DcmStateIdentifySetting;
import org.dubhe.dcm.machine.utils.identify.setting.DcmStateSelect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * @description 状态判断实现类
 * @date 2020-09-24
 */
@Component
public class DcmStateIdentifyUtil {


    /**
     * 数据查询处理
     */
    @Autowired
    private DcmDataHub dataHub;

    /**
     * 状态判断类
     */
    @Autowired
    private DcmStateSelect stateSelect;

    /**
     * 状态判断中所有的自定义方法数组
     */
    private final Method[] method = ReflectionUtils.getDeclaredMethods(DcmStateSelect.class);

    /**
     * 获取数据集状态(指定版本)
     *
     * @param datasetId               数据集id
     * @param needFileStateDoIdentify 是否需要查询文件状态判断
     * @return DatasetStatusEnum      数据集状态(指定版本)
     */
    public DcmDataStateEnum getStatus(Long datasetId, boolean needFileStateDoIdentify) {
        return needFileStateDoIdentify ?
                new IdentifyDatasetStateByFileState(datasetId, DcmStateIdentifySetting.NEED_FILE_STATE_DO_IDENTIFY).getStatus()
                : dataHub.getDatasetStatus(datasetId);
    }

    /**
     * 获取数据集状态(未指定版本)
     *
     * @param dataset                 数据集
     * @param needFileStateDoIdentify 是否需要查询文件状态判断
     * @return DatasetStatusEnum      数据集状态(指定版本)
     */
    public DcmDataStateEnum getStatus(Dataset dataset, boolean needFileStateDoIdentify) {
        return needFileStateDoIdentify ?
                new IdentifyDatasetStateByFileState(dataset.getId(), DcmStateIdentifySetting.NEED_FILE_STATE_DO_IDENTIFY).getStatus()
                : dataHub.getDatasetStatus(dataset.getId());
    }

    /**
     * 获取数据集状态(自动标注/目标跟踪回滚使用)
     *
     * @param datasetId   数据集id
     * @return DatasetStatusEnum    数据集状态(指定版本)
     */
    public DcmDataStateEnum getStatusForRollback(Long datasetId) {
        return new IdentifyDatasetStateByFileState(datasetId, DcmStateIdentifySetting.ROLL_BACK_FOR_STATE).getStatus();
    }


    class IdentifyDatasetStateByFileState {

        /**
         * 判断得到的数据集状态
         */
        public DcmDataStateEnum state;

        /**
         * 会查询文件的状态去对数据集的状态做判断
         *
         * @param datasetId   数据集ID
         */
        public IdentifyDatasetStateByFileState(Long datasetId, Set<DcmDataStateEnum> dataStateEnums) {
            state = dataHub.getDatasetStatus(datasetId);
            if (dataStateEnums.contains(state)) {
                List<Integer> stateList = dataHub.getFileStatusListByDataset(datasetId);
                if (stateList == null || stateList.isEmpty()) {
                    state = DcmDataStateEnum.NOT_ANNOTATION_STATE;
                    return;
                }
                for (Method stateSelectMethod : method) {
                    state = (DcmDataStateEnum) ReflectionUtils.invokeMethod(stateSelectMethod, stateSelect, new Object[]{stateList});
                    if (state != null) {
                        return;
                    }
                }
            }
        }

        DcmDataStateEnum getStatus() {
            return this.state;
        }
    }
}