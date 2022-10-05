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
package org.dubhe.model.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.dataresponse.factory.DataResponseFactory;
import org.dubhe.model.service.PtModelStructureService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @description 模型结构管理 控制层
 * @date 2022-06-10
 */
@Api(tags = "模型管理：炼知模型结构管理")
@RestController
@RequestMapping("/ptModelStruct")
@RequiredArgsConstructor
public class ptModelStructureController {

	private final PtModelStructureService ptModelStructureService;

	@GetMapping
	@ApiOperation("获取炼知模型结构列表")
	public DataResponseBody<List<String>> queryAll(@RequestParam String jobType) {
		return DataResponseFactory.success(ptModelStructureService.queryStructListByType(jobType));
	}
}
