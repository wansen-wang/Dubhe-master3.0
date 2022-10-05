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
package org.dubhe.measure.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.collect.Maps;
import lombok.RequiredArgsConstructor;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.utils.BaseUtils;
import org.dubhe.biz.base.utils.EncryptUtils;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.cloud.remotecall.config.RestTemplateHolder;
import org.dubhe.measure.async.FfmpegAtlasVideoAsync;
import org.dubhe.measure.config.AtlasUrlProperties;
import org.dubhe.measure.dao.PtAtlasCameraMapper;
import org.dubhe.measure.domain.entity.PtAtlasCamera;
import org.dubhe.measure.domain.vo.PtAtlasCameraVO;
import org.dubhe.measure.service.PtAtlasCameraService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @description 模型炼知 视频流
 * @date 2022-07-06
 */
@Service
@RequiredArgsConstructor
public class PtAtlasCameraServiceImpl implements PtAtlasCameraService {

	private final RestTemplateHolder restTemplateHolder;

	private final PtAtlasCameraMapper ptAtlasCameraMapper;

	private final FfmpegAtlasVideoAsync ffmpegAtlasVideoAsync;

	private final AtlasUrlProperties atlasUrlProperties;

	@Override
	public List<PtAtlasCameraVO> atlasCameraList() {
		List<PtAtlasCamera> atlasCameras = ptAtlasCameraMapper.selectList(new LambdaQueryWrapper<PtAtlasCamera>()
				.eq(PtAtlasCamera::getStatus, 1));
		List<PtAtlasCameraVO> atlasCameraVOS = new ArrayList<>();
		atlasCameras.forEach(atlasCamera -> {
			PtAtlasCameraVO ptAtlasCameraVO = new PtAtlasCameraVO();
			BeanUtil.copyProperties(atlasCamera, ptAtlasCameraVO);
			atlasCameraVOS.add(ptAtlasCameraVO);
		});

		return atlasCameraVOS;
	}

	@Override
	public String start(String cameraIndexCode) {
		RestTemplate restTemplate = restTemplateHolder.getRestTemplate();

		PtAtlasCamera ptAtlasCamera = ptAtlasCameraMapper.selectByCameraIndexCode(cameraIndexCode);
		if (ptAtlasCamera != null && ptAtlasCamera.getCamera_resource() == MagicNumConstant.ONE) {
			//校验视频流是否过期
			checkCameraUrl(ptAtlasCamera);
		} else {
			ptAtlasCamera = save(cameraIndexCode);
		}

		ffmpegAtlasVideoAsync.pushStream(ptAtlasCamera.getHlsUrl(), ptAtlasCamera.getId().toString());
		return atlasUrlProperties.getStream() + ptAtlasCamera.getId();
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public PtAtlasCamera save(String cameraIndexCode) {
		RestTemplate restTemplate = restTemplateHolder.getRestTemplate();
		UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(atlasUrlProperties.getVms() + "/vmsCameras");
		HttpHeaders headers = new HttpHeaders();
		Map<String, Object> param = Maps.newHashMap();
		param.put("cameraIndexCode", cameraIndexCode);

		try {
			headers.add("sign", EncryptUtils.desEncrypt(BaseUtils.createLinkString(param)));
			HttpEntity<String> httpEntity = new HttpEntity<>(headers);

			// 在此处拼接真实请求地址 "?pageNo=1&pageSize=999&productid=productid"
			if (!param.isEmpty()) {
				for (Map.Entry<String, Object> e : param.entrySet()) {
					builder.queryParam(e.getKey(), e.getValue());
				}
			}
			String reqUrl = builder.build().toUriString();
			ResponseEntity<Map> exchange = restTemplate.exchange(reqUrl, HttpMethod.GET, httpEntity, Map.class, param);

			PtAtlasCamera ptAtlasCamera = JSON.parseArray(EncryptUtils.desDecrypt(exchange.getBody().get("data").toString())).toJavaList(PtAtlasCamera.class).get(0);
			ptAtlasCameraMapper.insert(ptAtlasCamera);
			return ptAtlasCamera;
		} catch (Exception e) {
			LogUtil.error(LogEnum.MEASURE, "PtAtlasCameraService save failed，exception {}", e);
			throw new BusinessException("保存视频流信息失败");
		}
	}

	private void checkCameraUrl(PtAtlasCamera ptAtlasCamera) {
		RestTemplate restTemplate = restTemplateHolder.getRestTemplate();

		HttpHeaders headers = new HttpHeaders();
		Map<String, Object> param = Maps.newHashMap();
		param.put("cameraIndexCode", ptAtlasCamera.getCameraIndexCode());
		param.put("cameraUrl", ptAtlasCamera.getHlsUrl());

		try {
			headers.add("sign", EncryptUtils.desEncrypt(BaseUtils.createLinkString(param)));
			//将请求头部和参数合成一个请求
			HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity(param, headers);

			ResponseEntity<Map> exchange = restTemplate.exchange(atlasUrlProperties.getVms() + "/checkCameraUrl", HttpMethod.POST, requestEntity, Map.class, param);
			Map<String, String> statusMap = (Map) exchange.getBody().get("data");
			if (statusMap.containsKey("cameraUrl")) {
				ptAtlasCamera.setHlsUrl(statusMap.get("cameraUrl").replace("172.18.26.2", "10.105.10.51"));
				ptAtlasCameraMapper.updateById(ptAtlasCamera);
			}
		} catch (Exception e) {
			LogUtil.error(LogEnum.MEASURE, "PtAtlasCameraService checkCameraUrl failed，exception {}", e);
			throw new BusinessException("检测视频流是否在线失败");
		}
	}
}
