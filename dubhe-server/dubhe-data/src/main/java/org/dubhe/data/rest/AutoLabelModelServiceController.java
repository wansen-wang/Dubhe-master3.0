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

package org.dubhe.data.rest;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.dubhe.biz.base.constant.Permissions;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.data.constant.Constant;
import org.dubhe.data.domain.dto.AutoLabelModelServiceCreateDTO;
import org.dubhe.data.domain.dto.AutoLabelModelServiceDeleteDTO;
import org.dubhe.data.domain.dto.AutoLabelModelServiceQueryDTO;
import org.dubhe.data.domain.dto.AutoLabelModelServiceUpdateDTO;
import org.dubhe.data.service.AutoLabelModelServiceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author ${author}
 * @since 2022-05-25
 */
@Api(tags = "数据处理：模型服务管理")
@RestController
@RequestMapping(Constant.MODULE_URL_PREFIX + "/datasets/label/service")
public class AutoLabelModelServiceController {

    @Resource
    private AutoLabelModelServiceService autoLabelModelServiceService;

    @ApiOperation("创建模型服务")
    @PostMapping
    @PreAuthorize(Permissions.DATA)
    public DataResponseBody create(@Validated @Valid @RequestBody AutoLabelModelServiceCreateDTO autoLabelModelServiceCreateDTO) {
        return new DataResponseBody(autoLabelModelServiceService.create(autoLabelModelServiceCreateDTO));
    }

    @ApiOperation("修改模型服务")
    @PutMapping
    @PreAuthorize(Permissions.DATA)
    public DataResponseBody update(@Validated @Valid @RequestBody AutoLabelModelServiceUpdateDTO autoLabelModelServiceUpdateDTO) {
        return new DataResponseBody(autoLabelModelServiceService.update(autoLabelModelServiceUpdateDTO));
    }

    @ApiOperation("模型服务详情")
    @GetMapping("/{modelServiceId}")
    public DataResponseBody detail(@PathVariable Long modelServiceId) {
        return new DataResponseBody(autoLabelModelServiceService.detail(modelServiceId));
    }

    @ApiOperation("删除模型服务")
    @DeleteMapping
    @PreAuthorize(Permissions.DATA)
    public DataResponseBody delete(@RequestBody AutoLabelModelServiceDeleteDTO autoLabelModelServiceDeleteDTO) {
        autoLabelModelServiceService.delete(autoLabelModelServiceDeleteDTO.getIds());
        return new DataResponseBody();
    }

    @ApiOperation("模型服务列表")
    @GetMapping("list")
    @PreAuthorize(Permissions.DATA)
    public DataResponseBody list(Page page, AutoLabelModelServiceQueryDTO autoLabelModelServiceQueryDTO) {
        return new DataResponseBody(autoLabelModelServiceService.list(page,autoLabelModelServiceQueryDTO));
    }

    @ApiOperation("启动模型服务")
    @PutMapping("start/{id}")
    @PreAuthorize(Permissions.DATA)
    public DataResponseBody start(@PathVariable(name = "id") Long modelServiceId){
        autoLabelModelServiceService.startService(modelServiceId);
        return new DataResponseBody();
    }

    @ApiOperation("停止模型服务")
    @PutMapping("stop/{id}")
    @PreAuthorize(Permissions.DATA)
    public DataResponseBody stop(@PathVariable(name = "id") Long modelServiceId){
        autoLabelModelServiceService.stopService(modelServiceId);
        return new DataResponseBody();
    }

    @ApiOperation("运行中模型服务列表")
    @GetMapping("running/list")
    @PreAuthorize(Permissions.DATA)
    public DataResponseBody runningList(@RequestParam(value = "modelType") Integer modelType){
        return new DataResponseBody(autoLabelModelServiceService.runningModelList(modelType));
    }

    @ApiOperation("模型服务容器列表")
    @GetMapping("pods/{id}")
    @PreAuthorize(Permissions.DATA)
    public DataResponseBody pods(@PathVariable(name = "id") Long modelServiceId){
        return new DataResponseBody( autoLabelModelServiceService.listPods(modelServiceId));
    }
}

