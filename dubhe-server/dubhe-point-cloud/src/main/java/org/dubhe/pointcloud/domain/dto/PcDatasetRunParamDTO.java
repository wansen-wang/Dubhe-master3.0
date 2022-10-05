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
package org.dubhe.pointcloud.domain.dto;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @description 点云运算DTO
 * @date 2022-04-11
 **/
@Data
@Accessors(chain = true)
public class PcDatasetRunParamDTO {

    /**
     * 数据集id
     */
    private Long datasetId;
    /**
     * 镜像路径
     */
    private String image;
    /**
     *
     */
    private String taskIdentify;
    /**
     * gpuNum
     */
    private Integer gpuNum;
    /**
     * cpuNum
     */
    private Integer cpuNum;
    /**
     * 内存
     */
    private Integer memNum;
    /**
     * 标注命令
     */
    private String command;

    /**
     * 节点类型(0为CPU，1为GPU)
     */
    private Integer resourcesPoolType;

    /**
     * 节点规格
     */
    private String resourcesPoolSpecs;

    /**
     * 规格信息
     */
    private String poolSpecsInfo;
    /**
     * 节点个数
     */
    private Integer resourcesPoolNode;

    private String resourceInfo;
    /**
     * 模型路径
     */
    private String modelDir;
    /**
     * 数据集路径
     */
    private String datasetDir;
    /**
     * 输出结果集路径
     */
    private String resultsDir;
    /**
     * 算法路径（自动标注脚本）
     */
    private String algorithmDir;
    /**
     * 创建用户
     */
    private Long createUserId;


    /**
     * @return 每个节点的GPU数量
     */
    public Integer getGpuNum() {
        return JSONObject.parseObject(poolSpecsInfo.replace("\\", "")).getInteger("gpuNum");
    }

    /**
     * @return cpu数量
     */
    public Integer getCpuNum() {
        return JSONObject.parseObject(poolSpecsInfo.replace("\\", "")).getInteger("cpuNum");
    }

    /**
     * @return cpu数量
     */
    public Integer getMemNum() {
        return JSONObject.parseObject(poolSpecsInfo.replace("\\", "")).getInteger("memNum");
    }

}
