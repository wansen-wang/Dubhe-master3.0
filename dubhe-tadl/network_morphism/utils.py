from enum import Enum
import json

class Constant:
    # Data
    CUTOUT_HOLES = 1
    CUTOUT_RATIO = 0.5

    # Searcher
    MAX_MODEL_NUM = 1000
    MAX_LAYERS = 200
    N_NEIGHBOURS = 8
    MAX_MODEL_SIZE = (1 << 25)
    MAX_LAYER_WIDTH = 4096
    KERNEL_LAMBDA = 1.0
    BETA = 2.576
    T_MIN = 0.0001


    MLP_MODEL_LEN = 3
    MLP_MODEL_WIDTH = 5
    MODEL_LEN = 3
    MODEL_WIDTH = 64
    POOLING_KERNEL_SIZE = 2
    DENSE_DROPOUT_RATE = 0.5
    CONV_DROPOUT_RATE = 0.25
    MLP_DROPOUT_RATE = 0.25
    CONV_BLOCK_DISTANCE = 2

    # trainer
    MAX_NO_IMPROVEMENT_NUM = 5
    MIN_LOSS_DEC = 1e-4


class OptimizeMode(Enum):
    """Optimize Mode class

    if OptimizeMode is 'minimize', it means the tuner need to minimize the reward
    that received from Trial.

    if OptimizeMode is 'maximize', it means the tuner need to maximize the reward
    that received from Trial.
    """
    Minimize = 'minimize'
    Maximize = 'maximize'

class EarlyStop:
    """A class check for early stop condition.
    Attributes:
        training_losses: Record all the training loss.
        minimum_loss: The minimum loss we achieve so far. Used to compared to determine no improvement condition.
        no_improvement_count: Current no improvement count.
        _max_no_improvement_num: The maximum number specified.
        _done: Whether condition met.
        _min_loss_dec: A threshold for loss improvement.
    """

    def __init__(self, max_no_improvement_num=None, min_loss_dec=None):
        self.training_losses = []
        self.minimum_loss = None
        self.no_improvement_count = 0
        self._max_no_improvement_num = max_no_improvement_num if max_no_improvement_num is not None \
            else Constant.MAX_NO_IMPROVEMENT_NUM
        self._done = False
        self._min_loss_dec = min_loss_dec if min_loss_dec is not None else Constant.MIN_LOSS_DEC

    def on_train_begin(self):
        """Initiate the early stop condition.
        Call on every time the training iteration begins.
        """
        self.training_losses = []
        self.no_improvement_count = 0
        self._done = False
        self.minimum_loss = float('inf')

    def on_epoch_end(self, loss):
        """Check the early stop condition.
        Call on every time the training iteration end.
        Args:
            loss: The loss function achieved by the epoch.
        Returns:
            True if condition met, otherwise False.
        """
        self.training_losses.append(loss)
        if self._done and loss > (self.minimum_loss - self._min_loss_dec):
            return False

        if loss > (self.minimum_loss - self._min_loss_dec):
            self.no_improvement_count += 1
        else:
            self.no_improvement_count = 0
            self.minimum_loss = loss

        if self.no_improvement_count > self._max_no_improvement_num:
            self._done = True

        return True

def save_json_result(path, data):
    with open(path,'a') as f:
        json.dump(data,f)
        f.write('\n')
