# import os
# debugger_path = os.path.abspath("./")
# os.chdir(debugger_path)

import json
import argparse
from evolution_tuner import EvolutionWithFlops

from evaluator import Evaluator
import sys
sys.path.append("../")
sys.path.append("../../")


def load_search_space(path="./auto_gen_search_space.json"):
    with open(path) as f:
        search_space = json.load(f)
    return search_space


def trial(args, trial_id, search_space):
    """
    search the best model by evoluationary algo
    """

    evolution_spos = EvolutionWithFlops(max_epochs=args.max_epoches,
                                       num_select=args.num_select,
                                       num_population=args.num_population,
                                       m_prob=args.m_prob,
                                       num_crossover=args.num_crossover,
                                       num_mutation=args.num_mutation,
                                       epoch=trial_id, 
                                       )

    evolution_spos.update_search_space(search_space)


if __name__ == "__main__":

    parser = argparse.ArgumentParser("search the net by evolution")
    parser.add_argument("--search_space_path", type=str, default="./auto_gen_search_space.json")
    parser.add_argument("--checkpoint", type=str, default="./data/checkpoint-150000.pth.tar")     # ./data/checkpoint-150000.pth.tar
    parser.add_argument("--num_select", type=int, default=2)     # 10
    parser.add_argument("--num_population", type=int, default=4)    # 50
    parser.add_argument("--workers", type=int, default=1)  # 线程数
    parser.add_argument("--num_crossover", type=int, default=2)    # 25
    parser.add_argument("--num_mutation", type=int, default=2)    # 25
    parser.add_argument("--max_epoches", type=int, default=3)    # 20
    parser.add_argument("--trial_id", type=int, default=1)
    parser.add_argument("--m_prob", type=float, default=0.1)

    parser.add_argument("--imagenet-dir", type=str, default="/mnt/local/hanjiayi/imagenet")     # ./data/imagenet
    parser.add_argument("--spos-preprocessing", default=True,
                        help="When true, image values will range from 0 to 255 and use BGR "
                             "(as in original repo).")    
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--train-batch-size", type=int, default=128)
    parser.add_argument("--train-iters", type=int, default=200)
    parser.add_argument("--test-batch-size", type=int, default=512)    # nni中为512，官方repo为200
    parser.add_argument("--log-frequency", type=int, default=10)
    parser.add_argument("--architecture", type=str, default="./architecture_final.json", help="load the file to retrain or eval")

    args = parser.parse_args()

    search_space = load_search_space(path=args.search_space_path)
    # evl = Evaluator()

    # if args.single_trial:
    #     epoch = 0
    #     print("*" * 50, "\n")
    #     print("epoch {}{}".format(epoch, "\n"))
    #     print("*" * 50, "\n")
    #     trial(args, epoch, search_space=search_space)
    # else:
    #     for epoch in range(2, args.max_epoches+2):
    #         print("*"*50, "\n")
    #         print("epoch {}{}".format(epoch, "\n"))
    #         print("*"*50, "\n")
    #         trial(args, epoch, search_space=search_space)

    trial(args, args.trial_id, search_space)
