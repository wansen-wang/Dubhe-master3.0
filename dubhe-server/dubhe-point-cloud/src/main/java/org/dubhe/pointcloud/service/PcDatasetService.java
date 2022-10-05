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
package org.dubhe.pointcloud.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.db.base.PageQueryBase;
import org.dubhe.k8s.domain.vo.PodVO;
import org.dubhe.pointcloud.domain.dto.AnnotationK8sPodCallbackCreateDTO;
import org.dubhe.pointcloud.domain.dto.DatasetDetailDTO;
import org.dubhe.pointcloud.domain.dto.PcDatasetAutoDTO;
import org.dubhe.pointcloud.domain.dto.PcDatasetCreateDTO;
import org.dubhe.pointcloud.domain.dto.PcDatasetLogQueryDTO;
import org.dubhe.pointcloud.domain.dto.PcDatasetQueryDTO;
import org.dubhe.pointcloud.domain.dto.PcDatasetUpdateDTO;
import org.dubhe.pointcloud.domain.entity.PcDataset;
import org.dubhe.pointcloud.domain.vo.DatasetLogQueryVO;
import org.dubhe.pointcloud.domain.vo.DatasetQueryVO;
import org.dubhe.recycle.domain.dto.RecycleCreateDTO;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @description 数据集服务
 * @date 2022-04-01
 **/
public interface PcDatasetService {
    /**
     * 数据集查询列表
     *
     * @param pcDatasetQueryDTO
     * @return
     */
    Map<String, Object> query(PcDatasetQueryDTO pcDatasetQueryDTO);

    /**
     * 创建数据集
     *
     * @param pcDatasetCreateDTO
     * @return
     */
    DataResponseBody create(PcDatasetCreateDTO pcDatasetCreateDTO);

    /**
     * 修改数据集
     *
     * @param pcDatasetUpdateDTO
     * @return
     */
    DataResponseBody update(PcDatasetUpdateDTO pcDatasetUpdateDTO);

    /**
     * 删除数据集
     *
     * @param ids
     * @return
     */
    DataResponseBody delete(Set<Long> ids);

    /**
     * 数据集发布
     *
     * @param id
     * @return
     */
    DataResponseBody publish(Long id);

    /**
     * 根据id 查询数据集
     *
     * @param datasetId
     * @return
     */
    PcDataset selectById(Long datasetId);

    /**
     * 更新数据集
     *
     * @param lambdaUpdateWrapper 更新条件
     * @return
     */
    int updatePcDataset(LambdaUpdateWrapper<PcDataset> lambdaUpdateWrapper);

    /**
     * k8s回调pod自动标注状态
     *
     * @param times 回调请求次数
     * @param req   回调请求对象
     * @return boolean 返回回调是否成功
     */
    boolean annotationPodCallback(int times, AnnotationK8sPodCallbackCreateDTO req);

    /**
     * 开启自动标注
     *
     * @param pcDatasetAutoDTO
     * @return
     */
    DataResponseBody autoAnnotation(PcDatasetAutoDTO pcDatasetAutoDTO);

    /**
     * 停止自定标注
     *
     * @param id
     * @return
     */
    DataResponseBody stopAutoAnnotation(Long id);


    /**
     * 根据id集合查询数据
     *
     * @param ids
     * @return
     */
    List<DatasetQueryVO> queryByIds(Set<Long> ids);


    /**
     * 数据还原
     * @param dto
     */
    void recycleRollback(RecycleCreateDTO dto);

    /**
     * 根据数据集ID删除数据信息
     * @param pcDatasetId
     */
    void deleteInfoByById(Long pcDatasetId);

    /**
     * 本地日志查询接口
     * @param pcDatasetLogQueryDTO
     * @return
     */
    DatasetLogQueryVO getDatasetLog(PcDatasetLogQueryDTO pcDatasetLogQueryDTO);

    /**
     * 获取pod name接口
     * @param id
     * @return
     */
    List<PodVO> getPods(Long id);

    /**
     * 获取数据集详情信息
     * @param id
     * @return
     */
    DatasetDetailDTO getDetails(Long id);

    /**
     * 获取训练模块用的数据集信息
     * @return List<PcDatasetTrainVO>
     */
    Map<String, Object> getTrainList(PageQueryBase pageQueryBase);
}
