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

import codecs
import sys
import os
import logging
import classify_by_textcnn as classify

logging.basicConfig(format='%(asctime)s - %(pathname)s[line:%(lineno)d] - %(levelname)s: %(message)s',
                    level=logging.DEBUG)

sys.stdout = codecs.getwriter("utf-8")(sys.stdout.detach())
current_dir = os.path.dirname(os.path.abspath(__file__))
label_to_name_file = current_dir + os.sep + "label.names"
label_2_name = []
with open(label_to_name_file, 'r') as f:
    label_2_name = f.readlines()


def execute(task):
        return textClassificationExecutor(task)

def textClassificationExecutor(jsonObject):
    """Annotation task method.
                Args:
                    redisClient: redis client.
                    key: annotation task key.
    """
    try:
        text_path_list = []
        id_list = []
        labels = []
        label_list = jsonObject['labels']
        for label in label_list:
            for i in range(0, len(label_2_name)):
                if (label == label_2_name[i].rstrip('\n')):
                    labels.append(i)
        for fileObject in jsonObject['files']:
            text_path_list.append(fileObject['url'])
            id_list.append(fileObject['id'])
        logging.debug(text_path_list)
        classifications = _classification(text_path_list, id_list, labels)  # --------------
        return {"annotations": classifications}
    except Exception as e:
        print(e)

def _init():
    logging.info('init classify_obj')
    global classify_obj
    classify_obj = classify.TextCNNClassifier()

def _classification(text_path_list, id_list, label_list):
    """Perform automatic text classification task."""
    textnum = len(text_path_list)
    batched_num = ((textnum - 1) // classify.BATCH_SIZE + 1) * classify.BATCH_SIZE
    for i in range(batched_num - textnum):
        text_path_list.append(text_path_list[0])
        id_list.append(id_list[0])
    logging.info("-------------1111")    
    annotations = classify_obj.inference(text_path_list, id_list, label_list)  #
    return annotations[0:textnum]
