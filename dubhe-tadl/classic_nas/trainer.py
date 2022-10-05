# Copyright (c) Microsoft Corporation.
# Licensed under the MIT license.

import copy
import logging
import os
import argparse
import logging
import sys
sys.path.append('..'+ '/' + '..')
from collections import OrderedDict


import torch
import torch.nn as nn
import torch.nn.functional as F

from torchvision import datasets, transforms
from model import Net

from pytorch.trainer import Trainer
from pytorch.utils import AverageMeterGroup
from pytorch.utils import mkdirs
from pytorch.mutables import LayerChoice, InputChoice

from mutator import ClassicMutator
import numpy as np
import time
import json

logger = logging.getLogger(__name__)
#logger.setLevel(logging.INFO)


class ClassicnasTrainer(Trainer):
    """
    Classicnas trainer.

    Parameters
    ----------
    model : nn.Module
        PyTorch model to be trained.
    loss : callable
        Receives logits and ground truth label, return a loss tensor.
    metrics : callable
        Receives logits and ground truth label, return a dict of metrics.
    optimizer : Optimizer
        The optimizer used for optimizing the model.
    num_epochs : int
        Number of epochs planned for training.
    dataset_train : Dataset
        Dataset for training. Will be split for training weights and architecture weights.
    dataset_valid : Dataset
        Dataset for testing.
    mutator : ClassicMutator
        Use in case of customizing your own ClassicMutator. By default will instantiate a ClassicMutator.
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
    arc_learning_rate : float
        Learning rate of architecture parameters.
    unrolled : float
        ``True`` if using second order optimization, else first order optimization.
    """
    def __init__(self, model, loss, metrics,
                 optimizer, epochs, dataset_train, dataset_valid, search_space_path,selected_space_path,trial_id,
                 mutator=None, batch_size=64, workers=4, device=None, log_frequency=None,
                 callbacks=None, arc_learning_rate=3.0E-4, unrolled=False):


        self.model = model

        self.loss = loss
        self.metrics = metrics 
        self.optimizer = optimizer
        self.epochs = epochs
        self.device = device
        self.batch_size = batch_size

        self.train_loader = torch.utils.data.DataLoader(
            datasets.MNIST(dataset_train, train=True, download=False,
                        transform=transforms.Compose([
                            transforms.ToTensor(),
                            transforms.Normalize((0.1307,), (0.3081,))
                        ])),
            batch_size=batch_size, shuffle=True, **kwargs)

        self.test_loader = torch.utils.data.DataLoader(
            datasets.MNIST(dataset_valid, train=False, transform=transforms.Compose([
                transforms.ToTensor(),
                transforms.Normalize((0.1307,), (0.3081,))
            ])),
            batch_size=1000, shuffle=True, **kwargs)



        self.search_space_path = search_space_path
        self.selected_space_path =selected_space_path
        self.trial_id = trial_id
        self.num_epochs = 10
        self.classicmutator=ClassicMutator(self.model,trial_id=self.trial_id,selected_path=self.selected_space_path,search_space_path=self.search_space_path)

        self.result = {"accuracy": [],"cost_time": 0.}

    def train_one_epoch(self, epoch):
        

        # t1 = time()
        # phase 1. architecture step
        self.classicmutator.trial_id = epoch
        self.classicmutator._chosen_arch=self.classicmutator.random_generate_chosen()  
        #print('epoch:',epoch,'\n',self.classicmutator._chosen_arch)

        # phase 2: child network step
        for child_epoch in range(1, self.epochs + 1):

            self.model.train()
            for batch_idx, (data, target) in enumerate(self.train_loader):
                data, target = data.to(self.device), target.to(self.device)
                optimizer.zero_grad()
                output = self.model(data)
                loss = F.nll_loss(output, target)
                loss.backward()
                optimizer.step()
                if batch_idx % args['log_interval'] == 0:
                    logger.info('Train Epoch: {} [{}/{} ({:.0f}%)]\tLoss: {:.6f}'.format(
                        child_epoch, batch_idx * len(data), len(self.train_loader.dataset),
                        100. * batch_idx / len(self.train_loader), loss.item()))

            test_acc = self.validate_one_epoch(epoch)
            print({"type":"accuracy","result":{"sequence":child_epoch,"category":"epoch","value":test_acc}} )
            with open(args['result_path'], "a") as ss_file:
                ss_file.write(json.dumps({"type":"accuracy","result":{"sequence":child_epoch,"category":"epoch","value":test_acc}} ) + '\n')
            self.result['accuracy'].append(test_acc)

    def validate_one_epoch(self, epoch):
        self.model.eval()
        test_loss = 0
        correct = 0
        with torch.no_grad():
            for data, target in self.test_loader:
                data, target = data.to(self.device), target.to(self.device)
                output = self.model(data)
                # sum up batch loss
                test_loss += F.nll_loss(output, target, reduction='sum').item()
                # get the index of the max log-probability
                pred = output.argmax(dim=1, keepdim=True)
                correct += pred.eq(target.view_as(pred)).sum().item()

        test_loss /= len(self.test_loader.dataset)

        accuracy = 100. * correct / len(self.test_loader.dataset)

        logger.info('\nTest set: Average loss: {:.4f}, Accuracy: {}/{} ({:.0f}%)\n'.format(
            test_loss, correct, len(self.test_loader.dataset), accuracy))

        return accuracy
    def train(self):
        """
        Train ``num_epochs``.
        Trigger callbacks at the start and the end of each epoch.

        Parameters
        ----------
        validate : bool
            If ``true``, will do validation every epoch.
        """
        for epoch in range(self.num_epochs):
            # training           
            self.train_one_epoch(epoch)


def dump_global_result(args,global_result):
    with open(args['result_path'], "w") as ss_file:
        json.dump(global_result, ss_file, sort_keys=True, indent=2)

def get_params():
    # Training settings
    parser = argparse.ArgumentParser(description='PyTorch MNIST Example')
    parser.add_argument("--data_dir", type=str,
                        default='./data', help="data directory")
    parser.add_argument("--model_selected_space_path", type=str,
                        default='./selected_space.json', help="selected_space_path")
    parser.add_argument("--search_space_path", type=str,
                        default='./selected_space.json', help="search_space_path")        
    parser.add_argument("--result_path", type=str,
                        default='./model_result.json', help="result_path")
    parser.add_argument('--batch_size', type=int, default=64, metavar='N',
                        help='input batch size for training (default: 64)')
    parser.add_argument("--hidden_size", type=int, default=512, metavar='N',
                        help='hidden layer size (default: 512)')
    parser.add_argument('--lr', type=float, default=0.01, metavar='LR',
                        help='learning rate (default: 0.01)')
    parser.add_argument('--momentum', type=float, default=0.5, metavar='M',
                        help='SGD momentum (default: 0.5)')
    parser.add_argument('--epochs', type=int, default=10, metavar='N',
                        help='number of epochs to train (default: 10)')
    parser.add_argument('--seed', type=int, default=1, metavar='S',
                        help='random seed (default: 1)')
    parser.add_argument('--no_cuda', default=False,
                        help='disables CUDA training')
    parser.add_argument('--log_interval', type=int, default=1000, metavar='N',
                        help='how many batches to wait before logging training status')
    parser.add_argument('--trial_id', type=int, default=0, metavar='N',
                    help='trial_id,start from 0')

    args, _ = parser.parse_known_args()
    return args

if __name__ == '__main__':
    try:
        start=time.time()

        params = vars(get_params())
        args =params

        use_cuda = not args['no_cuda'] and torch.cuda.is_available()

        torch.manual_seed(args['seed'])

        device = torch.device("cuda" if use_cuda else "cpu")

        kwargs = {'num_workers': 1, 'pin_memory': True} if use_cuda else {}

        data_dir = args['data_dir']

        hidden_size = args['hidden_size']

        model = Net(hidden_size=hidden_size).to(device)

        optimizer = torch.optim.SGD(model.parameters(), lr=args['lr'],
                            momentum=args['momentum'])

        mkdirs(args['search_space_path'])
        mkdirs(args['model_selected_space_path'])
        mkdirs(args['result_path'])
        trainer = ClassicnasTrainer(model,
                        loss=None,
                        metrics=None,
                        optimizer=optimizer,
                        epochs=args['epochs'],
                        dataset_train=data_dir,
                        dataset_valid=data_dir,
                        search_space_path = args['search_space_path'],
                        selected_space_path = args['model_selected_space_path'],
                        trial_id = args['trial_id'],
                        batch_size=args['batch_size'],
                        log_frequency=args['log_interval'],
                        device= device,
                        unrolled=None,
                        callbacks=None)

        with open(args['result_path'], "w") as ss_file:
            ss_file.write('')
        trainer.train_one_epoch(args['trial_id'])
        #trainer.train()
        global_result = trainer.result
        #global_result['cost_time'] = str(time.time() - start) +'s'
        #dump_global_result(params,global_result)
    except Exception as exception:
        logger.exception(exception)
        raise
