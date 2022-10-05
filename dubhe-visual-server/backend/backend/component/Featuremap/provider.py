from utils.cache_io import CacheIO
from utils.logfile_utils import get_file_path
from backend.api.utils import get_api_params
from .feature_read import featuremap_read
import base64
import io
import numpy as np
from PIL import Image


def featuremap_provider(file_path, ranges, tag, file_sorce_path, file_label_path):
    res = CacheIO(file_path).get_cache()
    sorce_data = CacheIO(file_sorce_path).get_cache()
    label_data = CacheIO(file_label_path).get_cache()
    if res:
        map_data = featuremap_read(data=res, ranges=ranges, tag=tag, sorce_data=sorce_data, label_data=label_data).get_data()
        return map_data
    else:
        return []


def encode_base64(data):
    _io = io.BytesIO()
    _img = Image.fromarray(data.astype(np.uint8))
    _img.save(_io, "png")
    _content = _io.getvalue()
    _data = base64.b64encode(_content)
    res = "data:image/png;base64,%s" % _data.decode()
    return res


def get_featuremap_data(request):
    params = ['uid', 'trainJobName', 'run', 'tag', 'range', 'task']
    uid, trainJobName, run, tag, ranges, task = get_api_params(request, params)
    file_path = get_file_path(uid, run, 'featuremap', tag)
    file_sorce_path = get_file_path(uid, run, 'featuremap', task + '-sorce')
    file_label_path = get_file_path(uid, run, 'featuremap', task + '-label')
    data = featuremap_provider(file_path, int(ranges), tag, file_sorce_path, file_label_path)
    for item in range(len(data)):
        data[item]['value'] = [encode_base64(img) for img in data[item]['value']]
    return {tag: data}
