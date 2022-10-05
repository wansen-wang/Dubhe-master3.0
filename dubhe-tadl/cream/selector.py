import sys

sys.path.append('../..')
from pytorch.selector import Selector


class ClassicnasSelector(Selector):
    def __init__(self, *args, single_candidate=True):
        super().__init__(single_candidate)
        self.args = args

    def fit(self):
        """
        only one candatite, function passed
        """
        pass


if __name__ == "__main__":
    hpo_selector = ClassicnasSelector()
    hpo_selector.fit()
