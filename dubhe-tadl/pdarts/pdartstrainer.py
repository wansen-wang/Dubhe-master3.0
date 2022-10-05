import os
import logging
import torch
import torch.nn as nn
import numpy as np
from collections import OrderedDict
import json
from pytorch.callbacks import LRSchedulerCallback
from pytorch.trainer import BaseTrainer, TorchTensorEncoder
from pytorch.utils import dump_global_result

from model import CNN
from pdartsmutator import PdartsMutator
from pytorch.darts.utils import accuracy
from pytorch.darts import datasets
from pytorch.darts.dartstrainer import DartsTrainer

logger = logging.getLogger(__name__)

class PdartsTrainer(BaseTrainer):
    """
    This trainer implements the PDARTS algorithm.
    PDARTS bases on DARTS algorithm, and provides a network growth approach to find deeper and better network.
    This class relies on pdarts_num_layers and pdarts_num_to_drop parameters to control how network grows.
    pdarts_num_layers means how many layers more than first epoch.
    pdarts_num_to_drop means how many candidate operations should be dropped in each epoch.
        So that the grew network can in similar size.
    """

    def __init__(self, init_layers, pdarts_num_layers, pdarts_num_to_drop, pdarts_dropout_rates, num_epochs,  num_pre_epochs, model_lr, class_num,
                 arch_lr, channels, batch_size, result_path, log_frequency, unrolled,  data_dir, search_space_path,
                 best_selected_space_path, device=None, workers=4):
        super(PdartsTrainer, self).__init__()
        self.init_layers = init_layers
        self.class_num = class_num
        self.channels = channels
        self.model_lr = model_lr
        self.num_epochs = num_epochs
        self.class_num = class_num
        self.pdarts_num_layers = pdarts_num_layers
        self.pdarts_num_to_drop = pdarts_num_to_drop
        self.pdarts_dropout_rates = pdarts_dropout_rates
        self.pdarts_epoches = len(pdarts_num_to_drop)
        self.search_space_path = search_space_path
        self.best_selected_space_path = best_selected_space_path

        logger.info("loading data")
        dataset_train, dataset_valid = datasets.get_dataset(
            "cifar10", root=data_dir)
        self.darts_parameters = {
            "metrics": lambda output, target: accuracy(output, target, topk=(1,)),
            "arch_lr": arch_lr,
            "num_epochs": num_epochs,
            "num_pre_epochs": num_pre_epochs,
            "dataset_train": dataset_train,
            "dataset_valid": dataset_valid,
            "batch_size": batch_size,
            "result_path": result_path,
            "workers": workers,
            "device": device,
            "log_frequency": log_frequency,
            "unrolled": unrolled,
            "search_space_path": None
        }

    def train(self, validate=False):
        switches = None
        last = False
        for epoch in range(self.pdarts_epoches):
            if epoch == self.pdarts_epoches - 1:
                last = True
            # create network for each stage
            layers = self.init_layers + self.pdarts_num_layers[epoch]
            init_dropout_rate = float(self.pdarts_dropout_rates[epoch])
            model = CNN(32, 3, self.channels, self.class_num, layers,
                        init_dropout_rate, n_nodes=4, search=True)
            criterion = nn.CrossEntropyLoss()
            optim = torch.optim.SGD(
                model.parameters(), self.model_lr, momentum=0.9, weight_decay=3.0E-4)
            lr_scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(
                optim, self.num_epochs, eta_min=0.001)

            logger.info(
                "############Start PDARTS training epoch %s############", epoch)
            self.mutator = PdartsMutator(
                model, epoch, self.pdarts_num_to_drop, switches)
            if epoch == 0:
                # only write original search space in first stage
                search_space = self.mutator._generate_search_space()
                dump_global_result(self.search_space_path,
                search_space)

            darts_callbacks = []
            if lr_scheduler is not None:
                darts_callbacks.append(LRSchedulerCallback(lr_scheduler))
                # darts_callbacks.append(ArchitectureCheckpoint(
                #     os.path.join(self.selected_space_path, "stage_{}".format(epoch))))
            self.trainer = DartsTrainer(model, mutator=self.mutator, loss=criterion,
                                        optimizer=optim, callbacks=darts_callbacks, **self.darts_parameters)

            for train_epoch in range(self.darts_parameters["num_epochs"]):
                for callback in darts_callbacks:
                    callback.on_epoch_begin(train_epoch)

                # training
                logger.info("Epoch %d Training", train_epoch)
                if train_epoch < self.darts_parameters["num_pre_epochs"]:
                    dropout_rate = init_dropout_rate * \
                        (self.darts_parameters["num_epochs"] - train_epoch -
                         1) / self.darts_parameters["num_epochs"]
                else:
                    # scale_factor = 0.2
                    dropout_rate = init_dropout_rate * \
                        np.exp(-(epoch -
                                 self.darts_parameters["num_pre_epochs"]) * 0.2)

                model.drop_path_prob(search=True, p=dropout_rate)
                self.trainer.train_one_epoch(train_epoch)

                if validate:
                    # validation
                    logger.info("Epoch %d Validating", train_epoch + 1)
                    self.trainer.validate_one_epoch(
                        train_epoch, log_print=True if last else False)

                for callback in darts_callbacks:
                    callback.on_epoch_end(train_epoch)

            switches = self.mutator.drop_paths()

            # In last pdarts_epoches, need to restrict skipconnection and save best structure
            if last:
                res = OrderedDict()
                op_value = [value for value in search_space["op_list"]["_value"] if value != 'none']
                res["op_list"] = search_space["op_list"]
                res["op_list"]["_value"] = op_value
                res["best_selected_space"] = self.mutator.export(last, switches)
                logger.info(res)
                dump_global_result(self.best_selected_space_path, res)

    def validate(self):
        self.trainer.validate()

    def export(self, file, last, switches):
        self.mutator.export(last, switches)
        mutator_export = self.mutator.export()
        
        with open(file, "w") as f:
            json.dump(mutator_export, f, indent=2, sort_keys=True, cls=TorchTensorEncoder)

    def checkpoint(self, file_path, epoch):
        if isinstance(self.model, nn.DataParallel):
            child_model_state_dict = self.model.module.state_dict()
        else:
            child_model_state_dict = self.model.state_dict()

        save_state = {'child_model_state_dict': child_model_state_dict,
                      'optimizer_state_dict': self.optimizer.state_dict(),
                      'epoch': epoch}

        dest_path = os.path.join(
            file_path, "best_checkpoint_epoch_{}.pth.tar".format(epoch))
        logger.info("Saving model to %s", dest_path)
        torch.save(save_state, dest_path)
        raise NotImplementedError("Not implemented yet")


