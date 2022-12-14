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
package org.dubhe.dcm.machine.utils.identify.data;

import org.dubhe.data.machine.enums.DataStateEnum;
import org.dubhe.dcm.machine.enums.DcmDataStateEnum;
import org.dubhe.dcm.service.DataMedicineFileService;
import org.dubhe.dcm.service.DataMedicineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @description 数据查询/加工
 * @date 2020-09-24
 */
@Component
public class DcmDataHub {

    /**
     * 数据集版本文件关系服务类
     */
    @Autowired
    private DataMedicineFileService medicineFileService;

    /**
     * 数据集服务类
     */
    @Autowired
    @Lazy
    private DataMedicineService medicineService;

    /**
     * 获取数据集的状态
     * @param datasetId 数据集ID
     * @return          数据集状态枚举
     */
    public DcmDataStateEnum getDatasetStatus(Long datasetId){
        return DcmDataStateEnum.getState(medicineService.getOneById(datasetId).getStatus());
    }

    /**
     * 获取数据集下文件的状态（数据经过去重处理）
     * @param datasetId     数据集ID
     * @return              数据集下文件状态的并集
     */
    public  List<Integer> getFileStatusListByDataset(Long datasetId) {
        return medicineFileService.getFileStatusListByDataset(datasetId);
    }

}
