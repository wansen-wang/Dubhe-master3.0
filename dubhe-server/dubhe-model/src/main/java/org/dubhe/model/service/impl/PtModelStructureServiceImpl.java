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
package org.dubhe.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dubhe.biz.base.context.UserContext;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.service.UserContextService;
import org.dubhe.model.dao.PtModelStructureMapper;
import org.dubhe.model.domain.entity.PtModelStructure;
import org.dubhe.model.service.PtModelStructureService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @description 模型结构管理 实现层
 * @date 2022-06-10
 */
@Service
@RequiredArgsConstructor
public class PtModelStructureServiceImpl implements PtModelStructureService {

	private final PtModelStructureMapper ptModelStructureMapper;
	private final UserContextService userContextService;

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void create(String structName, Integer jobType) {

		//获取用户信息
		UserContext user = userContextService.getCurUser();
		//判断结构名称是否存在
		Integer count = ptModelStructureMapper.selectCount(new LambdaQueryWrapper<PtModelStructure>().eq(PtModelStructure::getStructName, structName));
		if (count >= 1) {
			return;
		}

		PtModelStructure modelStructure = new PtModelStructure();
		modelStructure.setStructName(structName)
				.setJobType(jobType)
				.setCreateUserId(user.getId());

		ptModelStructureMapper.insert(modelStructure);
	}
	
	@Override
	public List<String> queryStructListByType(String jobType) {
		return ptModelStructureMapper.queryStructListByType(jobType);
	}
}
