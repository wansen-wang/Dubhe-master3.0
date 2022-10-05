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
package org.dubhe.measure.service;

import org.dubhe.measure.domain.entity.PtAtlasCamera;
import org.dubhe.measure.domain.vo.PtAtlasCameraVO;

import java.util.List;

/**
 * @description vms视频流
 * @date 2022-07-06
 */
public interface PtAtlasCameraService {

	/**
	 * 获取视频流列表
	 *
	 * @return {@code List<PtAtlasCameraVO>}
	 */
	List<PtAtlasCameraVO> atlasCameraList();

	/**
	 * 开始推流并返回拉流地址
	 *
	 * @param cameraIndexCode
	 * @return {@code String}
	 */
	String start(String cameraIndexCode);

	/**
	 * 保存视频流
	 *
	 * @param cameraIndexCode
	 * @return {@code PtAtlasCamera}
	 */
	PtAtlasCamera save(String cameraIndexCode);
}
