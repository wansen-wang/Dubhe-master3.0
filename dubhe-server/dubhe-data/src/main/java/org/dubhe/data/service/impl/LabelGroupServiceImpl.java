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

package org.dubhe.data.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.dubhe.biz.base.vo.LabelGroupBaseVO;
import org.dubhe.biz.permission.annotation.DataPermissionMethod;
import org.dubhe.biz.permission.annotation.RolePermission;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.base.constant.NumberConstant;
import org.dubhe.biz.base.context.DataContext;
import org.dubhe.biz.base.dto.CommonPermissionDataDTO;
import org.dubhe.biz.base.enums.DatasetTypeEnum;
import org.dubhe.biz.base.enums.SwitchEnum;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.utils.RandomUtil;
import org.dubhe.biz.base.utils.StringUtils;
import org.dubhe.biz.permission.base.BaseService;
import org.dubhe.biz.db.utils.PageUtil;
import org.dubhe.biz.db.utils.WrapperHelp;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.cloud.authconfig.utils.JwtUtils;
import org.dubhe.data.constant.*;
import org.dubhe.data.dao.DatasetMapper;
import org.dubhe.data.dao.LabelGroupMapper;
import org.dubhe.data.dao.PcDatasetMapper;
import org.dubhe.data.domain.dto.*;
import org.dubhe.data.domain.entity.DatasetGroupLabel;
import org.dubhe.data.domain.entity.Label;
import org.dubhe.data.domain.entity.LabelGroup;
import org.dubhe.data.domain.vo.LabelGroupQueryVO;
import org.dubhe.data.domain.vo.LabelGroupVO;
import org.dubhe.data.domain.vo.LabelVO;
import org.dubhe.data.service.DatasetGroupLabelService;
import org.dubhe.data.service.DatasetLabelService;
import org.dubhe.data.service.LabelGroupService;
import org.dubhe.data.service.LabelService;
import org.dubhe.data.util.FileUtil;
import org.dubhe.data.util.JsonUtil;
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
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

import static org.dubhe.data.constant.Constant.SORT_ASC;

/**
 * @description ????????? ???????????????
 * @date 2020-09-22
 */
@Service
public class LabelGroupServiceImpl extends ServiceImpl<LabelGroupMapper, LabelGroup> implements LabelGroupService {

    @Autowired
    private LabelService labelService;

    @Autowired
    private DatasetLabelService datasetLabelService;

    @Autowired
    private PcDatasetMapper pcDatasetMapper;

    @Autowired
    private DatasetMapper datasetService;

    @Autowired
    private DatasetGroupLabelService datasetGroupLabelService;

    /**
     * ??????????????????
     */
    @Autowired
    private RecycleService recycleService;

    /**
     * ???????????????
     *
     * @param labelGroupCreateDTO ???????????????DTO
     * @return Long ?????????id
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long creatLabelGroup(LabelGroupCreateDTO labelGroupCreateDTO) {

        //1 ???????????????????????????
        labelGroupCreateDTO.setOriginUserId(JwtUtils.getCurUserId());
        if (checkoutLabelGroupName(labelGroupCreateDTO.getName())) {
            throw new BusinessException(ErrorEnum.LABELGROUP_NAME_DUPLICATED_ERROR);
        }
        LabelGroup labelGroup = LabelGroupCreateDTO.from(labelGroupCreateDTO);
        try {
            //2 ?????????????????????
            save(labelGroup);
        } catch (Exception e) {
            throw new BusinessException(ErrorEnum.LABELGROUP_NAME_DUPLICATED_ERROR);
        }
        if (StringUtils.isEmpty(labelGroupCreateDTO.getLabels())) {
            throw new BusinessException(ErrorEnum.LABELGROUP_JSON_FILE_ERROR);
        }
        //3 ????????????json????????????
        List<LabelDTO> labelList = analyzeLabelData(labelGroupCreateDTO.getLabels());

        //4 ?????????????????????
        if (!CollectionUtils.isEmpty(labelList)) {
            buildLabelDataByCreate(labelGroup, labelList);
        }
        return labelGroup.getId();
    }

    /**
     * ???????????????????????????
     *
     * @param labelGroupId        ?????????ID
     * @param labelGroupCreateDTO ???????????????DTO
     * @return Boolean ??????????????????
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void update(Long labelGroupId, LabelGroupCreateDTO labelGroupCreateDTO) {
        LabelGroup labelGroup = getBaseMapper().selectById(labelGroupId);
        //1 ???????????????????????????
        if (Objects.isNull(labelGroup)) {
            throw new BusinessException(ErrorEnum.LABELGROUP_DOES_NOT_EXIST);
        }
        //2 ??????????????????????????????
        if (checkoutLabelGroupName(labelGroupCreateDTO.getName()) && !labelGroup.getName().equals(labelGroupCreateDTO.getName())) {
            throw new BusinessException(ErrorEnum.LABELGROUP_NAME_DUPLICATED_ERROR);
        }
        LabelGroup group = LabelGroup.builder()
                .id(labelGroupId).name(labelGroupCreateDTO.getName()).remark(labelGroupCreateDTO.getRemark()).build();
        try {
            //3 ?????????????????????
            updateById(group);
        } catch (Exception e) {
            throw new BusinessException(ErrorEnum.LABELGROUP_NAME_DUPLICATED_ERROR);
        }
        //4 ??????????????????
        List<LabelDTO> labelList = analyzeLabelData(labelGroupCreateDTO.getLabels());

        //5 ?????????????????????
        if (!CollectionUtils.isEmpty(labelList)) {
            // 6-0 ??????????????????????????????
            List<Label> dbLabels = labelService.listByGroupId(labelGroup.getId());
            if (!CollectionUtils.isEmpty(dbLabels)) {
                //????????????????????????
                Map<Long, String> pubLabels = getPubLabels(labelGroupCreateDTO.getLabelGroupType());
                Map<Long, List<Label>> dbListMap = dbLabels.stream().collect(Collectors.groupingBy(Label::getId));
                //????????????????????????????????????
                int count = datasetService.getCountByLabelGroupId(labelGroupId);
                if (count > 0) {
                    buildLabelDataByUpdate(labelGroup, labelList, dbListMap, pubLabels);
                } else {
                    buildLabelDataByUpdate(dbListMap, labelGroup, labelList, pubLabels);
                }
            }
        }
    }

    /**
     * ???????????????????????????
     *
     * @param dbListMap  ???????????????map key: ??????id value: ??????
     * @param labelGroup ?????????
     * @param labelList  ????????????
     * @param pubLabels  ????????????
     */
    private void buildLabelDataByUpdate(Map<Long, List<Label>> dbListMap, LabelGroup labelGroup, List<LabelDTO> labelList, Map<Long, String> pubLabels) {

        //???????????????????????????????????????
        datasetGroupLabelService.deleteByGroupId(labelGroup.getId());

        Map<String, Long> nameMap = new HashMap<>(labelList.size());

        for (LabelDTO dto : labelList) {
            checkoutNameAndColor(dto, nameMap);
            //??????id????????????
            if (!Objects.isNull(dto.getId())) {
                //???????????????
                if (!Objects.isNull(pubLabels.get(dto.getId()))) {
                    //???????????????????????????
                    if (!dto.getName().equals(pubLabels.get(dto.getId()))) {
                        //??????????????????????????????
                        throw new BusinessException(ErrorEnum.LABELGROUP_LABELG_ID_ERROR);
                    }
                    //????????????????????????????????????
                    datasetGroupLabelService.insert(
                            DatasetGroupLabel.builder()
                                    .labelId(dto.getId())
                                    .labelGroupId(labelGroup.getId()).build());
                } else {
                    //????????????????????????????????????
                    List<Label> labels = dbListMap.get(dto.getId());
                    if (!CollectionUtils.isEmpty(labels)) {
                        labelService.updateLabel(Label.builder().id(dto.getId()).color(dto.getColor()).name(dto.getName()).build());
                        //????????????????????????????????????
                        datasetGroupLabelService.insert(
                                DatasetGroupLabel.builder()
                                        .labelId(dto.getId())
                                        .labelGroupId(labelGroup.getId()).build());
                    } else {
                        //???????????????????????????????????????
                        throw new BusinessException(ErrorEnum.LABELGROUP_LABELG_ID_ERROR);
                    }
                }
            } else {
                // 7-2 ??????????????????
                Label buildLabel = Label.builder()
                        .color(dto.getColor())
                        .name(dto.getName())
                        .type(DatasetLabelEnum.CUSTOM.getType())
                        .build();
                labelService.insert(buildLabel);
                //????????????????????????????????????
                datasetGroupLabelService.insert(
                        DatasetGroupLabel.builder()
                                .labelId(buildLabel.getId())
                                .labelGroupId(labelGroup.getId()).build());
            }

        }
    }

    /**
     * ???????????????
     *
     * @param labelGroupDeleteDTO ???????????????DTO
     */
    @Override
    public void delete(LabelGroupDeleteDTO labelGroupDeleteDTO) {
        if (CollectionUtils.isEmpty(Collections.singleton(labelGroupDeleteDTO.getIds()))) {
            return;
        }
        for (Long id : labelGroupDeleteDTO.getIds()) {
            delete(id);
        }
    }

    /**
     * ?????????????????????
     *
     * @param labelGroupId ?????????ID
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(Long labelGroupId) {
        LabelGroup labelGroup = baseMapper.selectById(labelGroupId);
        //???????????????????????????
        if(Objects.isNull(labelGroup)){
            throw new BusinessException(ErrorEnum.LABELGROUP_DOES_NOT_EXIST);
        }
        //?????????????????????????????????????????????
        if (labelGroup.getType().compareTo(MagicNumConstant.ONE) == 0) {
            BaseService.checkAdminPermission();
        }

        //???????????????????????????????????????
        if (datasetService.getCountByLabelGroupId(labelGroupId) > 0) {
            throw new BusinessException(ErrorEnum.LABELGROUP_LABEL_GROUP_QUOTE_DEL_ERROR);
        }

        // ?????????????????????????????????????????????
        if (pcDatasetMapper.getCountPCByLabelGroupId(labelGroupId) > 0) {
            throw new BusinessException(ErrorEnum.LABELGROUP_LABEL_GROUP_PC_DEL_ERROR);
        }

        List<Label> labels = labelService.listByGroupId(labelGroupId);

        if (!CollectionUtils.isEmpty(labels)) {

            //?????????????????????
            List<Long> ids = labelService.getPubLabelIds(labelGroup.getLabelGroupType());
            if (!CollectionUtils.isEmpty(ids)) {
                labels = labels.stream().filter(label -> !ids.contains(label.getId())).collect(Collectors.toList());
            }
            if (!CollectionUtils.isEmpty(labels)) {
                List<Long> labelIds = new ArrayList<>();
                labels.forEach(label -> labelIds.add(label.getId()));
                if (datasetLabelService.isLabelGroupInUse(labels)) {
                    throw new BusinessException(ErrorEnum.LABELGROUP_IN_USE_STATUS);
                }
                //????????????
                labelService.updateStatusByLabelIds(labelIds,true);
                //????????????????????????????????????
                datasetGroupLabelService.updateStatusByGroupId(labelGroupId,true);
            }
        }
        //?????????????????????
        getBaseMapper().updateStatusByGroupId(labelGroupId,true);
        //??????????????????
        try {
            addRecycleDataByDeleteDataset(labelGroup);
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_DATASET, "LabelGroupServiceImpl addRecycleDataByDeleteDataset error:{}", e);
        }


    }


    /**
     * ??????????????????
     *
     * @param labelGroup ???????????????
     */
    private void addRecycleDataByDeleteDataset( LabelGroup labelGroup){

        //??????????????????????????????????????????
        List<RecycleDetailCreateDTO> detailList = new ArrayList<>();
        detailList.add( RecycleDetailCreateDTO.builder()
                .recycleCondition(labelGroup.getId().toString())
                .recycleType(RecycleTypeEnum.TABLE_DATA.getCode())
                .recycleNote(RecycleTool.generateRecycleNote("?????? ?????????DB ????????????", labelGroup.getId()))
                .build());
        //??????????????????
        RecycleCreateDTO recycleCreateDTO = RecycleCreateDTO.builder()
                .recycleModule(RecycleModuleEnum.BIZ_DATASET.getValue())
                .recycleCustom(RecycleResourceEnum.LABEL_GROUP_RECYCLE_FILE.getClassName())
                .restoreCustom(RecycleResourceEnum.LABEL_GROUP_RECYCLE_FILE.getClassName())
                .recycleDelayDate(NumberConstant.NUMBER_1)
                .recycleNote(RecycleTool.generateRecycleNote("???????????????????????????", labelGroup.getName(), labelGroup.getId()))
                .detailList(detailList)
                .build();
        recycleService.createRecycleTask(recycleCreateDTO);
    }

    /**
     * ?????????????????????
     *
     * @param page              ????????????
     * @param labelGroupQueryVO ????????????
     * @return Map<String, Object> ???????????????????????????
     */
    @Override
    @DataPermissionMethod
    public Map<String, Object> listVO(Page<LabelGroup> page, LabelGroupQueryVO labelGroupQueryVO) {
        String name = labelGroupQueryVO.getName();
        if(MagicNumConstant.ONE == labelGroupQueryVO.getType()){
            DataContext.set(CommonPermissionDataDTO.builder().type(true).build());
        }
        if (StringUtils.isEmpty(name)) {
            return queryLabelGroups(page, labelGroupQueryVO, null);
        }
        boolean nameFlag = Constant.PATTERN_NUM.matcher(name).matches();
        if (nameFlag) {
            LabelGroupQueryVO queryCriteriaId = new LabelGroupQueryVO();
            BeanUtils.copyProperties(labelGroupQueryVO, queryCriteriaId);
            queryCriteriaId.setName(null);
            queryCriteriaId.setId(Long.parseLong(labelGroupQueryVO.getName()));
            Map<String, Object> map = queryLabelGroups(page, queryCriteriaId, null);
            if (((List) map.get(Constant.RESULT)).size() > 0) {
                queryCriteriaId.setName(name);
                queryCriteriaId.setId(null);
                return queryLabelGroups(page, queryCriteriaId, Long.parseLong(labelGroupQueryVO.getName()));
            }
        }
        return queryLabelGroups(page, labelGroupQueryVO, null);
    }

    public Map<String, Object> queryLabelGroups(Page<LabelGroup> page, LabelGroupQueryVO labelGroupQueryVO, Long labelGroupId) {

        QueryWrapper<LabelGroup> queryWrapper = WrapperHelp.getWrapper(labelGroupQueryVO);
        queryWrapper.eq("deleted", MagicNumConstant.ZERO);
        if (labelGroupId != null) {
            queryWrapper.or().eq("id", labelGroupId);
        }
        if(!Objects.isNull(labelGroupQueryVO.getLabelGroupType())){
            queryWrapper.eq("label_group_type",labelGroupQueryVO.getLabelGroupType());
        }
        if (StringUtils.isNotEmpty(labelGroupQueryVO.getSort()) && StringUtils.isNotEmpty(labelGroupQueryVO.getOrder())) {
            queryWrapper.orderBy(true, SORT_ASC.equals(labelGroupQueryVO.getOrder().toLowerCase()),
                    StringUtils.humpToLine(labelGroupQueryVO.getSort())
            );
        } else {
            queryWrapper.orderByDesc("update_time");
        }
        Page<LabelGroup> labelGroupPage = baseMapper.selectPage(page, queryWrapper);
        List<LabelGroupQueryVO> labelGroups = new ArrayList<>();
        if(!CollectionUtils.isEmpty(labelGroupPage.getRecords())){
            List<LabelGroup> records = labelGroupPage.getRecords();
            List<Long> groupIds = records.stream().map(a -> a.getId()).collect(Collectors.toList());
            Map<Long, Integer> labelGroupMap = datasetGroupLabelService.getLabelByGroupIds(groupIds);
            labelGroups = records.stream().map(labelGroup -> {
                LabelGroupQueryVO labelGroupQuery = LabelGroupQueryVO.builder()
                        .id(labelGroup.getId()).name(labelGroup.getName())
                        .operateType(labelGroup.getOperateType())
                        .type(labelGroup.getType())
                        .createTime(labelGroup.getCreateTime())
                        .labelGroupType(labelGroup.getLabelGroupType())
                        .remark(labelGroup.getRemark()).updateTime(labelGroup.getUpdateTime()).build();
                labelGroupQuery.setCount(labelGroupMap.get(labelGroup.getId()));
                return labelGroupQuery;
            }).collect(Collectors.toList());
        }
        Map<String, Object> stringObjectMap = PageUtil.toPage(page,labelGroups);
        return stringObjectMap;
    }

    /**
     * ???????????????
     *
     * @param labelGroupId ?????????id
     * @return org.dubhe.data.domain.vo.LabelGroupVO ??????Id???????????????????????????
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public LabelGroupVO get(Long labelGroupId) {
        LabelGroup labelGroup = baseMapper.selectById(labelGroupId);
        if (labelGroup == null) {
            throw new BusinessException(ErrorEnum.LABELGROUP_DOES_NOT_EXIST);
        }
        List<Label> labels = labelService.listByGroupId(labelGroup.getId());
        List<LabelVO> labelVOS = new ArrayList<>();
        if (!CollectionUtils.isEmpty(labels)) {
            labelVOS = labels.stream().map(a -> {
                return LabelVO.builder().id(a.getId()).color(a.getColor()).name(a.getName()).build();
            }).collect(Collectors.toList());
        }
        return LabelGroupVO.builder()
                .id(labelGroupId)
                .type(labelGroup.getType())
                .name(labelGroup.getName())
                .remark(labelGroup.getRemark())
                .operateType(labelGroup.getOperateType())
                .labelGroupType(labelGroup.getLabelGroupType())
                .labels(labelVOS).build();
    }

    /**
     * ???????????????
     *
     * @param labelGroupQueryDTO ????????????
     * @return List<LabelGroup> ???????????????????????????
     */
    @Override
    @DataPermissionMethod
    public List<LabelGroup> getList(LabelGroupQueryDTO labelGroupQueryDTO) {
        if(MagicNumConstant.ONE == labelGroupQueryDTO.getType()){
            DataContext.set(CommonPermissionDataDTO.builder().type(true).build());
        }
        Integer groupType = LabelGroupTypeEnum.convertGroup(DatatypeEnum.getEnumValue(labelGroupQueryDTO.getDataType())).getValue();
        LambdaQueryWrapper<LabelGroup> labelGroupLambdaQueryWrapper = new LambdaQueryWrapper<>();
        labelGroupLambdaQueryWrapper.eq(LabelGroup::getDeleted, MagicNumConstant.ZERO)
                .eq(LabelGroup::getType, labelGroupQueryDTO.getType())
                .eq(LabelGroup::getLabelGroupType,groupType);
        if (MagicNumConstant.ONE == labelGroupQueryDTO.getType()) {
            if(AnnotateTypeEnum.OBJECT_DETECTION.getValue().compareTo(labelGroupQueryDTO.getAnnotateType()) == 0
                    || AnnotateTypeEnum.OBJECT_TRACK.getValue().compareTo(labelGroupQueryDTO.getAnnotateType()) == 0
                    || AnnotateTypeEnum.SEMANTIC_CUP.getValue().compareTo(labelGroupQueryDTO.getAnnotateType()) == 0){
                labelGroupLambdaQueryWrapper.ne(LabelGroup::getId,MagicNumConstant.TWO);
            }
            if (AnnotateTypeEnum.CLASSIFICATION.getValue().equals(labelGroupQueryDTO.getAnnotateType())) {
                labelGroupLambdaQueryWrapper.ne(LabelGroup::getId,MagicNumConstant.ONE);
            }
            labelGroupLambdaQueryWrapper.orderByAsc(LabelGroup::getId);
        }else {
            labelGroupLambdaQueryWrapper.orderByDesc(LabelGroup::getUpdateTime);
        }
        return baseMapper.selectList(labelGroupLambdaQueryWrapper);

    }


    /**
     * ???????????????
     *
     * @param labelGroupImportDTO ???????????????DTO
     * @param file                ????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long importLabelGroup(LabelGroupImportDTO labelGroupImportDTO, MultipartFile file) {
        //????????????/??????/????????????
        FileUtil.checkoutFile(file);

        //??????????????????
        String labels = null;
        try {
            labels = FileUtil.readFile(file);
        } catch (Exception e) {
            throw new BusinessException(ErrorEnum.LABELGROUP_JSON_FILE_FORMAT_ERROR);
        }
        LabelGroupCreateDTO createDTO = LabelGroupCreateDTO.builder()
                .labels(labels)
                .name(labelGroupImportDTO.getName())
                .labelGroupType(labelGroupImportDTO.getLabelGroupType())
                .remark(labelGroupImportDTO.getRemark()).build();

        //????????????????????????
        return this.creatLabelGroup(createDTO);
    }

    /**
     * ???????????????
     *
     * @param labelGroupCopyDTO ???????????????DTO
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void copy(LabelGroupCopyDTO labelGroupCopyDTO) {

        LabelGroup group = LabelGroup.builder().name(labelGroupCopyDTO.getName()).remark(labelGroupCopyDTO.getRemark()).build();

        //???????????????????????????
        LabelGroup oldLabelGroup = getBaseMapper().selectOne(
                new LambdaUpdateWrapper<LabelGroup>().eq(LabelGroup::getId, labelGroupCopyDTO.getId()));
        if (Objects.isNull(oldLabelGroup)) {
            throw new BusinessException(ErrorEnum.LABELGROUP_DOES_NOT_EXIST);
        }

        //?????????????????????
        LabelGroup labelGroup = getBaseMapper().selectOne(
                new LambdaUpdateWrapper<LabelGroup>().eq(LabelGroup::getName, labelGroupCopyDTO.getName()));
        if (!Objects.isNull(labelGroup)) {
            group.setName(buildLabelGroupName(labelGroup.getName()));
        }


        //?????????????????????
        LabelGroup dbLabelGroup = LabelGroup.builder()
                .labelGroupType(oldLabelGroup.getLabelGroupType())
                .name(group.getName()).remark(group.getRemark()).originUserId(oldLabelGroup.getCreateUserId()).build();
        baseMapper.insert(dbLabelGroup);

        //???????????????????????????
        List<DatasetGroupLabel> datasetGroupLabels = datasetGroupLabelService.listByGroupId(labelGroupCopyDTO.getId());

        //????????????????????????
        List<Label> labels = labelService.listByGroupId(labelGroupCopyDTO.getId());
        Map<Long, List<Label>> labelListMap = new HashMap<>(labels.size());
        if (!CollectionUtils.isEmpty(labels)) {
            labelListMap = labels.stream().collect(Collectors.groupingBy(Label::getId));
        }

        List<Label> pubLabels = labelService.getPubLabels(oldLabelGroup.getLabelGroupType());
        Map<Long, String> longListMap = new HashMap<>(pubLabels.size());
        if (!CollectionUtils.isEmpty(pubLabels)) {
            longListMap = pubLabels.stream().collect(Collectors.toMap(Label::getId, Label::getName));
        }

        if (!CollectionUtils.isEmpty(datasetGroupLabels)) {
            for (DatasetGroupLabel groupLabel : datasetGroupLabels) {
                //???????????????????????????????????????
                String labelName = longListMap.get(groupLabel.getLabelId());
                //????????????????????????
                if (Objects.isNull(labelName)) {
                    Label buildLabel = Label.builder()
                            .color(labelListMap.get(groupLabel.getLabelId()).get(0).getColor())
                            .name(labelListMap.get(groupLabel.getLabelId()).get(0).getName())
                            .type(DatasetLabelEnum.CUSTOM.getType())
                            .build();
                    labelService.insert(buildLabel);

                    //?????????????????????????????????
                    datasetGroupLabelService.insert(
                            DatasetGroupLabel.builder()
                                    .labelGroupId(dbLabelGroup.getId())
                                    .labelId(buildLabel.getId()).build());
                } else {
                    //?????????????????????????????????
                    datasetGroupLabelService.insert(
                            DatasetGroupLabel.builder()
                                    .labelGroupId(dbLabelGroup.getId())
                                    .labelId(groupLabel.getLabelId()).build());
                }
            }
        }
    }


    /**
     * ?????????????????????
     *
     * @param name ??????????????????
     * @return  ????????????????????????
     */
    private String buildLabelGroupName(String name){
        int length = name.length();
        if(name.length() > MagicNumConstant.TWENTY){
            name = name.substring(0,length-MagicNumConstant.SEVEN);
        }
        return name+ RandomUtil.randomCode();
    }

    /**
     * ???????????????ID ???????????????????????????
     *
     * @param labelGroupId  ?????????
     * @return  true: ???  false: ???
     */
    @Override
    public boolean isAnnotationByGroupId(Long labelGroupId) {
        if(Objects.isNull(labelGroupId)){
            throw new BusinessException(ErrorEnum.LABEL_GROUP_ID_IS_NULL);
        }
        LabelGroup labelGroup = baseMapper.selectById(labelGroupId);
        if(Objects.isNull(labelGroup)){
            throw new BusinessException(ErrorEnum.LABELGROUP_DOES_NOT_EXIST);
        }
        return true;
    }


    /**
     * ????????????????????????
     *
     * @param groupConvertPresetDTO ????????????????????????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @RolePermission
    public void convertPreset(GroupConvertPresetDTO groupConvertPresetDTO) {
        LabelGroup labelGroup = baseMapper.selectById(groupConvertPresetDTO.getLabelGroupId());
        if(Objects.isNull(labelGroup)){
           throw new BusinessException("????????????????????????");
        }
        if(MagicNumConstant.ZERO != labelGroup.getType()){
            throw new BusinessException("??????????????????????????????");
        }
        baseMapper.updateInfoByGroupId(MagicNumConstant.ONE, (long) MagicNumConstant.ZERO,groupConvertPresetDTO.getLabelGroupId());
    }

    /**
     * ???????????????ID?????????????????????
     *
     * @param groupId ?????????ID
     */
    @Override
    public void deleteByGroupId(Long groupId) {
        baseMapper.deleteByGroupId(groupId);
    }

    /**
     * ???????????????ID????????????
     *
     * @param groupId ?????????ID
     * @param deletedFlag ????????????
     */
    @Override
    public void updateStatusByGroupId(Long groupId, Boolean deletedFlag) {
            baseMapper.updateStatusByGroupId(groupId,deletedFlag);
    }

    @Override
    public List<LabelGroupBaseVO> queryLabelGroupList(Set<Long> labelGroupIds) {
        if (CollectionUtils.isEmpty(labelGroupIds)){
            return new ArrayList<>();
        }
        List<LabelGroup> labelGroupList = baseMapper.selectBatchIds(labelGroupIds);
        if (CollectionUtils.isEmpty(labelGroupList)) {
            throw new BusinessException(ErrorEnum.LABELGROUP_DOES_NOT_EXIST);
        }
        List<LabelGroupBaseVO> labelGroupBaseVOList = labelGroupList.stream().map(labelGroup -> {
            LabelGroupBaseVO labelGroupBaseVO = new LabelGroupBaseVO();
            BeanUtils.copyProperties(labelGroup, labelGroupBaseVO);
            return labelGroupBaseVO;
        }).collect(Collectors.toList());

        return labelGroupBaseVOList;
    }


    /**
     * ?????????????????????????????????
     *
     * @param name ???????????????
     * @return true: ????????? false:?????????
     */
    private boolean checkoutLabelGroupName(String name) {
        //???????????????????????????
        LabelGroup labelGroup = baseMapper.selectOne(
                new LambdaQueryWrapper<LabelGroup>()
                        .eq(LabelGroup::getName, name)
                        .eq(LabelGroup::getDeleted, SwitchEnum.getBooleanValue(SwitchEnum.OFF.getValue()))
        );

        return !Objects.isNull(labelGroup);
    }

    /**
     * ???????????????????????????
     *
     * @param labelGroup ?????????
     * @param labelList  ????????????
     */
    private void buildLabelDataByCreate(LabelGroup labelGroup, List<LabelDTO> labelList) {

        //????????????????????????
        Map<Long, String> pubLabels = getPubLabels(labelGroup.getLabelGroupType());

        Map<String, Long> nameMap = new HashMap<>(labelList.size());
        for (LabelDTO label : labelList) {

            // 5-1 ?????????????????? ?????? ????????????????????????
            checkoutNameAndColor(label, nameMap);

            // 5-3 ??????????????????id??????????????????
            //????????????id??????
            if (!Objects.isNull(label.getId())) {
                //5-3-1 ?????????????????????????????????????????????
                String pubLabelName = pubLabels.get(label.getId());
                //????????????????????????????????????
                if (Objects.isNull(pubLabelName)) {
                    throw new BusinessException(ErrorEnum.LABELGROUP_LABELG_ID_ERROR);
                    //???????????????????????????????????????????????????
                } else if (!pubLabelName.equals(label.getName())) {
                    throw new BusinessException(ErrorEnum.LABELGROUP_LABEL_NAME_ERROR);
                } else {
                    //????????????????????????????????????????????????
                    //????????????????????????????????????
                    datasetGroupLabelService.insert(
                            DatasetGroupLabel.builder()
                                    .labelId(label.getId())
                                    .labelGroupId(labelGroup.getId()).build());
                }
            } else {
                // 5-3 ??????????????????
                Label buildLabel = Label.builder()
                        .color(label.getColor())
                        .name(label.getName())
                        .type(DatasetLabelEnum.CUSTOM.getType())
                        .build();
                labelService.insert(buildLabel);
                //????????????????????????????????????
                datasetGroupLabelService.insert(
                        DatasetGroupLabel.builder()
                                .labelId(buildLabel.getId())
                                .labelGroupId(labelGroup.getId()).build());
            }
        }
    }

    /**
     * ????????????????????????
     *
     * @param labelGroupType ???????????????
     * @return ?????????????????? key: ??????id value: ????????????
     */
    private Map<Long, String> getPubLabels(Integer labelGroupType) {
        List<Label> pubLabels = labelService.getPubLabels(labelGroupType);
        Map<Long, String> pubListMap = new HashMap<>(pubLabels.size());
        if (!CollectionUtils.isEmpty(pubLabels)) {
            pubListMap = pubLabels.stream().collect(Collectors.toMap(Label::getId, Label::getName));
        }
        return pubListMap;
    }

    /**
     * ??????????????????
     *
     * @param labels ??????Json?????????
     * @return ????????????????????????
     */
    private List<LabelDTO> analyzeLabelData(String labels) {
        if (!JsonUtil.isJson(labels)) {
            throw new BusinessException(ErrorEnum.LABELGROUP_JSON_FILE_FORMAT_ERROR);
        }

        //5 ??????????????????
        List<LabelDTO> labelList = new ArrayList<>();
        try {
            labelList = JSONObject.parseArray(labels, LabelDTO.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorEnum.LABELGROUP_JSON_FILE_FORMAT_ERROR);
        }

        return labelList;
    }

    /**
     * ???????????????????????????
     *
     * @param dbListMap  ???????????????map key: ??????id value: ??????
     * @param labelGroup ?????????
     * @param labelList  ????????????
     * @param pubLabels  ????????????
     */
    private void buildLabelDataByUpdate(LabelGroup labelGroup, List<LabelDTO> labelList, Map<Long, List<Label>> dbListMap, Map<Long, String> pubLabels) {

        Map<String, Long> nameMap = new HashMap<>(labelList.size());

        for (LabelDTO label : labelList) {
            List<Label> labels = dbListMap.get(label.getId());
            //?????????????????? ?????? ????????????????????????
            checkoutNameAndColor(label, nameMap);

            // 6-4 ???????????????????????????
            if (!CollectionUtils.isEmpty(labels) && label.getName().equals(labels.get(0).getName())) {
                continue;
            }

            // 7 ??????ID????????????
            // 7-1 ??????????????????????????????????????????
            String pubLabelName = pubLabels.get(label.getId());
            if (!Objects.isNull(label.getId())) {

                //?????????????????????
                if (!Objects.isNull(pubLabelName)) {
                    //?????????????????????????????????????????????id
                    if (!pubLabelName.equals(label.getName()) && dbListMap.keySet().contains(label.getId())) {
                        throw new BusinessException(ErrorEnum.LABELGROUP_OPERATE_LABEL_ID_ERROR);
                    } else {
                        //????????????????????????????????????
                        datasetGroupLabelService.insert(
                                DatasetGroupLabel.builder()
                                        .labelId(label.getId())
                                        .labelGroupId(labelGroup.getId()).build());
                    }
                    //???????????????????????? ?????????????????????
                } else if (!CollectionUtils.isEmpty(labels)) {
                    Label buildLabel = Label.builder()
                            .color(label.getColor())
                            .name(label.getName())
                            .id(labels.get(0).getId())
                            .build();
                    labelService.updateLabel(buildLabel);
                } else {//???????????????????????? ????????????????????????
                    throw new BusinessException(ErrorEnum.LABELGROUP_LABELG_ID_ERROR);
                }
            } else {
                // 7-2 ??????????????????
                Label buildLabel = Label.builder()
                        .color(label.getColor())
                        .name(label.getName())
                        .type(DatasetLabelEnum.CUSTOM.getType())
                        .build();
                labelService.insert(buildLabel);
                //????????????????????????????????????
                datasetGroupLabelService.insert(
                        DatasetGroupLabel.builder()
                                .labelId(buildLabel.getId())
                                .labelGroupId(labelGroup.getId()).build());
            }
        }
    }

    /**
     * ?????????????????? ?????? ????????????????????????
     *
     * @param label   ????????????
     * @param nameMap ????????????Map key:???????????? value:??????ID
     */
    private void checkoutNameAndColor(LabelDTO label, Map<String, Long> nameMap) {

        //???????????????????????????
        if (Objects.isNull(label.getName()) || Objects.isNull(label.getColor())) {
            throw new BusinessException(ErrorEnum.LABEL_FORMAT_IS_ERROR);
        }

        //?????????????????????/ID????????????
        Long labelId = nameMap.get(label.getName());
        if (nameMap.containsKey(label.getName()) ||
                ((!Objects.isNull(labelId)) && labelId.equals(label.getId()))
        ) {
            throw new BusinessException(ErrorEnum.LABEL_NAME_DUPLICATION);
        }
        nameMap.put(label.getName(), label.getId());
    }

}