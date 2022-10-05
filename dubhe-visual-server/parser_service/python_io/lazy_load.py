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
from pathlib import Path
from typing import Union

from python_io.logfile_loader import Trace_Thread
from utils.logfile_utils import is_available_flie

class LazyLoad:
    def __init__(self, uid, run: str, rundir: Union[str, Path], run_logs: dict):
        self.uid = uid
        self.run = run
        self.rundir = rundir
        self.run_logs = run_logs

    # 惰性加载，在初始化的时候加载目前日志中的所有数据
    def init_load(self, cache_path, is_init=False):

        #查询当前文件夹的所有文件
        files = [f for f in self.rundir.glob("*") if is_available_flie(f)]
        self.run_logs[self.run] = set(files)

        # 构建线程间通信的队列
        for file in files:
            _thread = Trace_Thread(self.uid, self.run, file, cache_path, self.run_logs[self.run], is_init, daemon=True)
            _thread.start()
