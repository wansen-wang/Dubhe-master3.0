import sys
sys.path.append('../..')
from pytorch.selector import Selector

class EnasSelector(Selector):
    def __init__(self, *args, single_candidate=True):
        super().__init__(single_candidate)
        self.args = args

    def fit(self):
        """
        only one candatite, function passed
        """
        pass

if __name__ == "__main__":
    hpo_selector = EnasSelector()
    hpo_selector.fit()