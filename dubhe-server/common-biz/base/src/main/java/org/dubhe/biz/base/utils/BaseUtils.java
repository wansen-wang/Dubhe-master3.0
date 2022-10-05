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
package org.dubhe.biz.base.utils;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import javax.servlet.ServletRequest;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * @description
 * @date 2022-07-06
 */
public class BaseUtils {
	/**
	 * 获取request 中的参数，以map形式返回
	 * @param request
	 * @return
	 */
	public static Map<String, Object> getParamMap(ServletRequest request) {
		//Assert.notNull(request,"参数不能为空");
		Map<String, Object> map = Maps.newHashMap();
		Enumeration<String> en = request.getParameterNames();
		while (en.hasMoreElements()) {
			String name = en.nextElement();
			String[] values = request.getParameterValues(name);
			if (values == null || values.length == 0) {
				continue;
			}
			String value = values[0];
			if (value != null) {
				map.put(name, value);
			}
		}
		return map;
	}

	/**
	 * 把数组所有元素，按字母排序，然后按照“参数=参数值”的模式用“&”字符拼接成字符串
	 *
	 * @param param
	 *            需要签名的参数
	 * @return 签名的字符串
	 */
	public static String createLinkString(Map<String, Object> param) {
		Map<String, Object> params = Maps.newHashMap();
		params.putAll(param);
		params.put("dev-name", "VMS");
		params.put("project", "TIANSHU");
		List<String> keys = Lists.newArrayList(params.keySet().iterator());
		Collections.sort(keys);
		StringBuilder signStr = new StringBuilder();
		for (String key : keys) {
			if (!StringUtils.isNotEmpty(params.get(key).toString())) {
				continue;
			}
			signStr.append(key).append("=").append(params.get(key)).append("&");
		}
		return signStr.deleteCharAt(signStr.length() - 1).toString();
	}
}
