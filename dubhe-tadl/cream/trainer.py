# https://github.com/microsoft/nni/blob/v2.0/examples/nas/cream/train.py
import sys

sys.path.append('../..')

import os
import sys
import time
import json
import torch
import numpy as np
import torch.nn as nn

from argparse import ArgumentParser

# import timm packages
from timm.loss import LabelSmoothingCrossEntropy
from timm.data import Dataset, create_loader
from timm.models import resume_checkpoint

# import apex as distributed package
# try:
#     from apex.parallel import DistributedDataParallel as DDP
#     from apex.parallel import convert_syncbn_model
#
#     USE_APEX = True
# except ImportError as e:
#     print(e)
#     from torch.nn.parallel import DistributedDataParallel as DDP
#
#     USE_APEX = False

# import models and training functions
from lib.utils.flops_table import FlopsEst
from lib.models.structures.supernet import gen_supernet
from lib.config import DEFAULT_CROP_PCT, IMAGENET_DEFAULT_STD, IMAGENET_DEFAULT_MEAN
from lib.utils.util import get_logger, \
    create_optimizer_supernet, create_supernet_scheduler

from pytorch.utils import mkdirs, str2bool
from pytorch.callbacks import LRSchedulerCallback
from pytorch.callbacks import ModelCheckpoint
from algorithms import CreamSupernetTrainer
from algorithms import RandomMutator


def parse_args():
    """See lib.utils.config"""
    parser = ArgumentParser()

    # path
    parser.add_argument("--checkpoint_dir", type=str, default='')
    parser.add_argument("--data_dir", type=str, default='./data')
    parser.add_argument("--experiment_dir", type=str, default='./')
    parser.add_argument("--model_name", type=str, default='trainer')
    parser.add_argument("--log_path", type=str, default='output/log')
    parser.add_argument("--result_path", type=str, default='output/result.json')
    parser.add_argument("--search_space_path", type=str, default='output/search_space.json')
    parser.add_argument("--best_selected_space_path", type=str,
                        default='output/selected_space.json')

    # int
    parser.add_argument("--acc_gap", type=int, default=5)
    parser.add_argument("--batch_size", type=int, default=1)
    parser.add_argument("--epochs", type=int, default=200)
    parser.add_argument("--flops_minimum", type=int, default=0)
    parser.add_argument("--flops_maximum", type=int, default=200)
    parser.add_argument("--image_size", type=int, default=224)
    parser.add_argument("--local_rank", type=int, default=0)
    parser.add_argument("--log_interval", type=int, default=50)
    parser.add_argument("--meta_sta_epoch", type=int, default=20)
    parser.add_argument("--num_classes", type=int, default=1000)
    parser.add_argument("--num_gpu", type=int, default=1)
    parser.add_argument("--pool_size", type=int, default=10)
    parser.add_argument("--trial_id", type=int, default=42)
    parser.add_argument("--slice_num", type=int, default=4)
    parser.add_argument("--tta", type=int, default=0)
    parser.add_argument("--update_iter", type=int, default=1300)
    parser.add_argument("--workers", type=int, default=4)

    # float
    parser.add_argument("--color_jitter", type=float, default=0.4)
    parser.add_argument("--dropout_rate", type=float, default=0.0)
    parser.add_argument("--lr", type=float, default=1e-2)
    parser.add_argument("--meta_lr", type=float, default=1e-4)
    parser.add_argument("--opt_eps", type=float, default=1e-2)
    parser.add_argument("--re_prob", type=float, default=0.2)
    parser.add_argument("--momentum", type=float, default=0.9)
    parser.add_argument("--smoothing", type=float, default=0.1)
    parser.add_argument("--weight_decay", type=float, default=1e-4)

    # bool
    parser.add_argument("--auto_resume", type=str2bool, default='False')
    parser.add_argument("--dil_conv", type=str2bool, default='False')
    parser.add_argument("--resunit", type=str2bool, default='False')
    parser.add_argument("--sync_bn", type=str2bool, default='False')
    parser.add_argument("--verbose", type=str2bool, default='False')

    # str
    # gp: type of global pool ["avg", "max", "avgmax", "avgmaxc"]
    parser.add_argument("--gp", type=str, default='avg')
    parser.add_argument("--interpolation", type=str, default='bilinear')
    parser.add_argument("--opt", type=str, default='sgd')
    parser.add_argument("--pick_method", type=str, default='meta')
    parser.add_argument("--re_mode", type=str, default='pixel')

    args = parser.parse_args()
    args.sync_bn = False
    args.verbose = False
    args.data_dir = args.data_dir + "/imagenet"
    return args


def main():
    args = parse_args()

    mkdirs(args.experiment_dir,
           args.best_selected_space_path,
           args.search_space_path,
           args.result_path,
           args.log_path)

    with open(args.result_path, "w") as ss_file:
        ss_file.write('')

    # resolve logging

    if len(args.checkpoint_dir > 1):
        mkdirs(args.checkpoint_dir + "/")
        args.checkpoint_dir = os.path.join(
            args.checkpoint_dir,
            "{}_{}".format(args.model_name, time.strftime("%Y-%m-%d_%H:%M:%S", time.localtime()))
        )
        if not os.path.exists(args.checkpoint_dir):
            os.mkdir(args.checkpoint_dir)

    if args.local_rank == 0:
        logger = get_logger(args.log_path)
    else:
        logger = None

    # initialize distributed parameters
    torch.cuda.set_device(args.local_rank)
    # torch.distributed.init_process_group(backend='nccl', init_method='env://')
    if args.local_rank == 0:
        logger.info(
            'Training on Process %d with %d GPUs.',
            args.local_rank, args.num_gpu)

    # fix random seeds
    torch.manual_seed(args.trial_id)
    torch.cuda.manual_seed_all(args.trial_id)
    np.random.seed(args.trial_id)
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark = False

    # generate supernet and optimizer
    model, sta_num, resolution, search_space = gen_supernet(
        flops_minimum=args.flops_minimum,
        flops_maximum=args.flops_maximum,
        num_classes=args.num_classes,
        drop_rate=args.dropout_rate,
        global_pool=args.gp,
        resunit=args.resunit,
        dil_conv=args.dil_conv,
        slice=args.slice_num,
        verbose=args.verbose,
        logger=logger)
    optimizer = create_optimizer_supernet(args, model)

    # number of choice blocks in supernet
    choice_num = len(model.blocks[7])
    if args.local_rank == 0:
        logger.info('Supernet created, param count: %d', (
            sum([m.numel() for m in model.parameters()])))
        logger.info('resolution: %d', resolution)
        logger.info('choice number: %d', choice_num)
        with open(args.search_space_path, "w") as f:
            print("dump search space.")
            json.dump({'search_space': search_space}, f)

    # initialize flops look-up table
    model_est = FlopsEst(model)
    flops_dict, flops_fixed = model_est.flops_dict, model_est.flops_fixed
    model = model.cuda()

    # convert model to distributed mode
    if args.sync_bn:
        try:
            # if USE_APEX:
            #     model = convert_syncbn_model(model)
            # else:
            model = torch.nn.SyncBatchNorm.convert_sync_batchnorm(model)
            if args.local_rank == 0:
                logger.info('Converted model to use Synchronized BatchNorm.')
        except Exception as exception:
            logger.info(
                'Failed to enable Synchronized BatchNorm. '
                'Install Apex or Torch >= 1.1 with Exception %s', exception)
    # if USE_APEX:
    #     model = DDP(model, delay_allreduce=True)
    # else:
    #     if args.local_rank == 0:
    #         logger.info(
    #             "Using torch DistributedDataParallel. Install NVIDIA Apex for Apex DDP.")
    #     # can use device str in Torch >= 1.1
    #     model = DDP(model, device_ids=[args.local_rank], find_unused_parameters=True)

    # optionally resume from a checkpoint
    resume_epoch = None
    if False:  # args.auto_resume:
        checkpoint = torch.load(args.experiment_dir)

        model.load_state_dict(checkpoint['child_model_state_dict'])
        optimizer.load_state_dict(checkpoint['optimizer_state_dict'])
        resume_epoch = checkpoint['epoch']

    # create learning rate scheduler
    lr_scheduler, num_epochs = create_supernet_scheduler(optimizer, args.epochs, args.num_gpu,
                                                         args.batch_size, args.lr)

    start_epoch = resume_epoch if resume_epoch is not None else 0
    if start_epoch > 0:
        lr_scheduler.step(start_epoch)

    if args.local_rank == 0:
        logger.info('Scheduled epochs: %d', num_epochs)

    # imagenet train dataset
    train_dir = os.path.join(args.data_dir, 'train')
    if not os.path.exists(train_dir):
        logger.info('Training folder does not exist at: %s', train_dir)
        sys.exit()

    dataset_train = Dataset(train_dir)
    loader_train = create_loader(
        dataset_train,
        input_size=(3, args.image_size, args.image_size),
        batch_size=args.batch_size,
        is_training=True,
        use_prefetcher=True,
        re_prob=args.re_prob,
        re_mode=args.re_mode,
        color_jitter=args.color_jitter,
        interpolation='random',
        num_workers=args.workers,
        distributed=False,
        collate_fn=None,
        crop_pct=DEFAULT_CROP_PCT,
        mean=IMAGENET_DEFAULT_MEAN,
        std=IMAGENET_DEFAULT_STD
    )

    # imagenet validation dataset
    eval_dir = os.path.join(args.data_dir, 'val')
    if not os.path.isdir(eval_dir):
        logger.info('Validation folder does not exist at: %s', eval_dir)
        sys.exit()
    dataset_eval = Dataset(eval_dir)
    loader_eval = create_loader(
        dataset_eval,
        input_size=(3, args.image_size, args.image_size),
        batch_size=4 * args.batch_size,
        is_training=False,
        use_prefetcher=True,
        num_workers=args.workers,
        distributed=False,
        crop_pct=DEFAULT_CROP_PCT,
        mean=IMAGENET_DEFAULT_MEAN,
        std=IMAGENET_DEFAULT_STD,
        interpolation=args.interpolation
    )

    # whether to use label smoothing
    if args.smoothing > 0.:
        train_loss_fn = LabelSmoothingCrossEntropy(
            smoothing=args.smoothing).cuda()
        validate_loss_fn = nn.CrossEntropyLoss().cuda()
    else:
        train_loss_fn = nn.CrossEntropyLoss().cuda()
        validate_loss_fn = train_loss_fn

    mutator = RandomMutator(model)

    _callbacks = [LRSchedulerCallback(lr_scheduler)]
    if len(args.checkpoint_dir) > 1:
        _callbacks.append(ModelCheckpoint(checkpoint_dir))
    trainer = CreamSupernetTrainer(args.best_selected_space_path, model, train_loss_fn,
                                   validate_loss_fn,
                                   optimizer, num_epochs, loader_train, loader_eval,
                                   result_path=args.result_path,
                                   mutator=mutator,
                                   batch_size=args.batch_size,
                                   log_frequency=args.log_interval,
                                   meta_sta_epoch=args.meta_sta_epoch,
                                   update_iter=args.update_iter,
                                   slices=args.slice_num,
                                   pool_size=args.pool_size,
                                   pick_method=args.pick_method,
                                   choice_num=choice_num,
                                   sta_num=sta_num,
                                   acc_gap=args.acc_gap,
                                   flops_dict=flops_dict,
                                   flops_fixed=flops_fixed,
                                   local_rank=args.local_rank,
                                   callbacks=_callbacks)

    trainer.train()


if __name__ == '__main__':
    main()
