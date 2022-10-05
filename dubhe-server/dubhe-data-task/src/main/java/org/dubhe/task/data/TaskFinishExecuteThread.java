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
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.base.utils.SpringContextHolder;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.redis.utils.RedisUtils;
import org.dubhe.data.util.TaskUtils;
import org.dubhe.task.constant.DataAlgorithmEnum;
import org.dubhe.task.constant.TaskQueueNameEnum;
import org.dubhe.task.execute.AbstractAlgorithmExecute;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@Component
public class TaskFinishExecuteThread implements Runnable {

    @Autowired
    private TaskUtils taskUtils;

    @Autowired
    private RedisUtils redisUtils;

    private Object object;

    private String detailQueue;

    /**
     * 启动标注任务处理线程
     */
    @PostConstruct
    public void start() {
        Thread thread = new Thread(this, "任务完成任务处理队列");
        thread.start();
    }

    @Override
    public void run() {
        while (true) {
            try {
                finishExecute(true);
                finishExecute(false);
                TimeUnit.MILLISECONDS.sleep(MagicNumConstant.ONE_THOUSAND);
            } catch (Exception e) {
                LogUtil.error(LogEnum.BIZ_DATASET, "finish algorithm task failed:{}", e);
            }
        }
    }

    private void finishExecute(boolean ifFinish) {
        String queueName;
        if(ifFinish){
            queueName = TaskQueueNameEnum.getTemplate(TaskQueueNameEnum.FINISHED_TASK, TaskQueueNameEnum.TaskQueueConfigEnum.ALL);
        } else {
            queueName = TaskQueueNameEnum.getTemplate(TaskQueueNameEnum.FAILED_TASK, TaskQueueNameEnum.TaskQueueConfigEnum.ALL);
        }
        JSONObject detail = getDetail(queueName, ifFinish);
        if(detail != null){
            Integer algorithm = detail.getInteger("algorithm");
            AbstractAlgorithmExecute abstractAlgorithmExecute = (AbstractAlgorithmExecute) SpringContextHolder.getBean(DataAlgorithmEnum.getType(algorithm).getClassName());
            if(ifFinish){
                abstractAlgorithmExecute.finishMethod(object,detailQueue,detail);
            } else {
                abstractAlgorithmExecute.failMethod(object,detailQueue,detail);
            }
        }
    }

    private JSONObject getDetail(String queueName,boolean ifFinish){
        if(ifFinish){
            object = taskUtils.getFinishedTask(queueName);
        } else {
            object = taskUtils.getFailedTask(queueName);
        }
        if (ObjectUtil.isNotNull(object)) {
            JSONObject jsonObject = JSONObject.parseObject(JSON.toJSONString(redisUtils.get(object.toString())));
            //获取详情
            String objectString = object.toString();
            StringBuffer sb = new StringBuffer(objectString);
            detailQueue = sb.replace(objectString.lastIndexOf("annotation")
                    ,objectString.lastIndexOf("annotation")+"annotation".length(),"detail").toString();
            JSONObject taskDetail = JSON.parseObject(JSON.toJSONString(redisUtils.get(detailQueue)));
            if (taskDetail == null) {
                redisUtils.del(object.toString());
                return null;
            }
            taskDetail.put("object", jsonObject);
            return taskDetail;
        }
        return null;
    }
}
