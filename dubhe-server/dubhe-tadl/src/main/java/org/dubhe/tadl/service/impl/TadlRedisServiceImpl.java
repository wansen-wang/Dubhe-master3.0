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
package org.dubhe.tadl.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.beanutils.BeanUtils;
import org.dubhe.biz.base.constant.NumberConstant;
import org.dubhe.biz.base.constant.SymbolConstant;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.utils.StringUtils;
import org.dubhe.biz.file.utils.MinioUtil;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.biz.redis.utils.RedisUtils;
import org.dubhe.biz.statemachine.dto.StateChangeDTO;
import org.dubhe.k8s.api.LogMonitoringApi;
import org.dubhe.k8s.enums.PodPhaseEnum;
import org.dubhe.k8s.utils.K8sNameTool;
import org.dubhe.tadl.config.RedisStreamListenerContainerConfig;
import org.dubhe.tadl.constant.RedisKeyConstant;
import org.dubhe.tadl.constant.TadlConstant;
import org.dubhe.tadl.dao.TrialDataMapper;
import org.dubhe.tadl.domain.dto.ExperimentAndTrailDTO;
import org.dubhe.tadl.domain.dto.TrialDeleteDTO;
import org.dubhe.tadl.domain.dto.TrialK8sPodCallBackCreateDTO;
import org.dubhe.tadl.domain.dto.TrialRunParamDTO;
import org.dubhe.tadl.domain.entity.Experiment;
import org.dubhe.tadl.domain.entity.ExperimentStage;
import org.dubhe.tadl.domain.entity.Trial;
import org.dubhe.tadl.domain.vo.TrialResultVO;
import org.dubhe.tadl.enums.ExperimentStageStateEnum;
import org.dubhe.tadl.enums.ExperimentStatusEnum;
import org.dubhe.tadl.enums.StageEnum;
import org.dubhe.tadl.enums.TrialStatusEnum;
import org.dubhe.tadl.machine.constant.ExperimentEventMachineConstant;
import org.dubhe.tadl.machine.constant.ExperimentStageEventMachineConstant;
import org.dubhe.tadl.machine.constant.TrialEventMachineConstant;
import org.dubhe.tadl.machine.utils.identify.StateMachineStatusUtil;
import org.dubhe.tadl.machine.utils.identify.StateMachineUtil;
import org.dubhe.tadl.service.ExperimentService;
import org.dubhe.tadl.service.ExperimentStageService;
import org.dubhe.tadl.service.TadlRedisService;
import org.dubhe.tadl.service.TadlTrialService;
import org.dubhe.tadl.task.TrialJobAsyncTask;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @description tadl ?????? Redis ??????????????????
 * @date 2021-03-05
 */
@Service
public class TadlRedisServiceImpl implements TadlRedisService {

   @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private ExperimentService experimentService;

    @Resource
    private ExperimentStageService experimentStageService;

    @Resource
    private TadlTrialService trialService;

    @Resource
    private K8sNameTool k8sNameTool;

    @Resource
    private TrialJobAsyncTask trialJobAsyncTask;

    @Resource
    private TrialDataMapper trialDataMapper;

    @Resource
    private MinioUtil minioUtil;

    @Resource
    private RedisStreamListenerContainerConfig redisStreamListenerContainerConfig;

    @Resource
    private ReactiveRedisTemplate reactiveRedisTemplate;

    @Resource
    private TadlRedisService tadlRedisService;

    @Resource
    private LogMonitoringApi logMonitoringApi;

    @Value("${minio.bucketName}")
    private String bucketName;

    @Value("Task:TADL:" + "${spring.profiles.active}_experiment_id_")
    private String experimentIdPrefix;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StateMachineStatusUtil stateMachineStatusUtil;



    /**
     * ????????????????????????????????? ??????????????????
     *
     * @param times ??????????????????
     * @param req ??????????????????
     * @return boolean ??????????????????
     */
    @Override
    public boolean trialCallback(int times, TrialK8sPodCallBackCreateDTO req) {
        Map<String, String> labels = req.getLables();
        Long trialId = Long.parseLong(labels.get("trial-id"));
        Long experimentId = Long.parseLong(labels.get("experiment-id"));
        Long stageId = Long.parseLong(labels.get("stage-id"));
        String redisStreamRecodeId = labels.get("redis-stream-recode-id");
        Experiment experiment = experimentService.selectById(experimentId);
        Trial trial = trialService.selectOne(trialId);
        LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"The information of trial status:{}", experimentId, stageId, trialId, req.getPhase());
        if (experiment == null || trial == null) {
            LogUtil.error(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"The request: {}", experimentId, stageId, trialId, req.toString());
            return false;
        }
        // ????????????trial??????????????????
        if (TrialStatusEnum.FINISHED.getVal().equals(trial.getStatus())) {
            LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"The trial is finished", experimentId, stageId, trialId);
            return true;
        }
        //??????????????????????????????????????????????????????????????????,????????????????????????
        if (ExperimentStatusEnum.PAUSED_EXPERIMENT_STATE.getValue().equals(experiment.getStatus()) && !PodPhaseEnum.DELETED.getPhase().equals(req.getPhase())) {
            //?????????????????????????????????????????????trial?????????
            tadlRedisService.deleteRunningTrial(stageId);
            LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"The experiment has been suspended, delete the running trial,freeing up resource", experiment.getId(), stageId, trial.getId());
            return true;
        }
        PodPhaseEnum phase = Enum.valueOf(PodPhaseEnum.class, req.getPhase().toUpperCase());
        switch (phase) {
            case PENDING:
                break;
            case RUNNING:
                podRunning(experiment, stageId, trial);
                break;
            case SUCCEEDED:
                podSucceed(experiment, stageId, trial, redisStreamRecodeId);
                break;
            case FAILED:
                podFailed(experiment, stageId, trial, redisStreamRecodeId,req.getMessages());
                break;
            case DELETED:
                podDeleted(trial);
                break;
            default:
                podUnknown(experiment,stageId, trial);
                break;
        }
        return true;
    }


    /**
     * ??????????????????
     * @param trial
     */
    private void podUnknown(Experiment experiment,Long stageId,Trial trial) {
        LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"The trial result is unknown", trial.getExperimentId(), trial.getStageId(), trial.getId());
        experiment.putStatusDetail(TadlConstant.EXPERIMENT_RUN_FAILED,TadlConstant.UNKNOWN_EXCEPTION);
        experimentFailed(experiment, stageId, trial,TrialEventMachineConstant.UNKNOWN_TRIAL_EVENT);
    }

    /**
     * pod ????????????
     *
     * @param trial
     */
    private void podDeleted(Trial trial) {

        LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"Pod starts deleting.", trial.getExperimentId(), trial.getStageId(), trial.getId());
        redisUtils.del(RedisKeyConstant.buildDeletedKey(trial.getExperimentId(), trial.getStageId(), trial.getId()));
        LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"Pod deletion completed.", trial.getExperimentId(), trial.getStageId(), trial.getId());
    }

    /**
     * trial??????????????????
     *
     * @param experiment
     * @param stageId
     * @param trial
     * @param redisStreamRecodeId
     */
    private void podFailed(Experiment experiment, Long stageId, Trial trial, String redisStreamRecodeId,String message) {
        LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"Trial experiment runs failed", experiment.getId(), stageId, trial.getId());
        confirmationAckMessage(experiment.getId(), stageId, redisStreamRecodeId);
        //?????????????????????????????????,?????????pod??????;?????????????????????????????????,?????????????????????
        message = StringUtils.isNotBlank(message)?message:TadlConstant.ABNORMAL_OPERATION_OF_ALGORITHM;
        experiment.putStatusDetail(TadlConstant.EXPERIMENT_RUN_FAILED,message);
        //????????????????????????????????? ????????????trial????????????????????????????????????????????????????????????trial????????????????????????????????????????????????????????????
        LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"Trail experiment failed and the status was changed", experiment.getId(), stageId, trial.getId());
        experimentFailed(experiment, stageId, trial,TrialEventMachineConstant.FAILED_TRIAL_EVENT);
    }

    /**
     * ???????????????????????????
     * @param experiment ??????
     * @param stageId ????????????id
     * @param trial ??????trial
     */
    private void experimentFailed(Experiment experiment, Long stageId, Trial trial,String eventMethodName) {
        //??????trial ????????????????????????????????????
        StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{trial.getId(),experiment.getStatusDetail()},TrialEventMachineConstant.TRIAL_STATE_MACHINE,eventMethodName));
        //?????????????????????????????????????????????????????????trial ???????????????????????????
        List<Trial> trialList = trialService.getTrialList(new LambdaQueryWrapper<Trial>()
                .eq(Trial::getStageId, stageId)
                        .in(Trial::getStatus, TrialStatusEnum.RUNNING.getVal(), TrialStatusEnum.WAITING.getVal())
        );
        if (!CollectionUtils.isEmpty(trialList)){
            Map<Integer, List<Long>> statusTrialIdListMap = trialList.stream().collect(Collectors.groupingBy(Trial::getStatus,
                    Collectors.mapping(Trial::getId, Collectors.toList())));
            for (Map.Entry<Integer,List<Long>> statusTrialIdList:statusTrialIdListMap.entrySet()){
                if (!CollectionUtils.isEmpty(statusTrialIdList.getValue())) {
                    StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{statusTrialIdList.getValue()},TrialEventMachineConstant.TRIAL_STATE_MACHINE,TrialEventMachineConstant.TO_RUN_BATCH_TRIAL_EVENT));
                }
            }
        }
        // ????????????????????????????????????????????????
        tadlRedisService.deleteRunningTrial(stageId);
        //??????redis????????????
        LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"Prepare to delete redis cache information. ", experiment.getId(), stageId, trial.getId());
        delRedisExperimentInfo(experiment.getId());
        //????????????????????????
        LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"Redis cache deletion completed. ", experiment.getId(), stageId, trial.getId());
    }

    /**
     * trial????????????????????????
     *
     * @param experiment
     * @param stageId
     * @param trial
     * @param redisStreamRecodeId
     */
    private void podSucceed(Experiment experiment, Long stageId, Trial trial, String redisStreamRecodeId) {
        LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"Trial experiment runs successfully.", experiment.getId(), stageId, trial.getId());
        ExperimentStage experimentStage = experimentStageService.selectById(stageId);
        //??????trial?????????running??????????????????succeed?????????????????????running???????????????????????????
        if (experimentStage.getStatus().equals(ExperimentStageStateEnum.TO_RUN_EXPERIMENT_STAGE_STATE.getCode())){
            trialRunFirstTime(experiment,stageId,trial);
        }
        finishTrial(trial, redisStreamRecodeId);
        LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"The stage name of experiment stage :{}", experiment.getId(), stageId, trial.getId(),experimentStage.getStageName());
        //?????????????????????????????????
        saveBestData(trial, experimentStage);
        //?????????,?????????????????????????????????????????????????????????????????????trial???????????????????????????
        RLock lock = redissonClient.getLock(TadlConstant.LOCK + SymbolConstant.COLON+ experimentStage.getExperimentId()+SymbolConstant.COLON + experimentStage.getStageOrder());
        try{
            lock.lock(10,TimeUnit.SECONDS);
            //??????????????????trial??????
            Integer unsuccessfulCount = trialService.selectCount(new LambdaQueryWrapper<Trial>() {
                {
                    eq(Trial::getExperimentId, experiment.getId())
                            .eq(Trial::getStageId, stageId)
                            .ne(Trial::getStatus, TrialStatusEnum.FINISHED.getVal());
                }
            });
            LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"The number of  unsuccessful count:{}.", experiment.getId(), stageId, trial.getId(),unsuccessfulCount);
            //??????????????????????????????????????????retrain ??????????????????????????????
            if (unsuccessfulCount == NumberConstant.NUMBER_0 && StageEnum.RETRAIN.getName().equals(experimentStage.getStageName())){
                LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"Start to finish experiment.", experiment.getId(), stageId, trial.getId());
                finishExperiment(experiment.getId(),experimentStage.getId());
                return;
            }
            //?????????????????????,?????????????????????,?????????????????????????????????????????????
            if (unsuccessfulCount ==  NumberConstant.NUMBER_0){
                StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{stageId},ExperimentStageEventMachineConstant.EXPERIMENT_STAGE_STATE_MACHINE,ExperimentStageEventMachineConstant.FINISHED_EXPERIMENT_STAGE_EVENT));
                Integer stageOrder = StageEnum.getStageOrder(experimentStage.getStageName());
                experimentStage = experimentStageService.selectOne(experiment.getId(),++stageOrder);
            }
        }catch (Exception e){
            LogUtil.error(LogEnum.TADL,TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"????????????????????????:{}",trial.getExperimentId(),trial.getStageId(),trial.getId(),e.getMessage());
            String statusDetail = StringUtils.putIntoJsonStringMap(TadlConstant.DISTRIBUTED_LOCK_ACQUISITION_FAILED,e.getMessage(),null);
            stateMachineStatusUtil.trialExperimentFailedState(trial.getExperimentId(),trial.getStageId(),trial.getId(),statusDetail);
            throw new BusinessException("????????????????????????");
        }finally {
            lock.unlock();
        }

        ExperimentAndTrailDTO experimentAndTrailDTO = experimentService.buildExperimentStageQueueMessage(experiment.getId(),experimentStage);
        if (Objects.isNull(experimentAndTrailDTO)){

            LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"Trial experiment and trial is null .", experiment.getId(), stageId, trial.getId());
            return;
        }
        LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"Start to push data to consumer.", experiment.getId(), stageId, trial.getId());
        tadlRedisService.pushDataToConsumer(experimentAndTrailDTO);
    }


    /**
     * pod????????????????????????
     *
     * @param experiment
     * @param stageId
     * @param trial
     */
    private void podRunning(Experiment experiment, Long stageId, Trial trial) {

        LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+" Change the trial experiment to running", experiment.getId(), stageId, trial.getId());
        ExperimentStage experimentStage = experimentStageService.selectById(trial.getStageId());
        //???????????????????????????????????????trial???????????????????????????
        if (!ExperimentStageStateEnum.TO_RUN_EXPERIMENT_STAGE_STATE.getCode().equals(experimentStage.getStatus())){
            LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+" Experiment and Experiment stage  is  running", trial.getId(),trial.getStageId(),trial.getId());
            //??????trial ????????????????????????
            StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{trial.getId()},TrialEventMachineConstant.TRIAL_STATE_MACHINE,TrialEventMachineConstant.RUNNING_TRIAL_EVENT));
            LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"Pod running", experiment.getId(), stageId, trial.getId());
            return;
        }
        trialRunFirstTime(experiment, stageId, trial);


    }

    /**
     * ??????????????????trial??????????????????????????????
     * ?????????????????????trial????????????????????????
     * @param experiment ??????
     * @param stageId ????????????id
     * @param trial trial ??????
     */
    private void trialRunFirstTime(Experiment experiment, Long stageId, Trial trial) {
        //????????????trial????????????running??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
        RLock lock = redissonClient.getLock(TadlConstant.LOCK + SymbolConstant.COLON+ trial.getExperimentId()+SymbolConstant.COLON + PodPhaseEnum.PENDING.getPhase());
        try{
            lock.lock(30,TimeUnit.SECONDS);
            Experiment experimentLock = experimentService.selectById(trial.getExperimentId());
            //?????????????????????
            if (ExperimentStatusEnum.WAITING_EXPERIMENT_STATE.getValue().equals(experimentLock.getStatus())) {
                LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+" Change the  experiment to running", experimentLock.getId(), trial.getStageId(), trial.getId());
                //??????????????????????????????????????????????????????
                StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{experimentLock.getId()},ExperimentEventMachineConstant.EXPERIMENT_STATE_MACHINE,ExperimentEventMachineConstant.RUNNING_EXPERIMENT_EVENT));
            }
            //?????????????????????????????????
            ExperimentStage experimentStageLock = experimentStageService.selectById(trial.getStageId());
            if (ExperimentStageStateEnum.TO_RUN_EXPERIMENT_STAGE_STATE.getCode().equals(experimentStageLock.getStatus())){
                LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+" Change the  experiment stage to running", experimentLock.getId(), trial.getStageId(), trial.getId());
                //????????????????????????????????????
                StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{trial.getStageId()},ExperimentStageEventMachineConstant.EXPERIMENT_STAGE_STATE_MACHINE,ExperimentStageEventMachineConstant.RUNNING_EXPERIMENT_STAGE_EVENT));
            }
            //????????????????????????????????????????????????????????????????????????????????????????????????running????????????????????????trial ????????????????????????
            StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{trial.getId()},TrialEventMachineConstant.TRIAL_STATE_MACHINE,TrialEventMachineConstant.RUNNING_TRIAL_EVENT));
            LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"Pod running", experiment.getId(), stageId, trial.getId());

        }catch (Exception e){
            LogUtil.error(LogEnum.TADL,TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"????????????????????????:{}", trial.getExperimentId(), trial.getStageId(), trial.getId(),e.getMessage());
            String statusDetail = StringUtils.putIntoJsonStringMap(TadlConstant.DISTRIBUTED_LOCK_ACQUISITION_FAILED,e.getMessage(),null);
            stateMachineStatusUtil.trialExperimentFailedState( experiment.getId(),stageId,trial.getId(),statusDetail);
            throw new BusinessException("????????????????????????");
        }finally {
            lock.unlock();
        }
    }


    /**
     * ????????????trial??????????????????
     * @param trial      trial??????
     * @param redisStreamRecodeId  ??????id
     *
     */
    private void finishTrial(Trial trial, String redisStreamRecodeId) {
        //???????????? ?????????????????????
        long number = confirmationAckMessage(trial.getExperimentId(), trial.getStageId(), redisStreamRecodeId);
        if (number > 0) {
            StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{trial.getId()},TrialEventMachineConstant.TRIAL_STATE_MACHINE,TrialEventMachineConstant.FINISHED_TRIAL_EVENT));
            LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+"The trial experiment completed.", trial.getExperimentId(),trial.getStageId(),trial.getId());
        } else {
            LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+" The trial experiment does not exist in redis queue.", trial.getExperimentId(),trial.getStageId(),trial.getId());
            String statusDetail = StringUtils.putIntoJsonStringMap(TadlConstant.REDIS_MESSAGE_QUEUE_EXCEPTION,"????????????????????????id="+trial.getId()+"???trial??????",null);
            stateMachineStatusUtil.trialExperimentFailedState(trial.getExperimentId(),trial.getStageId(),trial.getId(),statusDetail);
            throw new BusinessException("??????????????????");
        }


    }
    /**
     * ?????????????????????????????????
     *
     * @param experimentId ??????id
     */
    private void finishExperiment(Long experimentId,Long stageId) {
        //???????????????????????????,??????????????????????????????
        StateMachineUtil.stateChange(Arrays.asList(new StateChangeDTO(new Object[]{experimentId},ExperimentEventMachineConstant.EXPERIMENT_STATE_MACHINE,ExperimentEventMachineConstant.FINISHED_EXPERIMENT_EVENT) ,
                new StateChangeDTO(new Object[]{stageId},ExperimentStageEventMachineConstant.EXPERIMENT_STAGE_STATE_MACHINE,ExperimentStageEventMachineConstant.FINISHED_EXPERIMENT_STAGE_EVENT)));
        LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_EXPERIMENT_FLOW_LOG+"Prepare to delete redis cache information.", experimentId);
        //??????redis????????????
        delRedisExperimentInfo(experimentId);
        //??????????????????????????? ????????????
        LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_EXPERIMENT_FLOW_LOG+"Delete completed. Experiment completed", experimentId);

    }

    /**
     * ??????redis???????????????????????????
     *
     * @param experimentId ??????id
     */
    @Override
    public void delRedisExperimentInfo(Long experimentId) {
        LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_EXPERIMENT_FLOW_LOG+"Start to delete cache", experimentId);

        List<ExperimentStage> experimentStageList =  experimentStageService.getExperimentStageListByExperimentId(experimentId);

        for (ExperimentStage experimentStage:experimentStageList){
            redisUtils.del(RedisKeyConstant.buildStreamStageKey(experimentId,experimentStage.getId()));
        }
        redisUtils.del(RedisKeyConstant.buildPausedKey(experimentId));
        LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_EXPERIMENT_FLOW_LOG+"Delete cache success", experimentId);
    }

    /**
     * ????????????????????????trial
     *
     * @param stageId ??????id
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW,rollbackFor = Exception.class)
    public void deleteRunningTrial(Long stageId) {
        ExperimentStage experimentStage = experimentStageService.selectById(stageId);
        if (experimentStage == null) {
            LogUtil.error(LogEnum.TADL, "Stage id:{}.?????????????????????.", stageId);
            return;
        }
        Experiment experiment = experimentService.selectById(experimentStage.getExperimentId());
        //???????????????,?????????????????????,???????????????trial??????????????????????????????????????????????????????????????????????????????????????????pod?????????
        List<Trial> trialList = trialService.getTrialList(new LambdaQueryWrapper<Trial>()
                .eq(Trial::getStageId, stageId)
                        .in(Trial::getStatus, TrialStatusEnum.RUNNING.getVal(), TrialStatusEnum.TO_RUN.getVal(),TrialStatusEnum.WAITING.getVal(),TrialStatusEnum.FAILED.getVal())
                        .isNotNull(Trial::getResourceName)
        );
        LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_STAGE_KEYWORD_LOG+"Delete running trial.The trial size:{}", experiment.getId(), stageId, trialList.size());
        List<TrialDeleteDTO> trialDeleteDTOList = trialList.stream().map(trial -> {
            TrialDeleteDTO trialDeleteDTO = new TrialDeleteDTO();
            try{
                BeanUtils.copyProperties(trialDeleteDTO, trial);
            }catch (Exception e){
                LogUtil.error(LogEnum.TADL,TadlConstant.PROCESS_STAGE_KEYWORD_LOG+"Abnormal trial data conversion",experiment.getId(), stageId);
                throw new BusinessException("trial ??????????????????");
            }
            String namespace = k8sNameTool.getNamespace(experiment.getCreateUserId());
            trialDeleteDTO.setNamespace(namespace);
            trialDeleteDTO.setTrialId(trial.getId());
            return trialDeleteDTO;
        }).collect(Collectors.toList());
        //????????????trial????????????
         trialJobAsyncTask.deleteTrialList(trialDeleteDTOList);
        String taskIdentify = (String) redisUtils.get(experimentIdPrefix + experimentStage.getExperimentId());
        if (StringUtils.isNotEmpty(taskIdentify)) {
            redisUtils.del(taskIdentify, experimentIdPrefix + experimentStage.getExperimentId());
        }
    }

    /**
     * ??????result.json?????????????????????????????????????????????
     * @param trial
     */
    @Override
    public void saveBestData(Trial trial, ExperimentStage experimentStage) {
        try {
            String result = minioUtil.readString(bucketName, "TADL" + File.separator + "experiment" + File.separator
                    + trial.getExperimentId() + File.separator + StageEnum.getStage(experimentStage.getStageOrder()).getName()
                    + File.separator + trial.getSequence() + TadlConstant.RESULT_PATH);
            LogUtil.info(LogEnum.TADL,TadlConstant.PROCESS_TRIAL_KEYWORD_LOG,"result file info :{}",trial.getExperimentId(),trial.getStageId(),trial.getId(),result);
            String jsonArr[] = result.split("\n");
            Double value = 0.0;
            for (int i = 0; i < jsonArr.length; i++) {
                TrialResultVO trialResultVO = JSONArray.parseObject(jsonArr[i], TrialResultVO.class);
                if (TadlConstant.RESULT_JSON_TYPE.equals(trialResultVO.getType().toLowerCase()) && Double.parseDouble(trialResultVO.getResult().getValue()) > value) {
                    value = Double.parseDouble(trialResultVO.getResult().getValue());
                }
            }
            trialDataMapper.updateValue(trial.getId(), value);
            logMonitoringApi.addTadlLogsToEs(trial.getExperimentId(),"stage:" + experimentStage.getStageName() + ", trial " + trial.getSequence() + ",save best accuracy:" + value);
        } catch (Exception e) {

            LogUtil.error(LogEnum.TADL, "can not find file result.json.Exception message:{}", e.getMessage());
        }
    }


    /**
     * ????????? ????????????????????????ID ???????????????Redis Stream?????????
     *
     * @param experimentId ??????ID
     * @param stageId      ??????
     * @param messageId    ??????ID
     * @return long
     */
    @Override
    public long confirmationAckMessage(Long experimentId, Long stageId, String messageId) {
        return stringRedisTemplate.opsForStream().delete(RedisKeyConstant.buildStreamStageKey(experimentId, stageId), messageId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pushDataToConsumer(ExperimentAndTrailDTO experimentAndTrailDTO) {
        LogUtil.info(LogEnum.TADL,TadlConstant.PROCESS_STAGE_KEYWORD_LOG +"Start to push trial data",experimentAndTrailDTO.getExperimentId(),experimentAndTrailDTO.getStageId());

        redisStreamListenerContainerConfig.buildRedisStream(reactiveRedisTemplate.opsForStream(), RedisKeyConstant.buildStreamStageKey(experimentAndTrailDTO.getExperimentId(), experimentAndTrailDTO.getStageId()),
                RedisKeyConstant.buildStreamGroupStageKey(experimentAndTrailDTO.getExperimentId(), experimentAndTrailDTO.getStageId()));
        if (checkAndPushMessages(experimentAndTrailDTO)) {
            LogUtil.error(LogEnum.TADL,TadlConstant.PROCESS_STAGE_KEYWORD_LOG + "Trial data push failed",experimentAndTrailDTO.getExperimentId(),experimentAndTrailDTO.getStageId());
            return;
        }
        List<Long> trialIdList = trialService.getTrialList(new LambdaQueryWrapper<Trial>()
                .eq(Trial::getExperimentId, experimentAndTrailDTO.getExperimentId())
                        .eq(Trial::getStageId, experimentAndTrailDTO.getStageId())
                        .in(Trial::getStatus, TrialStatusEnum.RUNNING.getVal(),TrialStatusEnum.WAITING.getVal())
         ).stream().map(Trial::getId).collect(Collectors.toList());

        //?????????????????????????????????recordId
        StreamOperations<String, String, TrialRunParamDTO> streamOperations = stringRedisTemplate.opsForStream();
        List<MapRecord<String, String, TrialRunParamDTO>> redisDataList = streamOperations.range(RedisKeyConstant.buildStreamStageKey(experimentAndTrailDTO.getExperimentId(), experimentAndTrailDTO.getStageId()), Range.closed("-", "+"));
        for (MapRecord<String, String, TrialRunParamDTO> mapRecord : redisDataList){
            TrialRunParamDTO trialRunParamDTO = new TrialRunParamDTO();
            try {
                BeanUtils.populate(trialRunParamDTO, mapRecord.getValue());
                if (trialIdList.contains(trialRunParamDTO.getTrialId())){
                    LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG + "Trial is already running ", trialRunParamDTO.getExperimentId(), trialRunParamDTO.getStageId(),trialRunParamDTO.getTrialId());
                    continue;
                }
                trialRunParamDTO.setRedisStreamRecodeId(mapRecord.getId().toString());
                LogUtil.info(LogEnum.TADL, TadlConstant.PROCESS_TRIAL_KEYWORD_LOG+ "Start create  trial experiment job", trialRunParamDTO.getExperimentId(), trialRunParamDTO.getStageId(),trialRunParamDTO.getTrialId());

                StateMachineUtil.stateChange(new StateChangeDTO(new Object[]{trialRunParamDTO.getTrialId()},TrialEventMachineConstant.TRIAL_STATE_MACHINE,TrialEventMachineConstant.WAITING_TRIAL_EVENT));
                //???????????????????????????????????????????????????commit??????????????????????????????k8s?????????????????????
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                    @Override
                    public void afterCommit(){
                        trialJobAsyncTask.runTrial(trialRunParamDTO);
                    }
                });
            } catch (Exception e) {
                LogUtil.error(LogEnum.TADL, TadlConstant.PROCESS_EXPERIMENT_FLOW_LOG + "Error message???{}",experimentAndTrailDTO.getExperimentId(), e.getMessage());
                String statusDetail = StringUtils.putIntoJsonStringMap(TadlConstant.REDIS_STREAM_DATA_CONVERSION_EXCEPTION, e.getMessage(), null);
                StateMachineUtil.stateChange(Arrays.asList(new StateChangeDTO(new Object[]{experimentAndTrailDTO.getExperimentId(),statusDetail},ExperimentEventMachineConstant.EXPERIMENT_STATE_MACHINE,ExperimentEventMachineConstant.FAILED_EXPERIMENT_EVENT) ,
                        new StateChangeDTO(new Object[]{experimentAndTrailDTO.getStageId()},ExperimentStageEventMachineConstant.EXPERIMENT_STAGE_STATE_MACHINE,ExperimentStageEventMachineConstant.FAILED_EXPERIMENT_STAGE_EVENT) ));
                throw new BusinessException("Redis Stream ????????????????????????!");
            }
        }
        LogUtil.info(LogEnum.TADL,TadlConstant.PROCESS_STAGE_KEYWORD_LOG + "Trial data push successfully",experimentAndTrailDTO.getExperimentId(),experimentAndTrailDTO.getStageId());
        logMonitoringApi.addTadlLogsToEs(experimentAndTrailDTO.getExperimentId(),"trial data push successfully.stage id :" + experimentAndTrailDTO.getStageId());

    }

    /**
     * ??????????????????
     * @param experimentAndTrailDTO
     * @return
     */
    private boolean checkAndPushMessages(ExperimentAndTrailDTO experimentAndTrailDTO) {
        RLock lock = redissonClient.getLock(TadlConstant.LOCK + experimentAndTrailDTO.getStageId());
        try {
        lock.lock(30, TimeUnit.SECONDS);
        Experiment experiment = experimentService.selectById(experimentAndTrailDTO.getExperimentId());
        if (ExperimentStatusEnum.FAILED_EXPERIMENT_STATE.getValue().equals(experiment.getStatus())){
            LogUtil.info(LogEnum.TADL,TadlConstant.PROCESS_STAGE_KEYWORD_LOG+" The experiment status is :{}. ", experimentAndTrailDTO.getExperimentId(), experimentAndTrailDTO.getStageId(),ExperimentStatusEnum.FAILED_EXPERIMENT_STATE.getMsg());
            return true ;
        }
        LogUtil.info(LogEnum.TADL,TadlConstant.PROCESS_STAGE_KEYWORD_LOG +"Get stream operations. ", experimentAndTrailDTO.getExperimentId(), experimentAndTrailDTO.getStageId());
        StreamOperations<String, String, TrialRunParamDTO> streamOperations = stringRedisTemplate.opsForStream();
        List<MapRecord<String, String, TrialRunParamDTO>> redisDataList =  streamOperations.range(RedisKeyConstant.buildStreamStageKey(experimentAndTrailDTO.getExperimentId(), experimentAndTrailDTO.getStageId()), Range.closed("-", "+"));
        //?????? ?????????????????????????????? >= ???????????? ????????????????????????
        if (redisDataList.size() >= experimentAndTrailDTO.getTrialConcurrentNum()){
            LogUtil.info(LogEnum.TADL,TadlConstant.PROCESS_STAGE_KEYWORD_LOG +"Steam size are grater than concurrent number.");
            return true;
        }
        //??????stage key??????????????????trial id??????
        List<Long> trialIdList = redisDataList.stream().map(mapRecord -> {
            TrialRunParamDTO trialRunParamDTO = new TrialRunParamDTO();
            try {
                BeanUtils.populate(trialRunParamDTO, mapRecord.getValue());
            } catch (Exception e) {
                LogUtil.error(LogEnum.TADL, TadlConstant.PROCESS_STAGE_KEYWORD_LOG+"Redis Stream ????????????????????????!???????????????{}.",experimentAndTrailDTO.getExperimentId(),experimentAndTrailDTO.getStageId(), e.getMessage());
                throw new BusinessException("Redis Stream ????????????????????????!");
            }
            return trialRunParamDTO.getTrialId();
        }).collect(Collectors.toList());
        //?????????????????? ?????????????????? trial??????????????????
        List<TrialRunParamDTO> trialRunParamDTOList = experimentAndTrailDTO.getTrialRunParamDTOList().stream().filter(e -> !trialIdList.contains(e.getTrialId())).collect(Collectors.toList());
        LogUtil.info(LogEnum.TADL,TadlConstant.PROCESS_STAGE_KEYWORD_LOG+" trialRunParamDTOList size:{}. ", experimentAndTrailDTO.getExperimentId(), experimentAndTrailDTO.getStageId(),trialRunParamDTOList.size());

        if (CollectionUtils.isEmpty(trialRunParamDTOList)){
            LogUtil.info(LogEnum.TADL,TadlConstant.PROCESS_STAGE_KEYWORD_LOG+" trialRunParamDTOList size:{}.The trial run param size is zero. ", experimentAndTrailDTO.getExperimentId(), experimentAndTrailDTO.getStageId(),trialRunParamDTOList.size());
            return true;
        }

        //??????????????????????????????
        for (TrialRunParamDTO trialRunParamDTO : trialRunParamDTOList) {
            ObjectRecord<String, TrialRunParamDTO> mapRecord = ObjectRecord.create(RedisKeyConstant.buildStreamStageKey(experimentAndTrailDTO.getExperimentId(), experimentAndTrailDTO.getStageId()), trialRunParamDTO);
            //??????mapRecord ??????recordId
            stringRedisTemplate.opsForStream().add(mapRecord);
        }

        }catch (Exception e){
            LogUtil.error(LogEnum.TADL, TadlConstant.PROCESS_STAGE_KEYWORD_LOG+"??????????????????????????????????????????{}", experimentAndTrailDTO.getExperimentId(),experimentAndTrailDTO.getStageId(),e.getMessage());
            String statusDetail = StringUtils.putIntoJsonStringMap(TadlConstant.DISTRIBUTED_LOCK_ACQUISITION_FAILED, e.getMessage(), null);
            StateMachineUtil.stateChange(Arrays.asList(new StateChangeDTO(new Object[]{experimentAndTrailDTO.getExperimentId(),statusDetail},ExperimentEventMachineConstant.EXPERIMENT_STATE_MACHINE,ExperimentEventMachineConstant.FAILED_EXPERIMENT_EVENT)
                    ,new StateChangeDTO(new Object[]{experimentAndTrailDTO.getStageId()},ExperimentStageEventMachineConstant.EXPERIMENT_STAGE_STATE_MACHINE,ExperimentStageEventMachineConstant.FAILED_EXPERIMENT_STAGE_EVENT) ));
            throw new BusinessException("????????????????????????");
        }finally {
            lock.unlock();
        }
        return false;
    }
}
