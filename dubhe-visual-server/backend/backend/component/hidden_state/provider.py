from utils.cache_io import CacheIO
from utils.logfile_utils import get_file_path
from backend.api.utils import get_api_params
from .state_read import state_read, state_select_read


def state_provider(file_path, word_file_path, pos, ranges):
    res = CacheIO(file_path).get_cache()
    word = CacheIO(word_file_path).get_cache()
    if res:
        data = state_read(data=res, pos=pos, ranges=ranges, word=word).get_data()
        return data


def state_select_provider(file_path, word_file_path, pattern, threshold, length):
    res = CacheIO(file_path).get_cache()
    word = CacheIO(word_file_path).get_cache()
    if res:
        data = state_select_read(data=res, pattern=pattern, threshold=threshold, word=word, length=length).get_data()
        return data


def get_state_data(request):
    params = ['uid', 'trainJobName', 'run', 'tag', 'pos', 'range']
    uid, trainJobName, run, tag, pos, ranges = get_api_params(request, params)
    file_path = get_file_path(uid, run, 'hiddenstate', tag)
    word_file_path = get_file_path(uid, run, 'hiddenstate', "hidden_state_word")
    data = state_provider(file_path, word_file_path, pos, ranges)
    return data


def get_state_select_data(request):
    params = ['uid', 'trainJobName', 'run', 'tag', 'pattern', 'threshold', "length"]
    uid, trainJobName, run, tag, pattern, threshold, length = get_api_params(request, params)
    file_path = get_file_path(uid, run, 'hiddenstate', tag)
    word_file_path = get_file_path(uid, run, 'hiddenstate', "hidden_state_word")
    data = state_select_provider(file_path, word_file_path, pattern, threshold, length)
    return data
