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

package org.dubhe.image.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.dubhe.biz.base.constant.Permissions;
import org.dubhe.biz.base.dto.PtImageIdDTO;
import org.dubhe.biz.base.dto.PtImageIdsDTO;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.base.vo.PtImageVO;
import org.dubhe.image.domain.dto.PtImageDeleteDTO;
import org.dubhe.image.domain.dto.PtImageQueryDTO;
import org.dubhe.image.domain.dto.PtImageQueryImageDTO;
import org.dubhe.image.domain.dto.PtImageQueryNameDTO;
import org.dubhe.image.domain.dto.PtImageQueryUrlDTO;
import org.dubhe.image.domain.dto.PtImageSaveDTO;
import org.dubhe.image.domain.dto.PtImageUpdateDTO;
import org.dubhe.image.service.PtImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @description ??????
 * @date 2020-04-27
 */
@Api(tags = "?????????????????????")
@RestController
@RequestMapping("/ptImage")
public class PtImageController {

    @Autowired
    private PtImageService ptImageService;

    @GetMapping("/list")
    @ApiOperation("????????????")
    @PreAuthorize(Permissions.IMAGE)
    public DataResponseBody getImage(PtImageQueryDTO ptImageQueryDTO) {
        return new DataResponseBody(ptImageService.getImage(ptImageQueryDTO));
    }

    @ApiOperation("??????ImageName????????????")
    @GetMapping
    public DataResponseBody getTagsByImageName(@Validated PtImageQueryImageDTO ptImageQueryImageDTO) {
        return new DataResponseBody(ptImageService.searchImages(ptImageQueryImageDTO));
    }

    @PostMapping
    @ApiOperation("??????????????????")
    @PreAuthorize(Permissions.IMAGE_SAVE)
    public DataResponseBody saveImageInfo(@Validated @RequestBody PtImageSaveDTO ptImageSaveDTO) {
        ptImageService.saveImageInfo(ptImageSaveDTO);
        return new DataResponseBody();
    }

    @DeleteMapping
    @ApiOperation("????????????")
    @PreAuthorize(Permissions.IMAGE_DELETE)
    public DataResponseBody deleteTrainImage(@RequestBody PtImageDeleteDTO ptImageDeleteDTO) {
        ptImageService.deleteTrainImage(ptImageDeleteDTO);
        return new DataResponseBody();
    }

    @PutMapping
    @ApiOperation("??????????????????")
    @PreAuthorize(Permissions.IMAGE_EDIT)
    public DataResponseBody updateTrainImage(@Validated @RequestBody PtImageUpdateDTO ptImageUpdateDTO) {
        ptImageService.updateTrainImage(ptImageUpdateDTO);
        return new DataResponseBody();
    }

    @GetMapping("/imageNameList")
    @ApiOperation("????????????????????????")
    public DataResponseBody getImageNameList(@Validated PtImageQueryNameDTO ptImageQueryNameDTO) {
        return new DataResponseBody(ptImageService.getImageNameList(ptImageQueryNameDTO));
    }

    @GetMapping("/imageDefault")
    @ApiOperation("??????Notebook????????????")
    public DataResponseBody getImageDefault() {
        return new DataResponseBody(ptImageService.getImageDefault());
    }

    @PutMapping("/imageDefault")
    @ApiOperation("??????Notebook????????????")
    @PreAuthorize(Permissions.IMAGE_EDIT_DEFAULT)
    public DataResponseBody updateImageDefault(@RequestParam Long id) {
        ptImageService.updImageDefault(id);
        return new DataResponseBody();
    }

    @GetMapping("/imageUrl")
    @ApiOperation("????????????url")
    public DataResponseBody<String> getImageUrl(@Validated PtImageQueryUrlDTO ptImageQueryUrlDTO) {
        return new DataResponseBody(ptImageService.getImageUrl(ptImageQueryUrlDTO));
    }

    @GetMapping("/terminalImageList")
    @ApiOperation("????????????????????????")
    public DataResponseBody getTerminalImageList() {
        return new DataResponseBody(ptImageService.getTerminalImageList());
    }

    @GetMapping("/byId")
    @ApiOperation("???????????????id??????????????????")
    public DataResponseBody<PtImageVO> getById(@Validated PtImageIdDTO ptImageIdDTO) {
        return new DataResponseBody(ptImageService.getById(ptImageIdDTO.getId()));
    }

    @GetMapping("/listByIds")
    @ApiOperation("???????????????id??????????????????")
    public DataResponseBody<List<PtImageVO>> listByIds(@Validated PtImageIdsDTO ptImageIdsDTO) {
        return new DataResponseBody(ptImageService.listByIds(ptImageIdsDTO.getIds()));
    }

}
