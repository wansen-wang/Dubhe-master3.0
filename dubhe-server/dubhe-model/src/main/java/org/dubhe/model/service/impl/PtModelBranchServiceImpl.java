/**
 * Copyright 2020 Tianshu AI Platform. All Rights Reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =============================================================
 */

package org.dubhe.model.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.compress.utils.Lists;
import org.dubhe.biz.base.constant.SymbolConstant;
import org.dubhe.biz.base.context.UserContext;
import org.dubhe.biz.base.dto.PtModelBranchConditionQueryDTO;
import org.dubhe.biz.base.dto.PtModelBranchQueryByIdDTO;
import org.dubhe.biz.base.dto.PtModelStatusQueryDTO;
import org.dubhe.biz.base.enums.DatasetTypeEnum;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.service.UserContextService;
import org.dubhe.biz.base.utils.PtModelUtil;
import org.dubhe.biz.base.utils.ReflectionUtils;
import org.dubhe.biz.base.utils.StringUtils;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.base.vo.PtModelBranchQueryVO;
import org.dubhe.biz.db.utils.PageUtil;
import org.dubhe.biz.db.utils.WrapperHelp;
import org.dubhe.biz.file.enums.BizPathEnum;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.permission.annotation.DataPermissionMethod;
import org.dubhe.cloud.remotecall.config.RestTemplateHolder;
import org.dubhe.k8s.utils.K8sNameTool;
import org.dubhe.model.dao.PtModelBranchMapper;
import org.dubhe.model.dao.PtModelInfoMapper;
import org.dubhe.model.domain.dto.*;
import org.dubhe.model.domain.entity.PtModelBranch;
import org.dubhe.model.domain.entity.PtModelInfo;
import org.dubhe.model.domain.enums.ModelConvertEnum;
import org.dubhe.model.domain.enums.ModelCopyStatusEnum;
import org.dubhe.model.domain.vo.PtModelBranchCreateVO;
import org.dubhe.model.domain.vo.PtModelBranchDeleteVO;
import org.dubhe.model.domain.vo.PtModelBranchUpdateVO;
import org.dubhe.model.domain.vo.PtModelConvertOnnxVO;
import org.dubhe.model.service.FileService;
import org.dubhe.model.service.PtModelBranchService;
import org.dubhe.model.utils.ModelStatusUtil;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * @description ??????????????????
 * @date 2020-03-24
 */
@Service
public class PtModelBranchServiceImpl implements PtModelBranchService {

    @Autowired
    private PtModelBranchMapper ptModelBranchMapper;

    @Autowired
    private PtModelInfoMapper ptModelInfoMapper;

    @Autowired
    private RecycleService recycleService;

    @Autowired
    private RecycleConfig recycleConfig;

    @Autowired
    private FileService fileService;

    @Autowired
    private UserContextService userContextService;

    @Autowired
    private ModelStatusUtil ModelStatusUtil;

    @Autowired
    private K8sNameTool k8sNameTool;

    @Autowired
    private RestTemplateHolder restTemplateHolder;

    @Value("${model.converter.url}")
    private String modelConverterUrl;

    public final static List<String> FIELD_NAMES;

    static {
        FIELD_NAMES = ReflectionUtils.getFieldNames(PtModelBranchQueryVO.class);
    }

    /**
     * ??????????????????
     *
     * @param ptModelBranchQueryDTO ??????????????????????????????
     * @return Map<String, Object>  ??????????????????????????????
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public Map<String, Object> queryAll(PtModelBranchQueryDTO ptModelBranchQueryDTO) {
        Page page = ptModelBranchQueryDTO.toPage();
        QueryWrapper wrapper = WrapperHelp.getWrapper(ptModelBranchQueryDTO);

        String orderField = FIELD_NAMES.contains(ptModelBranchQueryDTO.getSort())
                ? StringUtils.humpToLine(ptModelBranchQueryDTO.getSort())
                : PtModelUtil.ID;
        boolean isAsc = PtModelUtil.SORT_ASC.equalsIgnoreCase(ptModelBranchQueryDTO.getOrder());
        wrapper.orderBy(true, isAsc, orderField);

        IPage<PtModelBranch> ptModelBranches = ptModelBranchMapper.selectPage(page, wrapper);

        PtModelInfo ptModelInfo = ptModelInfoMapper.selectById(ptModelBranchQueryDTO.getParentId());
        List<PtModelBranchQueryVO> ptModelBranchQueryVOs = ptModelBranches.getRecords().stream().map(x -> {
            PtModelBranchQueryVO ptModelBranchQueryVO = new PtModelBranchQueryVO();
            BeanUtils.copyProperties(x, ptModelBranchQueryVO);
            ptModelBranchQueryVO.setName(ptModelInfo.getName()).setModelDescription(ptModelInfo.getModelDescription());
            //???????????????????????????(?????????tensorflow???oneflow,keras?????????savedmodel???pytorch??????pth,?????????????????????)
            boolean flag = (ptModelInfo.getFrameType() == PtModelUtil.NUMBER_ONE && ptModelInfo.getModelType() == PtModelUtil.NUMBER_ONE) ||
                    (ptModelInfo.getFrameType() == PtModelUtil.NUMBER_TWO && ptModelInfo.getModelType() == PtModelUtil.NUMBER_ONE) ||
                    (ptModelInfo.getFrameType() == PtModelUtil.NUMBER_FOUR && ptModelInfo.getModelType() == PtModelUtil.NUMBER_ONE) ||
                    (ptModelInfo.getFrameType() == PtModelUtil.NUMBER_THREE && ptModelInfo.getModelType() == PtModelUtil.NUMBER_EIGHT);
            ptModelBranchQueryVO.setServingModel(flag);
            return ptModelBranchQueryVO;
        }).collect(Collectors.toList());
        return PageUtil.toPage(page, ptModelBranchQueryVOs);
    }

    /**
     * ??????
     *
     * @param ptModelBranchCreateDTO ??????????????????????????????
     * @return PtModelBranchCreateVO ??????????????????????????????VO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PtModelBranchCreateVO create(PtModelBranchCreateDTO ptModelBranchCreateDTO) {
        //??????????????????
        UserContext user = userContextService.getCurUser();
        PtModelBranch ptModelBranch = new PtModelBranch();
        BeanUtils.copyProperties(ptModelBranchCreateDTO, ptModelBranch);
        QueryWrapper<PtModelInfo> ptModelInfoQueryWrapper = new QueryWrapper<PtModelInfo>();
        ptModelInfoQueryWrapper.eq("id", ptModelBranchCreateDTO.getParentId());
        PtModelInfo ptModelInfo = ptModelInfoMapper.selectOne(ptModelInfoQueryWrapper);
        if (ptModelInfo == null) {
            LogUtil.error(LogEnum.BIZ_MODEL, "User {} failed to update model list", user.getUsername());
            throw new BusinessException("????????????????????????");
        }
        ptModelBranch.setVersion(getVersion(ptModelInfo));
        ptModelBranch.setModelPath("");
        ptModelBranch.setCreateUserId(ptModelInfo.getCreateUserId());
        //???????????????
        String sourcePath = ptModelBranchCreateDTO.getModelAddress();
        fileService.validatePath(sourcePath);

        if (ptModelBranchCreateDTO.getModelSource() == PtModelUtil.USER_UPLOAD) {
            String targetPath = fileService.transfer(sourcePath, user);
            //??????????????????
            ptModelBranch.setModelAddress(targetPath);
            //?????????????????????????????????
            checkModelVersion(ptModelBranchCreateDTO, user, ptModelBranch);
            if (ptModelBranchMapper.insert(ptModelBranch) < 1) {
                LogUtil.error(LogEnum.BIZ_MODEL, "User {} failed to create new version", user.getUsername());
                throw new BusinessException("????????????????????????");
            }
        } else if (ptModelBranchCreateDTO.getModelSource() == PtModelUtil.TRAINING_IMPORT || ptModelBranchCreateDTO.getModelSource() == PtModelUtil.MODEL_OPTIMIZATION
                || ptModelBranchCreateDTO.getModelSource() == PtModelUtil.AUTOMATIC_MACHINE_LEARNING) {
            //???????????????
            ptModelBranch.setStatus(ModelCopyStatusEnum.COPING.getCode());
            //?????????????????????????????????
            checkModelVersion(ptModelBranchCreateDTO, user, ptModelBranch);
            if (ptModelBranchMapper.insert(ptModelBranch) < 1) {
                LogUtil.error(LogEnum.BIZ_MODEL, "User {} failed to create new version", user.getUsername());
                throw new BusinessException("????????????????????????");
            }

            fileService.copyFileAsync(sourcePath, user,
                    (targetPath) -> {
                        //??????????????????
                        ptModelBranch.setStatus(ModelCopyStatusEnum.SUCCESS.getCode());
                        ptModelBranch.setModelAddress(targetPath);
                        ptModelBranchMapper.updateById(ptModelBranch);
                    },
                    (e) -> {
                        //??????????????????
                        ptModelBranch.setStatus(ModelCopyStatusEnum.FAIL.getCode());
                        ptModelBranchMapper.updateById(ptModelBranch);
                    }
            );
        }
        //??????????????????
        ptModelInfo.setVersion(ptModelBranch.getVersion());
        ptModelInfo.setModelAddress(ptModelBranch.getModelAddress());
        ptModelInfo.setTotalNum(ptModelInfo.getTotalNum() + 1);
        if (ptModelInfoMapper.updateById(ptModelInfo) < 1) {
            LogUtil.error(LogEnum.BIZ_MODEL, "User {} failed to modify version, failed to modify version table", user.getUsername());
            throw new BusinessException("????????????????????????");
        }

        PtModelBranchCreateVO ptModelBranchCreateVO = new PtModelBranchCreateVO();
        ptModelBranchCreateVO.setId(ptModelBranch.getId());
        return ptModelBranchCreateVO;
    }

    /**
     * ?????????????????????????????????
     * @param ptModelBranchCreateDTO ??????
     * @param user                   ??????
     * @param ptModelBranch          ??????
     */
    private void checkModelVersion(PtModelBranchCreateDTO ptModelBranchCreateDTO, UserContext user, PtModelBranch ptModelBranch) {
        QueryWrapper<PtModelBranch> ptModelBranchWrapper = new QueryWrapper<>();
        ptModelBranchWrapper.eq("version", ptModelBranch.getVersion()).eq("parent_id", ptModelBranchCreateDTO.getParentId());
        List<PtModelBranch> ptModelBrancheList = ptModelBranchMapper.selectList(ptModelBranchWrapper);
        if (!CollectionUtils.isEmpty(ptModelBrancheList)) {
            LogUtil.error(LogEnum.BIZ_TRAIN, "Version = {} of model_parent_id = {} created by user {} already exists", ptModelBranch.getVersion(), ptModelBranchCreateDTO.getParentId(), user.getUsername());
            throw new BusinessException("????????????????????????");
        }
    }

    /**
     * ??????????????????
     *
     * @param ptModelInfo      ??????????????????????????????
     * @return String          ????????????
     */
    private String getVersion(PtModelInfo ptModelInfo) {
        String version = "V" + String.format("%04d", ptModelInfo.getTotalNum() + 1);
        return version;
    }

    /**
     * ??????
     *
     * @param ptModelBranchUpdateDTO ??????????????????????????????
     * @return PtModelBranchUpdateVO ??????????????????????????????VO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PtModelBranchUpdateVO update(PtModelBranchUpdateDTO ptModelBranchUpdateDTO) {
        //??????????????????
        UserContext user = userContextService.getCurUser();
        QueryWrapper<PtModelBranch> wrapper = new QueryWrapper<>();
        wrapper.eq("id", ptModelBranchUpdateDTO.getId());
        if (ptModelBranchMapper.selectCount(wrapper) < 1) {
            LogUtil.error(LogEnum.BIZ_MODEL, "The user {} failed to modify the model version, and has no permission to modify the corresponding data in the model version table", user.getUsername());
            throw new BusinessException("????????????ID????????????????????????");
        }
        PtModelBranch ptModelBranch = ptModelBranchMapper.selectById(ptModelBranchUpdateDTO.getId());
        BeanUtils.copyProperties(ptModelBranchUpdateDTO, ptModelBranch);

        if (ptModelBranchMapper.updateById(ptModelBranch) < 1) {
            //??????????????????????????????????????????????????????????????????
            LogUtil.error(LogEnum.BIZ_MODEL, "The user {} failed to modify the model version, and failed to modify the model version table", user.getUsername());
            throw new BusinessException("????????????????????????");
        }

        //????????????????????????id
        PtModelBranchUpdateVO ptModelBranchUpdateVO = new PtModelBranchUpdateVO();
        ptModelBranchUpdateVO.setId(ptModelBranch.getId());
        return ptModelBranchUpdateVO;
    }

    /**
     * ????????????
     *
     * @param ptModelBranchDeleteDTO ??????????????????????????????
     * @return PtModelBranchDeleteVO ??????????????????????????????VO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PtModelBranchDeleteVO deleteAll(PtModelBranchDeleteDTO ptModelBranchDeleteDTO) {
        //??????????????????
        UserContext user = userContextService.getCurUser();
        //??????ids??????
        List<Long> ids = Arrays.stream(ptModelBranchDeleteDTO.getIds()).distinct().collect(Collectors.toList());

        //????????????
        QueryWrapper<PtModelBranch> query = new QueryWrapper<>();
        query.in("id", ids);
        if (ptModelBranchMapper.selectCount(query) < ids.size()) {
            throw new BusinessException("??????????????????");
        }
        //???????????????????????????????????????????????????????????????????????????(??????????????????????????????????????????????????????????????????????????????)
        PtModelStatusQueryDTO ptModelStatusQueryDTO = new PtModelStatusQueryDTO();
        ptModelStatusQueryDTO.setModelBranchIds(ids);
        ModelStatusUtil.queryModelStatus(user, ptModelStatusQueryDTO, ids);

        //??????parentID
        List<PtModelBranch> ptModelBranches = ptModelBranchMapper.selectBatchIds(ids);
        List<Long> parentIdLists = ptModelBranches.stream().map(x -> {
            return x.getParentId();
        }).distinct().collect(Collectors.toList());

        //??????????????????
        if (ptModelBranchMapper.deleteBatchIds(ids) < ids.size()) {
            //???????????????????????????,????????????????????????????????????
            LogUtil.error(LogEnum.BIZ_MODEL, "User {} failed to delete model version. Deleting model version table according to ID array {} failed", user.getUsername(), ids);
            throw new BusinessException("????????????????????????");
        }

        //??????parent?????????
        LogUtil.info(LogEnum.BIZ_MODEL, "Parentid of update algorithm{}", parentIdLists);
        for (int num = 0; num < parentIdLists.size(); num++) {
            QueryWrapper<PtModelBranch> queryWrapper = new QueryWrapper<PtModelBranch>();
            queryWrapper.eq("parent_id", parentIdLists.get(num));
            queryWrapper.orderByDesc("id");
            queryWrapper.last("limit 1");
            List<PtModelBranch> ptModelBranchList = ptModelBranchMapper.selectList(queryWrapper);
            PtModelInfo ptModelInfo = ptModelInfoMapper.selectById(parentIdLists.get(num));
            if (ptModelBranchList.size() > 0) {
                ptModelInfo.setVersion(ptModelBranchList.get(0).getVersion());
                ptModelInfo.setModelAddress(ptModelBranchList.get(0).getModelAddress());
                if (ptModelInfoMapper.updateById(ptModelInfo) < 1) {
                    LogUtil.error(LogEnum.BIZ_MODEL, "The user {} failed to delete the model version and failed to modify the model management table", user.getUsername());
                    throw new BusinessException("????????????????????????");
                }
            } else {
                ptModelInfo.setVersion("");
                ptModelInfo.setModelAddress("");
                if (ptModelInfoMapper.updateById(ptModelInfo) < 1) {
                    LogUtil.error(LogEnum.BIZ_MODEL, "The user {} failed to delete the model version and failed to modify the model management table", user.getUsername());
                    throw new BusinessException("????????????????????????");
                }
            }
        }
        //???????????????????????????????????????
        for (PtModelBranch ptModelBranch : ptModelBranches) {
            RecycleCreateDTO recycleCreateDTO = RecycleCreateDTO.builder()
                    .recycleModule(RecycleModuleEnum.BIZ_MODEL.getValue())
                    .recycleDelayDate(recycleConfig.getModelValid())
                    .recycleNote(RecycleTool.generateRecycleNote("????????????????????????", ptModelBranch.getId()))
                    .remark(ptModelBranch.getId().toString())
                    .restoreCustom(RecycleResourceEnum.MODEL_RECYCLE_FILE.getClassName())
                    .build();
            recycleCreateDTO.addRecycleDetailCreateDTO(RecycleDetailCreateDTO.builder()
                    .recycleType(RecycleTypeEnum.FILE.getCode())
                    .recycleCondition(fileService.getAbsolutePath(ptModelBranch.getModelAddress()))
                    .recycleNote(RecycleTool.generateRecycleNote("????????????????????????", ptModelBranch.getId()))
                    .build()
            );
            recycleService.createRecycleTask(recycleCreateDTO);
        }
        PtModelBranchDeleteVO ptModelBranchDeleteVO = new PtModelBranchDeleteVO();
        ptModelBranchDeleteVO.setIds(ptModelBranchDeleteDTO.getIds());
        return ptModelBranchDeleteVO;
    }


    /**
     * ??????????????????id????????????????????????
     *
     * @param ptModelBranchQueryByIdDTO ???????????????id????????????????????????????????????
     * @return PtModelBranchQueryByIdVO ??????????????????id????????????????????????????????????
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public PtModelBranchQueryVO queryByBranchId(PtModelBranchQueryByIdDTO ptModelBranchQueryByIdDTO) {
        PtModelBranch ptModelBranch = ptModelBranchMapper.selectById(ptModelBranchQueryByIdDTO.getId());
        if(Objects.isNull(ptModelBranch)){
            return null;
        }
        PtModelBranchQueryVO ptModelBranchQueryByIdVO = new PtModelBranchQueryVO();
        BeanUtils.copyProperties(ptModelBranch, ptModelBranchQueryByIdVO);
        return ptModelBranchQueryByIdVO;
    }

    /**
     * ??????????????????????????????
     *
     * @param ptModelBranchConditionQueryDTO ????????????
     * @return PtModelBranchQueryVO ??????????????????????????????
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public PtModelBranchQueryVO getConditionQuery(PtModelBranchConditionQueryDTO ptModelBranchConditionQueryDTO) {
        LambdaQueryWrapper<PtModelBranch> wrapper = new LambdaQueryWrapper();
        wrapper.eq(PtModelBranch::getParentId, ptModelBranchConditionQueryDTO.getParentId());
        wrapper.eq(PtModelBranch::getModelAddress, ptModelBranchConditionQueryDTO.getModelAddress());
        PtModelBranch ptModelBranch = ptModelBranchMapper.selectOne(wrapper);
        if (ptModelBranch == null) {
            return null;
        }
        PtModelBranchQueryVO ptModelBranchQueryVO = new PtModelBranchQueryVO();
        BeanUtils.copyProperties(ptModelBranch, ptModelBranchQueryVO);
        return ptModelBranchQueryVO;
    }

    /**
     * ???????????????????????????
     * @param modelConvertPresetDTO ????????????id?????????
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void convertPreset(ModelConvertPresetDTO modelConvertPresetDTO) {
        //??????????????????
        UserContext user = userContextService.getCurUser();
        //????????????????????????
        QueryWrapper<PtModelInfo> nameVerification = new QueryWrapper<>();
        nameVerification.eq("name", modelConvertPresetDTO.getName());
        Integer countResult = ptModelInfoMapper.selectCount(nameVerification);
        if (countResult > 0) {
            throw new BusinessException("???????????????");
        }
        //??????????????????????????????
        QueryWrapper<PtModelBranch> modelBranchWrapper = new QueryWrapper<>();
        modelBranchWrapper.eq("id", modelConvertPresetDTO.getId()).last(" limit 1 ");
        PtModelBranch ptModelBranch = ptModelBranchMapper.selectOne(modelBranchWrapper);
        if (ptModelBranch == null) {
            throw new BusinessException("????????????????????????");
        }
        //????????????????????????
        QueryWrapper<PtModelInfo> modelInfoWrapper = new QueryWrapper<>();
        modelInfoWrapper.eq("id", ptModelBranch.getParentId()).last(" limit 1 ");
        PtModelInfo ptModelInfo = ptModelInfoMapper.selectOne(modelInfoWrapper);
        if (ptModelInfo == null) {
            throw new BusinessException("??????????????????");
        }
        //?????????????????????pt_model_info???
        String targetPath = fileService.convertPreset(ptModelBranch.getModelAddress(), user);
        PtModelInfo preModelInfo = new PtModelInfo();
        preModelInfo.setName(modelConvertPresetDTO.getName()).setFrameType(ptModelInfo.getFrameType()).setModelType(ptModelInfo.getModelType())
                .setModelClassName(ptModelInfo.getModelClassName())
                .setModelAddress(targetPath)
                .setModelResource(1).setOriginUserId(0L).setPackaged(ptModelInfo.getPackaged())
                .setTotalNum(1).setVersion("V0001");
        if (ptModelInfo.getTags() != null) {
            preModelInfo.setTags(ptModelInfo.getTags());
        }
        if (ptModelInfo.getTeamId() != null) {
            preModelInfo.setTeamId(ptModelInfo.getTeamId());
        }
        if (modelConvertPresetDTO.getModelDescription() != null) {
            preModelInfo.setModelDescription(modelConvertPresetDTO.getModelDescription());
        }
        try {
            ptModelInfoMapper.insert(preModelInfo);
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_MODEL, "User {} failed to transfer my model to preset model because ???{}", user.getUsername(), e);
            throw new BusinessException("?????????????????????????????????");
        }
        //?????????????????????pt_model_branch???
        PtModelBranch preModelBranch = new PtModelBranch();
        preModelBranch.setParentId(preModelInfo.getId()).setVersion("V0001").setModelAddress(targetPath).setModelPath(ptModelBranch.getModelPath())
                .setOriginUserId(0L).setModelSource(ptModelBranch.getModelSource()).setTeamId(ptModelBranch.getTeamId()).setAlgorithmId(ptModelBranch.getAlgorithmId())
                .setAlgorithmName(ptModelBranch.getAlgorithmName()).setAlgorithmSource(ptModelBranch.getAlgorithmSource());
        try {
            ptModelBranchMapper.insert(preModelBranch);
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_MODEL, "User {} failed to transfer my model to preset model because ???{}", user.getUsername(), e);
            throw new BusinessException("?????????????????????????????????");
        }
    }

    /**
     * ????????????????????????
     * @param dto ????????????
     */
    @Override
    public void modelRecycleFileRollback(RecycleCreateDTO dto) {
        //??????????????????
        UserContext user = userContextService.getCurUser();
        if (dto == null) {
            LogUtil.error(LogEnum.BIZ_MODEL, "User {} restore model failed to delete the file because RecycleCreateDTO is null", user.getUsername());
            throw new BusinessException("????????????");
        }
        //????????????id
        Long ptModelBranchId = Long.valueOf(dto.getRemark());
        PtModelBranch ptModelBranchAll = ptModelBranchMapper.selectAllById(ptModelBranchId);
        //??????id
        Long ptModelInfoId = ptModelBranchAll.getParentId();
        //????????????????????????????????????
        String modelName = ptModelInfoMapper.selectNameById(ptModelInfoId);
        QueryWrapper<PtModelInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("name", modelName).last(" limit 1 ");
        PtModelInfo modelInfo = ptModelInfoMapper.selectOne(wrapper);
        if (modelInfo != null && !ptModelInfoId.equals(modelInfo.getId())) {
            throw new BusinessException("???????????????");
        }
        try {
            //??????????????????
            ptModelBranchMapper.updateStatusById(ptModelBranchId, false);
            //????????????
            PtModelInfo ptModelInfo = ptModelInfoMapper.selectById(ptModelInfoId);
            if (ptModelInfo == null) {
                ptModelInfoMapper.updateStatusById(ptModelInfoId, false, ptModelBranchAll.getVersion());
            } else {
                if (ptModelInfo.getVersion() == null || Integer.parseInt(ptModelBranchAll.getVersion().substring(PtModelUtil.NUMBER_ONE, PtModelUtil.NUMBER_FIVE)) > Integer.parseInt(ptModelInfo.getVersion().substring(PtModelUtil.NUMBER_ONE, PtModelUtil.NUMBER_FIVE))) {
                    ptModelInfoMapper.updateModelVersionById(ptModelInfoId, ptModelBranchAll.getVersion());

                }
            }

        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_MODEL, "User {} restore model failed to delete the file because:{}", user.getUsername(), e);
            throw new BusinessException("????????????");
        }
    }


    /**
     * TensorFlow SaveModel ???????????????ONNX ??????
     *
     * @param ptModelConvertOnnxDTO ???????????? id ?????????
     * @return PtModelConvertOnnxVO ?????????????????? VO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PtModelConvertOnnxVO convertToOnnx(PtModelConvertOnnxDTO ptModelConvertOnnxDTO) {
        // ??????????????????
        UserContext user = userContextService.getCurUser();

        // ??????????????????????????????
        QueryWrapper<PtModelBranch> modelBranchWrapper = new QueryWrapper<>();
        modelBranchWrapper.eq("id", ptModelConvertOnnxDTO.getId()).last(" limit 1 ");
        PtModelBranch ptModelBranch = ptModelBranchMapper.selectOne(modelBranchWrapper);
        if (ptModelBranch == null) {
            throw new BusinessException("????????????????????????");
        }

        // ????????????????????????
        QueryWrapper<PtModelInfo> modelInfoWrapper = new QueryWrapper<>();
        modelInfoWrapper.eq("id", ptModelBranch.getParentId()).last(" limit 1 ");
        PtModelInfo ptModelInfo = ptModelInfoMapper.selectOne(modelInfoWrapper);
        if (ptModelInfo == null) {
            throw new BusinessException("??????????????????");
        }
        String modelName = "onnx-from-" + ptModelInfo.getName() + SymbolConstant.HYPHEN + ptModelBranch.getVersion();
        String modelDescription = "This is an onnx model converted from " + ptModelInfo.getName() + SymbolConstant.COLON + ptModelBranch.getVersion();

        // ??????????????????
        QueryWrapper<PtModelInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("name", modelName).and(wrapper -> wrapper.eq("create_user_id", user.getId()).or().eq("origin_user_id", 0L));
        Integer countResult = ptModelInfoMapper.selectCount(queryWrapper);
        if (countResult > 0) {
            LogUtil.error(LogEnum.BIZ_MODEL, "The user {} fail to convert model???the name of model is already exist !", user.getUsername());
            throw new BusinessException("????????? ONNX ???????????????");
        }
        String onnxModelPath = k8sNameTool.getPath(BizPathEnum.MODEL, user.getId());

        // ????????????
        String onnxModelUrl = generateOnnxModel(ptModelBranch.getModelAddress(), onnxModelPath);
        if (StringUtils.isEmpty(onnxModelUrl)) {
            throw new BusinessException(ModelConvertEnum.CONVERT_SERVER_ERROR.getMsg());
        }

        // ?????? ONNX ?????????pt_model_info???
        PtModelInfo onnxModelInfo = new PtModelInfo();
        onnxModelInfo.setName(modelName).setFrameType(ptModelInfo.getFrameType()).setModelType(5).setModelDescription(modelDescription)
                .setModelAddress(onnxModelPath).setModelResource(0).setTotalNum(1).setVersion("V0001");
        if (ptModelInfo.getTeamId() != null) {
            onnxModelInfo.setTeamId(ptModelInfo.getTeamId());
        }

        try {
            ptModelInfoMapper.insert(onnxModelInfo);
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_MODEL, "User {} failed to insert ONNX model_info to database because ???{}", user.getUsername(), e);
            throw new BusinessException("ONNX ??????????????????");
        }

        // ?????? onnx ?????????pt_model_branch???
        PtModelBranch onnxModelBranch = new PtModelBranch();
        onnxModelBranch.setParentId(onnxModelInfo.getId()).setVersion("V0001").setModelAddress(onnxModelPath).setModelSource(3)
                .setModelPath(ptModelBranch.getModelPath()).setTeamId(ptModelBranch.getTeamId()).setAlgorithmId(ptModelBranch.getAlgorithmId())
                .setAlgorithmName(ptModelBranch.getAlgorithmName()).setAlgorithmSource(ptModelBranch.getAlgorithmSource());
        try {
            ptModelBranchMapper.insert(onnxModelBranch);
            PtModelConvertOnnxVO ptModelConvertOnnxVO = new PtModelConvertOnnxVO();
            ptModelConvertOnnxVO.setId(onnxModelBranch.getParentId());
            return ptModelConvertOnnxVO;
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_MODEL, "User {} failed to insert onnx model_branch to database because ???{}", user.getUsername(), e);
            throw new BusinessException("ONNX ??????????????????");
        }
    }

    /**
     * ?????? ONNX ??????
     *
     * @param modelPath ????????????
     * @param outputPath ???????????? ONNX ????????????
     * @return
     */
    private String generateOnnxModel(String modelPath, String outputPath) {
        JSONObject params = new JSONObject();
        params.put("model_path", modelPath);
        params.put("output_path", outputPath);
        RestTemplate restTemplate = restTemplateHolder.getRestTemplate();

        // ?????????????????????Python??????
        DataResponseBody result;
        try {
            result = restTemplate.postForObject(modelConverterUrl, params, DataResponseBody.class);
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_MODEL, "generate ONNX model fail,cause of exception msg {}", e.getMessage());
            throw new BusinessException(ModelConvertEnum.CONVERT_SERVER_ERROR.getMsg());
        }
        if (result != null) {
            if (result.succeed()) {
                return (String) result.getData();
            }
            throw new BusinessException(ModelConvertEnum.getModelConvertEnum(result.getCode()).getMsg());
        }
        return SymbolConstant.BLANK;
    }

    @Override
    public List<PtModelBranchQueryVO> listByBranchIds(List<Long> ids) {
        List<PtModelBranchQueryVO> ptModelBranchQueryVOS = Lists.newArrayList();

        // ??????????????????????????????
        QueryWrapper<PtModelBranch> modelBranchWrapper = new QueryWrapper<>();
        modelBranchWrapper.lambda().in(PtModelBranch::getId, ids);
        List<PtModelBranch> ptModelBranches= ptModelBranchMapper.selectList(modelBranchWrapper);
        if(CollectionUtils.isEmpty(ptModelBranches)){
            return ptModelBranchQueryVOS;
        }

        List<Long> modelIds = ptModelBranches.stream()
                .map(PtModelBranch::getParentId)
                .collect(Collectors.toList());
        List<PtModelInfo> ptModelInfos=ptModelInfoMapper.selectBatchIds(modelIds);
        Map<Long, String> modelNameMap=ptModelInfos.stream()
                .collect(Collectors.toMap(PtModelInfo::getId,PtModelInfo::getName));

        ptModelBranches.stream().forEach(ptModelBranch -> {
            PtModelBranchQueryVO ptModelBranchQueryVO = new PtModelBranchQueryVO();
            BeanUtils.copyProperties(ptModelBranch, ptModelBranchQueryVO);
            ptModelBranchQueryVO.setName(modelNameMap.get(ptModelBranchQueryVO.getParentId()));
            ptModelBranchQueryVOS.add(ptModelBranchQueryVO);
        });

        return ptModelBranchQueryVOS;
    }
}
