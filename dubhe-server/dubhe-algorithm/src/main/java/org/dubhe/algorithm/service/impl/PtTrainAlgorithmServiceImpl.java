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

package org.dubhe.algorithm.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.HashedMap;
import org.dubhe.algorithm.async.TrainAlgorithmUploadAsync;
import org.dubhe.algorithm.client.ImageClient;
import org.dubhe.algorithm.client.NoteBookClient;
import org.dubhe.algorithm.constant.AlgorithmConstant;
import org.dubhe.algorithm.constant.TrainAlgorithmConfig;
import org.dubhe.algorithm.dao.PtTrainAlgorithmMapper;
import org.dubhe.algorithm.domain.dto.PtTrainAlgorithmCreateDTO;
import org.dubhe.algorithm.domain.dto.PtTrainAlgorithmDeleteDTO;
import org.dubhe.algorithm.domain.dto.PtTrainAlgorithmQueryDTO;
import org.dubhe.algorithm.domain.dto.PtTrainAlgorithmUpdateDTO;
import org.dubhe.algorithm.domain.entity.PtTrainAlgorithm;
import org.dubhe.algorithm.domain.vo.PtTrainAlgorithmQueryVO;
import org.dubhe.algorithm.service.PtTrainAlgorithmService;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.base.constant.NumberConstant;
import org.dubhe.biz.base.constant.SymbolConstant;
import org.dubhe.biz.base.context.UserContext;
import org.dubhe.biz.base.dto.*;
import org.dubhe.biz.base.enums.AlgorithmSourceEnum;
import org.dubhe.biz.base.enums.AlgorithmStatusEnum;
import org.dubhe.biz.base.enums.DatasetTypeEnum;
import org.dubhe.biz.base.enums.ImageTypeEnum;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.service.UserContextService;
import org.dubhe.biz.base.utils.ReflectionUtils;
import org.dubhe.biz.base.utils.StringUtils;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.base.vo.ModelOptAlgorithmQureyVO;
import org.dubhe.biz.base.vo.TrainAlgorithmQureyVO;
import org.dubhe.biz.db.utils.PageUtil;
import org.dubhe.biz.file.api.FileStoreApi;
import org.dubhe.biz.file.enums.BizPathEnum;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.permission.annotation.DataPermissionMethod;
import org.dubhe.biz.permission.base.BaseService;
import org.dubhe.k8s.utils.K8sNameTool;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;


/**
 * @description ???????????? ???????????????
 * @date 2020-04-27
 */
@Service
public class PtTrainAlgorithmServiceImpl implements PtTrainAlgorithmService {

    @Autowired
    private PtTrainAlgorithmMapper ptTrainAlgorithmMapper;

    @Autowired
    private ImageClient imageClient;

    @Autowired
    private K8sNameTool k8sNameTool;

    @Autowired
    private TrainAlgorithmConfig trainAlgorithmConstant;

    @Autowired
    private NoteBookClient noteBookClient;

    @Autowired
    private TrainAlgorithmUploadAsync algorithmUpdateAsync;

    @Autowired
    private RecycleService recycleService;

    @Autowired
    private RecycleConfig recycleConfig;

    @Autowired
    private UserContextService userContext;

    @Resource(name = "hostFileStoreApiImpl")
    private FileStoreApi fileStoreApi;

    public final static List<String> FIELD_NAMES;

    static {
        FIELD_NAMES = ReflectionUtils.getFieldNames(PtTrainAlgorithmQueryVO.class);
    }

    /**
     * ??????????????????
     *
     * @param ptTrainAlgorithmQueryDTO ??????
     * @return Map<String, Object>  ??????????????????
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public Map<String, Object> queryAll(PtTrainAlgorithmQueryDTO ptTrainAlgorithmQueryDTO) {
        //??????????????????
        UserContext user = userContext.getCurUser();
        //???????????????????????????????????????????????????
        if (ptTrainAlgorithmQueryDTO.getAlgorithmSource() == null) {
            ptTrainAlgorithmQueryDTO.setAlgorithmSource(trainAlgorithmConstant.getAlgorithmSource());
        }
        QueryWrapper<PtTrainAlgorithm> wrapper = new QueryWrapper<>();
        wrapper.eq("algorithm_source", ptTrainAlgorithmQueryDTO.getAlgorithmSource());
        //??????????????????
        if (AlgorithmSourceEnum.MINE.getStatus().equals(ptTrainAlgorithmQueryDTO.getAlgorithmSource())) {
            if (!BaseService.isAdmin(user)) {
                wrapper.eq("create_user_id", userContext.getCurUserId());
            }
        }
        //????????????????????????
        if (ptTrainAlgorithmQueryDTO.getAlgorithmUsage() != null) {
            wrapper.like("algorithm_usage", ptTrainAlgorithmQueryDTO.getAlgorithmUsage());
        }
        if (!StringUtils.isEmpty(ptTrainAlgorithmQueryDTO.getAlgorithmName())) {
            wrapper.and(qw -> qw.eq("id", ptTrainAlgorithmQueryDTO.getAlgorithmName()).or().like("algorithm_name",
                    ptTrainAlgorithmQueryDTO.getAlgorithmName()));
        }

        Page page = ptTrainAlgorithmQueryDTO.toPage();
        IPage<PtTrainAlgorithm> ptTrainAlgorithms;
        try {
            if (ptTrainAlgorithmQueryDTO.getSort() != null && FIELD_NAMES.contains(ptTrainAlgorithmQueryDTO.getSort())) {
                if (AlgorithmConstant.SORT_ASC.equalsIgnoreCase(ptTrainAlgorithmQueryDTO.getOrder())) {
                    wrapper.orderByAsc(StringUtils.humpToLine(ptTrainAlgorithmQueryDTO.getSort()));
                } else {
                    wrapper.orderByDesc(StringUtils.humpToLine(ptTrainAlgorithmQueryDTO.getSort()));
                }
            } else {
                wrapper.orderByDesc(AlgorithmConstant.ID);
            }
            ptTrainAlgorithms = ptTrainAlgorithmMapper.selectPage(page, wrapper);
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_ALGORITHM, "Query training algorithm list display exceptions :{}, request information :{}", e,
                    ptTrainAlgorithmQueryDTO);
            throw new BusinessException("????????????????????????????????????");
        }
        List<PtTrainAlgorithmQueryVO> ptTrainAlgorithmQueryResult = ptTrainAlgorithms.getRecords().stream().map(x -> {
            PtTrainAlgorithmQueryVO ptTrainAlgorithmQueryVO = new PtTrainAlgorithmQueryVO();
            BeanUtils.copyProperties(x, ptTrainAlgorithmQueryVO);
            //???????????????????????????
            getImageNameAndImageTag(x, ptTrainAlgorithmQueryVO);
            return ptTrainAlgorithmQueryVO;
        }).collect(Collectors.toList());
        return PageUtil.toPage(page, ptTrainAlgorithmQueryResult);
    }

    /**
     * ????????????
     *
     * @param ptTrainAlgorithmCreateDTO ??????????????????
     * @return idList  ??????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> create(PtTrainAlgorithmCreateDTO ptTrainAlgorithmCreateDTO) {
        //??????????????????
        UserContext user = userContext.getCurUser();
        String imageName = ptTrainAlgorithmCreateDTO.getImageName();
        String imageTag = ptTrainAlgorithmCreateDTO.getImageTag();
        if (StringUtils.isNotBlank(imageName) && StringUtils.isNotBlank(imageTag)) {
            ptTrainAlgorithmCreateDTO.setImageName(imageName + SymbolConstant.COLON + imageTag);
        }
        //??????????????????DTO??????????????????
        setAlgorithmDtoDefault(ptTrainAlgorithmCreateDTO);
        //????????????
        String path = fileStoreApi.getBucket() + ptTrainAlgorithmCreateDTO.getCodeDir();
        if (!fileStoreApi.fileOrDirIsExist(fileStoreApi.getRootDir() + path)) {
            LogUtil.error(LogEnum.BIZ_ALGORITHM, "The user {} upload path {} does not exist", user.getUsername(), path);
            throw new BusinessException("??????????????????????????????");
        }
        //????????????
        PtTrainAlgorithm ptTrainAlgorithm = new PtTrainAlgorithm();
        BeanUtils.copyProperties(ptTrainAlgorithmCreateDTO, ptTrainAlgorithm);
        //??????????????????
        if (BaseService.isAdmin(user) && AlgorithmSourceEnum.PRE.getStatus().equals(ptTrainAlgorithmCreateDTO.getAlgorithmSource())) {
            ptTrainAlgorithm.setAlgorithmSource(AlgorithmSourceEnum.PRE.getStatus());
            ptTrainAlgorithm.setOriginUserId(0L);
        } else {
            ptTrainAlgorithm.setAlgorithmSource(AlgorithmSourceEnum.MINE.getStatus());
        }
        ptTrainAlgorithm.setCreateUserId(user.getId());

        //??????????????????
        QueryWrapper<PtTrainAlgorithm> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("algorithm_name", ptTrainAlgorithmCreateDTO.getAlgorithmName()).and(wrapper -> wrapper.eq("create_user_id", user.getId()).or().eq("origin_user_id", 0L));
        Integer countResult = ptTrainAlgorithmMapper.selectCount(queryWrapper);
        //????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
        if (countResult > 0) {
            if (ptTrainAlgorithmCreateDTO.getNoteBookId() != null) {
                String randomStr = RandomUtil.randomNumbers(MagicNumConstant.FOUR);
                ptTrainAlgorithm.setAlgorithmName(ptTrainAlgorithmCreateDTO.getAlgorithmName() + randomStr);
            } else {
                LogUtil.error(LogEnum.BIZ_ALGORITHM, "The algorithm name ({}) already exists", ptTrainAlgorithmCreateDTO.getAlgorithmName());
                throw new BusinessException("???????????????????????????????????????");
            }
        }
        //??????path???????????????????????????????????????????????????????????????????????????????????????
        if (path.toLowerCase().endsWith(AlgorithmConstant.COMPRESS_ZIP)) {
            unZip(user, path, ptTrainAlgorithm, ptTrainAlgorithmCreateDTO);
        }

        //??????????????????????????????????????????????????????????????????????????????
        if (ptTrainAlgorithmCreateDTO.getInference()) {
            //??????????????????????????????
            copyFile(user, path, ptTrainAlgorithm, ptTrainAlgorithmCreateDTO);
        }

        try {
            //????????????????????????????????????????????????????????????
            ptTrainAlgorithmMapper.insert(ptTrainAlgorithm);
            //?????????????????????
            ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            RequestContextHolder.setRequestAttributes(servletRequestAttributes, true);
            //????????????????????????
            algorithmUpdateAsync.createTrainAlgorithm(userContext.getCurUser(), ptTrainAlgorithm, ptTrainAlgorithmCreateDTO);
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_ALGORITHM, "The user {} saving algorithm was not successful. Failure reason :{}", user.getUsername(), e.getMessage());
            throw new BusinessException("?????????????????????");
        }
        return Collections.singletonList(ptTrainAlgorithm.getId());
    }

    /**
     * ????????????
     *
     * @param ptTrainAlgorithmUpdateDTO ??????????????????
     * @return PtTrainAlgorithmUpdateVO  ??????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> update(PtTrainAlgorithmUpdateDTO ptTrainAlgorithmUpdateDTO) {
        //??????????????????
        UserContext user = userContext.getCurUser();
        //????????????
        PtTrainAlgorithm ptTrainAlgorithm = ptTrainAlgorithmMapper.selectById(ptTrainAlgorithmUpdateDTO.getId());
        if (null == ptTrainAlgorithm) {
            LogUtil.error(LogEnum.BIZ_ALGORITHM, "It is illegal for the user {} to modify the algorithm with id {}", user.getUsername(), ptTrainAlgorithmUpdateDTO.getId());
            throw new BusinessException("??????????????????????????????????????????");
        }
        PtTrainAlgorithm updatePtAlgorithm = new PtTrainAlgorithm();
        updatePtAlgorithm.setId(ptTrainAlgorithm.getId()).setUpdateUserId(user.getId());
        //??????????????????????????????
        if (StringUtils.isNotBlank(ptTrainAlgorithmUpdateDTO.getAlgorithmName())) {
            //??????????????????
            QueryWrapper<PtTrainAlgorithm> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("algorithm_name", ptTrainAlgorithmUpdateDTO.getAlgorithmName())
                    .ne("id", ptTrainAlgorithmUpdateDTO.getId());
            Integer countResult = ptTrainAlgorithmMapper.selectCount(queryWrapper);
            if (countResult > 0) {
                LogUtil.error(LogEnum.BIZ_ALGORITHM, "The algorithm name ({}) already exists", ptTrainAlgorithmUpdateDTO.getAlgorithmName());
                throw new BusinessException("???????????????????????????????????????");
            }
            updatePtAlgorithm.setAlgorithmName(ptTrainAlgorithmUpdateDTO.getAlgorithmName());
        }
        //??????????????????????????????
        if (ptTrainAlgorithmUpdateDTO.getDescription() != null) {
            updatePtAlgorithm.setDescription(ptTrainAlgorithmUpdateDTO.getDescription());
        }
        //??????????????????????????????
        if (ptTrainAlgorithmUpdateDTO.getAlgorithmUsage() != null) {
            updatePtAlgorithm.setAlgorithmUsage(ptTrainAlgorithmUpdateDTO.getAlgorithmUsage());
        }
        //??????????????????????????????
        if (ptTrainAlgorithmUpdateDTO.getIsTrainOut() != null) {
            updatePtAlgorithm.setIsTrainOut(ptTrainAlgorithmUpdateDTO.getIsTrainOut());
        }
        //?????????????????????????????????
        if (ptTrainAlgorithmUpdateDTO.getIsVisualizedLog() != null) {
            updatePtAlgorithm.setIsVisualizedLog(ptTrainAlgorithmUpdateDTO.getIsVisualizedLog());
        }
        try {
            //????????????????????????????????????????????????????????????
            ptTrainAlgorithmMapper.updateById(updatePtAlgorithm);
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_ALGORITHM, "User {} failed to modify the algorithm. Pt_train_algorithm table modification operation failed. Failure reason :{}", user.getUsername(), e.getMessage());
            throw new BusinessException("????????????");
        }
        return Collections.singletonList(ptTrainAlgorithm.getId());
    }

    /**
     * ??????????????????????????????
     *
     * @param user             ??????
     * @param path             ????????????
     * @param ptTrainAlgorithm ????????????
     */
    private void copyFile(UserContext user, String path, PtTrainAlgorithm ptTrainAlgorithm, PtTrainAlgorithmCreateDTO ptTrainAlgorithmCreateDTO) {
        //????????????
        String targetPath = null;
        if (BaseService.isAdmin(user) && AlgorithmSourceEnum.PRE.getStatus().equals(ptTrainAlgorithmCreateDTO.getAlgorithmSource())) {
            targetPath = k8sNameTool.getPrePath(BizPathEnum.ALGORITHM, user.getId());
        } else {
            targetPath = k8sNameTool.getPath(BizPathEnum.ALGORITHM, user.getId());
        }
        boolean copyFile;
        if (fileStoreApi.isDirectory(fileStoreApi.getRootDir() + path)) {
            copyFile = fileStoreApi.copyPath(fileStoreApi.getRootDir() + path, fileStoreApi.getRootDir() + fileStoreApi.getBucket() + targetPath);
        } else {
            copyFile = fileStoreApi.copyFile(fileStoreApi.getRootDir() + path, fileStoreApi.getRootDir() + fileStoreApi.getBucket() + targetPath);
        }
        if (!copyFile) {
            LogUtil.error(LogEnum.BIZ_ALGORITHM, "User {} failed to inference copyFile", user.getUsername());
            throw new BusinessException("??????????????????");
        }
        //????????????
        ptTrainAlgorithm.setCodeDir(targetPath);
    }

    /**
     * ?????????zip?????????
     *
     * @param user             ??????
     * @param path             ????????????
     * @param ptTrainAlgorithm ????????????
     */
    private void unZip(UserContext user, String path, PtTrainAlgorithm ptTrainAlgorithm, PtTrainAlgorithmCreateDTO ptTrainAlgorithmCreateDTO) {
        //????????????
        String targetPath = null;
        if (BaseService.isAdmin(user) && AlgorithmSourceEnum.PRE.getStatus().equals(ptTrainAlgorithmCreateDTO.getAlgorithmSource())) {
            targetPath = k8sNameTool.getPrePath(BizPathEnum.ALGORITHM, user.getId());
        } else {
            targetPath = k8sNameTool.getPath(BizPathEnum.ALGORITHM, user.getId());
        }
        boolean unzip = fileStoreApi.unzip(path, fileStoreApi.getBucket() + targetPath);
        if (!unzip) {
            LogUtil.error(LogEnum.BIZ_ALGORITHM, "User {} failed to unzip", user.getUsername());
            throw new BusinessException("????????????");
        }
        //????????????
        ptTrainAlgorithm.setCodeDir(targetPath);
    }

    /**
     * ????????????
     *
     * @param ptTrainAlgorithmDeleteDTO ??????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public void deleteAll(PtTrainAlgorithmDeleteDTO ptTrainAlgorithmDeleteDTO) {
        //??????????????????
        UserContext user = userContext.getCurUser();
        Set<Long> idList = ptTrainAlgorithmDeleteDTO.getIds();
        //????????????
        QueryWrapper<PtTrainAlgorithm> query = new QueryWrapper<>();
        //????????????????????????????????????
        if (!BaseService.isAdmin(user)) {
            query.eq("algorithm_source", 1);
        }
        query.in("id", idList);
        List<PtTrainAlgorithm> algorithmList = ptTrainAlgorithmMapper.selectList(query);
        if (algorithmList.size() < idList.size()) {
            LogUtil.error(LogEnum.BIZ_ALGORITHM, "User {} delete algorithm failed, no permission to delete the corresponding data in the algorithm table", user.getUsername());
            throw new BusinessException("????????????ID????????????????????????");
        }
        int deleteCountResult = ptTrainAlgorithmMapper.deleteBatchIds(idList);
        if (deleteCountResult < idList.size()) {
            LogUtil.error(LogEnum.BIZ_ALGORITHM, "The user {} deletion algorithm failed, and the algorithm table deletion operation based on the ID array {} failed", user.getUsername(), ptTrainAlgorithmDeleteDTO.getIds());
            throw new BusinessException("?????????????????????");
        }
        //????????????noteBook??????algorithmId=0
        NoteBookAlgorithmQueryDTO noteBookAlgorithmQueryDTO = new NoteBookAlgorithmQueryDTO();
        List<Long> ids = new ArrayList<>();
        idList.stream().forEach(id -> {
            ids.add(id);
        });
        noteBookAlgorithmQueryDTO.setAlgorithmIdList(ids);
        DataResponseBody<List<Long>> dataResponseBody = noteBookClient.getNoteBookIdByAlgorithm(noteBookAlgorithmQueryDTO);
        if (dataResponseBody.succeed()) {
            List<Long> noteBookIdList = dataResponseBody.getData();
            if (!CollectionUtils.isEmpty(noteBookIdList)) {
                //????????????
                NoteBookAlgorithmUpdateDTO noteBookAlgorithmUpdateDTO = new NoteBookAlgorithmUpdateDTO();
                noteBookAlgorithmUpdateDTO.setNotebookIdList(noteBookIdList);
                noteBookAlgorithmUpdateDTO.setAlgorithmId(0L);
                noteBookClient.updateNoteBookAlgorithm(noteBookAlgorithmUpdateDTO);
            }
        }
        //???????????????????????????????????????
        for (PtTrainAlgorithm algorithm : algorithmList) {
            RecycleCreateDTO recycleCreateDTO = new RecycleCreateDTO();
            recycleCreateDTO.setRecycleModule(RecycleModuleEnum.BIZ_ALGORITHM.getValue())
                    .setRecycleDelayDate(recycleConfig.getAlgorithmValid())
                    .setRecycleNote(RecycleTool.generateRecycleNote("??????????????????", algorithm.getAlgorithmName(), algorithm.getId()))
                    .setRemark(algorithm.getId().toString())
                    .setRestoreCustom(RecycleResourceEnum.ALGORITHM_RECYCLE_FILE.getClassName());
            RecycleDetailCreateDTO detail = new RecycleDetailCreateDTO();
            detail.setRecycleType(RecycleTypeEnum.FILE.getCode())
                    .setRecycleCondition(fileStoreApi.formatPath(fileStoreApi.getRootDir() + fileStoreApi.getBucket() + algorithm.getCodeDir()));
            recycleCreateDTO.addRecycleDetailCreateDTO(detail);
            recycleService.createRecycleTask(recycleCreateDTO);
        }
    }

    /**
     * ??????????????????
     *
     * @return count  ????????????
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public Map<String, Object> getAlgorithmCount() {
        QueryWrapper<PtTrainAlgorithm> wrapper = new QueryWrapper();
        wrapper.eq("algorithm_source", AlgorithmSourceEnum.MINE.getStatus());
        Integer countResult = ptTrainAlgorithmMapper.selectCount(wrapper);
        return new HashedMap() {{
            put("count", countResult);
        }};
    }

    /**
     * ??????Id??????????????????(??????????????????????????????)
     *
     * @param trainAlgorithmSelectAllByIdDTO ??????id
     * @return TrainAlgorithmQureyVO??????????????????(??????????????????????????????)
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public TrainAlgorithmQureyVO selectAllById(TrainAlgorithmSelectAllByIdDTO trainAlgorithmSelectAllByIdDTO) {
        PtTrainAlgorithm ptTrainAlgorithm = ptTrainAlgorithmMapper.selectAllById(trainAlgorithmSelectAllByIdDTO.getId());
        TrainAlgorithmQureyVO trainAlgorithmQureyVO = new TrainAlgorithmQureyVO();
        BeanUtils.copyProperties(ptTrainAlgorithm, trainAlgorithmQureyVO);
        return trainAlgorithmQureyVO;
    }

    /**
     * ??????Id??????
     *
     * @param trainAlgorithmSelectByIdDTO ??????id
     * @return TrainAlgorithmQureyVO ??????????????????
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public TrainAlgorithmQureyVO selectById(TrainAlgorithmSelectByIdDTO trainAlgorithmSelectByIdDTO) {
        PtTrainAlgorithm ptTrainAlgorithm = ptTrainAlgorithmMapper.selectById(trainAlgorithmSelectByIdDTO.getId());
        TrainAlgorithmQureyVO trainAlgorithmQureyVO = new TrainAlgorithmQureyVO();
        if (ptTrainAlgorithm != null) {
            BeanUtils.copyProperties(ptTrainAlgorithm, trainAlgorithmQureyVO);
        }
        return trainAlgorithmQureyVO;
    }

    /**
     * ??????Id????????????
     *
     * @param trainAlgorithmSelectAllBatchIdDTO ??????ids
     * @return List<TrainAlgorithmQureyVO> ??????????????????
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public List<TrainAlgorithmQureyVO> selectAllBatchIds(TrainAlgorithmSelectAllBatchIdDTO trainAlgorithmSelectAllBatchIdDTO) {
        List<PtTrainAlgorithm> ptTrainAlgorithms = ptTrainAlgorithmMapper.selectAllBatchIds(trainAlgorithmSelectAllBatchIdDTO.getIds());
        List<TrainAlgorithmQureyVO> trainAlgorithmQureyVOS = ptTrainAlgorithms.stream().map(x -> {
            TrainAlgorithmQureyVO trainAlgorithmQureyVO = new TrainAlgorithmQureyVO();
            BeanUtils.copyProperties(x, trainAlgorithmQureyVO);
            return trainAlgorithmQureyVO;
        }).collect(Collectors.toList());
        return trainAlgorithmQureyVOS;
    }

    /**
     * ???????????????????????????
     *
     * @param trainAlgorithm          ??????URL
     * @param ptTrainAlgorithmQueryVO ?????????????????????
     */
    private void getImageNameAndImageTag(PtTrainAlgorithm trainAlgorithm, PtTrainAlgorithmQueryVO ptTrainAlgorithmQueryVO) {
        String image = trainAlgorithm.getImageName();
        if (StringUtils.isNotBlank(trainAlgorithm.getImageName())) {
            String[] imageNameSuffixArray = image.split(StrUtil.COLON);
            ptTrainAlgorithmQueryVO.setImageName(imageNameSuffixArray[0]);
            ptTrainAlgorithmQueryVO.setImageTag(imageNameSuffixArray[1]);
        }
    }


    /**
     * ????????????DTO????????????????????????
     *
     * @param dto ??????DTO
     **/
    private void setAlgorithmDtoDefault(PtTrainAlgorithmCreateDTO dto) {

        //??????fork????????????fork:?????????????????????
        if (dto.getFork() == null) {
            dto.setFork(trainAlgorithmConstant.getFork());
        }
        //??????inference?????????(inference:??????????????????????????????)
        if (dto.getInference() == null) {
            dto.setInference(trainAlgorithmConstant.getInference());
        }
        //??????????????????????????????
        if (dto.getIsTrainOut() == null) {
            dto.setIsTrainOut(trainAlgorithmConstant.getIsTrainOut());
        }
        //??????????????????????????????
        if (dto.getIsTrainModelOut() == null) {
            dto.setIsTrainModelOut(trainAlgorithmConstant.getIsTrainModelOut());
        }
        //?????????????????????????????????
        if (dto.getIsVisualizedLog() == null) {
            dto.setIsVisualizedLog(trainAlgorithmConstant.getIsVisualizedLog());
        }
    }

    /**
     * ????????????url
     *
     * @param baseImageDto ????????????
     * @return BaseImageDTO  ??????url
     **/
    private String getImageUrl(BaseImageDTO baseImageDto, UserContext user) {

        PtImageQueryUrlDTO ptImageQueryUrlDTO = new PtImageQueryUrlDTO();
        ptImageQueryUrlDTO.setImageTag(baseImageDto.getImageTag());
        ptImageQueryUrlDTO.setImageName(baseImageDto.getImageName());
        List<Integer> trainImageType = new ArrayList() {{
            add(ImageTypeEnum.TRAIN.getType());
        }};
        ptImageQueryUrlDTO.setImageTypes(trainImageType);
        DataResponseBody<String> dataResponseBody = imageClient.getImageUrl(ptImageQueryUrlDTO);
        if (!dataResponseBody.succeed()) {
            LogUtil.error(LogEnum.BIZ_TRAIN, " User {} gets image ,the imageName is {}, the imageTag is {}, and the result of dubhe-image service call failed", user.getUsername(), baseImageDto.getImageName(), baseImageDto.getImageTag());
            throw new BusinessException("????????????????????????");
        }
        String ptImage = dataResponseBody.getData();
        // ????????????
        if (StringUtils.isBlank(ptImage)) {
            LogUtil.error(LogEnum.BIZ_ALGORITHM, "User {} gets image ,the imageName is {}, the imageTag is {}, and the result of query image table (PT_image) is empty", user.getUsername(), baseImageDto.getImageName(), baseImageDto.getImageTag());
            throw new BusinessException("???????????????");
        }
        return ptImage;
    }

    /**
     * @param modelOptAlgorithmCreateDTO ??????????????????????????????
     * @return PtTrainAlgorithm ??????????????????
     */
    @Override
    public ModelOptAlgorithmQureyVO modelOptimizationUploadAlgorithm(ModelOptAlgorithmCreateDTO modelOptAlgorithmCreateDTO) {
        PtTrainAlgorithmCreateDTO ptTrainAlgorithmCreateDTO = new PtTrainAlgorithmCreateDTO();
        ptTrainAlgorithmCreateDTO.setAlgorithmName(modelOptAlgorithmCreateDTO.getName())
                .setCodeDir(modelOptAlgorithmCreateDTO.getPath()).setAlgorithmUsage("5001")
                .setIsTrainModelOut(false).setIsTrainOut(false).setIsVisualizedLog(false);
        List<Long> ids = create(ptTrainAlgorithmCreateDTO);
        PtTrainAlgorithm ptTrainAlgorithm = ptTrainAlgorithmMapper.selectById(ids.get(NumberConstant.NUMBER_0));
        ModelOptAlgorithmQureyVO modelOptAlgorithmQureyVO = new ModelOptAlgorithmQureyVO();
        BeanUtils.copyProperties(ptTrainAlgorithm, modelOptAlgorithmQureyVO);
        return modelOptAlgorithmQureyVO;
    }

    /**
     * ????????????????????????
     *
     * @param dto ????????????
     */
    @Override
    public void algorithmRecycleFileRollback(RecycleCreateDTO dto) {
        //??????????????????
        UserContext user = userContext.getCurUser();
        if (dto == null) {
            LogUtil.error(LogEnum.BIZ_ALGORITHM, "User {} restore algorithm failed to delete the file because RecycleCreateDTO is null", user.getUsername());
            throw new BusinessException("????????????");
        }
        Long algorithmId = Long.valueOf(dto.getRemark());
        PtTrainAlgorithm ptTrainAlgorithm = ptTrainAlgorithmMapper.selectAllById(algorithmId);
        QueryWrapper<PtTrainAlgorithm> wrapper = new QueryWrapper<>();
        wrapper.eq("algorithm_name", ptTrainAlgorithm.getAlgorithmName());
        if (ptTrainAlgorithmMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("???????????????");
        }
        try {
            ptTrainAlgorithmMapper.updateStatusById(algorithmId, false);
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_ALGORITHM, "User {} restore algorithm failed to delete the file because:{}", user.getUsername(), e);
            throw new BusinessException("????????????");
        }
    }

    /**
     * ?????????????????????
     *
     * @return List<PtTrainAlgorithmQueryVO> ???????????????????????????
     */
    @Override
    public List<PtTrainAlgorithmQueryVO> getInferenceAlgorithm() {
        //??????????????????
        UserContext user = userContext.getCurUser();
        QueryWrapper<PtTrainAlgorithm> wrapper = new QueryWrapper<>();
        List<PtTrainAlgorithm> ptTrainAlgorithms = ptTrainAlgorithmMapper.selectList(wrapper);
        List<PtTrainAlgorithmQueryVO> ptTrainAlgorithmQueryResult = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(ptTrainAlgorithms)) {
            ptTrainAlgorithmQueryResult = ptTrainAlgorithms.stream().map(x -> {
                PtTrainAlgorithmQueryVO ptTrainAlgorithmQueryVO = new PtTrainAlgorithmQueryVO();
                BeanUtils.copyProperties(x, ptTrainAlgorithmQueryVO);
                //???????????????????????????
                getImageNameAndImageTag(x, ptTrainAlgorithmQueryVO);
                return ptTrainAlgorithmQueryVO;
            }).collect(Collectors.toList());
        }

        //?????????????????????????????????????????????
        if (!BaseService.isAdmin(user)) {
            List<PtTrainAlgorithm> preAlgorithms = ptTrainAlgorithmMapper.selectPreAlgorithm();
            List<PtTrainAlgorithmQueryVO> preAlgorithmQueryResult = preAlgorithms.stream().map(x -> {
                PtTrainAlgorithmQueryVO ptTrainAlgorithmQueryVO = new PtTrainAlgorithmQueryVO();
                BeanUtils.copyProperties(x, ptTrainAlgorithmQueryVO);
                //???????????????????????????
                getImageNameAndImageTag(x, ptTrainAlgorithmQueryVO);
                return ptTrainAlgorithmQueryVO;
            }).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(preAlgorithmQueryResult)) {
                ptTrainAlgorithmQueryResult.addAll(preAlgorithmQueryResult);
            }
        }
        return ptTrainAlgorithmQueryResult;
    }

    @Override
    public List<Long> listIdByName(String algorithmName) {
        QueryWrapper<PtTrainAlgorithm> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .like(PtTrainAlgorithm::getAlgorithmName, algorithmName);
        List<PtTrainAlgorithm> ptTrainAlgorithms = ptTrainAlgorithmMapper.selectList(wrapper);
        List<Long> ids = Lists.newArrayList();
        if (CollectionUtils.isEmpty(ptTrainAlgorithms)) {
            return ids;
        }
        ids = ptTrainAlgorithms.stream()
                .map(PtTrainAlgorithm::getId)
                .collect(Collectors.toList());
        return ids;
    }

    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public TrainAlgorithmQureyVO findAlgorithmByName(String algorithmName) {

        TrainAlgorithmQureyVO atlasTrainAlgorithmVO = new TrainAlgorithmQureyVO();
        List<PtTrainAlgorithm> ptTrainAlgorithms = ptTrainAlgorithmMapper.selectList(new LambdaQueryWrapper<PtTrainAlgorithm>()
                .eq(PtTrainAlgorithm::getAlgorithmName, algorithmName));
        if (CollUtil.isNotEmpty(ptTrainAlgorithms)) {
            BeanUtils.copyProperties(ptTrainAlgorithms.get(0), atlasTrainAlgorithmVO);
            //???????????????????????????
            if (StrUtil.isNotBlank(ptTrainAlgorithms.get(0).getImageName())) {
                String imageNameSuffix = ptTrainAlgorithms.get(0).getImageName().substring(ptTrainAlgorithms.get(0).getImageName().lastIndexOf(StrUtil.SLASH) + MagicNumConstant.ONE);
                String[] imageNameSuffixArray = imageNameSuffix.split(StrUtil.COLON);
                atlasTrainAlgorithmVO.setImageName(imageNameSuffixArray[0]);
                atlasTrainAlgorithmVO.setImageTag(imageNameSuffixArray[1]);
            }
        }
        return atlasTrainAlgorithmVO;
    }

    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public List<PtTrainAlgorithmQueryVO> getAll(String algorithmUsage) {
        List<PtTrainAlgorithmQueryVO> all = new ArrayList<>();
        all.addAll(getAlgorithmListBySource(AlgorithmSourceEnum.MINE.getStatus(), algorithmUsage));
        all.addAll(getAlgorithmListBySource(AlgorithmSourceEnum.PRE.getStatus(), algorithmUsage));
        return all;
    }

    public List<PtTrainAlgorithmQueryVO> getAlgorithmListBySource(Integer algorithmSource, String algorithmUsage) {
        List<PtTrainAlgorithmQueryVO> result = new ArrayList<>();
        //??????????????????
        UserContext user = userContext.getCurUser();
        // ??????????????????
        QueryWrapper<PtTrainAlgorithm> ptTrainAlgorithmQueryWrapper = new QueryWrapper<>();
        //??????????????????
        if (AlgorithmSourceEnum.MINE.getStatus().equals(algorithmSource)) {
            if (!BaseService.isAdmin(user)) {
                ptTrainAlgorithmQueryWrapper.lambda().eq(PtTrainAlgorithm::getCreateUserId, userContext.getCurUserId());
            }
        }
        if (StringUtils.isNotEmpty(algorithmUsage)) {
            ptTrainAlgorithmQueryWrapper.lambda().eq(PtTrainAlgorithm::getAlgorithmUsage, algorithmUsage);
        }
        ptTrainAlgorithmQueryWrapper.lambda().eq(PtTrainAlgorithm::getAlgorithmStatus, AlgorithmStatusEnum.SUCCESS.getCode());
        ptTrainAlgorithmQueryWrapper.lambda().eq(PtTrainAlgorithm::getAlgorithmSource, algorithmSource);
        List<PtTrainAlgorithm> ptTrainAlgorithmList = ptTrainAlgorithmMapper.selectList(ptTrainAlgorithmQueryWrapper);
        if (CollectionUtil.isNotEmpty(ptTrainAlgorithmList)) {
            result = ptTrainAlgorithmList.stream().map(x -> {
                PtTrainAlgorithmQueryVO ptTrainAlgorithmQueryVO = new PtTrainAlgorithmQueryVO();
                BeanUtils.copyProperties(x, ptTrainAlgorithmQueryVO);
                //???????????????????????????
                getImageNameAndImageTag(x, ptTrainAlgorithmQueryVO);
                return ptTrainAlgorithmQueryVO;
            }).collect(Collectors.toList());
        }
        return result;
    }

}

