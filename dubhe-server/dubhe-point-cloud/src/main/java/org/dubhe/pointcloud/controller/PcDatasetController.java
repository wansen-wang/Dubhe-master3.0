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
package org.dubhe.pointcloud.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.dataresponse.factory.DataResponseFactory;
import org.dubhe.biz.db.base.PageQueryBase;
import org.dubhe.pointcloud.domain.dto.DeleteDTO;
import org.dubhe.pointcloud.domain.dto.PcDatasetAutoDTO;
import org.dubhe.pointcloud.domain.dto.PcDatasetCreateDTO;
import org.dubhe.pointcloud.domain.dto.PcDatasetLogQueryDTO;
import org.dubhe.pointcloud.domain.dto.PcDatasetPublishDTO;
import org.dubhe.pointcloud.domain.dto.PcDatasetQueryDTO;
import org.dubhe.pointcloud.domain.dto.PcDatasetStopAutoDTO;
import org.dubhe.pointcloud.domain.dto.PcDatasetUpdateDTO;
import org.dubhe.pointcloud.service.PcDatasetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * @description 点云数据集管理
 * @date 2022-04-01
 **/
@Api(tags = "点云数据处理：数据管理")
@RestController
@RequestMapping("/datasets/pointCloud")
public class PcDatasetController {
    @Autowired
    private PcDatasetService pcDatasetService;

    @ApiOperation("数据集查询列表")
    @GetMapping
    public DataResponseBody query(PcDatasetQueryDTO pcDatasetQueryDTO) {
        return new DataResponseBody(pcDatasetService.query(pcDatasetQueryDTO));
    }

    @ApiOperation("根据数据集id查询列表")
    @GetMapping("/queryByIds")
    public DataResponseBody queryByIds(@RequestParam Set<Long> ids) {
        return new DataResponseBody(pcDatasetService.queryByIds(ids));
    }

    @ApiOperation("数据集创建")
    @PostMapping
    public DataResponseBody create(@Validated @RequestBody PcDatasetCreateDTO datasetsCreateDTO) {
        return pcDatasetService.create(datasetsCreateDTO);
    }

    @ApiOperation("更新数据集")
    @PutMapping
    public DataResponseBody update(@Validated @RequestBody PcDatasetUpdateDTO pcDatasetUpdateDTO) {
        return pcDatasetService.update(pcDatasetUpdateDTO);
    }

    @ApiOperation("删除数据集")
    @DeleteMapping
    public DataResponseBody delete(@Validated @RequestBody DeleteDTO deleteDTO) {
        return pcDatasetService.delete(deleteDTO.getIds());
    }


    @ApiOperation("开启自动标注")
    @PostMapping("/auto")
    public DataResponseBody autoAnnotation(@Validated @RequestBody PcDatasetAutoDTO pcDatasetAutoDTO) {
        return pcDatasetService.autoAnnotation(pcDatasetAutoDTO);
    }


    @ApiOperation("停止自动标注")
    @PostMapping("/stop")
    public DataResponseBody stopAutoAnnotation(@Validated @RequestBody PcDatasetStopAutoDTO pcDatasetStopAutoDTO) {
        return pcDatasetService.stopAutoAnnotation(pcDatasetStopAutoDTO.getId());
    }

    @ApiOperation("数据集发布")
    @PostMapping("/publish")
    public DataResponseBody publish(@Validated @RequestBody PcDatasetPublishDTO pcDatasetPublishDTO) {
        return pcDatasetService.publish(pcDatasetPublishDTO.getId());
    }

    @GetMapping("/logs")
    @ApiOperation("数据集日志查询")
    public DataResponseBody getDatasetLog(@Validated PcDatasetLogQueryDTO pcDatasetLogQueryDTO) {
        return DataResponseFactory.success(pcDatasetService.getDatasetLog(pcDatasetLogQueryDTO));
    }


    @GetMapping("/pod/{id}")
    @ApiOperation("获取pod节点")
    public DataResponseBody getPods(@PathVariable Long id) {
        return DataResponseFactory.success(pcDatasetService.getPods(id));
    }

    @GetMapping("/details/{id}")
    @ApiOperation("获取数据集详情信息")
    public DataResponseBody details(@PathVariable Long id) {
        return DataResponseFactory.success(pcDatasetService.getDetails(id));
    }

    @ApiOperation(value = "获取用于训练的数据集列表")
    @GetMapping("/list")
    public DataResponseBody getTrainList(@Validated PageQueryBase pageQueryBase) {
        return new DataResponseBody(pcDatasetService.getTrainList(pageQueryBase));
    }

}
