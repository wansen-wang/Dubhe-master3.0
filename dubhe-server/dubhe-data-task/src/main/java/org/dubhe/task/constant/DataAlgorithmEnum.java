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

package org.dubhe.task.constant;

import lombok.Getter;
import org.dubhe.task.execute.impl.*;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

@Getter
public enum DataAlgorithmEnum {

    STANDARD_ANNOTATION(101, AnnotationQueueExecute.class, "标注标注"),
    TEXT_CLASSIFICATION(102, TextClassificationQueueExecute.class,"文本分类"),
    TRACK(103, TrackQueueExecute.class,"目标跟踪"),
    ENHANCE(104, EnhanceQueueExecute.class,"数据增强"),
    MEDICINE_ANNOTATION(105,MedicineAnnotataionQueueExecute.class,"医学标注"),
    OF_RECORD(106,OfRecordQueueExecute.class,"ofrecord转换"),
    VIDEO_SAMPLE(107,VideoSampleQueueExecute.class,"视频抽帧");

    private int algorithmType;

    private Class<?> className;

    private String desc;

    private static final Map<Integer, DataAlgorithmEnum> map = new HashMap<>();

    static{
        for (DataAlgorithmEnum dataAlgorithmEnum: EnumSet.allOf(DataAlgorithmEnum.class)){
            map.put(dataAlgorithmEnum.getAlgorithmType(),dataAlgorithmEnum);
        }
    }

    DataAlgorithmEnum(int algorithmType,Class clazz,String desc){
        this.algorithmType = algorithmType;
        this.className = clazz;
        this.desc = desc;
    }

    public static DataAlgorithmEnum getType(int algorithmType){
        return map.get(algorithmType);
    }
}
