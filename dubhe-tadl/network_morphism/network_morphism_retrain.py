import sys
sys.path.append('..'+ '/' + '..')
import os
import logging
from pytorch.network_morphism.network_morphism_trainer import NetworkMorphismTrainer
import argparse
from pytorch.utils import init_logger, mkdirs
import json

logger = logging.getLogger(__name__)

class Retrain:
    def __init__(self, args):
        self.args = args

    def run(self):
        logger.info("Retraining the best model.")
        with open(args.best_selected_space_path, 'r') as f:
            json_out = json.load(f)
        json_out = json.dumps(json_out)

        trainer = NetworkMorphismTrainer(json_out, self.args)
        trainer.retrain()


if __name__ == "__main__":
    parser = argparse.ArgumentParser("network_morphism_retrain")
    parser.add_argument("--trial_id", type=int, default=0, help="Trial id")
    parser.add_argument("--log_path", type=str,
                        default='./log', help="log for info")
    parser.add_argument(
        "--experiment_dir", type=str, default='./TADL', help="experiment level path"
    )
    parser.add_argument(
        "--best_selected_space_path", type=str, default='./best_selected_space.json', help="Path to best selected space"
    )
    parser.add_argument(
        "--result_path", type=str, default='./result.json', help="Path to result"
    )
    parser.add_argument(
        "--best_checkpoint_dir", type=str, default='./', help="Path to checkpoint saved"
    )
    parser.add_argument(
        "--data_dir", type=str, default='../data/', help="Path to dataset"
    )
    parser.add_argument("--batch_size", type=int,
                        default=128, help="batch size")
    parser.add_argument("--opt", type=str, default="SGD", help="optimizer")
    parser.add_argument("--epochs", type=int, default=200, help="epoch limit")
    parser.add_argument(
        "--lr", type=float, default=0.001, help="learning rate"
    )
    args = parser.parse_args()
    mkdirs(args.result_path, args.log_path, args.best_checkpoint_dir)
    init_logger(args.log_path)
    retrain = Retrain(args)
    retrain.run()
