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
from .config_utils import ConfigInstance
from .redis_utils import RedisInstance
support = ["events", ".json"]

def id2logdir(uid, trainJobName):
    _base = Path(ConfigInstance.conf_logdir_base()).absolute()
    log_dir = _base / uid / trainJobName / "visualizedlog"
    try:
        int(uid)
    except ValueError:
        _base = Path("../demo_logs").absolute()
        log_dir = _base / uid / trainJobName

    cache_dir = Path("../__cache__").absolute() / uid / trainJobName
    return log_dir, cache_dir

def get_runinfo(logdir):
    """
    给定日志目录，返回有哪些run（文件夹）
    """
    def check(p):
        files = list(p.glob('*events*')) + list(p.glob('*.json'))
        return True if files else False

    p = Path(logdir)
    dirs = sorted(f for f in p.rglob('*') if f.is_dir())

    res = {}
    if check(p):
        res['.'] = p.absolute()

    for _dir in dirs:
        if check(_dir):
            res[_dir.name] = _dir.absolute()
    return res

def is_available_flie(filename):
    filename = Path(filename)
    if filename.is_file():
        for _s in support:
            if _s in filename.name:
                return True
    return False

def get_file_path(uid, run, type, tag):
    _key = uid + '_' + run + '_' + type + '_' + tag
    try:
        _res = RedisInstance.get(_key)
        return Path(_res)
    except TypeError:
        raise OSError('Redis key {} not found according to request '
                      'parameters, please check the parameters\n _path={}'
                      .format(_key, _res))

def path_parser(cache_path, run, category, tag):
    run = run if not (run == '.') else 'root'
    tag = tag.replace('/', '#').replace(':', '$')
    if isinstance(cache_path, str):
        cache_path = Path(cache_path)
    else:
        assert isinstance(cache_path, Path), \
            f"Parameter cache_path must be a instance of {Path.__name__}"
    file_path = cache_path / run / category / tag
    return file_path
