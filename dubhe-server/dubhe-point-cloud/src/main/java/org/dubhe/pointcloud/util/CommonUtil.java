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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.commons.lang3.ObjectUtils;
import org.dubhe.biz.base.constant.StringConstant;
import org.dubhe.biz.base.exception.BusinessException;
import org.dubhe.biz.base.utils.StringUtils;
import org.dubhe.biz.db.base.PageQueryBase;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.pointcloud.enums.ErrorEnum;

import java.util.Arrays;
import java.util.List;

/**
 * @description 公共方法
 * @date 2022-04-01
 **/
public class CommonUtil {

    /**
     *
     * @param pageQueryBase
     * @param wrapper
     * @param FILED_MANES
     * @return
     */
    public static QueryWrapper getSortWrapper(PageQueryBase pageQueryBase, QueryWrapper wrapper, final List<String> FILED_MANES) {
        try {
            //排序字段，默认按更新时间降序，否则将驼峰转换为下划线
            String column = "update_time";
            if (StringUtils.isNotBlank(pageQueryBase.getSort())) {
                List<String> sortList = Arrays.asList(StringUtils.deleteWhitespace(pageQueryBase.getSort()).split(","));
                if (FILED_MANES.containsAll(sortList)) {
                    column = StringUtils.humpToLine(pageQueryBase.getSort());
                }
            }
            //排序方式
            boolean isAsc = !StringUtils.isEmpty(pageQueryBase.getOrder()) && !StringUtils.equals(pageQueryBase.getOrder(), StringConstant.SORT_DESC);
            wrapper.orderBy(true, isAsc, column);
        } catch (Exception e) {
            LogUtil.error(LogEnum.POINT_CLOUD, "Failed to select data,error message:{}", e.getMessage());
            throw new BusinessException(ErrorEnum.INTERNAL_SERVER_ERROR);
        }
        return wrapper;
    }

    /**
     * 去除转义
     * @param queryName
     * @return
     */
    public static String escapeChar(String queryName) {
        if (!ObjectUtils.isEmpty(queryName)) {
            return queryName.replaceAll("/", "//")
                    .replaceAll("_", "/_")
                    .replaceAll("%", "/%");
        }
        return queryName;
    }
}
