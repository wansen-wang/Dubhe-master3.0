import copy

import numpy as np
import torch
import logging
from collections import OrderedDict
from torch import nn

from pytorch.darts.dartsmutator import DartsMutator
from pytorch.mutables import LayerChoice, InputChoice

logger = logging.getLogger(__name__)

class PdartsMutator(DartsMutator):
    """
    It works with PdartsTrainer to calculate ops weights,
    and drop weights in different PDARTS epochs.
    """
    def __init__(self, model, pdarts_epoch_index, pdarts_num_to_drop, switches={}):
        self.pdarts_epoch_index = pdarts_epoch_index
        self.pdarts_num_to_drop = pdarts_num_to_drop
        # save the last two switches and choices for restrict skip
        self.last_two_switches = None 
        self.last_two_choices = None

        if switches is None:
            self.switches = {}
        else:
            self.switches = switches

        super(PdartsMutator, self).__init__(model)

        # this loop go through mutables with different keys,
        # it's mainly to update length of choices.
        for mutable in self.mutables:
            if isinstance(mutable, LayerChoice):
                switches = self.switches.get(mutable.key, [True for j in range(len(mutable))])
                # choices = self.choices[mutable.key]

                operations_count = np.sum(switches)
                # +1 and -1 are caused by zero operation in darts network
                # the zero operation is not in choices list(switches) in network, but its weight are in,
                # so it needs one more weights and switch for zero.
                self.choices[mutable.key] = nn.Parameter(1.0E-3 * torch.randn(operations_count + 1))
                self.switches[mutable.key] = switches

        # update LayerChoice instances in model,
        # it's physically remove dropped choices operations.
        for module in self.model.modules():
            if isinstance(module, LayerChoice):
                switches = self.switches.get(module.key)
                choices = self.choices[module.key]
                if len(module) > len(choices):
                    # from last to first, so that it won't effect previous indexes after removed one.
                    for index in range(len(switches)-1, -1, -1):
                        if switches[index] == False:
                            del module[index]
                assert len(module) <= len(choices), "Failed to remove dropped choices."

    def export(self, last, switches):
        # In last pdarts_epoches, need to restrict skipconnection                  
        # Cannot rely on super().export() because P-DARTS has deleted some of the choices and has misaligned length.
        if last:
            # restrict Up to 2 skipconnect (normal cell only)
            name = "normal"
            max_num =  2
            skip_num = self.check_skip_num(name, switches)
            logger.info("Initially, the number of skipconnect is {}.".format(skip_num))
            while skip_num > max_num:
                logger.info("Restricting {} skipconnect to {}.".format(skip_num, max_num))
                logger.info("Original normal_switch is {}.".format(switches))
                # update self.choices setting skip prob to 0 and self.switches setting skip prob to False
                switches = self.delete_min_sk(name, switches)
                logger.info("Restricted normal_switch is {}.".format(switches))
                skip_num = self.check_skip_num(name, switches)
        
        # from bool result convert to human readable by Mutator export()
        results = super().sample_final()
        for mutable in self.mutables:
            if isinstance(mutable, LayerChoice):
                # As some operations are dropped physically,
                # so it needs to fill back false to track dropped operations.
                trained_result = results[mutable.key]
                trained_index = 0
                switches = self.switches[mutable.key]
                result = torch.Tensor(switches).bool()
                for index in range(len(result)):
                    if result[index]:
                        result[index] = trained_result[trained_index]
                        trained_index += 1
                results[mutable.key] = result
        return results

    def drop_paths(self):
        """
        This method is called when a PDARTS epoch is finished.
        It prepares switches for next epoch.
        candidate operations with False switch will be doppped in next epoch.
        """
        all_switches = copy.deepcopy(self.switches)
        for key in all_switches:
            switches = all_switches[key]
            idxs = []
            for j in range(len(switches)):
                if switches[j]:
                    idxs.append(j)
            sorted_weights = self.choices[key].data.cpu().numpy()[:-1]
            drop = np.argsort(sorted_weights)[:self.pdarts_num_to_drop[self.pdarts_epoch_index]]
            for idx in drop:
                switches[idxs[idx]] = False
        return all_switches


    def check_skip_num(self, name, switches):
        counter = 0
        for key in switches:
            if name in key:
                # zero operation not in switches, so "skipconnect" in 2
                if switches[key][2]:
                    counter += 1
        return counter

    def delete_min_sk(self, name, switches):
        def _get_sk_idx(key, switches):
            if not switches[key][2]:
                idx = -1
            else:
                idx = 0
                for i in range(2):
                    # switches has 1 True, self.switches has 2 True
                    if self.switches[key][i]:
                        idx += 1
            return idx
        sk_choices = [1.0 for i in range(14)]
        sk_keys = [None for i in range(14)] # key has skip connection
        sk_choices_idx = -1
        for key in switches:
            if name in key:
                # default key in order
                sk_choices_idx += 1
                idx = _get_sk_idx(key, switches)
                if not idx == -1:
                    sk_keys[sk_choices_idx] = key
                    sk_choices[sk_choices_idx] = self.choices[key][idx]
        min_sk_idx = np.argmin(sk_choices)
        idx = _get_sk_idx(sk_keys[min_sk_idx], switches)
        # modify self.choices or copy.deepcopy ?
        self.choices[sk_keys[min_sk_idx]][idx] =  0.0
        # modify self.switches or copy.deepcopy ?
        # self.switches indicate last two switches, and switches indicate present(last) switches
        self.switches[sk_keys[min_sk_idx]][2] = False
        switches[sk_keys[min_sk_idx]][2] = False
        return switches


    def _generate_search_space(self):
        """
        Generate search space from mutables.
        Here is the search space format:
        ::
            { key_name: {"_type": "layer_choice",
                         "_value": ["conv1", "conv2"]} }
            { key_name: {"_type": "input_choice",
                         "_value": {"candidates": ["in1", "in2"],
                                    "n_chosen": 1}} }
        Returns
        -------
        dict
            the generated search space
        """
        res = OrderedDict()
        res["op_list"] = OrderedDict()
        res["search_space"] = {"reduction_cell": OrderedDict(), "normal_cell": OrderedDict()}
        keys = []
        for mutable in self.mutables:
            # for now we only generate flattened search space
            if (len(res["search_space"]["reduction_cell"]) + len(res["search_space"]["normal_cell"])) >= 36:
                break

            if isinstance(mutable, LayerChoice):
                key = mutable.key
                if key not in keys:
                    val = mutable.names
                    if not res["op_list"]:
                        res["op_list"] = {"_type": "layer_choice", "_value": val + ["none"]}
                    node_type = "normal_cell" if "normal" in key else "reduction_cell"
                    res["search_space"][node_type][key] = "op_list"
                    keys.append(key)

            elif isinstance(mutable, InputChoice):
                key = mutable.key
                if key not in keys:
                    node_type = "normal_cell" if "normal" in key else "reduction_cell"
                    res["search_space"][node_type][key] = {"_type": "input_choice",
                                                    "_value": {"candidates": mutable.choose_from,
                                                               "n_chosen": mutable.n_chosen}}
                    keys.append(key)
            else:
                raise TypeError("Unsupported mutable type: '%s'." % type(mutable))

        return res