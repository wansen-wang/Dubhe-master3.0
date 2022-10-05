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
package org.dubhe.pointcloud.util;

import org.apache.shardingsphere.api.sharding.standard.PreciseShardingAlgorithm;
import org.apache.shardingsphere.api.sharding.standard.PreciseShardingValue;
import org.dubhe.biz.base.utils.SpringContextHolder;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.pointcloud.service.FileService;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @description 点云分表策略
 * @date 2022-04-14
 **/
public class PointCloudPreciseShardingAlgorithm implements PreciseShardingAlgorithm<Long> {


    public static final Long INTERVAL_NUMBER = 50L;

    public static Set<String> tableNames = new HashSet<>();

    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Long> shardingValue) {
        long startIndex = 1;
        long endIndex = 50;
        String tableName = shardingValue.getLogicTableName() + "_" + preciseSharding(shardingValue.getValue(), startIndex, endIndex);
        if (!tableNames.contains(tableName)) {
            FileService fileService = SpringContextHolder.getBean(FileService.class);
            if (!fileService.checkTableExist(tableName)) {
                try {
                    //表不存在进行创建
                    fileService.createNewTable(tableName);
                } catch (Exception e) {
                    LogUtil.info(LogEnum.POINT_CLOUD, "table name repeat {}", tableName);
                }
            }
            //移入集合
            tableNames.add(tableName);
        }
        return tableName;
    }

    /**
     * 分表处理逻辑
     *
     * @param indexId     当前值
     * @param startIndex  开始值
     * @param endIndex    结束值
     * @return long
     */
    public long preciseSharding(long indexId, long startIndex, long endIndex) {
        if (indexId > endIndex) {
            startIndex = startIndex + INTERVAL_NUMBER;
            endIndex = endIndex + INTERVAL_NUMBER;
            return preciseSharding(indexId, startIndex, endIndex);
        }
        return endIndex / INTERVAL_NUMBER;
    }
}
