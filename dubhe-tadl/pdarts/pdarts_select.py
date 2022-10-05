import sys
sys.path.append('../..')
from pytorch.selector import Selector
from argparse import ArgumentParser


class PdartsSelector(Selector):
    def __init__(self, single_candidate=True):
        super().__init__(single_candidate)
    
    def fit(self):
        pass

if __name__ == "__main__":
    parser = ArgumentParser("PDARTS select")
    parser.add_argument("--best_selected_space_path", type=str,
                        default='./best_selected_space.json', help="final best selected space")

    args = parser.parse_args()
    darts_selector = PdartsSelector(True)
    darts_selector.fit()
    