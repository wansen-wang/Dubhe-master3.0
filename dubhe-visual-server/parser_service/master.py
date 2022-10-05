# -*- coding: UTF-8 -*-
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
import sys
sys.path.append('../service_utils')
from pathlib import Path
from utils.redis_utils import RedisInstance
from tbparser.log_parser import LogParser
from threading import Thread
import time
import json

def response(stateId, code, msg):
    # 通过redis消息队列通知当前状态
    s = json.dumps({'code': code, 'msg': msg})
    RedisInstance.lpush(stateId, s)

class Master:
    fileParsers = {}
    def __init__(self):
        RedisInstance.flushdb()

    def set_parser(self, uid, log_dir, cache_dir):
        # 日志路径不存在
        if not Path(log_dir).exists():
            return response(stateId='parser_statu' + uid,
                            code = 500,
                            msg = 'User does not exist or log path not found error: {}'.format(log_dir))

        # 若当前任务已经解析，则跳过
        if uid in self.fileParsers.keys():
            response(stateId='parser_statu' + uid,
                     code=200,
                     msg="User {} has already started".format(uid))
        else:
            response(stateId='parser_statu' + uid,
                     code=200,
                     msg='({}) starts successfully'.format(uid))

            parser = LogParser(uid, log_dir, cache_dir)
            parser.start()
            self.fileParsers[uid] = parser

        response(stateId='parser_statu' + uid,
                 code=200,
                 msg='({}) is finished'.format(uid))

    def kill_parser(self, uid):
        if uid in self.fileParsers.keys():
            parser = self.fileParsers.pop(uid)
            if parser.alive:
                parser.close()

    def run_server(self):
        while True:
            _, request = RedisInstance.brpop('sessions') #取出django的通知消息
            request = json.loads(request)
            if request['type'] == 'run':
                self.set_parser(uid = request['uid'],
                                log_dir = request['logdir'],
                                cache_dir = request['cachedir'])
            elif request['type'] == 'kill':
                self.kill_parser(uid=request['uid'])
            else:
                print('Unrecognized request')

def run():
    Master().run_server()

def cleanup(signum=None, frame=None):
    # 正常退出，触发每个parser的线程回收函数cleanup,清空所有的cache文件
    for parser in Master.fileParsers.values():
        parser.close()
    print('closing master ...')
    sys.exit()

if __name__ == '__main__':
    import signal
    # 为响应信号绑定触发函数
    signal.signal(signal.SIGINT, cleanup) # ctrl + c 退出
    signal.signal(signal.SIGTERM, cleanup) # kill pids 退出

    print("Master running...")
    p = Thread(target=run, daemon=True)
    p.start()

    while True:
        time.sleep(100)