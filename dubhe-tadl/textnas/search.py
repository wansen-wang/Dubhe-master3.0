# Copyright (c) Microsoft Corporation.
# Licensed under the MIT license.

import logging
import os
import random
from argparse import ArgumentParser
from itertools import cycle

import numpy as np
import torch
import torch.nn as nn
import sys
sys.path.append("../..") 
from pytorch.enas import EnasMutator, EnasTrainer
from pytorch.callbacks import LRSchedulerCallback
from pytorch.mutables import LayerChoice, InputChoice, MutableScope

from dataloader import read_data_sst
from model import Model
from utils import accuracy, dump_global_result

from collections import OrderedDict
import os
import json
import time

logger = logging.getLogger("nni.textnas")
logger.setLevel(logging.INFO)

# For debugging mode
# os.chdir('/home/yangyi/pytorch/textnas')
os.environ["CUDA_VISIBLE_DEVICES"]='4'


def save_textnas_search_space(mutator,file_path):
    result = OrderedDict()
    cur_layer_idx = None
    for mutable in mutator.mutables.traverse():
        if not isinstance(mutable,(LayerChoice, InputChoice)):
            cur_layer_idx = mutable.key
            continue
        if isinstance(mutable,LayerChoice):
            if 'op_list' not in result:
                result['op_list'] = [str(i) for i in mutable]
            result[cur_layer_idx+ '_'+ mutable.key] = 'op_list'

        else:
            result[cur_layer_idx+ '_'+ mutable.key] = {'skip_connection':False if mutable.n_chosen else True,
                                                        'n_chosen': mutable.n_chosen if mutable.n_chosen else '',
                                                        'choose_from': mutable.choose_from if mutable.choose_from else ''}


    dump_global_result(file_path,result) 
 

class TextNASTrainer(EnasTrainer):
    def __init__(self, *args, train_loader=None, valid_loader=None, test_loader=None, **kwargs):
        super().__init__(*args, **kwargs)
        self.train_loader = train_loader
        self.valid_loader = valid_loader
        self.test_loader = test_loader
        self.result = {'accuracy':[],
                       'cost_time':0}
    def init_dataloader(self):
        pass



if __name__ == "__main__":
    parser = ArgumentParser("textnas")
    parser.add_argument("--search_space_path", type=str,
                        default='./search_space.json', help="search_space directory")
    parser.add_argument("--selected_space_path", type=str,
                        default='./selected_space.json', help="sapce_path_out directory")
    parser.add_argument("--result_path", type=str,
                        default='./result.json', help="res directory")
    parser.add_argument('--trial_id', type=int, default=0, metavar='N',
                    help='trial_id,start from 0')
    parser.add_argument("--batch-size", default=128, type=int)
    parser.add_argument("--log-frequency", default=50, type=int)
    parser.add_argument("--epochs", default=2, type=int)
    parser.add_argument("--lr", default=5e-3, type=float)
    args = parser.parse_args()
    # 设置随机种子
    torch.manual_seed(args.trial_id)
    torch.cuda.manual_seed_all(args.trial_id)
    np.random.seed(args.trial_id)
    random.seed(args.trial_id)
    # use deterministic instead of nondeterministic algorithm
    # make sure exact results can be reproduced everytime.
    torch.backends.cudnn.deterministic = True



 
    # 配置计算资源及load数据
    device = torch.device("cuda") if torch.cuda.is_available() else torch.device("cpu")
    train_dataset, valid_dataset, test_dataset, embedding = read_data_sst("data")
    train_loader = torch.utils.data.DataLoader(train_dataset, batch_size=args.batch_size, num_workers=4, shuffle=True)
    valid_loader = torch.utils.data.DataLoader(valid_dataset, batch_size=args.batch_size, num_workers=4, shuffle=True)
    test_loader = torch.utils.data.DataLoader(test_dataset, batch_size=args.batch_size, num_workers=4)
    train_loader, valid_loader = cycle(train_loader), cycle(valid_loader)
 

    # 导入模型以及预训练的词向量
    model = Model(embedding)


    # 实例化一个mutator, mutator主要是用于选择搜索空间的
    mutator = EnasMutator(model, temperature=None, tanh_constant=None, entropy_reduction="mean")
    
    # 储存整个网络结构
    save_textnas_search_space(mutator, args.search_space_path)

    criterion = nn.CrossEntropyLoss()
    # 实例化优化器
    optimizer = torch.optim.Adam(model.parameters(), lr=args.lr, eps=1e-3, weight_decay=2e-6)
    # 实例化学习率变化器
    lr_scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=args.epochs, eta_min=1e-5)
    # 实例话一个训练器
    trainer = TextNASTrainer(model,
                             loss=criterion,
                             metrics=lambda output, target: {"acc": accuracy(output, target)},
                             reward_function=accuracy,
                             optimizer=optimizer,
                             callbacks=[LRSchedulerCallback(lr_scheduler)],
                             batch_size=args.batch_size,
                             num_epochs=args.epochs,
                             dataset_train=None,
                             dataset_valid=None,
                             train_loader=train_loader,
                             valid_loader=valid_loader,
                             test_loader=test_loader,
                             log_frequency=args.log_frequency,
                             mutator=mutator,
                             mutator_lr=2e-3,
                             mutator_steps=5,
                             mutator_steps_aggregate=1,
                             child_steps=50,
                             baseline_decay=0.99,
                             test_arc_per_epoch=10)

    
    logger.info(trainer.metrics)

    t1 = time.time()
    trainer.train()
    trainer.result["cost_time"] = time.time() - t1
    dump_global_result(args.result_path,trainer.result)
    
    # os.makedirs("checkpoints", exist_ok=True)
    # for i in range(2):
    #    trainer.export(os.path.join("checkpoints", "architecture_%02d.json" % i))

    selected_model = trainer.export_child_model(selected_space = True)
    dump_global_result(args.selected_space_path,selected_model)