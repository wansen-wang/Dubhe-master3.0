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

package org.dubhe.data.machine.statemachine;

import lombok.Data;
import org.dubhe.biz.base.utils.SpringContextHolder;
import org.dubhe.biz.statemachine.exception.StateMachineException;
import org.dubhe.data.dao.AutoLabelModelServiceMapper;
import org.dubhe.data.domain.entity.AutoLabelModelService;
import org.dubhe.data.machine.state.AbstractModelState;
import org.dubhe.data.machine.state.specific.moel.ModelState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Data
@Component
public class ModelStateMachine extends AbstractModelState implements Serializable {

    @Autowired
    private ModelState modelState;

    @Autowired
    private AutoLabelModelServiceMapper modelMapper;

    private AbstractModelState memoryModelState;

    public AutoLabelModelService initMemoryDataState(Long primaryKeyId){
        if(primaryKeyId == null){
            throw new StateMachineException("未找到业务ID");
        }
        AutoLabelModelService modelService = modelMapper.selectById(primaryKeyId);
        if(modelService== null || modelService.getStatus()==null){
            throw new StateMachineException("未找到业务数据");
        }
        memoryModelState = SpringContextHolder.getBean("modelState");
        return modelService;
    }

    @Override
    public void startModel(Long primaryKeyId) {
        initMemoryDataState(primaryKeyId);
        memoryModelState.startModel(primaryKeyId);
    }

    @Override
    public void startModelFinish(Long primaryKeyId) {
        initMemoryDataState(primaryKeyId);
        memoryModelState.startModelFinish(primaryKeyId);
    }

    @Override
    public void startModelFail(Long primaryKeyId) {
        initMemoryDataState(primaryKeyId);
        memoryModelState.startModelFail(primaryKeyId);
    }

    @Override
    public void stopModel(Long primaryKeyId) {
        initMemoryDataState(primaryKeyId);
        memoryModelState.stopModel(primaryKeyId);
    }

    @Override
    public void stopModelFinish(Long primaryKeyId) {
        initMemoryDataState(primaryKeyId);
        memoryModelState.stopModelFinish(primaryKeyId);
    }
}
