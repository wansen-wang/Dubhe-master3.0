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
import re
import time
import copy
import urllib.parse
from pathlib import Path
from django.http import HttpResponseNotAllowed, HttpResponseBadRequest, \
    JsonResponse, HttpResponse
from utils.redis_utils import RedisInstance
import json
from utils.logfile_utils import id2logdir
from utils.config_utils import ConfigInstance


def validate_get_request(request, func, accept_params=None, args=None):
    """Check if method of request is GET and request params is legal

    Args:
         request: request data given by django
         func: function type, get request and return HTTP response
         accept_params: list type, acceptable parameter list
         args: value of parameters
    Returns:
         HTTP response
    """
    if accept_params is None:
        accept_params = []
    if args is None:
        args = []
    if request.method != 'GET':
        return HttpResponseNotAllowed(['GET'])
    elif set(accept_params).issubset(set(request.GET)):
        return func(request, *args)
    else:
        return HttpResponseBadRequest('parameter lost!')


def validate_post_request(request, func, accept_params=None, args=None):
    """Check if method of request is POST and request params is legal

    Args:
         request: request data given by django
         func: function type, get request and return HTTP response
         accept_params: list type, acceptable parameter list
         args: value of parameters
    Returns:
         HTTP response
    """
    if accept_params is None:
        accept_params = []
    if args is None:
        args = []
    if request.method != 'POST':
        return HttpResponseNotAllowed(['POST'])
    elif set(accept_params).issubset(set(request.POST)):
        return func(request, *args)
    else:
        return HttpResponseBadRequest('parameter lost!')

def is_init_finish(uid):
    try:
        ctime = time.time()
        # 获取当前解析状态
        _, response = RedisInstance.brpop('parser_statu' + uid, timeout=5)
        response = json.loads(response)
        if response['code'] == 200:
            # 等待解析结束，等待时间为30s
            if RedisInstance.brpop('parser_statu' + uid, timeout=30):
                print(time.time() - ctime)
            return True
        else:
            raise ValueError(response['msg'])

    except TypeError:
        raise ValueError('Parse service not responding')

def get_classified_label(tags):
    category = {}
    for tag in tags:
        # 使用斜杠分割类别
        i = tag.find('/')
        _cate_name = tag[0:] if i == -1 else tag[0:i]
        if _cate_name not in category.keys():
            category[_cate_name] = [tag]
        else:
            category[_cate_name].append(tag)
    return category


def process_category(path):
    runs = {}
    # 搜索 cache_path 下的所有文件
    for run in path.iterdir():
        if run.is_dir():
            key = run.name if not (run.name == 'root') else '.'
            runs[key] = get_component(run)
    merge_category(runs)
    return runs


def get_component(path):
    components = {'custom': ['true']}
    for component in path.iterdir():
        if component.is_dir():
            if component.name == 'hist':
                components['statistic'] = {'histogram': get_tags(component)}
            elif component.name == 'graph':
                components['graph'] = get_tags(component)
                if 'c_graph' in components['graph']:
                    components['graph'].append("s_graph")
            elif component.name == 'hyperparm':
                components['hyperparm'] = ['true']
            elif component.name == 'projector':
                components['embedding'] = get_tags(component)
            elif component.name in ['image', 'audio', 'text']:
                key = component.name
                if 'media' in components.keys():
                    components['media'][key] = get_tags(component)
                else:
                    components['media'] = {key: get_tags(component)}
            elif component.name == 'scalar':
                res = get_tags(component)
                components['scalar'] = get_classified_label(res)
            else:
                key = component.name
                components[key] = get_tags(component)
    return components


def get_tags(path):
    files = []
    for file in path.iterdir():
        if file.is_file() and not ('sample' in file.name):
            tag = file.name.replace('#', '/').replace('$', ':')
            files.append(tag)
    return sorted(files, key=lambda x: sort_func(x))


def sort_func(x):
    # 获取字符串中的字母
    res1 = re.findall(r'[A-Za-z]', x)
    # 获取字符串中的数字
    res2 = re.sub("\D", "", x)
    res2 = int(float(res2)) if res2 else 0
    return res1, res2


def get_init_data(request):
    params = ['id', 'trainJobName']
    uid, trainJobName = get_api_params(request, params)
    session_id = request.session.session_key
    if not session_id or request.session[session_id]!= uid:
        if session_id:
            del request.session[session_id]
        request.session.create()
        session_id = request.session.session_key

    request.session[session_id] = uid

    # 计算用户对应的日志文件路径和缓存路径
    logdir, cachedir = id2logdir(uid, trainJobName)
    unique_task = uid + '_' + trainJobName
    # 通过redis的消息队进行进程通信，告诉parser 开始解析任务
    msg = {
        "type": "run",
        "uid": unique_task,
        "logdir": str(logdir),
        "cachedir": str(cachedir)
    }
    RedisInstance.send_message(json.dumps(msg))

    # 等待解析结束
    if is_init_finish(unique_task):
        return {
                'msg': 'success',
                'session_id': session_id}

def get_category_data(request):
    params = ['uid', 'trainJobName']
    uid, trainJobName = get_api_params(request, params)

    if RedisInstance.exist(uid):
        cache_path = Path(RedisInstance.get(uid))
        res = process_category(cache_path)
        return res
    else:
        raise ValueError('ID was not found')

def response_wrapper(fn):
    def inner(*args, **kwargs):
        try:
            res = fn(*args, **kwargs)
            _sid = args[0].session.session_key
            _session = args[0].session.get(_sid, '')
            _uid = _session if _session else args[0].GET.get('id')
            _job = args[0].GET.get('trainJobName')
            # 更新session过期时间
            if _uid and _job:
                t = 60 * ConfigInstance.conf_user_expiration_time()
                args[0].session.set_expiry(t)
                aus = _uid + '_' + _job
                RedisInstance.set(
                    aus + "_is_alive",
                    time.time()
                    + t
                )
            if not isinstance(res, HttpResponse):
                return JsonResponse({
                    'code': 200,
                    'msg': 'ok',
                    'data': res
                })
            return res
        except Exception as e:
            _tb = e.__traceback__
            _str_tb = ""
            while _tb:
                _st = "in {}, at line {} \n".format(_tb.tb_frame.f_globals["__file__"],
                                                    _tb.tb_lineno)
                _str_tb += _st
                _tb = _tb.tb_next
            if ConfigInstance.conf_warning_debug():
                msg = "{}: Trace: {}".format(str(e), _str_tb)
            else:
                msg = "请求参数错误或同时登录多个用户，请刷新页面重试"
            return JsonResponse({
                'code': 500,
                'msg': msg,
                'data': ""
            })

    return inner


def get_api_params(request, params):
    """
        解析request中的参数：id，trainJobName
    """
    res = {}
    for params_name in params:
        # 若参数名为’uid'，表示需要从session中获取，并与'trainJobName'字段拼接
        if params_name == 'uid':
            p = request.session[request.session.session_key]
        else:
            p = request.GET.get(params_name)

        if not p:
            msg = "{} :Parameter request error, parameter '{}' not found" \
                .format(request.path, params_name)
            raise ValueError(msg)
        else:
            res[params_name] = urllib.parse.unquote(p) # 转译url中的 %

    if 'uid' in res and 'trainJobName' in res:
        res['uid'] = res['uid'] + '_' + res['trainJobName']

    return [res[name] for name in params]


def merge_category(category):
    for i in category:
        if isinstance(category[i], dict):
            merge_category(category[i])
        if 'featuremap' in list(category.keys()):
            if isinstance(category['featuremap'], list):
                category['graph'].extend(category['featuremap'])
                del category['featuremap']
                break
            else:
                merge_category(category['featuremap'])
