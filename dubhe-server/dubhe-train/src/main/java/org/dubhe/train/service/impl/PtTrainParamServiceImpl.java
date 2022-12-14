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

package org.dubhe.train.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.collections4.CollectionUtils;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.base.constant.StringConstant;
import org.dubhe.biz.base.constant.SymbolConstant;
import org.dubhe.biz.base.context.UserContext;
import org.dubhe.biz.base.dto.*;
import org.dubhe.biz.base.enums.DatasetTypeEnum;
import org.dubhe.biz.base.enums.ModelResourceEnum;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.service.UserContextService;
import org.dubhe.biz.base.utils.CommandUtil;
import org.dubhe.biz.base.utils.StringUtils;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.base.vo.NoteBookVO;
import org.dubhe.biz.base.vo.PtModelBranchQueryVO;
import org.dubhe.biz.base.vo.PtModelInfoQueryVO;
import org.dubhe.biz.base.vo.TrainAlgorithmQureyVO;
import org.dubhe.biz.db.utils.PageUtil;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.permission.annotation.DataPermissionMethod;
import org.dubhe.biz.permission.base.BaseService;
import org.dubhe.cloud.authconfig.service.AdminClient;
import org.dubhe.train.client.AlgorithmClient;
import org.dubhe.train.client.ModelBranchClient;
import org.dubhe.train.client.ModelInfoClient;
import org.dubhe.train.client.NoteBookClient;
import org.dubhe.train.config.TrainJobConfig;
import org.dubhe.train.dao.PtTrainParamMapper;
import org.dubhe.train.domain.dto.*;
import org.dubhe.train.domain.entity.PtTrainParam;
import org.dubhe.train.domain.vo.PtTrainParamQueryVO;
import org.dubhe.train.enums.ResourcesPoolTypeEnum;
import org.dubhe.train.inner.RunCommandInnerService;
import org.dubhe.train.service.PtTrainParamService;
import org.dubhe.train.utils.ImageUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @description ???????????????????????????
 * @date 2020-04-27
 */
@Service
public class PtTrainParamServiceImpl implements PtTrainParamService {

    @Autowired
    private PtTrainParamMapper ptTrainParamMapper;

    @Autowired
    private AlgorithmClient algorithmClient;

    @Autowired
    private ImageUtil imageUtil;

    @Autowired
    private ModelBranchClient modelBranchClient;

    @Autowired
    private ModelInfoClient modelInfoClient;

    @Autowired
    private UserContextService userContextService;

    @Resource
    private RunCommandInnerService runCommandInnerService;

    @Resource
    private NoteBookClient noteBookClient;

    @Resource
    private AdminClient adminClient;

    /**
     * ??????????????????
     *
     * @param ptTrainParamQueryDTO  ??????????????????????????????
     * @return Map<String, Object>  ??????????????????????????????
     **/
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public Map<String, Object> getTrainParam(PtTrainParamQueryDTO ptTrainParamQueryDTO) {
        Page page = ptTrainParamQueryDTO.toPage();
        //????????????????????????
        QueryWrapper<PtTrainParam> query = new QueryWrapper<>();
        //????????????????????????????????????
        if (ptTrainParamQueryDTO.getParamName() != null) {
            query.like("param_name", ptTrainParamQueryDTO.getParamName());
        }
        //????????????????????????
        if (ptTrainParamQueryDTO.getTrainType() != null) {
            query.eq("train_type", ptTrainParamQueryDTO.getTrainType());
        }
        //??????????????????
        if (ptTrainParamQueryDTO.getResourcesPoolType() != null) {
            query.eq("resources_pool_type", ptTrainParamQueryDTO.getResourcesPoolType());
        }
        IPage<PtTrainParam> ptTrainParams;
        try {
            if (ptTrainParamQueryDTO.getSort() == null || ptTrainParamQueryDTO.getSort().equalsIgnoreCase(TrainJobConfig.ALGORITHM_NAME)) {
                query.orderByDesc(StringConstant.ID);
            } else {
                if (StringConstant.SORT_ASC.equalsIgnoreCase(ptTrainParamQueryDTO.getOrder())) {
                    query.orderByAsc(StringUtils.humpToLine(ptTrainParamQueryDTO.getSort()));
                } else {
                    query.orderByDesc(StringUtils.humpToLine(ptTrainParamQueryDTO.getSort()));
                }
            }
            ptTrainParams = ptTrainParamMapper.selectPage(page, query);
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_TRAIN, "Query task parameter list shows exception {}", e);
            throw new BusinessException("????????????");
        }
        //??????????????????
        //????????????id
        Set<Long> algorithmIds = ptTrainParams.getRecords().stream().map(PtTrainParam::getAlgorithmId).collect(Collectors.toSet());
        List<PtTrainParamQueryVO> ptTrainParamQueryResult = new ArrayList<>();
        if (algorithmIds.size() < 1) {
            return PageUtil.toPage(page, ptTrainParamQueryResult);
        }
        TrainAlgorithmSelectAllBatchIdDTO trainAlgorithmSelectAllBatchIdDTO = new TrainAlgorithmSelectAllBatchIdDTO();
        trainAlgorithmSelectAllBatchIdDTO.setIds(algorithmIds);
        DataResponseBody<List<TrainAlgorithmQureyVO>> dataResponseBody = algorithmClient.selectAllBatchIds(trainAlgorithmSelectAllBatchIdDTO);
        if (!dataResponseBody.succeed()) {
            throw new BusinessException("??????????????????");
        }
        List<TrainAlgorithmQureyVO>  ptTrainAlgorithms = dataResponseBody.getData();

        Map<Long, String> idUserNameMap = new HashMap<>();
        List<Long> userIds = ptTrainParams.getRecords().stream().map(PtTrainParam::getCreateUserId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(userIds)) {
            DataResponseBody<List<UserDTO>> result = adminClient.getUserList(userIds);
            if (result.getData() != null) {
                idUserNameMap = result.getData().stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getUsername, (o, n) -> n));
            }
        }
        Map<Long, String> finalIdUserNameMap = idUserNameMap;
        //????????????id???????????????VO????????????map?????????
        Map<Long, TrainAlgorithmQureyVO> ptTrainAlgorithmMap = ptTrainAlgorithms.stream().collect(Collectors.toMap(TrainAlgorithmQureyVO::getId, Function.identity()));
        ptTrainParamQueryResult = ptTrainParams.getRecords().stream().map(x -> {
            PtTrainParamQueryVO ptTrainParamQueryVO = new PtTrainParamQueryVO();
            BeanUtils.copyProperties(x, ptTrainParamQueryVO);
            TrainAlgorithmQureyVO trainAlgorithmQureyVO = ptTrainAlgorithmMap.get(x.getAlgorithmId());
            ptTrainParamQueryVO.setAlgorithmName(trainAlgorithmQureyVO.getAlgorithmName());
            //???????????????????????????
            getImageNameAndImageTag(x, ptTrainParamQueryVO);

            BaseTrainJobDTO baseTrainJobDTO = new BaseTrainJobDTO();
            BeanUtils.copyProperties(ptTrainParamQueryVO, baseTrainJobDTO);
            if (ResourcesPoolTypeEnum.isGpuCode(ptTrainParamQueryVO.getResourcesPoolType())) {
                baseTrainJobDTO.setGpuNum(ptTrainParamQueryVO.getResourcesPoolNode());
            }
            String runCommand =  CommandUtil.buildPythonCommand(ptTrainParamQueryVO.getRunCommand(),
                    ptTrainParamQueryVO.getRunParams());
            ptTrainParamQueryVO.setDisplayRunCommand(runCommandInnerService.buildDisplayRunCommand(baseTrainJobDTO,ptTrainParamQueryVO.getCreateUserId(),
                    trainAlgorithmQureyVO.getIsTrainModelOut(), trainAlgorithmQureyVO.getIsTrainOut(),
                    trainAlgorithmQureyVO.getIsVisualizedLog(), runCommand));
            ptTrainParamQueryVO.setRunCommand(runCommand);
            //?????????????????????????????????
            if (BaseService.isAdmin(userContextService.getCurUser()) && x.getCreateUserId() != null) {
                ptTrainParamQueryVO.setCreateUserName(finalIdUserNameMap.getOrDefault(x.getCreateUserId(), null));
            }
            return ptTrainParamQueryVO;
        }).collect(Collectors.toList());
        return PageUtil.toPage(page, ptTrainParamQueryResult);
    }

    /**
     * ??????????????????
     *
     * @param ptTrainParamCreateDTO ????????????????????????
     * @return List<Long>           ??????????????????id??????
     **/
    @Override
    @Transactional(rollbackFor = Exception.class)
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public List<Long> createTrainParam(PtTrainParamCreateDTO ptTrainParamCreateDTO) {
        //????????????
        TrainAlgorithmQureyVO ptTrainAlgorithm = checkCreateTrainParam(ptTrainParamCreateDTO, userContextService.getCurUser());
        //?????????????????????1??????????????????2??????????????????
        Integer algorithmSource = ptTrainAlgorithm.getAlgorithmSource();
        //??????????????????
        PtTrainParam ptTrainParam = new PtTrainParam();
        //????????????
        BaseTrainParamDTO baseTrainParamDTO = new BaseTrainParamDTO();
        BeanUtil.copyProperties(ptTrainParamCreateDTO, baseTrainParamDTO);
        checkModel(userContextService.getCurUser(), baseTrainParamDTO);

        BeanUtils.copyProperties(ptTrainParamCreateDTO, ptTrainParam);
        //????????????
        String images = imageUtil.getImageUrl(ptTrainParamCreateDTO, userContextService.getCurUser());
        ptTrainParam.setImageName(images).setAlgorithmSource(algorithmSource).setCreateUserId(userContextService.getCurUserId());
        int insertResult = ptTrainParamMapper.insert(ptTrainParam);
        //??????????????????????????????????????????????????????????????????
        if (insertResult < 1) {
            LogUtil.error(LogEnum.BIZ_TRAIN, "The user {} saved the task parameters successfully, and the pt_train_param table insert operation failed", userContextService.getCurUser().getUsername());
            throw new BusinessException("????????????");
        }
        //????????????????????????id
        return Collections.singletonList(ptTrainParam.getId());
    }

    /**
     * ????????????????????????
     *
     * @param currentUser         ??????
     * @param baseTrainParamDTO   ????????????????????????
     */
    private void checkModel(UserContext currentUser, BaseTrainParamDTO baseTrainParamDTO) {

        Integer modelResource = baseTrainParamDTO.getModelResource();
        if (null == modelResource) {
            if (null == baseTrainParamDTO.getModelId() &&
                    StringUtils.isBlank(baseTrainParamDTO.getStudentModelIds()) &&
                    StringUtils.isBlank(baseTrainParamDTO.getStudentModelIds())) {
                return;
            } else {
                logErrorInfoOnModel(currentUser.getUsername());
            }
        }
        PtModelBranchQueryByIdDTO ptModelBranchQueryByIdDTO = new PtModelBranchQueryByIdDTO();
        PtModelInfoQueryByIdDTO ptModelInfoQueryByIdDTO = new PtModelInfoQueryByIdDTO();
        PtModelInfoConditionQueryDTO ptModelInfoConditionQueryDTO = new PtModelInfoConditionQueryDTO();
        switch (ModelResourceEnum.getType(modelResource)) {
            case MINE:
                if (null == baseTrainParamDTO.getModelBranchId() || null == baseTrainParamDTO.getModelId() ||
                        StringUtils.isNotBlank(baseTrainParamDTO.getTeacherModelIds()) ||
                        StringUtils.isNotBlank(baseTrainParamDTO.getStudentModelIds())) {
                    logErrorInfoOnModel(currentUser.getUsername());
                }
                ptModelBranchQueryByIdDTO.setId(baseTrainParamDTO.getModelBranchId());
                DataResponseBody<PtModelBranchQueryVO> dataResponseBody = modelBranchClient.getByBranchId(ptModelBranchQueryByIdDTO);
                PtModelBranchQueryVO ptModelBranch = null;
                if (dataResponseBody.succeed()) {
                    ptModelBranch = dataResponseBody.getData();
                }
                if (null == ptModelBranch || ptModelBranch.getParentId().compareTo(baseTrainParamDTO.getModelId()) != 0 ||
                        StringUtils.isBlank(ptModelBranch.getModelAddress())) {
                    logErrorInfoOnModel(currentUser.getUsername());
                }
                ptModelInfoQueryByIdDTO.setId(ptModelBranch.getParentId());
                DataResponseBody<PtModelInfoQueryVO> modelInfoDataResponseBody = modelInfoClient.getByModelId(ptModelInfoQueryByIdDTO);
                PtModelInfoQueryVO ptModelInfo = null;
                if (modelInfoDataResponseBody.succeed()) {
                    ptModelInfo = modelInfoDataResponseBody.getData();
                }
                if (null == ptModelInfo || ptModelInfo.getModelResource().compareTo(baseTrainParamDTO.getModelResource()) != 0) {
                    logErrorInfoOnModel(currentUser.getUsername());
                }
                break;
            case PRESET:
                if (null == baseTrainParamDTO.getModelId() || StringUtils.isNotBlank(baseTrainParamDTO.getTeacherModelIds()) ||
                        StringUtils.isNotBlank(baseTrainParamDTO.getStudentModelIds())) {
                    logErrorInfoOnModel(currentUser.getUsername());
                }
                ptModelInfoQueryByIdDTO.setId(baseTrainParamDTO.getModelId());
                DataResponseBody<PtModelInfoQueryVO> modelInfoPresetDataResponseBody = modelInfoClient.getByModelId(ptModelInfoQueryByIdDTO);
                PtModelInfoQueryVO ptModelInfoPreset = null;
                if (modelInfoPresetDataResponseBody.succeed()) {
                    ptModelInfoPreset = modelInfoPresetDataResponseBody.getData();
                }
                if (null == ptModelInfoPreset || StringUtils.isBlank(ptModelInfoPreset.getModelAddress()) ||
                        ptModelInfoPreset.getModelResource().compareTo(baseTrainParamDTO.getModelResource()) != 0) {
                    logErrorInfoOnModel(currentUser.getUsername());
                }
                break;
            case ATLAS:
                if (StringUtils.isBlank(baseTrainParamDTO.getTeacherModelIds()) || null != baseTrainParamDTO.getModelId()) {
                    logErrorInfoOnModel(currentUser.getUsername());
                }
                Set<Long> ids = new HashSet<>();
                Set<Long> teacherModelList = new HashSet<>();
                Arrays.stream(baseTrainParamDTO.getTeacherModelIds().trim().split(SymbolConstant.COMMA))
                        .forEach(id -> teacherModelList.add(Long.parseLong(id)));
                ids.addAll(teacherModelList);

                Set<Long> studentModelList = new HashSet<>();
                if (StringUtils.isNotBlank(baseTrainParamDTO.getStudentModelIds())) {
                    Arrays.stream(baseTrainParamDTO.getStudentModelIds().trim().split(SymbolConstant.COMMA))
                            .forEach(id -> studentModelList.add(Long.parseLong(id)));
                    ids.addAll(studentModelList);
                }
                if (ids.isEmpty()) {
                    logErrorInfoOnModel(currentUser.getUsername());
                }
                ptModelInfoConditionQueryDTO.setIds(ids);
                ptModelInfoConditionQueryDTO.setModelResource(baseTrainParamDTO.getModelResource());
                DataResponseBody<List<PtModelInfoQueryVO>> conditionQueryDataResponseBody = modelInfoClient.getConditionQuery(ptModelInfoConditionQueryDTO);
                List<PtModelInfoQueryVO> modelInfoList = null;
                if (conditionQueryDataResponseBody.succeed()) {
                    modelInfoList = conditionQueryDataResponseBody.getData();
                }
                if (null == modelInfoList || modelInfoList.size() < ids.size()) {
                    logErrorInfoOnModel(currentUser.getUsername());
                }
                break;
        }
    }

    /**
     * ????????????????????????????????????????????????
     *
     * @param username
     */
    private void logErrorInfoOnModel(String username) {
        LogUtil.error(LogEnum.BIZ_TRAIN, "User {} operating training param, error on model......", username);
        throw new BusinessException("?????????????????????");
    }

    /**
     * ??????????????????
     *
     * @param ptTrainParamUpdateDTO ????????????????????????
     * @return List<Long>           ??????????????????id??????
     **/
    @Override
    @Transactional(rollbackFor = Exception.class)
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public List<Long> updateTrainParam(PtTrainParamUpdateDTO ptTrainParamUpdateDTO) {
        //????????????
        checkUpdateTrainParam(ptTrainParamUpdateDTO, userContextService.getCurUser());
        //??????????????????
        PtTrainParam ptTrainParam = new PtTrainParam();
        //????????????
        BaseTrainParamDTO baseTrainParamDTO = new BaseTrainParamDTO();
        BeanUtil.copyProperties(ptTrainParamUpdateDTO, baseTrainParamDTO);
        checkModel(userContextService.getCurUser(), baseTrainParamDTO);

        BeanUtils.copyProperties(ptTrainParamUpdateDTO, ptTrainParam);
        ptTrainParam.setUpdateUserId(userContextService.getCurUserId());
        //????????????url
        String images = imageUtil.getImageUrl(ptTrainParamUpdateDTO, userContextService.getCurUser());
        //????????????url
        ptTrainParam.setImageName(images);
        if (ptTrainParamUpdateDTO.getRunParams() == null) {
            ptTrainParam.setRunParams(null);
        }
        try {
            //??????????????????????????????????????????????????????????????????
            ptTrainParamMapper.updateById(ptTrainParam);
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_TRAIN, "The user {} failed to modify the task parameters. The modify pt_train_param failed. The reason is {}", userContextService.getCurUser().getUsername(), e.getMessage());
            throw new BusinessException("????????????");
        }

        //????????????????????????id
        return Collections.singletonList(ptTrainParam.getId());
    }

    /**
     * ??????????????????
     *
     * @param ptTrainParamDeleteDTO ????????????????????????
     **/
    @Override
    @Transactional(rollbackFor = Exception.class)
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public void deleteTrainParam(PtTrainParamDeleteDTO ptTrainParamDeleteDTO) {
        Set<Long> idList = ptTrainParamDeleteDTO.getIds();
        //????????????
        checkDeleteTrainParam(ptTrainParamDeleteDTO, userContextService.getCurUser(), idList);
        //??????????????????
        int deleteCountResult = ptTrainParamMapper.deleteBatchIds(idList);
        //???????????????????????????,????????????????????????????????????
        if (deleteCountResult < idList.size()) {
            LogUtil.error(LogEnum.BIZ_TRAIN, "The user {} failed to delete the task parameter, and delete pt_train_param  failed, ids are {}", userContextService.getCurUser().getUsername(), ptTrainParamDeleteDTO.getIds());
            throw new BusinessException("????????????");
        }
    }

    /**
     * ???????????????????????????
     *
     * @param trainParam                   ??????URL
     * @param ptTrainParamQueryVO          ?????????????????????
     */
    private void getImageNameAndImageTag(PtTrainParam trainParam, PtTrainParamQueryVO ptTrainParamQueryVO) {
        if (StringUtils.isNotBlank(trainParam.getImageName())) {
            String imageNameSuffix = trainParam.getImageName().substring(trainParam.getImageName().lastIndexOf(StrUtil.SLASH) + MagicNumConstant.ONE);
            String[] imageNameSuffixArray = imageNameSuffix.split(StrUtil.COLON);
            ptTrainParamQueryVO.setImageName(imageNameSuffixArray[0]);
            ptTrainParamQueryVO.setImageTag(imageNameSuffixArray[1]);
        }
    }

    /**
     * ????????????????????????????????????
     *
     * @param ptTrainParamCreateDTO ????????????????????????
     * @param user                  ??????
     * @return PtTrainAlgorithm     ??????
     */
    private TrainAlgorithmQureyVO checkCreateTrainParam(PtTrainParamCreateDTO ptTrainParamCreateDTO, UserContext user) {

        if (ptTrainParamCreateDTO.getAlgorithmId() == null && ptTrainParamCreateDTO.getNotebookId() == null) {
            LogUtil.error(LogEnum.BIZ_TRAIN, "Neither algorithm's id  nor notebook's id can be null  at the same time");
            throw new BusinessException("??????ID??????notebookId?????????????????????");
        }

        TrainAlgorithmQureyVO ptTrainAlgorithm = new TrainAlgorithmQureyVO();
        if (ptTrainParamCreateDTO.getAlgorithmId() != null) {
            //??????id??????
            TrainAlgorithmSelectAllByIdDTO trainAlgorithmSelectAllByIdDTO = new TrainAlgorithmSelectAllByIdDTO();
            trainAlgorithmSelectAllByIdDTO.setId(ptTrainParamCreateDTO.getAlgorithmId());
            DataResponseBody<TrainAlgorithmQureyVO> dataResponseBody = algorithmClient.selectAllById(trainAlgorithmSelectAllByIdDTO);
            if (dataResponseBody.succeed()) {
                ptTrainAlgorithm = dataResponseBody.getData();
            }
            if (ptTrainAlgorithm == null) {
                LogUtil.error(LogEnum.BIZ_TRAIN, "Algorithm ID  {} does not exist", ptTrainParamCreateDTO.getAlgorithmId());
                throw new BusinessException("??????????????????????????????");
            }
        } else if (ptTrainParamCreateDTO.getNotebookId() != null) {
            //notebook ??????
            NoteBookVO noteBook = getNoteBook(ptTrainParamCreateDTO.getNotebookId());
            ptTrainAlgorithm.setImageName(noteBook.getK8sImageName());
            ptTrainAlgorithm.setIsTrainOut(true);
            ptTrainAlgorithm.setIsTrainModelOut(true);
            //?????????????????????????????? python ???????????????????????????????????????
            ptTrainAlgorithm.setIsVisualizedLog(false);
            ptTrainAlgorithm.setCodeDir(noteBook.getK8sPvcPath());
        }

        //????????????????????????
        QueryWrapper<PtTrainParam> query = new QueryWrapper<>();
        query.eq("param_name", ptTrainParamCreateDTO.getParamName());
        Integer trainParamCountResult = ptTrainParamMapper.selectCount(query);
        if (trainParamCountResult > 0) {
            LogUtil.error(LogEnum.BIZ_TRAIN, "The task parameter name {} already exists", ptTrainParamCreateDTO.getParamName());
            throw new BusinessException("???????????????????????????");
        }
        return ptTrainAlgorithm;
    }

    /**
     * ??????notebook
     *
     * @param id
     * @return
     */
    private NoteBookVO getNoteBook(Long id) {
        DataResponseBody<NoteBookVO> dataResponseBody = noteBookClient.getNoteBook(id);
        if (dataResponseBody.succeed()) {
            NoteBookVO data = dataResponseBody.getData();
            if (data == null) {
                LogUtil.info(LogEnum.BIZ_TRAIN, "There is no such notebook, id is ", id);
                throw new BusinessException("??????NoteBook");
            }
            return dataResponseBody.getData();
        } else {
            LogUtil.info(LogEnum.BIZ_TRAIN, "NoteBook service unreachable! Msg is {}", dataResponseBody.getMsg());
            throw new BusinessException("NoteBook????????????????????????????????????~");
        }
    }

    /**
     * ????????????????????????????????????
     *
     * @param ptTrainParamUpdateDTO ????????????????????????
     * @param user                  ??????
     */
    private void checkUpdateTrainParam(PtTrainParamUpdateDTO ptTrainParamUpdateDTO, UserContext user) {
        //????????????id??????
        PtTrainParam ptTrainParam = ptTrainParamMapper.selectById(ptTrainParamUpdateDTO.getId());
        if (ptTrainParam == null) {
            LogUtil.error(LogEnum.BIZ_TRAIN, "The task parameter ID {} does not exist", ptTrainParamUpdateDTO.getId());
            throw new BusinessException("??????????????????????????????");
        }
        //??????id??????
        TrainAlgorithmSelectByIdDTO trainAlgorithmSelectByIdDTO = new TrainAlgorithmSelectByIdDTO();
        trainAlgorithmSelectByIdDTO.setId(ptTrainParamUpdateDTO.getAlgorithmId());
        DataResponseBody<TrainAlgorithmQureyVO> dataResponseBody = algorithmClient.selectById(trainAlgorithmSelectByIdDTO);
        TrainAlgorithmQureyVO ptTrainAlgorithm = null;
        if (dataResponseBody.succeed()) {
            ptTrainAlgorithm = dataResponseBody.getData();
        }
        if (ptTrainAlgorithm == null) {
            LogUtil.error(LogEnum.BIZ_TRAIN, "Algorithm ID {} does not exist", ptTrainParamUpdateDTO.getAlgorithmId());
            throw new BusinessException("??????id?????????");
        }
        //????????????
        QueryWrapper<PtTrainParam> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", ptTrainParamUpdateDTO.getId());
        Integer countResult = ptTrainParamMapper.selectCount(queryWrapper);
        if (countResult < 1) {
            LogUtil.error(LogEnum.BIZ_TRAIN, "The user {} failed to modify the task parameters and has no permission to modify the corresponding data in the pt_train_param table", user.getUsername());
            throw new BusinessException("????????????ID????????????????????????");
        }
        //????????????????????????
        QueryWrapper<PtTrainParam> query = new QueryWrapper<>();
        query.eq("param_name", ptTrainParamUpdateDTO.getParamName());
        PtTrainParam trainParam = ptTrainParamMapper.selectOne(query);
        if (trainParam != null && !ptTrainParamUpdateDTO.getId().equals(trainParam.getId())) {
            LogUtil.error(LogEnum.BIZ_TRAIN, "The task parameter name {} already exists", ptTrainParamUpdateDTO.getParamName());
            throw new BusinessException("???????????????????????????");
        }
    }

    /**
     * ????????????????????????????????????
     *
     * @param ptTrainParamDeleteDTO ????????????????????????
     * @param user                  ??????
     * @param idList                ????????????id??????
     **/
    private void checkDeleteTrainParam(PtTrainParamDeleteDTO ptTrainParamDeleteDTO, UserContext user, Set<Long> idList) {
        //id??????
        List<PtTrainParam> ptTrainParams = ptTrainParamMapper.selectBatchIds(idList);
        if (ptTrainParams.size() == 0 || ptTrainParams.size() != idList.size()) {
            LogUtil.error(LogEnum.BIZ_TRAIN, "User {} failed to delete the task parameters, request parameters ids ={} cannot query the corresponding data in pt_train_param table, the parameters are illegal", user.getUsername(), ptTrainParamDeleteDTO.getIds());
            throw new BusinessException("????????????ID????????????????????????");
        }
        //????????????
        QueryWrapper<PtTrainParam> query = new QueryWrapper<>();
        query.in("id", idList);
        Integer queryCountResult = ptTrainParamMapper.selectCount(query);
        if (queryCountResult < idList.size()) {
            LogUtil.error(LogEnum.BIZ_TRAIN, "User {} failed to delete the task parameters and has no permission to delete the corresponding data in the pt_train_param table", user.getUsername());
            throw new BusinessException("????????????ID????????????????????????");
        }
    }

}
