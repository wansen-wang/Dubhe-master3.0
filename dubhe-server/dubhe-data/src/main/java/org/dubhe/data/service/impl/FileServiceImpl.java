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
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.dubhe.biz.file.api.FileStoreApi;
import org.dubhe.biz.file.dto.FileDTO;
import org.dubhe.biz.file.dto.FilePageDTO;
import org.dubhe.biz.file.utils.MinioUtil;
import org.dubhe.biz.permission.annotation.DataPermissionMethod;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.base.constant.NumberConstant;
import org.dubhe.biz.base.constant.SymbolConstant;
import org.dubhe.biz.base.context.DataContext;
import org.dubhe.biz.base.dto.CommonPermissionDataDTO;
import org.dubhe.biz.base.enums.DatasetTypeEnum;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.service.UserContextService;
import org.dubhe.biz.db.utils.PageUtil;
import org.dubhe.biz.db.utils.WrapperHelp;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.redis.utils.RedisUtils;
import org.dubhe.biz.statemachine.dto.StateChangeDTO;
import org.dubhe.cloud.authconfig.utils.JwtUtils;
import org.dubhe.data.constant.*;
import org.dubhe.data.dao.FileMapper;
import org.dubhe.biz.base.vo.ProgressVO;
import org.dubhe.data.domain.bo.FileAnnotationBO;
import org.dubhe.data.domain.bo.TaskSplitBO;
import org.dubhe.data.domain.bo.TextAnnotationBO;
import org.dubhe.data.domain.dto.*;
import org.dubhe.data.domain.entity.*;
import org.dubhe.data.domain.vo.*;
import org.dubhe.data.machine.constant.DataStateMachineConstant;
import org.dubhe.data.machine.constant.FileStateCodeConstant;
import org.dubhe.data.machine.enums.FileStateEnum;
import org.dubhe.data.machine.utils.StateMachineUtil;
import org.dubhe.data.service.*;
import org.dubhe.data.service.store.IStoreService;
import org.dubhe.data.service.store.MinioStoreServiceImpl;
import org.dubhe.data.util.FileUtil;
import org.dubhe.data.util.GeneratorKeyUtil;
import org.dubhe.data.util.TaskUtils;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.support.replication.ReplicationResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.dubhe.data.constant.Constant.ABSTRACT_NAME_PREFIX;


/**
 * @description ???????????? ???????????????
 * @date 2020-04-10
 */
@Service
public class FileServiceImpl extends ServiceImpl<FileMapper, File> implements FileService {

    /**
     * ????????????????????????
     */
    @Value("${data.annotation.task.splitSize:16}")
    private Integer taskSplitSize;

    /**
     * ??????????????????????????????????????????
     */
    @Value("${data.file.pageSize:20}")
    private Integer defaultFilePageSize;

    /**
     * ???????????????
     */
    @Value("${storage.file-store-root-path:/nfs/}")
    private String prefixPath;

    /**
     * minIO??????
     */
    @Value("${minio.accessKey}")
    private String accessKey;

    /**
     * minIO??????
     */
    @Value("${minio.secretKey}")
    private String secretKey;

    /**
     * ???????????????
     */
    @Value("${minio.url}")
    private String url;

    /**
     * ?????????
     */
    @Value("${minio.bucketName}")
    private String bucketName;

    /**
     * esSearch??????
     */
    @Value("${es.index}")
    private String esIndex;

    /**
     * ????????????
     */
    @Autowired
    private FileConvert fileConvert;

    /**
     * ???????????????
     */
    @Autowired
    private FileUtil fileUtil;

    @Autowired
    private MinioUtil minioUtil;

    /**
     * ?????????
     */
    @Autowired
    @Lazy
    private TaskService taskService;

    /**
     * ???????????????????????????
     */
    @Resource(type = MinioStoreServiceImpl.class)
    private IStoreService storeService;

    /**
     * ????????????????????????
     */
    @Resource
    @Lazy
    private DatasetService datasetService;

    /**
     * ????????????????????????????????????
     */
    @Resource
    @Lazy
    private DatasetVersionFileService datasetVersionFileService;

    @Autowired
    private TaskUtils taskUtils;

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Resource
    private BulkProcessor bulkProcessor;

    @Resource
    private RedisUtils redisUtils;

    /**
     * ????????????????????????????????????
     */
    @Resource
    @Lazy
    private FileMapper fileMapper;

    @Resource
    private UserContextService contextService;

    @Autowired
    private DatasetLabelService datasetLabelService;

    @Autowired
    private DataFileAnnotationService dataFileAnnotationService;

    @Resource(name = "hostFileStoreApiImpl")
    private FileStoreApi fileStoreApi;

    /**
     * ????????????????????????
     */
    private static final String SAMPLE_FINISHED_QUEUE_NAME = "videoSample_finished_queue";

    /**
     * ????????????????????????
     */
    private static final String SAMPLE_FAILED_QUEUE_NAME = "videoSample_failed_queue";

    /**
     * ???????????????????????????
     */
    private static final String START_SAMPLE_QUEUE = "videoSample_processing_queue";

    /**
     * ???????????????????????????
     */
    private static final String SAMPLE_PENDING_QUEUE = "videoSample_task_queue";

    /**
     * ????????????????????????
     */
    private static final String DETAIL_NAME = "videoSample_pictures:";

    @Autowired
    private GeneratorKeyUtil generatorKeyUtil;


    /**
     * ????????????
     *
     * @param fileId ??????ID
     * @return FileVO ????????????
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public FileVO get(Long fileId, Long datasetId) {
        File file = fileMapper.selectFile(fileId, datasetId);
        Dataset dataset = datasetService.getOneById(datasetId);
        DatasetVersionFile datasetVersionFile = datasetVersionFileService.getDatasetVersionFile(datasetId, dataset.getCurrentVersionName(), fileId);
        if (file == null) {
            return null;
        }
        FileVO fileVO = fileConvert.toDto(file,
                getAnnotation(file.getDatasetId(), FileUtil.interceptFileNameAndDatasetId(datasetId, file.getName()),
                        datasetVersionFile.getVersionName(), datasetVersionFile.getChanged() == NumberConstant.NUMBER_0));
        if (datasetVersionFile.getChanged() == NumberConstant.NUMBER_0) {//???????????????????????????????????????id
            String annotation = fileVO.getAnnotation();
            if (StringUtils.isNotEmpty(annotation)) {
                List<Label> datasetLabels = datasetLabelService.listLabelByDatasetId(dataset.getId());
                Map<String, Long> labelMaps = new HashMap<>();
                datasetLabels.stream().forEach(label -> {
                    labelMaps.put(label.getName(), label.getId());
                });
                JSONArray jsonArray = JSON.parseArray(annotation);
                for(int i = 0; i < jsonArray.size(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    String categoryIdStr = jsonObject.getString("category_id");
                    if (!NumberUtil.isNumber(categoryIdStr)) {
                        if (labelMaps.containsKey(categoryIdStr)) {
                            jsonObject.put("category_id", labelMaps.get(categoryIdStr));
                        }
                    }
                }
                fileVO.setAnnotation(JSON.toJSONString(jsonArray));
            }
        }
        return fileVO;
    }

    /**
     * ??????????????????
     *
     * @param datasetId ?????????ID
     * @param fileName  ?????????
     * @return String
     */
    public String getAnnotation(Long datasetId, String fileName, String versionName, boolean change) {
        String path = fileUtil.getReadAnnotationAbsPath(datasetId, fileName, versionName, change);
        return storeService.read(path);
    }

    /**
     * ??????????????????????????????????????????
     *
     * @param datasetId ?????????ID
     */
    @Override
    public void isExistVideo(Long datasetId) {
        QueryWrapper<File> fileQueryWrapper = new QueryWrapper<>();
        fileQueryWrapper.lambda().eq(File::getDatasetId, datasetId);
        if (getBaseMapper().selectCount(fileQueryWrapper) > MagicNumConstant.ZERO) {
            throw new BusinessException(ErrorEnum.VIDEO_EXIST);
        }
    }

    /**
     * ????????????
     *
     * @param datasetId ?????????ID
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(Long datasetId) {
        QueryWrapper<File> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(File::getDatasetId, datasetId);
        remove(queryWrapper);
    }

    /**
     * ?????????????????????
     *
     * @param datasets ?????????
     * @return Map<Long, ProgressVO> ?????????????????????map
     */
    @Override
    public Map<Long, ProgressVO> listStatistics(List<Dataset> datasets) {
        if (CollectionUtils.isEmpty(datasets)) {
            return Collections.emptyMap();
        }
        Map<Long, ProgressVO> res = new HashMap<>(datasets.size());

        // ???????????????????????????
        datasets.forEach(dataset -> {
            Map<Integer, Integer> fileStatus = datasetVersionFileService.getDatasetVersionFileCount(dataset.getId(), dataset.getCurrentVersionName());
            ProgressVO progressVO = ProgressVO.builder().build();
            if (fileStatus != null) {
                for (Map.Entry<Integer, Integer> entry : fileStatus.entrySet()) {
                    JSONObject jsonObject = JSON.parseObject(JSON.toJSONString(entry.getValue()));
                    if (entry.getKey().equals(FileStateCodeConstant.NOT_ANNOTATION_FILE_STATE) || entry.getKey().equals(FileStateCodeConstant.MANUAL_ANNOTATION_FILE_STATE)) {
                        progressVO.setUnfinished(progressVO.getUnfinished() + jsonObject.getInteger("count"));
                    } else if (entry.getKey().equals(FileStateCodeConstant.AUTO_TAG_COMPLETE_FILE_STATE)) {
                        progressVO.setAutoFinished(progressVO.getAutoFinished() + jsonObject.getInteger("count"));
                    } else if (entry.getKey().equals(FileStateCodeConstant.ANNOTATION_COMPLETE_FILE_STATE)) {
                        progressVO.setFinished(progressVO.getFinished() + jsonObject.getInteger("count"));
                    } else if (entry.getKey().equals(FileStateCodeConstant.TARGET_COMPLETE_FILE_STATE)) {
                        progressVO.setFinishAutoTrack(progressVO.getFinishAutoTrack() + jsonObject.getInteger("count"));
                    } else if (entry.getKey().equals(FileStateCodeConstant.ANNOTATION_NOT_DISTINGUISH_FILE_STATE)) {
                        progressVO.setAnnotationNotDistinguishFile(progressVO.getAnnotationNotDistinguishFile() + jsonObject.getInteger("count"));
                    }
                }
            }
            res.put(dataset.getId(), progressVO);
        });
        return res;
    }


    /**
     * ?????????????????????
     *
     * @param files ????????????
     * @param task  ??????
     * @return List<TaskSplitBO> ????????????
     */
    @Override
    public List<TaskSplitBO> split(Collection<File> files, Task task) {
        if (CollectionUtils.isEmpty(files)) {
            return new LinkedList<>();
        }
        LogUtil.info(LogEnum.BIZ_DATASET, "split file. file size:{}", files.size());
        Map<Long, List<File>> groupedFiles = files.stream().collect(Collectors.groupingBy(File::getDatasetId));
        List<TaskSplitBO> ts = groupedFiles.values().stream()
                .flatMap(fs -> CollectionUtil.split(fs, taskSplitSize).stream())
                .map(fs -> TaskSplitBO.from(fs, task)).filter(Objects::nonNull).collect(Collectors.toList());
        LogUtil.info(LogEnum.BIZ_DATASET, "split result. split size:{}", ts.size());
        return ts;
    }

    /**
     * ??????????????????
     *
     * @param ids            ??????ID
     * @param fileStatusEnum ????????????
     * @return int ????????????
     */
    public int doUpdate(Collection<Long> ids, FileStateEnum fileStatusEnum) {
        if (CollectionUtils.isEmpty(ids)) {
            return MagicNumConstant.ZERO;
        }
        File newObj = File.builder().status(fileStatusEnum.getCode()).build();
        QueryWrapper<File> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().in(File::getId, ids);
        return baseMapper.update(newObj, queryWrapper);
    }

    /**
     * ??????????????????
     *
     * @param files          ????????????
     * @param fileStatusEnum ????????????
     * @return int ????????????
     */
    @Override
    public int update(Collection<File> files, FileStateEnum fileStatusEnum) {
        Collection<Long> ids = toIds(files);
        if (CollectionUtils.isEmpty(files)) {
            return MagicNumConstant.ZERO;
        }
        int count = doUpdate(ids, fileStatusEnum);
        if (count == MagicNumConstant.ZERO) {
            throw new BusinessException(ErrorEnum.DATA_ABSENT_OR_NO_AUTH);
        }
        return count;
    }


    /**
     * ??????????????????ID
     *
     * @param files file??????
     * @return Collection<Long> ??????ID
     */
    private Collection<Long> toIds(Collection<File> files) {
        if (CollectionUtils.isEmpty(files)) {
            return Collections.emptySet();
        }
        return files.stream().map(File::getId).collect(Collectors.toSet());
    }

    /**
     * ????????????
     *
     * @param fileId         ??????ID
     * @param fileStatusEnum ????????????
     * @return int ????????????
     */
    public int update(Long fileId, FileStateEnum fileStatusEnum) {
        File newObj = File.builder()
                .id(fileId)
                .status(fileStatusEnum.getCode())
                .build();
        return baseMapper.updateById(newObj);
    }

    /**
     * ????????????
     *
     * @param fileId         ??????ID
     * @param fileStatusEnum ????????????
     * @param originStatus   ????????????
     * @return boolean ????????????
     */
    public boolean update(Long fileId, FileStateEnum fileStatusEnum, FileStateEnum originStatus) {
        if (getById(fileId) == null) {
            return true;
        }
        UpdateWrapper<File> updateWrapper = new UpdateWrapper<>();
        updateWrapper.lambda().eq(File::getId, fileId).eq(File::getStatus, originStatus.getCode())
                .set(File::getId, fileId).set(File::getStatus, fileStatusEnum.getCode());
        return update(updateWrapper);
    }

    /**
     * ????????????
     *
     * @param fileId ??????ID
     * @param files  file??????
     * @return List<Long> ???????????????id??????
     */
    @Override
    public List<File> saveFiles(Long fileId, List<FileCreateDTO> files) {
        LogUtil.debug(LogEnum.BIZ_DATASET, "save files start, file size {}", files.size());
        Long start = System.currentTimeMillis();
        Map<String, String> fail = new HashMap<>(files.size());
        List<File> newFiles = new ArrayList<>();
        Long datasetUserId = datasetService.getOneById(fileId).getCreateUserId();
        files.stream().map(file -> FileCreateDTO.toFile(file, fileId, datasetUserId)).forEach(f -> {
            try {
                newFiles.add(f);
            } catch (DuplicateKeyException e) {
                fail.put(f.getName(), "the file already exists");
            }
        });
        if (!CollectionUtils.isEmpty(fail)) {
            throw new BusinessException(ErrorEnum.FILE_EXIST, JSON.toJSONString(fail), null);
        }
        Queue<Long> dataFileIds = generatorKeyUtil.getSequenceByBusinessCode(Constant.DATA_FILE, newFiles.size());
        for (File f : newFiles) {
            f.setId(dataFileIds.poll());
        }
        baseMapper.saveList(newFiles, JwtUtils.getCurUserId(), datasetUserId);
        LogUtil.debug(LogEnum.BIZ_DATASET, "save files end, times {}", (System.currentTimeMillis() - start));
        return newFiles;
    }

    /**
     * ??????????????????
     *
     * @param fileId ????????????ID
     * @param files  file??????
     * @param type   ????????????
     * @param pid    ?????????ID
     * @param userId ??????ID
     *
     * @return  List<File> ????????????
     */
    @Override
    public List<File> saveVideoFiles(Long fileId, List<FileCreateDTO> files, int type, Long pid, Long userId) {
        List<File> list = new ArrayList<>();
        Long createUserId = datasetService.getOneById(fileId).getCreateUserId();
        files.forEach(fileCreateDTO -> {
            File file = FileCreateDTO.toFile(fileCreateDTO, fileId, type, pid);
            list.add(file);
        });
        Queue<Long> dataFileIds = generatorKeyUtil.getSequenceByBusinessCode(Constant.DATA_FILE, list.size());
        for (File f : list) {
            f.setId(dataFileIds.poll());
        }
        baseMapper.saveList(list, userId, createUserId);
        return list;
    }

    /**
     * ????????????
     *
     * @param datasetId ?????????ID
     * @param status    ??????
     * @return QueryWrapper<File> ????????????
     */
    public QueryWrapper<File> buildQuery(Long datasetId, Set<Integer> status) {
        FileQueryCriteriaVO criteria = FileQueryCriteriaVO.builder()
                .datasetId(datasetId).order("id ASC").build();
        return WrapperHelp.getWrapper(criteria);
    }

    /**
     * ??????offset
     *
     * @param datasetId ?????????ID
     * @param fileId    ??????ID
     * @param type      ???????????????
     * @return Integer ?????????offset
     */
    @Override
    public Integer getOffset(Long fileId, Long datasetId, Integer[] type, Long[] labelIds) {
        Integer offset = datasetVersionFileService.getOffset(fileId, datasetId, type, labelIds);
        return offset == MagicNumConstant.ZERO ? null : offset - MagicNumConstant.ONE;
    }


    /**
     * ?????????????????????????????????????????????
     *
     * @param datasetId ?????????ID
     * @param offset    Offset
     * @param limit     ?????????
     * @param page      ????????????
     * @param type      ???????????????
     * @return Page<File> ????????????????????????
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public Page<File> listByLimit(Long datasetId, Long offset, Integer limit, Integer page, Integer[] type, Long[] labelId) {
        if (page == null) {
            page = MagicNumConstant.ONE;
        }
        if (offset == null) {
            offset = getDefaultOffset();
        }
        if (limit == null) {
            limit = defaultFilePageSize;
        }
        //???????????????
        Dataset dataset = datasetService.getOneById(datasetId);
        //???????????????????????????????????????(?????????)
        List<DatasetVersionFileDTO> datasetVersionFiles = datasetVersionFileService
                .getListByDatasetIdAndAnnotationStatus(dataset.getId(), dataset.getCurrentVersionName(), type, offset,
                        limit, "id", null, labelId);
        if (datasetVersionFiles == null || datasetVersionFiles.isEmpty()) {
            Page<File> filePage = new Page<>();
            filePage.setCurrent(page);
            filePage.setSize(limit);
            filePage.setTotal(NumberConstant.NUMBER_0);
            return filePage;
        }
        QueryWrapper<File> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("id", datasetVersionFiles
                .stream()
                .map(DatasetVersionFileDTO::getFileId)
                .collect(Collectors.toSet())).eq("dataset_id", dataset.getId());
        List<File> files = baseMapper.selectList(queryWrapper);
        //??????????????????????????????
        files.forEach(v -> {
            datasetVersionFiles.forEach(d -> {
                if (v.getId().equals(d.getFileId())) {
                    v.setStatus(d.getAnnotationStatus());
                }
            });
        });
        //?????????????????????????????????????????????
        List<File> fileArrayList = new ArrayList<>();
        datasetVersionFiles.forEach(v -> {
            files.forEach(f -> {
                if (v.getFileId().equals(f.getId())) {
                    f.setName(FileUtil.interceptFileNameAndDatasetId(datasetId, f.getName()));
                    fileArrayList.add(f);
                }
            });
        });
        Page<File> pages = new Page<>();
        if(!ArrayUtils.isEmpty(labelId)){
            pages.setTotal(dataFileAnnotationService.selectDetectionCount(datasetId, dataset.getCurrentVersionName(), labelId));
        } else {
            pages.setTotal(datasetVersionFileService.selectFileListTotalCount(dataset.getId(),
                    dataset.getCurrentVersionName(), type, labelId));
        }
        pages.setRecords(fileArrayList);
        pages.setSize(limit);
        pages.setCurrent(page);
        return pages;
    }

    /**
     * ????????????
     *
     * @param datasetId         ?????????ID
     * @param page              ????????????
     * @param queryCriteria     ????????????
     * @return Map<String, Object> ??????????????????
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public Map<String, Object> listPage(Long datasetId, Page page, FileQueryCriteriaVO queryCriteria) {
        Dataset dataset = datasetService.getOneById(queryCriteria.getDatasetId());
        List<DatasetVersionFileDTO> datasetVersionFiles = commDatasetVersionFiles(datasetId, dataset.getCurrentVersionName(), page, queryCriteria);
        if (datasetVersionFiles == null || datasetVersionFiles.isEmpty()) {
            return buildPage(page);
        }
        List<File> files = getFileList(datasetVersionFiles, datasetId);
        //??????????????????????????????
        files.forEach(v -> {
            datasetVersionFiles.forEach(d -> {
                if (v.getId().equals(d.getFileId())) {
                    v.setStatus(d.getAnnotationStatus());
                }
                d.setVersionName(dataset.getCurrentVersionName());
            });
        });
        //?????????????????????????????????????????????
        List<File> fileArrayList = new ArrayList<>();
        datasetVersionFiles.forEach(v -> {
            files.forEach(f -> {
                if (v.getFileId().equals(f.getId())) {
                    fileArrayList.add(f);
                }
            });
        });
        Map<Long, File> fileListMap = files.stream().collect(Collectors.toMap(File::getId, obj -> obj));
        List<Label> datasetLabels = datasetLabelService.listLabelByDatasetId(dataset.getId());
        Map<String, Long> labelMaps = new HashMap<>();
        datasetLabels.stream().forEach(label -> {
            labelMaps.put(label.getName(), label.getId());
        });
        List<FileVO> vos = datasetVersionFiles.stream().map(versionFile -> {
            FileVO fileVO = FileVO.builder().build();
            if (!Objects.isNull(fileListMap.get(versionFile.getFileId()))) {
                File file = fileListMap.get(versionFile.getFileId());
                BeanUtil.copyProperties(file, fileVO);
                fileVO.setLabelId(versionFile.getLabelId());
                fileVO.setPrediction(versionFile.getPrediction());
                fileVO.setAnnotation(getAnnotation(datasetId, FileUtil.interceptFileNameAndDatasetId(datasetId,file.getName()), versionFile.getVersionName(), versionFile.getChanged() == NumberConstant.NUMBER_0));
            }
            if (versionFile.getChanged() == NumberConstant.NUMBER_0) {
                String annotation = fileVO.getAnnotation();
                if (StringUtils.isNotEmpty(annotation)) {
                    JSONArray jsonArray = JSON.parseArray(annotation);
                    Long[] labels = new Long[jsonArray.size()];
                    for(int i = 0; i < jsonArray.size(); i++) {
                        JSONObject jsonObject = jsonArray.getJSONObject(i);
                        String categoryIdStr = jsonObject.getString("category_id");
                        if (!NumberUtil.isNumber(categoryIdStr)) {
                            if (labelMaps.containsKey(categoryIdStr)) {
                                jsonObject.put("category_id", labelMaps.get(categoryIdStr));
                                labels[i] = labelMaps.get(categoryIdStr);
                            }
                        } else {
                            labels[i] = jsonObject.getLong("category_id");
                        }
                    }
                    fileVO.setAnnotation(JSON.toJSONString(jsonArray));
                    fileVO.setLabelId(labels);
                }
            }
            return fileVO;
        }).collect(Collectors.toList());
        Page<File> pages = buildPages(page, files, dataset, queryCriteria);
        return PageUtil.toPage(pages, vos);
    }

    /**
     * ??????????????????
     *
     * @param datasetId ?????????id
     * @param type      ???????????????
     * @return Long ????????????Id
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public Long getFirst(Long datasetId, Integer type) {
        Dataset dataset = datasetService.getOneById(datasetId);
        DatasetVersionFile datasetVersionFile = datasetVersionFileService
                .getFirstByDatasetIdAndVersionNum(datasetId, dataset.getCurrentVersionName(), FileTypeEnum.getStatus(type));
        return datasetVersionFile == null ? null : datasetVersionFile.getFileId();
    }


    /**
     * ??????offset
     *
     * @return Long ??????offset
     */
    public Long getDefaultOffset() {
        return MagicNumConstant.ZERO_LONG;
    }

    /**
     * ??????ids?????????????????????
     *
     * @param fileIds ??????id??????
     * @return Set<File> ????????????
     */
    @Override
    public Set<File> get(List<Long> fileIds, Long datasetId) {
        if (CollectionUtils.isEmpty(fileIds)) {
            return new HashSet<>();
        }
        QueryWrapper<File> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dataset_id", datasetId);
        queryWrapper.eq("id", fileIds.get(MagicNumConstant.ZERO));
        File fileOne = baseMapper.selectOne(queryWrapper);
        if (fileOne == null) {
            return new HashSet<>();
        }
        QueryWrapper<File> fileQueryWrapper = new QueryWrapper<>();
        fileQueryWrapper.eq("dataset_id", fileOne.getDatasetId());
        fileQueryWrapper.in("id", fileIds);
        return new HashSet(baseMapper.selectList(fileQueryWrapper));
    }

    /**
     * ??????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void videoSample(String finishedQueue, String failedQueue) {
        try {
            Object object = taskUtils.getFinishedTask(finishedQueue);
            if (ObjectUtil.isNotNull(object)) {
                JSONObject jsonObject = JSONObject.parseObject(JSON.toJSONString(redisUtils.get(object.toString())));
                String datasetIdAndSub = jsonObject.getString("datasetIdAndSub");
                List<String> pictureNames = JSON.parseObject(jsonObject.getString("pictureNames"), ArrayList.class);
                Long datasetId = Long.valueOf(StringUtils.substringBefore(String.valueOf(datasetIdAndSub), ":"));
                QueryWrapper<Task> taskQueryWrapper = new QueryWrapper<>();
                taskQueryWrapper.lambda().eq(Task::getId, Long.valueOf(jsonObject.getString("id")));
                Task task = taskService.selectOne(taskQueryWrapper);
                if (taskService.isStop(task.getId())) {
                    redisUtils.del(object.toString());
                    redisUtils.del(object.toString().replace("annotation","detail"));
                    return;
                }
                Integer segment = Integer.valueOf(StringUtils.substringAfter(String.valueOf(datasetIdAndSub), ":"));
                if (segment.equals(task.getFinished() + MagicNumConstant.ONE)) {
                    try {
                        videSampleFinished(pictureNames, task);
                    } catch (Exception exception) {
                        LogUtil.error(LogEnum.BIZ_DATASET, "videoFinishedTask exception:{}", exception);
                    }
                    redisUtils.del(object.toString());
                    redisUtils.del(object.toString().replace("annotation","detail"));
                } else {
                    //????????????????????????
                    redisUtils.zAdd(object.toString().replace("task","finished"), System.currentTimeMillis()/1000, ("\"" + object.toString() + "\"").getBytes("utf-8"));
                }
            } else {
                TimeUnit.MILLISECONDS.sleep(MagicNumConstant.THREE_THOUSAND);
            }
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_DATASET, "get videoSample finish task failed:{}", e);
        }
        try {
            Object object = taskUtils.getFailedTask(failedQueue);
            if (ObjectUtil.isNotNull(object)) {
                String taskId = object.toString();
                JSONObject jsonObject = JSONObject.parseObject(JSON.toJSONString(redisUtils.get(taskId)));
                String datasetIdAndSub = jsonObject.getString("datasetIdAndSub");
                videoSampleFailed(datasetIdAndSub);
            }
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_DATASET, "get videoSample failed task failed:{}", e);
        }
    }

    /**
     * ????????????????????????
     *
     * @param failedId ??????????????????ID
     */
    public void videoSampleFailed(String failedId) {
        Long datasetId = Long.valueOf(StringUtils.substringBefore(String.valueOf(failedId), ":"));
        //?????????????????????
        StateChangeDTO stateChangeDTO = new StateChangeDTO();
        //????????????????????????????????????????????????
        Object[] objects = new Object[1];
        objects[0] = datasetId.intValue();
        stateChangeDTO.setObjectParam(objects);
        //?????????????????????????????????
        stateChangeDTO.setStateMachineType(DataStateMachineConstant.DATA_STATE_MACHINE);
        //??????????????????
        stateChangeDTO.setEventMethodName(DataStateMachineConstant.DATA_SAMPLING_FAILURE_EVENT);
        StateMachineUtil.stateChange(stateChangeDTO);
    }

    /**
     * ????????????????????????
     *
     * @param picNames ?????????????????????
     * @param task           ????????????
     */
    public void videSampleFinished(List<String> picNames, Task task) {
        QueryWrapper<File> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(File::getDatasetId, task.getDatasetId())
                .eq(File::getFileType, MagicNumConstant.ONE)
                .eq(File::getStatus, FileTypeEnum.UNFINISHED.getValue())
                .eq(File::getId, task.getTargetId());
        File file = getBaseMapper().selectOne(queryWrapper);
        saveVideoPic(picNames, file);
        task.setFinished(task.getFinished() + MagicNumConstant.ONE);
        taskService.updateByTaskId(task);
        //????????????????????????
        if (task.getTotal().equals(task.getFinished())) {
            file.setStatus(FileStateCodeConstant.AUTO_TAG_COMPLETE_FILE_STATE);
            getBaseMapper().updateFileStatus(file.getDatasetId(), file.getId(), file.getStatus());
            QueryWrapper<File> statusQuery = new QueryWrapper<>();
            statusQuery.lambda().eq(File::getDatasetId,task.getDatasetId())
                    .eq(File::getFileType, MagicNumConstant.ONE)
                    .ne(File::getStatus,FileStateCodeConstant.AUTO_TAG_COMPLETE_FILE_STATE);
            Integer unfinishedNum = baseMapper.selectCount(statusQuery);
            if(unfinishedNum.equals(MagicNumConstant.ZERO)){
                //?????????????????????
                StateChangeDTO stateChangeDTO = new StateChangeDTO();
                //????????????????????????????????????????????????
                Object[] objects = new Object[1];
                objects[0] = file.getDatasetId().intValue();
                stateChangeDTO.setObjectParam(objects);
                //?????????????????????????????????
                stateChangeDTO.setStateMachineType(DataStateMachineConstant.DATA_STATE_MACHINE);
                //????????????
                stateChangeDTO.setEventMethodName(DataStateMachineConstant.DATA_SAMPLING_EVENT);
                StateMachineUtil.stateChange(stateChangeDTO);
            }
        }
    }

    /**
     * ?????????????????????
     *
     * @param picNames ??????????????????
     * @param file     ????????????
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveVideoPic(List<String> picNames, File file) {
        Collections.reverse(picNames);
        List<FileCreateDTO> fileCreateDTOS = new ArrayList<>();
        picNames.forEach(picName -> {
            picName = StringUtils.substringAfter(picName, prefixPath);
            FileCreateDTO f = FileCreateDTO.builder()
                    .url(picName)
                    .build();
            fileCreateDTOS.add(f);
        });
        List<File> files = saveVideoFiles(file.getDatasetId(), fileCreateDTOS, DatatypeEnum.IMAGE.getValue(), file.getId(), file.getCreateUserId());
        List<DatasetVersionFile> datasetVersionFiles = new ArrayList<>();
        files.forEach(fileOne -> {
            DatasetVersionFile datasetVersionFile = new DatasetVersionFile(file.getDatasetId(), null, fileOne.getId(), fileOne.getName());
            datasetVersionFiles.add(datasetVersionFile);
        });
        datasetVersionFileService.insertList(datasetVersionFiles);
    }

    /**
     * ????????????file
     *
     * @param datasetVersionFiles ????????????
     * @param init                ????????????
     */
    public void updateStatus(List<DatasetVersionFile> datasetVersionFiles, FileStateEnum init) {
        List<Long> fileIds = datasetVersionFiles
                .stream().map(DatasetVersionFile::getFileId)
                .collect(Collectors.toList());
        UpdateWrapper<File> fileUpdateWrapper = new UpdateWrapper();
        fileUpdateWrapper.in("id", fileIds);
        File file = new File();
        file.setStatus(init.getCode());
        baseMapper.update(file, fileUpdateWrapper);
    }


    /**
     * ????????????????????????????????????
     *
     * @param fileId ??????id
     * @return List<File> ??????????????????????????????
     */
    @Override
    @DataPermissionMethod(dataType = DatasetTypeEnum.PUBLIC)
    public List<File> getEnhanceFileList(Long fileId, Long datasetId) {
        File file = baseMapper.getOneById(fileId, datasetId);

        if (ObjectUtil.isNull(file)) {
            throw new BusinessException(ErrorEnum.FILE_ABSENT);
        }
        Dataset dataset = datasetService.getOneById(file.getDatasetId());
        if (ObjectUtil.isNull(dataset)) {
            throw new BusinessException(ErrorEnum.DATASET_ABSENT);
        }
        int enhanceFileCount = datasetVersionFileService.getEnhanceFileCount(dataset.getId(), dataset.getCurrentVersionName());
        if (enhanceFileCount > 0) {
            return datasetVersionFileService.getEnhanceFileList(dataset.getId(), dataset.getCurrentVersionName(), fileId);
        }
        return null;
    }

    /**
     * ??????????????????
     *
     * @param fileId ??????ID
     * @return File ????????????
     */
    @Override
    public File selectById(Long fileId, Long datasetId) {
        QueryWrapper<File> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dataset_id", datasetId);
        queryWrapper.eq("id", fileId);
        return baseMapper.selectOne(queryWrapper);
    }

    /**
     * ??????????????????????????????
     *
     * @param queryWrapper ????????????
     * @return ????????????
     */
    @Override
    public File selectOne(QueryWrapper<File> queryWrapper) {
        return baseMapper.selectOne(queryWrapper);
    }

    /**
     * ??????????????????
     *
     * @param wrapper ????????????
     * @return ????????????
     */
    @Override
    public List<File> listFile(QueryWrapper<File> wrapper) {
        return list(wrapper);
    }

    /**
     * ????????????????????????
     *
     * @param datasetId ?????????ID
     * @param offset    ?????????
     * @param batchSize ?????????
     * @param status    ??????????????????
     * @return ????????????
     */
    @Override
    public List<File> listBatchFile(Long datasetId, int offset, int batchSize, Collection<Integer> status) {
        try{
            Dataset dataset = datasetService.getOneById(datasetId);
            return baseMapper.selectListOne(datasetId, dataset.getCurrentVersionName(), offset, batchSize, status);
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_DATASET, "get annotation files error {}", e);
            return null;
        }
    }

    /**
     * ??????????????????????????????????????????
     */
    @Override
    public void expireSampleTask() {
        Set<ZSetOperations.TypedTuple<Object>> typedTuples = taskUtils.zGetWithScore(START_SAMPLE_QUEUE);
        typedTuples.forEach(value -> {
            String timestampString = new BigDecimal(StringUtils.substringBefore(value.getScore().toString(),"."))
                    .toPlainString();
            long timestamp = Long.parseLong(timestampString);
            String keyId = JSONObject.parseObject(JSON.toJSONString(value.getValue())).getString("datasetIdKey");
            long timestampNow = System.currentTimeMillis() / 1000;
            if (timestampNow - timestamp > MagicNumConstant.TWO_HUNDRED) {
                LogUtil.info(LogEnum.BIZ_DATASET, "restart videoSample task keyId:{}", keyId);
//                taskUtils.restartTask(keyId, START_SAMPLE_QUEUE, SAMPLE_PENDING_QUEUE, DETAIL_NAME
//                        , JSON.toJSONString(value.getValue()));
            }
        });
    }

    /**
     * ????????????????????????ID????????????url
     *
     * @param datasetId     ?????????ID
     * @param versionName   ?????????
     * @return List<String> url??????
     */
    @Override
    public List<String> selectUrls(Long datasetId, String versionName) {
        return baseMapper.selectUrls(datasetId, versionName);
    }

    /**
     * ??????version.changed????????????name??????
     *
     * @param datasetId     ?????????ID
     * @param changed       ????????????????????????
     * @param versionName   ????????????
     * @return List<FileAnnotationBO>   ????????????
     */
    @Override
    public List<FileAnnotationBO> selectFileAnnotations(Long datasetId, Integer changed, String versionName) {
        return baseMapper.selectFileAnnotations(datasetId, changed, versionName);
    }

    /**
     * ??????????????????????????????
     *
     * @param datasetId             ?????????ID
     * @param currentVersionName    ??????????????????
     * @param page                  ??????
     * @param queryCriteria         ????????????
     * @return List<DatasetVersionFileDTO> ??????????????????
     */
    private List<DatasetVersionFileDTO> commDatasetVersionFiles(Long datasetId, String currentVersionName, Page page, FileQueryCriteriaVO queryCriteria) {
        queryCriteria.setDatasetId(datasetId);
        queryCriteria.setFileType(DatatypeEnum.IMAGE.getValue());

        Integer[] status = findStatus(queryCriteria.getStatus(),queryCriteria.getAnnotateStatus(),queryCriteria.getAnnotateType());

        //???????????????ID??????????????????????????????????????????????????????????????????
        List<DatasetVersionFileDTO> datasetVersionFiles = datasetVersionFileService
                .getListByDatasetIdAndAnnotationStatus(datasetId,
                        currentVersionName,
                        status,
                        (page.getCurrent() - 1) * page.getSize(),
                        (int) page.getSize(),
                        queryCriteria.getSort(),
                        queryCriteria.getOrder(),
                        queryCriteria.getLabelId()
                );
        return datasetVersionFiles;
    }

    /**
     * ????????????????????????
     *
     * @param status            ???????????????
     * @param annotateStatus    ?????????????????????
     * @param annotateType      ?????????????????????
     * @return Integer[] ????????????
     */
    private Integer[] findStatus(Integer[] status, Integer[] annotateStatus, Integer[] annotateType) {
        Set<Integer> statusResult = new HashSet<>();
        // ????????????????????????????????????????????????????????????
        statusResult.addAll(FileTypeEnum.getStatus(status[0]));
        // ???????????????????????????????????????????????????????????????????????????????????????????????????
        if (annotateStatus != null && annotateStatus.length > 0) {
            statusResult.retainAll(FileTypeEnum.getStatus(Arrays.asList(annotateStatus)));
        }
        // ?????????????????????????????????????????????????????????????????????????????????????????????????????????
        if (annotateType != null && annotateType.length > 0) {
            statusResult.retainAll(FileTypeEnum.getStatus(Arrays.asList(annotateType)));
        }
        // ?????????????????????????????????????????????????????????????????????????????????????????????????????????
        statusResult.add(-1);
        Integer[] statusList = new Integer[statusResult.size()];
        statusResult.toArray(statusList);
        return statusList;
    }


    /**
     * ??????????????????
     *
     * @param page ????????????
     * @return Map<String, Object> ????????????
     */
    private Map<String, Object> buildPage(Page page) {
        return PageUtil.toPage(new Page<File>() {{
            setCurrent(page.getCurrent());
            setSize(page.getSize());
            setTotal(NumberConstant.NUMBER_0);
        }}, new ArrayList<FileVO>());
    }

    /**
     * ???????????????????????????
     *
     * @param datasetId         ?????????id
     * @param page              ????????????
     * @param queryCriteria ??????????????????
     * @return Map<String, Object> ??????????????????
     */
    @Override
    public Map<String, Object> audioFilesByPage(Long datasetId, Page page, FileQueryCriteriaVO queryCriteria) {
        //???????????????
        Dataset dataset = datasetService.getOneById(queryCriteria.getDatasetId());
        if (DatasetTypeEnum.PUBLIC.getValue().compareTo(dataset.getType()) == 0) {
            DataContext.set(CommonPermissionDataDTO.builder().type(true).build());
        }
        List<File> files = new ArrayList<>();
        List<TxtFileVO> vos = new ArrayList<>();
        try {
            List<DatasetVersionFileDTO> datasetVersionFiles = commDatasetVersionFiles(datasetId, dataset.getCurrentVersionName(), page, queryCriteria);
            if (datasetVersionFiles == null || datasetVersionFiles.isEmpty()) {
                return buildPage(page);
            }
            files = getFileList(datasetVersionFiles, datasetId);
            Map<Long, File> fileListMap = files.stream().collect(Collectors.toMap(File::getId, obj -> obj));
            vos = datasetVersionFiles.stream().map(versionFile -> {
                TxtFileVO fileVO = TxtFileVO.builder().build();
                if (!Objects.isNull(fileListMap.get(versionFile.getFileId()))) {
                    File file = fileListMap.get(versionFile.getFileId());
                    BeanUtil.copyProperties(file, fileVO);
                    fileVO.setPrediction(versionFile.getPrediction());
                    fileVO.setLabelId(versionFile.getLabelId());
                    fileVO.setAbstractName(Constant.ABSTRACT_NAME_PREFIX + file.getName());
                    String afterPath = StringUtils.substringAfterLast(fileVO.getUrl(), SymbolConstant.SLASH);
                    String beforePath = StringUtils.substringBeforeLast(fileVO.getUrl(), SymbolConstant.SLASH);
                    String newPath = beforePath + SymbolConstant.SLASH + ABSTRACT_NAME_PREFIX + afterPath;
                    fileVO.setAbstractUrl(newPath);
                    fileVO.setStatus(versionFile.getAnnotationStatus());
                    fileVO.setAnnotation(getAnnotation(datasetId, FileUtil.interceptFileNameAndDatasetId(datasetId,file.getName()), versionFile.getVersionName(), versionFile.getChanged() == NumberConstant.NUMBER_0));
                }
                return fileVO;
            }).collect(Collectors.toList());
        } finally {
            if (DatasetTypeEnum.PUBLIC.getValue().compareTo(dataset.getType()) == 0) {
                DataContext.remove();
            }
        }
        Page<File> pages = buildPages(page, files, dataset, queryCriteria);
        return PageUtil.toPage(pages, vos);
    }

    /**
     * ???????????????????????????
     *
     * @param datasetId         ?????????id
     * @param page              ????????????
     * @param fileQueryCriteria ??????????????????
     * @return Map<String, Object> ??????????????????
     */
    @Override
    public Map<String, Object> txtContentByPage(Long datasetId, Page page, FileQueryCriteriaVO fileQueryCriteria) {
        SearchRequest searchRequest = new SearchRequest(esIndex);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        QueryBuilder queryBuilder = null;
        BoolQueryBuilder boolQueryBuilder = null;
        if(fileQueryCriteria.getAnnotateType() == null || fileQueryCriteria.getAnnotateType().length == 0){
            if (fileQueryCriteria.getStatus()[0].equals(FileTypeEnum.UNFINISHED_FILE.getValue())){
                boolQueryBuilder = QueryBuilders.boolQuery()
                        .must(fileQueryCriteria.getContent()==null?QueryBuilders.matchAllQuery()
                                :QueryBuilders.matchPhraseQuery("content", fileQueryCriteria.getContent()))
                        .must(QueryBuilders.termQuery("datasetId", fileQueryCriteria.getDatasetId().toString()))
                        .must(QueryBuilders.termsQuery("status"
                                , FileStateCodeConstant.NOT_ANNOTATION_FILE_STATE.toString()
                                , FileStateCodeConstant.ANNOTATION_NOT_DISTINGUISH_FILE_STATE.toString()));
            }
            if(fileQueryCriteria.getStatus()[0].equals(FileTypeEnum.FINISHED_FILE.getValue())){
                boolQueryBuilder = QueryBuilders.boolQuery()
                        .must(fileQueryCriteria.getContent()==null?QueryBuilders.matchAllQuery()
                                :QueryBuilders.matchPhraseQuery("content", fileQueryCriteria.getContent()))
                        .must(QueryBuilders.termQuery("datasetId", fileQueryCriteria.getDatasetId().toString()))
                        .must(QueryBuilders.termsQuery("status"
                                , FileStateCodeConstant.AUTO_TAG_COMPLETE_FILE_STATE.toString()
                                , FileStateCodeConstant.ANNOTATION_COMPLETE_FILE_STATE.toString()));
            }
            if (fileQueryCriteria.getStatus()[0].equals(FileTypeEnum.HAVE_ANNOTATION.getValue())){
                boolQueryBuilder = QueryBuilders.boolQuery()
                        .must(fileQueryCriteria.getContent()==null?QueryBuilders.matchAllQuery()
                                :QueryBuilders.matchPhraseQuery("content", fileQueryCriteria.getContent()))
                        .must(QueryBuilders.termQuery("datasetId", fileQueryCriteria.getDatasetId().toString()))
                        .must(QueryBuilders.termsQuery("status"
                                , FileStateCodeConstant.MANUAL_ANNOTATION_FILE_STATE.toString()
                                , FileStateCodeConstant.AUTO_TAG_COMPLETE_FILE_STATE.toString()
                                , FileStateCodeConstant.ANNOTATION_COMPLETE_FILE_STATE.toString()
                                , FileStateCodeConstant.TARGET_COMPLETE_FILE_STATE.toString()));
            }
            if(fileQueryCriteria.getStatus()[0].equals(FileTypeEnum.NO_ANNOTATION.getValue())){
                boolQueryBuilder = QueryBuilders.boolQuery()
                        .must(fileQueryCriteria.getContent()==null?QueryBuilders.matchAllQuery()
                                :QueryBuilders.matchPhraseQuery("content", fileQueryCriteria.getContent()))
                        .must(QueryBuilders.termQuery("datasetId", fileQueryCriteria.getDatasetId().toString()))
                        .must(QueryBuilders.termsQuery("status"
                                , FileStateCodeConstant.NOT_ANNOTATION_FILE_STATE.toString()
                                , FileStateCodeConstant.ANNOTATION_NOT_DISTINGUISH_FILE_STATE.toString()));
            }
        }
         else {
            if(fileQueryCriteria.getAnnotateType().length == MagicNumConstant.ONE){
                boolQueryBuilder = QueryBuilders.boolQuery()
                        .must(fileQueryCriteria.getContent()==null?QueryBuilders.matchAllQuery()
                                :QueryBuilders.matchPhraseQuery("content", fileQueryCriteria.getContent()))
                        .must(QueryBuilders.termQuery("datasetId", fileQueryCriteria.getDatasetId().toString()))
                        .must(QueryBuilders.termsQuery("status"
                                , fileQueryCriteria.getAnnotateType()[0].toString()));
            } else if(fileQueryCriteria.getAnnotateType ().length == MagicNumConstant.TWO){
                boolQueryBuilder = QueryBuilders.boolQuery()
                        .must(fileQueryCriteria.getContent()==null?QueryBuilders.matchAllQuery()
                                :QueryBuilders.matchPhraseQuery("content", fileQueryCriteria.getContent()))
                        .must(QueryBuilders.termQuery("datasetId", fileQueryCriteria.getDatasetId().toString()))
                        .must(QueryBuilders.termsQuery("status"
                                , fileQueryCriteria.getAnnotateType()[0].toString()
                                , fileQueryCriteria.getAnnotateType()[1].toString()));
            }
        }
        Dataset dataset = datasetService.getOneById(datasetId);
        boolQueryBuilder.must(QueryBuilders.matchPhraseQuery("versionName", StringUtils.isEmpty(dataset.getCurrentVersionName())?"V0000" : dataset.getCurrentVersionName()));
        if(fileQueryCriteria.getLabelId() != null){
            queryBuilder = boolQueryBuilder.must(QueryBuilders.termsQuery("labelId", fileQueryCriteria.getLabelId()));
        } else {
            queryBuilder = boolQueryBuilder;
        }
        sourceBuilder.query(queryBuilder);
        sourceBuilder.from((int)(page.getSize()*(page.getCurrent()-1)));
        sourceBuilder.size((int) page.getSize());
        sourceBuilder.sort(new FieldSortBuilder("updateTime.keyword").order(SortOrder.DESC).unmappedType("long"));
        sourceBuilder.sort(new FieldSortBuilder("createTime.keyword").order(SortOrder.DESC).unmappedType("long"));
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.preTags("<font class='highlight'>");
        highlightBuilder.postTags("</font>");
        highlightBuilder.field("content");
        sourceBuilder.highlighter(highlightBuilder);
        sourceBuilder.trackTotalHits(true);
        searchRequest.source(sourceBuilder);
        List<TxtFileVO> vos = new ArrayList<>();
        try {
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            SearchHit[] hits = searchResponse.getHits().getHits();
            for (int i = 0; i < hits.length; i++) {
                EsDataFileDTO esDataFileDTO = JSON.parseObject(hits[i].getSourceAsString(), EsDataFileDTO.class);
                StringBuilder highlightContent = new StringBuilder();
                if(fileQueryCriteria.getContent()!=null){
                    Map<String, HighlightField> highlightFields = hits[i].getHighlightFields();
                    Text[] fragments = highlightFields.get("content").getFragments();
                    for(Text text:fragments){
                        highlightContent.append(text);
                    }
                }
                TxtFileVO txtFileVO = new TxtFileVO();
                txtFileVO.setPrediction(esDataFileDTO.getPrediction());
                txtFileVO.setContent(fileQueryCriteria.getContent()==null?esDataFileDTO.getContent():highlightContent.toString());
                txtFileVO.setName(esDataFileDTO.getName());
                txtFileVO.setDatasetId(esDataFileDTO.getDatasetId());
                txtFileVO.setStatus(esDataFileDTO.getStatus());
                txtFileVO.setId(Long.parseLong(hits[i].getId()));
                txtFileVO.setLabelId(esDataFileDTO.getLabelId());
                txtFileVO.setAnnotation(esDataFileDTO.getAnnotation());
                vos.add(txtFileVO);
            }
            page.setTotal(searchResponse.getHits().getTotalHits().value);
        } catch (IOException e) {
            LogUtil.error(LogEnum.BIZ_DATASET, "search text from es error:{}", e);
        }
        return PageUtil.toPage(page, vos);
    }

    /**
     * ????????????????????????
     *
     * @param datasetId ?????????ID
     * @param fileScreenStatSearchDTO       ??????????????????
     * @return ProgressVO ????????????????????????
     */
    @Override
    public FileScreenStatVO getFileCountByStatus(Long datasetId, FileScreenStatSearchDTO fileScreenStatSearchDTO) {
        Dataset dataset = datasetService.getOneById(datasetId);
        Set<Integer> statusResult = Arrays.stream(findStatus(new Integer[]{fileScreenStatSearchDTO.getAnnotationResult()},
                fileScreenStatSearchDTO.getAnnotationStatus(),
                fileScreenStatSearchDTO.getAnnotationMethod())).collect(Collectors.toSet());
        Long haveAnnotation = FileTypeEnum.HAVE_ANNOTATION.getValue() == fileScreenStatSearchDTO.getAnnotationResult().intValue()
                ? getFileCount(dataset, statusResult, fileScreenStatSearchDTO.getLabelIds())
                :getFileCount(dataset, FileTypeEnum.getStatus(FileTypeEnum.HAVE_ANNOTATION.getValue()), null);
        Long noAnnotation = FileTypeEnum.NO_ANNOTATION.getValue() == fileScreenStatSearchDTO.getAnnotationResult().intValue()
                ? getFileCount(dataset, statusResult, fileScreenStatSearchDTO.getLabelIds())
                : getFileCount(dataset, FileTypeEnum.getStatus(FileTypeEnum.NO_ANNOTATION.getValue()), null);
        return FileScreenStatVO.builder().haveAnnotation(haveAnnotation).noAnnotation(noAnnotation).build();
    }

    /**
     * ?????????????????????
     *
     * @param  dataset           ?????????
     * @param  fileStatus        ??????????????????
     * @return Long              ??????????????????
     */
    private Long getFileCount(Dataset dataset, Set<Integer> fileStatus, List<Long> labelIds){
        return datasetVersionFileService.getVersionFileCountByStatusVersionAndLabelId(dataset.getId(), fileStatus, dataset.getCurrentVersionName(), labelIds);
    }

    /**
     * ??????????????????
     *
     * @param datasetVersionFiles   ???????????????????????????
     * @param datasetId             ?????????ID
     * @return List<File> ????????????
     */
    private List<File> getFileList(List<DatasetVersionFileDTO> datasetVersionFiles, Long datasetId) {
        Set<Long> set = datasetVersionFiles
                .stream()
                .map(DatasetVersionFileDTO::getFileId)
                .collect(Collectors.toSet());
        QueryWrapper queryWrapper = new QueryWrapper<>()
                .in("id", set)
                .eq("dataset_id", datasetId);
        List<File> files = baseMapper.selectList(queryWrapper);

        return files;
    }


    /**
     * ????????????????????????
     *
     * @param page          ????????????
     * @param files         ????????????
     * @param dataset       ???????????????
     * @param queryCriteria ????????????
     * @return ge<File> ????????????
     */
    private Page<File> buildPages(Page page, List<File> files, Dataset dataset, FileQueryCriteriaVO queryCriteria) {
        Page<File> pages = new Page<>();
        Integer[] status = findStatus(queryCriteria.getStatus(), queryCriteria.getAnnotateStatus(), queryCriteria.getAnnotateType());
        pages.setTotal(datasetVersionFileService.selectFileListTotalCount(dataset.getId(),
                dataset.getCurrentVersionName(), status, queryCriteria.getLabelId()));
        pages.setRecords(files);
        pages.setSize(page.getSize());
        pages.setCurrent(page.getCurrent());
        return pages;
    }

    /**
     * ???????????????????????????
     *
     * @param datasetId ?????????ID
     * @return ?????????????????????
     */
    @Override
    public int getFileCountByDatasetId(Long datasetId) {
        QueryWrapper<File> queryWrapper = new QueryWrapper();
        queryWrapper.lambda().eq(File::getDeleted, 0).eq(File::getDatasetId, datasetId);
        return baseMapper.selectCount(queryWrapper);
    }

    /**
     * ?????????????????????????????????
     *
     * @param datasetId ?????????ID
     * @param versionName ????????????
     * @return ???????????????????????????
     */
    @Override
    public int getOriginalFileCountOfDataset(Long datasetId, String versionName) {
        return fileMapper.getOriginalFileCountOfDataset(datasetId, versionName);
    }

    /**
     * ???????????????????????????
     * @param originDataset    ??????????????????
     * @param targetDataset    ?????????????????????
     * @return ????????????
     */
    @Override
    public List<File> backupFileDataByDatasetId(Dataset originDataset, Dataset targetDataset) {
        Long pid = 0L;
        List<File> fileList = null;
        List<File> files = baseMapper.selectList(new LambdaQueryWrapper<File>().eq(File::getDatasetId, originDataset.getId())
                .ne(File::getFileType, MagicNumConstant.ONE).or().isNull(File::getFileType));
        if (!CollectionUtils.isEmpty(files)) {
            Queue<Long> dataFileIds = generatorKeyUtil.getSequenceByBusinessCode(Constant.DATA_FILE, files.size());
            for (int i = 0; i < files.size(); i++) {
                File f = files.get(i);
                f.setId(dataFileIds.poll());
                if (!Objects.isNull(f.getFileType()) && f.getFileType().compareTo(MagicNumConstant.ONE) == 0) {
                    f.setUrl(f.getUrl().replace(originDataset.getId().toString(), targetDataset.getId().toString()));
                    f.setName(FileUtil.spliceFileNameAndDatasetId(targetDataset.getId(), f.getName()));
                    baseMapper.insert(f);
                    pid = f.getId();
                    files.remove(i);
                }
            }
            Long finalPid = pid;
            fileList = files.stream().map(a -> {
                File file = File.builder()
                        .id(a.getId())
                        .fileType(a.getFileType())
                        .datasetId(targetDataset.getId())
                        .enhanceType(a.getEnhanceType())
                        .frameInterval(a.getFrameInterval())
                        .height(a.getHeight())
                        .name(a.getName())
                        .status(a.getStatus())
                        .pid(finalPid)
                        .originUserId(MagicNumConstant.ZERO_LONG)
                        .width(a.getWidth())
                        .url(a.getUrl().replace(originDataset.getId().toString() + SymbolConstant.SLASH
                                , targetDataset.getId().toString() + SymbolConstant.SLASH))
                        .build();
                file.setCreateUserId(targetDataset.getCreateUserId());
                file.setUpdateUserId(file.getCreateUserId());
                file.setDeleted(false);
                return file;
            }).collect(Collectors.toList());
            List<List<File>> splitFiles = CollectionUtil.split(fileList, MagicNumConstant.FOUR_THOUSAND);
            splitFiles.forEach(splitFile->baseMapper.insertBatch(splitFile));
        }

        return fileList;

    }

    /**
     * ????????????????????????ES
     *
     * @param dataset ?????????
     */
    @Override
    public void transportTextToEs(Dataset dataset,List<Long> fileIdsNotToEs,Boolean ifImport) {
        List<EsTransportDTO> esTransportDTOList = fileMapper.selectTextDataNoTransport(dataset.getId(), fileIdsNotToEs, ifImport);
        if(ifImport != null && ifImport){
            List<TextAnnotationBO> textAnnotationBOS = fileMapper.selectTextAnnotation(dataset.getId(), fileIdsNotToEs);
            Map<Long, List<TextAnnotationBO>> annotationGroup = textAnnotationBOS.stream().collect(Collectors.groupingBy(TextAnnotationBO::getId));
            esTransportDTOList.stream().forEach(esTransportDTO -> {
                List<TextAnnotationBO> annotationsById = annotationGroup.get(esTransportDTO.getId());
                List<Long> labelIds = annotationsById.stream().map(TextAnnotationBO::getLabelId).collect(Collectors.toList());
                esTransportDTO.setLabelId(labelIds.toArray(new Long[labelIds.size()]));
                JSONArray annotations = new JSONArray();
                annotationsById.forEach(annotation->{
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("category_id",annotation.getLabelId());
                    jsonObject.put("prediction", annotation.getPrediction());
                    annotations.add(jsonObject);
                });
                esTransportDTO.setAnnotation(annotations.toJSONString());
            });
        }
        esTransportDTOList.forEach(esTransportDTO -> {
            FileInputStream fileInputStream = null;
            InputStreamReader reader = null;
            BufferedReader bufferedReader = null;
            try {
                String url = prefixPath + esTransportDTO.getUrl();
                fileInputStream = new FileInputStream(url);
                reader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);
                bufferedReader = new BufferedReader(reader);
                StringBuffer testContent = new StringBuffer();
                String tempContent;
                while ((tempContent = bufferedReader.readLine()) != null) {
                    testContent.append(tempContent);
                }
                Map<String, Object> jsonMap = new HashMap<>();
                jsonMap.put("content", testContent.toString());
                jsonMap.put("name", esTransportDTO.getFileName());
                jsonMap.put("status",esTransportDTO.getAnnotationStatus().toString());
                jsonMap.put("datasetId",dataset.getId().toString());
                jsonMap.put("createUserId",esTransportDTO.getCreateUserId()==null?null:esTransportDTO.getCreateUserId().toString());
                jsonMap.put("createTime",esTransportDTO.getCreateTime().toString());
                jsonMap.put("updateUserId",esTransportDTO.getUpdateUserId()==null?null:esTransportDTO.getUpdateUserId().toString());
                jsonMap.put("updateTime",esTransportDTO.getUpdateTime().toString());
                jsonMap.put("fileType",esTransportDTO.getFileType()==null?null:esTransportDTO.getFileType().toString());
                jsonMap.put("enhanceType",esTransportDTO.getEnhanceType()==null?null:esTransportDTO.getEnhanceType().toString());
                jsonMap.put("originUserId",esTransportDTO.getOriginUserId().toString());
                jsonMap.put("prediction",esTransportDTO.getPrediction()==null?null:esTransportDTO.getPrediction().toString());
                jsonMap.put("labelId",esTransportDTO.getLabelId()==null?null:esTransportDTO.getLabelId());
                jsonMap.put("annotation", esTransportDTO.getAnnotation()==null?null:esTransportDTO.getAnnotation());
                jsonMap.put("versionName", StringUtils.isEmpty(dataset.getCurrentVersionName())?"V0000" : dataset.getCurrentVersionName());
                IndexRequest request = new IndexRequest(esIndex);
                request.source(jsonMap);
                request.id(esTransportDTO.getId().toString());
                bulkProcessor.add(request);
                LogUtil.info(LogEnum.BIZ_DATASET,"transport one text to es:{}",esTransportDTO.getUrl());
            } catch (Exception e) {
                LogUtil.error(LogEnum.BIZ_DATASET, "transport text to es error:{}", e);
            } finally {
                try {
                    fileInputStream.close();
                    reader.close();
                    bufferedReader.close();
                } catch (Exception e) {
                    LogUtil.error(LogEnum.BIZ_DATASET, "transport text to es error:{}", e);
                }
            }
        });
        bulkProcessor.flush();
        List<Long> fileIds = new ArrayList<>();
        esTransportDTOList.forEach(esTransportDTO -> fileIds.add(esTransportDTO.getId()));
        fileMapper.updateEsStatus(dataset.getId(),fileIds);
    }

    /**
     * ??????es_transport??????
     *
     * @param datasetId ?????????ID
     * @param fileId ??????ID
     */
    @Override
    public void recoverEsStatus(Long datasetId, Long fileId){
        fileMapper.recoverEsStatus(datasetId, fileId);
    }

    /**
     * ??????es?????????
     *
     * @param fileIds ??????ID??????
     */
    @Override
    public void deleteEsData(Long[] fileIds){
        for (Long fileId : fileIds) {
            DeleteRequest deleteRequest = new DeleteRequest(esIndex,fileId.toString());
            deleteRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            try {
                DeleteResponse delete = restHighLevelClient.delete(deleteRequest, RequestOptions.DEFAULT);
                ReplicationResponse.ShardInfo shardInfo = delete.getShardInfo();
                if(shardInfo.getFailed() > MagicNumConstant.ZERO){
                    throw new BusinessException(ErrorEnum.ES_DATA_DELETE_ERROR);
                }
            } catch (IOException e) {
                LogUtil.error(LogEnum.BIZ_DATASET, "delete es data error:{}", e);
                throw new BusinessException(ErrorEnum.ES_DATA_DELETE_ERROR);
            }
        }

    }

    /**
     * ???????????????csv??????
     * 1.?????????????????????datafile???
     * 2.????????????????????????
     *
     * @param datasetCsvImportDTO ????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void tableImport(DatasetCsvImportDTO datasetCsvImportDTO) {
        Dataset dataset = datasetService.getOneById(datasetCsvImportDTO.getDatasetId());
        File file = File.builder().build().setName(datasetCsvImportDTO.getFileName())
                .setStatus(FileStateCodeConstant.NOT_ANNOTATION_FILE_STATE)
                .setDatasetId(datasetCsvImportDTO.getDatasetId())
                .setUrl(datasetCsvImportDTO.getFilePath())
                .setFileType(MagicNumConstant.TWO)
                .setPid(0L)
                .setOriginUserId(contextService.getCurUserId())
                .setExcludeHeader(datasetCsvImportDTO.getExcludeHeader()==null?true:datasetCsvImportDTO.getExcludeHeader());
        Queue<Long> dataFileIds = generatorKeyUtil.getSequenceByBusinessCode(Constant.DATA_FILE, 1);
        file.setId(dataFileIds.poll());
        baseMapper.saveList(Arrays.asList(new File[]{file}), contextService.getCurUserId(), dataset.getCreateUserId());
        Task task = Task.builder().build().setDatasetId(datasetCsvImportDTO.getDatasetId())
                .setCreateUserId(contextService.getCurUserId())
                .setLabels("")
                .setMergeColumn(StringUtils.join(datasetCsvImportDTO.getMergeColumn(), ','))
                .setFiles(Strings.join(Arrays.asList(new Long[]{file.getId()}), ','))
                .setType(DataTaskTypeEnum.CSV_IMPORT.getValue());
        taskService.createTask(task);
        //?????????????????????
        StateChangeDTO stateChangeDTO = new StateChangeDTO();
        //?????????????????????????????????
        stateChangeDTO.setObjectParam(new Object[]{datasetCsvImportDTO.getDatasetId().intValue()});
        //?????????????????????????????????
        stateChangeDTO.setStateMachineType(DataStateMachineConstant.DATA_STATE_MACHINE);
        //??????????????????
        stateChangeDTO.setEventMethodName(DataStateMachineConstant.TABLE_IMPORT_EVENT);
        StateMachineUtil.stateChange(stateChangeDTO);
    }

    /**
     * ??????????????????
     *
     * @param datasetId  ?????????ID
     * @param prefix     ????????????
     * @param recursive  ????????????
     * @return List<FileListDTO> ????????????
     */
    @Override
    public List<FileDTO> fileList(Long datasetId, String prefix, boolean recursive, String versionName, boolean isVersionFile) {
        /**
         * if(prefix == ???) {
         *     if(isVersionFile) {
         *         ?????????????????????????????????
         *     } else {
         *         ??????????????????????????????????????????
         *     }
         * } else {
         *     ??????minio????????????
         * }
         *
         */
        if (StringUtils.isEmpty(prefix)) {
            if (isVersionFile) {
                if (StringUtils.isEmpty(versionName)) {
                    versionName = datasetService.getOneById(datasetId).getCurrentVersionName();
                }
                prefix = "dataset/" + datasetId + "/versionFile/" + versionName + "/";
            } else {
                prefix = datasetService.getOneById(datasetId).getUri() + "/";
            }
        }
        return minioUtil.fileList(bucketName, prefix, recursive);
    }

    /**
     * ????????????????????????
     *
     * @param filePageDTO ???????????????????????????
     */
    @Override
    public void filePage(FilePageDTO filePageDTO, Long datasetId) {
        Dataset dataset = datasetService.getOneById(datasetId);
        filePageDTO.setFilePath(prefixPath + bucketName + "/" + dataset.getUri() + filePageDTO.getFilePath());
        fileStoreApi.filterFilePageWithPath(filePageDTO);
        if (!CollectionUtils.isEmpty(filePageDTO.getRows())) {
            for (FileDTO fileDto : filePageDTO.getRows()) {
                fileDto.setPath(fileDto.getPath().replaceFirst(filePageDTO.getFilePath(), ""));
            }
        }
    }

    @Override
    public List<FileAnnotationBO> listByDatasetIdAndVersionName(Long datasetId, String versionName) {
        return baseMapper.listByDatasetIdAndVersionName(datasetId,versionName);
    }
}