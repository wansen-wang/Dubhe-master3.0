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
package org.dubhe.model.service;

import java.util.List;

/**
 * @description 模型结构管理 接口层
 * @date 2020-04-02
 */
public interface PtModelStructureService {


	/**
	 * 新增模型结构
	 *
	 * @param structName 模型结构名
	 * @param jobType 模型重组任务类型（1：单任务，2多任务）
	 */
	void create(String structName, Integer jobType);

	/**
	 * 根据重组任务类型筛选模型结构列表
	 *
	 * @param jobType 重组任务类型
	 * @return {@code List<String>} 重组模型结构列表
	 */
	List<String> queryStructListByType(String jobType);
}
