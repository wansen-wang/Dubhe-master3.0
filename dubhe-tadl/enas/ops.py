# Copyright (c) Microsoft Corporation.
# Licensed under the MIT license.

import torch
import torch.nn as nn


class StdConv(nn.Module):
    def __init__(self, C_in, C_out):
        super(StdConv, self).__init__()
        self.conv = nn.Sequential(
            nn.Conv2d(C_in, C_out, 1, stride=1, padding=0, bias=False),
            nn.BatchNorm2d(C_out, affine=False),
            nn.ReLU()
        )

    def forward(self, x):
        return self.conv(x)

    def __str__(self):
        return 'StdConv'


class PoolBranch(nn.Module):
    def __init__(self, pool_type, C_in, C_out, kernel_size, stride, padding, affine=False):
        super().__init__()
        self.kernel_size = kernel_size
        self.pool_type = pool_type
        self.preproc = StdConv(C_in, C_out)
        self.pool = Pool(pool_type, kernel_size, stride, padding)
        self.bn = nn.BatchNorm2d(C_out, affine=affine)

    def forward(self, x):
        out = self.preproc(x)
        out = self.pool(out)
        out = self.bn(out)
        return out

    def __str__(self):
        return '{}PoolBranch_{}'.format(self.pool_type, self.kernel_size)

class SeparableConv(nn.Module):
    def __init__(self, C_in, C_out, kernel_size, stride, padding):
        self.kernel_size = kernel_size
        super(SeparableConv, self).__init__()
        self.depthwise = nn.Conv2d(C_in, C_in, kernel_size=kernel_size, padding=padding, stride=stride,
                                   groups=C_in, bias=False)
        self.pointwise = nn.Conv2d(C_in, C_out, kernel_size=1, bias=False)

    def forward(self, x):
        out = self.depthwise(x)
        out = self.pointwise(out)
        return out

    def __str__(self):
        return 'SeparableConv_{}'.format(self.kernel_size)

class ConvBranch(nn.Module):
    def __init__(self, C_in, C_out, kernel_size, stride, padding, separable):
        super(ConvBranch, self).__init__()
        self.kernel_size = kernel_size
        self.preproc = StdConv(C_in, C_out)
        if separable:
            self.conv = SeparableConv(C_out, C_out, kernel_size, stride, padding)
        else:
            self.conv = nn.Conv2d(C_out, C_out, kernel_size, stride=stride, padding=padding)
        self.postproc = nn.Sequential(
            nn.BatchNorm2d(C_out, affine=False),
            nn.ReLU()
        )

    def forward(self, x):
        out = self.preproc(x)
        out = self.conv(out)
        out = self.postproc(out)
        return out

    def __str__(self):
        return 'ConvBranch_{}'.format(self.kernel_size)

class FactorizedReduce(nn.Module):
    def __init__(self, C_in, C_out, affine=False):
        super().__init__()
        self.conv1 = nn.Conv2d(C_in, C_out // 2, 1, stride=2, padding=0, bias=False)
        self.conv2 = nn.Conv2d(C_in, C_out // 2, 1, stride=2, padding=0, bias=False)
        self.bn = nn.BatchNorm2d(C_out, affine=affine)

    def forward(self, x):
        out = torch.cat([self.conv1(x), self.conv2(x[:, :, 1:, 1:])], dim=1)
        out = self.bn(out)
        return out

    def __str__(self):
        return 'FactorizedReduce'

class Pool(nn.Module):
    def __init__(self, pool_type, kernel_size, stride, padding):
        super().__init__()
        self.kernel_size = kernel_size
        self.pool_type = pool_type
        if pool_type.lower() == 'max':
            self.pool = nn.MaxPool2d(kernel_size, stride, padding)
        elif pool_type.lower() == 'avg':
            self.pool = nn.AvgPool2d(kernel_size, stride, padding, count_include_pad=False)
        else:
            raise ValueError()

    def forward(self, x):
        return self.pool(x)

    def __str__(self):
        return '{}Pool_{}'.format(self.pool_type, self.kernel_size)

class SepConvBN(nn.Module):
    def __init__(self, C_in, C_out, kernel_size, padding):
        super().__init__()
        self.kernel_size = kernel_size
        self.relu = nn.ReLU()
        self.conv = SeparableConv(C_in, C_out, kernel_size, 1, padding)
        self.bn = nn.BatchNorm2d(C_out, affine=True)

    def forward(self, x):
        x = self.relu(x)
        x = self.conv(x)
        x = self.bn(x)
        return x

    def __str__(self):
        return 'SepConvBN_{}'.format(self.kernel_size)