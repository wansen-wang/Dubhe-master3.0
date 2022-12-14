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
package org.dubhe.train.async;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.base.constant.SymbolConstant;
import org.dubhe.biz.base.enums.BizEnum;
import org.dubhe.biz.base.enums.ModelResourceEnum;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.utils.StringUtils;
import org.dubhe.biz.file.api.FileStoreApi;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.k8s.api.DistributeTrainApi;
import org.dubhe.k8s.api.NamespaceApi;
import org.dubhe.k8s.api.TrainJobApi;
import org.dubhe.k8s.domain.bo.DistributeTrainBO;
import org.dubhe.k8s.domain.bo.PtJupyterJobBO;
import org.dubhe.k8s.domain.resource.BizDistributeTrain;
import org.dubhe.k8s.domain.resource.BizNamespace;
import org.dubhe.k8s.domain.vo.PtJupyterJobVO;
import org.dubhe.k8s.utils.K8sNameTool;
import org.dubhe.train.config.TrainJobConfig;
import org.dubhe.train.constant.TrainConstant;
import org.dubhe.train.dao.PtTrainJobMapper;
import org.dubhe.train.domain.dto.BaseTrainJobDTO;
import org.dubhe.train.domain.entity.PtTrainJob;
import org.dubhe.train.domain.vo.PtImageAndAlgorithmVO;
import org.dubhe.train.enums.ResourcesPoolTypeEnum;
import org.dubhe.train.enums.TrainJobStatusEnum;
import org.dubhe.train.enums.TrainSystemRunParamEnum;
import org.dubhe.train.inner.factory.SystemRunParamFactory;
import org.dubhe.train.inner.handler.SystemRunParamHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @description ??????????????????
 * @date 2020-07-17
 */
@Component
public class TrainJobAsync {

    @Autowired
    private K8sNameTool k8sNameTool;

    @Autowired
    private NamespaceApi namespaceApi;

    @Autowired
    private TrainJobConfig trainJobConfig;

    @Resource(name = "hostFileStoreApiImpl")
    private FileStoreApi fileStoreApi;

    @Autowired
    private PtTrainJobMapper ptTrainJobMapper;

    @Autowired
    private TrainJobApi trainJobApi;

    @Autowired
    private DistributeTrainApi distributeTrainApi;

    @Resource
    private SystemRunParamFactory systemRunParamFactory;

    /**
     * ?????????????????????
     *
     * @param baseTrainJobDTO       ??????????????????
     * @param userId                ??????id
     * @param ptImageAndAlgorithmVO ?????????????????????
     * @param ptTrainJob            ????????????????????????
     */
    public void doDistributedJob(BaseTrainJobDTO baseTrainJobDTO, Long userId, PtImageAndAlgorithmVO ptImageAndAlgorithmVO, PtTrainJob ptTrainJob) {
        try {
            //???????????????????????????namespace,?????????????????????
            String namespace = getNamespace(userId);
            // ??????DistributeTrainBO
            DistributeTrainBO bo = buildDistributeTrainBO(baseTrainJobDTO, userId, ptImageAndAlgorithmVO, ptTrainJob, namespace);
            if (null == bo) {
                LogUtil.error(LogEnum.BIZ_TRAIN, "user {} failed to create train Job ,distributeTrainBO is empty, the namespace is {}", userId, namespace);
                ptTrainJob.putStatusDetail("Message", "????????????");
                updateTrainStatus(userId, ptTrainJob, baseTrainJobDTO, "", false);
                return;
            }
            // ??????K8s
            BizDistributeTrain bizDistributeTrain = distributeTrainApi.create(bo);
            if (bizDistributeTrain.isSuccess()) {
                // ????????????
                updateTrainStatus(userId, ptTrainJob, baseTrainJobDTO, bizDistributeTrain.getName(), true);
            } else {
                // ????????????
                String message = null == bizDistributeTrain.getMessage() ? "???????????????" : bizDistributeTrain.getMessage();
                LogUtil.error(LogEnum.BIZ_TRAIN, "userId {} create Distribute Train, K8s creation failed, the received parameters are {}, the wrong information is{}", userId, bo, message);
                ptTrainJob.putStatusDetail("Message", message);
                updateTrainStatus(userId, ptTrainJob, baseTrainJobDTO, bizDistributeTrain.getName(), false);
            }
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_TRAIN, "doDistributedJob ERROR???{} ", e);
            ptTrainJob.putStatusDetail("Message", "????????????");
            updateTrainStatus(userId, ptTrainJob, baseTrainJobDTO, "", false);
        }
    }

    /**
     * ?????????????????????DistributeTrainBO(????????????????????????)
     *
     * @param baseTrainJobDTO       ??????????????????
     * @param userId                ??????id
     * @param ptImageAndAlgorithmVO ?????????????????????
     * @param ptTrainJob            ????????????????????????
     * @param namespace             ????????????
     * @return DistributeTrainBO
     */
    private DistributeTrainBO buildDistributeTrainBO(BaseTrainJobDTO baseTrainJobDTO, Long userId, PtImageAndAlgorithmVO ptImageAndAlgorithmVO, PtTrainJob ptTrainJob, String namespace) {
        //????????????
        String basePath = fileStoreApi.getBucket() + trainJobConfig.getManage() + StrUtil.SLASH
                + userId + StrUtil.SLASH + baseTrainJobDTO.getJobName();
        //????????????
        String relativePath = StrUtil.SLASH + trainJobConfig.getManage() + StrUtil.SLASH
                + userId + StrUtil.SLASH + baseTrainJobDTO.getJobName();
        String[] codeDirArray = ptImageAndAlgorithmVO.getCodeDir().split(StrUtil.SLASH);
        String workspaceDir = codeDirArray[codeDirArray.length - 1];
        // ??????????????????????????????
        String sourcePath = fileStoreApi.getBucket() + ptImageAndAlgorithmVO.getCodeDir().substring(1);
        String trainDir = basePath.substring(1) + StrUtil.SLASH + workspaceDir;

        if (!fileStoreApi.copyPath(fileStoreApi.getRootDir() + sourcePath, fileStoreApi.getRootDir() + trainDir)) {
            LogUtil.error(LogEnum.BIZ_TRAIN, "buildDistributeTrainBO copyPath failed ! sourcePath:{},basePath:{},trainDir:{}", sourcePath, basePath, trainDir);
            return null;
        }
        // ????????????
        String paramPrefix = trainJobConfig.getPythonFormat();
        // ????????????????????????????????????????????????IP
        StringBuilder sb = new StringBuilder("export NODE_IPS=`cat /home/hostfile.json |jq -r \".[]|.ip\"|paste -d \",\" -s` ");
        // ????????????????????????
        sb.append(" && cd ").append(trainJobConfig.getDockerTrainPath()).append(StrUtil.SLASH).append(workspaceDir).append(" && ");
        // ?????????????????????python????????????
        sb.append(ptImageAndAlgorithmVO.getRunCommand());
        // ??????python???????????? ??????IP
        sb.append(paramPrefix).append(trainJobConfig.getNodeIps()).append("=\"$NODE_IPS\" ");
        // ??????python???????????? ????????????
        sb.append(paramPrefix).append(trainJobConfig.getNodeNum()).append(SymbolConstant.FLAG_EQUAL).append(ptTrainJob.getResourcesPoolNode()).append(StrUtil.SPACE);

        // ??????python???????????? ?????????
        sb.append(paramPrefix).append(trainJobConfig.getDockerDataset());


        // ????????????????????????????????????
        DistributeTrainBO distributeTrainBO = new DistributeTrainBO();

        for (TrainSystemRunParamEnum systemRunParamEnum : TrainSystemRunParamEnum.values()) {
            SystemRunParamHandler systemRunParamHandler = systemRunParamFactory.getHandler(systemRunParamEnum);
            if (systemRunParamHandler == null) {
                continue;
            }
            String paramValue = systemRunParamHandler.buildSystemRunCommand(null, userId, distributeTrainBO, baseTrainJobDTO, ptImageAndAlgorithmVO.getIsTrainModelOut(),
                    ptImageAndAlgorithmVO.getIsTrainOut(), ptImageAndAlgorithmVO.getIsVisualizedLog(), systemRunParamEnum.name(), true);
            if (StringUtils.isNotBlank(paramValue)) {
                sb.append(paramValue);
            }
        }

        buildBoAboutModel(baseTrainJobDTO, distributeTrainBO, sb);

        JSONObject runParams = baseTrainJobDTO.getRunParams();
        if (null != runParams && !runParams.isEmpty()) {
            // ???????????????????????????
            runParams.entrySet().forEach(entry ->
                    sb.append(paramPrefix).append(entry.getKey()).append(SymbolConstant.FLAG_EQUAL).append(entry.getValue()).append(StrUtil.SPACE)
            );
        }
        
        // ?????????????????????????????????????????????????????????????????????????????????????????????
        if (ResourcesPoolTypeEnum.isGpuCode(baseTrainJobDTO.getResourcesPoolType())) {
            // ??????GPU
            sb.append(paramPrefix).append(trainJobConfig.getGpuNumPerNode()).append(SymbolConstant.FLAG_EQUAL).append(baseTrainJobDTO.getGpuNum()).append(StrUtil.SPACE);
        }
        String mainCommand = sb.toString();
        // ????????????????????????
        String wholeCommand = " echo 'Distribute training mission begins...  "
                + mainCommand
                + " ' && "
                + mainCommand
                + " && echo 'Distribute training mission is over' ";
        distributeTrainBO
                .setNamespace(namespace)
                .setName(baseTrainJobDTO.getJobName())
                .setSize(ptTrainJob.getResourcesPoolNode())
                .setImage(ptImageAndAlgorithmVO.getImageUrl())
                .setMasterCmd(wholeCommand)
                .setMemNum(baseTrainJobDTO.getMemNum())
                .setCpuNum(baseTrainJobDTO.getCpuNum() * MagicNumConstant.ONE_THOUSAND)
                .putFsMounts(TrainConstant.WORKSPACE_VOLUME_MOUNTS, fileStoreApi.formatPath(fileStoreApi.getRootDir() + basePath))
                .putFsMounts(TrainConstant.MODEL_VOLUME_MOUNTS, k8sNameTool.getAbsolutePath(relativePath + StrUtil.SLASH + trainJobConfig.getOutPath()))
                .setBusinessLabel(k8sNameTool.getPodLabel(BizEnum.ALGORITHM))
                .setTaskIdentifyLabel(baseTrainJobDTO.getTaskIdentify());

        //??????????????????????????????
        if (baseTrainJobDTO.getDelayCreateTime() != null && baseTrainJobDTO.getDelayCreateTime() > 0) {
            distributeTrainBO.setDelayCreateTime(baseTrainJobDTO.getDelayCreateTime() * MagicNumConstant.SIXTY);
        }
        //??????????????????????????????
        if (baseTrainJobDTO.getDelayDeleteTime() != null && baseTrainJobDTO.getDelayDeleteTime() > 0) {
            distributeTrainBO.setDelayDeleteTime(baseTrainJobDTO.getDelayDeleteTime() * MagicNumConstant.SIXTY);
        }
        if (ResourcesPoolTypeEnum.isGpuCode(baseTrainJobDTO.getResourcesPoolType())) {
            // ??????GPU
            distributeTrainBO.setGpuNum(baseTrainJobDTO.getGpuNum());
        }
        // ????????????
        distributeTrainBO.setSlaveCmd(distributeTrainBO.getMasterCmd());
        return distributeTrainBO;
    }


    /**
     * ??????job
     *
     * @param baseTrainJobDTO       ??????????????????
     * @param userId                ??????id
     * @param ptImageAndAlgorithmVO ?????????????????????
     */
    public void doJob(BaseTrainJobDTO baseTrainJobDTO, Long userId, PtImageAndAlgorithmVO ptImageAndAlgorithmVO, PtTrainJob ptTrainJob) {
        PtJupyterJobBO jobBo = null;
        String k8sJobName = "";
        try {
            //???????????????????????????namespace,?????????????????????
            String namespace = getNamespace(userId);

            //??????PtJupyterJobBO??????,??????????????????????????????
            jobBo = pkgPtJupyterJobBo(baseTrainJobDTO, userId, ptImageAndAlgorithmVO, namespace);
            if (null == jobBo) {
                LogUtil.error(LogEnum.BIZ_TRAIN, "userId {} create TrainJob, ptJupyterJobBO is empty,the received parameters namespace???{}", userId, namespace);
                ptTrainJob.putStatusDetail("Message", "????????????");
                updateTrainStatus(userId, ptTrainJob, baseTrainJobDTO, k8sJobName, false);
                return;
            }
            PtJupyterJobVO ptJupyterJobResult = trainJobApi.create(jobBo);
            if (!ptJupyterJobResult.isSuccess()) {
                String message = null == ptJupyterJobResult.getMessage() ? "???????????????" : ptJupyterJobResult.getMessage();
                LogUtil.error(LogEnum.BIZ_TRAIN, "userId {} create TrainJob, k8s creation failed, the received parameters are {}, the wrong information is{}", userId, jobBo, message);
                ptTrainJob.putStatusDetail("Message", message);
                updateTrainStatus(userId, ptTrainJob, baseTrainJobDTO, k8sJobName, false);
            }
            k8sJobName = ptJupyterJobResult.getName();
            //????????????????????????
            updateTrainStatus(userId, ptTrainJob, baseTrainJobDTO, k8sJobName, true);
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_TRAIN, "userId {} create TrainJob, K8s creation failed, the received parameters are {}, the wrong information is{}", userId,
                    jobBo, e);
            ptTrainJob.putStatusDetail("Message", "????????????");
            updateTrainStatus(userId, ptTrainJob, baseTrainJobDTO, k8sJobName, false);
        }
    }


    /**
     * ??????namespace
     *
     * @param userId ??????id
     * @return String     ????????????
     */
    private String getNamespace(Long userId) {
        String namespaceStr = k8sNameTool.generateNamespace(userId);
        BizNamespace bizNamespace = namespaceApi.get(namespaceStr);
        if (null == bizNamespace) {
            BizNamespace namespace = namespaceApi.create(namespaceStr, null);
            if (null == namespace || !namespace.isSuccess()) {
                LogUtil.error(LogEnum.BIZ_TRAIN, "User {} failed to create namespace during training job...");
                throw new BusinessException("????????????");
            }
        }
        return namespaceStr;
    }

    /**
     * ???????????????job?????????BO
     *
     * @param baseTrainJobDTO       ??????????????????
     * @param ptImageAndAlgorithmVO ?????????????????????
     * @param namespace             ????????????
     * @return PtJupyterJobBO       jupyter??????BO
     */
    private PtJupyterJobBO pkgPtJupyterJobBo(BaseTrainJobDTO baseTrainJobDTO, Long userId,
                                             PtImageAndAlgorithmVO ptImageAndAlgorithmVO, String namespace) {

        //????????????
        String commonPath = fileStoreApi.getBucket() + trainJobConfig.getManage() + StrUtil.SLASH
                + userId + StrUtil.SLASH + baseTrainJobDTO.getJobName();
        //????????????
        String relativeCommonPath = StrUtil.SLASH + trainJobConfig.getManage() + StrUtil.SLASH
                + userId + StrUtil.SLASH + baseTrainJobDTO.getJobName();
        String[] codeDirArray = ptImageAndAlgorithmVO.getCodeDir().split(StrUtil.SLASH);
        String workspaceDir = codeDirArray[codeDirArray.length - 1];
        // ??????????????????????????????
        String sourcePath = fileStoreApi.getBucket() + ptImageAndAlgorithmVO.getCodeDir().substring(1);
        String trainDir = commonPath.substring(1) + StrUtil.SLASH + workspaceDir;
        LogUtil.info(LogEnum.BIZ_TRAIN, "Algorithm path copy sourcePath:{},commonPath:{},trainDir:{}", sourcePath, commonPath, trainDir);
        boolean bool = fileStoreApi.copyPath(fileStoreApi.getRootDir() + sourcePath.substring(1), fileStoreApi.getRootDir() + trainDir);
        if (!bool) {
            LogUtil.error(LogEnum.BIZ_TRAIN, "During the process of userId {} creating training Job , it failed to copy algorithm directory {} to the target directory {}", userId, sourcePath.substring(1),
                    trainDir);
            return null;
        }

        List<String> list = new ArrayList<>();
        PtJupyterJobBO jobBo = new PtJupyterJobBO();
        JSONObject runParams = baseTrainJobDTO.getRunParams();

        StringBuilder sb = new StringBuilder();
        sb.append(ptImageAndAlgorithmVO.getRunCommand());
        // ??????out,log???dataset
        String pattern = trainJobConfig.getPythonFormat();

        for (TrainSystemRunParamEnum systemRunParamEnum : TrainSystemRunParamEnum.getNormalTrainParams()) {
            SystemRunParamHandler systemRunParamHandler = systemRunParamFactory.getHandler(systemRunParamEnum);
            String paramValue = systemRunParamHandler.buildSystemRunCommand(jobBo, userId,null, baseTrainJobDTO, ptImageAndAlgorithmVO.getIsTrainModelOut(),
                    ptImageAndAlgorithmVO.getIsTrainOut(), ptImageAndAlgorithmVO.getIsVisualizedLog(), systemRunParamEnum.name(), true);
            if (StringUtils.isNotBlank(paramValue)) {
                sb.append(paramValue);
            }
        }

        //????????????????????????????????????
        buildBoAboutModel(baseTrainJobDTO, jobBo, sb);
        buildBoAboutDataset(baseTrainJobDTO, jobBo, sb);

        if (null != runParams && !runParams.isEmpty()) {
            runParams.forEach((k, v) ->
                    sb.append(pattern).append(k).append(SymbolConstant.FLAG_EQUAL).append(v).append(StrUtil.SPACE)
            );
        }
        
        String executeCmd = sb.toString();
        list.add("-c");

        String workPath = trainJobConfig.getDockerTrainPath() + StrUtil.SLASH + workspaceDir;
        String command;
        Integer modelResource = baseTrainJobDTO.getModelResource();
        if (null != modelResource && modelResource.intValue() == ModelResourceEnum.ATLAS.getType().intValue()) {
            command = "&& " + trainJobConfig.getAtlasAnaconda() +
                    " && cd " + workPath +
                    " && " + trainJobConfig.getAtlasPythonioencoding() + executeCmd;
        } else {
            command = " && cd " + workPath + " && " + executeCmd;
        }
        command = "echo 'training mission begins... " + executeCmd + "\r\n '" + command + " && echo 'the training mission is over' ";

        list.add(command);

        jobBo.setNamespace(namespace)
                .setName(baseTrainJobDTO.getJobName())
                .setImage(ptImageAndAlgorithmVO.getImageUrl())
                .setCmdLines(list)
                .putFsMounts(trainJobConfig.getDockerTrainPath(), fileStoreApi.getRootDir() + commonPath.substring(1))
                .setBusinessLabel(k8sNameTool.getPodLabel(BizEnum.ALGORITHM))
                .setTaskIdentifyLabel(baseTrainJobDTO.getTaskIdentify());


        

        //??????pip??????
        if (StringUtils.isNotBlank(baseTrainJobDTO.getPipSitePackagePath())) {
            String formatPath = fileStoreApi.formatPath(fileStoreApi.getRootDir() + fileStoreApi.getBucket() + baseTrainJobDTO.getPipSitePackagePath());
            jobBo.putFsMounts(trainJobConfig.getDockerPipSitePackagePath(), formatPath);
            //??????pip???????????????
            int startIndex = -1;
            List<String> cmdLines = jobBo.getCmdLines();
            for (int i = 0; i < cmdLines.size(); i++) {
                //bash -c ????????????
                if ("-c".equals(cmdLines.get(i))) {
                    startIndex = i;
                }
            }
            String cmdLine = cmdLines.get(startIndex + 1);
            String appendPythonPath = " export PYTHONPATH=" + trainJobConfig.getDockerPipSitePackagePath() + " && ";
            cmdLine = appendPythonPath + cmdLine;
            cmdLines.set(startIndex + 1, cmdLine);
        }
        //??????????????????????????????
        if (baseTrainJobDTO.getDelayCreateTime() != null && baseTrainJobDTO.getDelayCreateTime() > 0) {
            jobBo.setDelayCreateTime(baseTrainJobDTO.getDelayCreateTime() * MagicNumConstant.SIXTY);
        }
        //??????????????????????????????
        if (baseTrainJobDTO.getDelayDeleteTime() != null && baseTrainJobDTO.getDelayDeleteTime() > 0) {
            jobBo.setDelayDeleteTime(baseTrainJobDTO.getDelayDeleteTime() * MagicNumConstant.SIXTY);
        }
        jobBo.setCpuNum(baseTrainJobDTO.getCpuNum() * MagicNumConstant.ONE_THOUSAND).setMemNum(baseTrainJobDTO.getMemNum());
        if (ResourcesPoolTypeEnum.isGpuCode(baseTrainJobDTO.getResourcesPoolType())) {
            jobBo.setUseGpu(true).setGpuNum(baseTrainJobDTO.getGpuNum());
        } else {
            jobBo.setUseGpu(false);
        }
        return jobBo;
    }

    private void buildBoAboutDataset(BaseTrainJobDTO baseTrainJobDTO, PtJupyterJobBO jobBo, StringBuilder sb) {
        if ((baseTrainJobDTO.getModelResource() != null && baseTrainJobDTO.getModelResource().equals(ModelResourceEnum.ATLAS.getType()))) {
            if (CollUtil.isNotEmpty(baseTrainJobDTO.getAtlasDatasetPaths())) {
                appendAtlasDataPath(baseTrainJobDTO.getAtlasDatasetPaths(), baseTrainJobDTO.getAtlasDatasetNames(), jobBo, sb);
            }
        }
        

    }


    /**
     * ??????????????????????????????????????????????????????
     *
     * @param atlasDataPaths ???????????????????????????
     * @param jobBo ??????????????????
     * @param sb ??????????????????
     */
    private void appendAtlasDataPath(List<String> atlasDataPaths, List<String> atlasNames, PtJupyterJobBO jobBo, StringBuilder sb) {
        if (CollUtil.isNotEmpty(atlasDataPaths)) {
            StringBuilder appendDataPath = new StringBuilder();
            if (CollUtil.isNotEmpty(atlasDataPaths)) {
                for (int i = 0; i < atlasDataPaths.size(); i++) {
                    String atlasDataPath = trainJobConfig.getDockerDatasetPath() + StrUtil.SLASH + atlasNames.get(i).split(StrUtil.COLON)[0];
                    appendDataPath.append(atlasDataPath).append(SymbolConstant.COMMA);
                    jobBo.putFsMounts(atlasDataPath, fileStoreApi.getRootDir() + fileStoreApi.getBucket().substring(1) + atlasDataPaths.get(i));
                }
                String dataPath = SymbolConstant.MARK + appendDataPath.toString().substring(MagicNumConstant.ZERO, appendDataPath.length() - MagicNumConstant.ONE) + SymbolConstant.MARK;
                sb.append(trainJobConfig.getPythonFormat()).append(trainJobConfig.getDockerAtlasDatasetKey()).append(SymbolConstant.FLAG_EQUAL).append(dataPath);
            }
        }

    }


    /**
     * ??????????????????????????????????????????????????????
     *
     * @param atlasValDataPaths ???????????????????????????
     * @param jobBO ??????????????????
     * @param sb ??????????????????
     */
    private void appendAtlasValDataPath(List<String> atlasValDataPaths, List<String> atlasValNames, PtJupyterJobBO jobBO, StringBuilder sb) {
        if (CollUtil.isNotEmpty(atlasValDataPaths)) {
            StringBuilder appendValDataPath = new StringBuilder();
            if (CollUtil.isNotEmpty(atlasValDataPaths)) {
                for (int i = 0; i < atlasValDataPaths.size(); i++) {
                    String atlasValDataPath = trainJobConfig.getDockerValDatasetPath() + StrUtil.SLASH + atlasValNames.get(i).split(StrUtil.COLON)[0];
                    appendValDataPath.append(atlasValDataPath).append(SymbolConstant.COMMA);
                    jobBO.putFsMounts(atlasValDataPath, fileStoreApi.getRootDir() + fileStoreApi.getBucket().substring(1) + atlasValDataPaths.get(i));
                }
                String dataPath = SymbolConstant.MARK + appendValDataPath.toString().substring(MagicNumConstant.ZERO, appendValDataPath.length() - MagicNumConstant.ONE) + SymbolConstant.MARK;
                sb.append(trainJobConfig.getPythonFormat()).append(trainJobConfig.getDockerAtlasValDatasetKey()).append(SymbolConstant.FLAG_EQUAL).append(dataPath);
            }
        }
    }


    /**
     * ????????????????????????????????????
     *
     * @param baseTrainJobDTO ????????????????????????
     * @param jobBo           ??????????????????
     * @param sb              ??????????????????
     */
    private void buildBoAboutModel(BaseTrainJobDTO baseTrainJobDTO, Object jobBo, StringBuilder sb) {
        if (null == baseTrainJobDTO.getModelResource()) {
            return;
        }
        //??????????????????????????????
        appendAtlasModelPath(baseTrainJobDTO.getTeacherModelPathList(), jobBo, sb, true);
        //??????????????????????????????
        appendAtlasModelPath(baseTrainJobDTO.getStudentModelPathList(), jobBo, sb, false);
    }

    /**
     * ??????????????????????????????????????????
     *
     * @param modelPathList ??????????????????
     * @param jobBo         ??????????????????
     * @param sb            ??????????????????
     * @param isTeacher     ??????????????????
     */
    private void appendAtlasModelPath(List<String> modelPathList, Object jobBo, StringBuilder sb, boolean isTeacher) {
        if (null == modelPathList || modelPathList.isEmpty()) {
            return;
        }
        StringBuilder appendModelPath = new StringBuilder();
        String preModelKey;
        String preModelPath;
        if (isTeacher) {
            preModelKey = trainJobConfig.getDockerTeacherModelKey();
            preModelPath = trainJobConfig.getDockerTeacherModelPath();
        } else {
            preModelKey = trainJobConfig.getDockerStudentModelKey();
            preModelPath = trainJobConfig.getDockerStudentModelPath();
        }
        modelPathList.stream()
                .forEach(modelPath -> {
                    String[] urlArray = modelPath.split(SymbolConstant.SLASH);
                    String dockerModelPath = urlArray[urlArray.length - MagicNumConstant.ONE];
                    String mountPath = preModelPath + SymbolConstant.SLASH + dockerModelPath;
                    appendModelPath.append(mountPath).append(SymbolConstant.COMMA);
                    if (jobBo instanceof PtJupyterJobBO) {
                        PtJupyterJobBO ptJupyterJobBO = (PtJupyterJobBO) jobBo;
                        ptJupyterJobBO.putFsMounts(mountPath, fileStoreApi.formatPath(fileStoreApi.getRootDir() + fileStoreApi.getBucket() + modelPath));
                    } else if (jobBo instanceof DistributeTrainBO) {
                        DistributeTrainBO distributeTrainBO = (DistributeTrainBO) jobBo;
                        distributeTrainBO.putFsMounts(mountPath, fileStoreApi.formatPath(fileStoreApi.getRootDir() + fileStoreApi.getBucket() + modelPath));
                    }
                });
        String resultPath = SymbolConstant.MARK +
                appendModelPath.toString().substring(MagicNumConstant.ZERO, appendModelPath.toString().length() - MagicNumConstant.ONE) +
                SymbolConstant.MARK;

        sb.append(trainJobConfig.getPythonFormat()).append(preModelKey).append(SymbolConstant.FLAG_EQUAL).append(resultPath);
    }

    /**
     * ??????????????????????????????????????????
     *
     * @param userId             ??????id
     * @param ptTrainJob         ????????????
     * @param baseTrainJobDTO    ??????????????????
     * @param k8sJobName         k8s?????????job????????????????????????????????????
     * @param createTrainSuccess ??????????????????????????????(true????????????false?????????)
     **/
    private void updateTrainStatus(Long userId, PtTrainJob ptTrainJob, BaseTrainJobDTO baseTrainJobDTO, String k8sJobName, boolean createTrainSuccess) {

        ptTrainJob.setK8sJobName(k8sJobName)
                .setModelPath(baseTrainJobDTO.getTrainModelPath())
                .setOutPath(baseTrainJobDTO.getTrainOutPath())
                .setVisualizedLogPath(baseTrainJobDTO.getVisualizedLogPath());
        LogUtil.info(LogEnum.BIZ_TRAIN, "user {} training tasks are processed asynchronously to update training status???receiving parameters:{}", userId, ptTrainJob);

        //????????????????????????
        if (!createTrainSuccess) {
            ptTrainJob.setTrainStatus(TrainJobStatusEnum.CREATE_FAILED.getStatus());
        }
        ptTrainJobMapper.updateById(ptTrainJob);
    }
    
}
