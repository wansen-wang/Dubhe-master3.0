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
package org.dubhe.measure.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.base.Joiner;
import org.dubhe.biz.base.constant.StringConstant;
import org.dubhe.biz.base.context.UserContext;
import org.dubhe.biz.base.enums.DatasetTypeEnum;
import org.dubhe.biz.base.enums.MeasureStateEnum;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.service.UserContextService;
import org.dubhe.biz.base.utils.ReflectionUtils;
import org.dubhe.biz.base.utils.StringUtils;
import org.dubhe.biz.db.utils.PageUtil;
import org.dubhe.biz.file.api.FileStoreApi;
import org.dubhe.biz.file.enums.BizPathEnum;
import org.dubhe.biz.file.utils.IOUtil;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.permission.annotation.DataPermissionMethod;
import org.dubhe.biz.permission.base.BaseService;
import org.dubhe.k8s.utils.K8sNameTool;
import org.dubhe.measure.async.GenerateMeasureFileAsync;
import org.dubhe.measure.dao.PtMeasureMapper;
import org.dubhe.measure.domain.dto.PtMeasureCreateDTO;
import org.dubhe.measure.domain.dto.PtMeasureDeleteDTO;
import org.dubhe.measure.domain.dto.PtMeasureQueryDTO;
import org.dubhe.measure.domain.dto.PtMeasureUpdateDTO;
import org.dubhe.measure.domain.entity.PtMeasure;
import org.dubhe.measure.domain.vo.PtMeasureQueryVO;
import org.dubhe.measure.service.PtMeasureService;
import org.dubhe.recycle.config.RecycleConfig;
import org.dubhe.recycle.domain.dto.RecycleCreateDTO;
import org.dubhe.recycle.domain.dto.RecycleDetailCreateDTO;
import org.dubhe.recycle.enums.RecycleModuleEnum;
import org.dubhe.recycle.enums.RecycleResourceEnum;
import org.dubhe.recycle.enums.RecycleTypeEnum;
import org.dubhe.recycle.service.RecycleService;
import org.dubhe.recycle.utils.RecycleTool;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @description ?????????????????????
 * @date 2020-11-16
 */
@Service
public class PtMeasureServiceImpl implements PtMeasureService {


    @Autowired
    private PtMeasureMapper ptMeasureMapper;

    @Resource(name = "hostFileStoreApiImpl")
    private FileStoreApi fileStoreApi;

    @Autowired
    private RecycleService recycleService;

    @Autowired
    private RecycleConfig recycleConfig;

    @Autowired
    private GenerateMeasureFileAsync measureFileAsync;

    @Autowired
    private K8sNameTool k8sNameTool;

    @Autowired
    private UserContextService userContextService;

    public final static List<String> FIELD_NAMES;

    static {
        FIELD_NAMES = ReflectionUtils.getFieldNames(PtMeasureQueryDTO.class);
    }


    /**
     * ??????????????????
     *
     * @param ptMeasureQueryDTO ????????????
     * @return Map<String, Object> ????????????????????????
     */
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    @Override
    public Map<String, Object> getMeasure(PtMeasureQueryDTO ptMeasureQueryDTO) {

        //??????????????????????????????
        UserContext currentUser = userContextService.getCurUser();
        Page page = ptMeasureQueryDTO.toPage();

        QueryWrapper<PtMeasure> query = new QueryWrapper<>();

        if (!BaseService.isAdmin(currentUser)) {
            query.eq("create_user_id", currentUser.getId());
        }

        if (ptMeasureQueryDTO.getMeasureStatus() != null) {
            query.eq("measure_status", ptMeasureQueryDTO.getMeasureStatus());
        }

        if (StrUtil.isNotEmpty(ptMeasureQueryDTO.getNameOrId())) {
            query.and(x -> x.eq("id", ptMeasureQueryDTO.getNameOrId()).or().like("name", ptMeasureQueryDTO.getNameOrId()));
        }

        //??????
        IPage<PtMeasure> ptMeasures;
        try {
            if (StrUtil.isNotEmpty(ptMeasureQueryDTO.getSort()) && FIELD_NAMES.contains(ptMeasureQueryDTO.getSort())) {
                if (StringConstant.SORT_ASC.equalsIgnoreCase(ptMeasureQueryDTO.getOrder())) {
                    query.orderByAsc(StringUtils.humpToLine(ptMeasureQueryDTO.getSort()));
                } else {
                    query.orderByDesc(StringUtils.humpToLine(ptMeasureQueryDTO.getSort()));
                }
            } else {
                query.orderByDesc(StringConstant.ID);
            }
            ptMeasures = ptMeasureMapper.selectPage(page, query);
        } catch (Exception e) {
            LogUtil.error(LogEnum.MEASURE, "User {} query measure list failed exception {}", currentUser.getId(), e);
            throw new BusinessException("??????????????????????????????");
        }

        List<PtMeasureQueryVO> ptMeasureQueryResult = ptMeasures.getRecords().stream().map(x -> {
            PtMeasureQueryVO ptMeasureQueryVO = new PtMeasureQueryVO();
            BeanUtils.copyProperties(x, ptMeasureQueryVO);
            if (StrUtil.isNotEmpty(x.getModelUrls())) {
                ptMeasureQueryVO.setModelUrls(StrUtil.split(x.getModelUrls(), ','));
            }
            return ptMeasureQueryVO;
        }).collect(Collectors.toList());
        return PageUtil.toPage(page, ptMeasureQueryResult);
    }

    /**
     * ????????????
     *
     * @param ptMeasureCreateDTO ??????????????????DTO
     */
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void createMeasure(PtMeasureCreateDTO ptMeasureCreateDTO) {
        //??????????????????????????????
        UserContext currentUser = userContextService.getCurUser();

        //???????????????????????????????????????
        List<PtMeasure> ptMeasures = ptMeasureMapper.selectList(new LambdaQueryWrapper<PtMeasure>()
                .eq(PtMeasure::getName, ptMeasureCreateDTO.getName())
                .eq(PtMeasure::getCreateUserId, currentUser.getId())
        );
        if (CollUtil.isNotEmpty(ptMeasures)) {
            throw new BusinessException("?????????????????????!");
        }

        PtMeasure ptMeasure = new PtMeasure();
        BeanUtils.copyProperties(ptMeasureCreateDTO, ptMeasure);
        //?????????????????????????????????
        String measurePath = k8sNameTool.getPath(BizPathEnum.MEASURE, currentUser.getId());

        ptMeasure
                .setUrl(measurePath)
                .setDatasetId(ptMeasureCreateDTO.getDatasetId())
                .setDatasetUrl(ptMeasureCreateDTO.getDatasetUrl())
                .setModelUrls(Joiner.on(",").join(ptMeasureCreateDTO.getModelUrls()))
                .setCreateUserId(currentUser.getId());
        try {
            ptMeasureMapper.insert(ptMeasure);
        } catch (Exception e) {
            LogUtil.error(LogEnum.MEASURE, "pt_measure table insert operation failed,exception {}", e);
            throw new BusinessException("????????????");
        }
        //????????????????????????
        measureFileAsync.generateMeasureFile(ptMeasure, measurePath);
    }

    /**
     * ????????????
     *
     * @param ptMeasureUpdateDTO ??????????????????DTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMeasure(PtMeasureUpdateDTO ptMeasureUpdateDTO) {
        //??????????????????????????????
        UserContext currentUser = userContextService.getCurUser();
        PtMeasure measure = ptMeasureMapper.selectById(ptMeasureUpdateDTO.getId());
        if (measure == null) {
            throw new BusinessException("???????????????!");
        }
        if (MeasureStateEnum.MAKING.equals(measure.getMeasureStatus())) {
            throw new BusinessException("????????????????????????????????????!");
        }

        //???????????????????????????????????????
        List<PtMeasure> ptMeasures = ptMeasureMapper.selectList(new LambdaQueryWrapper<PtMeasure>()
                .eq(PtMeasure::getName, ptMeasureUpdateDTO.getName())
                .eq(PtMeasure::getCreateUserId, currentUser.getId())
        );
        if (CollUtil.isNotEmpty(ptMeasures) && !ptMeasures.get(0).getId().equals(measure.getId())) {
            throw new BusinessException("?????????????????????!");
        }

        BeanUtils.copyProperties(ptMeasureUpdateDTO, measure);
        measure.setMeasureStatus(MeasureStateEnum.MAKING.getCode())
                .setUpdateUserId(currentUser.getId());
        ptMeasureMapper.updateById(measure);

        //?????????????????????????????????
        String measurePath = k8sNameTool.getPath(BizPathEnum.MEASURE, currentUser.getId());
        measureFileAsync.generateMeasureFile(measure, measurePath);

    }

    /**
     * ??????id????????????
     *
     * @param ptMeasureDeleteDTO ?????????????????????DTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMeasure(PtMeasureDeleteDTO ptMeasureDeleteDTO) {
        //??????????????????????????????
        UserContext currentUser = userContextService.getCurUser();
        try {

            Set<Long> idList = ptMeasureDeleteDTO.getIds();
            List<PtMeasure> measureList = ptMeasureMapper.selectBatchIds(idList);
            if (CollUtil.isEmpty(idList)) {
                throw new BusinessException("????????????ID????????????????????????");
            }

            int count = ptMeasureMapper.deleteBatchIds(idList);
            if (count < measureList.size()) {
                throw new BusinessException("????????????ID????????????????????????");
            }
            measureList.forEach(ptMeasure -> {
                delMeasureFile(ptMeasure);
            });

        } catch (BusinessException e) {
            LogUtil.error(LogEnum.MEASURE, "delete the measure failed,exception {}", e);
            throw new BusinessException("????????????");
        }
    }

    /**
     * ??????????????????????????????????????????
     *
     * @param name ????????????
     * @return String ????????????json?????????
     */
    @Override
    public String getMeasureByName(String name) {

        List<PtMeasure> ptMeasureList = ptMeasureMapper.selectList(new LambdaQueryWrapper<PtMeasure>()
                .eq(PtMeasure::getName, name));
        BufferedInputStream bufferedInput = null;
        byte[] buffer = new byte[1024];
        StringBuilder sb = new StringBuilder();
        try {
            if (CollUtil.isNotEmpty(ptMeasureList)) {
                String url = fileStoreApi.formatPath(fileStoreApi.getRootDir() + fileStoreApi.getBucket() + StrUtil.SLASH + ptMeasureList.get(0).getUrl());

                if (fileStoreApi.fileOrDirIsExist(url)) {
                    int bytesRead = 0;
                    //?????????????????????
                    bufferedInput = new BufferedInputStream(new FileInputStream(url));
                    while ((bytesRead = bufferedInput.read(buffer)) != -1) {
                        //???????????????????????????????????????
                        String tmpStr = new String(buffer, 0, bytesRead);
                        sb.append(tmpStr);
                    }
                }
            }
        } catch (IOException e) {
            LogUtil.error(LogEnum.MEASURE, "getMeasureByName method read jsonFile operation failed,exception{}", e);
            throw new BusinessException("????????????");
        } finally {
            IOUtil.close(bufferedInput);
        }
        return JSONUtil.toJsonStr(sb);
    }


    /**
     * ??????nfs???????????????????????????
     *
     * @param ptMeasure ?????????????????????
     */
    private void delMeasureFile(PtMeasure ptMeasure) {
        String recyclePath = "";
        String filePath = ptMeasure.getUrl();
        if (StrUtil.isNotBlank(filePath)) {
            String nfsBucket = fileStoreApi.getRootDir() + fileStoreApi.getBucket() + StrUtil.SLASH;
            //???????????????nfs?????????????????????
            filePath = filePath.substring(0, filePath.lastIndexOf(StrUtil.SLASH));
            //???????????????nfs?????????????????????
            recyclePath = fileStoreApi.formatPath((nfsBucket + StrUtil.SLASH + filePath));
        }

        // ????????????????????????????????????????????????
        RecycleCreateDTO recycleCreateDTO = RecycleCreateDTO.builder()
                .recycleModule(RecycleModuleEnum.BIZ_MEASURE.getValue())
                .recycleDelayDate(recycleConfig.getMeasureValid())
                .recycleNote(RecycleTool.generateRecycleNote("??????????????????", ptMeasure.getName(), ptMeasure.getId()))
                .recycleCustom(RecycleResourceEnum.MEASURE_RECYCLE_FILE.getClassName())
                .restoreCustom(RecycleResourceEnum.MEASURE_RECYCLE_FILE.getClassName())
                .remark(String.valueOf(ptMeasure.getId()))
                .build();

        recycleCreateDTO.addRecycleDetailCreateDTO(RecycleDetailCreateDTO.builder()
                .recycleType(RecycleTypeEnum.FILE.getCode())
                .recycleCondition(recyclePath)
                .recycleNote(RecycleTool.generateRecycleNote("??????????????????", ptMeasure.getName(), ptMeasure.getId()))
                .remark(String.valueOf(ptMeasure.getId()))
                .build()
        );
        recycleService.createRecycleTask(recycleCreateDTO);
    }

    /**
     * ????????????????????????
     *
     * @param dto ??????DTO??????
     */
    @Override
    public void recycleRollback(RecycleCreateDTO dto) {
        String measureId = dto.getRemark();
        ptMeasureMapper.updateDeletedById(Long.valueOf(measureId), false);
    }
}
