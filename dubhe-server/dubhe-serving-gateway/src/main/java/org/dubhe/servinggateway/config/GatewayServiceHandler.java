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

package org.dubhe.servinggateway.config;

import lombok.extern.slf4j.Slf4j;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.base.constant.NumberConstant;
import org.dubhe.biz.base.constant.SymbolConstant;
import org.dubhe.biz.base.utils.StringUtils;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.servinggateway.constant.GatewayConstant;
import org.dubhe.servinggateway.domain.vo.GatewayRouteQueryVO;
import org.dubhe.servinggateway.service.GatewayRouteService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @description 服务路由信息处理类
 * @date 2020-09-07
 */
@Slf4j
@Service
public class GatewayServiceHandler implements ApplicationEventPublisherAware, CommandLineRunner {

    private ApplicationEventPublisher publisher;

    @Resource
    private RedisRouteDefinitionRepository routeDefinitionRepository;

    @Resource
    private GatewayRouteService gatewayRouteService;

    @Override
    public void run(String... args) {
        this.loadRouteConfig();
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.publisher = applicationEventPublisher;
    }

    /**
     * 加载服务路由信息
     */
    public void loadRouteConfig() {
        LogUtil.info(LogEnum.SERVING_GATEWAY, "Begin load route config");
        List<GatewayRouteQueryVO> activeRoutes = gatewayRouteService.findActiveRoutes();
        for (GatewayRouteQueryVO activeRoute : activeRoutes) {
            if (StringUtils.isEmpty(activeRoute.getUri())){
                continue;
            }
            RouteDefinition definition = this.convert2RouteDefinition(activeRoute);
            routeDefinitionRepository.save(Mono.just(definition)).subscribe();
        }
        // 发布刷新路由事件
        this.publisher.publishEvent(new RefreshRoutesEvent(this));
        LogUtil.info(LogEnum.SERVING_GATEWAY, "Load route config done");
    }

    /**
     * 根据id更新路由配置
     *
     * @param id 在线服务路由ID
     */
    public void saveRouteByRouteId(Long id) {
        LogUtil.info(LogEnum.SERVING_GATEWAY, "Deal save route event");
        GatewayRouteQueryVO gatewayRouteQueryVO = gatewayRouteService.findActiveById(id);
        //查不到数据进行重试
        int count = 0;
        while (gatewayRouteQueryVO == null && count < MagicNumConstant.THREE){
            LogUtil.info(LogEnum.SERVING_GATEWAY, "gatewayRouteQueryVO is null retry:"+count +" id="+id);
            gatewayRouteQueryVO = gatewayRouteService.findActiveById(id);
            count++;
            try {
                Thread.sleep(MagicNumConstant.FIVE_HUNDRED);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (Objects.nonNull(gatewayRouteQueryVO) && StringUtils.isNotEmpty(gatewayRouteQueryVO.getUri())) {
            routeDefinitionRepository.save(Mono.just(this.convert2RouteDefinition(gatewayRouteQueryVO))).subscribe();
        }
        // 发布刷新路由事件
        this.publisher.publishEvent(new RefreshRoutesEvent(this));
        LogUtil.info(LogEnum.SERVING_GATEWAY, "Deal save route event done");
    }

    /**
     * 根据id删除路由配置信息
     *
     * @param id 在线服务路由ID
     */
    public void deleteRouteByRouteId(Long id) {
        LogUtil.info(LogEnum.SERVING_GATEWAY, "Deal delete route event");
        routeDefinitionRepository.delete(Mono.just(GatewayConstant.ROUTE_PREFIX + id)).subscribe();
        // 发布刷新路由事件
        this.publisher.publishEvent(new RefreshRoutesEvent(this));
        LogUtil.info(LogEnum.SERVING_GATEWAY, "Deal delete route event done");
    }

    /**
     * 根据相应规则转化路由信息
     *
     * @param gatewayRouteQueryVO 数据库中存储的路由对象
     * @return 网关所需的RouteDefinition
     */
    private RouteDefinition convert2RouteDefinition(GatewayRouteQueryVO gatewayRouteQueryVO) {
        if(StringUtils.isEmpty(gatewayRouteQueryVO.getUri())){
            return null;
        }
        RouteDefinition definition = new RouteDefinition();
        // 设置路由基础信息
        definition.setId(GatewayConstant.ROUTE_PREFIX + gatewayRouteQueryVO.getId());
        definition.setUri(UriComponentsBuilder.fromHttpUrl(SymbolConstant.HTTP_SLASH + gatewayRouteQueryVO.getUri()).build().toUri());
        // 定义url匹配规则的断言
        // 举例 {abc}.dubhe.ai 匹配{abc}部分
        PredicateDefinition pathPredicate = new PredicateDefinition();
        pathPredicate.setName("Path");
        Map<String, String> predicateParams = new HashMap<>(NumberConstant.NUMBER_8);
        predicateParams.put("pattern", "/"+gatewayRouteQueryVO.getPatternPath() + "/**");
        pathPredicate.setArgs(predicateParams);
        // 定义权重断言
        // 根据配置的权重信息进行分流
        PredicateDefinition weightPredicate = new PredicateDefinition();
        weightPredicate.setName("Weight");
        Map<String, String> weightParams = new HashMap<>(NumberConstant.NUMBER_8);
        weightParams.put("weight.group", GatewayConstant.GROUP_PREFIX + gatewayRouteQueryVO.getServiceInfoId());
        weightParams.put("weight.weight", gatewayRouteQueryVO.getWeight());
        weightPredicate.setArgs(weightParams);
        definition.setPredicates(Arrays.asList(pathPredicate, weightPredicate));

        List<FilterDefinition> filters =new ArrayList<>();
        // 传入自定义的监控指标过滤器
        FilterDefinition filterDefinition = new FilterDefinition();
        // name设置为定义的MetricsGatewayFilterFactory中GatewayFilterFactory前部分
        filterDefinition.setName("Metrics");
        filters.add(filterDefinition);

        //去除一级url转发
        FilterDefinition filterDefinitionUrl = new FilterDefinition();
        filterDefinitionUrl.setName("StripPrefix");
        filterDefinitionUrl.addArg("parts","1");
        filters.add(filterDefinitionUrl);

        definition.setFilters(filters);
        // 塞入自定义数据，供之后filter使用
        HashMap<String, Object> metadata = new HashMap<>(NumberConstant.NUMBER_4);
        metadata.put("servingInfoId", gatewayRouteQueryVO.getServiceInfoId());
        metadata.put("servingConfigId", gatewayRouteQueryVO.getId());
        definition.setMetadata(metadata);
        return definition;
    }
}
