# Copyright (c) Microsoft Corporation.
# Licensed under the MIT license.
import sys
sys.path.append('..'+ '/' + '..')
import logging
import time
from argparse import ArgumentParser

import torch
import torch.nn as nn

import datasets
from macro import GeneralNetwork
from micro import MicroNetwork
from trainer import EnasTrainer
from mutator import EnasMutator
from pytorch.callbacks import (ArchitectureCheckpoint,
                                       LRSchedulerCallback)
from utils import accuracy, reward_accuracy
from collections import OrderedDict
from pytorch.mutables import LayerChoice, InputChoice
import json
torch.cuda.set_device(4)

logger = logging.getLogger('tadl-enas')

# save search space as search_space.json
def save_nas_search_space(mutator,file_path):
    result = OrderedDict()
    cur_layer_idx = None
    for mutable in mutator.mutables.traverse():
        if not isinstance(mutable,(LayerChoice, InputChoice)):
            cur_layer_idx = mutable.key + '_'
            continue
        # macro
        if 'layer' in cur_layer_idx:
            if isinstance(mutable, LayerChoice):
                if 'op_list' not in result:
                    result['op_list'] = [str(i) for i in mutable]
                result[cur_layer_idx + mutable.key] = 'op_list'
            else:
                result[cur_layer_idx + mutable.key] = {'skip_connection': False if mutable.n_chosen else True,
                                       'n_chosen': mutable.n_chosen if mutable.n_chosen else '',
                                       'choose_from': mutable.choose_from if mutable.choose_from else ''}
        # micro
        elif 'node' in cur_layer_idx:
            if isinstance(mutable,LayerChoice):
                if 'op_list' not in result:
                    result['op_list'] = [str(i) for i in mutable]
                result[mutable.key] = 'op_list'
            else:
                result[mutable.key] = {'skip_connection':False if mutable.n_chosen else True,
                                                            'n_chosen': mutable.n_chosen if mutable.n_chosen else '',
                                                            'choose_from': mutable.choose_from if mutable.choose_from else ''}

    dump_global_result(file_path,result)

# def dump_global_result(args,global_result):
#     with open(args['result_path'], "w") as ss_file:
#         json.dump(global_result, ss_file, sort_keys=True, indent=2)

def dump_global_result(res_path,global_result, sort_keys = False):
    with open(res_path, "w") as ss_file:
        json.dump(global_result, ss_file, sort_keys=sort_keys, indent=2)



if __name__ == "__main__":
    parser = ArgumentParser("enas")
    parser.add_argument("--search_space_path", type=str,
                        default='./search_space.json', help="search_space directory")
    parser.add_argument("--selected_space_path", type=str,
                        default='./selected_space.json', help="sapce_path_out directory")
    parser.add_argument("--result_path", type=str,
                        default='./result.json', help="res directory")
    parser.add_argument('--trial_id', type=int, default=0, metavar='N',
                    help='trial_id,start from 0')

    parser.add_argument("--batch-size", default=128, type=int)
    parser.add_argument("--log-frequency", default=10, type=int)
    parser.add_argument("--search_for", choices=["macro", "micro"], default="macro")
    parser.add_argument("--epochs", default=None, type=int, help="Number of epochs (default: macro 310, micro 150)")
    args = parser.parse_args()

    # 设置随机种子
    torch.manual_seed(args.trial_id)
    torch.cuda.manual_seed_all(args.trial_id)
    np.random.seed(args.trial_id)
    random.seed(args.trial_id)

    dataset_train, dataset_valid = datasets.get_dataset("cifar10")
    if args.search_for == "macro":
        model = GeneralNetwork()
        num_epochs = args.epochs or 310
        mutator = None
        mutator = EnasMutator(model)
    elif args.search_for == "micro":
        model = MicroNetwork(num_layers=6, out_channels=20, num_nodes=5, dropout_rate=0.1, use_aux_heads=True)
        num_epochs = args.epochs or 150
        mutator = EnasMutator(model, tanh_constant=1.1, cell_exit_extra_step=True)
    else:
        raise AssertionError

    # 储存整个网络结构
    # args.search_spach_path =  None#str(args.search_for) + str(args.search_space_path)
    # print( args.search_space_path, args.search_for )
    save_nas_search_space(mutator, args.search_space_path)

    criterion = nn.CrossEntropyLoss()
    optimizer = torch.optim.SGD(model.parameters(), 0.05, momentum=0.9, weight_decay=1.0E-4)
    lr_scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=num_epochs, eta_min=0.001)

    trainer = EnasTrainer(model,
                               loss=criterion,
                               metrics=accuracy,
                               reward_function=reward_accuracy,
                               optimizer=optimizer,
                               callbacks=[LRSchedulerCallback(lr_scheduler)],
                               batch_size=args.batch_size,
                               num_epochs=num_epochs,
                               dataset_train=dataset_train,
                               dataset_valid=dataset_valid,
                               log_frequency=args.log_frequency,
                               mutator=mutator,
                               child_model_path='./'+args.search_for+'_child_model')

    logger.info(trainer.metrics)

    t1 = time.time()
    trainer.train()
    trainer.result["cost_time"] = time.time() - t1
    dump_global_result(args.result_path,trainer.result)

    selected_model = trainer.export_child_model(selected_space = True)
    dump_global_result(args.selected_space_path,selected_model)