import sys
sys.path.append('..'+ '/' + '..')
import time
import logging
from argparse import ArgumentParser
from pdartstrainer import PdartsTrainer
from pytorch.utils import mkdirs, set_seed, init_logger, list_str2int

logger = logging.getLogger(__name__)

if __name__ == "__main__":
    parser = ArgumentParser("pdarts")
    parser.add_argument("--data_dir", type=str,
                        default='../data/', help="search_space json file")
    parser.add_argument("--result_path", type=str,
                        default='0/result.json', help="training result")
    parser.add_argument("--log_path", type=str,
                        default='0/log', help="log for info")
    parser.add_argument("--search_space_path", type=str,
                        default='./search_space.json', help="search space of PDARTS")
    parser.add_argument("--best_selected_space_path", type=str,
                        default='./best_selected_space.json', help="final best selected space")
    parser.add_argument('--trial_id', type=int, default=0, help='for ensuring reproducibility ')
    parser.add_argument('--model_lr', type=float, default=0.025, help='learning rate for training model weights')
    parser.add_argument('--arch_lr', type=float, default=3e-4, help='learning rate for training architecture')
    parser.add_argument("--epochs", default=2, type=int)
    parser.add_argument("--pre_epochs", default=15, type=int)
    parser.add_argument("--batch_size", default=96, type=int)
    parser.add_argument("--init_layers", default=5, type=int)
    parser.add_argument('--add_layers', default=[0, 6, 12], nargs='+', type=int, help='add layers in each stage')
    parser.add_argument('--dropped_ops', default=[3, 2, 1], nargs='+', type=int, help='drop ops in each stage')
    parser.add_argument('--dropout_rates', default=[0.1, 0.4, 0.7], nargs='+', type=float, help='drop ops probability in each stage')
    # parser.add_argument('--add_layers', action='append', help='add layers in each stage')
    # parser.add_argument('--dropped_ops', action='append', help='drop ops in each stage')
    # parser.add_argument('--dropout_rates', action='append', help='drop ops probability in each stage')
    parser.add_argument("--channels", default=16, type=int)
    parser.add_argument("--log_frequency", default=50, type=int)
    parser.add_argument("--class_num", default=10, type=int)
    parser.add_argument("--unrolled", default=False, action="store_true")
    args = parser.parse_args()

    mkdirs(args.result_path, args.log_path, args.search_space_path, args.best_selected_space_path)
    init_logger(args.log_path, "info")
    set_seed(args.trial_id)
    # args.add_layers = list_str2int(args.add_layers)
    # args.dropped_ops = list_str2int(args.dropped_ops)
    # args.dropout_rates = list_str2int(args.dropout_rates)
    logger.info(args)
  
    logger.info("initializing pdarts trainer")
    trainer = PdartsTrainer(
                init_layers=args.init_layers,
                pdarts_num_layers=args.add_layers,
                pdarts_num_to_drop=args.dropped_ops,
                pdarts_dropout_rates=args.dropout_rates,
                num_epochs=args.epochs,
                num_pre_epochs=args.pre_epochs,
                model_lr=args.model_lr,
                arch_lr=args.arch_lr,
                batch_size=args.batch_size,
                class_num=args.class_num,
                channels=args.channels,
                result_path=args.result_path,
                log_frequency=args.log_frequency,
                unrolled=args.unrolled,
                data_dir = args.data_dir,
                search_space_path=args.search_space_path,
                best_selected_space_path=args.best_selected_space_path
                )
                
    logger.info("training")
    start_time = time.time()
    trainer.train(validate=True)
    # result = trainer.result
    cost_time = time.time() - start_time
   # 后端在终端过滤，{"type": "Cost_time", "result": {"value": "* s"}}
    logger.info({"type": "Cost_time", "result": {"value": str(cost_time) + ' s'}})
    with open(args.result_path, "a") as file:
        file.write(str({"type": "Cost_time", "result": {"value": str(cost_time) + ' s'}}))
