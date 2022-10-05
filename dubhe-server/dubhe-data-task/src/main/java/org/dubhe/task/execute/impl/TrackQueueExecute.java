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

package org.dubhe.task.execute.impl;

import com.alibaba.fastjson.JSONObject;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.statemachine.dto.StateChangeDTO;
import org.dubhe.data.domain.dto.AutoTrackCreateDTO;
import org.dubhe.data.domain.entity.Task;
import org.dubhe.data.machine.constant.DataStateMachineConstant;
import org.dubhe.data.machine.utils.StateMachineUtil;
import org.dubhe.data.service.AnnotationService;
import org.dubhe.data.service.TaskService;
import org.dubhe.task.execute.AbstractAlgorithmExecute;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@DependsOn("springContextHolder")
@Component
public class TrackQueueExecute extends AbstractAlgorithmExecute {

    @Autowired
    @Lazy
    private TaskService taskService;

    @Autowired
    private AnnotationService annotationService;

    @Override
    public void finishExecute(JSONObject taskDetail) {
        Long id = taskDetail.getLong("id");
        Task task = taskService.detail(id);
        Boolean flag = taskService.finishTask(id, 1);
        if (flag) {
            AutoTrackCreateDTO autoTrackCreateDTO = new AutoTrackCreateDTO();
            autoTrackCreateDTO.setCode(MagicNumConstant.TWO_HUNDRED);
            autoTrackCreateDTO.setData(null);
            autoTrackCreateDTO.setMsg("success");
            annotationService.finishAutoTrack(task.getDatasetId(), autoTrackCreateDTO);
        }
    }

    @Override
    public void failExecute(JSONObject failDetail) {
        Long id = failDetail.getLong("id");
        Task task = taskService.detail(id);
        Boolean flag = taskService.finishTask(id, MagicNumConstant.ONE);
        if (flag) {
            //嵌入状态机（目标跟踪中—>目标跟踪失败）
            StateMachineUtil.stateChange(new StateChangeDTO() {{
                setObjectParam(new Object[]{task.getDatasetId()});
                setEventMethodName(DataStateMachineConstant.DATA_AUTO_TRACK_FAIL_EVENT);
                setStateMachineType(DataStateMachineConstant.DATA_STATE_MACHINE);
            }});
        }
    }

    @Override
    public boolean checkStop(Object object, String queueName, JSONObject taskDetail) {
        Long taskId = taskDetail.getLong("id");
        return taskService.isStop(taskId);
    }
}
