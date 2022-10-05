import sys
sys.path.append('..'+ '/' + '..')
import os
import logging
import pickle
import shutil
import random
import math
import time
import datetime
import argparse
import distutils.util
import numpy as np
import json
import torch
from torch import nn
from torch import optim
from torch.utils.data import DataLoader
import torch.nn.functional as Func

from macro import GeneralNetwork
from micro import MicroNetwork
import datasets
from utils import accuracy, reward_accuracy
from pytorch.fixed import apply_fixed_architecture
from pytorch.utils import AverageMeterGroup, to_device, save_best_checkpoint

logger = logging.getLogger("enas-retrain")

# TODO:
def set_random_seed(seed):
    logger.info("set random seed for data reading: {}".format(seed))
    random.seed(seed)
    os.environ['PYTHONHASHSEED'] = str(seed)
    np.random.seed(seed)
    random.seed(seed)
    torch.manual_seed_all(seed)
    if FLAGS.is_cuda:
        torch.cuda.manual_seed_all(seed)
        torch.backends.cudnn.deterministic = True


# TODO: parser args
def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--data_dir",
        type=str,
        default="./data",
        help="Directory containing the dataset and embedding file. (default: %(default)s)")
    parser.add_argument("--search_space_path", type=str,
                        default='./search_space.json', help="search_space directory")
    parser.add_argument(
        "--selected_space_path",
        type=str,
        default="./selected_space.json",
        # required=True,
        help="Architecture json file. (default: %(default)s)")
    parser.add_argument("--result_path", type=str,
                        default='./result.json', help="res directory")
    parser.add_argument('--trial_id', type=int, default=0, metavar='N',
                    help='trial_id,start from 0')
    parser.add_argument(
        "--output_dir",
        type=str,
        default="./output",
        help="The output directory. (default: %(default)s)")
    parser.add_argument(
        "--best_checkpoint_dir",
        type=str,
        default="best_checkpoint",
        help="Path for saved checkpoints. (default: %(default)s)")
    parser.add_argument("--search_for",
        choices=["macro", "micro"],
        default="micro")
    parser.add_argument(
        "--batch_size",
        type=int,
        default=128,
        help="Number of samples each batch for training. (default: %(default)s)")
    parser.add_argument(
        "--eval_batch_size",
        type=int,
        default=128,
        help="Number of samples each batch for evaluation. (default: %(default)s)")
    parser.add_argument(
        "--class_num",
        type=int,
        default=10,
        help="The number of categories. (default: %(default)s)")
    parser.add_argument(
        "--epochs",
        type=int,
        default=10,
        help="The number of training epochs. (default: %(default)s)")
    parser.add_argument(
        "--child_lr",
        type=float,
        default=0.02,
        help="The initial learning rate. (default: %(default)s)")
    parser.add_argument(
        "--is_cuda",
        type=distutils.util.strtobool,
        default=True,
        help="Specify the device type. (default: %(default)s)")
    parser.add_argument(
        "--load_checkpoint",
        type=distutils.util.strtobool,
        default=False,
        help="Whether to load checkpoint. (default: %(default)s)")
    parser.add_argument(
        "--log_every",
        type=int,
        default=50,
        help="How many steps to log. (default: %(default)s)")
    parser.add_argument(
        "--eval_every_epochs",
        type=int,
        default=1,
        help="How many epochs to eval. (default: %(default)s)")
    parser.add_argument(
        "--child_grad_bound",
        type=float,
        default=5.0,
        help="The threshold for gradient clipping. (default: %(default)s)") #
    parser.add_argument(
        "--child_lr_decay_scheme",
        type=str,
        default="cosine",
        help="Learning rate annealing strategy, only 'cosine' supported. (default: %(default)s)") #todo: remove
    parser.add_argument(
        "--child_lr_T_0",
        type=int,
        default=10,
        help="The length of one cycle. (default: %(default)s)") # todo: use for
    parser.add_argument(
        "--child_lr_T_mul",
        type=int,
        default=2,
        help="The multiplication factor per cycle. (default: %(default)s)") # todo: use for
    parser.add_argument(
        "--child_l2_reg",
        type=float,
        default=3e-6,
        help="Weight decay factor. (default: %(default)s)")
    parser.add_argument(
        "--child_lr_max",
        type=float,
        default=0.002,
        help="The max learning rate. (default: %(default)s)")
    parser.add_argument(
        "--child_lr_min",
        type=float,
        default=0.001,
        help="The min learning rate. (default: %(default)s)")
    parser.add_argument(
        "--multi_path",
        type=distutils.util.strtobool,
        default=False,
        help="Search for multiple path in the architecture. (default: %(default)s)") # todo: use for
    parser.add_argument(
        "--is_mask",
        type=distutils.util.strtobool,
        default=True,
        help="Apply mask. (default: %(default)s)")
    global FLAGS
    FLAGS = parser.parse_args()


def print_user_flags(FLAGS, line_limit=80):
    log_strings = "\n" + "-" * line_limit + "\n"
    for flag_name in sorted(vars(FLAGS)):
        value = "{}".format(getattr(FLAGS, flag_name))
        log_string = flag_name
        log_string += "." * (line_limit - len(flag_name) - len(value))
        log_string += value
        log_strings = log_strings + log_string
        log_strings = log_strings + "\n"
    log_strings += "-" * line_limit
    logger.info(log_strings)

def eval_once(child_model, device, eval_set, criterion, valid_dataloader=None, test_dataloader=None):
    if eval_set == "test":
        assert test_dataloader is not None
        dataloader = test_dataloader
    elif eval_set == "valid":
        assert valid_dataloader is not None
        dataloader = valid_dataloader
    else:
        raise NotImplementedError("Unknown eval_set '{}'".format(eval_set))

    tot_acc = 0
    tot = 0
    losses = []

    with torch.no_grad():  # save memory
        for batch in dataloader:

            x, y = batch
            x, y = to_device(x, device), to_device(y, device)
            logits = child_model(x)

            if isinstance(logits, tuple):
                logits, aux_logits = logits
                aux_loss = criterion(aux_logits, y)
            else:
                aux_loss = 0.

            loss = criterion(logits, y)
            loss = loss + aux_weight * aux_loss
            #             loss = loss.mean()
            preds = logits.argmax(dim=1).long()
            acc = torch.eq(preds, y.long()).long().sum().item()

            losses.append(loss)
            tot_acc += acc
            tot += len(y)

    losses = torch.tensor(losses)
    loss = losses.mean()
    if tot > 0:
        final_acc = float(tot_acc) / tot
    else:
        final_acc = 0
        logger.info("Error in calculating final_acc")
    return final_acc, loss

# TODO: learning rate scheduler
def update_lr(
        optimizer,
        epoch,
        l2_reg=1e-4,
        lr_warmup_val=None,
        lr_init=0.1,
        lr_decay_scheme="cosine",
        lr_max=0.002,
        lr_min=0.000000001,
        lr_T_0=4,
        lr_T_mul=1,
        sync_replicas=False,
        num_aggregate=None,
        num_replicas=None):
    if lr_decay_scheme == "cosine":
        assert lr_max is not None, "Need lr_max to use lr_cosine"
        assert lr_min is not None, "Need lr_min to use lr_cosine"
        assert lr_T_0 is not None, "Need lr_T_0 to use lr_cosine"
        assert lr_T_mul is not None, "Need lr_T_mul to use lr_cosine"

        T_i = lr_T_0
        t_epoch = epoch
        last_reset = 0
        while True:
            t_epoch -= T_i
            if t_epoch < 0:
              break
            last_reset += T_i
            T_i *= lr_T_mul

        T_curr = epoch - last_reset

        def _update():
            rate = T_curr / T_i * 3.1415926
            lr = lr_min + 0.5 * (lr_max - lr_min) * (1.0 + math.cos(rate))
            return lr

        learning_rate = _update()
    else:
        raise ValueError("Unknown learning rate decay scheme {}".format(lr_decay_scheme))

    #update lr in optimizer
    for params_group in optimizer.param_groups:
        params_group['lr'] = learning_rate
    return learning_rate

def train(device, output_dir='./output'):
    workers = 4
    data = 'cifar10'

    data_dir = FLAGS.data_dir
    output_dir = FLAGS.output_dir
    checkpoint_dir = FLAGS.best_checkpoint_dir
    batch_size = FLAGS.batch_size
    eval_batch_size = FLAGS.eval_batch_size
    class_num = FLAGS.class_num
    epochs = FLAGS.epochs
    child_lr = FLAGS.child_lr
    is_cuda = FLAGS.is_cuda
    load_checkpoint = FLAGS.load_checkpoint
    log_every = FLAGS.log_every
    eval_every_epochs = FLAGS.eval_every_epochs

    child_grad_bound = FLAGS.child_grad_bound
    child_l2_reg = FLAGS.child_l2_reg

    logger.info("Build dataloader")
    dataset_train, dataset_valid = datasets.get_dataset("cifar10")
    n_train = len(dataset_train)
    split = n_train // 10
    indices = list(range(n_train))
    train_sampler = torch.utils.data.sampler.SubsetRandomSampler(indices[:-split])
    valid_sampler = torch.utils.data.sampler.SubsetRandomSampler(indices[-split:])
    train_dataloader = torch.utils.data.DataLoader(dataset_train,
                                                   batch_size=batch_size,
                                                   sampler=train_sampler,
                                                   num_workers=workers)
    valid_dataloader = torch.utils.data.DataLoader(dataset_train,
                                                   batch_size=batch_size,
                                                   sampler=valid_sampler,
                                                   num_workers=workers)
    test_dataloader = torch.utils.data.DataLoader(dataset_valid,
                                                  batch_size=batch_size,
                                                  num_workers=workers)



    criterion = nn.CrossEntropyLoss()
    optimizer = torch.optim.SGD(child_model.parameters(), 0.05, momentum=0.9, weight_decay=1.0E-4, nesterov=True)
    # optimizer = optim.Adam(child_model.parameters(), eps=1e-3, weight_decay=FLAGS.child_l2_reg)
    # TODO
    lr_scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs, eta_min=0.001)

    # move model to CPU/GPU device
    child_model.to(device)
    criterion.to(device)

    logger.info('Start training')
    start_time = time.time()
    step = 0

    # save path
    if not os.path.exists(output_dir):
        os.mkdir(output_dir)
    # model_save_path = os.path.join(output_dir, "model.pth")
    # best_model_save_path = os.path.join(output_dir, "best_model.pth")
    best_acc = 0
    start_epoch = 0

    # TODO: load checkpoints

    # train
    for epoch in range(start_epoch, epochs):
        lr = update_lr(optimizer,
            epoch,
            l2_reg= 1e-4,
            lr_warmup_val=None,
            lr_init=FLAGS.child_lr,
            lr_decay_scheme=FLAGS.child_lr_decay_scheme,
            lr_max=0.05,
            lr_min=0.001,
            lr_T_0=10,
            lr_T_mul=2)
        child_model.train()
        for batch in train_dataloader:
            step += 1

            x, y = batch
            x, y = to_device(x, device), to_device(y, device)
            logits = child_model(x)

            if isinstance(logits, tuple):
                logits, aux_logits = logits
                aux_loss = criterion(aux_logits, y)
            else:
                aux_loss = 0.

            acc = accuracy(logits, y)
            loss = criterion(logits, y)
            loss = loss + aux_weight * aux_loss

            optimizer.zero_grad()
            loss.backward()
            grad_norm = 0
            trainable_params = child_model.parameters()

            for param in trainable_params:
                nn.utils.clip_grad_norm_(param, child_grad_bound)  # clip grad

            optimizer.step()

            if step % log_every == 0:
                curr_time = time.time()
                log_string = ""
                log_string += "epoch={:<6d}".format(epoch)
                log_string += "ch_step={:<6d}".format(step)
                log_string += " loss={:<8.6f}".format(loss)
                log_string += " lr={:<8.4f}".format(lr)
                log_string += " |g|={:<8.4f}".format(grad_norm)
                log_string += " tr_acc={:<8.4f}/{:>3d}".format(acc['acc1'], logits.size()[0])
                log_string += " mins={:<10.2f}".format(float(curr_time - start_time) / 60)
                logger.info(log_string)

        epoch += 1
        save_state = {
            'step': step,
            'epoch': epoch,
            'child_model_state_dict': child_model.state_dict(),
            'optimizer_state_dict': optimizer.state_dict()}
        #         print(' Epoch {:<3d} loss: {:<.2f} '.format(epoch, loss))
        # torch.save(save_state, model_save_path)
        child_model.eval()
        logger.info("Epoch {}: Eval".format(epoch))
        eval_acc, eval_loss = eval_once(child_model, device, "test", criterion, test_dataloader=test_dataloader)
        logger.info(
            "ch_step={} {}_accuracy={:<6.4f} {}_loss={:<6.4f}".format(step, "test", eval_acc, "test", eval_loss))
        if eval_acc > best_acc:
            best_acc = eval_acc
            logger.info("Save best model")
            # save_state = {
            #     'step': step,
            #     'epoch': epoch,
            #     'child_model_state_dict': child_model.state_dict(),
            #     'optimizer_state_dict': optimizer.state_dict()}
            # torch.save(save_state, best_model_save_path)
            save_best_checkpoint(checkpoint_dir, child_model, optimizer, epoch)

        result['accuracy'].append('Epoch {} acc: {:<6.4f}'.format(epoch, eval_acc,))

        acc_l.append(eval_acc)

        print(result['accuracy'][-1])

    print('max acc %.4f at epoch: %i'%(max(acc_l), np.argmax(np.array(acc_l))))
    print('Time cost: %.4f hours'%( float(time.time() - start_time) /3600.  ))
    return result

# macro = True
parse_args()
child_fixed_arc = FLAGS.selected_space_path  # './macro_seletced_space'
search_for = FLAGS.search_for
# 设置随机种子
torch.manual_seed(FLAGS.trial_id)
torch.cuda.manual_seed_all(FLAGS.trial_id)
np.random.seed(FLAGS.trial_id)
random.seed(FLAGS.trial_id)

aux_weight = 0.4
result = {'accuracy':[]}
acc_l = []

# decode human readable search space to model
def convert_selected_space_format():
    # with open('./macro_selected_space.json') as js:
    with open(child_fixed_arc) as js:
        selected_space = json.load(js)

    ops = selected_space['op_list']
    selected_space.pop('op_list')
    new_selected_space = {}

    for key, value in selected_space.items():
        # for macro
        if FLAGS.search_for == 'macro':
            new_key = key.split('_')[-1]
        # for micro
        elif FLAGS.search_for == 'micro':
            new_key = key

        if len(value) > 1 or len(value)==0:
            new_value = value
        elif  len(value) > 0 and value[0] in ops:
            new_value = ops.index(value[0])
        else:
            new_value = value[0]
        new_selected_space[new_key] = new_value
    return new_selected_space

fixed_arc = convert_selected_space_format()
# TODO : macro search or micro search
if FLAGS.search_for == 'macro':
    child_model = GeneralNetwork()
elif FLAGS.search_for == 'micro':
    child_model = MicroNetwork(num_layers=6, out_channels=20, num_nodes=5, dropout_rate=0.1, use_aux_heads=True)

apply_fixed_architecture(child_model,fixed_arc)

def dump_global_result(res_path,global_result, sort_keys = False):
    with open(res_path, "w") as ss_file:
        json.dump(global_result, ss_file, sort_keys=sort_keys, indent=2)


def main():
    os.environ['CUDA_VISIBLE_DEVICES'] = '4'
    # device = torch.device("cuda") if torch.cuda.is_available() else torch.device("cpu")
    device = torch.device("cuda" if FLAGS.is_cuda else "cpu")
    train(device)
    dump_global_result('result_retrain.json', result['accuracy'])

if __name__ == "__main__":
    main()

