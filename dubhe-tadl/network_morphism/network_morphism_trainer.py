import logging
from algorithm.graph import json_to_graph

import torch
import torch.nn as nn
import torch.optim as optim
import torch.utils.data as data
import torchvision
import re

import datasets
from utils import Constant, EarlyStop, save_json_result
from pytorch.utils import save_best_checkpoint

# pylint: disable=W0603
# set the logger format
logger = logging.getLogger(__name__)

class NetworkMorphismTrainer:
    def __init__(self, model_json, args):
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.batch_size = args.batch_size
        self.epochs = args.epochs
        self.lr = args.lr
        self.optimizer_name = args.opt
        self.data_dir = args.data_dir
        self.trial_id = args.trial_id
        self.args = args

        # Loading Data
        logger.info("Preparing data..")

        transform_train, transform_test = datasets.data_transforms_cifar10()

        trainset = torchvision.datasets.CIFAR10(
            root=self.data_dir, train=True, download=True, transform=transform_train
        )
        self.trainloader = data.DataLoader(
            trainset, batch_size=self.batch_size, shuffle=True, num_workers=1
        )

        testset = torchvision.datasets.CIFAR10(
            root=self.data_dir, train=False, download=True, transform=transform_test
        )
        self.testloader = data.DataLoader(
            testset, batch_size=self.batch_size, shuffle=False, num_workers=1
        )

        # Model
        logger.info("Building model..")
        # build model from json representation
        self.graph = json_to_graph(model_json)

        self.net = self.graph.produce_torch_model()

        if self.device == "cuda" and torch.cuda.device_count() > 1:
            self.net = nn.DataParallel(self.net)
        self.net.to(self.device)

        self.criterion = nn.CrossEntropyLoss()
        if self.optimizer_name == "SGD":
            self.optimizer = optim.SGD(
                self.net.parameters(), lr=self.lr, momentum=0.9, weight_decay=3e-4
            )
        if self.optimizer_name == "Adadelta":
            self.optimizer = optim.Adadelta(self.net.parameters(), lr=self.lr)
        if self.optimizer_name == "Adagrad":
            self.optimizer = optim.Adagrad(self.net.parameters(), lr=self.lr)
        if self.optimizer_name == "Adam":
            self.optimizer = optim.Adam(self.net.parameters(), lr=self.lr)
        if self.optimizer_name == "Adamax":
            self.optimizer = optim.Adamax(self.net.parameters(), lr=self.lr)
        if self.optimizer_name == "RMSprop":
            self.optimizer = optim.RMSprop(self.net.parameters(), lr=self.lr)

        self.scheduler = optim.lr_scheduler.CosineAnnealingLR(
            self.optimizer, self.epochs)

    def train_one_epoch(self):
        """
            train model on each epoch in trainset
        """
        self.net.train()

        for batch_idx, (inputs, targets) in enumerate(self.trainloader):
            inputs, targets = inputs.to(self.device), targets.to(self.device)
            self.optimizer.zero_grad()
            outputs = self.net(inputs)
            loss = self.criterion(outputs, targets)
            loss.backward()
            self.optimizer.step()

    def validate_one_epoch(self, epoch):
        """ eval model on each epoch in testset
        """
        self.net.eval()
        test_loss = 0
        correct = 0
        total = 0
        with torch.no_grad():
            for batch_idx, (inputs, targets) in enumerate(self.testloader):
                inputs, targets = inputs.to(
                    self.device), targets.to(self.device)
                outputs = self.net(inputs)
                loss = self.criterion(outputs, targets)

                test_loss += loss.item()
                _, predicted = outputs.max(1)
                total += targets.size(0)
                correct += predicted.eq(targets).sum().item()

        acc = correct / total
        logger.info("Epoch: %d, accuracy: %.3f", epoch, acc)
        result = {"type": "Accuracy", "result": {
                "sequence": epoch, "category": "epoch", "value": acc}}

        save_json_result(self.args.result_path, result)
        return test_loss, acc

    def train(self):
        try:
            max_no_improvement_num = Constant.MAX_NO_IMPROVEMENT_NUM
            early_stop = EarlyStop(max_no_improvement_num)
            early_stop.on_train_begin()
            test_metric_value_list = []

            for ep in range(self.epochs):
                self.train_one_epoch()
                test_loss, test_acc = self.validate_one_epoch(ep)
                self.scheduler.step()
                test_metric_value_list.append(test_acc)
                decreasing = early_stop.on_epoch_end(test_loss)
                if not decreasing:
                    break

            last_num = min(max_no_improvement_num, self.epochs)
            estimated_performance = sum(
                test_metric_value_list[-last_num:]) / last_num

            logger.info("final accuracy: %.3f", estimated_performance)


        except RuntimeError as e:
            if not re.search('out of memory', str(e)):
                raise e
            print(
                '\nCurrent model size is too big. Discontinuing training this model to search for other models.')
            Constant.MAX_MODEL_SIZE = self.graph.size()-1
            return None
        except Exception as e:
            logger.exception(e)
            raise

        return estimated_performance

    def retrain(self):
        logger.info("here")
        try:
            best_acc = 0.0
            for ep in range(self.epochs):
                logger.info(ep)
                self.train_one_epoch()
                _, test_acc = self.validate_one_epoch(ep)
                self.scheduler.step()
                if test_acc > best_acc:
                    best_acc = test_acc
                    save_best_checkpoint(self.args.best_checkpoint_dir,
                                 self.net, self.optimizer, self.epochs)

            logger.info("final accuracy: %.3f", best_acc)
        

        except Exception as exception:
            logger.exception(exception)
            raise
