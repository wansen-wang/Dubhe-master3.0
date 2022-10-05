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

package org.dubhe.task.data;

import cn.hutool.core.util.ObjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.redis.utils.RedisUtils;
import org.dubhe.data.domain.entity.Task;
import org.dubhe.data.service.TaskService;
import org.dubhe.task.constant.TaskQueueNameEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.util.Arrays;

/**
 * @author 王伟
 * @date 2022年06月23日 15:42
 */
@Slf4j
@Component
public class StopTaskSchedule implements Runnable {

    @Autowired
    private TaskService taskService;

    @Autowired
    private RedisUtils redisUtils;

    @PostConstruct
    public void start() {
        Thread thread = new Thread(this, "停止任务处理");
        thread.start();
    }

    @Override
    public void run() {
        while(true) {
            try {
                // 获取一个需要停止的任务(状态是运行中，且停止状态为true的任务数据)
                Task task = taskService.getOneNeedStopTask();
                if (ObjectUtil.isNotNull(task)) {
                    String algorithmName = ObjectUtil.isNull(task.getModelServiceId())?TaskQueueNameEnum.getTaskNamespace(task.getType()):task.getModelServiceId().toString();
                    String key = String.format(TaskQueueNameEnum.STOP_KEY.getTemplate(), new Object[]{algorithmName, task.getDatasetId().toString(), task.getId().toString()});
                    redisUtils.del(key);
                    task.setStatus(MagicNumConstant.THREE);
                    taskService.updateByTaskId(task);
                } else {
                    Thread.sleep(3000);
                }
            } catch (Exception e) {
                LogUtil.error(LogEnum.BIZ_DATASET, "stop task error");
            }
        }
    }
}
