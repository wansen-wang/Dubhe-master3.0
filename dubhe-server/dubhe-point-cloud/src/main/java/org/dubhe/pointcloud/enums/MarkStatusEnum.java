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

/**
 * @description 标注状态枚举类
 * @Date 2021-12-23
 **/
@Getter
public enum MarkStatusEnum {
    UN_MARK(101, "未标注"),
    AUTO_MARKED(102, "自动标注完成"),
    MANUAL_MARKING(103, "手动标注中"),
    MANUAL_MARKED(104, "手动标注完成");

    private Integer code;
    private String name;

    MarkStatusEnum(Integer code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 根据code获取name
     *
     * @param code
     * @return
     */
    public static String getMarkStatusName(Integer code) {
        if (code != null) {
            for (MarkStatusEnum markStatusEnum : MarkStatusEnum.values()) {
                if (markStatusEnum.getCode().equals(code)) {
                    return markStatusEnum.getName();
                }
            }
        }
        return null;
    }

    public static Integer getMarkStatusCode(String name) {
        if (name != null) {
            for (MarkStatusEnum markStatusEnum : MarkStatusEnum.values()) {
                if (markStatusEnum.getName().equals(name)) {
                    return markStatusEnum.getCode();
                }
            }
        }
        return null;
    }
}
