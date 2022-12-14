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

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.servinggateway.constant.GatewayConstant;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @description redis路由定义加载类
 * @date 2020-09-07
 */
@Component
@Slf4j
public class RedisRouteDefinitionRepository implements RouteDefinitionRepository {

    @Resource
    private RedisTemplate<Object, Object> redisTemplate;

    /**
     * 保存路由信息
     *
     * @param route 路由信息
     * @return Mono<Void> 返回保存结果
     */
    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return route.flatMap(routeDefinition -> {
            LogUtil.info(LogEnum.SERVING_GATEWAY, "save route :" + JSON.toJSONString(routeDefinition));
            redisTemplate.opsForHash().put(GatewayConstant.SERVING_GATEWAY_ROUTES, routeDefinition.getId(), JSON.toJSONString(routeDefinition));
            return Mono.empty();
        });
    }

    /**
     * 删除路由信息
     *
     * @param routeId 路由id
     * @return Mono<Void> 返回删除结果
     */
    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return routeId.flatMap(id -> {
            if (redisTemplate.opsForHash().hasKey(GatewayConstant.SERVING_GATEWAY_ROUTES, id)) {
                String routeDefinitionString = redisTemplate.opsForHash().get(GatewayConstant.SERVING_GATEWAY_ROUTES, id).toString().replace(GatewayConstant.ROUTE_WEIGHT_100,GatewayConstant.ROUTE_WEIGHT_0);
                redisTemplate.opsForHash().put(GatewayConstant.SERVING_GATEWAY_ROUTES, id, routeDefinitionString);
                LogUtil.info(LogEnum.SERVING_GATEWAY, "update route :" + id + " weight : 0");
                return Mono.empty();
            }
            return Mono.empty();
        });
    }

    /**
     * 获取所有路由信息
     *
     * @return Flux<RouteDefinition> 路由信息集合
     */
    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        List<RouteDefinition> routeDefinitions = new ArrayList<>();
         redisTemplate.opsForHash().entries(GatewayConstant.SERVING_GATEWAY_ROUTES).forEach((k,v) ->{
             routeDefinitions.add(JSON.parseObject(v.toString(), RouteDefinition.class));
             if (v.toString().contains(GatewayConstant.ROUTE_WEIGHT_0)){
                 redisTemplate.opsForHash().delete(GatewayConstant.SERVING_GATEWAY_ROUTES, k);
                 LogUtil.info(LogEnum.SERVING_GATEWAY, "delete route :" + k);
             }
         });
        return Flux.fromIterable(routeDefinitions);
    }
}
