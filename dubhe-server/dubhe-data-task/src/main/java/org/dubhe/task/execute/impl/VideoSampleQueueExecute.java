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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.commons.lang3.StringUtils;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.redis.utils.RedisUtils;
import org.dubhe.data.domain.entity.Task;
import org.dubhe.data.service.TaskService;
import org.dubhe.data.service.impl.FileServiceImpl;
import org.dubhe.task.execute.AbstractAlgorithmExecute;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@DependsOn("springContextHolder")
@Component
public class VideoSampleQueueExecute extends AbstractAlgorithmExecute {

    private boolean presentSegmentation = false;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private FileServiceImpl fileServiceImpl;

    @Autowired
    @Lazy
    private TaskService taskService;

    @Override
    public void finishExecute(JSONObject taskDetail) {
        JSONObject detailObject = JSON.parseObject(taskDetail.get("object").toString(),JSONObject.class);
        String datasetIdAndSub = detailObject.getString("datasetIdAndSub");
        List<String> pictureNames = JSON.parseObject(detailObject.getString("pictureNames"), ArrayList.class);
        Integer segment = Integer.valueOf(StringUtils.substringAfter(String.valueOf(datasetIdAndSub), ":"));
        QueryWrapper<Task> taskQueryWrapper = new QueryWrapper<>();
        taskQueryWrapper.lambda().eq(Task::getId, Long.valueOf(detailObject.getString("id")));
        Task task = taskService.selectOne(taskQueryWrapper);
        if (segment.equals(task.getFinished() + MagicNumConstant.ONE)) {
            fileServiceImpl.videSampleFinished(pictureNames, task);
            presentSegmentation = true;
            if (task.getFinished().equals(task.getTotal())) {
                task.setStatus(MagicNumConstant.THREE);
                taskService.updateByTaskId(task);
            }
        } else {
            presentSegmentation = false;
        }
    }

    @Override
    public void failExecute(JSONObject failDetail) {
        String datasetIdAndSub = failDetail.getString("datasetIdAndSub");
        fileServiceImpl.videoSampleFailed(datasetIdAndSub);
        QueryWrapper<Task> taskQueryWrapper = new QueryWrapper<>();
        taskQueryWrapper.lambda().eq(Task::getId, Long.valueOf(failDetail.getString("id")));
        Task task = taskService.selectOne(taskQueryWrapper);
        task.setStatus(MagicNumConstant.FOUR);
        taskService.updateByTaskId(task);
        presentSegmentation = true;
    }

    @Override
    public boolean checkStop(Object object, String queueName, JSONObject taskDetail) {
        Long taskId = taskDetail.getLong("id");
        return taskService.isStop(taskId);
    }

    @Override
    public void deleteRedisKey(Object object, String detailQueue) throws Exception{
        if(presentSegmentation) {
            super.deleteRedisKey(object, detailQueue);
        } else {
            redisUtils.zAdd(object.toString().replace("task","finished"), System.currentTimeMillis()/1000, ("\"" + object.toString() + "\"").getBytes("utf-8"));
        }
    }
}
