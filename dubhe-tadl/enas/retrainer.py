import sys
from utils import accuracy
import torch
from torch import nn
from torch.utils.data import DataLoader
import datasets
import time
import logging
import os
import argparse
import distutils.util
import numpy as np
import json
import random

sys.path.append('..'+ '/' + '..')

# import custom packages
from macro import GeneralNetwork
from micro import MicroNetwork
from pytorch.fixed import apply_fixed_architecture
from pytorch.retrainer import Retrainer
from pytorch.utils import AverageMeterGroup, to_device, save_best_checkpoint, mkdirs

class EnasRetrainer(Retrainer):
    """
    ENAS retrainer.

    Parameters
    ----------
    model : nn.Module
        PyTorch model to be trained.
    data_dir : dataset path
        The path of the dataset.
    best_checkpoint_dir: 'best_checkpoint.pth'
        The directory for saving model.
    batch_size : int
        Batch size.
    eval_batch_size : int
        Batch size.
    num_epochs : int
        Number of epochs planned for training.
    lr : float
        Learning rate.
    is_cuda: Boolean
        Whether to use GPU for training.
    log_every : int
        Step count per logging.
    child_grad_bound : float
        Gradient bound.
    child_l2_reg: float
        L2 regression.
    eval_every_epochs: int
        Evaluate every epochs.
    logger:
        logging.
    workers : int
        Workers for data loading.
    device : torch.device
        ``torch.device("cpu")`` or ``torch.device("cuda")``.
    aux_weight : float
        Weight of auxiliary head loss. ``aux_weight * aux_loss`` will be added to total loss.
    """
    def __init__(self,model,data_dir = './data',best_checkpoint_dir = './best_checkpoint',
                 batch_size = 1024, eval_batch_size = 1024,num_epochs = 2,lr = 0.02,is_cuda = 'True',
                 log_every = 40,child_grad_bound = 0.5, child_l2_reg=3e-6, eval_every_epochs=2,
                 logger = logging.getLogger("enas-retrain"), result_path='./'):
        self.aux_weight = 0.4
        self.device = torch.device("cuda:0" )
        self.workers = 4

        self.child_model = model
        self.data_dir = data_dir
        self.best_checkpoint_dir = best_checkpoint_dir
        self.batch_size = batch_size
        self.eval_batch_size = eval_batch_size
        self.num_epochs = num_epochs
        self.lr = lr
        self.is_cuda = is_cuda
        self.log_every = log_every
        self.child_grad_bound = child_grad_bound
        self.child_l2_reg = child_l2_reg
        self.eval_every_epochs = eval_every_epochs
        self.logger = logger

        self.optimizer = torch.optim.SGD(self.child_model.parameters(), self.lr, momentum=0.9, weight_decay=1.0E-4, nesterov=True)
        self.criterion = nn.CrossEntropyLoss()
        self.lr_scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(self.optimizer, T_max=self.num_epochs, eta_min=0.001)

        # load dataset
        self.init_dataloader()
        self.child_model.to(self.device)
        self.result_path = result_path
        with open(self.result_path, "w") as file:
            file.write('')

    def train(self):
        """
        Train ``num_epochs``.
        Trigger callbacks at the start and the end of each epoch.

        Parameters
        ----------
        validate : bool
            If ``true``, will do validation every epoch.
        """
        self.logger.info('** Start training **')

        self.start_time = time.time()
        for epoch in range(self.num_epochs):

            self.train_one_epoch(epoch)

            self.child_model.eval()

            # if epoch / self.eval_every_epochs == 0:
            self.logger.info("Epoch {}: Eval".format(epoch))
            self.validate_one_epoch(epoch)

            self.lr_scheduler.step()

        # print('** saving model **')

        self.logger.info("** Save best model **")
        # save_state = {
        #     'epoch': epoch,
        #     'child_model_state_dict': self.child_model.state_dict(),
        #     'optimizer_state_dict': self.optimizer.state_dict()}
        # torch.save(save_state, self.best_checkpoint_dir)
        save_best_checkpoint(self.best_checkpoint_dir, self.child_model, self.optimizer, epoch)

    def validate(self):
        """
        Do one validation. Validate one epoch.
        """
        pass

    def export(self, file):
        """
         dump the architecture to ``file``.

        Parameters
        ----------
        file : str
            File path to export to. Expected to be a JSON.
        """
        pass

    def checkpoint(self):
        """
        Override to dump a checkpoint.
        """
        pass

    def init_dataloader(self):
        self.logger.info("Build dataloader")
        self.dataset_train, self.dataset_valid = datasets.get_dataset("cifar10", self.data_dir)
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
                                                        batch_size=self.eval_batch_size,
                                                        sampler=valid_sampler,
                                                        num_workers=self.workers)
        self.test_loader = torch.utils.data.DataLoader(self.dataset_valid,
                                                       batch_size=self.batch_size,
                                                       num_workers=self.workers)
        # self.train_loader = cycle(self.train_loader)
        # self.valid_loader = cycle(self.valid_loader)

    def train_one_epoch(self,epoch):
        """
        Train one epoch.

        Parameters
        ----------
        epoch : int
            Epoch number starting from 0.
        """
        tot_acc = 0
        tot = 0
        losses = []
        step = 0
        self.child_model.train()
        meters = AverageMeterGroup()

        for batch in self.train_loader:
            step += 1

            x, y = batch
            x, y = to_device(x, self.device), to_device(y, self.device)

            logits = self.child_model(x)

            if isinstance(logits, tuple):
                logits, aux_logits = logits
                aux_loss = self.criterion(aux_logits, y)
            else:
                aux_loss = 0.

            acc = accuracy(logits, y)
            loss = self.criterion(logits, y)
            loss = loss + self.aux_weight * aux_loss

            self.optimizer.zero_grad()
            loss.backward()
            grad_norm = 0
            trainable_params = self.child_model.parameters()

            #             assert FLAGS.child_grad_bound is not None, "Need grad_bound to clip gradients."
            #             # compute the gradient norm value
            #             grad_norm = nn.utils.clip_grad_norm_(trainable_params, 99999999)
            # for param in trainable_params:
            #     nn.utils.clip_grad_norm_(param, self.child_grad_bound)  # clip grad
            #     print(param_ == param)
            if self.child_grad_bound is not None:
                grad_norm = nn.utils.clip_grad_norm_(trainable_params, self.child_grad_bound)
                trainable_params = grad_norm

            self.optimizer.step()

            tot_acc += acc['acc1']
            tot += 1
            losses.append(loss)
            acc["loss"] = loss.item()
            meters.update(acc)

            if step % self.log_every == 0:
                curr_time = time.time()
                log_string = ""
                log_string += "epoch={:<6d}".format(epoch)
                log_string += "ch_step={:<6d}".format(step)
                log_string += " loss={:<8.6f}".format(loss)
                log_string += " lr={:<8.4f}".format(self.optimizer.param_groups[0]['lr'])
                log_string += " |g|={:<8.4f}".format(grad_norm)
                log_string += " tr_acc={:<8.4f}/{:>3d}".format(acc['acc1'], logits.size()[0])
                log_string += " mins={:<10.2f}".format(float(curr_time - self.start_time) / 60)
                self.logger.info(log_string)

        print("Model Epoch [%d/%d]  %.3f mins  %s  \n " % (epoch + 1,
            self.num_epochs, float(time.time() - self.start_time) / 60, meters ))
        final_acc = float(tot_acc) / tot

        losses = torch.tensor(losses)
        loss = losses.mean()


    def validate_one_epoch(self,epoch):
        tot_acc = 0
        tot = 0
        losses = []
        meters = AverageMeterGroup()

        with torch.no_grad():  # save memory
            meters = AverageMeterGroup()
            for batch in self.valid_loader:
                x, y = batch
                x, y = to_device(x, self.device), to_device(y, self.device)
                logits = self.child_model(x)

                if isinstance(logits, tuple):
                    logits, aux_logits = logits
                    aux_loss = self.criterion(aux_logits, y)
                else:
                    aux_loss = 0.

                loss = self.criterion(logits, y)
                loss = loss + self.aux_weight * aux_loss
                #             loss = loss.mean()
                preds = logits.argmax(dim=1).long()
                acc = torch.eq(preds, y.long()).long().sum().item()
                acc_v = accuracy(logits, y)

                losses.append(loss)
                tot_acc += acc
                tot += len(y)

                acc_v["loss"] = loss.item()
                meters.update(acc_v)

        losses = torch.tensor(losses)
        loss = losses.mean()
        if tot > 0:
            final_acc = float(tot_acc) / tot
        else:
            final_acc = 0
            self.logger.info("Error in calculating final_acc")

        with open(self.result_path, "a") as file:
            file.write(
                str({"type": "Accuracy",
                     "result": {"sequence": epoch, "category": "epoch", "value": final_acc}}) + '\n')

        # print("Model eval %.3fmins  %s \n " % (
        #     float(time.time() - self.start_time) / 60, meters ))
        print({"type": "Accuracy",
                     "result": {"sequence": epoch, "category": "epoch", "value": final_acc}})

        self.logger.info(
            "ch_step= {}_accuracy={:<6.4f} {}_loss={:<6.4f}".format( "test", final_acc, "test", loss))


logging.basicConfig(format='%(asctime)s - %(filename)s[line:%(lineno)d] - %(levelname)s: %(message)s',
                    level=logging.INFO,
                    filename='./retrain.log',
                    filemode='a')
logger = logging.getLogger("enas-retrain")

def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--data_dir",
        type=str,
        default="./data",
        help="Directory containing the dataset and embedding file. (default: %(default)s)")
    parser.add_argument(
        "--model_selected_space_path",
        type=str,
        default="./model_selected_space.json",
        # required=True,
        help="Architecture json file. (default: %(default)s)")
    parser.add_argument("--result_path", type=str,
                        default='./result.json', help="res directory")
    parser.add_argument("--search_space_path", type=str,
                        default='./search_space.json', help="search_space directory")
    parser.add_argument("--log_path", type=str, default='output/log')
    parser.add_argument(
        "--best_selected_space_path",
        type=str,
        default="./best_selected_space.json",
        # required=True,
        help="Best architecture selected  json file by experiment. (default: %(default)s)")
    parser.add_argument(
        "--best_checkpoint_dir",
        type=str,
        default="best_checkpoint",
        help="Path for saved checkpoints. (default: %(default)s)")
    parser.add_argument('--trial_id', type=int, default=0, metavar='N',
                    help='trial_id,start from 0')
    parser.add_argument("--search_for",
        choices=["macro", "micro"],
        default="macro")
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
        "--epochs",
        type=int,
        default=10,
        help="The number of training epochs. (default: %(default)s)")
    parser.add_argument(
        "--lr",
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
        "--child_l2_reg",
        type=float,
        default=3e-6,
        help="Weight decay factor. (default: %(default)s)")
    parser.add_argument(
        "--child_lr_decay_scheme",
        type=str,
        default="cosine",
        help="Learning rate annealing strategy, only 'cosine' supported. (default: %(default)s)") #todo: remove
    global FLAGS
    FLAGS = parser.parse_args()

# decode human readable search space to model
def convert_selected_space_format(child_fixed_arc):
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

def set_random_seed(seed):
    logger.info("set random seed for data reading: {}".format(seed))
    random.seed(seed)
    os.environ['PYTHONHASHSEED'] = str(seed)
    np.random.seed(seed)
    random.seed(seed)
    torch.manual_seed(seed)
    if FLAGS.is_cuda:
        torch.cuda.manual_seed_all(seed)
        torch.backends.cudnn.deterministic = True

def main():

    parse_args()

    child_fixed_arc = FLAGS.best_selected_space_path  # './macro_seletced_space'
    search_for = FLAGS.search_for

    # set seed to result todo: trial ID
    set_random_seed(FLAGS.trial_id)

    mkdirs(FLAGS.result_path, FLAGS.log_path, FLAGS.best_checkpoint_dir)
    # define and load model
    logger.info('** ' + FLAGS.search_for + 'search **')
    fixed_arc = convert_selected_space_format(child_fixed_arc)
    # Model, macro search or micro search
    if FLAGS.search_for == 'macro':
        child_model = GeneralNetwork()
    elif FLAGS.search_for == 'micro':
        child_model = MicroNetwork(num_layers=6, out_channels=20, num_nodes=5, dropout_rate=0.1, use_aux_heads=True)

    apply_fixed_architecture(child_model, fixed_arc)

    # load model
    if FLAGS.load_checkpoint:
        print('** Load model **')
        logger.info('** Load model **')
        child_model.load_state_dict(torch.load(FLAGS.best_checkpoint_dir)['child_model_state_dict'])

    retrainer = EnasRetrainer(model=child_model,
                              data_dir = FLAGS.data_dir,
                              best_checkpoint_dir=FLAGS.best_checkpoint_dir,
                              batch_size=FLAGS.batch_size,
                              eval_batch_size=FLAGS.eval_batch_size,
                              num_epochs=FLAGS.epochs,
                              lr=FLAGS.lr,
                              is_cuda=FLAGS.is_cuda,
                              log_every=FLAGS.log_every,
                              child_grad_bound=FLAGS.child_grad_bound,
                              child_l2_reg=FLAGS.child_l2_reg,
                              eval_every_epochs=FLAGS.eval_every_epochs,
                              logger=logger,
                              result_path=FLAGS.result_path,
                              )

    t1 = time.time()
    retrainer.train()
    print('cost time for retrain: ' , time.time() - t1)

if __name__ == "__main__":
    main()