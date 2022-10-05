/**
 * Copyright 2020 Tianshu AI Platform. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =============================================================
 */
package org.dubhe.admin.service;

import java.util.Map;

/**
 * @description minIo服务 service
 * @date 2022-6-8
 **/
public interface MinIoService {
    /**
     * 对minio 的账户密码进行加密操作
     * @return  Map<String,String> minio账户密码加密map
     */
    Map<String,String> getMinIOInfo();
}
