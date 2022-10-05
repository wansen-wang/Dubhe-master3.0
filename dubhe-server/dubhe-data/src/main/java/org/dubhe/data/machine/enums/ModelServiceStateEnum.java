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

package org.dubhe.data.machine.enums;

import org.dubhe.data.constant.AutoLabelModelServiceStatusEnum;

/**
 * 模型标注
 */
public enum ModelServiceStateEnum {

    STARTING(AutoLabelModelServiceStatusEnum.STARTING.getValue(), "startingModelServiceState","启动中"),

    RUNNING(AutoLabelModelServiceStatusEnum.RUNNING.getValue(), "runningModelServiceState", "运行中"),

    START_FAILED(AutoLabelModelServiceStatusEnum.START_FAILED.getValue(), "startFailedModelServiceState", "启动失败"),

    STOPING(AutoLabelModelServiceStatusEnum.STOPING.getValue(),"stopingModelServiceState", "停止中"),

    STOPED(AutoLabelModelServiceStatusEnum.STOPED.getValue(), "stopedModelServiceState","已停止");

    /**
     * 编码
     */
    private Integer code;
    /**
     * 状态机
     */
    private String stateMachine;
    /**
     * 描述
     */
    private String description;

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStateMachine() {
        return stateMachine;
    }

    public void setStateMachine(String stateMachine) {
        this.stateMachine = stateMachine;
    }

    ModelServiceStateEnum(Integer code, String stateMachine , String description) {
        this.code = code;
        this.stateMachine = stateMachine;
        this.description = description;
    }
}
