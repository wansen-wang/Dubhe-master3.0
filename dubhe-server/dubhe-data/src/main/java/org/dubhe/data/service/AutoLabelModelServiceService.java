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

package org.dubhe.data.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dubhe.data.domain.dto.AutoLabelModelServiceCreateDTO;
import org.dubhe.data.domain.dto.AutoLabelModelServiceQueryDTO;
import org.dubhe.data.domain.dto.AutoLabelModelServiceUpdateDTO;
import org.dubhe.data.domain.dto.DataK8sDeploymentCallbackCreateDTO;
import org.dubhe.data.domain.entity.AutoLabelModelService;
import org.dubhe.data.domain.entity.Dataset;
import org.dubhe.data.domain.vo.AutoLabelModelServicePodVO;
import org.dubhe.data.domain.vo.AutoLabelModelServiceVO;

import java.util.List;
import java.util.Map;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author ${author}
 * @since 2022-05-25
 */
public interface AutoLabelModelServiceService {

    /**
     * 创建模型服务
     *
     * @param autoLabelModelServiceCreateDTO
     * @return 模型服务id
     */
    Long create(AutoLabelModelServiceCreateDTO autoLabelModelServiceCreateDTO);

    /**
     * 修改模型服务
     *
     * @param autoLabelModelServiceUpdateDTO
     * @return
     */
    boolean update(AutoLabelModelServiceUpdateDTO autoLabelModelServiceUpdateDTO);

    AutoLabelModelService detail(Long modelServiceId);

    /**
     * 删除模型服务
     *
     * @param ids
     */
    void delete(List<Long> ids);

    /**
     * 模型服务查询
     *
     * @param page            分页信息
     * @param autoLabelModelServiceQueryDTO 查询条件
     * @return MapMap<String, Object> 查询出对应的数据集
     */
    Map<String, Object> list(Page<AutoLabelModelService> page, AutoLabelModelServiceQueryDTO autoLabelModelServiceQueryDTO);

    void startService(Long modelServiceId);

    void stopService(Long modelServiceId);

    List<AutoLabelModelServiceVO> runningModelList(Integer modelType);

    /**
     * k8s回调更新服务状态
     *
     * @param req   回调请求对象
     * @return boolean 返回是否回调成功
     */
    boolean deploymentCallback(DataK8sDeploymentCallbackCreateDTO req);

    List<AutoLabelModelServicePodVO> listPods(Long modelServiceId);

    /**
     * 获取模型服务详情
     * @param id
     * @return
     */
    AutoLabelModelService getOneById(Long id);

}
