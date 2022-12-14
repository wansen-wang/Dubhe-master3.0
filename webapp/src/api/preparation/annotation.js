/** Copyright 2020 Tianshu AI Platform. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =============================================================
 */

import request from '@/utils/request';
import { API_MODULE_NAME } from '@/config';

export function batchFinishAnnotation(data, datasetId) {
  return request({
    url: `/${API_MODULE_NAME.DATA}/datasets/files/${datasetId}/annotations`,
    method: 'post',
    data,
  });
}

export function delAnnotation(id) {
  const delData = { datasetId: id };
  return request({
    url: `/${API_MODULE_NAME.DATA}/datasets/files/annotations`,
    method: 'delete',
    data: delData,
  });
}

export function track(id, modelServiceId) {
  return request({
    url: `/${API_MODULE_NAME.DATA}/datasets/files/annotations/auto/track/${id}/${modelServiceId}`,
    method: 'get',
  });
}

export function annotateStatus(id) {
  return request({
    url: `/${API_MODULE_NAME.DATA}/datasets/${id}`,
    method: 'get',
  });
}

// 发布版本
export function publish(data = {}) {
  return request({
    url: `/${API_MODULE_NAME.DATA}/datasets/versions`,
    method: 'post',
    data,
  });
}
