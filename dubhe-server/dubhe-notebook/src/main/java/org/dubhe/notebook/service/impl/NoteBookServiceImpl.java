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

package org.dubhe.notebook.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateBetween;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.base.constant.NumberConstant;
import org.dubhe.biz.base.constant.StringConstant;
import org.dubhe.biz.base.constant.SymbolConstant;
import org.dubhe.biz.base.context.UserContext;
import org.dubhe.biz.base.dto.NoteBookAlgorithmQueryDTO;
import org.dubhe.biz.base.dto.NoteBookAlgorithmUpdateDTO;
import org.dubhe.biz.base.dto.PtImageQueryUrlDTO;
import org.dubhe.biz.base.dto.SysUserConfigDTO;
import org.dubhe.biz.base.enums.BizEnum;
import org.dubhe.biz.base.enums.ImageSourceEnum;
import org.dubhe.biz.base.enums.ImageTypeEnum;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.service.UserContextService;
import org.dubhe.biz.base.utils.HttpUtils;
import org.dubhe.biz.base.utils.NumberUtil;
import org.dubhe.biz.base.utils.ResultUtil;
import org.dubhe.biz.base.utils.StringUtils;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.biz.base.vo.DatasetVO;
import org.dubhe.biz.base.vo.NoteBookVO;
import org.dubhe.biz.db.utils.PageUtil;
import org.dubhe.biz.db.utils.WrapperHelp;
import org.dubhe.biz.file.api.FileStoreApi;
import org.dubhe.biz.file.enums.BizPathEnum;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.redis.utils.RedisUtils;
import org.dubhe.k8s.api.JupyterResourceApi;
import org.dubhe.k8s.api.NamespaceApi;
import org.dubhe.k8s.api.PodApi;
import org.dubhe.k8s.cache.ResourceCache;
import org.dubhe.k8s.domain.PtBaseResult;
import org.dubhe.k8s.domain.resource.BizNamespace;
import org.dubhe.k8s.domain.resource.BizPod;
import org.dubhe.k8s.domain.vo.PtJupyterDeployVO;
import org.dubhe.k8s.enums.K8sResponseEnum;
import org.dubhe.k8s.utils.K8sNameTool;
import org.dubhe.notebook.client.DatasetClient;
import org.dubhe.notebook.client.ImageClient;
import org.dubhe.notebook.config.NoteBookConfig;
import org.dubhe.notebook.constants.NoteBookErrorConstant;
import org.dubhe.notebook.convert.NoteBookConvert;
import org.dubhe.notebook.convert.PtJupyterResourceConvert;
import org.dubhe.notebook.dao.NoteBookMapper;
import org.dubhe.notebook.domain.dto.NoteBookCreateDTO;
import org.dubhe.notebook.domain.dto.NoteBookListQueryDTO;
import org.dubhe.notebook.domain.dto.SourceNoteBookDTO;
import org.dubhe.notebook.domain.entity.NoteBook;
import org.dubhe.notebook.enums.NoteBookStatusEnum;
import org.dubhe.notebook.service.NoteBookService;
import org.dubhe.notebook.service.ProcessNotebookCommand;
import org.dubhe.notebook.utils.NotebookUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @description notebook????????????
 * @date 2020-04-28
 */
@Service
public class NoteBookServiceImpl implements NoteBookService {

    @Autowired
    private NoteBookMapper noteBookMapper;

    @Autowired
    private NoteBookConvert noteBookConvert;

    @Autowired
    private JupyterResourceApi jupyterResourceApi;

    @Autowired
    private PodApi podApi;

    @Autowired
    private NamespaceApi namespaceApi;

    @Autowired
    private K8sNameTool k8sNameTool;

    @Autowired
    private UserContextService userContextService;

    @Value("${user.config.notebook-delay-delete-time}")
    private Integer defaultNotebookDelayDeleteTime;


    @Autowired
    private ImageClient imageClient;

    @Autowired
    @Qualifier("hostFileStoreApiImpl")
    private FileStoreApi fileStoreApi;

    @Autowired
    private DatasetClient datasetClient;

    @Autowired
    private NoteBookConfig noteBookConfig;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private ResourceCache resourceCache;

    @Value("Task:Notebook:"+"${spring.profiles.active}_notebook_id_")
    private String notebookIdPrefix;

    /**
     * ?????????????????? notebook ??????
     *
     * @param page                 ????????????
     * @param noteBookListQueryDTO ????????????
     * @return Map<String, Object> ??????????????????
     */
    @Override
    public Map<String, Object> getNoteBookList(Page page, NoteBookListQueryDTO noteBookListQueryDTO) {
        QueryWrapper<NoteBook> queryWrapper = WrapperHelp.getWrapper(noteBookListQueryDTO);
        queryWrapper.ne(NoteBook.COLUMN_STATUS, NoteBookStatusEnum.DELETED.getCode())
                .ne("deleted", NoteBookStatusEnum.STOP.getCode());
        if (noteBookListQueryDTO.getStatus() != null) {
            if (noteBookListQueryDTO.getStatus().equals(NoteBookStatusEnum.RUN.getCode())) {
                //????????????notebook?????????url
                queryWrapper.eq(NoteBook.COLUMN_STATUS, NoteBookStatusEnum.RUN.getCode())
                        .ne(NoteBook.COLUMN_URL, SymbolConstant.BLANK);
            } else if (noteBookListQueryDTO.getStatus().equals(NoteBookStatusEnum.STARTING.getCode())) {
                //????????????notebook???????????????????????????url
                queryWrapper.and((qw) ->
                        qw.eq(NoteBook.COLUMN_STATUS, NoteBookStatusEnum.RUN.getCode()).eq(NoteBook.COLUMN_URL, SymbolConstant.BLANK)
                                .or()
                                .eq(NoteBook.COLUMN_STATUS, NoteBookStatusEnum.STARTING.getCode())
                );
            } else {
                // ??????????????????
                queryWrapper.eq(NoteBook.COLUMN_STATUS, noteBookListQueryDTO.getStatus());
            }
        }
        queryWrapper.orderBy(true, false, "id");
        IPage<NoteBook> noteBookPage = noteBookMapper.selectPage(page, queryWrapper);
        return PageUtil.toPage(noteBookPage, noteBookConvert::toDto);
    }

    /**
     * ????????????notebook??????
     *
     * @param page                ????????????
     * @param noteBookStatusEnums notebook????????????
     * @return notebook??????
     */
    @Override
    public List<NoteBook> getList(Page page, NoteBookStatusEnum... noteBookStatusEnums) {

        LambdaQueryWrapper<NoteBook> queryWrapper = new LambdaQueryWrapper<>();
        List<Integer> status = Arrays.asList(noteBookStatusEnums).stream().map(x -> x.getCode()).collect(Collectors.toList());
        queryWrapper.in(noteBookStatusEnums != null, NoteBook::getStatus, status);

        return noteBookMapper.selectPage(page, queryWrapper).getRecords();
    }

    /**
     * ????????????????????????
     *
     * @return String ????????????
     */
    private String getDefaultImage() {
        PtImageQueryUrlDTO imageQueryUrlDTO = new PtImageQueryUrlDTO();
        List<Integer> notebookImageType = new ArrayList(){{
            add(ImageTypeEnum.NOTEBOOK.getType());
        }};
        imageQueryUrlDTO.setImageTypes(notebookImageType).setImageResource(ImageSourceEnum.PRE.getCode()).setIsDefault(true);
        DataResponseBody<String> responseBody = imageClient.getImageUrl(imageQueryUrlDTO);
        if (!responseBody.succeed()) {
            LogUtil.error(LogEnum.NOTE_BOOK, "dubhe-image service call failed, responseBody is ???{}???", responseBody);
            throw new BusinessException("????????????????????????");
        }

        String imageUrl = responseBody.getData();
        if (StringUtils.isBlank(imageUrl)) {
            LogUtil.error(LogEnum.NOTE_BOOK, "There is no default notebook image !");
            throw new BusinessException(ImageTypeEnum.NOTEBOOK.getCode() + "????????????????????????");
        }

        return imageUrl;
    }

    /**
     * ????????????????????????
     *
     * @param noteBookName notebook??????
     * @return true??????????????? false ??????????????????
     */
    public boolean existsName(String noteBookName) {

        LambdaQueryWrapper<NoteBook> queryWrapper = new LambdaQueryWrapper();

        queryWrapper.eq(NoteBook::getName, noteBookName);

        int res = noteBookMapper.selectCount(queryWrapper);

        return res > 0;
    }

    /**
     * ????????? notebook
     *
     * @param createDTO notebook????????????
     * @return NoteBookVO notebook vo??????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteBookVO createNoteBook(NoteBookCreateDTO createDTO) {
        NoteBook noteBook = new NoteBook();

        if (createDTO.getDataSourceId() != null) {
            DataResponseBody<DatasetVO> responseBody = datasetClient.get(createDTO.getDataSourceId());
            if (responseBody.succeed()) {
                if (responseBody.getData() != null) {
                    noteBook.setDataSourceName(responseBody.getData().getName());
                } else {
                    throw new BusinessException("????????????????????????");
                }
            } else {
                throw new BusinessException("???????????????????????????");
            }
        }

        BeanUtils.copyProperties(createDTO, noteBook);
        return createNoteBook(noteBook);
    }

    /**
     * ????????? notebook
     *
     * @param noteBook notebook
     * @return NoteBookVO notebook vo
     */
    @Transactional(rollbackFor = Exception.class)
    public NoteBookVO createNoteBook(NoteBook noteBook) {

        long curUserId = userContextService.getCurUserId();

        String noteBookName = noteBook.getNoteBookName();
        if (existsName(noteBook.getNoteBookName())) {
            LogUtil.error(LogEnum.NOTE_BOOK, "The name ???{}??? of notebook already exists ", noteBookName);
            throw new BusinessException("Notebook???????????????????????????????????????");
        }

        String dataSourcePath = noteBook.getDataSourcePath();
        if (StringUtils.isNotEmpty(dataSourcePath)) {
            if (fileStoreApi.fileOrDirIsExist(fileStoreApi.getBucket() + File.separator + dataSourcePath)) {
                noteBook.setDataSourcePath(dataSourcePath);
            } else {
                LogUtil.error(LogEnum.NOTE_BOOK, "Data source path ???{}??? doesn't exist!", dataSourcePath);
                throw new BusinessException("??????????????????????????????");
            }
        }

        noteBook.setName(k8sNameTool.getK8sName());

        noteBook.setK8sNamespace(k8sNameTool.generateNamespace(curUserId));

        noteBook.setK8sResourceName(k8sNameTool.generateResourceName(BizEnum.NOTEBOOK, noteBook.getName()));
        if (StringUtils.isBlank(noteBook.getK8sPvcPath())) {
            noteBook.setK8sPvcPath(k8sNameTool.getPath(BizPathEnum.ALGORITHM, curUserId));
        }
        noteBook.setCreateResource(BizPathEnum.NOTEBOOK.getCreateResource());
        noteBook.setK8sMountPath(NotebookUtil.getK8sMountPath());
        String taskIdentify = StringUtils.getUUID();
        if (start(noteBook, taskIdentify)) {
            noteBook.setStatus(NoteBookStatusEnum.STARTING.getCode());
        } else {
            noteBook.setStatus(NoteBookStatusEnum.STOP.getCode());
        }
        noteBookMapper.insert(noteBook);
        resourceCache.addTaskCache(taskIdentify,noteBook.getId(), noteBookName, notebookIdPrefix);
        return noteBookConvert.toDto(noteBook);
    }

    /**
     * ?????????namespace
     *
     * @param noteBook notebook
     * @param labels   ????????????
     * @return boolean ??????????????? true ?????? false ??????
     */
    private boolean initNameSpace(NoteBook noteBook, Map<String, String> labels) {
        try {
            BizNamespace result = namespaceApi.create(noteBook.getK8sNamespace(), labels);
            noteBook.setK8sStatusCode(result.getCode() == null ? SymbolConstant.BLANK : result.getCode());
            noteBook.setK8sStatusInfo(NotebookUtil.getK8sStatusInfo(result));
            return (HttpUtils.isSuccess(result.getCode())
                    || K8sResponseEnum.EXISTS.getCode().equals(result.getCode()));
        } catch (Exception e) {
            LogUtil.error(LogEnum.NOTE_BOOK, "createNoteBook??????jupyterResourceApi.createNamespace?????????{}", e);
            noteBook.setK8sStatusCode(SymbolConstant.BLANK);
            noteBook.setK8sStatusInfo(NotebookUtil.getK8sStatusInfo(e));
            return false;
        }
    }

    /**
     * ?????? notebook ???????????????
     *
     * @param noteBookIds notebook id ??????
     * @return List<NoteBook> ????????????notebook??????
     */
    @Override
    public List<NoteBook> validateDeletableNoteBook(Set<Long> noteBookIds) {
        for (Long noteBookId : noteBookIds) {
            NumberUtil.isNumber(noteBookId);
        }

        List<NoteBook> noteBookList = noteBookMapper.selectBatchIds(noteBookIds);
        for (NoteBook noteBook : noteBookList) {
            if (!NoteBookStatusEnum.deletable(noteBook.getStatus())) {
                throw new BusinessException("???????????????????????????notebook???");
            }
        }
        return noteBookList;
    }

    /**
     * ????????????notebook
     *
     * @param noteBookIds notebook id ??????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNoteBooks(Set<Long> noteBookIds) {
        List<NoteBook> noteBookList = validateDeletableNoteBook(noteBookIds);
        if (CollUtil.isNotEmpty(noteBookList)) {
            for (NoteBook noteBook : noteBookList) {
                noteBook.setStatus(NoteBookStatusEnum.DELETING.getCode());
                noteBookMapper.updateById(noteBook);
                String taskIdentify = (String) redisUtils.get(notebookIdPrefix + String.valueOf(noteBook.getId()));
                if (StringUtils.isNotEmpty(taskIdentify)){
                    redisUtils.del(taskIdentify, notebookIdPrefix + String.valueOf(noteBook.getId()));
                }
            }
        }
    }

    /**
     * ??????notebook
     *
     * @param noteBookId notebook id
     * @return String ??????????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String startNoteBook(Long noteBookId) {
        NumberUtil.isNumber(noteBookId);
        NoteBook noteBook = noteBookMapper.selectById(noteBookId);
        return startNoteBook(noteBook);
    }

    /**
     * ????????????notebook??????
     *
     * @param noteBook notebook
     * @return String ??????????????????
     */
    private String startNoteBook(NoteBook noteBook) {
        if (noteBook == null) {
            throw new BusinessException(NotebookUtil.NOTEBOOK_NOT_EXISTS);
        }
        if (NoteBookStatusEnum.RUN.getCode().equals(noteBook.getStatus())) {
            return "notebook " + NoteBookStatusEnum.RUN.getDescription();
        } else if (NoteBookStatusEnum.STARTING.getCode().equals(noteBook.getStatus())) {
            return "notebook " + NoteBookStatusEnum.STARTING.getDescription();
        } else if (!NoteBookStatusEnum.STOP.getCode().equals(noteBook.getStatus())) {
            throw new BusinessException("notebook???" + noteBook.getName() + "??????????????????" + NoteBookStatusEnum.getDescription(noteBook.getStatus()) + ",?????????????????????");
        }
        String returnStr;
        String taskIdentify = resourceCache.getTaskIdentify(noteBook.getId(), noteBook.getNoteBookName(), notebookIdPrefix);
        if (start(noteBook, taskIdentify)) {
            noteBook.setStatus(NoteBookStatusEnum.STARTING.getCode());
            returnStr = NoteBookStatusEnum.STARTING.getDescription();
        } else {
            // ??????notebook????????????????????????
            returnStr = "??????" + NotebookUtil.FAILED;
        }
        this.updateById(noteBook);
        return returnStr;
    }

    /**
     * ??????notebook
     *
     * @param noteBook ???????????????notebook
     * @return NoteBook ????????????notebook
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteBook updateById(NoteBook noteBook) {
        noteBook.setUpdateTime(null);
        noteBook.setUpdateUserId(NotebookUtil.getCurUserId(userContextService));
        if (StringUtils.isBlank(noteBook.getStatusDetail())) {
            noteBook.setStatusDetail(SymbolConstant.BRACKETS);
        }
        noteBookMapper.updateById(noteBook);
        return noteBook;
    }

    /**
     * ??????notebook
     *
     * @param noteBook notebook
     * @return true ???????????????false ????????????
     */
    private boolean start(NoteBook noteBook, String taskIdentify) {
        Long curUserId = userContextService.getCurUserId();
        if (StringUtils.isBlank(noteBook.getPipSitePackagePath())) {
            String pipSitePackagePath = StringConstant.PIP_SITE_PACKAGE + SymbolConstant.SLASH + curUserId + SymbolConstant.SLASH + noteBook.getName() + SymbolConstant.SLASH;
            noteBook.setPipSitePackagePath(pipSitePackagePath);
        }
        // ??????????????????
        noteBook.setLastStartTime(new Date());
        // ?????????????????????
        noteBook.setLastOperationTimeout(NotebookUtil.getTimeoutSecondLong());
        if (initNameSpace(noteBook, null)) {
            try {
                // ??????Notebook????????????????????????????????????????????????
                int notebookDelayDeleteTime = getNotebookDelayDeleteTime() * 60;
                //??????????????????PVC
                PtJupyterDeployVO result = jupyterResourceApi.create(PtJupyterResourceConvert.toPtJupyterResourceBo(noteBook, k8sNameTool, notebookDelayDeleteTime, taskIdentify));
                noteBook.setK8sStatusCode(result.getCode() == null ? SymbolConstant.BLANK : result.getCode());
                noteBook.setK8sStatusInfo(NotebookUtil.getK8sStatusInfo(result));
                if (!result.isSuccess()) {
                    noteBook.putStatusDetail(noteBook.getK8sResourceName(), result.getMessage());
                }
                return HttpUtils.isSuccess(result.getCode());
            } catch (Exception e) {
                LogUtil.error(LogEnum.NOTE_BOOK, "There is an error when create jupyter resource, the exception is ???{}???", e);
                noteBook.setK8sStatusCode(SymbolConstant.BLANK);
                noteBook.setK8sStatusInfo(NotebookUtil.getK8sStatusInfo(e));
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * ??????notebook
     *
     * @param noteBookId notebook id
     * @return String ??????notebook???????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String stopNoteBook(Long noteBookId) {
        NumberUtil.isNumber(noteBookId);
        NoteBook noteBook = noteBookMapper.selectById(noteBookId);
        ResultUtil.notNull(noteBook, NoteBookErrorConstant.NOTEBOOK_NOT_EXISTS);
        ResultUtil.isEquals(NoteBookStatusEnum.RUN.getCode(), noteBook.getStatus(),
                NoteBookErrorConstant.INVALID_NOTEBOOK_STATUS);

        String returnStr;
        NoteBookStatusEnum statusEnum = getStatus(noteBook);
        if (NoteBookStatusEnum.STOP == statusEnum) {
            noteBook.setK8sStatusCode(SymbolConstant.BLANK);
            noteBook.setK8sStatusInfo(SymbolConstant.BLANK);
            noteBook.setUrl(SymbolConstant.BLANK);
            noteBook.setStatus(NoteBookStatusEnum.STOP.getCode());
            returnStr = "?????????";
        } else {
            try {
                PtBaseResult result = jupyterResourceApi.delete(noteBook.getK8sNamespace(), noteBook.getK8sResourceName());
                noteBook.setK8sStatusCode(result.getCode() == null ? SymbolConstant.BLANK : result.getCode());
                noteBook.setK8sStatusInfo(NotebookUtil.getK8sStatusInfo(result));
                if (HttpUtils.isSuccess(result.getCode())) {
                    noteBook.setStatus(NoteBookStatusEnum.STOPPING.getCode());
                    // ?????????????????????
                    noteBook.setLastOperationTimeout(NotebookUtil.getTimeoutSecondLong());
                    noteBook.setUrl(SymbolConstant.BLANK);
                    returnStr = NoteBookStatusEnum.STOPPING.getDescription();
                } else if (K8sResponseEnum.REPEAT.getCode().equals(result.getCode())) {
                    // ??????????????????????????????????????????????????????????????????
                    noteBook.setStatus(NoteBookStatusEnum.STOP.getCode());
                    noteBook.setUrl(SymbolConstant.BLANK);
                    returnStr = NoteBookStatusEnum.STOP.getDescription();
                } else {
                    // ?????????????????? -> ????????????,???????????????
                    returnStr = "??????" + NotebookUtil.FAILED;
                }
            } catch (Exception e) {
                LogUtil.error(LogEnum.NOTE_BOOK, "??????notebook??????jupyterResourceApi.delete?????????{}", e);
                noteBook.setK8sStatusCode(SymbolConstant.BLANK);
                noteBook.setK8sStatusInfo(NotebookUtil.getK8sStatusInfo(e));
                returnStr = "??????" + NotebookUtil.FAILED;
            }
        }
        this.updateById(noteBook);
        return returnStr;
    }

    /**
     * @see NoteBookService#batchStopNoteBooks()
     */
    @Override
    public void batchStopNoteBooks() {
        List<NoteBook> noteBooks = noteBookMapper.selectRunningList();
        if (CollectionUtils.isEmpty(noteBooks)) {
            return;
        }
        noteBooks.forEach(noteBook -> stopNoteBook(noteBook.getId()));
    }

    /**
     * ??????notebook
     *
     * @param noteBookId notebook id
     * @return String ??????notebook???????????????
     */
    @Override
    public String openNoteBook(Long noteBookId) {
        NumberUtil.isNumber(noteBookId);
        NoteBook noteBook = noteBookMapper.selectById(noteBookId);
        if (noteBook == null) {
            throw new BusinessException(NotebookUtil.NOTEBOOK_NOT_EXISTS);
        } else if (NoteBookStatusEnum.RUN.getCode().equals(noteBook.getStatus())) {
            if (NotebookUtil.checkUrlContainsToken(noteBook.getUrl())) {
                return noteBook.getUrl();
            } else {
                // ??????:?????????notebook?????????????????????
                String jupyterUrlWithToken = this.getJupyterUrl(noteBook);
                if (NotebookUtil.checkUrlContainsToken(jupyterUrlWithToken)) {
                    noteBook.setUrl(jupyterUrlWithToken);
                    this.updateById(noteBook);
                    return noteBook.getUrl();
                } else {
                    throw new BusinessException("notebook????????? ??????URL?????????");
                }
            }
        } else {
            throw new BusinessException("notebook ??????????????????,???????????????");
        }
    }

    /**
     * ??????jupyter ??????
     *
     * @param noteBook notebook
     * @return String jupyter??????
     */
    @Override
    public String getJupyterUrl(NoteBook noteBook) {
        try {
            return podApi.getUrlByResourceName(noteBook.getK8sNamespace(), noteBook.getK8sResourceName());
        } catch (Exception e) {
            LogUtil.error(LogEnum.NOTE_BOOK, "notebook nameSpace ???{}??? resourceName ???{}??? ??????URL?????????", noteBook.getK8sNamespace(), noteBook.getK8sResourceName(), e);
            noteBook.setK8sStatusCode(SymbolConstant.BLANK);
            noteBook.setK8sStatusInfo(NotebookUtil.getK8sStatusInfo(e));
            return null;
        }
    }

    /**
     * ??????notebook??????
     *
     * @param noteBook notebook
     * @return NoteBookStatusEnum notebook??????
     */
    @Override
    public NoteBookStatusEnum getStatus(NoteBook noteBook) {
        try {
            BizPod result = podApi.getWithResourceName(noteBook.getK8sNamespace(), noteBook.getK8sResourceName());
            noteBook.setK8sStatusCode(result.getCode() == null ? SymbolConstant.BLANK : result.getCode());
            noteBook.setK8sStatusInfo(NotebookUtil.getK8sStatusInfo(result));
            if (K8sResponseEnum.NOT_FOUND.getCode().equals(result.getCode())) {

                long gap = new DateBetween(noteBook.getLastStartTime(), new Date()).between(DateUnit.MINUTE);
                // ????????????
                if (gap < NumberConstant.NUMBER_2) {
                    return null;
                }
                // ???????????????????????????
                return NoteBookStatusEnum.STOP;
            } else if (!HttpUtils.isSuccess(result.getCode())) {
                LogUtil.warn(LogEnum.NOTE_BOOK, "Fail to get status ,notebook nameSpace is ???{}???, resourceName is ???{}??? ???", noteBook.getK8sNamespace(), noteBook.getK8sResourceName());
                return null;
            }
            return NoteBookStatusEnum.convert(result.getPhase());
        } catch (Exception e) {
            LogUtil.error(LogEnum.NOTE_BOOK, "Fail to get status ,notebook nameSpace is ???{}???, resourceName is ???{}??? ???Exception is ???{}???", noteBook.getK8sNamespace(), noteBook.getK8sResourceName(), e);
            noteBook.setK8sStatusCode(SymbolConstant.BLANK);
            noteBook.setK8sStatusInfo(NotebookUtil.getK8sStatusInfo(e));
            return null;
        }
    }

    /**
     * ???????????????notebook
     *
     * @param bizPathEnum       ??????????????????
     * @param sourceNoteBookDTO ???????????????NoteBook????????????
     * @return NoteBookVO notebook???????????????
     */
    @Override
    public NoteBookVO createNoteBookByThirdParty(BizPathEnum bizPathEnum, SourceNoteBookDTO sourceNoteBookDTO) {
        String k8sPvcPath = sourceNoteBookDTO.getSourceFilePath();
        Long curUserId = userContextService.getCurUserId();
        LambdaQueryWrapper<NoteBook> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.eq(NoteBook::getK8sPvcPath, k8sPvcPath);
        queryWrapper.eq(NoteBook::getOriginUserId, curUserId);
        queryWrapper.last(" limit 1 ");
        NoteBook noteBook = noteBookMapper.selectOne(queryWrapper);
        if (noteBook == null) {
            NoteBook newNoteBook = initSourceReqNoteBook(bizPathEnum, sourceNoteBookDTO, k8sPvcPath);
            return this.createNoteBook(newNoteBook);
        } else {
            if (!NoteBookStatusEnum.RUN.getCode().equals(noteBook.getStatus())) {
                this.startNoteBook(noteBook);
            }
            return noteBookConvert.toDto(noteBook);
        }
    }

    /**
     * ???????????????????????????notebook
     *
     * @param bizPathEnum       ??????????????????
     * @param sourceNoteBookDTO ???????????????NoteBook????????????
     * @param k8sPvcPath        k8s pvc??????
     * @return NoteBook notebook
     */
    private NoteBook initSourceReqNoteBook(BizPathEnum bizPathEnum, SourceNoteBookDTO sourceNoteBookDTO, String k8sPvcPath) {
        NoteBook noteBook = new NoteBook();


        noteBook.setCreateResource(bizPathEnum.getCreateResource());
        noteBook.setDescription(bizPathEnum.getBizName());
        noteBook.setName(k8sNameTool.getK8sName());
        String notebookName = NotebookUtil.generateName(bizPathEnum, sourceNoteBookDTO.getSourceId());
        if (existsName(notebookName)) {
            // ????????????????????????
            notebookName += RandomUtil.randomString(MagicNumConstant.TWO);
        }

        noteBook.setNoteBookName(notebookName);
        noteBook.setCpuNum(noteBookConfig.getCpuNum());
        noteBook.setGpuNum(noteBookConfig.getGpuNum());
        noteBook.setMemNum(noteBookConfig.getMemNum());
        noteBook.setDiskMemNum(noteBookConfig.getDiskMemNum());
        noteBook.setAlgorithmId(sourceNoteBookDTO.getSourceId());

        noteBook.setK8sPvcPath(k8sPvcPath);
        noteBook.setK8sImageName(getDefaultImage());
        return noteBook;
    }

    /**
     * ????????????
     *
     * @param noteBookId notebook id
     * @return String url??????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String getAddress(Long noteBookId) {
        NumberUtil.isNumber(noteBookId);
        NoteBook noteBook = noteBookMapper.selectById(noteBookId);
        if (noteBook == null) {
            throw new BusinessException(NotebookUtil.NOTEBOOK_NOT_EXISTS);
        } else if (NoteBookStatusEnum.RUN.getCode().equals(noteBook.getStatus())) {
            if (NotebookUtil.checkUrlContainsToken(noteBook.getUrl())) {
                return noteBook.getUrl();
            } else {
                // ??????:?????????notebook?????????????????????
                String jupyterUrlWithToken = this.getJupyterUrl(noteBook);
                if (NotebookUtil.checkUrlContainsToken(jupyterUrlWithToken)) {
                    noteBook.setUrl(jupyterUrlWithToken);
                    this.updateById(noteBook);
                    return noteBook.getUrl();
                }
            }
        }
        return null;
    }

    /**
     * ?????????????????????notebook??????
     *
     * @return int notebook??????
     */
    @Override
    public int getNoteBookRunNumber() {
        return noteBookMapper.selectRunNoteBookNum(NoteBookStatusEnum.RUN.getCode());
    }

    /**
     * ??????notebook??????
     *
     * @param statusEnum notebook ????????????
     * @param noteBook   notebook
     * @return boolean true ???????????? false ????????????
     */
    @Override
    public boolean refreshNoteBookStatus(NoteBookStatusEnum statusEnum, NoteBook noteBook) {
        return refreshNoteBookStatus(statusEnum, noteBook, new ProcessNotebookCommand());
    }

    /**
     * ??????notebook??????
     *
     * @param statusEnum             notebook ????????????
     * @param noteBook               notebook
     * @param processNotebookCommand ??????notebook???????????????????????????
     * @return boolean true ???????????? false ????????????
     */
    @Override
    public boolean refreshNoteBookStatus(NoteBookStatusEnum statusEnum, NoteBook noteBook, ProcessNotebookCommand processNotebookCommand) {
        if (statusEnum == null || noteBook == null) {
            return false;
        }
        if (statusEnum.getCode().equals(noteBook.getStatus())) {
            return false;
        }

        // ??????notebook (?????????->??????)
        if (NoteBookStatusEnum.RUN == statusEnum) {
            if (NoteBookStatusEnum.STARTING.getCode().equals(noteBook.getStatus())) {
                noteBook.setUrl(this.getJupyterUrl(noteBook));
                noteBook.setStatus(NoteBookStatusEnum.RUN.getCode());
                processNotebookCommand.running(noteBook);
                updateById(noteBook);
                return true;
            }
        } else if (NoteBookStatusEnum.STOP == statusEnum) {
            //??????notebook (?????????->??????)
            if (NoteBookStatusEnum.DELETING.getCode().equals(noteBook.getStatus())) {
                processNotebookCommand.delete(noteBook);
                noteBookMapper.deleteById(noteBook.getId());
                return true;
            }
            noteBook.setUrl(SymbolConstant.BLANK);
            noteBook.setStatus(NoteBookStatusEnum.STOP.getCode());
            noteBook.setStatusDetail(SymbolConstant.BLANK);
            jupyterResourceApi.delete(noteBook.getK8sNamespace(),noteBook.getK8sResourceName());
            processNotebookCommand.stop(noteBook);
            updateById(noteBook);
            return true;
        }
        return false;
    }

    /**
     * ??????notebook??????
     *
     * @param noteBookIds notebook id ??????
     * @return List<NoteBookVO> notebook vo ??????
     */
    @Override
    public List<NoteBookVO> getNotebookDetail(Set<Long> noteBookIds) {
        QueryWrapper<NoteBook> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("id", noteBookIds);
        List<NoteBook> noteBookList = noteBookMapper.selectList(queryWrapper);
        return noteBookConvert.toDto(noteBookList);
    }

    /**
     * ??????notebook??????
     *
     * @param noteBookId notebook id
     * @return List<NoteBookVO> notebook vo ??????
     */
    @Override
    public NoteBookVO getNotebookDetail(Long noteBookId) {
        NoteBook noteBook = noteBookMapper.selectById(noteBookId);
        return noteBookConvert.toDto(noteBook);
    }



    /**
     * ???????????????????????????URL???notebook
     *
     * @param page ????????????
     * @return List<NoteBook> notebook??????
     */
    @Override
    public List<NoteBook> getRunNotUrlList(Page page) {
        return noteBookMapper.selectRunNotUrlList(page, NoteBookStatusEnum.RUN.getCode());
    }

    /**
     * ??????notebook??????ID
     *
     * @param noteBookAlgorithmListQueryDTO ????????????notebook??????
     * @return ??????notebook??????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateNoteBookAlgorithm(NoteBookAlgorithmUpdateDTO noteBookAlgorithmListQueryDTO) {
        if (CollectionUtils.isEmpty(noteBookAlgorithmListQueryDTO.getNotebookIdList())) {
            return 0;
        }
        return noteBookMapper.updateNoteBookAlgorithm(noteBookAlgorithmListQueryDTO.getNotebookIdList(), noteBookAlgorithmListQueryDTO.getAlgorithmId());
    }

    /**
     * ????????????ID??????notebook Id
     *
     * @param noteBookAlgorithmQueryDTO ????????????notebook??????
     * @return notebook id??????
     */
    @Override
    public List<Long> getNoteBookIdByAlgorithm(NoteBookAlgorithmQueryDTO noteBookAlgorithmQueryDTO) {
        if (CollectionUtils.isEmpty(noteBookAlgorithmQueryDTO.getAlgorithmIdList())) {
            return Collections.emptyList();
        }
        return noteBookMapper.getNoteBookIdByAlgorithm(noteBookAlgorithmQueryDTO.getAlgorithmIdList());
    }

    /**
     * ?????? Notebook ??????????????????
     */
    private int getNotebookDelayDeleteTime() {

        UserContext curUser = userContextService.getCurUser();
        SysUserConfigDTO userConfig = curUser.getUserConfig();
        // ??????????????????????????? Notebook ??????????????????
        Integer notebookDelayDeleteTime = userConfig.getNotebookDelayDeleteTime();
        if (userConfig.getNotebookDelayDeleteTime() != null) {
            return notebookDelayDeleteTime;
        }

        // ????????????????????? Notebook ?????????????????????????????????????????????
        return defaultNotebookDelayDeleteTime;
    }
}
