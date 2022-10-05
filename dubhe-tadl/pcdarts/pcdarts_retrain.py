import sys
sys.path.append('..'+ '/' + '..')
import os
import logging
import time
from argparse import ArgumentParser

import torch
import torch.nn as nn
import numpy as np
# from torch.utils.tensorboard import SummaryWriter
import torch.backends.cudnn as cudnn

from model import CNN
from pytorch.fixed import apply_fixed_architecture
from pytorch.utils import set_seed, mkdirs, init_logger, save_best_checkpoint, AverageMeter
from pytorch.darts import utils
from pytorch.darts import datasets
from pytorch.retrainer import Retrainer

logger = logging.getLogger(__name__)
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
# writer = SummaryWriter()

class PCdartsRetrainer(Retrainer):
    def __init__(self, aux_weight, grad_clip, epochs, log_frequency):
        self.aux_weight = aux_weight
        self.grad_clip = grad_clip
        self.epochs = epochs
        self.log_frequency = log_frequency

    def train(self, train_loader, model, optimizer, criterion, epoch):
        top1 = AverageMeter("top1")
        top5 = AverageMeter("top5")
        losses = AverageMeter("losses")

        cur_step = epoch * len(train_loader)
        cur_lr = optimizer.param_groups[0]["lr"]
        logger.info("Epoch %d LR %.6f", epoch, cur_lr)
        # writer.add_scalar("lr", cur_lr, global_step=cur_step)

        model.train()

        for step, (x, y) in enumerate(train_loader):
            x, y = x.to(device, non_blocking=True), y.to(device, non_blocking=True)
            bs = x.size(0)

            optimizer.zero_grad()
            logits, aux_logits = model(x)
            loss = criterion(logits, y)
            if self.aux_weight > 0.:
                loss += self.aux_weight * criterion(aux_logits, y)
            loss.backward()
            # gradient clipping
            nn.utils.clip_grad_norm_(model.parameters(), self.grad_clip)
            optimizer.step()

            accuracy = utils.accuracy(logits, y, topk=(1, 5))
            losses.update(loss.item(), bs)
            top1.update(accuracy["acc1"], bs)
            top5.update(accuracy["acc5"], bs)
            # writer.add_scalar("loss/train", loss.item(), global_step=cur_step)
            # writer.add_scalar("acc1/train", accuracy["acc1"], global_step=cur_step)
            # writer.add_scalar("acc5/train", accuracy["acc5"], global_step=cur_step)

            if step % self.log_frequency == 0 or step == len(train_loader) - 1:
                logger.info(
                    "Train: [{:3d}/{}] Step {:03d}/{:03d} Loss {losses.avg:.3f} "
                    "Prec@(1,5) ({top1.avg:.1%}, {top5.avg:.1%})".format(
                        epoch + 1, self.epochs, step, len(train_loader) - 1, losses=losses,
                        top1=top1, top5=top5))

            cur_step += 1

        logger.info("Train: [{:3d}/{}] Final Prec@1 {:.4%}".format(epoch + 1, self.epochs, top1.avg))


    def validate(self, valid_loader, model, criterion, epoch, cur_step):
        top1 = AverageMeter("top1")
        top5 = AverageMeter("top5")
        losses = AverageMeter("losses")

        model.eval()

        with torch.no_grad():
            for step, (X, y) in enumerate(valid_loader):
                X, y = X.to(device, non_blocking=True), y.to(device, non_blocking=True)
                bs = X.size(0)

                logits = model(X)
                loss = criterion(logits, y)

                accuracy = utils.accuracy(logits, y, topk=(1, 5))
                losses.update(loss.item(), bs)
                top1.update(accuracy["acc1"], bs)
                top5.update(accuracy["acc5"], bs)

                if step % self.log_frequency == 0 or step == len(valid_loader) - 1:
                    logger.info(
                        "Valid: [{:3d}/{}] Step {:03d}/{:03d} Loss {losses.avg:.3f} "
                        "Prec@(1,5) ({top1.avg:.1%}, {top5.avg:.1%})".format(
                            epoch + 1, self.epochs, step, len(valid_loader) - 1, losses=losses,
                            top1=top1, top5=top5))

        # writer.add_scalar("loss/test", losses.avg, global_step=cur_step)
        # writer.add_scalar("acc1/test", top1.avg, global_step=cur_step)
        # writer.add_scalar("acc5/test", top5.avg, global_step=cur_step)

        logger.info("Valid: [{:3d}/{}] Final Prec@1 {:.4%}".format(epoch + 1, self.epochs, top1.avg))

        return top1.avg

if __name__ == "__main__":
    parser = ArgumentParser("PCDARTS retrain")
    parser.add_argument("--data_dir", type=str,
                        default='./', help="search_space json file")
    parser.add_argument("--result_path", type=str,
                        default='./result.json', help="training result")
    parser.add_argument("--log_path", type=str,
                        default='.0/log', help="log for info")
    parser.add_argument("--best_selected_space_path", type=str,
                        default='./best_selected_space.json', help="final best selected space")
    parser.add_argument("--best_checkpoint_dir", type=str,
                        default='', help="default name is best_checkpoint_epoch{}.pth")
    parser.add_argument('--trial_id', type=int, default=0, metavar='N',
                        help='trial_id,start from 0')
    parser.add_argument("--layers", default=20, type=int)
    parser.add_argument("--lr", default=0.01, type=float)
    parser.add_argument("--batch_size", default=96, type=int)
    parser.add_argument("--log_frequency", default=10, type=int)
    parser.add_argument("--epochs", default=600, type=int)
    parser.add_argument("--aux_weight", default=0.4, type=float)
    parser.add_argument("--drop_path_prob", default=0.2, type=float)
    parser.add_argument("--workers", default=4, type=int)
    parser.add_argument("--class_num", default=10, type=int, help="cifar10")
    parser.add_argument("--channels", default=36, type=int)
    parser.add_argument("--grad_clip", default=6., type=float)
    args = parser.parse_args()

    mkdirs(args.result_path, args.log_path, args.best_checkpoint_dir)
    init_logger(args.log_path)
    logger.info(args)
    set_seed(args.trial_id) 
    logger.info("loading data")
    dataset_train, dataset_valid = datasets.get_dataset("cifar10", cutout_length=16, root=args.data_dir)

    model = CNN(32, 3, args.channels, args.class_num, args.layers, auxiliary=True, search=False)
    apply_fixed_architecture(model, args.best_selected_space_path)
    criterion = nn.CrossEntropyLoss()

    model.to(device)
    criterion.to(device)

    optimizer = torch.optim.SGD(model.parameters(), args.lr, momentum=0.9, weight_decay=3.0E-4)
    lr_scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, args.epochs, eta_min=1E-6)

    train_loader = torch.utils.data.DataLoader(dataset_train,
                                            batch_size=args.batch_size,
                                            shuffle=True,
                                            num_workers=args.workers,
                                            pin_memory=True)
    valid_loader = torch.utils.data.DataLoader(dataset_valid,
                                            batch_size=args.batch_size,
                                            shuffle=False,
                                            num_workers=args.workers,
                                            pin_memory=True)

    retrainer = PCdartsRetrainer(aux_weight=args.aux_weight,
                                grad_clip=args.grad_clip,
                                epochs=args.epochs,
                                log_frequency = args.log_frequency)
    # result = {"Accuracy": [], "Cost_time": ''}
    best_top1 = 0.
    start_time = time.time()
    for epoch in range(args.epochs):
        drop_prob = args.drop_path_prob * epoch / args.epochs
        model.drop_path_prob(drop_prob)

        # training
        retrainer.train(train_loader, model, optimizer, criterion, epoch)

        # validation
        cur_step = (epoch + 1) * len(train_loader)
        top1 = retrainer.validate(valid_loader, model, criterion, epoch, cur_step)
        # 后端在终端过滤，{"type": "Accuracy", "result": {"sequence": 1, "category": "epoch", "value":96.7}}
        logger.info({"type": "Accuracy", "result": {"sequence": epoch, "category": "epoch", "value": top1}})
        with open(args.result_path, "a") as file:
            file.write(str({"type": "Accuracy", "result": {"sequence": epoch, "category": "epoch", "value": top1}}) + '\n')
        # result["Accuracy"].append(top1)
        best_top1 = max(best_top1, top1)

        lr_scheduler.step()

    logger.info("Final best Prec@1 = {:.4%}".format(best_top1))
    cost_time = time.time() - start_time
    # 后端在终端过滤，{"type": "Cost_time", "result": {"value": "* s"}}
    logger.info({"type": "Cost_time", "result": {"value": str(cost_time) + ' s'}})
    with open(args.result_path, "a") as file:
        file.write(str({"type": "Cost_time", "result": {"value": str(cost_time) + ' s'}}))
    
    # result["Cost_time"] = str(cost_time) + ' s'
    # dump_global_result(args.result_path, result)
    save_best_checkpoint(args.best_checkpoint_dir, model, optimizer, epoch)
    logger.info("Save best checkpoint in {}".format(os.path.join(args.best_checkpoint_dir, "best_checkpoint_epoch{}.pth".format(epoch))))