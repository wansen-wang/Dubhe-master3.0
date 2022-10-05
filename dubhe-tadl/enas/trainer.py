from itertools import cycle
import os
import sys
sys.path.append('..'+ '/' + '..')
import numpy as np
import random
import logging
import time
from argparse import ArgumentParser
from collections import OrderedDict
import json
import torch
import torch.nn as nn
import torch.optim as optim


# import custom libraries
import datasets
from pytorch.trainer import Trainer
from pytorch.utils import AverageMeterGroup, to_device, mkdirs
from pytorch.mutables import LayerChoice, InputChoice, MutableScope
from macro import GeneralNetwork
from micro import MicroNetwork
# from trainer import EnasTrainer
from mutator import EnasMutator
from pytorch.callbacks import (ArchitectureCheckpoint,
                                       LRSchedulerCallback)
from utils import accuracy, reward_accuracy

torch.cuda.set_device(0)

logging.basicConfig(format='%(asctime)s - %(filename)s[line:%(lineno)d] - %(levelname)s: %(message)s',
                    level=logging.INFO,
                    filename='./train.log',
                    filemode='a')
logger = logging.getLogger('enas_train')

class EnasTrainer(Trainer):
    """
    ENAS trainer.

    Parameters
    ----------
    model : nn.Module
        PyTorch model to be trained.
    loss : callable
        Receives logits and ground truth label, return a loss tensor.
    metrics : callable
        Receives logits and ground truth label, return a dict of metrics.
    reward_function : callable
        Receives logits and ground truth label, return a tensor, which will be feeded to RL controller as reward.
    optimizer : Optimizer
        The optimizer used for optimizing the model.
    num_epochs : int
        Number of epochs planned for training.
    dataset_train : Dataset
        Dataset for training. Will be split for training weights and architecture weights.
    dataset_valid : Dataset
        Dataset for testing.
    mutator : EnasMutator
        Use when customizing your own mutator or a mutator with customized parameters.
    batch_size : int
        Batch size.
    workers : int
        Workers for data loading.
    device : torch.device
        ``torch.device("cpu")`` or ``torch.device("cuda")``.
    log_frequency : int
        Step count per logging.
    callbacks : list of Callback
        list of callbacks to trigger at events.
    entropy_weight : float
        Weight of sample entropy loss.
    skip_weight : float
        Weight of skip penalty loss.
    baseline_decay : float
        Decay factor of baseline. New baseline will be equal to ``baseline_decay * baseline_old + reward * (1 - baseline_decay)``.
    child_steps : int
        How many mini-batches for model training per epoch.
    mutator_lr : float
        Learning rate for RL controller.
    mutator_steps_aggregate : int
        Number of steps that will be aggregated into one mini-batch for RL controller.
    mutator_steps : int
        Number of mini-batches for each epoch of RL controller learning.
    aux_weight : float
        Weight of auxiliary head loss. ``aux_weight * aux_loss`` will be added to total loss.
    test_arc_per_epoch : int
        How many architectures are chosen for direct test after each epoch.
    """
    def __init__(self, model, loss, metrics, reward_function,
                 optimizer, num_epochs, dataset_train, dataset_valid,
                 mutator=None, batch_size=64, workers=4, device=None, log_frequency=None, callbacks=None,
                 entropy_weight=0.0001, skip_weight=0.8, baseline_decay=0.999, child_steps=500,
                 mutator_lr=0.00035, mutator_steps_aggregate=20, mutator_steps=50, aux_weight=0.4,
                 test_arc_per_epoch=1,child_model_path = './', result_path='./'):
        super().__init__(model, mutator if mutator is not None else EnasMutator(model),
                         loss, metrics, optimizer, num_epochs, dataset_train, dataset_valid,
                         batch_size, workers, device, log_frequency, callbacks)
        self.reward_function = reward_function
        self.mutator_optim = optim.Adam(self.mutator.parameters(), lr=mutator_lr)
        self.batch_size = batch_size
        self.workers = workers

        self.entropy_weight = entropy_weight
        self.skip_weight = skip_weight
        self.baseline_decay = baseline_decay
        self.baseline = 0.
        self.mutator_steps_aggregate = mutator_steps_aggregate
        self.mutator_steps = mutator_steps
        self.child_steps = child_steps
        self.aux_weight = aux_weight
        self.test_arc_per_epoch = test_arc_per_epoch
        self.child_model_path = child_model_path # saving the child model
        self.init_dataloader()
        # self.result = {'accuracy':[],
        #                'cost_time':0}
        self.result_path = result_path
        with open(self.result_path, "w") as file:
            file.write('')

    def init_dataloader(self):
        n_train = len(self.dataset_train)
        split = n_train // 10
        indices = list(range(n_train))
        train_sampler = torch.utils.data.sampler.SubsetRandomSampler(indices[:-split])
        valid_sampler = torch.utils.data.sampler.SubsetRandomSampler(indices[-split:])
        self.train_loader = torch.utils.data.DataLoader(self.dataset_train,
                                                        batch_size=self.batch_size,
                                                        sampler=train_sampler,
                                                        num_workers=self.workers)
        self.valid_loader = torch.utils.data.DataLoader(self.dataset_train,
                                                        batch_size=self.batch_size,
                                                        sampler=valid_sampler,
                                                        num_workers=self.workers)
        self.test_loader = torch.utils.data.DataLoader(self.dataset_valid,
                                                       batch_size=self.batch_size,
                                                       num_workers=self.workers)
        self.train_loader = cycle(self.train_loader)
        self.valid_loader = cycle(self.valid_loader)

    def train_one_epoch(self, epoch):
        # Sample model and train
        self.model.train()
        self.mutator.eval()
        meters = AverageMeterGroup()
        for step in range(1, self.child_steps + 1):
            x, y = next(self.train_loader)
            x, y = to_device(x, self.device), to_device(y, self.device)
            self.optimizer.zero_grad()

            with torch.no_grad():
                self.mutator.reset()
            # self._write_graph_status()
            logits = self.model(x)

            if isinstance(logits, tuple):
                logits, aux_logits = logits
                aux_loss = self.loss(aux_logits, y)
            else:
                aux_loss = 0.
            metrics = self.metrics(logits, y)
            loss = self.loss(logits, y)
            loss = loss + self.aux_weight * aux_loss
            loss.backward()
            nn.utils.clip_grad_norm_(self.model.parameters(), 5.)
            self.optimizer.step()
            metrics["loss"] = loss.item()
            meters.update(metrics)

            if self.log_frequency is not None and step % self.log_frequency == 0:
                logger.info("Model Epoch [%d/%d] Step [%d/%d]  %s", epoch + 1,
                            self.num_epochs, step, self.child_steps, meters)

        # Train sampler (mutator)
        self.model.eval()
        self.mutator.train()
        meters = AverageMeterGroup()
        for mutator_step in range(1, self.mutator_steps + 1):
            self.mutator_optim.zero_grad()
            for step in range(1, self.mutator_steps_aggregate + 1):
                x, y = next(self.valid_loader)
                x, y = to_device(x, self.device), to_device(y, self.device)

                self.mutator.reset()
                with torch.no_grad():
                    logits = self.model(x)
                # self._write_graph_status()
                metrics = self.metrics(logits, y)
                reward = self.reward_function(logits, y)
                if self.entropy_weight:
                    reward += self.entropy_weight * self.mutator.sample_entropy.item()
                self.baseline = self.baseline * self.baseline_decay + reward * (1 - self.baseline_decay)
                loss = self.mutator.sample_log_prob * (reward - self.baseline)
                if self.skip_weight:
                    loss += self.skip_weight * self.mutator.sample_skip_penalty
                metrics["reward"] = reward
                metrics["loss"] = loss.item()
                metrics["ent"] = self.mutator.sample_entropy.item()
                metrics["log_prob"] = self.mutator.sample_log_prob.item()
                metrics["baseline"] = self.baseline
                metrics["skip"] = self.mutator.sample_skip_penalty

                loss /= self.mutator_steps_aggregate
                loss.backward()
                meters.update(metrics)

                cur_step = step + (mutator_step - 1) * self.mutator_steps_aggregate
                if self.log_frequency is not None and cur_step % self.log_frequency == 0:
                    logger.info("RL Epoch [%d/%d] Step [%d/%d] [%d/%d]  %s", epoch + 1, self.num_epochs,
                                mutator_step, self.mutator_steps, step, self.mutator_steps_aggregate,
                                meters)

            nn.utils.clip_grad_norm_(self.mutator.parameters(), 5.)
            self.mutator_optim.step()

    def validate_one_epoch(self, epoch):
        with torch.no_grad():
            accuracy = 0
            for arc_id in range(self.test_arc_per_epoch):
                meters = AverageMeterGroup()
                count, acc_this_round = 0,0
                for x, y in self.test_loader:
                    x, y = to_device(x, self.device), to_device(y, self.device)
                    self.mutator.reset()
                    child_model = self.export_child_model()
                    # self._generate_child_model(epoch,
                    #                            count,
                    #                            arc_id,
                    #                            child_model,
                    #                            self.child_model_path)
                    logits = self.model(x)
                    if isinstance(logits, tuple):
                        logits, _ = logits
                    metrics = self.metrics(logits, y)
                    loss = self.loss(logits, y)
                    metrics["loss"] = loss.item()
                    meters.update(metrics)
                    count += 1
                    acc_this_round += metrics['acc1']

                logger.info("Test Epoch [%d/%d] Arc [%d/%d] Summary  %s",
                            epoch + 1, self.num_epochs, arc_id + 1, self.test_arc_per_epoch,
                            meters.summary())
                acc_this_round /= count
                accuracy += acc_this_round
            # logger.info({"type": "Accuracy", "result": {"sequence": epoch, "category": "epoch", "value": meters.get_last_acc()}})
            print({"type": "Accuracy", "result": {"sequence": epoch, "category": "epoch", "value": meters.get_last_acc()}})
            with open(self.result_path, "a") as file:
                    file.write(str({"type": "Accuracy", "result": {"sequence": epoch, "category": "epoch",
                                                                   "value": meters.get_last_acc()}}) + '\n')
            # self.result['accuracy'].append(accuracy / self.test_arc_per_epoch)

    # export child_model
    def export_child_model(self, selected_space=False):
        if selected_space:
            sampled = self.mutator.sample_final()
        else:
            sampled = self.mutator._cache
        result = OrderedDict()
        cur_layer_id = None
        for mutable in self.mutator.mutables:
            if not isinstance(mutable, (LayerChoice, InputChoice)):
                cur_layer_id = mutable.key
                # not supported as built-in
                continue
            choosed_ops_idx = self.mutator._convert_mutable_decision_to_human_readable(mutable, sampled[mutable.key])
            if not isinstance(choosed_ops_idx, list):
                choosed_ops_idx = [choosed_ops_idx]
            if isinstance(mutable, LayerChoice):
                if 'op_list' not in result:
                    result['op_list'] = [str(i) for i in mutable]
                choosed_ops = [str(mutable[idx]) for idx in choosed_ops_idx]
            else:

                choosed_ops = choosed_ops_idx
            if 'node' in cur_layer_id:
                result[mutable.key] = choosed_ops
            else:
                result[cur_layer_id + '_' + mutable.key] = choosed_ops

        return result

    def _generate_child_model(self,
                              validation_epoch,
                              model_idx,
                              validation_step,
                              child_model,
                              file_path):

        # create child_models folder
        # parent_path = os.path.join(file_path, 'child_model')
        parent_path = file_path
        if not os.path.exists(parent_path):
            os.mkdir(parent_path)

        # create secondary directory
        secondary_path = os.path.join(parent_path, 'validation_epoch_{}'.format(validation_epoch))
        if not os.path.exists(secondary_path):
            os.mkdir(secondary_path)

        # create third directory
        folder_path = os.path.join(secondary_path, 'validation_step_{}'.format(validation_step))
        if not os.path.exists(folder_path):
            os.mkdir(folder_path)

        # save sampled child_model for validation
        saved_path = os.path.join(folder_path, "child_model_%02d.json" % model_idx)

        with open(saved_path, "w") as ss_file:
            json.dump(child_model, ss_file, indent=2)

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
    parser.add_argument(
        "--data_dir",
        type=str,
        default="./data",
        help="Directory containing the dataset and embedding file. (default: %(default)s)")
    parser.add_argument("--model_selected_space_path", type=str,
                        default='./model_selected_space.json', help="sapce_path_out directory")
    parser.add_argument("--result_path", type=str,
                        default='./model_result.json', help="res directory")
    parser.add_argument("--search_space_path", type=str,
                        default='./search_space.json', help="search_space directory")
    parser.add_argument("--best_selected_space_path", type=str,
                        default='./model_selected_space.json', help="Best sapce_path_out directory of experiment")
    parser.add_argument('--trial_id', type=int, default=0, metavar='N',
                    help='trial_id,start from 0')
    parser.add_argument('--lr', type=float, default=0.005, metavar='N',
                    help='learning rate')
    parser.add_argument("--epochs", default=None, type=int, help="Number of epochs (default: macro 310, micro 150)")
    parser.add_argument("--batch_size", default=128, type=int)
    parser.add_argument("--log_frequency", default=10, type=int)
    parser.add_argument("--search_for", choices=["macro", "micro"], default="macro")
    args = parser.parse_args()

    mkdirs(args.result_path, args.search_space_path, args.best_selected_space_path)
    # 设置随机种子
    torch.manual_seed(args.trial_id)
    torch.cuda.manual_seed_all(args.trial_id)
    np.random.seed(args.trial_id)
    random.seed(args.trial_id)
    # use deterministic instead of nondeterministic algorithm
    # make sure exact results can be reproduced everytime.
    torch.backends.cudnn.deterministic = True


    dataset_train, dataset_valid = datasets.get_dataset("cifar10", args.data_dir)
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
                               callbacks=[LRSchedulerCallback(lr_scheduler), ArchitectureCheckpoint("./"+args.search_for+"_checkpoints")],
                               batch_size=args.batch_size,
                               num_epochs=num_epochs,
                               dataset_train=dataset_train,
                               dataset_valid=dataset_valid,
                               log_frequency=args.log_frequency,
                               mutator=mutator,
                               child_steps=2,
                               mutator_steps=2,
                               child_model_path='./'+args.search_for+'_child_model',
                               result_path=args.result_path)

    logger.info(trainer.metrics)

    t1 = time.time()
    trainer.train()
    # trainer.result["cost_time"] = time.time() - t1
    # dump_global_result(args.result_path,trainer.result)

    selected_model = trainer.export_child_model(selected_space = True)
    dump_global_result(args.best_selected_space_path,selected_model)