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
import org.dubhe.pointcloud.domain.dto.DeleteDTO;
import org.dubhe.pointcloud.domain.dto.FileDifficultCasePublishDTO;
import org.dubhe.pointcloud.domain.dto.FileDoneInputDTO;
import org.dubhe.pointcloud.domain.dto.FileMarkDifficultDTO;
import org.dubhe.pointcloud.domain.vo.FileQueryInputVO;
import org.dubhe.pointcloud.domain.vo.MarkSaveInputVO;
import org.dubhe.pointcloud.domain.vo.UploadSaveInputVO;
import org.dubhe.pointcloud.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * @description 文件处理控制类
 * @date 2022-03-31
 **/

@Api(tags = "点云数据处理：文件相关接口")
@RestController
@RequestMapping("/files")
public class FileController {
    @Autowired
    private FileService fileService;

    @ApiOperation("点云文件列表")
    @GetMapping
    public DataResponseBody query(FileQueryInputVO fileQueryInputVO) {
        return new DataResponseBody(fileService.queryFileList(fileQueryInputVO));
    }

    @ApiOperation("点云文件解析")
    @GetMapping("/info")
    public DataResponseBody info(@Valid @NotNull @RequestParam("id") Long id, @Valid @NotNull @RequestParam("datasetId") Long datasetId) {
        return new DataResponseBody(fileService.info(id, datasetId));
    }

    @ApiOperation("标注信息保存")
    @PostMapping("/save")
    public DataResponseBody save(@RequestBody @Valid MarkSaveInputVO markSaveInputVO) {
        fileService.markSave(markSaveInputVO);
        return new DataResponseBody();
    }

    @ApiOperation("标注完成")
    @PostMapping("/done")
    public DataResponseBody done(@RequestBody @Valid FileDoneInputDTO fileDoneInputDTO) {
        return fileService.done(fileDoneInputDTO);
    }

    @ApiOperation("点云文件难例标记")
    @PostMapping("/difficult")
    public DataResponseBody mark(@Validated @RequestBody FileMarkDifficultDTO fileMarkDifficultDTO) {
        return fileService.mark(fileMarkDifficultDTO);
    }

    @ApiOperation("点云文件难例发布")
    @PostMapping("/difficult/publish")
    public DataResponseBody publishDifficultCase(@Validated @RequestBody FileDifficultCasePublishDTO fileDifficultCasePublishDTO) {
        return fileService.publishDifficultCase(fileDifficultCasePublishDTO);
    }

    @ApiOperation("点云文件删除")
    @DeleteMapping
    public DataResponseBody delete(@Validated @RequestBody DeleteDTO deleteDTO) {
        return fileService.delete(deleteDTO.getIds());
    }

    @ApiOperation("点云文件上传验证")
    @GetMapping("/upload/valid")
    public DataResponseBody valid(@Valid @NotNull Long id) {
        return new DataResponseBody(fileService.valid(id));
    }

    @ApiOperation("点云文件上传保存")
    @PostMapping("/upload/save")
    public DataResponseBody uploadSave(@RequestBody UploadSaveInputVO uploadSaveInputVO) {
        fileService.uploadSave(uploadSaveInputVO.getList(), uploadSaveInputVO.getLabelFileMap());
        return new DataResponseBody();
    }
}
