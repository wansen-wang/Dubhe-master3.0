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
import sys
import execute.execute as execute

logging.basicConfig(format='%(asctime)s - %(pathname)s[line:%(lineno)d] - %(levelname)s: %(message)s',
                    level=logging.DEBUG)

if __name__ == '__main__':
    """
       param:
       argv[1]: name 算法名称
       argv[2]: gpu true/false 默认为False
       argv[3]: redis_config 配置用英文的都好作为分隔符，分别为(ip,port,database,password)， 如果为空请输入空字符即可，比如127.0.0.1,6379,0,,
    """
    algorithm = sys.argv[1]
    gpu = sys.argv[2]
    redis_config = sys.argv[3]
    execute.start(algorithm, gpu==False, redis_config)
    