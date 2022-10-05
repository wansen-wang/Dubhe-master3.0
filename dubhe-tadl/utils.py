# Copyright (c) Microsoft Corporation.
# Licensed under the MIT license.

from collections import OrderedDict
import json
import random
import numpy as np
import torch
import torch.nn as nn
import os
from datetime import datetime
from io import TextIOBase
import logging
import sys
import time
from pytorch.trainer import TorchTensorEncoder

_counter = 0


def global_mutable_counting():
    """
    A program level counter starting from 1.
    """
    global _counter
    _counter += 1
    return _counter


def set_seed(seed):
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)

    torch.backends.cudnn.benchmark = False
    torch.backends.cudnn.deterministic = True


def _reset_global_mutable_counting():
    """
    Reset the global mutable counting to count from 1. Useful when defining multiple models with default keys.
    """
    global _counter
    _counter = 0


def to_device(obj, device):
    """
    Move a tensor, tuple, list, or dict onto device.
    """
    if torch.is_tensor(obj):
        return obj.to(device)
    if isinstance(obj, tuple):
        return tuple(to_device(t, device) for t in obj)
    if isinstance(obj, list):
        return [to_device(t, device) for t in obj]
    if isinstance(obj, dict):
        return {k: to_device(v, device) for k, v in obj.items()}
    if isinstance(obj, (int, float, str)):
        return obj
    raise ValueError("'%s' has unsupported type '%s'" % (obj, type(obj)))


def to_list(arr):
    if torch.is_tensor(arr):
        return arr.cpu().numpy().tolist()
    if isinstance(arr, np.ndarray):
        return arr.tolist()
    if isinstance(arr, (list, tuple)):
        return list(arr)
    return arr


def count_parameters_in_MB(model):
    return np.sum(
        np.prod(v.size()) for name, v in model.named_parameters() if "auxiliary" not in name) / 1e6


def str2bool(str):
    return True if str.lower() == 'true' else False


class AverageMeterGroup:
    """
    Average meter group for multiple average meters.
    """

    def __init__(self):
        self.meters = OrderedDict()

    def update(self, data):
        """
        Update the meter group with a dict of metrics.
        Non-exist average meters will be automatically created.
        """
        for k, v in data.items():
            if k not in self.meters:
                self.meters[k] = AverageMeter(k, ":4f")
            self.meters[k].update(v)

    def __getattr__(self, item):
        return self.meters[item]

    def __getitem__(self, item):
        return self.meters[item]

    def __str__(self):
        return "  ".join(str(v) for v in self.meters.values())

    def summary(self):
        """
        Return a summary string of group data.
        """
        return "  ".join(v.summary() for v in self.meters.values())

    def get_last_acc(self):
        return float([v.summary() for v in self.meters.values()][0].split(': ')[1])


class AverageMeter:
    """
    Computes and stores the average and current value.

    Parameters
    ----------
    name : str
        Name to display.
    fmt : str
        Format string to print the values.
    """

    def __init__(self, name, fmt=':f'):
        self.name = name
        self.fmt = fmt
        self.reset()

    def reset(self):
        """
        Reset the meter.
        """
        self.val = 0
        self.avg = 0
        self.sum = 0
        self.count = 0

    def update(self, val, n=1):
        """
        Update with value and weight.

        Parameters
        ----------
        val : float or int
            The new value to be accounted in.
        n : int
            The weight of the new value.
        """
        self.val = val
        self.sum += val * n
        self.count += n
        self.avg = self.sum / self.count

    def __str__(self):
        fmtstr = '{name} {val' + self.fmt + '} ({avg' + self.fmt + '})'
        return fmtstr.format(**self.__dict__)

    def summary(self):
        fmtstr = '{name}: {avg' + self.fmt + '}'
        return fmtstr.format(**self.__dict__)


class StructuredMutableTreeNode:
    """
    A structured representation of a search space.
    A search space comes with a root (with `None` stored in its `mutable`), and a bunch of children in its `children`.
    This tree can be seen as a "flattened" version of the module tree. Since nested mutable entity is not supported yet,
    the following must be true: each subtree corresponds to a ``MutableScope`` and each leaf corresponds to a
    ``Mutable`` (other than ``MutableScope``).

    Parameters
    ----------
    mutable : nni.nas.pytorch.mutables.Mutable
        The mutable that current node is linked with.
    """

    def __init__(self, mutable):
        self.mutable = mutable
        self.children = []

    def add_child(self, mutable):
        """
        Add a tree node to the children list of current node.
        """
        self.children.append(StructuredMutableTreeNode(mutable))
        return self.children[-1]

    def type(self):
        """
        Return the ``type`` of mutable content.
        """
        return type(self.mutable)

    def __iter__(self):
        return self.traverse()

    def traverse(self, order="pre", deduplicate=True, memo=None):
        """
        Return a generator that generates a list of mutables in this tree.

        Parameters
        ----------
        order : str
            pre or post. If pre, current mutable is yield before children. Otherwise after.
        deduplicate : bool
            If true, mutables with the same key will not appear after the first appearance.
        memo : dict
            An auxiliary dict that memorize keys seen before, so that deduplication is possible.

        Returns
        -------
        generator of Mutable
        """
        if memo is None:
            memo = set()
        assert order in ["pre", "post"]
        if order == "pre":
            if self.mutable is not None:
                if not deduplicate or self.mutable.key not in memo:
                    memo.add(self.mutable.key)
                    yield self.mutable
        for child in self.children:
            for m in child.traverse(order=order, deduplicate=deduplicate, memo=memo):
                yield m
        if order == "post":
            if self.mutable is not None:
                if not deduplicate or self.mutable.key not in memo:
                    memo.add(self.mutable.key)
                    yield self.mutable


def dump_global_result(res_path, global_result):
    with open(res_path, "w") as ss_file:
        json.dump(global_result, ss_file, indent=2, cls=TorchTensorEncoder)


def save_best_checkpoint(checkpoint_dir, model, optimizer, epoch):
    """
    Dump to 'best_checkpoint_epoch{}.pth.tar'.format(epoch)' on last epoch end.
    ``DataParallel`` object will have their inside modules exported.
    """
    if isinstance(model, nn.DataParallel):
        child_model_state_dict = model.module.state_dict()
    else:
        child_model_state_dict = model.state_dict()

    save_state = {'child_model_state_dict': child_model_state_dict,
                  'optimizer_state_dict': optimizer.state_dict(),
                  'epoch': epoch}

    dest_path = os.path.join(checkpoint_dir, "best_checkpoint_epoch{}.pth".format(epoch))
    torch.save(save_state, dest_path)


log_level_map = {
    'fatal': logging.FATAL,
    'error': logging.ERROR,
    'warning': logging.WARNING,
    'info': logging.INFO,
    'debug': logging.DEBUG
}

_time_format = '%m/%d/%Y, %I:%M:%S %p'


class _LoggerFileWrapper(TextIOBase):
    def __init__(self, logger_file):
        self.file = logger_file

    def write(self, s):
        if s != '\n':
            cur_time = datetime.now().strftime(_time_format)
            self.file.write('[{}] PRINT '.format(cur_time) + s + '\n')
            self.file.flush()
        return len(s)


def init_logger(logger_file_path, log_level_name='info'):
    """Initialize root logger.
    This will redirect anything from logging.getLogger() as well as stdout to specified file.
    logger_file_path: path of logger file (path-like object).
    """

    log_level = log_level_map.get(log_level_name)
    logger_file = open(logger_file_path, 'w')
    fmt = '[%(asctime)s] %(levelname)s (%(name)s/%(threadName)s) %(message)s'
    logging.Formatter.converter = time.localtime
    formatter = logging.Formatter(fmt, _time_format)

    stream_handler = logging.StreamHandler()
    stream_handler.setFormatter(formatter)
    file_handler = logging.FileHandler(logger_file_path)
    file_handler.setFormatter(formatter)

    root_logger = logging.getLogger()
    root_logger.addHandler(stream_handler)
    root_logger.addHandler(file_handler)
    root_logger.setLevel(log_level)

    # include print function output
    sys.stdout = _LoggerFileWrapper(logger_file)


def mkdirs(*args):
    for path in args:
        dirname = os.path.dirname(path)
        if dirname and not os.path.exists(dirname):
            print("make {} in dir: {}".format(path, dirname))
            os.makedirs(dirname)


def list_str2int(ls):
    return list(map(lambda x: int(x), ls))
