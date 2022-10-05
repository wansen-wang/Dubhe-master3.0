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

package org.dubhe.data.constant;

import cn.hutool.core.collection.CollectionUtil;
import lombok.Getter;
import org.dubhe.data.machine.constant.FileStateCodeConstant;
import org.dubhe.data.machine.enums.FileStateEnum;

import java.util.*;

/**
 * @description 用于映射前端文件筛选与后端文件状态
 * @date 2020-05-06
 */
@Getter
public enum FileTypeEnum {
    /**
     * 全部
     */
    All(0, "全部"),
    /**
     * 未标注
     */
    UNFINISHED(101, "未标注"),
    /**
     * 手动标注中
     */
    MANUAL_ANNOTATION(102, "手动标注中"),
    /**
     * 自动标注完成
     */
    AUTO_FINISHED(103, "自动标注完成"),
    /**
     * 已标注完成
     */
    FINISHED(104, "手动标注完成"),
    /**
     * 已标注完成
     */
    ANNOTATION_NOT_DISTINGUISH_FILE(105, "标注完成未识别"),
    /**
     * 自动目标跟踪完成
     */
    AUTO_TRACK_FINISHED(201, "自动目标跟踪完成"),

    /**
     * 未完成
     */
    UNFINISHED_FILE(301,"未完成"),

    /**
     * 已完成
     */
    FINISHED_FILE(302,"已完成"),

    /**
     * 有标注信息
     */
    HAVE_ANNOTATION(303,"有标注信息"),

    /**
     * 无标注信息
     */
    NO_ANNOTATION(304,"无标注信息"),
    /**
     * 手动标注
     */
    MANUAL_ANNOTATION_TYPE(401, "手动标注"),
    /**
     * 自动标注
     */
    AUTO_ANNOTATION_TYPE(402, "自动标注")
    ;

    static Set<Integer> ALL_STATUS = new HashSet<Integer>() {{
        addAll(FileStateEnum.getAllValue());
    }};

    static Set<Integer> UNFINISHED_STATUS = new HashSet<Integer>() {{
        add(FileStateCodeConstant.NOT_ANNOTATION_FILE_STATE);
    }};

    static Set<Integer> MANUAL_ANNOTATION_STATUS = new HashSet<Integer>() {{
        add(FileStateCodeConstant.MANUAL_ANNOTATION_FILE_STATE);
    }};

    static Set<Integer> AUTO_FINISHED_STATUS = new HashSet<Integer>() {{
        add(FileStateCodeConstant.AUTO_TAG_COMPLETE_FILE_STATE);
    }};

    static Set<Integer> FINISHED_STATUS = new HashSet<Integer>() {{
        add(FileStateCodeConstant.ANNOTATION_COMPLETE_FILE_STATE);
    }};

    static Set<Integer> ANNOTATION_NOT_DISTINGUISH_FILE_STATUS = new HashSet<Integer>() {{
        add(FileStateCodeConstant.ANNOTATION_NOT_DISTINGUISH_FILE_STATE);
    }};

    static Set<Integer> AUTO_TRACK_FINISHED_STATUS = new HashSet<Integer>() {{
        add(FileStateCodeConstant.TARGET_COMPLETE_FILE_STATE);
    }};

    /**
     * 未完成
     */
    static Set<Integer> UNFINISHED_FILE_STATUS = new HashSet<Integer>() {{
        add(FileStateCodeConstant.NOT_ANNOTATION_FILE_STATE);
        add(FileStateCodeConstant.ANNOTATION_NOT_DISTINGUISH_FILE_STATE);
    }};

    /**
     * 已完成
     */
    static Set<Integer> FINISHED_FILE_STATUS = new HashSet<Integer>() {{
        add(FileStateCodeConstant.AUTO_TAG_COMPLETE_FILE_STATE);
        add(FileStateCodeConstant.ANNOTATION_COMPLETE_FILE_STATE);
    }};

    /**
     * 有标注信息
     */
    static Set<Integer> HAVE_ANNOTATION_STATUS = new HashSet<Integer>() {{
        add(FileStateCodeConstant.MANUAL_ANNOTATION_FILE_STATE);
        add(FileStateCodeConstant.AUTO_TAG_COMPLETE_FILE_STATE);
        add(FileStateCodeConstant.ANNOTATION_COMPLETE_FILE_STATE);
        add(FileStateCodeConstant.TARGET_COMPLETE_FILE_STATE);
    }};

    /**
     * 无标注信息
     */
    static Set<Integer> NO_ANNOTATION_STATUS = new HashSet<Integer>() {{
        add(FileStateCodeConstant.NOT_ANNOTATION_FILE_STATE);
        add(FileStateCodeConstant.ANNOTATION_NOT_DISTINGUISH_FILE_STATE);
    }};

    /**
     * 手动标注
     */
    static Set<Integer> MANUAL_ANNOTATION_TYPE_STATUS = new HashSet<Integer>(){{
        add(FileStateCodeConstant.MANUAL_ANNOTATION_FILE_STATE);
        add(FileStateCodeConstant.ANNOTATION_COMPLETE_FILE_STATE);
    }};

    /**
     * 自动标注
     */
    static Set<Integer> AUTO_ANNOTATION_TYPE_STATUS = new HashSet<Integer>(){{
        add(FileStateCodeConstant.AUTO_TAG_COMPLETE_FILE_STATE);
        add(FileStateCodeConstant.TARGET_COMPLETE_FILE_STATE);
    }};

    private static final Map<Integer, Set<Integer>> TYPE_STATUS_MAP = new HashMap<Integer, Set<Integer>>() {{
        put(All.value, ALL_STATUS);
        put(UNFINISHED.value, UNFINISHED_STATUS);
        put(MANUAL_ANNOTATION.value, MANUAL_ANNOTATION_STATUS);
        put(AUTO_FINISHED.value, AUTO_FINISHED_STATUS);
        put(FINISHED.value, FINISHED_STATUS);
        put(ANNOTATION_NOT_DISTINGUISH_FILE.value, ANNOTATION_NOT_DISTINGUISH_FILE_STATUS);
        put(AUTO_TRACK_FINISHED.value, AUTO_TRACK_FINISHED_STATUS);
        put(UNFINISHED_FILE.value, UNFINISHED_FILE_STATUS);
        put(FINISHED_FILE.value, FINISHED_FILE_STATUS);
        put(HAVE_ANNOTATION.value, HAVE_ANNOTATION_STATUS);
        put(NO_ANNOTATION.value, NO_ANNOTATION_STATUS);
        put(MANUAL_ANNOTATION_TYPE.value, MANUAL_ANNOTATION_TYPE_STATUS);
        put(AUTO_ANNOTATION_TYPE.value, AUTO_ANNOTATION_TYPE_STATUS);
    }};

    FileTypeEnum(int value, String msg) {
        this.value = value;
        this.msg = msg;
    }

    private int value;
    private String msg;

    /**
     * 获取指定数据集状态下的文件状态列表
     *
     * @param type 文件类型
     * @return Set 符合条件的文件类型集合
     */
    public static Set<Integer> getStatus(Integer type) {
        Set<Integer> result = TYPE_STATUS_MAP.get(type);
        return CollectionUtil.isEmpty(result)?new HashSet<Integer>():result;
    }

    public static Set<Integer> getStatus(List<Integer> types) {
        Set<Integer> result = new HashSet<>();
        if (CollectionUtil.isNotEmpty(types)) {
            types.stream().forEach(integer -> {
                result.addAll(getStatus(integer));
            });
        }
        return result;
    }

    /**
     * 自动标注文件状态校验 用户web端接口调用时参数校验
     *
     * @param value 文件状态Integer值
     * @return      参数校验结果
     */
    public static boolean autoFileStatusIsValid(Integer value) {
        if (Arrays.asList(FileTypeEnum.All.value, FileTypeEnum.HAVE_ANNOTATION.value, FileTypeEnum.NO_ANNOTATION.value).contains(value)) {
            return true;
        }
        return false;
    }

}
