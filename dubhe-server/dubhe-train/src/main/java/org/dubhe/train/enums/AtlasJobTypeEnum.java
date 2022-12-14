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
package org.dubhe.train.enums;

import lombok.Getter;

/**
 * @description 重组训练任务类型
 * @date 2022-08-02
 */
@Getter
public enum AtlasJobTypeEnum {

	SINGLE_JOB(1, "单任务"),
	MULTI_JOB(2, "多任务");

	private Integer type;

	private String description;

	AtlasJobTypeEnum(Integer type, String description) {
		this.type = type;
		this.description = description;
	}
}
