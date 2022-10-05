# -*- coding: UTF-8 -*-
"""
 Copyright 2021 Tianshu AI Platform. All Rights Reserved.

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
import time
import threading
from pathlib import Path
from tbparser import SummaryReader
from utils.cache_io import CacheIO
from utils.logfile_utils import path_parser
from utils.redis_utils import RedisInstance
from tbparser.event_parser import filter_graph


class Trace_Thread(threading.Thread):
    def __init__(self, uid, runname, filename, cache_path, logs=None,
                 is_init=False, daemon=True):
        threading.Thread.__init__(self, name=filename.name)
        self.daemon = daemon
        self.uid = uid
        self.runname = runname
        self.cache_path = cache_path
        self.filename = filename
        self.logs = logs
        self.is_init = is_init
        self.redis_tags = []

    def set_redis_key(self, type, tag, file_path):
        _key = self.uid + '_' + self.runname + '_' + type + '_' + tag
        if _key not in self.redis_tags:
            RedisInstance.set(_key, str(file_path))
            self.redis_tags.append(_key)

    def run(self):
        print('监听文件 %s' % self.filename)
        self.trace()

    def trace(self):
        filename = Path(self.filename)
        if filename.suffix == ".json":
            with open(filename, "r") as f:
                # 结构图内容
                _cg_content = f.read()
                _sg_content = filter_graph(_cg_content)

                sg_file_path = path_parser(self.cache_path, self.runname,
                                           category="graph", tag="s_graph")

                CacheIO(sg_file_path).set_cache(data=_sg_content)
                self.set_redis_key(type='graph', tag='s_graph', file_path=sg_file_path)

            # 已完成graph文件解析，将完成标志放入队列
            if self.logs:
                self.logs.remove(self.filename)
            return

        # for event file
        if "event" in filename.name:
            fd = open(filename, "rb")
            while True:
                reader = SummaryReader(fd)
                for items in reader:
                    if items['type'] == "graph":
                        file_path = path_parser(self.cache_path, self.runname,
                                                items['type'], tag='c_graph')
                        CacheIO(file_path).set_cache(data=items['value'])
                        self.set_redis_key(type='graph', tag='c_graph', file_path=file_path)

                    elif items['type'] == "hparams":
                        file_path = path_parser(self.cache_path, self.runname,
                                                'hyperparm', tag='hparams')
                        CacheIO(file_path, mod='ab').set_cache(data=items['value'])
                        self.set_redis_key(type='hyperparm', tag='hparams', file_path=file_path)

                    else: # scalar, image, histogram, embedding
                        file_path = path_parser(self.cache_path, self.runname,
                                                items['type'], tag=items['tag'])
                        CacheIO(file_path).set_cache(data=items)
                        self.set_redis_key(type=items['type'], tag=items['tag'], file_path=file_path)


                # 已完成event文件解析，将完成标志放入队列
                if self.is_init and self.logs:
                    self.logs.remove(self.filename)
                    self.is_init = False

                # 文件读到末尾后睡眠几秒，然后继续解析文件，实现动态监听
                time.sleep(1)
