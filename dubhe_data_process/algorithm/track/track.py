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
from track_only.hog_track import *

delayId = ""


def execute(task):
        return trackProcess(task)

def trackProcess(task):
    """Track task method.
            Args:
                task: dataset id.
                key: video file path.
            Returns:
                True: track success
                False: track failed
    """
    global delayId
    image_list = []
    label_list = []
    images_data = task['images']
    path = task['path']
    dataset_id = task['id']
    labels = []
    for label in task['labels']:
        labels.append(int(label))
    result = True

    for file in images_data:
        filePath = path + "/origin/" + file
        annotationPath = path + "/annotation/" + file.split('.')[0]
        if not os.path.exists(filePath):
            continue
        if not os.path.exists(annotationPath):
            continue
        image_list.append(filePath)
        label_list.append(annotationPath)
    image_num = len(label_list)
    track_det = Detector(
        'xxx.avi',
        min_confidence=0.35,
        max_cosine_distance=0.2,
        max_iou_distance=0.7,
        max_age=30,
        out_dir='results/')
    track_det.write_img = False
    RET = track_det.run_track(image_list, label_list, labels)
    finished_json = {'id': dataset_id}
    if RET == 'OK':
        return finished_json
    else:
        return finished_json  
