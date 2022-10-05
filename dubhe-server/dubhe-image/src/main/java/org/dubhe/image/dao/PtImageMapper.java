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

package org.dubhe.image.dao;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Many;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.mapping.FetchType;
import org.dubhe.biz.base.annotation.DataPermission;
import org.dubhe.image.domain.entity.PtImage;

import java.util.List;


/**
 * @description 镜像 Mapper 接口
 * @date 2020-04-27
 */
public interface PtImageMapper extends BaseMapper<PtImage> {

    /**
     * 还原回收数据
     *
     * @param id            镜像id
     * @param deleteFlag    删除标识
     */
    @Update("update pt_image set deleted = #{deleteFlag} where id = #{id}")
    void updateDeletedById(@Param("id") Long id, @Param("deleteFlag") boolean deleteFlag);

    /**
     * 添加镜像与镜像用途映射
     *
     * @param imageId 镜像ID
     * @param imageType 镜像用途
     */
    @Insert("insert into pt_image_type values (#{imageId}, #{imageType})")
    void insertImageType(Long imageId, int imageType);

    @Delete("delete from pt_image_type where image_id = #{imageId} and image_type = #{imageType}")
    void deleteImageType(Long imageId, int imageType);

    /**
     * 分页查询镜像列表
     */
    @Select("select * from pt_image ${ew.customSqlSegment}")
    @Results(id = "ptImageMapperResults",
            value = {
                    @Result(property = "id", column = "id"),
                    @Result(property = "imageTypes",
                            column = "id",
                            many = @Many(select = "org.dubhe.image.dao.PtImageMapper.selectImageType", fetchType = FetchType.LAZY)),
                    })
    IPage<PtImage> getImagesPage(Page<PtImage> page, @Param("ew") Wrapper<PtImage> queryWrapper);


    /**
     * 按照镜像用途分页查询镜像列表
     * @param imageTypes 镜像用途
     */
    @Select("<script>" +
            "select * from pt_image inner join pt_image_type" +
            " on pt_image.id=pt_image_type.image_id and pt_image_type.image_type in <foreach item='imageType' index='index' collection='imageTypes' open='(' separator=',' close=')'>" +
            " #{imageType} </foreach> ${ew.customSqlSegment}" +
            " </script>")
    @ResultMap(value = "ptImageMapperResults")
    IPage<PtImage> getImagesPageByImageTypes(Page<PtImage> page, @Param("ew") Wrapper<PtImage> queryWrapper, @Param("imageTypes") List<Integer> imageTypes);

    /**
     * 按照镜像用途查询镜像列表
     * @param imageTypes 镜像用途
     */
    @Select("<script>" +
            "select * from pt_image inner join  pt_image_type" +
            " on pt_image.id=pt_image_type.image_id and pt_image_type.image_type in <foreach item='imageType' index='index' collection='imageTypes' open='(' separator=',' close=')'>" +
            " #{imageType} </foreach> ${ew.customSqlSegment}" +
            " </script>")
    @ResultMap(value = "ptImageMapperResults")
    List<PtImage> getImagesByTypes(@Param("ew") Wrapper<PtImage> queryWrapper, @Param("imageTypes") List<Integer> imageTypes);


    /**
     * 根据镜像 ID 查询镜像
     * @param id 镜像ID
     */
    @Select("select * from pt_image where id = #{id} and deleted=0")
    @ResultMap(value = "ptImageMapperResults")
    PtImage getImageById(@Param("id") Long id);

    /**
     * 查询 Notebook 默认镜像
     * @param isDefault 默认标识
     */
    @Select("select * from pt_image where is_default = #{isDefault} and image_resource=1 and deleted=0")
    List<PtImage> getImageByDefault(@Param("isDefault") Integer isDefault);

    /**
     * 根据镜像 ID 查询镜像用途
     * @param imageId 镜像 ID
     */
    @Select("select image_type from pt_image_type where image_id = #{imageId}")
    List<Integer> selectImageType (@Param("imageId") Long imageId);

}
