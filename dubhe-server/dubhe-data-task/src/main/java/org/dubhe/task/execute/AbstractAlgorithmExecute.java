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

package org.dubhe.task.execute;

import com.alibaba.fastjson.JSONObject;
import org.dubhe.biz.base.utils.SpringContextHolder;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.redis.utils.RedisUtils;

public abstract class AbstractAlgorithmExecute {

    private RedisUtils redisUtils;

    public AbstractAlgorithmExecute(){
        this.redisUtils = SpringContextHolder.getBean(RedisUtils.class);
    }

    public final void finishMethod(Object object, String queueName,JSONObject taskDetail){
        try{
            if(!checkStop(object, queueName,taskDetail)){
                finishExecute(taskDetail);
            }
            deleteRedisKey(object,queueName);
        } catch (Exception e){
            LogUtil.error(LogEnum.BIZ_DATASET, "execute finish task failed:{}", e);
        }
    }

    public final void failMethod(Object object, String queueName,JSONObject failDetail){
        try {
            if(!checkStop(object, queueName, failDetail)){
                failExecute(failDetail);
            }
            deleteRedisKey(object,queueName);
        } catch (Exception e){
            LogUtil.error(LogEnum.BIZ_DATASET, "execute failed task failed:{}", e);
        }
    }

    public abstract void finishExecute(JSONObject taskDetail);

    public void failExecute(JSONObject failDetail){
    }

    public boolean checkStop(Object object, String queueName,JSONObject taskDetail){
        return false;
    }

    public void deleteRedisKey(Object object,String detailQueue) throws Exception{
        redisUtils.del(object.toString());
        redisUtils.del(detailQueue);
    }
}
