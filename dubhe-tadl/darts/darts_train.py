# Copyright (c) Microsoft Corporation.
# Licensed under the MIT license.
import sys
sys.path.append('..'+ '/' + '..')

import time
from argparse import ArgumentParser

import torch
import torch.nn as nn   
import datasets
from model import CNN
from utils import accuracy
from dartstrainer import DartsTrainer
from pytorch.utils import *
from pytorch.callbacks import BestArchitectureCheckpoint, LRSchedulerCallback

logger = logging.getLogger(__name__)

if __name__ == "__main__":
    parser = ArgumentParser("DARTS train")
    parser.add_argument("--data_dir", type=str,
                        default='../data/', help="search_space json file")
    parser.add_argument("--result_path", type=str,
                        default='.0/result.json', help="training result")
    parser.add_argument("--log_path", type=str,
                        default='.0/log', help="log for info")
    parser.add_argument("--search_space_path", type=str,
                        default='./search_space.json', help="search space of PDARTS")
    parser.add_argument("--best_selected_space_path", type=str,
                        default='./best_selected_space.json', help="final best selected space")
    parser.add_argument('--trial_id', type=int, default=0, metavar='N',
                        help='trial_id,start from 0')
    parser.add_argument("--layers", default=8, type=int)
    parser.add_argument("--batch_size", default=64, type=int)
    parser.add_argument("--log_frequency", default=10, type=int)
    parser.add_argument("--epochs", default=5, type=int)
    parser.add_argument("--channels", default=16, type=int)
    parser.add_argument('--model_lr', type=float, default=0.025, help='learning rate for training model weights')
    parser.add_argument('--arch_lr', type=float, default=3e-4, help='learning rate for training architecture')
    parser.add_argument("--unrolled", default=False, action="store_true")
    parser.add_argument("--visualization", default=False, action="store_true")
    parser.add_argument("--class_num", default=10, type=int, help="cifar10")
    args = parser.parse_args()
    
    mkdirs(args.result_path, args.log_path, args.search_space_path, args.best_selected_space_path)
    init_logger(args.log_path, "info")
    logger.info(args)
    set_seed(args.trial_id)
    
    dataset_train, dataset_valid = datasets.get_dataset("cifar10", root=args.data_dir)
    model = CNN(32, 3, args.channels, args.class_num, args.layers)
    criterion = nn.CrossEntropyLoss()

    optim = torch.optim.SGD(model.parameters(), args.model_lr, momentum=0.9, weight_decay=3.0E-4)
    lr_scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optim, args.epochs, eta_min=0.001)

    trainer = DartsTrainer(model,
                           loss=criterion,
                           metrics=lambda output, target: accuracy(output, target, topk=(1,)),
                           optimizer=optim,
                           num_epochs=args.epochs,
                           dataset_train=dataset_train,
                           dataset_valid=dataset_valid,
                           search_space_path = args.search_space_path,
                           batch_size=args.batch_size,
                           log_frequency=args.log_frequency,
                           result_path=args.result_path,
                           unrolled=args.unrolled,
                           arch_lr=args.arch_lr,
                           callbacks=[LRSchedulerCallback(lr_scheduler), BestArchitectureCheckpoint(args.best_selected_space_path, args.epochs)])

    if args.visualization:
        trainer.enable_visualization()
    t1 = time.time()
    trainer.train()
    # res_json = trainer.result
    cost_time = time.time() - t1
    # 后端在终端过滤，{"type": "Cost_time", "result": {"value": "* s"}}
    logger.info({"type": "Cost_time", "result": {"value": str(cost_time) + ' s'}})
    with open(args.result_path, "a") as file:
        file.write(str({"type": "Cost_time", "result": {"value": str(cost_time) + ' s'}}))
    
    # res_json["Cost_time"] = str(cost_time) + ' s'
    # dump_global_result(args.result_path, res_json)