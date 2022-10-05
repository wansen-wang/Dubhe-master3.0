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
from utils.logfile_utils import get_runinfo
from python_io.lazy_load import LazyLoad
from multiprocessing import Process
from pathlib import Path
from shutil import rmtree
from python_io.dictionary_watcher import start_run_watcher
from utils.cache_io import CacheIO
from utils.redis_utils import RedisInstance

class ParserWorker(Process):
    def __init__(self, uid, logdir, cachedir):
        super(ParserWorker, self).__init__()
        self.uid = uid
        self._logdir = logdir
        self._cachedir = cachedir

    def run(self):
        if not Path(self._logdir).exists():
            raise FileExistsError("No such dictionary {}".format(self._logdir))

        run_dirs = get_runinfo(self._logdir)
        # 开启监听当前解析的文件夹
        start_run_watcher(self.uid, '.', self._logdir, self._cachedir)

        # 解析日志
        print(f'({self._logdir}) starts to parse successfully')
        start_time = time.time()

        run_logs = {}
        for _run, _dir in run_dirs.items():
            LazyLoad(self.uid, _run, _dir, run_logs).init_load(self._cachedir, is_init=True)

        # 检查是否解析完成
        assert len(run_logs) == len(run_dirs)
        while len(run_logs) > 0:
            runs = list(run_logs.keys())
            for run in runs:
                if len(run_logs[run]) == 0:
                    run_logs.pop(run)

            if time.time() - start_time >= 30:
                return
            else:
                time.sleep(0.5)

class LogParser:
    def __init__(self, uid, logdir, cachedir):
        super(LogParser, self).__init__()
        self.uid = uid
        self.logdir = logdir
        self.cachedir = Path(cachedir).absolute()
        self.r = RedisInstance
        self.alive = False

    def start(self):
        self.alive = True

        # 记录日志解析后的本地缓存路径
        self.r.set(self.uid, str(self.cachedir))
        if self.cachedir.exists():
            rmtree(self.cachedir)

        self.worker = ParserWorker(self.uid, self.logdir, self.cachedir)
        self.worker.run()

    def close(self):
        self.alive = False
        print(f'({self.uid}) : clean up ... ')

        # 关闭解析进程
        if self.worker.is_alive():
            self.worker.terminate()

        # 关闭当前parser已打开的文件io
        files =  list(CacheIO.file_io.keys())
        for file in files:
            if str(self.cachedir) in str(file):
                CacheIO.file_io[file].close()
                CacheIO.file_io.pop(file)

        # 清除redis缓存
        for key in self.r.keys(self.uid + '*'):
            self.r.delete(key)

        # 清除缓存文件
        if self.cachedir.exists():
            rmtree(self.cachedir)
            try:
                # 尝试删除空的父目录，直至cache根目录
                parent_cache = self.cachedir.parent
                while '__cache__' in str(parent_cache):
                    parent_cache.rmdir()
                    parent_cache = parent_cache.parent
            except:
                pass
