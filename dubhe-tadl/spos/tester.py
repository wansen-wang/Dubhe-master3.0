import argparse
# import logging
import random
import time
from itertools import cycle
import json


import sys
sys.path.insert(0,"/home/hanjiayi/Document/nni")
sys.path.insert(0,"/home/hanjiayi/Document/nni/examples")
sys.path.insert(0,"/home/hanjiayi/Document/nni/nni/algorithms")
import os

# 指定写入文件的环境变量，原实现于nnictl_utils.py的search_space_auto_gen
os.environ["NNI_GEN_SEARCH_SPACE"] = "auto_gen_search_space.json"

os.environ["CUDA_VISIBLE_DEVICES"] = "1,2,3"


import numpy as np
import torch
import torch.nn as nn
from nni.algorithms.nas.pytorch.classic_nas import get_and_apply_next_architecture
from nni.nas.pytorch.fixed import apply_fixed_architecture
from nni.nas.pytorch.utils import AverageMeterGroup

from dataloader import get_imagenet_iter_dali
from network import ShuffleNetV2OneShot, load_and_parse_state_dict
from utils import CrossEntropyLabelSmooth, accuracy


# logger = logging.getLogger("nni.spos.tester")     # "nni.spos.tester"
print("Evolution Beginning...")

def retrain_bn(model, criterion, max_iters, log_freq, loader):
    with torch.no_grad():
        # logger.info("Clear BN statistics...")
        print("clear BN statistics")
        for m in model.modules():
            if isinstance(m, nn.BatchNorm2d):
                m.running_mean = torch.zeros_like(m.running_mean)
                m.running_var = torch.ones_like(m.running_var)

        # logger.info("Train BN with training set (BN sanitize)...")
        print("Train BN with training set (BN sanitize)...")
        model.train()
        meters = AverageMeterGroup()
        start_time = time.time()

        for step in range(max_iters):
            inputs, targets = next(loader)
            logits = model(inputs)
            loss = criterion(logits, targets)
            metrics = accuracy(logits, targets)
            metrics["loss"] = loss.item()
            meters.update(metrics)
            if step % log_freq == 0 or step + 1 == max_iters:
                # logger.info("Train Step [%d/%d] %s time %.3fs ", step + 1, max_iters, meters, time.time() - start_time)
                print("Train Step [%d/%d] %s time %.3fs "% (step + 1, max_iters, meters, time.time() - start_time))

def test_acc(model, criterion, log_freq, loader):
    # logger.info("Start testing...")
    print("start testing...")
    model.eval()
    meters = AverageMeterGroup()
    start_time = time.time()

    with torch.no_grad():
        for step, (inputs, targets) in enumerate(loader):
            logits = model(inputs)
            loss = criterion(logits, targets)
            metrics = accuracy(logits, targets)
            metrics["loss"] = loss.item()
            meters.update(metrics)
            if step % log_freq == 0 or step + 1 == len(loader):
                # logger.info("Valid Step [%d/%d] time %.3fs acc1 %.4f acc5 %.4f loss %.4f",
                #             step + 1, len(loader), time.time() - start_time,
                #             meters.acc1.avg, meters.acc5.avg, meters.loss.avg)
                print("Valid Step [%d/%d] time %.3fs acc1 %.4f acc5 %.4f loss %.4f"%
                      (step + 1, len(loader), time.time() - start_time,
                              meters.acc1.avg, meters.acc5.avg, meters.loss.avg))
            if step>len(loader):     # 遍历一遍就停止
                break
    return meters.acc1.avg


def evaluate_acc(model, criterion, args, loader_train, loader_test):

    retrain_bn(model, criterion, args.train_iters, args.log_frequency, loader_train)     # todo
    acc = test_acc(model, criterion, args.log_frequency, loader_test)
    assert isinstance(acc, float)
    torch.cuda.empty_cache()
    return acc


if __name__ == "__main__":
    parser = argparse.ArgumentParser("SPOS Candidate Tester")
    parser.add_argument("--imagenet-dir", type=str, default="/mnt/local/hanjiayi/imagenet")     # ./data/imagenet
    parser.add_argument("--checkpoint", type=str, default="./data/checkpoint-150000.pth.tar")     # ./data/checkpoint-150000.pth.tar
    parser.add_argument("--spos-preprocessing", default=True,
                        help="When true, image values will range from 0 to 255 and use BGR "
                             "(as in original repo).")     # , action="store_true"
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--workers", type=int, default=6)    # 线程数
    parser.add_argument("--train-batch-size", type=int, default=128)
    parser.add_argument("--train-iters", type=int, default=200)
    parser.add_argument("--test-batch-size", type=int, default=512)    # nni中为512，官方repo为200
    parser.add_argument("--log-frequency", type=int, default=10)
    parser.add_argument("--architecture", type=str, default="./architecture_final.json", help="load the file to retrain or eval")
    parser.add_argument("--mode", type=str, default="gen", help="there are two modes here: gen mode for generating architecture, and evl mode for evaluation model")

    args = parser.parse_args()

    # use a fixed set of image will improve the performance
    torch.manual_seed(args.seed)
    torch.cuda.manual_seed_all(args.seed)
    np.random.seed(args.seed)
    random.seed(args.seed)
    torch.backends.cudnn.deterministic = True

    assert torch.cuda.is_available()

    model = ShuffleNetV2OneShot()
    criterion = CrossEntropyLabelSmooth(1000, 0.1)


    if args.mode == "gen":
        get_and_apply_next_architecture(model)
        model.load_state_dict(load_and_parse_state_dict(filepath=args.checkpoint))

    else:     # evaluate the model
        print("## test&retrain -- load model ## begin to load model")
        model.load_state_dict(load_and_parse_state_dict(filepath=args.checkpoint))
        print("## test&retrain -- load model ## model loaded")

        print("## test&retrain -- apply architecture ## begin to apply architecture to model")
        apply_fixed_architecture(model, args.architecture)
        print("## test&retrain -- apply architecture ## architecture applied")

    model.cuda(0)
    print("## test&retrain -- load train data ## begin to load train data")
    train_loader = get_imagenet_iter_dali("train", args.imagenet_dir, args.train_batch_size, args.workers,
                                          spos_preprocessing=args.spos_preprocessing,
                                          seed=args.seed, device_id=0)
    print("## test&retrain -- load train data ## train data loaded")

    print("## test&retrain -- load test data ## begin to load test data")
    val_loader = get_imagenet_iter_dali("val", args.imagenet_dir, args.test_batch_size, args.workers,
                                        spos_preprocessing=args.spos_preprocessing, shuffle=True,
                                        seed=args.seed, device_id=0)
    print("## test&retrain -- load test date ## test data loaded")

    train_loader = cycle(train_loader)
    acc = evaluate_acc(model, criterion, args, train_loader, val_loader)

    # 把模型最终的准确率写入一个文件中
    os.makedirs("./acc", exist_ok=True)
    with open("./acc/{}".format(args.architecture[-12:]), "w") as f:     # [-12:] 代表没有路径的文件名
        # {filename1: acc,
        #  filename2: acc,
        #  000_000.json: acc,
        #  000_001.json: acc,
        #  ......
        #  }
        json.dump({args.architecture: acc}, f)



