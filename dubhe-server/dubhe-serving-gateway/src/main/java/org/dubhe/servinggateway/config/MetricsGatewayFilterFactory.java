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

import cn.hutool.core.io.IoUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dubhe.biz.base.constant.NumberConstant;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.servinggateway.constant.GatewayConstant;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * @description ???????????????????????????
 * @date 2020-09-25
 */
@Component
@Slf4j
public class MetricsGatewayFilterFactory extends AbstractGatewayFilterFactory<MetricsGatewayFilterFactory.Config> {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * constructor
     */
    public MetricsGatewayFilterFactory() {
        // ???????????????????????????config???????????????????????????ClassCastException
        super(Config.class);
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return Collections.emptyList();
    }

    @Override
    public GatewayFilter apply(Config config) {
        return new MetricsGatewayFilter(config);
    }

    /**
     * ????????????config?????????????????????????????????
     */
    public static class Config {

    }

    private class MetricsGatewayFilter implements GatewayFilter, Ordered {

        private Config config;

        MetricsGatewayFilter(Config config) {
            this.config = config;
        }

        /**
         * ??????????????????
         *
         * @param exchange ?????????????????????
         * @param chain    ??????????????????
         * @return Mono<Void> ????????????????????????
         */
        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            ServerHttpResponse serverHttpResponse = exchange.getResponse();
            ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(serverHttpResponse) {
                @Override
                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                    if (body instanceof Flux) {
                        return super.writeWith(
                                DataBufferUtils.join(body)
                                        .doOnNext(dataBuffer -> {
                                            // ????????????????????????????????????
                                            boolean isInference = exchange.getRequest().getPath().toString().endsWith(GatewayConstant.INFERENCE_INTERFACE_NAME);
                                            if (isInference) {
                                                LogUtil.info(LogEnum.SERVING_GATEWAY, "Begin deal inference request filter");
                                                // ????????????????????????
                                                boolean callFailed = !HttpStatus.OK.equals(serverHttpResponse.getStatusCode());

                                                // ??????????????????
                                                Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
                                                if(route == null){
                                                    LogUtil.error(LogEnum.SERVING_GATEWAY,"????????????????????????");
                                                    throw  new BusinessException("????????????????????????");
                                                }

                                                // ??????redis???????????????????????????
                                                Map<String, Object> metadata = route.getMetadata();
                                                String metricsKey = GatewayConstant.INFERENCE_METRICS_PREFIX + metadata.get("servingConfigId");
                                                redisTemplate.opsForHash().increment(metricsKey, GatewayConstant.INFERENCE_CALL_COUNT, NumberConstant.NUMBER_1);
                                                if (callFailed) {
                                                    LogUtil.error(LogEnum.SERVING_GATEWAY, "Serving inference called failed");
                                                    redisTemplate.opsForHash().increment(metricsKey, GatewayConstant.INFERENCE_FAILED_COUNT, NumberConstant.NUMBER_1);
                                                    throw new BusinessException("????????????????????????????????????" + serverHttpResponse.getStatusCode());
                                                } else {
                                                    boolean isJsonResponse = true;
                                                    JSONObject inferenceResult = null;
                                                    try {
                                                        inferenceResult = JSON.parseObject(getResponseBody(serverHttpResponse, dataBuffer));
                                                    } catch (Exception e) {
                                                        isJsonResponse = false;
                                                    }
                                                    // ?????????JSON????????????????????????????????????
                                                    if (!isJsonResponse || Objects.isNull(inferenceResult) ||
                                                            !Boolean.TRUE.equals(inferenceResult.getBoolean(GatewayConstant.SUCCESS))) {
                                                        LogUtil.error(LogEnum.SERVING_GATEWAY, "Serving inference result JSON parse failed");
                                                        redisTemplate.opsForHash().increment(metricsKey, GatewayConstant.INFERENCE_FAILED_COUNT, NumberConstant.NUMBER_1);
                                                        throw new BusinessException("??????????????????");
                                                    }
                                                }
                                                LogUtil.info(LogEnum.SERVING_GATEWAY, "Deal inference request filter Done");
                                            }
                                        })
                        );
                    }
                    return super.writeWith(body);
                }
            };
            return chain.filter(exchange.mutate().response(responseDecorator).build());
        }

        /**
         * @param response   ??????
         * @param dataBuffer ????????????
         * @return String ??????response body??????
         */
        private String getResponseBody(ServerHttpResponse response, DataBuffer dataBuffer) {
            String bodyContent = "";
            // ??????response?????????
            String contentEncoding = response.getHeaders().getFirst("Content-Encoding");
            // ??????response???????????????gzip
            if (StringUtils.isNotBlank(contentEncoding) && contentEncoding.contains("gzip")) {
                // ??????gzip????????????
                bodyContent = decompressWithGZIP(dataBuffer, StandardCharsets.UTF_8);
            } else {
                bodyContent = dataBuffer.toString(StandardCharsets.UTF_8);
            }
            return bodyContent;
        }

        /**
         * ???????????????????????????????????????gzip??????
         *
         * @param dataBuffer ???????????????
         * @param charset    ???????????????
         * @return String ????????????????????????
         */
        private String decompressWithGZIP(DataBuffer dataBuffer, Charset charset) {
            GZIPInputStream gis;
            String content = "";
            try {
                gis = new GZIPInputStream(new ByteArrayInputStream(dataBuffer.asByteBuffer().array()));

                content = IoUtil.read(gis, charset);
            } catch (IOException e) {
                log.error("????????????");
            }
            return content;
        }

        /**
         * ????????????????????????
         *
         * @return int ???????????????
         */
        @Override
        public int getOrder() {
            // -1 is response write filter, must be called before that
            // ????????????????????????????????????WriteFilter
            return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - NumberConstant.NUMBER_1;
        }
    }
}
