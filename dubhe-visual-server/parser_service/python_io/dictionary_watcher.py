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
from watchdog.observers.polling import PollingObserver
from watchdog.events import *
from python_io.logfile_loader import Trace_Thread
from utils.logfile_utils import *

class Watcher_Handler(FileSystemEventHandler):
    def __init__(self, uid, run, path, cache_path):
        self.uid = uid
        self.runname = run
        self.runpath = path
        self.cache_path = cache_path

    def on_created(self, event):
        filename = Path(event.src_path)
        if filename.is_file():
            print("创建文件 --> %s" % event.src_path)
            if is_available_flie(event.src_path):
                runname = self.runname if filename.parent == self.runpath else filename.parts[-2]
                Trace_Thread(self.uid, runname, filename, self.cache_path).start()
            else:
                print("非有效日志文件 %s" % filename.name)
        else:
            pass


def start_run_watcher(uid, run, path, cache_path):
    event_handler = Watcher_Handler(uid, run, path, cache_path)
    observer = PollingObserver()
    observer.schedule(event_handler, path, recursive=True)
    observer.start()
