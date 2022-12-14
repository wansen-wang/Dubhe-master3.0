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

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.dubhe.biz.base.constant.*;
import org.dubhe.biz.base.vo.DatasetVO;
import org.dubhe.biz.file.utils.MinioUtil;
import org.dubhe.biz.file.api.FileStoreApi;
import org.dubhe.biz.file.api.impl.ShellFileStoreApiImpl;
import org.dubhe.biz.permission.annotation.DataPermissionMethod;
import org.dubhe.biz.base.context.DataContext;
import org.dubhe.biz.base.dto.CommonPermissionDataDTO;
import org.dubhe.biz.base.dto.PtTrainDataSourceStatusQueryDTO;
import org.dubhe.biz.base.enums.DatasetTypeEnum;
import org.dubhe.biz.base.enums.OperationTypeEnum;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.service.UserContextService;
import org.dubhe.biz.base.utils.StringUtils;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.permission.base.BaseService;
import org.dubhe.biz.db.utils.PageUtil;
import org.dubhe.biz.db.utils.WrapperHelp;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.permission.annotation.RolePermission;
import org.dubhe.biz.statemachine.dto.StateChangeDTO;
import org.dubhe.cloud.authconfig.utils.JwtUtils;
import org.dubhe.biz.base.vo.DatasetVO;
import org.dubhe.data.client.TrainServerClient;
import org.dubhe.data.constant.*;
import org.dubhe.data.dao.DatasetMapper;
import org.dubhe.data.dao.TaskMapper;
import org.dubhe.biz.base.vo.ProgressVO;
import org.dubhe.data.domain.bo.FileUploadBO;
import org.dubhe.data.domain.dto.*;
import org.dubhe.data.domain.entity.*;
import org.dubhe.data.domain.vo.*;
import org.dubhe.data.machine.constant.DataStateCodeConstant;
import org.dubhe.data.machine.constant.DataStateMachineConstant;
import org.dubhe.data.machine.enums.DataStateEnum;
import org.dubhe.data.machine.utils.StateIdentifyUtil;
import org.dubhe.data.machine.utils.StateMachineUtil;
import org.dubhe.data.pool.BasePool;
import org.dubhe.data.service.*;
import org.dubhe.data.service.task.DatasetRecycleFile;
import org.dubhe.data.util.GeneratorKeyUtil;
import org.dubhe.data.util.ZipUtil;
import org.dubhe.recycle.domain.dto.RecycleCreateDTO;
import org.dubhe.recycle.domain.dto.RecycleDetailCreateDTO;
import org.dubhe.recycle.enums.RecycleModuleEnum;
import org.dubhe.recycle.enums.RecycleResourceEnum;
import org.dubhe.recycle.enums.RecycleTypeEnum;
import org.dubhe.recycle.service.RecycleService;
import org.dubhe.recycle.utils.RecycleTool;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.dubhe.data.constant.Constant.*;
import static org.dubhe.data.constant.ErrorEnum.DATASET_PUBLIC_LIMIT_ERROR;

/**
 * @description ????????????????????????
 * @date 2020-04-10
 */
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
@Service
public class DatasetServiceImpl extends ServiceImpl<DatasetMapper, Dataset> implements DatasetService {

    @Autowired
    @Lazy
    private TaskService taskService;

    @Resource(name = "hostFileStoreApiImpl")
    private FileStoreApi fileStoreApi;

    /**
     * ?????????????????????
     */
    private static final Set<Integer> NEED_SYNC_STATUS = new HashSet<Integer>() {{
        add(DataStateCodeConstant.NOT_ANNOTATION_STATE);
        add(DataStateCodeConstant.MANUAL_ANNOTATION_STATE);
        add(DataStateCodeConstant.AUTO_TAG_COMPLETE_STATE);
        add(DataStateCodeConstant.ANNOTATION_COMPLETE_STATE);
        add(DataStateCodeConstant.NOT_SAMPLED_STATE);
        add(DataStateCodeConstant.TARGET_COMPLETE_STATE);
    }};

    /**
     * ????????????????????????????????????????????????
     */
    private static final Set<Integer> COMPLETE_STATUS = new HashSet<Integer>() {{
        add(DataStateCodeConstant.AUTO_TAG_COMPLETE_STATE);
        add(DataStateCodeConstant.ANNOTATION_COMPLETE_STATE);
        add(DataStateCodeConstant.TARGET_COMPLETE_STATE);
    }};


    /**
     * bucket
     */
    @Value("${minio.bucketName}")
    private String bucket;

    /**
     * ???????????????
     */
    @Value("${storage.file-store-root-path:/nfs/}")
    private String prefixPath;

    /**
     * esSearch??????
     */
    @Value("${es.index}")
    private String esIndex;

    /**
     * ??????????????????
     */
    @Autowired
    public FileService fileService;


    /**
     * ????????????????????????
     */
    @Resource
    @Lazy
    private LabelService labelService;

    /**
     * ???????????????
     */
    @Autowired
    private org.dubhe.data.util.FileUtil fileUtil;

    /**
     * ??????????????????????????????????????????
     */
    @Autowired
    @Lazy
    private DatasetVersionFileService datasetVersionFileService;

    /**
     * ??????????????????????????????
     */
    @Autowired
    @Lazy
    private DatasetVersionService datasetVersionService;

    /**
     * ?????????????????????????????????
     */
    @Autowired
    private StateIdentifyUtil stateIdentify;

    /**
     * ??????mapper
     */
    @Autowired
    private TaskMapper taskMapper;

    @Resource
    private TrainServerClient trainServiceClient;

    /**
     * ?????????????????????
     */
    @Autowired
    private DatasetLabelService datasetLabelService;


    @Autowired
    private DatasetGroupLabelService datasetGroupLabelService;

    /**
     * ??????????????????
     */
    @Autowired
    private RecycleService recycleService;

    /**
     * ???????????????
     */
    @Autowired
    private LabelGroupServiceImpl labelGroupService;

    /**
     * ??????????????????
     */
    @Autowired
    private UserContextService userContextService;

    /**
     * ?????????????????????
     */
    @Autowired
    private DatasetRecycleFile datasetRecycleFile;

    /**
     * ??????????????????
     */
    @Autowired
    private RecycleTool recycleTool;

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    /**
     * ??????????????????
     */
    @Resource
    private DataFileAnnotationService dataFileAnnotationService;

    /**
     * minIo???????????????
     */
    @Resource
    private MinioUtil minioUtil;

    @Autowired
    private GeneratorKeyUtil generatorKeyUtil;

    /**
     * ?????????
     */
    @Autowired
    private BasePool pool;

    @Value("${storage.file-store}")
    private String nfsIp;

    @Value("${data.server.userName}")
    private String userName;


    /**
     * ??????????????????????????????
     *
     * @param id ?????????id
     * @return Boolean ????????????????????????
     */
    @Override
    public Boolean checkPublic(Long id, OperationTypeEnum type) {
        Dataset dataset = baseMapper.selectById(id);
        return checkPublic(dataset, type);
    }

    /**
     * ??????????????????????????????
     *
     * @param dataset ?????????
     */
    @Override
    public Boolean checkPublic(Dataset dataset, OperationTypeEnum type) {
        if (Objects.isNull(dataset)) {
            return false;
        }
        if (DatasetTypeEnum.PUBLIC.getValue().equals(dataset.getType())) {
            //?????????????????????????????????
            if (OperationTypeEnum.UPDATE.equals(type)) {
                BaseService.checkAdminPermission();
                //?????????????????????????????????
            } else if (OperationTypeEnum.LIMIT.equals(type)) {
                throw new BusinessException(DATASET_PUBLIC_LIMIT_ERROR);
            } else {
                return true;
            }

        }
        return false;
    }

    /**
     * ??????????????????
     *
     * @param file ??????
     */
    @Override
    public void autoAnnotatingCheck(File file) {
        autoAnnotatingCheck(file.getDatasetId());
    }

    /**
     * ??????????????????
     *
     * @param datasetId ?????????id
     */
    public void autoAnnotatingCheck(Long datasetId) {
        LambdaQueryWrapper<Dataset> datasetQueryWrapper = new LambdaQueryWrapper<>();
        datasetQueryWrapper
                .eq(Dataset::getId, datasetId)
                .eq(Dataset::getStatus, DataStateCodeConstant.AUTOMATIC_LABELING_STATE);
        if (getBaseMapper().selectCount(datasetQueryWrapper) > MagicNumConstant.ZERO) {
            throw new BusinessException(ErrorEnum.AUTO_ERROR);
        }
    }

    /**
     * ???????????????
     *
     * @param datasetCreateDTO ????????????????????????
     * @param datasetId        ?????????id
     * @return boolean ??????????????????
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean update(DatasetCreateDTO datasetCreateDTO, Long datasetId) {
        if (!exist(datasetId)) {
            throw new BusinessException(ErrorEnum.DATASET_ABSENT);
        }
        checkPublic(datasetId, OperationTypeEnum.UPDATE);
        Dataset dataset = getBaseMapper().selectById(datasetId);
        int fileCount = fileService.getFileCountByDatasetId(datasetId);
        if (!dataset.getDataType().equals(datasetCreateDTO.getDataType())
                && fileCount > MagicNumConstant.ZERO && datasetCreateDTO.getDataType() != null) {
            throw new BusinessException(ErrorEnum.DATASET_TYPE_MODIFY_ERROR);
        }
        if (!dataset.getAnnotateType().equals(datasetCreateDTO.getAnnotateType())
                && dataset.getStatus() != MagicNumConstant.ZERO && datasetCreateDTO.getAnnotateType() != null) {
            throw new BusinessException(ErrorEnum.DATASET_ANNOTATION_MODIFY_ERROR);
        }
        Dataset newDataset = DatasetCreateDTO.update(datasetCreateDTO);
        newDataset.setId(datasetId);
        newDataset.setTop(dataset.isTop());
        newDataset.setImport(dataset.isImport());
        int count;
        try {
            count = getBaseMapper().updateById(newDataset);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorEnum.DATASET_NAME_DUPLICATED_ERROR, null, e);
        }
        if (count == MagicNumConstant.ZERO) {
            throw new BusinessException(ErrorEnum.DATA_ABSENT_OR_NO_AUTH);
        }
        //?????????????????????????????????
        doDatasetLabelByUpdate(dataset, datasetCreateDTO, datasetId);
        return true;
    }


    /**
     * ?????????????????????
     *
     * @param dataset ?????????
     * @param pre     ??????????????????
     * @return boolean ????????????
     */
    @Override
    public boolean updateStatus(Dataset dataset, DataStateEnum pre) {
        QueryWrapper<Dataset> datasetQueryWrapper = new QueryWrapper<>();
        datasetQueryWrapper.lambda().eq(Dataset::getId, dataset.getId());
        if (pre != null) {
            datasetQueryWrapper.lambda().eq(Dataset::getStatus, pre);
        }
        getBaseMapper().update(dataset, datasetQueryWrapper);
        return true;
    }

    /**
     * ????????????
     *
     * @param id ?????????id
     * @param to ??????????????????
     * @return boolean ????????????
     */
    @Override
    public boolean updateStatus(Long id, DataStateEnum to) {
        return updateStatus(id, null, to);
    }

    /**
     * ??????????????????
     *
     * @param id  ?????????id
     * @param pre ??????????????????
     * @param to  ??????????????????
     * @return boolean ????????????
     */
    public boolean updateStatus(Long id, DataStateEnum pre, DataStateEnum to) {
        Dataset dataset = new Dataset();
        dataset.setStatus(to.getCode());
        QueryWrapper<Dataset> datasetQueryWrapper = new QueryWrapper<>();
        datasetQueryWrapper.lambda().eq(Dataset::getId, id);
        getBaseMapper().update(dataset, datasetQueryWrapper);
        return true;
    }

    /**
     * ?????????????????????
     *
     * @param dataset ?????????
     * @param to      ??????????????????
     * @return boolean ????????????
     */
    @Override
    public boolean transferStatus(Dataset dataset, DataStateEnum to) {
        return transferStatus(dataset, null, to);
    }

    /**
     * ?????????????????????
     *
     * @param dataset ?????????
     * @param pre     ??????????????????
     * @param to      ??????????????????
     * @return boolean ????????????
     */
    public boolean transferStatus(Dataset dataset, DataStateEnum pre, DataStateEnum to) {
        if (dataset == null || to == null) {
            return false;
        }
        dataset.setStatus(to.getCode());
        return updateStatus(dataset, pre);
    }

    /**
     * ????????????
     *
     * @param label     ??????
     * @param datasetId ?????????id
     * @return Long     ??????id
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveLabel(Label label, Long datasetId) {
        if (label.getId() == null && StringUtils.isEmpty(label.getName())) {
            throw new BusinessException(ErrorEnum.LABEL_ERROR);
        }
        if (!exist(datasetId)) {
            throw new BusinessException(ErrorEnum.DATASET_ABSENT);
        }
        Dataset dataset = baseMapper.selectById(datasetId);
        if (Objects.isNull(dataset)) {
            throw new BusinessException(ErrorEnum.DATASET_ABSENT);
        }
        checkPublic(dataset, OperationTypeEnum.UPDATE);

        //??????????????????????????????
        DatatypeEnum enumValue = DatatypeEnum.getEnumValue(dataset.getDataType());
        List<Label> labelList = labelService.getPubLabels(enumValue.getValue());

        //?????????????????????
        if (labelService.checkoutLabelIsRepeat(datasetId, label.getName())) {
            throw new BusinessException(ErrorEnum.LABEL_NAME_REPEAT);
        }
        if (!CollectionUtils.isEmpty(labelList)) {
            Map<String, Long> labelNameMap = labelList.stream().collect(Collectors.toMap(Label::getName, Label::getId));
            if (!Objects.isNull(labelNameMap.get(label.getName()))) {
                datasetLabelService.insert(DatasetLabel.builder().datasetId(datasetId).labelId(labelNameMap.get(label.getName())).build());
                //datasetGroupLabelService.insert(DatasetGroupLabel.builder().labelGroupId(dataset.getLabelGroupId()).labelId(labelNameMap.get(label.getName())).build());
            } else {
                insertLabelData(label, datasetId);
            }
        } else {
            insertLabelData(label, datasetId);
        }

    }


    /**
     * ?????????????????????
     *
     * @param datasetId ?????????id
     * @return DatasetVO ???????????????
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public DatasetVO get(Long datasetId) {
        Dataset ds = baseMapper.selectById(datasetId);
        if (ds == null) {
            throw new BusinessException(ErrorEnum.DATASET_ABSENT);
        }
        if (checkPublic(ds, OperationTypeEnum.SELECT)) {
            DataContext.set(CommonPermissionDataDTO.builder().id(datasetId).type(true).build());
        }
        Map<Long, ProgressVO> statistics = fileService.listStatistics(Arrays.asList(ds));

        if (ds.getLabelGroupId() != null) {
            LabelGroup labelGroup = labelGroupService.getBaseMapper().selectById(ds.getLabelGroupId());
            DatasetVO datasetVO = buildDatasetVO(ds, labelGroup.getName(), labelGroup.getType());
            datasetVO.setProgress(statistics.get(datasetVO.getId()));
            setDatasetVOFileCount(datasetVO);
            return datasetVO;
        }

        DatasetVO datasetVO = buildDatasetVO(ds, null, null);
        setDatasetVOFileCount(datasetVO);
        return datasetVO;
    }

    private DatasetVO buildDatasetVO(Dataset dataset, String labelGroupName, Integer labelGroupType) {
        DatasetVO datasetVO = new DatasetVO();
        if (dataset == null) {
            return null;
        }
        datasetVO.setId(dataset.getId());
        datasetVO.setName(dataset.getName());
        datasetVO.setRemark(dataset.getRemark());
        datasetVO.setCreateTime(dataset.getCreateTime());
        datasetVO.setUpdateTime(dataset.getUpdateTime());
        datasetVO.setType(dataset.getType());
        datasetVO.setDataType(dataset.getDataType());
        datasetVO.setAnnotateType(dataset.getAnnotateType());
        datasetVO.setStatus(dataset.getStatus());
        datasetVO.setDecompressState(dataset.getDecompressState());
        datasetVO.setImport(dataset.isImport());
        datasetVO.setTop(dataset.isTop());
        datasetVO.setLabelGroupId(dataset.getLabelGroupId());
        datasetVO.setLabelGroupName(labelGroupName);
        datasetVO.setLabelGroupType(labelGroupType);
        datasetVO.setSourceId(dataset.getSourceId());
        datasetVO.setCurrentVersionName(dataset.getCurrentVersionName());
        datasetVO.setTemplateType(dataset.getTemplateType());
        datasetVO.setModule(dataset.getModule());
        return datasetVO;
    }

    /**
     * ???????????????FileCount??????
     *
     * @param datasetVO ???????????????
     */
    public void setDatasetVOFileCount(DatasetVO datasetVO) {
        datasetVO.setFileCount(datasetVersionFileService.getFileCountByDatasetIdAndVersion(new LambdaQueryWrapper<DatasetVersionFile>() {{
            eq(DatasetVersionFile::getDatasetId, datasetVO.getId());
            if ((datasetVO.getCurrentVersionName() == null)) {
                isNull(DatasetVersionFile::getVersionName);
            } else {
                eq(DatasetVersionFile::getVersionName, datasetVO.getCurrentVersionName());
            }
            ne(DatasetVersionFile::getStatus, DataStatusEnum.DELETE.getValue());
        }}));
    }

    /**
     * ???????????????
     *
     * @param datasetId           ?????????id
     * @param httpServletResponse ????????????
     */
    @Override
    public void download(Long datasetId, HttpServletResponse httpServletResponse) {
        Dataset ds = baseMapper.selectById(datasetId);
        if (ds == null) {
            return;
        }
        String zipFile = ZipUtil.zip(ds.getUri());
        fileStoreApi.download(zipFile, httpServletResponse);
    }

    /**
     * ???????????????
     *
     * @param datasetCreateDTO ???????????????
     * @return Long ?????????id
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long create(DatasetCreateDTO datasetCreateDTO) {
        Dataset dataset = DatasetCreateDTO.from(datasetCreateDTO);
        dataset.setOriginUserId(userContextService.getCurUserId());
        try {
            save(dataset);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorEnum.DATASET_NAME_DUPLICATED_ERROR);
        }
        //????????????????????????
        List<Label> labels = labelService.listByGroupId(datasetCreateDTO.getLabelGroupId());
        if (!CollectionUtils.isEmpty(labels)) {
            List<DatasetLabel> datasetLabels = labels.stream().map(a -> {
                DatasetLabel datasetLabel = new DatasetLabel();
                datasetLabel.setDatasetId(dataset.getId());
                datasetLabel.setLabelId(a.getId());
                return datasetLabel;
            }).collect(Collectors.toList());
            datasetLabelService.saveList(datasetLabels);
        }
        //??????????????????
        if (datasetCreateDTO.getPresetLabelType() != null) {
            presetLabel(datasetCreateDTO.getPresetLabelType(), dataset.getId());
        }
        if (DatatypeEnum.VIDEO.getValue().equals(datasetCreateDTO.getDataType())) {
            dataset.setStatus(DataStateCodeConstant.NOT_SAMPLED_STATE);
        }
        dataset.setUri(fileUtil.getDatasetAbsPath(dataset.getId()));
        if (datasetCreateDTO.getDataType().equals(DatatypeEnum.AUTO_IMPORT.getValue())) {
            //???????????????????????? 1.???????????????????????????????????????????????? 2.????????????????????????????????????
            datasetVersionService.insertOne(new DatasetVersion(dataset.getId(), DEFAULT_VERSION,
                    DatatypeEnum.getEnumValue(datasetCreateDTO.getDataType()).getMsg()));
            dataset.setStatus(DataStateCodeConstant.ANNOTATION_COMPLETE_STATE);
            dataset.setCurrentVersionName(DEFAULT_VERSION);
        }
        updateById(dataset);
        return dataset.getId();
    }

    /**
     * ??????????????????
     *
     * @param presetLabelType ??????????????????
     * @param datasetId       ?????????id
     */
    @Override
    public void presetLabel(Integer presetLabelType, Long datasetId) {
        List<Label> labels = labelService.listByType(presetLabelType);
        if (CollectionUtil.isNotEmpty(labels)) {
            List<DatasetLabel> datasetLabels = new ArrayList<>();
            labels.stream().forEach(label -> {
                datasetLabels.add(
                        DatasetLabel.builder()
                                .datasetId(datasetId)
                                .labelId(label.getId())
                                .build());
            });
            if (CollectionUtil.isNotEmpty(datasetLabels)) {
                datasetLabelService.saveList(datasetLabels);
            }
        }
    }

    /**
     * ???????????????
     *
     * @param datasetDeleteDTO ?????????????????????
     */
    @Override
    public void delete(DatasetDeleteDTO datasetDeleteDTO) {
        if (datasetDeleteDTO.getIds() == null || datasetDeleteDTO.getIds().length == MagicNumConstant.ZERO) {
            return;
        }
        for (Long id : datasetDeleteDTO.getIds()) {
            delete(id);
        }
    }

    /**
     * ???????????????????????????
     *
     * @param id ?????????id
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAll(Long id) {
        int count = baseMapper.updateStatusById(id, true);
        if (count <= MagicNumConstant.ZERO) {
            throw new BusinessException(ErrorEnum.DATA_ABSENT_OR_NO_AUTH);
        }
        //???????????????ID?????????????????????????????????
        labelService.updateStatusByDatasetId(id, true);

        //?????????????????? ????????????
        datasetVersionService.updateStatusByDatasetId(id, true);


    }

    /**
     * ??????????????????????????????????????????????????????,???????????????????????????????????????????????????????????????????????????????????????
     *
     * @param fileDeleteDTO ??????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(FileDeleteDTO fileDeleteDTO) {

        for (Long datasetId : fileDeleteDTO.getDatasetIds()) {
            Dataset dataset = getById(datasetId);
            checkPublic(dataset, OperationTypeEnum.UPDATE);
            //??????????????????????????????????????????
            datasetVersionFileService.deleteShip(
                    datasetId,
                    dataset.getCurrentVersionName(),
                    Arrays.asList(fileDeleteDTO.getFileIds())
            );

            if (dataset.getDataType().equals(DatatypeEnum.AUDIO.getValue())) {
                List<Long> versionFileIdsByFileIds = datasetVersionFileService
                        .getVersionFileIdsByFileIds(datasetId, Arrays.asList(fileDeleteDTO.getFileIds()));
                dataFileAnnotationService.deleteBatch(datasetId, versionFileIdsByFileIds);
            }

            //????????????????????????
            StateMachineUtil.stateChange(new StateChangeDTO() {{
                setObjectParam(new Object[]{dataset});
                setEventMethodName(DataStateMachineConstant.DATA_DELETE_FILES_EVENT);
                setStateMachineType(DataStateMachineConstant.DATA_STATE_MACHINE);
            }});
            if(dataset.getDataType().equals(MagicNumConstant.TWO) || dataset.getDataType().equals(MagicNumConstant.THREE)){
                fileService.deleteEsData(fileDeleteDTO.getFileIds());
            }
        }
    }


    /**
     * ???????????????
     *
     * @param id ?????????id
     */
    public void delete(Long id) {
        checkPublic(id, OperationTypeEnum.UPDATE);
        //??????????????????????????????
        Dataset dataset = baseMapper.selectById(id);
        if (dataset == null) {
            throw new BusinessException(ErrorEnum.DATASET_ABSENT);
        }
        if (dataset.getStatus().equals(DataStateCodeConstant.STRENGTHENING_STATE)) {
            throw new BusinessException(ErrorEnum.DATASET_ENHANCEMENT);
        }
        //????????????????????????????????? ??????COCO???
        if (DatasetTypeEnum.PUBLIC.getValue().compareTo(dataset.getType()) == 0 && Objects.isNull(dataset.getSourceId())) {
            throw new BusinessException(ErrorEnum.DATASET_NOT_OPERATIONS_BASE_DATASET);
        }

        //???????????????????????????
        List<DatasetVersionVO> datasetVersionVos = datasetVersionService.versionList(id);
        List<String> datasetVersionUrls = new ArrayList<>();
        datasetVersionVos.forEach(url -> {
            datasetVersionUrls.add(url.getVersionUrl());
            datasetVersionUrls.add(url.getVersionUrl() + StrUtil.SLASH + "ofrecord" + StrUtil.SLASH + "train");
        });
        if (CollectionUtil.isNotEmpty(datasetVersionUrls)) {
            //????????????url????????????
            PtTrainDataSourceStatusQueryDTO dto = new PtTrainDataSourceStatusQueryDTO();
            DataResponseBody<Map<String, Boolean>> trainDataSourceStatusData = trainServiceClient.getTrainDataSourceStatus(dto.setDataSourcePath(datasetVersionUrls));
            if (!trainDataSourceStatusData.succeed() || Objects.isNull(trainDataSourceStatusData.getData())) {
                throw new BusinessException(ErrorEnum.DATASET_VERSION_PTJOB_STATUS);
            }
            if (!trainDataSourceStatusData.getData().values().contains(false)) {
                ((DatasetServiceImpl) AopContext.currentProxy()).deleteAll(id);
            }
        } else {
            ((DatasetServiceImpl) AopContext.currentProxy()).deleteAll(id);
        }
        //??????????????????
        try {
            addRecycleDataByDeleteDataset(dataset);
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_DATASET, "MinIO delete the dataset file error", e);
        }
        if (dataset.getDataType().equals(DatatypeEnum.TEXT.getValue()) || dataset.getDataType().equals(DatatypeEnum.TABLE.getValue())) {
            DeleteByQueryRequest deleteRequest = new DeleteByQueryRequest(esIndex);
            deleteRequest.setQuery(new TermQueryBuilder("datasetId", dataset.getId().toString()));
            try {
                restHighLevelClient.deleteByQuery(deleteRequest, RequestOptions.DEFAULT);
            } catch (IOException e) {
                LogUtil.error(LogEnum.BIZ_DATASET, "delete es data error:{}", e);
            }
        }
    }


    /**
     * ??????????????????
     *
     * @param dataset ???????????????
     */
    private void addRecycleDataByDeleteDataset(Dataset dataset) {

        //??????????????????????????????????????????
        List<RecycleDetailCreateDTO> detailList = new ArrayList<>();
        detailList.add(RecycleDetailCreateDTO.builder()
                .recycleCondition(dataset.getId().toString())
                .recycleType(RecycleTypeEnum.TABLE_DATA.getCode())
                .recycleNote(RecycleTool.generateRecycleNote("?????? ?????????DB ??????????????????", dataset.getId()))
                .build());
        //??????????????????minio ????????????????????????
        if (!Objects.isNull(dataset.getUri())) {
            detailList.add(RecycleDetailCreateDTO.builder()
                    .recycleCondition(prefixPath + bucket + SymbolConstant.SLASH + dataset.getUri())
                    .recycleType(RecycleTypeEnum.FILE.getCode())
                    .recycleNote(RecycleTool.generateRecycleNote("?????? minio ??????????????????", dataset.getId()))
                    .build());
        }
        //??????????????????
        RecycleCreateDTO recycleCreateDTO = RecycleCreateDTO.builder()
                .recycleModule(RecycleModuleEnum.BIZ_DATASET.getValue())
                .recycleCustom(RecycleResourceEnum.DATASET_RECYCLE_FILE.getClassName())
                .restoreCustom(RecycleResourceEnum.DATASET_RECYCLE_FILE.getClassName())
                .recycleDelayDate(NumberConstant.NUMBER_1)
                .recycleNote(RecycleTool.generateRecycleNote("???????????????????????????", dataset.getName(), dataset.getId()))
                .detailList(detailList)
                .build();
        recycleService.createRecycleTask(recycleCreateDTO);
    }


    /**
     * ???????????????
     *
     * @param page            ????????????
     * @param datasetQueryDTO ????????????
     * @return MapMap<String, Object> ???????????????????????????
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public Map<String, Object> listVO(Page<Dataset> page, DatasetQueryDTO datasetQueryDTO) {
        String name = datasetQueryDTO.getName();
        if (StringUtils.isEmpty(name)) {
            return queryDatasets(page, datasetQueryDTO, null);
        }
        boolean nameFlag = PATTERN_NUM.matcher(name).matches();
        if (nameFlag) {
            DatasetQueryDTO queryCriteriaId = new DatasetQueryDTO();
            BeanUtils.copyProperties(datasetQueryDTO, queryCriteriaId);
            queryCriteriaId.setName(null);
            Set<Long> ids = new HashSet<>();
            ids.add(Long.parseLong(datasetQueryDTO.getName()));
            queryCriteriaId.setIds(ids);
            Map<String, Object> map = queryDatasets(page, queryCriteriaId, null);
            if (((List) map.get(RESULT)).size() > 0) {
                queryCriteriaId.setName(name);
                queryCriteriaId.setIds(null);
                return queryDatasets(page, queryCriteriaId, Long.parseLong(datasetQueryDTO.getName()));
            }
        }
        return queryDatasets(page, datasetQueryDTO, null);
    }

    /**
     * ?????????????????????
     *
     * @param page          ????????????
     * @param queryCriteria ????????????
     * @param datasetId     ?????????id
     * @return java.util.Map<java.lang.String, java.lang.Object> ???????????????
     */
    public Map<String, Object> queryDatasets(Page<Dataset> page, DatasetQueryDTO queryCriteria, Long datasetId) {
        queryCriteria.timeConvert();
        QueryWrapper<Dataset> datasetQueryWrapper = WrapperHelp.getWrapper(queryCriteria);
        datasetQueryWrapper.eq("deleted", MagicNumConstant.ZERO);
        if (datasetId != null) {
            datasetQueryWrapper.or().eq("id", datasetId);
        }
        if (StringUtils.isNotEmpty(queryCriteria.getSort()) && StringUtils.isNotEmpty(queryCriteria.getOrder())) {
            datasetQueryWrapper.orderByDesc("is_top").orderBy(
                    true,
                    SORT_ASC.equals(queryCriteria.getOrder().toLowerCase()),
                    StringUtils.humpToLine(queryCriteria.getSort())
            );
        } else {
            datasetQueryWrapper.orderByDesc("is_top", "update_time");
        }

        //???????????????????????????
        if (!Objects.isNull(queryCriteria.getType()) && queryCriteria.getType().compareTo(DatasetTypeEnum.PUBLIC.getValue()) == 0) {
            DataContext.set(CommonPermissionDataDTO.builder().id(datasetId).type(true).build());
        }
        page = getBaseMapper().listPage(page, datasetQueryWrapper);

        Map<Long, ProgressVO> statistics = newProgressVO(page.getRecords());

        List<DatasetVO> datasetVOS = new ArrayList<>();

        //?????????????????????
        if (!CollectionUtils.isEmpty(page.getRecords())) {
            List<Long> groupIds = page.getRecords().stream().map(a -> a.getLabelGroupId()).collect(Collectors.toList());
            Map<Long, List<LabelGroup>> groupListMap = new HashMap<>(groupIds.size());
            if (!CollectionUtils.isEmpty(groupIds)) {
                List<LabelGroup> labelGroups = labelGroupService.getBaseMapper().selectBatchIds(groupIds);
                if (!CollectionUtils.isEmpty(labelGroups)) {
                    groupListMap = labelGroups.stream().collect(Collectors.groupingBy(LabelGroup::getId));
                }
            }
            List<Dataset> records = page.getRecords();
            if (!CollectionUtils.isEmpty(records)) {
                for (Dataset dataset : records) {
                    DatasetVO datasetVO = buildDatasetVO(dataset, null, null);
                    if (dataset.getCurrentVersionName() != null) {
                        DatasetVersion datasetVersion = datasetVersionService
                                .getVersionByDatasetIdAndVersionName(dataset.getId(), dataset.getCurrentVersionName());
                        datasetVO.setDataConversion(datasetVersion.getDataConversion());
                    }
                    datasetVO.setProgress(statistics.get(datasetVO.getId()));
                    if (!Objects.isNull(groupListMap) && !Objects.isNull(dataset.getLabelGroupId()) &&
                            !Objects.isNull(groupListMap.get(dataset.getLabelGroupId()))) {
                        LabelGroup labelGroup = groupListMap.get(dataset.getLabelGroupId()).get(0);
                        datasetVO.setLabelGroupName(labelGroup.getName());
                        datasetVO.setLabelGroupType(labelGroup.getType());
                        datasetVO.setAutoAnnotation(labelGroup.getType() == MagicNumConstant.ONE);

                    }

                    datasetVOS.add(datasetVO);
                }
            }

        }

        //???????????????????????????
        if (CollectionUtil.isNotEmpty(datasetVOS)) {
            for (DatasetVO datasetVo : datasetVOS) {
                datasetVo.setFileCount(datasetVersionFileService.getFileCountByDatasetIdAndVersion(new LambdaQueryWrapper<DatasetVersionFile>() {{
                    eq(DatasetVersionFile::getDatasetId, datasetVo.getId());
                    if ((datasetVo.getCurrentVersionName() == null)) {
                        isNull(DatasetVersionFile::getVersionName);
                    } else {
                        eq(DatasetVersionFile::getVersionName, datasetVo.getCurrentVersionName());
                    }
                    ne(DatasetVersionFile::getStatus, DataStatusEnum.DELETE.getValue());
                }}));
            }
        }
        BaseService.removeContext();
        return PageUtil.toPage(page, datasetVOS);
    }

    /**
     * ?????????????????????????????????
     *
     * @param datasetIds ?????????id??????
     * @return Map<Long, ProgressVO> ?????????????????????
     */
    @Override
    public Map<Long, ProgressVO> progress(List<Long> datasetIds) {
        if (CollectionUtils.isEmpty(datasetIds)) {
            return Collections.emptyMap();
        }
        List<Dataset> datasets = new ArrayList<>();
        datasetIds.forEach(datasetId -> {
            Dataset dataset = getBaseMapper().selectById(datasetId);
            datasets.add(dataset);
        });
        return fileService.listStatistics(datasets);
    }

    /**
     * ???????????????????????????????????????
     *
     * @param datasets ???????????????
     * @return Map<Long, ProgressVO> ?????????????????????
     */
    public Map<Long, ProgressVO> newProgressVO(List<Dataset> datasets) {
        Map<Long, ProgressVO> res = new HashMap<>(datasets.size());
        datasets.forEach(dataset -> {
            ProgressVO progressVO = null;
            res.put(dataset.getId(), progressVO);
        });
        return res;
    }

    /**
     * ????????????
     *
     * @param datasetId          ?????????id
     * @param batchFileCreateDTO ???????????????
     */
    @Override
    public void uploadFiles(Long datasetId, BatchFileCreateDTO batchFileCreateDTO) {
        Dataset dataset = baseMapper.selectById(datasetId);
        List<Long> fileIds = saveDbForUploadFiles(datasetId, batchFileCreateDTO, batchFileCreateDTO.getIfImport());
        if(batchFileCreateDTO.getIfImport()!=null && batchFileCreateDTO.getIfImport()){
            importFileAnnotation(datasetId, fileIds);
        }
        transportTextToEsForUploadFiles(datasetId, fileIds,batchFileCreateDTO.getIfImport());
        //????????????????????????
        StateMachineUtil.stateChange(new StateChangeDTO() {{
            setObjectParam(new Object[]{dataset});
            setEventMethodName(DataStateMachineConstant.DATA_UPLOAD_FILES_EVENT);
            setStateMachineType(DataStateMachineConstant.DATA_STATE_MACHINE);
        }});
    }

    void importFileAnnotation(Long datasetId, List<Long> fileIds) {
        List<Long> versionFileIds = datasetVersionFileService.getVersionFileIdsByFileIds(datasetId, fileIds);
        List<FileUploadBO> fileUploadContent = datasetVersionFileService.getFileUploadContent(datasetId,fileIds);
        List<DataFileAnnotation> dataFileAnnotations = new ArrayList<>();
        fileUploadContent.forEach(fileUploadBO -> {
            String annPath = StringUtils.substringBeforeLast(fileUploadBO.getFileUrl(), ".");
            annPath = annPath.replace("/origin/","/annotation/").replace(bucket+"/","");
            try {
                JSONArray annJsonArray = JSONObject.parseArray((minioUtil.readString(bucket, annPath)));
                for (Object object : annJsonArray) {
                    JSONObject jsonObject = (JSONObject) object;
                    Long categoryId = Long.parseLong(jsonObject.getString("category_id"));
                    Double score = jsonObject.getString("score")==null ? null : Double.parseDouble(jsonObject.getString("score"));
                    DataFileAnnotation dataFileAnnotation = DataFileAnnotation.builder().fileName(fileUploadBO.getFileName())
                            .versionFileId(fileUploadBO.getVersionFileId())
                            .datasetId(datasetId)
                            .labelId(categoryId)
                            .prediction(score).build();
                    dataFileAnnotations.add(dataFileAnnotation);
                }
            } catch (Exception e) {
                LogUtil.error(LogEnum.BIZ_DATASET, "?????????????????????????????????:{}",e);
            }
        });
        if(!CollectionUtils.isEmpty(dataFileAnnotations)){
            Queue<Long> dataFileAnnotionIds = generatorKeyUtil.getSequenceByBusinessCode(Constant.DATA_FILE_ANNOTATION, dataFileAnnotations.size());
            for (DataFileAnnotation dataFileAnnotation : dataFileAnnotations) {
                dataFileAnnotation.setId(dataFileAnnotionIds.poll());
                dataFileAnnotation.setStatus(MagicNumConstant.ZERO);
                dataFileAnnotation.setInvariable(MagicNumConstant.ZERO);
            }
            dataFileAnnotationService.insertDataFileBatch(dataFileAnnotations);
        }
    }

    /**
     * ????????????????????????????????????
     *
     * @param datasetId
     * @param batchFileCreateDTO
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> saveDbForUploadFiles(Long datasetId, BatchFileCreateDTO batchFileCreateDTO,Boolean ifImport) {
        Dataset dataset = getBaseMapper().selectById(datasetId);
        if (null == dataset) {
            throw new BusinessException(ErrorEnum.DATA_ABSENT_OR_NO_AUTH, "id:" + datasetId, null);
        }
        checkPublic(datasetId, OperationTypeEnum.UPDATE);
        autoAnnotatingCheck(datasetId);
        List<File> list = fileService.saveFiles(datasetId, batchFileCreateDTO.getFiles());
        List<Long> fileIds = new ArrayList<>();
        list.forEach(file -> fileIds.add(file.getId()));
        if (!CollectionUtils.isEmpty(list)) {
            List<DatasetVersionFile> datasetVersionFiles = new ArrayList<>();
            for (File file : list) {
                DatasetVersionFile datasetVersionFile = new DatasetVersionFile(datasetId, dataset.getCurrentVersionName(), file.getId(), file.getName());
                if(ifImport != null && ifImport){
                    datasetVersionFile.setAnnotationStatus(FileTypeEnum.FINISHED.getValue());
                }
                datasetVersionFiles.add(datasetVersionFile);
            }
            datasetVersionFileService.insertList(datasetVersionFiles);
        }
        if (DataStateCodeConstant.NOT_ANNOTATION_STATE.equals(dataset.getStatus())
                || DataStateCodeConstant.MANUAL_ANNOTATION_STATE.equals(dataset.getStatus())) {
            return fileIds;
        }
        return fileIds;
    }

    /**
     * ??????????????????????????????????????????ES
     *
     * @param datasetId ?????????ID
     */
    public void transportTextToEsForUploadFiles(Long datasetId, List<Long> fileIds,Boolean ifImport) {
        Dataset dataset = getBaseMapper().selectById(datasetId);
        if (dataset.getDataType().equals(MagicNumConstant.TWO) || dataset.getDataType().equals(MagicNumConstant.THREE)) {
            fileService.transportTextToEs(dataset, fileIds,ifImport);
        }
    }

    /**
     * ????????????
     *
     * @param datasetId     ?????????id
     * @param batchFileCreateDTO ????????????
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void uploadVideo(Long datasetId, BatchFileCreateDTO batchFileCreateDTO) {
        batchFileCreateDTO.getFiles().forEach(fileCreateDTO -> {
            if (!exist(datasetId)) {
                throw new BusinessException(ErrorEnum.DATA_ABSENT_OR_NO_AUTH, "id:" + datasetId, null);
            }
            checkPublic(datasetId, OperationTypeEnum.UPDATE);
            autoAnnotatingCheck(datasetId);
//        fileService.isExistVideo(datasetId);
            List<FileCreateDTO> videoFile = new ArrayList<>();
            videoFile.add(fileCreateDTO);
            List<File> files = fileService.saveVideoFiles(datasetId, videoFile, DatatypeEnum.VIDEO.getValue(), PID_OF_VIDEO, null);
            //??????????????????redis?????????
            Task task = Task.builder()
                    .datasets(JSON.toJSONString(Collections.singletonList(datasetId)))
                    .files(JSON.toJSONString(Collections.EMPTY_LIST))
                    .labels(JSONArray.toJSONString(Collections.emptyList()))
                    .annotateType(MagicNumConstant.SIX)
                    .dataType(MagicNumConstant.ONE)
                    .datasetId(datasetId)
                    .type(MagicNumConstant.FIVE)
                    .url(fileCreateDTO.getUrl())
                    .targetId(files.get(0).getId())
                    .frameInterval(fileCreateDTO.getFrameInterval()).build();
            taskMapper.insert(task);
        });
        //?????????????????????
        StateChangeDTO stateChangeDTO = new StateChangeDTO();
        //????????????????????????????????????????????????
        Object[] objects = new Object[1];
        objects[0] = datasetId.intValue();
        stateChangeDTO.setObjectParam(objects);
        //?????????????????????????????????
        stateChangeDTO.setStateMachineType(DataStateMachineConstant.DATA_STATE_MACHINE);
        //????????????
        stateChangeDTO.setEventMethodName(DataStateMachineConstant.DATA_SAMPLED_EVENT);
        StateMachineUtil.stateChange(stateChangeDTO);
    }

    /**
     * ???????????????????????????
     *
     * @param id ?????????id
     * @return boolean ????????????
     */
    public boolean exist(Long id) {
        return getBaseMapper().selectById(id) != null;
    }

    /**
     * ???????????????????????????
     *
     * @param id          ?????????id
     * @param versionName ????????????
     */
    public void updateVersionName(Long id, String versionName) {
        baseMapper.updateVersionName(id, versionName);
    }


    /**
     * ???????????????????????????
     *
     * @param datasetIsVersionDTO ???????????????(?????????)??????
     * @return Map<String, Object> ???????????????(?????????)??????
     */
    @Override
    public Map<String, Object> dataVersionListVO(Page page, DatasetIsVersionDTO datasetIsVersionDTO) {
        Integer annotateType = AnnotateTypeEnum.getConvertAnnotateType(datasetIsVersionDTO.getAnnotateType());
        LambdaQueryWrapper<Dataset> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Dataset::getDeleted, MagicNumConstant.ZERO);
        lambdaQueryWrapper.isNotNull(Dataset::getCurrentVersionName);
        lambdaQueryWrapper.eq(ObjectUtil.isNotNull(annotateType),  Dataset::getAnnotateType, annotateType);
        lambdaQueryWrapper.eq(ObjectUtil.isNotNull(datasetIsVersionDTO.getModule()), Dataset::getModule,  datasetIsVersionDTO.getModule());
        lambdaQueryWrapper.in(CollectionUtil.isNotEmpty(datasetIsVersionDTO.getIds()), Dataset::getId, datasetIsVersionDTO.getIds());
        if (!BaseService.isAdmin()) {
            lambdaQueryWrapper.and(datasetLambdaQueryWrapper ->
                    datasetLambdaQueryWrapper.eq(Dataset::getType, DatasetTypeEnum.PUBLIC.getValue())
                            .or().eq(Dataset::getOriginUserId, userContextService.getCurUserId()));
        }
        List<Dataset> datasetList = baseMapper.selectList(lambdaQueryWrapper);
        return PageUtil.toPage(page, datasetList);
    }

    /**
     * ????????????
     *
     * @param datasetEnhanceRequestDTO ???????????????????????????
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void enhance(DatasetEnhanceRequestDTO datasetEnhanceRequestDTO) {
        //???????????????????????????
        Dataset dataset = getById(datasetEnhanceRequestDTO.getDatasetId());
        if (ObjectUtil.isNull(dataset)) {
            throw new BusinessException(ErrorEnum.DATASET_ABSENT);
        }
        if (CollectionUtil.isEmpty(datasetEnhanceRequestDTO.getTypes())) {
            throw new BusinessException(ErrorEnum.DATASET_LABEL_EMPTY);
        }
        //?????????????????????????????????
        if (!StringUtils.isBlank(dataset.getCurrentVersionName())) {
            if (datasetVersionService.getDatasetVersionSourceVersion(dataset).getDataConversion().equals(NumberConstant.NUMBER_4)) {
                throw new BusinessException(ErrorEnum.DATASET_PUBLISH_ERROR);
            }
        }
        if (fileService.getOriginalFileCountOfDataset(datasetEnhanceRequestDTO.getDatasetId()
                , dataset.getCurrentVersionName()) == MagicNumConstant.ZERO) {
            throw new BusinessException(ErrorEnum.DATASET_ORIGINAL_FILE_IS_EMPTY);
        }
        //????????????????????????????????????????????????????????????
        DataStateEnum dataStateEnum = stateIdentify.getStatus(
                datasetEnhanceRequestDTO.getDatasetId(),
                dataset.getCurrentVersionName(),
                true
        );
        if (dataStateEnum == null || !COMPLETE_STATUS.contains(dataStateEnum.getCode())) {
            throw new BusinessException(ErrorEnum.DATASET_NOT_ENHANCE);
        }
        // ??????????????????????????????
        List<DatasetVersionFile> datasetVersionFiles =
                datasetVersionFileService.getNeedEnhanceFilesByDatasetIdAndVersionName(
                        dataset.getId(),
                        dataset.getCurrentVersionName()
                );
        //????????????
        Task task = Task.builder()
                .status(TaskStatusEnum.INIT.getValue())
                .datasets(JSON.toJSONString(Arrays.asList(datasetEnhanceRequestDTO.getDatasetId())))
                .files(JSON.toJSONString(Collections.EMPTY_LIST))
                .dataType(dataset.getDataType())
                .labels(JSONArray.toJSONString(Collections.emptyList()))
                .annotateType(dataset.getAnnotateType())
                .finished(MagicNumConstant.ZERO)
                .total(datasetVersionFiles.size() * datasetEnhanceRequestDTO.getTypes().size())
                .enhanceType(JSON.toJSONString(datasetEnhanceRequestDTO.getTypes()))
                .datasetId(dataset.getId())
                .type(MagicNumConstant.THREE).build();
        taskMapper.insert(task);

        //???????????? ????????????????????????????????????/?????????????????? -> ???????????????
        StateMachineUtil.stateChange(StateChangeDTO.builder()
                .objectParam(
                        new Object[]{dataset.getId().intValue()})
                .eventMethodName(DataStateCodeConstant.AUTO_TAG_COMPLETE_STATE.compareTo(dataset.getStatus()) == 0
                        ? DataStateMachineConstant.DATA_STRENGTHENING_EVENT : DataStateMachineConstant.DATA_COMPLETE_STRENGTHENING_EVENT)
                .stateMachineType(DataStateMachineConstant.DATA_STATE_MACHINE)
                .build());
    }

    /**
     * ???????????????????????????
     *
     * @param datasetId ?????????ID
     * @return DatasetLabelEnum ?????????????????????
     */
    @Override
    public DatasetLabelEnum getDatasetLabelType(Long datasetId) {
        List<Integer> datasetLabelTypes = labelService.getDatasetLabelTypes(datasetId);
        if (CollectionUtil.isNotEmpty(datasetLabelTypes)) {
            if (datasetLabelTypes.contains(DatasetLabelEnum.MS_COCO.getType())) {
                return DatasetLabelEnum.MS_COCO;
            } else if (datasetLabelTypes.contains(DatasetLabelEnum.IMAGE_NET.getType())) {
                return DatasetLabelEnum.IMAGE_NET;
            } else if (datasetLabelTypes.contains(DatasetLabelEnum.AUTO.getType())) {
                return DatasetLabelEnum.AUTO;
            }
            return DatasetLabelEnum.CUSTOM;
        }
        return null;
    }

    /**
     * ???????????????????????????????????????
     *
     * @return DatasetCountVO ???????????????
     */
    @Override
    public DatasetCountVO queryDatasetsCount() {
        Long curUserId = JwtUtils.getCurUserId();
        if (curUserId == null) {
            throw new BusinessException("??????????????????????????????");
        }
        Integer publicCount = baseMapper.selectCountByPublic(DatasetTypeEnum.PUBLIC.getValue(), NumberConstant.NUMBER_0);
        Integer privateCount = baseMapper.selectCount(
                new LambdaQueryWrapper<Dataset>() {{
                    eq(Dataset::getType, DatasetTypeEnum.PRIVATE.getValue());
                    eq(Dataset::getDeleted, NumberConstant.NUMBER_0);
                    if (!BaseService.isAdmin()) {
                        eq(Dataset::getCreateUserId, curUserId);
                    }
                }}
        );
        return new DatasetCountVO(publicCount, privateCount);
    }

    /**
     * ???????????????ID?????????????????????
     *
     * @param datasetId ?????????ID
     * @return dataset ?????????
     */
    @Override
    public Dataset getOneById(Long datasetId) {
        return getById(datasetId);
    }

    /**
     * ?????????????????????
     *
     * @param datasetQueryWrapper
     * @return List ???????????????
     */
    @Override
    public List<Dataset> queryList(QueryWrapper<Dataset> datasetQueryWrapper) {
        return list(datasetQueryWrapper);
    }


    /**
     * ??????????????????????????????
     *
     * @param datasetCustomCreateDTO ??????????????????????????????????????????
     * @return Long ?????????ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long importDataset(DatasetCustomCreateDTO datasetCustomCreateDTO) {
        Dataset dataset = new Dataset(datasetCustomCreateDTO);
        dataset.setUri(fileUtil.getDatasetAbsPath(dataset.getId()));
        dataset.setOriginUserId(JwtUtils.getCurUserId());
        try {
            baseMapper.insert(dataset);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorEnum.DATASET_NAME_DUPLICATED_ERROR);
        }
        return dataset.getId();
    }


    /**
     * ???????????????
     *
     * @param datasetId ?????????id
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void topDataset(Long datasetId) {
        if (!exist(datasetId)) {
            throw new BusinessException(ErrorEnum.DATASET_ABSENT);
        }
        checkPublic(datasetId, OperationTypeEnum.UPDATE);
        Dataset dataset = getBaseMapper().selectById(datasetId);
        boolean isTop = dataset.isTop();
        if (isTop) {
            isTop = false;
        } else {
            isTop = true;
        }
        dataset.setTop(isTop);
        dataset.setUpdateTime(null);
        getBaseMapper().updateById(dataset);
    }

    /**
     * ?????????????????????
     *
     * @param datasetIds ?????????Id
     * @return Map<Long, IsImportVO> ?????????????????????
     */
    @Override
    public Map<Long, IsImportVO> determineIfTheDatasetIsAnImport(List<Long> datasetIds) {
        if (CollectionUtils.isEmpty(datasetIds)) {
            return Collections.emptyMap();
        }
        List<Dataset> datasets = new ArrayList<>();
        datasetIds.forEach(datasetId -> {
            Dataset dataset = getBaseMapper().selectById(datasetId);
            datasets.add(dataset);
        });
        Map<Long, IsImportVO> res = new HashMap<>(datasets.size());
        datasets.forEach(dataset -> {
            IsImportVO isImportVO = IsImportVO.builder().build();
            isImportVO.setStatus(dataset.getStatus());
            res.put(dataset.getId(), isImportVO);
        });
        return res;
    }


    /**
     * ????????????????????????
     *
     * @param dto ????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void allRollback(RecycleCreateDTO dto) {
        List<RecycleDetailCreateDTO> detailList = dto.getDetailList();
        if (CollectionUtil.isNotEmpty(detailList)) {
            for (RecycleDetailCreateDTO recycleDetailCreateDTO : detailList) {
                if (!Objects.isNull(recycleDetailCreateDTO) &&
                        RecycleTypeEnum.TABLE_DATA.getCode().compareTo(recycleDetailCreateDTO.getRecycleType()) == 0) {
                    Long datasetId = Long.valueOf(recycleDetailCreateDTO.getRecycleCondition());
                    //??????????????????ID???????????????????????????
                    Dataset dataset = baseMapper.selectById(datasetId);
                    if (!Objects.isNull(dataset) && Objects.isNull(dataset.getSourceId()) && DatasetTypeEnum.PUBLIC.getValue().compareTo(dataset.getType()) == 0) {
                        LogUtil.error(LogEnum.BIZ_DATASET, "???????????????ID???{} ????????????????????????");
                        throw new BusinessException(ErrorEnum.DATASET_PUBLIC_LIMIT_ERROR);
                    }
                    //?????????????????????
                    baseMapper.updateStatusById(datasetId, false);
                    //???????????????????????????
                    labelService.updateStatusByDatasetId(datasetId, false);
                    //???????????????????????????
                    datasetVersionService.updateStatusByDatasetId(datasetId, false);
                    return;
                }
            }

        }

    }


    /**
     * ???????????????????????? ????????????????????????
     *
     * @param datasetConvertPresetDTO ????????????????????????????????????
     */
    @Override
    @RolePermission
    public void convertPreset(DatasetConvertPresetDTO datasetConvertPresetDTO) {
        //1 ?????????????????????/????????????/????????????
        Dataset originDataset = verificationDatasetBaseInfo(datasetConvertPresetDTO);
        if (DatatypeEnum.AUTO_IMPORT.getValue().compareTo(originDataset.getAnnotateType()) != 0) {
            //3 ?????????????????????????????????????????????????????????
            List<Dataset> oldDatasets = baseMapper.selectList(new LambdaQueryWrapper<Dataset>().eq(Dataset::getSourceId, datasetConvertPresetDTO.getDatasetId()));
            //4 ??????????????? ??????????????????????????? ????????? ?????? ?????????minio????????????
            if (!Objects.isNull(oldDatasets)) {
                oldDatasets.forEach(oldDataset -> {
                    try {
                        addRecycleDataByDeleteDataset(oldDataset);
                    } catch (Exception e) {
                        LogUtil.error(LogEnum.BIZ_DATASET, "add recycle task error: {}", e);
                    }
                    oldDataset.setDeleted(true);
                    baseMapper.updateById(oldDataset);
                });
            }
        }
        //5 ???????????????????????????????????????????????????????????????
        Dataset targetDataset = buildTargetDataset(originDataset, datasetConvertPresetDTO);
        baseMapper.insert(targetDataset);
        targetDataset.setUri(fileUtil.getDatasetAbsPath(targetDataset.getId()));
        updateById(targetDataset);
        Task task = Task.builder()
                .status(TaskStatusEnum.INIT.getValue())
                .datasetId(datasetConvertPresetDTO.getDatasetId())
                .type(MagicNumConstant.ELEVEN)
                .status(MagicNumConstant.ZERO)
                .labels(JSONArray.toJSONString(Collections.emptyList()))
                .files(JSON.toJSONString(Collections.EMPTY_LIST))
                .targetId(targetDataset.getId())
                .versionName(datasetConvertPresetDTO.getVersionName())
                .build();
        taskService.createTask(task);
    }

    /**
     * ???????????????DB???MINIO??????
     *
     * @param originDataset ??????????????????
     * @param targetDataset ?????????????????????
     * @param versionFiles  ???????????????
     */
    @Override
    public void backupDatasetDBAndMinioData(Dataset originDataset, Dataset targetDataset, List<DatasetVersionFile> versionFiles) {
        LogUtil.info(LogEnum.BIZ_DATASET, "???????????????DB???MINIO?????? start");
        String versionName = SymbolConstant.BLANK;
        List<DatasetVersionFile> versionFilesSource = new ArrayList<>();
        versionFiles.forEach(versionFile -> {
            DatasetVersionFile datasetVersionFile = new DatasetVersionFile();
            BeanUtils.copyProperties(versionFile, datasetVersionFile);
            versionFilesSource.add(datasetVersionFile);
        });
        versionName = versionFilesSource.get(MagicNumConstant.ZERO).getVersionName();
        //6 ?????????????????????????????????
        datasetLabelService.backupDatasetLabelDataByDatasetId(originDataset.getId(), targetDataset);
        //7 ???????????????????????????
        datasetVersionService.backupDatasetVersionDataByDatasetId(originDataset, targetDataset, originDataset.getCurrentVersionName());
        if (!CollectionUtils.isEmpty(versionFiles)) {
            //8 ???????????????????????????
            List<File> files = fileService.backupFileDataByDatasetId(originDataset, targetDataset);
            if(targetDataset.getAnnotateType().equals(AnnotateTypeEnum.TEXT_CLASSIFICATION.getValue())
                    ||targetDataset.getAnnotateType().equals(AnnotateTypeEnum.TEXT_SEGMENTATION.getValue())
                    ||targetDataset.getAnnotateType().equals(AnnotateTypeEnum.NAMED_ENTITY_RECOGNITION.getValue())){
                Map<String, Long> fileNameMap = files.stream().collect(Collectors.toMap(File::getName, File::getId));
                datasetVersionService.insertEsData(versionName, versionName, originDataset.getId() ,targetDataset.getId(), fileNameMap);
            }
            //9 ?????????????????????????????????
            datasetVersionFileService.backupDatasetVersionFileDataByDatasetId(originDataset, targetDataset, versionFiles, files);
            //10 ??????????????????????????????
            dataFileAnnotationService.backupDataFileAnnotationDataByDatasetId(originDataset, targetDataset, versionFiles);
        }
        LogUtil.info(LogEnum.BIZ_DATASET, "???????????????DB end");
        //11 ??????MINIO????????????
        LogUtil.info(LogEnum.BIZ_DATASET, "??????MINIO?????? start");
        copyMinioData(originDataset, targetDataset, versionName, versionFilesSource);
    }


    /**
     * ????????????????????????
     *
     * @param oldDataset ???????????????
     */
    @Async
    public void clearOldDatasetData(Dataset oldDataset) {
        //???????????????minio????????????
        recycleTool.delTempInvalidResources(prefixPath + bucket + SymbolConstant.SLASH + oldDataset.getUri());
    }


    /**
     * ?????????????????????/????????????/????????????
     *
     * @param datasetConvertPresetDTO ????????????????????????????????????
     * @return ??????????????????
     */
    private Dataset verificationDatasetBaseInfo(DatasetConvertPresetDTO datasetConvertPresetDTO) {
        Dataset originDataset = baseMapper.selectOne(new LambdaQueryWrapper<Dataset>().eq(Dataset::getId, datasetConvertPresetDTO.getDatasetId()));
        if (Objects.isNull(originDataset)) {
            throw new BusinessException("??????????????????");
        }
        if (Objects.isNull(originDataset.getCurrentVersionName())) {
            throw new BusinessException("??????????????????");
        }
        if (DatasetTypeEnum.PRIVATE.getValue().compareTo(originDataset.getType()) != 0) {
            throw new BusinessException("??????????????????????????????????????????");
        }
        if (!(DataStateEnum.AUTO_TAG_COMPLETE_STATE.getCode().compareTo(originDataset.getStatus()) == 0 ||
                DataStateEnum.ANNOTATION_COMPLETE_STATE.getCode().compareTo(originDataset.getStatus()) == 0 ||
                DataStateEnum.TARGET_COMPLETE_STATE.getCode().compareTo(originDataset.getStatus()) == 0)) {
            throw new BusinessException("?????????????????????????????????");
        }
        return originDataset;
    }


    /**
     * ??????minio????????????
     *
     * @param originDataset ??????????????????
     * @param targetDataset ?????????????????????
     * @param versionName   ????????????
     */
    private void copyMinioData(Dataset originDataset, Dataset targetDataset, String versionName, List<DatasetVersionFile> versionFiles) {
        LogUtil.info(LogEnum.BIZ_DATASET, "??????minio???????????? start");
        try {
            //????????????
            List<String> annotationNames = new ArrayList<>();
            List<String> picNames = new ArrayList<>();
            //??????????????????????????????????????????URL
            List<Long> fileIds = new ArrayList<>();
            versionFiles.forEach(dataVersionFile -> fileIds.add(dataVersionFile.getFileId()));
            Set<File> files = fileService.get(fileIds, originDataset.getId());
            files.forEach(file -> {
                picNames.add(StringUtils.substringAfter(file.getUrl(), "/"));
                String fileName = StringUtils.substringBeforeLast(StringUtils.substringAfterLast(file.getUrl(), "/"), ".");
                String annotationUrl = originDataset.getUri() + SymbolConstant.SLASH + "versionFile" + SymbolConstant.SLASH +
                        versionName + SymbolConstant.SLASH + "annotation" + SymbolConstant.SLASH + fileName;
                annotationNames.add(annotationUrl);
            });
            String fileTargetDir = targetDataset.getUri() + java.io.File.separator + "origin";
            String fileTargetDirVersion = targetDataset.getUri() + java.io.File.separator + "versionFile" + java.io.File.separator
                    + targetDataset.getCurrentVersionName() + java.io.File.separator + "origin";
            String annotationTargetDir = targetDataset.getUri() + java.io.File.separator + "versionFile" + java.io.File.separator
                    + targetDataset.getCurrentVersionName() + java.io.File.separator + "annotation";
            minioUtil.copyDir(bucket, annotationNames, annotationTargetDir);
            minioUtil.copyDir(bucket, picNames, fileTargetDir);
            minioUtil.copyDir(bucket, picNames, fileTargetDirVersion);
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_DATASET, "????????????????????????!  error:{}", e);
            throw new BusinessException(ResponseCode.ERROR, e.getMessage());
        }
    }


    /**
     * ?????????????????????
     *
     * @param originDataset           ??????????????????
     * @param datasetConvertPresetDTO ????????????????????????
     * @return ?????????????????????
     */
    private Dataset buildTargetDataset(Dataset originDataset, DatasetConvertPresetDTO datasetConvertPresetDTO) {
        return Dataset.builder()
                .annotateType(originDataset.getAnnotateType())
                .dataType(originDataset.getDataType())
                .type(MagicNumConstant.TWO)
                .archiveUrl(originDataset.getArchiveUrl())
                .deleted(originDataset.getDeleted())
                .originUserId(MagicNumConstant.ZERO_LONG)
                .currentVersionName(DEFAULT_VERSION)
                .remark(originDataset.getRemark())
                .name(datasetConvertPresetDTO.getName())
                .sourceId(originDataset.getId())
                .isImport(originDataset.isImport())
                .status(originDataset.getStatus())
                .decompressState(originDataset.getDecompressState())
                .decompressFailReason(originDataset.getDecompressFailReason())
                .labelGroupId(originDataset.getLabelGroupId())
                .build();
    }


    /**
     * ???????????????ID?????????????????????????????????
     *
     * @param datasetId ?????????ID
     * @return true: ?????? false: ?????????
     */
    @Override
    @RolePermission
    public Boolean getConvertInfoByDatasetId(Long datasetId) {
        return !Objects.isNull(baseMapper.selectOne(new LambdaQueryWrapper<Dataset>()
                .eq(Dataset::getSourceId, datasetId).eq(Dataset::getDeleted, false)));
    }


    /**
     * ???????????????ID??????????????????
     *
     * @param datasetId ?????????ID
     */
    @Override
    public void deleteInfoById(Long datasetId) {
        baseMapper.deleteInfoById(datasetId);
    }


    /**
     * ??????????????????
     *
     * @param label     ????????????
     * @param datasetId ?????????ID
     */
    public void insertLabelData(Label label, Long datasetId) {
        labelService.insert(label);
        datasetLabelService.insert(DatasetLabel.builder().datasetId(datasetId).labelId(label.getId()).build());
    }


    /**
     * ?????????????????????????????????
     *
     * @param dataset          ???????????????
     * @param datasetCreateDTO ?????????????????????
     * @param datasetId        ????????????ID
     */
    private void doDatasetLabelByUpdate(Dataset dataset, DatasetCreateDTO datasetCreateDTO, Long datasetId) {
        //?????????????????????????????????????????????
        if (DataStateCodeConstant.NOT_ANNOTATION_STATE.compareTo(dataset.getStatus()) == 0) {
            List<Label> labels = labelService.listByGroupId(datasetCreateDTO.getLabelGroupId());
            if (!Objects.isNull(dataset.getLabelGroupId()) &&
                    !dataset.getLabelGroupId().equals(datasetCreateDTO.getLabelGroupId())) {
                //????????????????????????????????????
                datasetLabelService.del(datasetId);
                insertDatasetLabelAndUpdateDataset(labels, datasetCreateDTO, datasetId);
            } else if (Objects.isNull(dataset.getLabelGroupId()) &&
                    !Objects.isNull(datasetCreateDTO.getLabelGroupId())) {
                //?????????????????????????????????????????????
                insertDatasetLabelAndUpdateDataset(labels, datasetCreateDTO, datasetId);
            }
        } else if (!Objects.isNull(dataset.getLabelGroupId()) && !Objects.isNull(datasetCreateDTO.getLabelGroupId()) &&
                !dataset.getLabelGroupId().equals(datasetCreateDTO.getLabelGroupId())) {
            throw new BusinessException(ErrorEnum.LABELGROUP_IN_USE_STATUS);
        }
    }


    /**
     * ?????????????????????????????????????????????
     *
     * @param labels           ????????????
     * @param datasetCreateDTO ?????????????????????
     * @param datasetId        ????????????ID
     */
    private void insertDatasetLabelAndUpdateDataset(List<Label> labels, DatasetCreateDTO datasetCreateDTO, Long datasetId) {
        //????????????????????????ID
        baseMapper.updateById(Dataset.builder().id(datasetId).labelGroupId(datasetCreateDTO.getLabelGroupId()).build());
        //???????????????????????????
        if (!CollectionUtils.isEmpty(labels)) {
            datasetLabelService.saveList(
                    labels.stream()
                            .map(a -> DatasetLabel.builder().datasetId(datasetId)
                                    .labelId(a.getId()).build()
                            ).collect(Collectors.toList())
            );
        }
    }

    /**
     * ???????????????????????????
     *
     * @return Map<String, Object> ???????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Dataset> getPresetDataset() {
        QueryWrapper<Dataset> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("type", MagicNumConstant.TWO)
        .ne("deleted", MagicNumConstant.ONE);
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void taskStop(Long datasetId) {
        Dataset dataset = baseMapper.selectById(datasetId);
        checkDatasetForTask(dataset);
        // ??????????????????
        List<Task> tasks = taskService.selectRunningTask(datasetId);
        tasks.forEach(task -> {
            task.setStop(true);
            task.setStatus(MagicNumConstant.FOUR);
            taskService.updateByTaskId(task);
        });
        // ?????????????????????
        DataStateEnum status = stateIdentify.getStatusForRollback(datasetId, dataset.getCurrentVersionName());
        LambdaUpdateWrapper<Dataset> wrapper = new LambdaUpdateWrapper<Dataset>() {{
            eq(Dataset::getStatus, dataset.getStatus());
            eq(Dataset::getId, datasetId);
            set(Dataset::getStatus, status.getCode());
        }};
        update(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ofRecordStop(Long datasetId, String version) {
        DatasetVersion datasetVersion = datasetVersionService.getVersionByDatasetIdAndVersionName(datasetId, version);
        if(datasetVersion.getDataConversion()!=MagicNumConstant.FIVE){
            throw new BusinessException(ErrorEnum.DATASET_VERSION_STOP_OF_RECORD_ERROR);
        }
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(Task::getDatasetId,datasetId).eq(Task::getOfRecordVersion,version)
                .eq(Task::getType,MagicNumConstant.ONE).eq(Task::isStop, false).last(" limit 1");
        //??????????????????
        Task task = taskService.selectOne(queryWrapper);
        task.setStop(true);
        taskService.updateByTaskId(task);
        //??????????????????
        datasetVersion.setDataConversion(MagicNumConstant.ONE);
        datasetVersion.setOfRecord(MagicNumConstant.ZERO);
        datasetVersionService.updateByEntity(datasetVersion);
    }

    @Override
    public DatasetVO getPresetDatasetByName(String datasetName) {
        List<Dataset> datasets = baseMapper.selectList(new LambdaQueryWrapper<Dataset>()
                .eq(Dataset::getType, MagicNumConstant.TWO)
                .eq(Dataset::getName, datasetName));
        DatasetVO datasetVO = new DatasetVO();
        BeanUtil.copyProperties(datasets.get(0), datasetVO);
        return datasetVO;
    }

    public void checkDatasetForTask(Dataset dataset) {
        if (ObjectUtil.isEmpty(dataset)) {
            throw new BusinessException("??????????????????");
        }
        HashSet<Integer> set = new HashSet<Integer>() {{
            add(DataStateCodeConstant.AUTOMATIC_LABELING_STATE);
            add(DataStateCodeConstant.TARGET_FOLLOW_STATE);
            add(DataStateCodeConstant.SAMPLING_STATE);
            add(DataStateCodeConstant.STRENGTHENING_STATE);
            add(DataStateCodeConstant.IN_THE_IMPORT_STATE);
        }};
        if (!set.contains(dataset.getStatus())){
            throw new BusinessException("????????????????????????????????????");
        }
    }
}
