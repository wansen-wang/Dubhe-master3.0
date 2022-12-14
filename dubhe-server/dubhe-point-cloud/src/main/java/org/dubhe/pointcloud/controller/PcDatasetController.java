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
 * @description ?????????????????????
 * @date 2022-04-01
 **/
@Api(tags = "?????????????????????????????????")
@RestController
@RequestMapping("/datasets/pointCloud")
public class PcDatasetController {
    @Autowired
    private PcDatasetService pcDatasetService;

    @ApiOperation("?????????????????????")
    @GetMapping
    public DataResponseBody query(PcDatasetQueryDTO pcDatasetQueryDTO) {
        return new DataResponseBody(pcDatasetService.query(pcDatasetQueryDTO));
    }

    @ApiOperation("???????????????id????????????")
    @GetMapping("/queryByIds")
    public DataResponseBody queryByIds(@RequestParam Set<Long> ids) {
        return new DataResponseBody(pcDatasetService.queryByIds(ids));
    }

    @ApiOperation("???????????????")
    @PostMapping
    public DataResponseBody create(@Validated @RequestBody PcDatasetCreateDTO datasetsCreateDTO) {
        return pcDatasetService.create(datasetsCreateDTO);
    }

    @ApiOperation("???????????????")
    @PutMapping
    public DataResponseBody update(@Validated @RequestBody PcDatasetUpdateDTO pcDatasetUpdateDTO) {
        return pcDatasetService.update(pcDatasetUpdateDTO);
    }

    @ApiOperation("???????????????")
    @DeleteMapping
    public DataResponseBody delete(@Validated @RequestBody DeleteDTO deleteDTO) {
        return pcDatasetService.delete(deleteDTO.getIds());
    }


    @ApiOperation("??????????????????")
    @PostMapping("/auto")
    public DataResponseBody autoAnnotation(@Validated @RequestBody PcDatasetAutoDTO pcDatasetAutoDTO) {
        return pcDatasetService.autoAnnotation(pcDatasetAutoDTO);
    }


    @ApiOperation("??????????????????")
    @PostMapping("/stop")
    public DataResponseBody stopAutoAnnotation(@Validated @RequestBody PcDatasetStopAutoDTO pcDatasetStopAutoDTO) {
        return pcDatasetService.stopAutoAnnotation(pcDatasetStopAutoDTO.getId());
    }

    @ApiOperation("???????????????")
    @PostMapping("/publish")
    public DataResponseBody publish(@Validated @RequestBody PcDatasetPublishDTO pcDatasetPublishDTO) {
        return pcDatasetService.publish(pcDatasetPublishDTO.getId());
    }

    @GetMapping("/logs")
    @ApiOperation("?????????????????????")
    public DataResponseBody getDatasetLog(@Validated PcDatasetLogQueryDTO pcDatasetLogQueryDTO) {
        return DataResponseFactory.success(pcDatasetService.getDatasetLog(pcDatasetLogQueryDTO));
    }


    @GetMapping("/pod/{id}")
    @ApiOperation("??????pod??????")
    public DataResponseBody getPods(@PathVariable Long id) {
        return DataResponseFactory.success(pcDatasetService.getPods(id));
    }

    @GetMapping("/details/{id}")
    @ApiOperation("???????????????????????????")
    public DataResponseBody details(@PathVariable Long id) {
        return DataResponseFactory.success(pcDatasetService.getDetails(id));
    }

    @ApiOperation(value = "????????????????????????????????????")
    @GetMapping("/list")
    public DataResponseBody getTrainList(@Validated PageQueryBase pageQueryBase) {
        return new DataResponseBody(pcDatasetService.getTrainList(pageQueryBase));
    }

}
