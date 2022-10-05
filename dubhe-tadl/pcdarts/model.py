# Copyright (c) Microsoft Corporation.
# Licensed under the MIT license.

from collections import OrderedDict

import torch
import torch.nn as nn

from pytorch import mutables
from pytorch.darts import ops

def random_channel_shuffle(x):
    num_channels = x.data.size()[1]
    indices = torch.randperm(num_channels)
    x = x[:, indices]
    return x 

def channel_shuffle(x, groups):
    batchsize, num_channels, height, width = x.data.size()

    channels_per_group = num_channels // groups
    
    # reshape
    x = x.view(batchsize, groups, 
        channels_per_group, height, width)

    x = torch.transpose(x, 1, 2).contiguous()

    # flatten
    x = x.view(batchsize, -1, height, width)

    return x

class AuxiliaryHead(nn.Module):
    """ Auxiliary head in 2/3 place of network to let the gradient flow well """

    def __init__(self, input_size, C, n_classes):
        """ assuming input size 7x7 or 8x8 """
        assert input_size in [7, 8]
        super().__init__()
        self.net = nn.Sequential(
            nn.ReLU(inplace=True),
            nn.AvgPool2d(5, stride=input_size - 5, padding=0, count_include_pad=False),  # 2x2 out
            nn.Conv2d(C, 128, kernel_size=1, bias=False),
            nn.BatchNorm2d(128),
            nn.ReLU(inplace=True),
            nn.Conv2d(128, 768, kernel_size=2, bias=False),  # 1x1 out
            nn.BatchNorm2d(768),
            nn.ReLU(inplace=True)
        )
        self.linear = nn.Linear(768, n_classes)

    def forward(self, x):
        out = self.net(x)
        out = out.view(out.size(0), -1)  # flatten
        logits = self.linear(out)
        return logits


class Node(nn.Module):
    def __init__(self, node_id, num_prev_nodes, channels, k, num_downsample_connect, search):
        super().__init__()
        if search:
            self.k = k
            partial_channles = channels // k
        else:
            partial_channles = channels
        
        self.search = search
        self.ops = nn.ModuleList()
        choice_keys = []
        for i in range(num_prev_nodes):
            stride = 2 if i < num_downsample_connect else 1
            choice_keys.append("{}_p{}".format(node_id, i))
            self.ops.append(
                mutables.LayerChoice(OrderedDict([
                    ("maxpool", ops.PoolBN('max', partial_channles, 3, stride, 1, affine=False)),
                    ("avgpool", ops.PoolBN('avg', partial_channles, 3, stride, 1, affine=False)),
                    ("skipconnect", nn.Identity() if stride == 1 else ops.FactorizedReduce(partial_channles, partial_channles, affine=False)),
                    ("sepconv3x3", ops.SepConv(partial_channles, partial_channles, 3, stride, 1, affine=False)),
                    ("sepconv5x5", ops.SepConv(partial_channles, partial_channles, 5, stride, 2, affine=False)),
                    ("dilconv3x3", ops.DilConv(partial_channles, partial_channles, 3, stride, 2, 2, affine=False)),
                    ("dilconv5x5", ops.DilConv(partial_channles, partial_channles, 5, stride, 4, 2, affine=False))
                ]), key=choice_keys[-1]))
        self.drop_path = ops.DropPath()
        self.input_switch = mutables.InputChoice(choose_from=choice_keys, n_chosen=2, key="{}_switch".format(node_id))
        self.pool = nn.MaxPool2d(2,2)

    def forward(self, prev_nodes):
        assert len(self.ops) == len(prev_nodes), "len(self.ops) != len(prev_nodes) in Node"
        # for each candicate predecessor of each intermediate node
        if self.search:
        # in search
            results = []
            for op, x in zip(self.ops, prev_nodes):
                # channel shuffle
                channels = x.shape[1]
                # channel proportion k=4
                temp0 = x[ : , : channels//self.k, : , :]
                temp1 = x[ : ,channels//self.k : , : , :]
                out = op(temp0)
                # normal 
                if out.shape[2] == x.shape[2]:
                    result = torch.cat([out, temp1], dim=1)
                # reduction
                else:
                    result = torch.cat([out, self.pool(temp1)], dim=1)
                results.append(channel_shuffle(result, self.k))

                # # channel random shuffule
                # channels = random_channel_shuffle(x).shape[1]
                # # channel proportion k=4
                # temp0 = x[ : , : channels//self.k, : , :]
                # temp1 = x[ : ,channels//self.k : , : , :]
                # out = op(temp0)
                # # normal 
                # if out.shape[2] == x.shape[2]:
                #     result = torch.cat([out, temp1], dim=1)
                # # reduction
                # else:
                #     result = torch.cat([out, self.pool(temp1)], dim=1)
                # results.append(result)
        else:
            # in retrain, no channel shuffle
            results = [op(node) for op, node in zip(self.ops, prev_nodes)]
            
        output = [self.drop_path(re) if re is not None else None for re in results]
        return self.input_switch(output)


class Cell(nn.Module):

    def __init__(self, n_nodes, channels_pp, channels_p, channels, reduction_p, reduction, k, search):
        super().__init__()
        self.reduction = reduction
        self.n_nodes = n_nodes

        # If previous cell is reduction cell, current input size does not match with
        # output size of cell[k-2]. So the output[k-2] should be reduced by preprocessing.
        if reduction_p:
            self.preproc0 = ops.FactorizedReduce(channels_pp, channels, affine=False)
        else:
            self.preproc0 = ops.StdConv(channels_pp, channels, 1, 1, 0, affine=False)
        self.preproc1 = ops.StdConv(channels_p, channels, 1, 1, 0, affine=False)

        # generate dag
        self.mutable_ops = nn.ModuleList()
        for depth in range(2, self.n_nodes + 2):
            self.mutable_ops.append(Node("{}_n{}".format("reduce" if reduction else "normal", depth), depth, channels, k, 2 if reduction else 0, search))

    def forward(self, s0, s1):
        # s0, s1 are the outputs of previous previous cell and previous cell, respectively.
        tensors = [self.preproc0(s0), self.preproc1(s1)]
        for node in self.mutable_ops:
            cur_tensor = node(tensors)
            tensors.append(cur_tensor)

        output = torch.cat(tensors[2:], dim=1)
        return output


class CNN(nn.Module):

    def __init__(self, input_size, in_channels, channels, n_classes, n_layers, k=4, n_nodes=4, stem_multiplier=3, auxiliary=False, search=True):
        super().__init__()
        self.in_channels = in_channels
        self.channels = channels
        self.n_classes = n_classes
        self.n_layers = n_layers
        self.n_nodes = n_nodes
        self.aux_pos = 2 * n_layers // 3 if auxiliary else -1

        c_cur = stem_multiplier * self.channels
        self.stem = nn.Sequential(
            nn.Conv2d(in_channels, c_cur, 3, 1, 1, bias=False),
            nn.BatchNorm2d(c_cur)
        )

        # for the first cell, stem is used for both s0 and s1
        # [!] channels_pp and channels_p is output channel size, but c_cur is input channel size.
        channels_pp, channels_p, c_cur = c_cur, c_cur, channels

        self.cells = nn.ModuleList()
        reduction_p, reduction = False, False
        for i in range(n_layers):
            reduction_p, reduction = reduction, False
            # Reduce featuremap size and double channels in 1/3 and 2/3 layer.
            if i in [n_layers // 3, 2 * n_layers // 3]:
                c_cur *= 2
                reduction = True

            cell = Cell(n_nodes, channels_pp, channels_p, c_cur, reduction_p, reduction, k, search)
            self.cells.append(cell)
            c_cur_out = c_cur * n_nodes
            channels_pp, channels_p = channels_p, c_cur_out

            if i == self.aux_pos:
                self.aux_head = AuxiliaryHead(input_size // 4, channels_p, n_classes)

        self.gap = nn.AdaptiveAvgPool2d(1)
        self.linear = nn.Linear(channels_p, n_classes)

    def forward(self, x):
        s0 = s1 = self.stem(x)

        aux_logits = None
        for i, cell in enumerate(self.cells):
            s0, s1 = s1, cell(s0, s1)
            if i == self.aux_pos and self.training:
                aux_logits = self.aux_head(s1)

        out = self.gap(s1)
        out = out.view(out.size(0), -1)  # flatten
        logits = self.linear(out)

        if aux_logits is not None:
            return logits, aux_logits
        return logits

    def drop_path_prob(self, p):
        for module in self.modules():
            if isinstance(module, ops.DropPath):
                module.p = p

    def _loss(self, input, target):
        logits = self(input)
        return self._criterion(logits, target) 
