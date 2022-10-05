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

import logging
import os
import datetime
from logging import handlers
level_relations = {
        'debug':logging.DEBUG,
        'info':logging.INFO,
        'warning':logging.WARNING,
        'error':logging.ERROR,
        'crit':logging.CRITICAL
    }

fmt='%(asctime)s - %(pathname)s[line:%(lineno)d] - %(levelname)s: %(message)s'
def get_logger(module_name):
    fname = 'log_{}.log'.format(datetime.datetime.now().strftime("%Y-%m-%d"))
    ofpath = os.path.join('./log', fname)
    level = "debug"
    logger = logging.getLogger(module_name)
    th = handlers.TimedRotatingFileHandler(filename=ofpath,when='MIDNIGHT',backupCount=7,encoding='utf-8')
    format_str = logging.Formatter(fmt)
    logger.setLevel(level_relations.get(level))
    th.setFormatter(format_str)
    logger.addHandler(th)
    return logger
