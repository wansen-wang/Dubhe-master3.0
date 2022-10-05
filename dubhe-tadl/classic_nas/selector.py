import sys
sys.path.append('../..')
from pytorch.selector import Selector
from pytorch.utils import mkdirs
import shutil
import argparse
import os
import json

class ClassicnasSelector(Selector):
    def __init__(self, args, single_candidate=True):
        super().__init__(single_candidate)
        self.args = args

    def fit(self):
        """
        only one candatite, function passed
        """
        train_dir = os.path.join(self.args['experiment_dir'],'train') 
        max_accuracy = 0 
        best_selected_space = ''
        for trialId in os.listdir(train_dir):
            path= os.path.join(train_dir,trialId,'result','result.json')
            max_accuracy_trial = 0
            with open(path,'r') as f:
                for line in f:
                    result_dict = json.loads(line)
                    accuracy = result_dict["result"]["value"]
                    if accuracy>max_accuracy_trial:
                        max_accuracy_trial=accuracy
            print(max_accuracy_trial)
            if max_accuracy_trial > max_accuracy:
                max_accuracy = max_accuracy_trial
                best_selected_space = os.path.join(train_dir,trialId,'model_selected_space','model_selected_space.json')
                print('best trial id:',trialId)
        
        shutil.copyfile(best_selected_space,self.args['best_selected_space_path'])

            
def get_params():
    # Training settings
    parser = argparse.ArgumentParser(description='PyTorch MNIST Example')
    parser.add_argument("--experiment_dir", type=str,
                        default='./experiment_dir', help="data directory")
    parser.add_argument("--best_selected_space_path", type=str,
                    default='./best_selected_space.json', help="selected_space_path")

    args, _ = parser.parse_known_args()
    return args

if __name__ == "__main__":

    params = vars(get_params())
    args =params
    mkdirs(args['best_selected_space_path'])

    hpo_selector = ClassicnasSelector(args,single_candidate=False)
    hpo_selector.fit()