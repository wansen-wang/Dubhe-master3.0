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

/* eslint-disable no-unreachable */
import request from '@/utils/request';
import { API_MODULE_NAME } from '@/config';

export function list(params) {
  return request({
    url: `/${API_MODULE_NAME.ATLAS}/ptMeasure`,
    method: 'get',
    params,
  });
}

export function add(data) {
  return request({
    url: `/${API_MODULE_NAME.ATLAS}/ptMeasure`,
    method: 'post',
    data,
  });
}

export function edit(data) {
  return request({
    url: `/${API_MODULE_NAME.ATLAS}/ptMeasure`,
    method: 'put',
    data,
  });
}

export function del(ids) {
  return request({
    url: `/${API_MODULE_NAME.ATLAS}/ptMeasure`,
    method: 'delete',
    data: { ids },
  });
}

export function getGraphs(name) {
  return request({
    url: `/${API_MODULE_NAME.ATLAS}/ptMeasure/byName`,
    method: 'get',
    params: { name },
  });
}

export function getCameraList() {
  return request({
    url: `/${API_MODULE_NAME.ATLAS}/camera`,
    method: 'get',
  });
}

export function startCameraStream(cameraIndexCode) {
  return request({
    url: `/${API_MODULE_NAME.ATLAS}/camera/start`,
    method: 'get',
    params: { cameraIndexCode },
  });
}
