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
package org.dubhe.measure.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.measure.domain.dto.PtAtlasCameraDTO;
import org.dubhe.measure.service.PtAtlasCameraService;
import org.springframework.http.HttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description
 * @date 2022-07-06
 */
@Api(tags = "炼知模型：视频流管理")
@RequiredArgsConstructor
@RestController
@RequestMapping("/camera")
public class PtAtlasCameraController {

	private final PtAtlasCameraService ptAtlasCameraService;

	@GetMapping
	public DataResponseBody atlasCameraList() {
		return new DataResponseBody(ptAtlasCameraService.atlasCameraList());
	}

	@ApiOperation("开始推理视频流")
	@GetMapping("/start")
	public DataResponseBody start(@RequestParam String cameraIndexCode) {
		return new DataResponseBody(ptAtlasCameraService.start(cameraIndexCode));
	}

	@GetMapping("/save")
	public DataResponseBody save(@RequestParam String cameraIndexCode) {
		return new DataResponseBody(ptAtlasCameraService.save(cameraIndexCode));
	}

}
