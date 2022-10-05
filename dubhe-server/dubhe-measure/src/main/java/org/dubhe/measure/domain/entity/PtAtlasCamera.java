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
package org.dubhe.measure.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;
import org.dubhe.biz.db.entity.BaseEntity;

/**
 * @description 炼知模型视频流实体
 * @date 2022-07-06
 */
@Data
@Accessors(chain = true)
@TableName("pt_atlas_camera")
public class PtAtlasCamera extends BaseEntity {

	@TableId(value = "id", type = IdType.AUTO)
	private Long id;


	/**
	 * 摄像头来源（0:默认，1：第三方vms）
	 */
	private Integer camera_resource;

	/**
	 * 区域唯一标识
	 */
	@TableField("region_index_code")
	private String regionIndexCode;

	/**
	 * 设备唯一标识
	 */
	@TableField("camera_index_code")
	private String cameraIndexCode;

	/**
	 * 摄像头名称
	 */
	@TableField("camera_name")
	private String cameraName;

	/**
	 * hls视频流地址
	 */
	@TableField("hls_url")
	private String hlsUrl;

	/**
	 * rtsp视频流地址
	 */
	@TableField("rtsp_url")
	private String rtspUrl;
	
	
	private Integer status;
	
	private String statusName;
	
}
