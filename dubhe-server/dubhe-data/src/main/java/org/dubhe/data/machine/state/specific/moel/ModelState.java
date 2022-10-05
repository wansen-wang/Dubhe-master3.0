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

package org.dubhe.data.machine.state.specific.moel;

import org.dubhe.data.dao.AutoLabelModelServiceMapper;
import org.dubhe.data.machine.enums.ModelServiceStateEnum;
import org.dubhe.data.machine.state.AbstractModelState;
import org.dubhe.data.machine.statemachine.ModelStateMachine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class ModelState extends AbstractModelState {

    @Autowired
    @Lazy
    private ModelStateMachine modelStateMachine;

    @Autowired
    private AutoLabelModelServiceMapper modelMapper;

    @Override
    public void startModel(Long primaryKeyId) {
        modelMapper.updateStatus(primaryKeyId, ModelServiceStateEnum.STARTING.getCode());
        modelStateMachine.setMemoryModelState(modelStateMachine.getModelState());
    }

    @Override
    public void startModelFinish(Long primaryKeyId) {
        modelMapper.updateStatus(primaryKeyId, ModelServiceStateEnum.RUNNING.getCode());
        modelStateMachine.setMemoryModelState(modelStateMachine.getModelState());
    }

    @Override
    public void startModelFail(Long primaryKeyId) {
        modelMapper.updateStatus(primaryKeyId, ModelServiceStateEnum.START_FAILED.getCode());
        modelStateMachine.setMemoryModelState(modelStateMachine.getModelState());
    }

    @Override
    public void stopModel(Long primaryKeyId) {
        modelMapper.updateStatus(primaryKeyId, ModelServiceStateEnum.STOPING.getCode());
        modelStateMachine.setMemoryModelState(modelStateMachine.getModelState());
    }

    @Override
    public void stopModelFinish(Long primaryKeyId) {
        modelMapper.updateStatus(primaryKeyId, ModelServiceStateEnum.STOPED.getCode());
        modelStateMachine.setMemoryModelState(modelStateMachine.getModelState());
    }
}
