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
package org.dubhe.pointcloud.enums;

import lombok.Getter;
import org.dubhe.biz.base.exception.ErrorCode;


/**
 * @description 异常枚举类
 * @date 2022-04-02
 */
@Getter
public enum ErrorEnum implements ErrorCode {

    /**
     * 内部错误
     */
    INTERNAL_SERVER_ERROR(1414, "内部错误"),
    /**
     * 标签组名称已存在
     */
    LABEL_GROUP_NAME_EXIST(1403, "标签组名称已存在"),
    /**
     * 标签名称重复
     */
    DUPLICATE_LABEL_NAME(1404, "标签名称重复"),
    /**
     *标签不存在
     */
    LABEL_DOES_NOT_EXIST(1405, "标签不存在"),
    /**
     * 标签组不存在
     */
    LABEL_GROUP_DOES_NOT_EXIST_ERROR(1406, "标签组不存在"),
    /**
     * 标签编号重复
     */
    LABEL_SEQ_REPEATED(1407, "标签编号重复"),
    /**
     * 数据集名称重复
     */
    DUPLICATE_DATASET_NAME(1504, "数据集名称重复"),
    /**
     * 数据集不存在
     */
    DATASET_DOES_NOT_EXIST_ERROR(1505, "数据集不存在"),
    /**
     * 当前操作不允许
     */
    OPERATION_NOT_ALLOWED(1506, "当前操作不允许"),
    /**
     * 自动标注中不能修改数据集
     */
    AUTO_LABELING_NOT_UPDATE_DATASETS(1507, "自动标注中不能修改数据集"),
    /**
     * 当前状态不允许修改标签组
     */
    OPERATION_LABEL_GROUP_NOT_ALLOWED_IN_STATE(1508, "当前状态不允许修改标签组"),
    /**
     * 镜像服务调用失败
     */
    CALL_IMAGE_SERVER_FAIL(1509, "镜像服务调用失败"),
    /**
     * 算法服务调用失败
     */
    CALL_ALGORITHM_SERVER_FAIL(1510, "算法服务调用失败"),
    /**
     * 模型不存在
     */
    MODEL_NOT_EXIST(1510, "模型不存在"),

    MODEL_FRAME_TYPE_NOT_SUPPORTED(1511, "平台暂不支持该框架模型部署");


    private Integer code;
    private String msg;

    ErrorEnum(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
