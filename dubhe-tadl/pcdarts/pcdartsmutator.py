
# Copyright (c) Microsoft Corporation.
# Licensed under the MIT license.

import logging

import torch
import torch.nn as nn
import torch.nn.functional as F
from collections import OrderedDict
from pytorch.mutator import Mutator
from pytorch.mutables import LayerChoice, InputChoice

_logger = logging.getLogger(__name__)

class PCdartsMutator(Mutator):
    
    """
    Connects the model in a PC-DARTS (differentiable) way.

    Two connections are automatically inserted for each LayerChoice and InputChoice, when these connections are selected by softmax function. 
    Ops on the LayerChoice are selected by max top-k probabilities. But channels in the all candicate predecessors on the InputChoice are weighted sum 
    There is no op on this LayerChoice (namely a ``ZeroOp``), in which case, every element in the exported choice list is ``false``
    (not chosen).

    All input choice will be fully connected in the search phase. On exporting, the input choice will choose inputs based
    on keys in ``choose_from``. If the keys were to be keys of LayerChoices, the top logit of the corresponding LayerChoice
    will join the competition of input choice to compete against other logits. Otherwise, the logit will be assumed 0.

    It's possible to cut branches by setting parameter ``choices`` in a particular position to ``-inf``. After softmax, the
    value would be 0. Framework will ignore 0 values and not connect. Note that the gradient on the ``-inf`` location will
    be 0. Since manipulations with ``-inf`` will be ``nan``, you need to handle the gradient update phase carefully.  

    Attributes
    ----------
    choices: ParameterDict
        dict that maps keys of LayerChoices to weighted-connection float tensors.
    """
    def __init__(self, model):
        super().__init__(model)
        self.choices = nn.ParameterDict()
        for mutable in self.mutables:
            if isinstance(mutable, LayerChoice):
                self.choices[mutable.key] = nn.Parameter(1.0E-3 * torch.randn(mutable.length + 1))
            if isinstance(mutable, InputChoice):
                self.choices[mutable.key] = nn.Parameter(1.0E-3 * torch.randn(mutable.n_candidates))
            
    def device(self):
        for v in self.choices.values():
            return v.device

    def sample_search(self):
        result = dict()
        for mutable in self.mutables:
            if isinstance(mutable, LayerChoice):
                result[mutable.key] = F.softmax(self.choices[mutable.key], dim=-1)[:-1]
            elif isinstance(mutable, InputChoice):
                result[mutable.key] = F.softmax(self.choices[mutable.key], dim=-1)
        return result

    def sample_final(self):
        result = dict()
        edges_max = dict()
        choices = dict()
        for mutable in self.mutables:
            if isinstance(mutable, LayerChoice):
                # multiply the normalized coefficients together to select top-1 op in each LayerChoice
                predecessor_idx = int(mutable.key[-1])
                inputchoice_key = mutable.key[:-2] + "switch"
                choices[mutable.key] = self.choices[mutable.key] * self.choices[inputchoice_key][predecessor_idx]
        for mutable in self.mutables:
            if isinstance(mutable, LayerChoice):
                # select non-none top-1 op
                max_val, index = torch.max(F.softmax(choices[mutable.key], dim=-1)[:-1], 0)
                edges_max[mutable.key] = max_val
                result[mutable.key] = F.one_hot(index, num_classes=len(mutable)).view(-1).bool()
        for mutable in self.mutables:
            if isinstance(mutable, InputChoice):
                if mutable.n_chosen is not None:
                    weights = []
                    for src_key in mutable.choose_from:
                        if src_key not in edges_max:
                            _logger.warning("InputChoice.NO_KEY in '%s' is weighted 0 when selecting inputs.", mutable.key)
                        weights.append(edges_max.get(src_key, 0.))
                    weights = torch.tensor(weights)  # pylint: disable=not-callable
                    # select top-2 strongest predecessor
                    _, topk_edge_indices = torch.topk(weights, mutable.n_chosen)
                    selected_multihot = []
                    for i, src_key in enumerate(mutable.choose_from):
                        if i not in topk_edge_indices and src_key in result:
                            # If an edge is never selected, there is no need to calculate any op on this edge.
                            # This is to eliminate redundant calculation.
                            result[src_key] = torch.zeros_like(result[src_key])
                        selected_multihot.append(i in topk_edge_indices)
                    result[mutable.key] = torch.tensor(selected_multihot, dtype=torch.bool, device=self.device())  # pylint: disable=not-callable
                else:
                    result[mutable.key] = torch.ones(mutable.n_candidates, dtype=torch.bool, device=self.device())  # pylint: disable=not-callable
        return result

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

