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

package org.dubhe.image.service;

import org.dubhe.biz.base.vo.PtImageVO;
import org.dubhe.image.domain.dto.PtImageDeleteDTO;
import org.dubhe.image.domain.dto.PtImageQueryDTO;
import org.dubhe.image.domain.dto.PtImageQueryImageDTO;
import org.dubhe.image.domain.dto.PtImageQueryNameDTO;
import org.dubhe.image.domain.dto.PtImageQueryUrlDTO;
import org.dubhe.image.domain.dto.PtImageSaveDTO;
import org.dubhe.image.domain.dto.PtImageUpdateDTO;
import org.dubhe.image.domain.entity.PtImage;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @description 镜像服务service
 * @date 2020-06-22
 */
public interface PtImageService {

    /**
     * 查询镜像
     *
     * @param ptImageQueryDTO 查询条件
     * @return Map<String, Object> 镜像列表分页信息
     **/
    Map<String, Object> getImage(PtImageQueryDTO ptImageQueryDTO);


    /**
     * 保存镜像信息
     *
     * @param ptImageSaveDTO 镜像信息DTO
     */
    void saveImageInfo(PtImageSaveDTO ptImageSaveDTO);


    /**
     * 获取镜像信息
     *
     * @param ptImageQueryImageDTO 查询条件
     * @return List<String>  镜像集合
     */
    List<PtImage> searchImages(PtImageQueryImageDTO ptImageQueryImageDTO);

    /**
     * 删除镜像
     *
     * @param imageDeleteDTO 删除镜像条件参数
     */
    void deleteTrainImage(PtImageDeleteDTO imageDeleteDTO);

    /**
     * 修改镜像信息
     *
     * @param imageUpdateDTO 修改的镜像信息
     */
    void updateTrainImage(PtImageUpdateDTO imageUpdateDTO);


    /**
     * 获取镜像名称列表
     * @param ptImageQueryNameDTO 获取镜像名称列表查询条件
     * @return Set<String> 镜像列表
     */
    Set<String> getImageNameList(PtImageQueryNameDTO ptImageQueryNameDTO);

    /**
     * 查询Notebook默认镜像
     */
    List<PtImage> getImageDefault();
    /**
     * 设置Notebook默认镜像
     *
     * @param id 镜像id
     */
    void updImageDefault(Long id);

    /**
     * 获取镜像URL
     *
     * @param imageQueryUrlDTO 查询镜像路径DTO
     * @return String 镜像url
     */
    String getImageUrl(PtImageQueryUrlDTO imageQueryUrlDTO);

    /**
     * 获取终端镜像列表
     *
     * @return List<PtImage>
     */
    List<PtImage> getTerminalImageList();

    PtImageVO getById(Long id);

    List<PtImageVO> listByIds(List<Long> ids);


}
