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

import importlib
import os
import sys
import json
import time
import traceback
import common.util.public.logger_util as logger_util
logger = logger_util.get_logger("algorithm")
current_dir = os.path.dirname(os.path.abspath(__file__))

redis_module_name="common.util.public.RedisUtil"
gpu_model_name="common.util.public.select_gpu"
lua_script_module_name="execute.lua_script"
infernce_model_name="algorithm.%s.inference"
algorithm_parent_dir = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
def start(algorithm, gpu, redis_config):
    """
        启动程序，启动步骤
        1.判断是否有gpu，如果有，则需要选择GPU卡槽(为了解决多卡槽时，一个卡槽用完导致无法使用其他卡槽的问题)
        2.调用模型初始化方法
        3.初始化redis客户端
        4.获取任务
        5.调用推理接口
        6.保存结果
    """
    sys.path.insert(0, os.path.abspath(os.path.join(current_dir, ".." + os.sep + "algorithm" + os.sep + algorithm)))
    logger.debug("service start")
    if gpu:
        logger.debug("switch GPU")
        select_gpu()
    init_model(algorithm)
    logger.debug("start main loop")
    start_up()
    loop_count = 1
    while True:
        try:
            logger.debug("main loop %s count" % loop_count)
            start_time = time.time()
            redis_client = get_redis_client(redis_config)
            task = get_one_task(redis_client, "dataset:" + algorithm)
            if (task is not None):
                task = task.decode(encoding="utf-8").replace("\"", "")
                task_detail = get_task_detail(redis_client, task)
                result = infernce(json.loads(task_detail.decode(encoding="utf-8")), algorithm)
                logger.debug(result)
                save_result(redis_client, task, str(result))
                logger.debug("time consuming " + str(time.time() - start_time) + " second")
            else:
                logger.debug("No pending tasks are obtained, sleep [3] seconds")
                time.sleep(3)
            loop_count = loop_count + 1
        except Exception:
            traceback.print_exc()       

def select_gpu():
    """
        选择GPU处理
    """
    logger.debug("Service switch GPU card slot start")
    module = importlib.import_module(gpu_model_name)
    module.select_gpu()
    logger.debug("Service switch GPU card slot complete")


def init_model(algorithm):
    """
        模型初始化操作
    """
    logger.debug("Model initialization operation start")
    module = importlib.import_module(infernce_model_name % algorithm)
    module.load()
    logger.debug("Model initialization operation complete")


def get_redis_client(redis_config):
    """
        获取redis客户端
    """
    logger.debug("get redis client")
    redis_config = redis_config.split(",")
    module = importlib.import_module(redis_module_name)
    return module.getRedisConnection(redis_config[0], redis_config[1], redis_config[2], redis_config[3])


def get_one_task(redis_client, namespace):
    """
        获取一个待处理任务
    """
    logger.debug("Get pending task")
    module = importlib.import_module(lua_script_module_name)
    script = redis_client.register_script(module.getTaskLua)
    return script(keys=[namespace])


def infernce(task, algorithm):
    """
        调用推理接口
    """
    logger.debug("Call the inference interface for inference")
    module = importlib.import_module(infernce_model_name % algorithm)
    return module.inference(task)


def save_result(redis_client, task, result):
    """
        保存推理结果
    """
    logger.debug("save inference results")
    module = importlib.import_module(lua_script_module_name)
    script = redis_client.register_script(module.saveTaskLua)
    return script(keys=[task, result])


def get_task_detail(redis_client, task):
    """
        获取任务详情
    """
    logger.debug("Get task details")
    return redis_client.get(task)


def start_up():
    """
        启动成功(生成启动成功文件)
    """
    start_up_file = "/tmp/.startup"
    if not os.path.exists(start_up_file):
        os.mknod(start_up_file)
    