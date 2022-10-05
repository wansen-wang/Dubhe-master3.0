# Copyright (c) Microsoft Corporation.
# Licensed under the MIT license.
import sys
sys.path.append('..'+ '/' + '..')
import time
from argparse import ArgumentParser

from model import CNN
import torch
import torch.nn as nn

from pytorch.callbacks import BestArchitectureCheckpoint,  LRSchedulerCallback
from pytorch.pcdarts import PCdartsMutator
from pytorch.darts import DartsTrainer
from pytorch.darts.utils import accuracy
from pytorch.darts import datasets
from pytorch.utils import *

logger = logging.getLogger(__name__)

if __name__ == "__main__":
    parser = ArgumentParser("PCDARTS train")
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
    parser.add_argument('--model_lr', type=float, default=0.1, help='learning rate for training model weights')
    parser.add_argument('--arch_lr', type=float, default=6e-4, help='learning rate for training architecture')
    parser.add_argument("--nodes", default=4, type=int)
    parser.add_argument("--layers", default=8, type=int)
    parser.add_argument("--channels", default=16, type=int)
    parser.add_argument("--batch_size", default=96, type=int)
    parser.add_argument("--log_frequency", default=50, type=int)
    parser.add_argument("--class_num", default=10, type=int, help="cifar10")
    parser.add_argument("--epochs", default=5, type=int)
    parser.add_argument("--pre_epochs", default=15, type=int, help='pre epochs to train weight only')
    parser.add_argument("--k", default=4, type=int, help="channel portion of channel shuffle")
    parser.add_argument("--unrolled", default=False, action="store_true")
    args = parser.parse_args()
    
    mkdirs(args.result_path, args.log_path, args.search_space_path, args.best_selected_space_path)
    init_logger(args.log_path, "info")
    logger.info(args)
    set_seed(args.trial_id)

    logger.info("loading data")
    dataset_train, dataset_valid = datasets.get_dataset("cifar10", root=args.data_dir)
    
    model = CNN(32, 3, args.channels, args.class_num, args.layers, n_nodes=args.nodes, k=args.k)
    criterion = nn.CrossEntropyLoss()

    optim = torch.optim.SGD(model.parameters(), args.model_lr, momentum=0.9, weight_decay=3.0E-4)
    lr_scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optim, args.epochs, eta_min=0.001)

    logger.info("initializing trainer")
    trainer = DartsTrainer(model,
                           loss=criterion,
                           metrics=lambda output, target: accuracy(output, target, topk=(1,)),
                           optimizer=optim,
                           num_epochs=args.epochs,
                           dataset_train=dataset_train,
                           dataset_valid=dataset_valid,
                           mutator=PCdartsMutator(model),
                           batch_size=args.batch_size,
                           log_frequency=args.log_frequency,
                           arch_lr=args.arch_lr,
                           unrolled=args.unrolled,
                           result_path=args.result_path,
                           num_pre_epochs=args.pre_epochs, 
                           search_space_path=args.search_space_path,
                           callbacks=
                           [LRSchedulerCallback(lr_scheduler), BestArchitectureCheckpoint(args.best_selected_space_path, args.epochs)])

    logger.info("training")
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