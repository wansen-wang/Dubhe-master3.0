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
package org.dubhe.pointcloud.task;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.pointcloud.common.Constant;
import org.dubhe.pointcloud.dao.PcDatasetFileMapper;
import org.dubhe.pointcloud.service.PcDatasetService;
import org.dubhe.recycle.domain.dto.RecycleCreateDTO;
import org.dubhe.recycle.domain.dto.RecycleDetailCreateDTO;
import org.dubhe.recycle.enums.RecycleTypeEnum;
import org.dubhe.recycle.global.AbstractGlobalRecycle;
import org.dubhe.recycle.utils.RecycleTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @description 点云文件回收
 * @date 2022-04-12
 **/
@RefreshScope
@Component(value = "pcDatasetRecycleFile")
public class PcDatasetRecycleFile extends AbstractGlobalRecycle {
    @Autowired
    private RecycleTool recycleTool;

    @Autowired
    private PcDatasetService pcDatasetService;

    @Resource
    private PcDatasetFileMapper pcDatasetFileMapper;


    @Override
    protected boolean clearDetail(RecycleDetailCreateDTO detail, RecycleCreateDTO dto) throws Exception {
        LogUtil.info(LogEnum.POINT_CLOUD, "PcDatasetRecycleFile.clear(),param:{}", JSONObject.toJSONString(detail));
        if (StrUtil.isBlank(detail.getRecycleCondition())) {
            throw new BusinessException("回收条件不能为空");
        }
        if (RecycleTypeEnum.TABLE_DATA.getCode().compareTo(detail.getRecycleType()) == 0) {
            Long pcDatasetId = Long.valueOf(detail.getRecycleCondition());
            clearDataByDatasetId(pcDatasetId, dto);
        } else {
            recycleTool.delTempInvalidResources(detail.getRecycleCondition());
        }
        return true;
    }

    /**
     * 通过数据集ID删除数据集相关的DB数据
     *
     * @param pcDatasetId 数据集ID
     * @param dto       资源回收创建对象
     */
    public void clearDataByDatasetId(Long pcDatasetId, RecycleCreateDTO dto) throws Exception {
        initOverTime();
        //删除文件数据
        pcDatasetService.deleteInfoByById(pcDatasetId);

        // 循环分批删除点云文件
        while (pcDatasetFileMapper.deleteByDatasetId(pcDatasetId, Constant.LIMIT_NUMBER) > 0) {
            if (validateOverTime()) {
                //  延迟一秒
                TimeUnit.SECONDS.sleep(1);
            } else {
                // 超时添加新任务并中止任务
                LogUtil.warn(LogEnum.POINT_CLOUD, "PcDatasetRecycleFile.clear() 超时添加新任务并停止, param:{}", JSONObject.toJSONString(dto));
                if (!Objects.isNull(dto)) {
                    addNewRecycleTask(dto);
                }
                return;
            }
        }

    }

    @Override
    protected void rollback(RecycleCreateDTO dto) {
        if (Objects.isNull(dto) || Objects.isNull(dto.getRemark())) {
            LogUtil.error(LogEnum.POINT_CLOUD, "点云数据集文件恢复异常");
            return;
        }
        pcDatasetService.recycleRollback(dto);
    }

}
