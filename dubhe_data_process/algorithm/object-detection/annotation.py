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
import sys
import os
import codecs
import logging
import predict_with_print_box as yolo_demo
logging.basicConfig(format='%(asctime)s - %(pathname)s[line:%(lineno)d] - %(levelname)s: %(message)s',
                    level=logging.DEBUG)
sys.stdout = codecs.getwriter("utf-8")(sys.stdout.detach())
current_dir = os.path.dirname(os.path.abspath(__file__))
label_to_name_file = current_dir + os.sep + "coco.names"
label_2_name = []
with open(label_to_name_file, 'r') as f:
    label_2_name = f.readlines()

def execute(task):
    return annotationExecutor(task)

def annotationExecutor(jsonObject):
    """Annotation task method.
                Args:
                    redisClient: redis client.
                    key: annotation task key.
    """
    try:
        image_path_list = []
        id_list = []
        label_list = jsonObject['labels']
        labels = []
        for label in label_list:
            for i in range(0, len(label_2_name)):
                if (label == label_2_name[i].rstrip('\n')):
                    labels.append(i)
        for fileObject in jsonObject['files']:
            pic_url = fileObject['url']
            image_path_list.append(pic_url)
            id_list.append(fileObject['id'])
        annotations = _annotation(0, image_path_list, id_list, labels);
        finish_data = {"annotations": annotations}
        return finish_data
    except Exception as e:
        finish_data = {"annotations": annotations}
        return finish_data

def _init():
    print('init yolo_obj')
    global yolo_obj
    yolo_obj = yolo_demo.YoloInference()

def _annotation(type_, image_path_list, id_list, label_list):
    """Perform automatic annotation task."""
    image_num = len(image_path_list)
    if image_num < 16:
        for i in range(16 - image_num):
            image_path_list.append(image_path_list[0])
            id_list.append(id_list[0])
    image_num = len(image_path_list)
    annotations = yolo_obj.yolo_inference(type_, id_list, image_path_list, label_list)
    return annotations[0:image_num]