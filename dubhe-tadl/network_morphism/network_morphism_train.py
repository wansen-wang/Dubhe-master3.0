import sys
sys.path.append('..'+ '/' + '..')
import os
import logging
from pytorch.network_morphism.network_morphism_trainer import NetworkMorphismTrainer
from pytorch.network_morphism.algorithm.networkmorphism_searcher import NetworkMorphismSearcher
import argparse
import pickle
from pytorch.utils import init_logger, mkdirs

logger = logging.getLogger(__name__)

def create_dir(path):
    if os.path.exists(path):
        # shutil.rmtree(path)
        return path
    os.makedirs(path)
    return path

class Train:
    def __init__(self, args):
        self.id = args.trial_id
        self.trial_dir = os.path.join(
            args.experiment_dir, 'train', str(args.trial_id))
        self.searcher_dir = os.path.join(
            args.experiment_dir, '{}.pkl'.format(NetworkMorphismSearcher.__name__))
        self.args = args
        self.searcher = None
        # first trial
        if not os.path.exists(self.searcher_dir):
            self.searcher = NetworkMorphismSearcher(os.path.join(
                args.experiment_dir, 'train'), args.best_selected_space_path)
        else:
            # load from previous round
            with open(self.searcher_dir, 'rb') as f:
                self.searcher = pickle.load(f)

    def run_trial_job(self):
        logger.info('trial {} search next model'.format(self.id))
        model = self.searcher.search(self.id,self.args)

        trainer = NetworkMorphismTrainer(model, self.args)
        logger.info('trial {} run training script'.format(self.id))
        metric = trainer.train()

        if metric != None:
            logger.info('trial {} receive trial result'.format(self.id))
            self.searcher.update_searcher(self.id, metric)

        with open(self.searcher_dir, 'wb') as f:
            pickle.dump(self.searcher, f)


if __name__ == "__main__":
    parser = argparse.ArgumentParser("network_morphism")
    parser.add_argument("--trial_id", type=int, default=0, help="Trial id")
    parser.add_argument(
        "--data_dir", type=str, default='../data/', help="Path to dataset"
    )
    parser.add_argument(
        "--log_path", type=str, default='./log', help="Path to log file"
    )
    parser.add_argument(
        "--experiment_dir", type=str, default='./TADL', help="experiment level path"
    )
    parser.add_argument(
        "--result_path", type=str, default='./result.json', help="trial level path to result"
    )
    parser.add_argument(
        "--search_space_path", type=str, default='./search_space.json', help="experiment level path to search space"
    )
    parser.add_argument(
        "--best_selected_space_path", type=str, default='./best_selected_space.json', help="experiment level path to best selected space"
    )
    parser.add_argument("--batch_size", type=int,
                        default=128, help="batch size")
    parser.add_argument("--opt", type=str, default="SGD", help="optimizer")
    parser.add_argument("--epochs", type=int, default=2, help="epoch limit")
    parser.add_argument(
        "--lr", type=float, default=0.001, help="learning rate"
    )
    args = parser.parse_args()
    mkdirs(args.experiment_dir, args.result_path, args.log_path, args.search_space_path, args.best_selected_space_path)
    create_dir(os.path.join(args.experiment_dir,'train',str(args.trial_id)))
    init_logger(args.log_path)
    train = Train(args)
    train.run_trial_job()
