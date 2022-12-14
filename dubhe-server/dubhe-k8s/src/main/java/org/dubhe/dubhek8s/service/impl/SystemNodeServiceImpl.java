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

package org.dubhe.dubhek8s.service.impl;


import com.alibaba.fastjson.JSON;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.base.constant.SymbolConstant;
import org.dubhe.biz.base.dto.UserDTO;
import org.dubhe.biz.base.enums.SystemNodeEnum;
import org.dubhe.biz.base.utils.MathUtils;
import org.dubhe.biz.base.utils.SpringContextHolder;
import org.dubhe.biz.base.utils.StringUtils;
import org.dubhe.biz.base.vo.DataResponseBody;
import org.dubhe.cloud.authconfig.service.AdminClient;
import org.dubhe.dubhek8s.domain.dto.NodeDTO;
import org.dubhe.dubhek8s.domain.dto.PodDTO;
import org.dubhe.dubhek8s.service.SystemNodeService;
import org.dubhe.k8s.api.MetricsApi;
import org.dubhe.k8s.api.NodeApi;
import org.dubhe.k8s.api.PodApi;
import org.dubhe.k8s.constant.K8sLabelConstants;
import org.dubhe.k8s.constant.K8sParamConstants;
import org.dubhe.k8s.domain.dto.NodeIsolationDTO;
import org.dubhe.k8s.domain.resource.BizNode;
import org.dubhe.k8s.domain.resource.BizPod;
import org.dubhe.k8s.domain.resource.BizQuantity;
import org.dubhe.k8s.domain.resource.BizTaint;
import org.dubhe.k8s.domain.vo.PtContainerMetricsVO;
import org.dubhe.k8s.domain.vo.PtNodeMetricsVO;
import org.dubhe.k8s.enums.PodPhaseEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;


/**
 * @description SystemNodeService????????????
 * @date 2020-06-03
 */
@Service
public class SystemNodeServiceImpl implements SystemNodeService {


    @Autowired
    private NodeApi nodeApi;

    @Autowired
    private PodApi podApi;

    @Autowired
    private MetricsApi metricsApi;

    @Resource
    private AdminClient adminClient;




    /**
     * ????????????????????????
     *
     * @param
     * @return List<NodeDTO>  ??????????????????
     **/
    @Override
    public List<NodeDTO> findNodes() {
        /**??????nodeDto???????????????**/
        List<NodeDTO> nodeDtoS = new ArrayList<>(MagicNumConstant.SIXTEEN);
        /**nodeDto???name???NodeDto????????????**/
        Map<String, NodeDTO> nodeDtoMap = new HashMap<>(MagicNumConstant.SIXTEEN);
        /**??????node???????????????**/
        List<PtNodeMetricsVO> nodeMetricsList = metricsApi.getNodeMetrics().stream().collect(toList());
        List<PtContainerMetricsVO> ptContainerMetricsVOList = metricsApi.getContainerMetrics();
        /**
         * ??????pod??????????????????
         * <podName,PtContainerMetricsVO>
         */

        Map<String,PtContainerMetricsVO> containerMetricsMap = new HashMap<>();
        for (PtContainerMetricsVO vo : ptContainerMetricsVOList){
            if (containerMetricsMap.get(vo.getPodName()) == null){
                containerMetricsMap.put(vo.getPodName(),vo);
            }else {
                containerMetricsMap.get(vo.getPodName()).addCpuUsageAmount(vo.getCpuUsageAmount());
                containerMetricsMap.get(vo.getPodName()).addMemoryUsageAmount(vo.getMemoryUsageAmount());
            }
        }
        List<BizPod> bizPodList = podApi.listAll().parallelStream().filter(obj -> !PodPhaseEnum.SUCCEEDED.getPhase().equals(obj.getPhase())).collect(Collectors.toList());
        /**nodeName->BizNode**/
        Map<String, BizNode> bizNodes = nodeApi.listAll().parallelStream().collect(Collectors.toMap(BizNode::getName, obj -> obj));
        /**???????????????node????????????????????????nodeDto**/
        if (nodeMetricsList != null && bizNodes != null) {
            nodeMetricsList.forEach(nodeMetrics -> nodeDtoMap.put(nodeMetrics.getNodeName(), toNodeDTO(nodeMetrics, bizNodes)));
        }
        if (bizPodList != null) {
            /**???pod????????????????????????podDto??????**/
            bizPodList.forEach(pod -> putPod(toPodDTO(containerMetricsMap.get(pod.getName()), pod), nodeDtoMap));
        }
        /**???podDto???????????????NodeDto??????**/
        for (NodeDTO obj : nodeDtoMap.values()) {
            obj.setGpuAvailable(MathUtils.reduce(obj.getGpuCapacity(), obj.getGpuUsed()));
            nodeDtoS.add(obj);
        }
        return nodeDtoS;
    }

    /**
     * ??????????????????????????????????????????
     *
     * @param
     * @return List<NodeDTO> NodeDTO??????
     */
    @Override
    public List<NodeDTO> findNodesIsolation() {
        String curEnv = SpringContextHolder.getActiveProfile();
        List<NodeDTO> nodeDTOList = findNodes();
        List<Long> userIds = nodeDTOList.stream()
                .filter(nodeDTO -> StringUtils.isNotEmpty(nodeDTO.getIsolationEnv()) && nodeDTO.getIsolationEnv().equals(curEnv))
                .map(NodeDTO::getIsolationId)
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(userIds)){
            return nodeDTOList;
        }
        DataResponseBody<List<UserDTO>> userDTODataResponseBody = adminClient.getUserList(userIds);
        if (userDTODataResponseBody == null || CollectionUtils.isEmpty(userDTODataResponseBody.getData())){
            return nodeDTOList;
        }
        Map<Long,UserDTO> userDTOMap = userDTODataResponseBody.getData().stream().collect(Collectors.toMap(UserDTO::getId,Function.identity()));
        nodeDTOList.forEach(nodeDTO -> {
            if (nodeDTO.getIsolationId() != null){
                UserDTO userDTO = userDTOMap.get(nodeDTO.getIsolationId());
                if (userDTO != null){
                    nodeDTO.setIsolation(userDTO.getUsername());
                }else {
                    nodeDTO.setIsolation(nodeDTO.getIsolationEnv()+" ?????? ??????id-"+nodeDTO.getIsolationId());
                }
            }
        });
        return nodeDTOList;
    }

    /**
     * k8s????????????????????????
     *
     * @param nodeIsolationDTO k8s??????????????????DTO
     * @return boolean
     */
    @Override
    public List<BizNode> addNodeIisolation(NodeIsolationDTO nodeIsolationDTO) {
        List<BizNode> bizNodes = new ArrayList<>();
        if (nodeIsolationDTO == null || nodeIsolationDTO.getUserId() == null || CollectionUtils.isEmpty(nodeIsolationDTO.getNodeNames())){
            return bizNodes;
        }
        List<BizTaint> bizTaintList =  nodeApi.geBizTaintListByUserId(nodeIsolationDTO.getUserId());
        for (String nodeName : nodeIsolationDTO.getNodeNames()){
            nodeApi.addLabel(nodeName,K8sLabelConstants.PLATFORM_TAG_ISOLATION_KEY,nodeApi.getNodeIsolationValue(nodeIsolationDTO.getUserId()));
            BizNode bizNode = nodeApi.taint(nodeName,bizTaintList);
            bizNodes.add(bizNode);
        }
        return bizNodes;
    }

    /**
     * k8s????????????????????????
     *
     * @param nodeIsolationDTO k8s??????????????????DTO
     * @return boolean
     */
    @Override
    public List<BizNode> delNodeIisolation(NodeIsolationDTO nodeIsolationDTO) {
        List<BizNode> bizNodes = new ArrayList<>();
        if (nodeIsolationDTO == null || CollectionUtils.isEmpty(nodeIsolationDTO.getNodeNames())){
            return bizNodes;
        }
        if (nodeIsolationDTO.getUserId() == null){
            for (String nodeName : nodeIsolationDTO.getNodeNames()){
                nodeApi.deleteLabel(nodeName,K8sLabelConstants.PLATFORM_TAG_ISOLATION_KEY);
                BizNode bizNode = nodeApi.delTaint(nodeName);
                bizNodes.add(bizNode);
            }
        }else {
            List<BizTaint> bizTaintList = nodeApi.geBizTaintListByUserId(nodeIsolationDTO.getUserId());
            for (String nodeName : nodeIsolationDTO.getNodeNames()){
                nodeApi.deleteLabel(nodeName,K8sLabelConstants.PLATFORM_TAG_ISOLATION_KEY);
                BizNode bizNode = nodeApi.delTaint(nodeName,bizTaintList);
                bizNodes.add(bizNode);
            }
        }
        return bizNodes;
    }

    /**
     * ??????nodeDto??????
     *
     * @param nodeMetrics ????????????
     * @param bizNodes    BizNode??????
     * @return NodeDTO     NodeDTO??????
     **/
    private static NodeDTO toNodeDTO(PtNodeMetricsVO nodeMetrics, Map<String, BizNode> bizNodes) {
        NodeDTO nodeDTO = new NodeDTO();
        BizNode node = bizNodes.get(nodeMetrics.getNodeName());
        if (node != null && nodeMetrics != null) {
            fillIsolationInfo(nodeDTO,node);
            nodeDTO.setUid(node.getUid());
            nodeDTO.setName(node.getName());
            node.getAddresses().stream().forEach(bizNodeAddress -> {
                if (K8sParamConstants.INTERNAL_IP.equals(bizNodeAddress.getType())) {
                    nodeDTO.setIp(bizNodeAddress.getAddress());
                    return;
                }
            });
            nodeDTO.setStatus(node.getReady() ? K8sParamConstants.NODE_STATUS_TRUE : K8sParamConstants.NODE_STATUS_FALSE);
            Map<String, BizQuantity> capacity = node.getCapacity();
            nodeDTO.setGpuCapacity(capacity.get(K8sParamConstants.GPU_RESOURCE_KEY) == null ? SymbolConstant.ZERO : capacity.get(K8sParamConstants.GPU_RESOURCE_KEY).getAmount());
            nodeDTO.setNodeMemory(transferMemory(nodeMetrics.getMemoryUsageAmount()) + K8sParamConstants.MEM_UNIT);
            nodeDTO.setNodeCpu(transferCpu(nodeMetrics.getCpuUsageAmount()) + K8sParamConstants.CPU_UNIT);
            nodeDTO.setTotalNodeCpu(capacity.get(K8sParamConstants.QUANTITY_CPU_KEY).getAmount());
            nodeDTO.setTotalNodeMemory(transferMemory(capacity.get(K8sParamConstants.QUANTITY_MEMORY_KEY).getAmount()) + K8sParamConstants.MEM_UNIT);
            node.getConditions().stream().forEach((bizNodeCondition) -> {
                if ((!(K8sParamConstants.NODE_STATUS_TRUE.equals(bizNodeCondition.getType())) && (K8sParamConstants.NODE_READY_TRUE.equalsIgnoreCase(bizNodeCondition.getStatus())))) {
                    nodeDTO.setWarning(SystemNodeEnum.findMessageByType(bizNodeCondition.getType()));
                }
            });
            return nodeDTO;
        }
        return nodeDTO;
    }

    /**
     * ????????????????????????
     *
     * @param nodeDTO ???????????????
     * @param node ??????
     */
    private static void fillIsolationInfo(NodeDTO nodeDTO,BizNode node){
        List<BizTaint> taints = node.getTaints();
        if (CollectionUtils.isEmpty(taints)){
            return;
        }
        for (BizTaint taint : taints){
            String isolation =  taint.getKey();
            if (K8sLabelConstants.PLATFORM_TAG_ISOLATION_KEY.equals(isolation)){
                String[] isolationInfo = taint.getValue().split(K8sLabelConstants.PLATFORM_TAG_ISOLATION_VALUE_SPLIT);
                nodeDTO.setIsolationEnv(isolationInfo[0]);
                nodeDTO.setIsolationId(Long.valueOf(isolationInfo[1]));
            }
        }
    }

    /**
     * ??????PodDto??????
     *
     * @param podMetrics metrics ?????????
     * @param pod ?????????????????????k8s pod
     * @return PodDTO
     */
    private PodDTO toPodDTO(PtContainerMetricsVO podMetrics, BizPod pod){
        PodDTO podDTO = new PodDTO();
        podDTO.setPodName(pod.getName());
        if (podMetrics != null){
            podDTO.setPodCpu(transfer(podMetrics.getCpuUsageAmount()) + K8sParamConstants.CPU_UNIT);
            podDTO.setPodMemory(transferMemory(podMetrics.getMemoryUsageAmount()) + K8sParamConstants.MEM_UNIT);
        }else {
            podDTO.setPodCpu(SymbolConstant.ZERO + K8sParamConstants.CPU_UNIT);
            podDTO.setPodMemory(SymbolConstant.ZERO + K8sParamConstants.MEM_UNIT);
        }

        BizQuantity bizQuantity = pod.getContainers().get(MagicNumConstant.ZERO).getLimits() == null ? null : pod.getContainers().get(MagicNumConstant.ZERO).getLimits().get(K8sParamConstants.GPU_RESOURCE_KEY);
        podDTO.setPodCard(bizQuantity == null ? SymbolConstant.ZERO : bizQuantity.getAmount());

        podDTO.setStatus(pod.getPhase());
        podDTO.setNodeName(pod.getNodeName());
        podDTO.setPodCreateTime(pod.getCreationTimestamp());
        return podDTO;
    }

    /**
     * ???PodDto???????????????nodeDto????????????
     *
     * @param podDTO     pod????????????
     * @param nodeDtoMap ???????????????
     * @return void
     **/
    private static void putPod(PodDTO podDTO, Map<String, NodeDTO> nodeDtoMap) {
        NodeDTO nodeDTO = nodeDtoMap.get(podDTO.getNodeName());
        if (nodeDTO != null) {
            if (nodeDTO.getPods() == null) {
                nodeDTO.setPods(new ArrayList<PodDTO>(MagicNumConstant.SIXTEEN));
                nodeDTO.setGpuAvailable(nodeDTO.getGpuCapacity());
                nodeDTO.setGpuUsed(SymbolConstant.ZERO);
            }
            nodeDTO.getPods().add(podDTO);
            nodeDTO.setGpuUsed(MathUtils.add(nodeDTO.getGpuUsed(), podDTO.getPodCard()));
        }
    }

    /**
     * ???pod cpu????????????
     *
     * @param amount ????????????
     * @return String ???????????????
     **/
    private static String transfer(String amount) {
        if (StringUtils.isBlank(amount)) {
            return null;
        }
        double cpuAmount = Long.valueOf(amount) * MagicNumConstant.ONE_DOUBLE / MagicNumConstant.MILLION;
        if (cpuAmount < MagicNumConstant.ZERO_DOUBLE) {
            return String.valueOf((int) Math.floor(cpuAmount));
        } else {
            return String.valueOf((int) Math.ceil(cpuAmount));
        }
    }

    /**
     * ?????????????????????
     *
     * @param memory ????????????
     * @return String ???????????????
     **/
    private static String transferMemory(String memory) {
        if (StringUtils.isBlank(memory)) {
            return null;
        }
        return String.valueOf((int) Math.floor(Integer.valueOf(memory) / MagicNumConstant.BINARY_TEN_EXP));
    }

    /**
     * ???cpu????????????
     *
     * @param cpu ????????????
     * @return String ???????????????
     **/
    private static String transferCpu(String cpu) {
        if (StringUtils.isBlank(cpu)) {
            return null;
        }
        return String.valueOf((int) Math.ceil(Long.valueOf(cpu) * MagicNumConstant.ONE_DOUBLE / MagicNumConstant.MILLION));
    }

}
