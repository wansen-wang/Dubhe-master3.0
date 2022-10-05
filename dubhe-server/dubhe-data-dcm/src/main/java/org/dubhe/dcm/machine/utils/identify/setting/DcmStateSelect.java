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

package org.dubhe.dcm.machine.utils.identify.setting;

import org.dubhe.biz.base.constant.NumberConstant;
import org.dubhe.data.machine.constant.FileStateCodeConstant;
import org.dubhe.data.machine.enums.DataStateEnum;
import org.dubhe.dcm.machine.constant.DcmFileStateCodeConstant;
import org.dubhe.dcm.machine.enums.DcmDataStateEnum;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;

/**
 * @description 状态判断类
 * @date 2020-09-24
 */
@Component
public class DcmStateSelect {


    /**
     * 手动标注中
     *
     * @param stateList     数据集下文件状态的并集
     * @return              数据集状态枚举
     */
    public DcmDataStateEnum isManualAnnotating(List<Integer> stateList) {
        if (stateList.size() > 1 && stateList.contains(DcmFileStateCodeConstant.NOT_ANNOTATION_FILE_STATE)) {
            return DcmDataStateEnum.ANNOTATION_DATA_STATE;
        }
        return stateList.contains(DcmFileStateCodeConstant.ANNOTATION_FILE_STATE) ? DcmDataStateEnum.ANNOTATION_DATA_STATE : null;
    }

    /**
     * 自动标注完成
     *
     * @param stateList     数据集下文件状态的并集
     * @return              数据集状态枚举
     */
    public DcmDataStateEnum isAutoFinished(List<Integer> stateList) {
        HashSet<Integer> states = new HashSet<Integer>() {{
            add(DcmFileStateCodeConstant.AUTO_ANNOTATION_COMPLETE_FILE_STATE);
            add(DcmFileStateCodeConstant.ANNOTATION_COMPLETE_FILE_STATE);
        }};
        switch (stateList.size()) {
            case NumberConstant.NUMBER_1:
                if (stateList.contains(DcmFileStateCodeConstant.AUTO_ANNOTATION_COMPLETE_FILE_STATE)){
                    return DcmDataStateEnum.AUTO_ANNOTATION_COMPLETE_STATE;
                }
                return null;
            case NumberConstant.NUMBER_2:
            case NumberConstant.NUMBER_3:
            case NumberConstant.NUMBER_4:
                for (Integer fileState : stateList) {
                    if (!states.contains(fileState)) {
                        return null;
                    };
                }
                return DcmDataStateEnum.AUTO_ANNOTATION_COMPLETE_STATE;
            default:
                return null;
        }
    }

    /**
     * 标注完成
     *
     * @param stateList     数据集下文件状态的并集
     * @return              数据集状态枚举
     */
    public DcmDataStateEnum isFinished(List<Integer> stateList) {
        return stateList.contains(DcmFileStateCodeConstant.ANNOTATION_COMPLETE_FILE_STATE)&&stateList.size()==NumberConstant.NUMBER_1 ?
                DcmDataStateEnum.ANNOTATION_COMPLETE_STATE : null;
    }

    /**
     * 未标注
     *
     * @param stateList     数据集下文件状态的并集
     * @return              数据集状态枚举
     */
    public DcmDataStateEnum isInit(List<Integer> stateList) {
        if (stateList.size() == NumberConstant.NUMBER_1 && stateList.contains(DcmFileStateCodeConstant.NOT_ANNOTATION_FILE_STATE)){
            return DcmDataStateEnum.NOT_ANNOTATION_STATE;
        }
        return null;
    }

}