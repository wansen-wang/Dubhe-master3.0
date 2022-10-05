# Copyright (c) Microsoft Corporation.
# Licensed under the MIT License.
# Written by Hao Du and Houwen Peng
# email: haodu8-c@my.cityu.edu.hk and houwen.peng@microsoft.com
import sys

sys.path.append('../..')
import os
import json
import time
import timm
import torch
import numpy as np
import torch.nn as nn

from argparse import ArgumentParser
# from torch.utils.tensorboard import SummaryWriter

# import timm packages
from timm.optim import create_optimizer
from timm.models import resume_checkpoint
from timm.scheduler import create_scheduler
from timm.data import Dataset, create_loader
from timm.utils import CheckpointSaver, ModelEma, update_summary
from timm.loss import LabelSmoothingCrossEntropy

# import apex as distributed package
try:
    from apex import amp
    from apex.parallel import DistributedDataParallel as DDP
    from apex.parallel import convert_syncbn_model

    HAS_APEX = True
except ImportError as e:
    print(e)
    from torch.nn.parallel import DistributedDataParallel as DDP

    HAS_APEX = False

# import models and training functions
from pytorch.utils import mkdirs, save_best_checkpoint, str2bool
from lib.core.test import validate
from lib.core.retrain import train_epoch
from lib.models.structures.childnet import gen_childnet
from lib.utils.util import get_logger, get_model_flops_params
from lib.config import DEFAULT_CROP_PCT, IMAGENET_DEFAULT_MEAN, IMAGENET_DEFAULT_STD


def parse_args():
    """See lib.utils.config"""
    parser = ArgumentParser()

    # path
    parser.add_argument("--best_checkpoint_dir", type=str, default='./output/best_checkpoint/')
    parser.add_argument("--checkpoint_dir", type=str, default='./output/checkpoints/')
    parser.add_argument("--data_dir", type=str, default='./data')
    parser.add_argument("--experiment_dir", type=str, default='./')
    parser.add_argument("--model_name", type=str, default='retrainer')
    parser.add_argument("--log_path", type=str, default='output/log')
    parser.add_argument("--result_path", type=str, default='output/result.json')
    parser.add_argument("--best_selected_space_path", type=str,
                        default='output/selected_space.json')

    # int
    parser.add_argument("--acc_gap", type=int, default=5)
    parser.add_argument("--batch_size", type=int, default=32)
    parser.add_argument("--cooldown_epochs", type=int, default=10)
    parser.add_argument("--decay_epochs", type=int, default=10)
    parser.add_argument("--epochs", type=int, default=200)
    parser.add_argument("--flops_minimum", type=int, default=0)
    parser.add_argument("--flops_maximum", type=int, default=200)
    parser.add_argument("--image_size", type=int, default=224)
    parser.add_argument("--local_rank", type=int, default=0)
    parser.add_argument("--log_interval", type=int, default=50)
    parser.add_argument("--meta_sta_epoch", type=int, default=20)
    parser.add_argument("--num_classes", type=int, default=1000)
    parser.add_argument("--num_gpu", type=int, default=1)
    parser.add_argument("--parience_epochs", type=int, default=10)
    parser.add_argument("--pool_size", type=int, default=10)
    parser.add_argument("--recovery_interval", type=int, default=10)
    parser.add_argument("--trial_id", type=int, default=42)
    parser.add_argument("--selection", type=int, default=-1)
    parser.add_argument("--slice_num", type=int, default=4)
    parser.add_argument("--tta", type=int, default=0)
    parser.add_argument("--update_iter", type=int, default=1300)
    parser.add_argument("--val_batch_mul", type=int, default=4)
    parser.add_argument("--warmup_epochs", type=int, default=3)
    parser.add_argument("--workers", type=int, default=4)

    # float
    parser.add_argument("--color_jitter", type=float, default=0.4)
    parser.add_argument("--decay_rate", type=float, default=0.1)
    parser.add_argument("--dropout_rate", type=float, default=0.0)
    parser.add_argument("--ema_decay", type=float, default=0.998)
    parser.add_argument("--lr", type=float, default=1e-2)
    parser.add_argument("--meta_lr", type=float, default=1e-4)
    parser.add_argument("--re_prob", type=float, default=0.2)
    parser.add_argument("--opt_eps", type=float, default=1e-2)
    parser.add_argument("--momentum", type=float, default=0.9)
    parser.add_argument("--min_lr", type=float, default=1e-5)
    parser.add_argument("--smoothing", type=float, default=0.1)
    parser.add_argument("--weight_decay", type=float, default=1e-4)
    parser.add_argument("--warmup_lr", type=float, default=1e-4)

    # bool
    parser.add_argument("--auto_resume", type=str2bool, default='False')
    parser.add_argument("--dil_conv", type=str2bool, default='False')
    parser.add_argument("--ema_cpu", type=str2bool, default='False')
    parser.add_argument("--pin_mem", type=str2bool, default='True')
    parser.add_argument("--resunit", type=str2bool, default='False')
    parser.add_argument("--save_images", type=str2bool, default='False')
    parser.add_argument("--sync_bn", type=str2bool, default='False')
    parser.add_argument("--use_ema", type=str2bool, default='False')
    parser.add_argument("--verbose", type=str2bool, default='False')

    # str
    parser.add_argument("--aa", type=str, default='rand-m9-mstd0.5')
    parser.add_argument("--eval_metrics", type=str, default='prec1')
    # gp: type of global pool ["avg", "max", "avgmax", "avgmaxc"]
    parser.add_argument("--gp", type=str, default='avg')
    parser.add_argument("--interpolation", type=str, default='bilinear')
    parser.add_argument("--opt", type=str, default='sgd')
    parser.add_argument("--pick_method", type=str, default='meta')
    parser.add_argument("--re_mode", type=str, default='pixel')
    parser.add_argument("--sched", type=str, default='sgd')

    args = parser.parse_args()
    args.sync_bn = False
    args.verbose = False
    args.data_dir = args.data_dir + "/imagenet"
    return args


def main():
    args = parse_args()

    mkdirs(args.checkpoint_dir + "/",
           args.experiment_dir,
           args.best_selected_space_path,
           args.result_path)
    with open(args.result_path, "w") as ss_file:
        ss_file.write('')

    if len(args.checkpoint_dir > 1):
        mkdirs(args.best_checkpoint_dir + "/")

        args.checkpoint_dir = os.path.join(
            args.checkpoint_dir,
            "{}_{}".format(args.model_name, time.strftime("%Y-%m-%d_%H:%M:%S", time.localtime()))
        )
        if not os.path.exists(args.checkpoint_dir):
            os.mkdir(args.checkpoint_dir)

    # resolve logging
    if args.local_rank == 0:
        logger = get_logger(args.log_path)
        writer = None  # SummaryWriter(os.path.join(output_dir, 'runs'))
    else:
        writer, logger = None, None

    # retrain model selection

    if args.selection == -1:
        if os.path.exists(args.best_selected_space_path):
            with open(args.best_selected_space_path, "r") as f:
                arch_list = json.load(f)['selected_space']
        else:
            args.selection = 14
            logger.warning("args.best_selected_space_path is not exist. Set selection to 14.")

    if args.selection == 481:
        arch_list = [
            [0], [
                3, 4, 3, 1], [
                3, 2, 3, 0], [
                3, 3, 3, 1], [
                3, 3, 3, 3], [
                3, 3, 3, 3], [0]]
        args.image_size = 224
    elif args.selection == 43:
        arch_list = [[0], [3], [3, 1], [3, 1], [3, 3, 3], [3, 3], [0]]
        args.image_size = 96
    elif args.selection == 14:
        arch_list = [[0], [3], [3, 3], [3, 3], [3], [3], [0]]
        args.image_size = 64
    elif args.selection == 112:
        arch_list = [[0], [3], [3, 3], [3, 3], [3, 3, 3], [3, 3], [0]]
        args.image_size = 160
    elif args.selection == 287:
        arch_list = [[0], [3], [3, 3], [3, 1, 3], [3, 3, 3, 3], [3, 3, 3], [0]]
        args.image_size = 224
    elif args.selection == 604:
        arch_list = [
            [0], [
                3, 3, 2, 3, 3], [
                3, 2, 3, 2, 3], [
                3, 2, 3, 2, 3], [
                3, 3, 2, 2, 3, 3], [
                3, 3, 2, 3, 3, 3], [0]]
        args.image_size = 224
    elif args.selection == -1:
        args.image_size = 224
    else:
        raise ValueError("Model Retrain Selection is not Supported!")

    print(arch_list)
    # define childnet architecture from arch_list
    stem = ['ds_r1_k3_s1_e1_c16_se0.25', 'cn_r1_k1_s1_c320_se0.25']

    # TODO: this param from NNI is different from microsoft/Cream.
    choice_block_pool = ['ir_r1_k3_s2_e4_c24_se0.25',
                         'ir_r1_k5_s2_e4_c40_se0.25',
                         'ir_r1_k3_s2_e6_c80_se0.25',
                         'ir_r1_k3_s1_e6_c96_se0.25',
                         'ir_r1_k5_s2_e6_c192_se0.25']
    arch_def = [[stem[0]]] + [[choice_block_pool[idx]
                               for repeat_times in range(len(arch_list[idx + 1]))]
                              for idx in range(len(choice_block_pool))] + [[stem[1]]]

    # generate childnet
    model = gen_childnet(
        arch_list,
        arch_def,
        num_classes=args.num_classes,
        drop_rate=args.dropout_rate,
        global_pool=args.gp)

    # initialize distributed parameters
    distributed = args.num_gpu > 1
    torch.cuda.set_device(args.local_rank)
    if args.local_rank == 0:
        logger.info(
            'Training on Process {} with {} GPUs.'.format(
                args.local_rank, args.num_gpu))

    # fix random seeds
    torch.manual_seed(args.trial_id)
    torch.cuda.manual_seed_all(args.trial_id)
    np.random.seed(args.trial_id)
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark = False

    # get parameters and FLOPs of model
    if args.local_rank == 0:
        macs, params = get_model_flops_params(model, input_size=(
            1, 3, args.image_size, args.image_size))
        logger.info(
            '[Model-{}] Flops: {} Params: {}'.format(args.selection, macs, params))

    # create optimizer
    model = model.cuda()
    optimizer = create_optimizer(args, model)

    # optionally resume from a checkpoint
    resume_epoch = None
    if args.auto_resume:
        if int(timm.__version__[2]) >= 3:
            resume_epoch = resume_checkpoint(model, args.experiment_dir, optimizer)
        else:
            resume_state, resume_epoch = resume_checkpoint(model, args.experiment_dir)
            optimizer.load_state_dict(resume_state['optimizer'])
            del resume_state

    model_ema = None
    if args.use_ema:
        model_ema = ModelEma(
            model,
            decay=args.ema_decay,
            device='cpu' if args.ema_cpu else '',
            resume=args.experiment_dir if args.auto_resume else None)

    # initialize training parameters
    eval_metric = args.eval_metrics
    best_metric, best_epoch, saver = None, None, None
    if args.local_rank == 0:
        decreasing = True if eval_metric == 'loss' else False
        if int(timm.__version__[2]) >= 3:
            saver = CheckpointSaver(model, optimizer,
                                    checkpoint_dir=args.checkpoint_dir,
                                    recovery_dir=args.checkpoint_dir,
                                    model_ema=model_ema,
                                    decreasing=decreasing,
                                    max_history=2)
        else:
            saver = CheckpointSaver(
                checkpoint_dir=args.checkpoint_dir,
                recovery_dir=args.checkpoint_dir,
                decreasing=decreasing,
                max_history=2)

    if distributed:
        torch.distributed.init_process_group(backend='nccl', init_method='env://')

        if args.sync_bn:
            try:
                if HAS_APEX:
                    model = convert_syncbn_model(model)
                else:
                    model = torch.nn.SyncBatchNorm.convert_sync_batchnorm(model)
                if args.local_rank == 0:
                    logger.info('Converted model to use Synchronized BatchNorm.')
            except Exception as e:
                if args.local_rank == 0:
                    logger.error(
                        'Failed to enable Synchronized BatchNorm. '
                        'Install Apex or Torch >= 1.1 with exception {}'.format(e))
        if HAS_APEX:
            model = DDP(model, delay_allreduce=True)
        else:
            if args.local_rank == 0:
                logger.info(
                    "Using torch DistributedDataParallel. Install NVIDIA Apex for Apex DDP.")
            # can use device str in Torch >= 1.1
            model = DDP(model, device_ids=[args.local_rank], find_unused_parameters=True)

    # imagenet train dataset
    train_dir = os.path.join(args.data_dir, 'train')
    if not os.path.exists(train_dir) and args.local_rank == 0:
        logger.error('Training folder does not exist at: {}'.format(train_dir))
        exit(1)
    dataset_train = Dataset(train_dir)
    loader_train = create_loader(
        dataset_train,
        input_size=(3, args.image_size, args.image_size),
        batch_size=args.batch_size,
        is_training=True,
        color_jitter=args.color_jitter,
        auto_augment=args.aa,
        num_aug_splits=0,
        crop_pct=DEFAULT_CROP_PCT,
        mean=IMAGENET_DEFAULT_MEAN,
        std=IMAGENET_DEFAULT_STD,
        num_workers=args.workers,
        distributed=distributed,
        collate_fn=None,
        pin_memory=args.pin_mem,
        interpolation='random',
        re_mode=args.re_mode,
        re_prob=args.re_prob
    )

    # imagenet validation dataset
    eval_dir = os.path.join(args.data_dir, 'val')
    if not os.path.exists(eval_dir) and args.local_rank == 0:
        logger.error(
            'Validation folder does not exist at: {}'.format(eval_dir))
        exit(1)
    dataset_eval = Dataset(eval_dir)
    loader_eval = create_loader(
        dataset_eval,
        input_size=(3, args.image_size, args.image_size),
        batch_size=args.val_batch_mul * args.batch_size,
        is_training=False,
        interpolation=args.interpolation,
        crop_pct=DEFAULT_CROP_PCT,
        mean=IMAGENET_DEFAULT_MEAN,
        std=IMAGENET_DEFAULT_STD,
        num_workers=args.workers,
        distributed=distributed,
        pin_memory=args.pin_mem
    )

    # whether to use label smoothing
    if args.smoothing > 0.:
        train_loss_fn = LabelSmoothingCrossEntropy(
            smoothing=args.smoothing).cuda()
        validate_loss_fn = nn.CrossEntropyLoss().cuda()
    else:
        train_loss_fn = nn.CrossEntropyLoss().cuda()
        validate_loss_fn = train_loss_fn

    # create learning rate scheduler
    lr_scheduler, num_epochs = create_scheduler(args, optimizer)
    start_epoch = resume_epoch if resume_epoch is not None else 0
    if start_epoch > 0:
        lr_scheduler.step(start_epoch)
    if args.local_rank == 0:
        logger.info('Scheduled epochs: {}'.format(num_epochs))

    try:
        best_record, best_ep = 0, 0
        for epoch in range(start_epoch, num_epochs):
            if distributed:
                loader_train.sampler.set_epoch(epoch)

            train_metrics = train_epoch(
                epoch,
                model,
                loader_train,
                optimizer,
                train_loss_fn,
                args,
                lr_scheduler=lr_scheduler,
                saver=saver,
                output_dir=args.checkpoint_dir,
                model_ema=model_ema,
                logger=logger,
                writer=writer,
                local_rank=args.local_rank)

            eval_metrics = validate(
                epoch,
                model,
                loader_eval,
                validate_loss_fn,
                args,
                logger=logger,
                writer=writer,
                local_rank=args.local_rank,
                result_path=args.result_path
            )

            if model_ema is not None and not args.ema_cpu:
                ema_eval_metrics = validate(
                    epoch,
                    model_ema.ema,
                    loader_eval,
                    validate_loss_fn,
                    args,
                    log_suffix='_EMA',
                    logger=logger,
                    writer=writer,
                    local_rank=args.local_rank
                )
                eval_metrics = ema_eval_metrics

            if lr_scheduler is not None:
                lr_scheduler.step(epoch + 1, eval_metrics[eval_metric])

            update_summary(epoch, train_metrics, eval_metrics, os.path.join(
                args.checkpoint_dir, 'summary.csv'), write_header=best_metric is None)

            if saver is not None:
                # save proper checkpoint with eval metric
                save_metric = eval_metrics[eval_metric]

                if int(timm.__version__[2]) >= 3:
                    best_metric, best_epoch = saver.save_checkpoint(epoch, metric=save_metric)
                else:
                    best_metric, best_epoch = saver.save_checkpoint(
                        model, optimizer, args,
                        epoch=epoch, metric=save_metric)

            if best_record < eval_metrics[eval_metric]:
                best_record = eval_metrics[eval_metric]
                best_ep = epoch

            if args.local_rank == 0:
                logger.info(
                    '*** Best metric: {0} (epoch {1})'.format(best_record, best_ep))

    except KeyboardInterrupt:
        pass

    if best_metric is not None:
        logger.info(
            '*** Best metric: {0} (epoch {1})'.format(best_metric, best_epoch))
        save_best_checkpoint(args.best_checkpoint_dir, model, optimizer, epoch)


if __name__ == '__main__':
    main()
