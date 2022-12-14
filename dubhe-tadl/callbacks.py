# Copyright (c) Microsoft Corporation.
# Licensed under the MIT license.

import logging
import os

import torch
import torch.nn as nn

_logger = logging.getLogger(__name__)
_logger.setLevel(logging.INFO)

class Callback:
    """
    Callback provides an easy way to react to events like begin/end of epochs.
    """

    def __init__(self):
        self.model = None
        self.optimizer = None
        self.mutator = None
        self.trainer = None

    def build(self, model, optimizer, mutator, trainer):
        """
        Callback needs to be built with model, mutator, trainer, to get updates from them.

        Parameters
        ----------
        model : nn.Module
            Model to be trained.
        mutator : nn.Module
            Mutator that mutates the model.
        trainer : BaseTrainer
            Trainer that is to call the callback.
        """
        self.model = model
        self.optimizer = optimizer
        self.mutator = mutator
        self.trainer = trainer

    def on_epoch_begin(self, epoch):
        """
        Implement this to do something at the begin of epoch.

        Parameters
        ----------
        epoch : int
            Epoch number, starting from 0.
        """
        pass

    def on_epoch_end(self, epoch):
        """
        Implement this to do something at the end of epoch.

        Parameters
        ----------
        epoch : int
            Epoch number, starting from 0.
        """
        pass

    def on_batch_begin(self, epoch):
        pass

    def on_batch_end(self, epoch):
        pass


class LRSchedulerCallback(Callback):
    """
    Calls scheduler on every epoch ends.

    Parameters
    ----------
    scheduler : LRScheduler
        Scheduler to be called.
    """
    def __init__(self, scheduler, mode="epoch"):
        super().__init__()
        assert mode == "epoch"
        self.scheduler = scheduler
        self.mode = mode

    def on_epoch_end(self, epoch):
        """
        Call ``self.scheduler.step()`` on epoch end.
        """
        self.scheduler.step()


class ArchitectureCheckpoint(Callback):
    """
    Calls ``trainer.export()`` on every epoch ends.

    Parameters
    ----------
    checkpoint_dir : str
        Location to save checkpoints.
    """
    def __init__(self, checkpoint_dir):
        super().__init__()
        self.checkpoint_dir = checkpoint_dir
        os.makedirs(self.checkpoint_dir, exist_ok=True)

    def on_epoch_end(self, epoch):
        """
        Dump to ``/checkpoint_dir/epoch_{number}.json`` on epoch end.
        """
        dest_path = os.path.join(self.checkpoint_dir, "epoch_{}.json".format(epoch))
        _logger.info("Saving architecture to %s", dest_path)
        self.trainer.export(dest_path)

class BestArchitectureCheckpoint(Callback):
    """
    Calls ``trainer.export()`` on final epoch ends.

    Parameters
    ----------
    checkpoint_path : str
        Location to save checkpoints.
    """
    def __init__(self, checkpoint_path, epoches):
        super().__init__()
        self.epoches = epoches
        self.checkpoint_path = checkpoint_path

    def on_epoch_end(self, epoch):
        """
        Dump to ``./best_selected_space.json`` on epoch end.
        """
        if epoch == self.epoches -1:
            _logger.info("Saving architecture to %s", self.checkpoint_path)
            self.trainer.export(self.checkpoint_path)
            
class ModelCheckpoint(Callback):
    """
    Calls ``trainer.export()`` on every epoch ends.

    Parameters
    ----------
    checkpoint_dir : str
        Location to save checkpoints.
    """
    def __init__(self, checkpoint_dir):
        super().__init__()
        self.checkpoint_dir = checkpoint_dir
        os.makedirs(self.checkpoint_dir, exist_ok=True)

    def on_epoch_end(self, epoch):
        """
        Dump to ``/checkpoint_dir/epoch_{number}.pth.tar`` on every epoch end.
        ``DataParallel`` object will have their inside modules exported.
        """
        if isinstance(self.model, nn.DataParallel):
            child_model_state_dict = self.model.module.state_dict()
        else:
            child_model_state_dict = self.model.state_dict()

        save_state = {'child_model_state_dict': child_model_state_dict,
                      'optimizer_state_dict': self.optimizer.state_dict(),
                      'epoch': epoch}

        dest_path = os.path.join(self.checkpoint_dir, "epoch_{}.pth.tar".format(epoch))
        _logger.info("Saving model to %s", dest_path)
        torch.save(save_state, dest_path)
