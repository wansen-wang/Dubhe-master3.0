# !/usr/bin/env python
# -*- coding:utf-8 -*-

"""
Copyright 2020 Tianshu AI Platform. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
=============================================================
"""

getTaskLua = """
local namespace = KEYS[1]
local pre_task_queue_name = "pre_task"
local detail_task_queue_name = "detail"
local processing_task_queue_name = "processing"
local pre_task_queue = redis.call("get", namespace..":"..pre_task_queue_name)
local time = redis.call('TIME')
local time_reset = (time[1]*1000000+time[2])/1000
local current_task_queue
local keys = redis.call("keys",namespace..":*:*:task:*")

-- 定义分割字符串函数
local __split
function __split(str, reps)
    local r = {}
    if (str == nil) then
        return nil
    end
    string.gsub(str, "[^"..reps.."]+", function(w) table.insert(r,w) end)
    return r
end
-- 定义分割字符串函数

if (pre_task_queue == false and #keys > 0) then
    redis.call("set", namespace..":"..pre_task_queue_name, 1)
    current_task_queue = keys[1]
else
    for i=1,#keys,1
    do
        if i == pre_task_queue then
            if i < #keys then
                redis.call("set", namespace..":"..pre_task_queue_name, i+1)
                current_task_queue = keys[i+1]
            else
                redis.call("set", namespace..":"..pre_task_queue_name, 1)
                current_task_queue = keys[1]
            end
        end
    end
end

if (pre_task_queue ~= false and #keys > 0 and current_task_queue == nil) then
    redis.call("set", namespace..":"..pre_task_queue_name, 1)
    current_task_queue = keys[1]
end

if current_task_queue == nil then
    return nil
else
    local element = redis.call('zrangebyscore', current_task_queue, 0, 9999999999999, 'limit', 0, 1)
    redis.call("zrem", current_task_queue, element[1])
    element = string.gsub(element[1], "\\"", "")
    redis.call("zadd", KEYS[1]..":"..__split(current_task_queue, ":")[3]..":"..__split(current_task_queue, ":")[4]..":"..processing_task_queue_name..":"..__split(current_task_queue, ":")[6], time_reset, element)
    return KEYS[1]..":"..__split(current_task_queue, ":")[3]..":"..__split(current_task_queue, ":")[4]..":"..detail_task_queue_name..":"..element
end
"""

saveTaskLua= """
local __split
function __split(str, reps)
    local r = {}
    if (str == nil) then
        return nil
    end
    string.gsub(str, "[^"..reps.."]+", function(w) table.insert(r,w) end)
    return r
end
-- 定义分割字符串函数
local str_sub = __split(KEYS[1], ":")
local processing_task_queue_name = str_sub[1]..":"..str_sub[2]..":"..str_sub[3]..":"..str_sub[4]..":".."processing"..":001"
local finished_task_queue_name = str_sub[1]..":"..str_sub[2]..":"..str_sub[3]..":"..str_sub[4]..":".."finished"..":"..str_sub[6]
local annotation = str_sub[1]..":"..str_sub[2]..":"..str_sub[3]..":"..str_sub[4]..":".."annotation"..":"..str_sub[6]

redis.call("zrem", processing_task_queue_name, str_sub[6])
redis.call("zadd", finished_task_queue_name, 1, annotation)
redis.call("set", annotation, KEYS[2])
return annotation
"""