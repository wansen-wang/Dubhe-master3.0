import os
import json
import time
import random
import argparse
import numpy as np
# import logging
from itertools import cycle

import sys
sys.path.append("../")
sys.path.append("../../")

import torch
import torch.nn as nn

os.environ["NNI_GEN_SEARCH_SPACE"] = "auto_gen_search_space.json"
os.environ["CUDA_VISIBLE_DEVICES"] = "1,2,3"

from pytorch.fixed import apply_fixed_architecture
from pytorch.utils import AverageMeterGroup

from dataloader import get_imagenet_iter_dali
from network import ShuffleNetV2OneShot, load_and_parse_state_dict
from utils import CrossEntropyLabelSmooth, accuracy

# logger = logging.getLogger("nni.spos.tester")     # "nni.spos.tester"
print("Evolution Beginning...")


class Evaluator:

    """
    retrain the BN layer in specified model and evaluate it
    """

    def __init__(self, imagenet_dir="/mnt/local/hanjiayi/imagenet",    # imagenet dataset
                        checkpoint="./data/checkpoint-150000.pth.tar",    # fine model from supernet
                        spos_preprocessing=True,    # RGB or BGR
                        seed=42,    # torch.manual_seed
                        workers=1,    # the number of subprocess 
                        train_batch_size=128,     
                        train_iters=200,
                        test_batch_size=512, 
                        log_frequency=10, 
                ):

        self.imagenet_dir = imagenet_dir
        self.checkpoint = checkpoint
        self.spos_preprocessing = spos_preprocessing
        self.seed = seed
        self.workers = workers
        self.train_batch_size = train_batch_size
        self.train_iters = train_iters
        self.test_batch_size = test_batch_size
        self.log_frequency = log_frequency
        print("### program interval 1 ###")
        self.model = ShuffleNetV2OneShot()
        print("### program interval 2 ###")

        print("## test&retrain -- load model ## begin to load model")
        self.model.load_state_dict(load_and_parse_state_dict(filepath=self.checkpoint))
        print("## test&retrain -- load model ## model loaded")


        torch.manual_seed(self.seed)
        torch.cuda.manual_seed_all(self.seed)
        np.random.seed(self.seed)
        random.seed(self.seed)
        torch.backends.cudnn.deterministic = True

        assert torch.cuda.is_available()

        self.criterion = CrossEntropyLabelSmooth(1000, 0.1)

        print("##### load training data #####")
        self.train_loader = get_imagenet_iter_dali("train", self.imagenet_dir, self.train_batch_size, self.workers,
                                            spos_preprocessing=self.spos_preprocessing,
                                            seed=self.seed, device_id=0)
        print("##### training data loaded finished #####")

        print("##### load validating data #####")
        self.val_loader = get_imagenet_iter_dali("val", self.imagenet_dir, self.test_batch_size, self.workers,
                                            spos_preprocessing=self.spos_preprocessing, shuffle=True,
                                            seed=self.seed, device_id=0)
        print("##### validating data loaded finished #####")

    def retrain_bn(self, model, criterion, max_iters, log_freq, loader):
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

    def test_acc(self, model, criterion, log_freq, loader):
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

    def evaluate_acc(self, model, criterion, loader_train, loader_test):

        self.retrain_bn(model, criterion, self.train_iters, self.log_frequency, loader_train)     # todo
        acc = self.test_acc(model, criterion, self.log_frequency, loader_test)
        assert isinstance(acc, float)
        torch.cuda.empty_cache()
        return acc

    def eval_model(self, epoch, architecture):

        # evaluate the model

        print("## test&retrain -- apply architecture ## begin to apply architecture to model")
        apply_fixed_architecture(self.model, architecture)
        print("## test&retrain -- apply architecture ## architecture applied")

        self.model.cuda(0)
        self.train_loader = cycle(self.train_loader)
        acc = self.evaluate_acc(self.model, self.criterion, self.train_loader, self.val_loader)

        # 把模型最终的准确率写入一个文件中
        os.makedirs("./acc", exist_ok=True)
        with open("./acc/{}".format(architecture[-12:]), "w") as f:     # [-12:] 代表没有路径的文件名
            # {filename1: acc,
            #  filename2: acc,
            #  000_000.json: acc,
            #  000_001.json: acc,
            #  ......
            #  }
            json.dump({architecture: acc}, f)


if __name__ == "__main__":

    parser = argparse.ArgumentParser("SPOS Candidate Evaluator")
    parser.add_argument("--imagenet-dir", type=str, default="/mnt/local/hanjiayi/imagenet")     # ./data/imagenet
    parser.add_argument("--checkpoint", type=str, default="./data/checkpoint-150000.pth.tar")     # ./data/checkpoint-150000.pth.tar
    parser.add_argument("--spos-preprocessing", default=True,
                        help="When true, image values will range from 0 to 255 and use BGR "
                             "(as in original repo).")     # , action="store_true"
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--workers", type=int, default=1)    # 线程数
    parser.add_argument("--train-batch-size", type=int, default=128)
    parser.add_argument("--train-iters", type=int, default=200)
    parser.add_argument("--test-batch-size", type=int, default=512)    # nni中为512，官方repo为200
    parser.add_argument("--log-frequency", type=int, default=10)
    parser.add_argument("--architecture", type=str, default="./architecture_final.json", help="load the file to retrain or eval")
    parser.add_argument("--epoch", type=int, default=0, help="when epoch=0, this file should generate an architecture file")

    args = parser.parse_args()
    
    evl = Evaluator(imagenet_dir=args.imagenet_dir,    # imagenet dataset
                        checkpoint=args.checkpoint,    # fine model from supernet
                        spos_preprocessing=args.spos_preprocessing,    # RGB or BGR
                        seed=args.seed,    # torch.manual_seed
                        workers=args.workers,    # the number of subprocess 
                        train_batch_size=args.train_batch_size,     
                        train_iters=args.train_iters,
                        test_batch_size=args.test_batch_size, 
                        log_frequency=args.log_frequency, 
                    )

    
    evl.eval_model(args.epoch, args.architecture)