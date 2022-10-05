# !/usr/bin/env python
# -*- coding:utf-8 -*-

"""
Copyright 2020 Tianshu AI Platform. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
=============================================================
"""
import os
import logging
import json
import of_cnn_resnet as of_cnn_resnet
import numpy as np
current_dir = os.path.dirname(os.path.abspath(__file__))
label_to_name_file = current_dir + os.sep + "imagenet.names"
label_2_name = []
with open(label_to_name_file, 'r') as f:
    label_2_name = f.readlines()

def _init():
    of_cnn_resnet.init_resnet()
    logging.info('env init finished')

def execute(task):
    return process(task)

def process(task_dict):
    """Imagenet task method.
        Args:
            task_dict: imagenet task details.
            key: imagenet task key.
    """
    id_list = []
    image_path_list = []
    for file in task_dict["files"]:
        id_list.append(file["id"])
        image_path = file["url"]
        image_path_list.append(image_path)
    label_list = task_dict["labels"]
    labels = []
    for label in label_list:
        for i in range(0, len(label_2_name)):
            if (label == label_2_name[i].rstrip('\n')):
                labels.append(i)
    annotations = []
    for inds in range(len(image_path_list)):
        temp = {}
        temp['id'] = id_list[inds]
        temp['annotation'] = []
        score, ca_id = of_cnn_resnet.resnet_inf(image_path_list[inds])
        if ca_id in labels:
            label_name = label_2_name[int(ca_id)]
            temp['annotation'] = [{'category_id': label_name.rstrip('\n'), 'score': np.float(score)}]
        temp['annotation'] = json.dumps(temp['annotation'])
        annotations.append(temp)
    return {"annotations": annotations}