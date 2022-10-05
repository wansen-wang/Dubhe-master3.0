from abc import ABC, abstractmethod


class Retrainer(ABC):

    """
    Train the best performance model from scratch without structure optimization.
    To implement a new selector, users need to implement:
        method: "train"
        method: "__init__"
            super().__init__() must be called in __init__ method

    parameters:
    -----------
    candidates: candidates to be evaluated
    """

    @abstractmethod
    def train(self):
        """
        Override the method to train.
        """
        raise NotImplementedError

    def validate(self):
        """
        Override the method to validate.
        """
        raise NotImplementedError

    def export(self, file):
        """
        Override the method to export to file.

        Parameters
        ----------
        file : str
            File path to export to.
        """
        raise NotImplementedError

    def checkpoint(self):
        """
        Override to dump a checkpoint.
        """
        raise NotImplementedError