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
package org.dubhe.measure.async;

import com.google.common.collect.Maps;
import lombok.RequiredArgsConstructor;
import org.dubhe.cloud.remotecall.config.RestTemplateHolder;
import org.dubhe.measure.config.AtlasUrlProperties;
import org.dubhe.measure.constant.MeasureConstants;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * @description 异步推流
 * @date 2022-07-13
 */
@Component
@RequiredArgsConstructor
public class FfmpegAtlasVideoAsync {

	private final AtlasUrlProperties atlasUrlProperties;

	private final RestTemplateHolder restTemplateHolder;

	@Async(MeasureConstants.MEASURE_EXECUTOR)
	public void pushStream(String hlsUrl, String streamId) {

		RestTemplate restTemplate = restTemplateHolder.getRestTemplate();

		//推流
		HttpHeaders headers = new HttpHeaders();
		Map<String, Object> param = Maps.newHashMap();
		//视频流地址
		param.put("hlsUrl", hlsUrl);
		//流服务地址
		param.put("rtmpUrl", atlasUrlProperties.getRtmp());
		//流唯一标识（推流、拉流）
		param.put("uuid", streamId);

		//视频流推理服务地址
		UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(atlasUrlProperties.getVideoServe());
		if (!param.isEmpty()) {
			for (Map.Entry<String, Object> e : param.entrySet()) {
				builder.queryParam(e.getKey(), e.getValue());
			}
		}
		String reqUrl = builder.build().toUriString();

		try {
			//开始推流
			restTemplate.getForObject(reqUrl, String.class);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
 
