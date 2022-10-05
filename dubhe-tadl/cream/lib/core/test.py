# Copyright (c) Microsoft Corporation.
# Licensed under the MIT License.
# Written by Hao Du and Houwen Peng
# email: haodu8-c@my.cityu.edu.hk and houwen.peng@microsoft.com

import time
import torch
import json
from collections import OrderedDict
from ..utils.util import AverageMeter, accuracy, reduce_tensor


def validate(epoch, model, loader, loss_fn, args, log_suffix='',
             logger=None, writer=None, local_rank=0,result_path=None):
    batch_time_m = AverageMeter()
    losses_m = AverageMeter()
    prec1_m = AverageMeter()
    prec5_m = AverageMeter()

    model.eval()

    end = time.time()
    last_idx = len(loader) - 1
    with torch.no_grad():
        for batch_idx, (input, target) in enumerate(loader):
            last_batch = batch_idx == last_idx

            output = model(input)
            if isinstance(output, (tuple, list)):
                output = output[0]

            # augmentation reduction
            reduce_factor = args.tta
            if reduce_factor > 1:
                output = output.unfold(
                    0,
                    reduce_factor,
                    reduce_factor).mean(
                    dim=2)
                target = target[0:target.size(0):reduce_factor]

            loss = loss_fn(output, target)
            prec1, prec5 = accuracy(output, target, topk=(1, 5))

            if args.num_gpu > 1:
                reduced_loss = reduce_tensor(loss.data, args.num_gpu)
                prec1 = reduce_tensor(prec1, args.num_gpu)
                prec5 = reduce_tensor(prec5, args.num_gpu)
            else:
                reduced_loss = loss.data

            torch.cuda.synchronize()

            losses_m.update(reduced_loss.item(), input.size(0))
            prec1_m.update(prec1.item(), output.size(0))
            prec5_m.update(prec5.item(), output.size(0))

            batch_time_m.update(time.time() - end)
            end = time.time()
            if local_rank == 0 and (last_batch or batch_idx % args.log_interval == 0):
                log_name = 'Test' + log_suffix
                logger.info(
                    '{0}: [{1:>4d}/{2}]  '
                    'Time: {batch_time.val:.3f} ({batch_time.avg:.3f})  '
                    'Loss: {loss.val:>7.4f} ({loss.avg:>6.4f})  '
                    'Prec@1: {top1.val:>7.4f} ({top1.avg:>7.4f})  '
                    'Prec@5: {top5.val:>7.4f} ({top5.avg:>7.4f})'.format(
                        log_name, batch_idx, last_idx,
                        batch_time=batch_time_m, loss=losses_m,
                        top1=prec1_m, top5=prec5_m))

                # print({'type': 'Accuracy', 'result': {'sequence': epoch, 'category': 'epoch', 'value': prec1_m.val}})


                if result_path is not None:
                    with open(result_path, "a") as ss_file:
                        ss_file.write(json.dumps(
                            {'type': 'Accuracy',
                             'result': {'sequence': epoch,
                                        'category': 'epoch',
                                        'value': prec1_m.val}}) + '\n')


                # writer.add_scalar(
                #     'Loss' + log_suffix + '/vaild',
                #     prec1_m.avg,
                #     epoch * len(loader) + batch_idx)
                # writer.add_scalar(
                #     'Accuracy' +
                #     log_suffix +
                #     '/vaild',
                #     prec1_m.avg,
                #     epoch *
                #     len(loader) +
                #     batch_idx)

    metrics = OrderedDict(
        [('loss', losses_m.avg), ('prec1', prec1_m.avg), ('prec5', prec5_m.avg)])

    return metrics
