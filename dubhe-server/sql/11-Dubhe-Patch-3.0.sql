-- DML 脚本
use `dubhe-cloud-prod`;

-- 上传炼知模型新增字段
ALTER TABLE pt_model_info ADD model_size INT COMMENT '模型尺寸';
ALTER TABLE pt_model_info ADD struct_name VARCHAR(32) COMMENT '模型结构名称';


CREATE TABLE `pt_model_structure` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `struct_name` varchar(64) NOT NULL COMMENT '炼知模型结构名称',
  `job_type` tinyint(1) DEFAULT '1' COMMENT '模型重组任务类型（1：单任务，2多任务）',
  `create_user_id` bigint(20) DEFAULT NULL COMMENT '创建人id',
  `update_user_id` bigint(20) DEFAULT NULL COMMENT '修改人id',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) DEFAULT b'0' COMMENT '删除标记 0正常，1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `struct_name_uniq` (`struct_name`) COMMENT '炼知模型结构名称唯一'
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COMMENT='炼制模型结构管理表';

-- 炼知重组任务类型字典
INSERT INTO `dict`(`name`, `remark`) VALUES ('job_type', '炼知模型重组任务类型');
select @dict_id := @@IDENTITY;
INSERT INTO `dict_detail`(`dict_id`, `label`, `value`, `sort`) VALUES ( @dict_id, '单任务', '1', 1);
INSERT INTO `dict_detail`(`dict_id`, `label`, `value`, `sort`) VALUES ( @dict_id, '多任务', '2', 2);

-- 数据集场景类型字典
INSERT INTO `dict`(`name`, `remark`) VALUES ('dataset_occasion', '数据集场景类型');
select @dict_id := @@IDENTITY;
INSERT INTO `dict_detail`(`dict_id`, `label`, `value`, `sort`) VALUES ( @dict_id, '普通数据集', '0', 1);
INSERT INTO `dict_detail`(`dict_id`, `label`, `value`, `sort`) VALUES ( @dict_id, '重组训练-单任务', '1', 1);
INSERT INTO `dict_detail`(`dict_id`, `label`, `value`, `sort`) VALUES ( @dict_id, '重组训练-多任务', '2', 2);
DELETE FROM `data_label` WHERE id <= 1160;


-- 训练任务表中添加任务类型字段
ALTER TABLE pt_train_job ADD job_type TINYINT(1) DEFAULT 1 COMMENT '模型重组任务类型（1：单任务，2多任务）' AFTER train_type;


CREATE TABLE `pt_atlas_train_param` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `train_job_id` bigint(20) NOT NULL COMMENT '训练作业jobId',
  `dataset_id` bigint(20) NOT NULL COMMENT '数据集id',
  `dataset_type` varchar(32) NOT NULL COMMENT '数据集类型(1-视觉/语音/文本, 2-医学影像, 3-点云)',
  `data_source_name` varchar(127) NOT NULL COMMENT '数据集名称',
  `data_source_path` varchar(127) NOT NULL COMMENT '数据集路径',
  `dataset_version` varchar(32) DEFAULT NULL COMMENT '数据集版本',
  `teacher_model_struct` varchar(64) NOT NULL COMMENT '教师模型结构名称',
  `teacher_model_name` varchar(127) DEFAULT NULL COMMENT '教师模型名称',
  `teacher_model_path` varchar(255) NOT NULL COMMENT '教师模型路径',
  `deleted` bit(1) DEFAULT b'0' COMMENT '删除标记 0正常，1已删除',
  PRIMARY KEY (`id`),
  KEY `train_job_id_inx` (`train_job_id`)
) ENGINE=InnoDB AUTO_INCREMENT=151 DEFAULT CHARSET=utf8mb4 COMMENT='炼知模型重组任务参数表';

-- 炼知模型重组任务场景下的默认资源规格
INSERT INTO `resource_specs`(`specs_name`, `resources_pool_type`, `module`, `cpu_num`, `gpu_num`, `mem_num`, `workspace_request`, `create_user_id`, `create_time`, `update_user_id`, `update_time`, `deleted`)
VALUES ('4CPU16GB内存 2GPU', 1, 7, 4, 2, 8192, 500, 1, '2022-06-24 11:23:13', 1, '2022-06-24 11:23:13', 0);
INSERT INTO `resource_specs`(`specs_name`, `resources_pool_type`, `module`, `cpu_num`, `gpu_num`, `mem_num`, `workspace_request`, `create_user_id`, `create_time`, `update_user_id`, `update_time`, `deleted`)
VALUES ('4CPU8GB内存 1GPU', 1, 7, 4, 1, 8192, 500, 1, '2022-06-23 14:25:25', 1, '2022-06-23 14:25:25', 0);

-- 创建重组任务菜单脚本
INSERT INTO `menu`(`pid`, `type`, `name`, `icon`, `path`, `component`, `component_name`, `layout`, `permission`, `hidden`, `cache`, `sort`, `create_user_id`, `update_user_id`, `create_time`, `update_time`, `deleted`, `back_to`, `ext_config`)
VALUES (40, 1, '创建重组任务', NULL, 'atlasJobAdd', 'trainingJob/atlasAdd', 'AtlasJobAdd', 'SubpageLayout', NULL, b'1', b'0', 44, 63, 63, '2022-06-14 10:54:50', '2022-06-14 11:05:56', b'0', NULL, '');



-- 数据可视化分析（见微）菜单脚本
INSERT INTO `menu`(`pid`, `type`, `name`, `icon`, `path`, `component`, `component_name`, `layout`, `permission`, `hidden`, `cache`, `sort`, `create_user_id`, `update_user_id`, `create_time`, `update_time`, `deleted`, `back_to`, `ext_config`)
VALUES (0, 3, '数据可视化分析', 'vis-logo', 'https://nebula-dev.zjvis.net', NULL, 'Vis', NULL, NULL, b'0', b'0', 85, 1, 1, '2022-07-27 13:50:23', '2022-08-02 09:10:41', b'0', NULL, NULL);



ALTER TABLE pt_train_algorithm DROP run_params, DROP inference;
CREATE TABLE `pt_image_type` (
  `image_id` int(8) NOT NULL COMMENT '镜像ID',
  `image_type` tinyint(1) NOT NULL COMMENT '镜像用途(Notebook-0, 训练-1, Serving-2, 终端-3, 点云-4)',
  PRIMARY KEY (`image_id`,`image_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


DELIMITER $$
DROP PROCEDURE IF EXISTS  init_image_type;
CREATE PROCEDURE init_image_type()
BEGIN
DECLARE $image_id INT(8);
DECLARE $project_name VARCHAR(100);
DECLARE $image_type TINYINT(1);
DECLARE done INT DEFAULT 0;
DECLARE cur CURSOR FOR SELECT id, project_name FROM pt_image;
DECLARE CONTINUE HANDLER FOR SQLSTATE '02000' SET done = 1;
OPEN cur;
    FETCH NEXT FROM cur INTO $image_id, $project_name;
REPEAT
    IF NOT done THEN
			IF $project_name = 'notebook' THEN
					SET $image_type = 0;
			ELSEIF $project_name = 'train' THEN
					SET $image_type = 1;
			ELSEIF $project_name = 'serving' THEN
					SET $image_type = 2;
			ELSEIF $project_name = 'terminal' THEN
					SET $image_type = 3;
			END IF;
			IF EXISTS (SELECT id FROM pt_image WHERE id = $image_id AND deleted=0) THEN
					INSERT INTO pt_image_type VALUES($image_id, $image_type);
			END IF;
	END IF;
	FETCH NEXT FROM cur INTO $image_id, $project_name;
UNTIL done END REPEAT;
CLOSE cur;
END $$


call init_image_type();

ALTER TABLE `pt_image` ADD COLUMN is_default tinyint(1) DEFAULT '0' COMMENT "是否为Notebook默认镜像(0否，1是)" after image_tag;
UPDATE `pt_image` SET is_default=1 WHERE project_name = 'notebook' AND image_resource=1;


UPDATE `pt_image` SET image_resource=1 WHERE project_name = 'notebook';
UPDATE `pt_image` SET origin_user_id=0 WHERE image_resource=1;

ALTER TABLE `pt_image` DROP project_name;

INSERT INTO `permission`(`pid`,`name`,`permission`,`create_user_id`,`update_user_id`,`deleted`) SELECT id, '修改Notebook默认镜像', 'training:image:editDefault', 1, 1, b'0' FROM `permission` WHERE `name` = '镜像管理';

INSERT INTO `auth_permission`(`auth_id`, `permission_id`) SELECT 1, id FROM `permission` WHERE `name` = '修改Notebook默认镜像';


ALTER TABLE `pt_train_param` ADD COLUMN run_params_name_map json DEFAULT NULL COMMENT "运行参数映射" after val_algorithm_usage;

ALTER TABLE `pt_job_param` ADD COLUMN run_params_name_map json DEFAULT NULL COMMENT "运行参数映射" after val_algorithm_usage;

ALTER TABLE `pt_train_param` modify column `run_command` varchar(8192) DEFAULT NULL COMMENT "运行命令";
ALTER TABLE `pt_job_param` modify column `run_command` varchar(8192) DEFAULT NULL COMMENT "运行命令";




ALTER TABLE `pt_train_param` ADD COLUMN dataset_type tinyint(1) DEFAULT NULL COMMENT "数据集类型(1-视觉/语音/文本, 2-医学影像, 3-点云)";
ALTER TABLE `pt_train_param` ADD COLUMN val_dataset_type tinyint(1) DEFAULT NULL COMMENT "验证数据集类型(1-视觉/语音/文本, 2-医学影像, 3-点云)";
ALTER TABLE `pt_job_param` ADD COLUMN dataset_type tinyint(1) DEFAULT NULL COMMENT "数据集类型(1-视觉/语音/文本, 2-医学影像, 3-点云)";
ALTER TABLE `pt_job_param` ADD COLUMN val_dataset_type tinyint(1) DEFAULT NULL COMMENT "验证数据集类型(1-视觉/语音/文本, 2-医学影像, 3-点云)";

UPDATE  `pt_train_param` SET dataset_type=1 WHERE algorithm_usage != '';
UPDATE  `pt_train_param` SET val_dataset_type=1 WHERE val_algorithm_usage != '';
UPDATE  `pt_job_param` SET dataset_type=1 WHERE algorithm_usage != '';
UPDATE  `pt_job_param` SET val_dataset_type=1 WHERE val_algorithm_usage != '';


ALTER TABLE `pt_train_param` DROP `algorithm_usage`;
ALTER TABLE `pt_train_param` DROP `val_algorithm_usage`;
ALTER TABLE `pt_job_param` DROP `algorithm_usage`;
ALTER TABLE `pt_job_param` DROP `val_algorithm_usage`;


ALTER TABLE `pt_train_job` ADD COLUMN data_source_id bigint(20) DEFAULT NULL COMMENT "数据集ID" AFTER data_source_path;
ALTER TABLE `pt_train_job` ADD COLUMN val_data_source_id bigint(20) DEFAULT NULL COMMENT "验证数据集ID" AFTER val_data_source_path;

ALTER TABLE pt_train_algorithm ADD COLUMN `algorithm_usages` varchar(255) DEFAULT '' COMMENT '算法用途' AFTER `algorithm_usage`;
UPDATE pt_train_algorithm a INNER JOIN (SELECT label, value FROM dict_detail WHERE dict_id=8) b ON a.algorithm_usage=b.label set a.algorithm_usages = b.value;
ALTER TABLE pt_train_algorithm DROP algorithm_usage;
ALTER TABLE pt_train_algorithm CHANGE algorithm_usages algorithm_usage varchar(255) DEFAULT '' COMMENT '算法用途';
UPDATE pt_model_info a INNER JOIN (SELECT label, value FROM dict_detail WHERE dict_id=8) b ON a.model_type=b.label set a.model_type = b.value;



UPDATE `menu` SET  `icon` = 'gailannew'  WHERE `name` = '概览' AND `path` = 'dashboard' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'shujuguanlinew'  WHERE `name` = '数据管理' AND `path` = 'data' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'shujujiguanli'  WHERE `name` = '数据集管理' AND `path` = 'datasets/list' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'biaoqianzuguanli'  WHERE `name` = '标签组管理' AND `path` = 'labelgroup' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'biaozhufuwu'  WHERE `name` = '标注服务管理' AND `path` = 'datasets/model-service' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'suanfakaifanew'  WHERE `name` = '算法开发' AND `path` = 'development' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'notebook', `path` = 'notebook'  WHERE `name` = 'Notebook' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'suanfaguanli'  WHERE `name` = '算法管理' AND `path` = 'algorithm' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'zidongjiqi'  WHERE `name` = '自动机器学习' AND `path` = 'tadl' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'shiyanguanlinew'  WHERE `name` = '实验管理' AND `path` = 'list' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'sousuochelve'  WHERE `name` = '搜索策略' AND `path` = 'searchstrategy' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'xunlianguanlinew'  WHERE `name` = '训练管理' AND `path` = 'training' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'xunlianrenwu'  WHERE `name` = '训练任务' AND `path` = 'job' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'keshihuarenwu'  WHERE `name` = '可视化任务' AND `path` = 'visual' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'jingxiangguanlinew'  WHERE `name` = '镜像管理' AND `path` = 'image' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'moxingguanlinew'  WHERE `name` = '模型管理' AND `path` = 'model' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'moxingliebiao'  WHERE `name` = '模型列表' AND `path` = 'model' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'moxingyouhua'  WHERE `name` = '模型优化' AND `path` = 'optimize' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'yunduanbushu'  WHERE `name` = '云端部署' AND `path` = 'cloudserving' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'zaixianfuwu'  WHERE `name` = '在线服务' AND `path` = 'onlineserving' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'piliangfuwu'  WHERE `name` = '批量服务' AND `path` = 'batchserving' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'moxinglianzhinew'  WHERE `name` = '模型炼知' AND `path` = 'atlas' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'moxingchongzu'  WHERE `name` = '模型重组' AND `path` = 'restructuring' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'duliangguanli'  WHERE `name` = '度量管理' AND `path` = 'measure' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'tupukeshihua'  WHERE `name` = '图谱可视化' AND `path` = 'graphvisual' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'tupuliebiao'  WHERE `name` = '图谱列表' AND `path` = 'graph' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'qianghuaxuexi'  WHERE `name` = '强化学习' AND `path` = 'reinforcelearning' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'tianshu'  WHERE `name` = '天枢专业版' AND `path` = 'terminal' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'zhongduangailan'  WHERE `name` = '终端概览' AND `path` = 'overview' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'yuanchenglianjie'  WHERE `name` = '远程连接' AND `path` = 'remote' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'shujukeshi'  WHERE `name` = '数据可视分析' AND `path` = 'https://nebula-dev.zjvis.net/login' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'kongzhitainew'  WHERE `name` = '控制台' AND `path` = 'system' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'yonghuguanlinew'  WHERE `name` = '用户管理' AND `path` = 'user' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'yonghuzuguanli'  WHERE `name` = '用户组管理' AND `path` = 'userGroup' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'jueseguanli'  WHERE `name` = '角色管理' AND `path` = 'role' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'quanxianguanli'  WHERE `name` = '权限管理' AND `path` = 'authCode' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'caidanguanlinew'  WHERE `name` = '菜单管理' AND `path` = 'menu' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'ziyuanguige'  WHERE `name` = '资源规格管理' AND `path` = 'resources' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'zidianguanli'  WHERE `name` = '字典管理' AND `path` = 'dict' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'jiqunzhuangtai'  WHERE `name` = '集群状态' AND `path` = 'node' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'huishouzhannew'  WHERE `name` = '回收站' AND `path` = 'recycle' AND `deleted` = 0;
UPDATE `menu` SET  `icon` = 'shujujiguanli'  WHERE `name` = '数据集管理' AND `path` = 'datasets' AND `deleted` = 0;
UPDATE `menu` SET  `hidden` = b'1' WHERE `name` = '模型优化执行记录';



-- 模型服务表
CREATE TABLE `auto_label_model_service` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `model_type` smallint(6) NOT NULL COMMENT '模型类别',
  `remark` varchar(255) DEFAULT NULL COMMENT '服务描述 ',
  `model_branch_id` bigint(20) DEFAULT NULL COMMENT '模型版本ID',
  `model_parent_id` bigint(20) DEFAULT NULL COMMENT '模型版本父ID',
  `image_id` bigint(20) NOT NULL COMMENT '镜像ID',
  `image_name` varchar(64) NOT NULL COMMENT '镜像名称',
  `algorithm_id` bigint(20) NOT NULL COMMENT '算法ID',
  `status` smallint(6) NOT NULL COMMENT '状态\n101-启动中\n102-运行中\n103-启动失败\n104-停止中\n105-已停止',
  `resources_pool_type` smallint(6) NOT NULL DEFAULT '0' COMMENT '节点类型\n0-cpu\n1-gpu',
  `resources_pool_specs` varchar(512) NOT NULL COMMENT '节点规格',
  `instance_num` smallint(6) NOT NULL COMMENT '服务数量',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '数据集版本删除标记0正常，1已删除',
  `create_user_id` bigint(20) NOT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_user_id` bigint(20) DEFAULT NULL COMMENT '修改人',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `name` varchar(32) DEFAULT NULL COMMENT '服务名称',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_name` (`name`) USING BTREE COMMENT '模型服务名称不重复'
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 COMMENT='标注服务';

-- 数据集表增加模块字段
ALTER TABLE `data_dataset` ADD COLUMN `template_type` int(11) NULL DEFAULT 101 AFTER `source_id`;
ALTER TABLE `data_dataset` ADD COLUMN `module` smallint(3) NULL COMMENT '模块 1-普通数据集 2-重组训练-单任务 3-重组训练-多任务' AFTER `template_type`;
ALTER TABLE `data_dataset` MODIFY COLUMN `annotate_type` smallint(5) NOT NULL DEFAULT 0 COMMENT '标注类型：2分类,1目标检测,5目标跟踪' AFTER `data_type`;

-- 数据集版本表增加字段
ALTER TABLE `data_dataset_version` ADD COLUMN `format` char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'TS' COMMENT '格式  TS COCO YOLO' AFTER `of_record`;
ALTER TABLE `data_dataset_version` ADD COLUMN `contain_origin` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否包含源文件' AFTER `format`;

-- 数据集任务表
ALTER TABLE `data_task` ADD COLUMN `model_service_id` bigint(20) NULL COMMENT '模型服务id' AFTER `target_id`;
ALTER TABLE `data_task` ADD COLUMN `file_type` smallint(3) NULL COMMENT '待处理文件类型 0-全部 304-无标注 303-有标注' AFTER `model_service_id`;
ALTER TABLE `data_task` ADD COLUMN `stop` bit(1) NULL COMMENT '是否停止 0-没有 1-已停止' AFTER `file_type`;
ALTER TABLE `data_task` ADD COLUMN `of_record_version` varchar(255) NULL COMMENT '生成ofRecord的版本号' AFTER `stop`;

-- 医学表
ALTER TABLE `data_medicine` ADD COLUMN `stop` bit(1) NULL AFTER `annotate_type`;


-- 点云数据集表
create table if not exists `pc_dataset` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `name` varchar(64) CHARACTER SET utf8 NOT NULL COMMENT '数据集名称',
  `label_group_id` bigint(20) NOT NULL COMMENT '标签组id',
  `difficulty_count` bigint(20) DEFAULT '0' COMMENT '难例数',
  `file_count` bigint(20) DEFAULT '0' COMMENT '文件数',
  `status` int(4) DEFAULT '1001' COMMENT '1001:未采样 1002:导入中 1003:未标注 1004:自动标注中 1005:自动标注停止 1006:自动标注失败 1007:标注中 1008:自动标注完成 1009:难例发布中 1010:难例发布失败 1011:已发布',
  `status_detail` varchar(255) DEFAULT NULL COMMENT '状态详情',
  `resource_name` varchar(255) DEFAULT NULL COMMENT '资源名称',
  `scope_left` double(20,6) DEFAULT '0.000000' COMMENT '标注范围-左',
  `scope_right` double(20,6) DEFAULT '0.000000' COMMENT '标注范围-右',
  `scope_front` double(20,6) DEFAULT '0.000000' COMMENT '标注范围-前',
  `scope_behind` double(20,6) DEFAULT '0.000000' COMMENT '标注范围-后',
  `remark` varchar(255) DEFAULT NULL COMMENT '数据集描述',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_user_id` bigint(20) DEFAULT NULL,
  `update_user_id` bigint(20) DEFAULT NULL,
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '删除（0：正常，1：删除）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4;

-- 点云文件表
create table if not exists `pc_dataset_file` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `name` varchar(256) CHARACTER SET utf8 NOT NULL COMMENT '文件名称',
  `file_type` varchar(20) NOT NULL COMMENT '文件类型',
  `difficulty` int(1) DEFAULT NULL COMMENT '是否难例',
  `dataset_id` bigint(20) DEFAULT NULL COMMENT '数据集id',
  `url` varchar(256) NOT NULL COMMENT '文件url',
  `mark_status` int(8) DEFAULT NULL COMMENT '标注状态',
  `mark_file_name` varchar(64) DEFAULT NULL COMMENT '标注结果文件名称',
  `mark_file_url` varchar(256) DEFAULT NULL COMMENT '标注结果文件url',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_user_id` bigint(20) DEFAULT NULL,
  `create_user_id` bigint(20) DEFAULT NULL,
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '删除（0：正常，1：删除）',
  PRIMARY KEY (`id`),
  KEY `dataset_id_index` (`dataset_id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4;

-- 点云自动标注详情表
create table if not exists `pc_annotation_detail` (
  `dataset_id` bigint(20) NOT NULL COMMENT '数据集id',
  `dataset_dir_mapping` varchar(256) DEFAULT NULL COMMENT '数据集路径映射',
  `algorithm_id` bigint(20) NOT NULL COMMENT '算法Id',
  `algorithm_name` varchar(256) NOT NULL COMMENT '算法名称',
  `algorithm_source` int(20) NOT NULL COMMENT '算法来源',
  `model_id` bigint(20) NOT NULL COMMENT '模型id',
  `model_branch_id` bigint(20) NOT NULL COMMENT '模型对应版本id',
  `model_resource` int(20) NOT NULL COMMENT '模型来源',
  `model_name` varchar(256) NOT NULL COMMENT '模型名称',
  `model_version` varchar(128) DEFAULT NULL COMMENT '模型版本',
  `image_name` varchar(256) NOT NULL COMMENT '镜像名称',
  `image_tag` varchar(128) NOT NULL COMMENT '镜像版本',
  `pool_specs_info` varchar(256) DEFAULT NULL COMMENT '规格信息',
  `resources_pool_node` int(11) NOT NULL COMMENT '节点个数',
  `resources_pool_type` int(11) NOT NULL COMMENT '节点类型(0为CPU，1为GPU)',
  `model_dir_mapping` varchar(256) DEFAULT NULL COMMENT '模型路径映射',
  `result_dir_mapping` varchar(256) DEFAULT NULL COMMENT '结果输出路径映射',
  `resources_pool_specs` varchar(256) NOT NULL COMMENT '节点规格',
  `command` varchar(256) NOT NULL COMMENT '标注命令',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `create_time` timestamp NULL DEFAULT NULL,
  `create_user_id` bigint(20) DEFAULT NULL,
  `update_user_id` bigint(20) DEFAULT NULL,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`dataset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 修改字典数据

-- 删除旧数据
DELETE FROM `dict_detail` WHERE dict_id = 8;
-- 写入新数据
INSERT INTO `dict_detail`(`dict_id`, `label`, `value`, `sort`) VALUES (8, '图像分类', '101', 1);
INSERT INTO `dict_detail`(`dict_id`, `label`, `value`, `sort`) VALUES (8, '目标检测', '102', 2);
INSERT INTO `dict_detail`(`dict_id`, `label`, `value`, `sort`) VALUES (8, '语义分割', '103', 3);
INSERT INTO `dict_detail`(`dict_id`, `label`, `value`, `sort`) VALUES (8, '文本分类', '301', 5);
INSERT INTO `dict_detail`(`dict_id`, `label`, `value`, `sort`) VALUES (8, '目标跟踪', '201', 4);
INSERT INTO `dict_detail`(`dict_id`, `label`, `value`, `sort`) VALUES (8, '中文分词', '302', 6);
INSERT INTO `dict_detail`(`dict_id`, `label`, `value`, `sort`) VALUES (8, '命名实体识别', '303', 7);
INSERT INTO `dict_detail`(`dict_id`, `label`, `value`, `sort`) VALUES (8, '音频分类', '401', 8);
INSERT INTO `dict_detail`(`dict_id`, `label`, `value`, `sort`) VALUES (8, '语音识别', '402', 9);
INSERT INTO `dict_detail`(`dict_id`, `label`, `value`, `sort`) VALUES (8, '自定义', '10001', 13);
INSERT INTO `dict_detail`(`dict_id`, `label`, `value`, `sort`) VALUES (8, '器官分割', '1001', 10);
INSERT INTO `dict_detail`(`dict_id`, `label`, `value`, `sort`) VALUES (8, '肺结节检测', '2001', 11);
INSERT INTO `dict_detail`(`dict_id`, `label`, `value`, `sort`) VALUES (8, '其他病灶识别', '2999', 12);
INSERT INTO `dict_detail`(`dict_id`, `label`, `value`, `sort`) VALUES (8, '模型优化', '5001', 13);


-- 菜单中数据集管理部分修改
DELETE FROM menu where id = 12;

INSERT INTO `menu`(`pid`, `type`, `name`, `icon`, `path`, `component`, `component_name`, `layout`, `permission`, `hidden`, `cache`, `sort`, `create_user_id`, `update_user_id`, `create_time`, `update_time`, `deleted`, `back_to`, `ext_config`) 
VALUES (10, 1, '文本分类', NULL, 'datasets/text/classify/:datasetId', 'dataset/nlp/textClassify', 'TextClassify', 'DetailLayout', NULL, b'1', b'0', 29, 67, 67, '2022-06-20 14:46:39', '2022-06-29 10:11:00', b'0', NULL, '');
INSERT INTO `menu`(`pid`, `type`, `name`, `icon`, `path`, `component`, `component_name`, `layout`, `permission`, `hidden`, `cache`, `sort`, `create_user_id`, `update_user_id`, `create_time`, `update_time`, `deleted`, `back_to`, `ext_config`) 
VALUES (10, 1, '图像分类', NULL, 'datasets/classification/:datasetId', 'dataset/classification/index', 'DatasetClassification', 'DetailLayout', 'dataset', b'1', b'0', 12, 67, 67, '2022-06-08 11:10:32', '2022-06-29 10:04:11', b'0', NULL, '');


-- 点云菜单
insert  into `menu` (`pid`,`type`,`name`,`icon`,`path`,`component`,`component_name`,`layout`,`permission`,`back_to`,`ext_config`,`hidden`,`cache`,`sort`,`create_user_id`,`update_user_id`,`deleted`) values
(10, 1, '3D点云数据集', null, 'datasets/pointcloud', 'dataset/pointCloud/list', 'PointCloud', 'BaseLayout', null, null, '', true, false, 45, 14, 14, false),
(10, 1, '点云编辑器', null, 'datasets/pointcloud/editor', 'dataset/pointCloud/editor', 'PointCloudEditor', 'FullpageLayout', null, null, '', true, false, 56, 14, 14, false);

UPDATE `menu` SET `pid` = 0 WHERE `name` = '镜像管理';

-- 模型重组菜单
INSERT INTO `menu`(`pid`, `type`, `name`, `icon`, `path`, `component`, `component_name`, `layout`, `permission`, `hidden`, `cache`, `sort`, `create_user_id`, `update_user_id`, `create_time`, `update_time`, `deleted`, `back_to`, `ext_config`) 
VALUES (97, 1, '模型重组', 'moxingchongzu', 'restructuring', 'atlas/modelRestructuring', 'ModelRestructuring', 'BaseLayout', NULL, b'0', b'0', 70, 3, 63, '2022-06-20 10:44:14', '2022-08-09 15:19:59', b'0', NULL, '');
-- 标注服务管理
INSERT INTO `menu`(`pid`, `type`, `name`, `icon`, `path`, `component`, `component_name`, `layout`, `permission`, `hidden`, `cache`, `sort`, `create_user_id`, `update_user_id`, `create_time`, `update_time`, `deleted`, `back_to`, `ext_config`) SELECT id, 1 as `type`, '标注服务管理' as `name`, 'biaozhufuwu' as `icon`, 'datasets/model-service' as `path`, 'dataset/modelService' as `component`, 'ModelDataService' as `component_name`, 'BaseLayout' as `layout`, NULL as `permission`, b'0' as `hidden`, b'0' as `cache`, 999 as `sort`, 1 as `create_user_id`, 1 as `update_user_id`, '2022-05-24 15:05:50' as `create_time`, '2022-08-09 15:03:14' as `update_time`, b'0' as `deleted`, NULL as `back_to`, '' as `ext_config` FROM `menu` WHERE `name` = '数据管理';

-- 炼知模型重组预置算法
INSERT INTO `pt_train_algorithm`(`algorithm_name`, `description`, `algorithm_source`, `algorithm_status`, `code_dir`, `run_command`, `algorithm_usage`, `accuracy`, `p4_inference_speed`, `create_user_id`, `create_time`, `update_user_id`, `update_time`, `deleted`, `image_name`, `is_train_model_out`, `is_train_out`, `is_visualized_log`, `origin_user_id`)
VALUES ('layerwise-amalgamation', '炼知模型重组训练layerwise算法', 2, 1, '/algorithm-manage/common/9/20220610151221705g5ei/', 'python  comprehensive_classification_layerwise_amalgamation.py', '101', '', NULL, 1, '2022-06-10 15:12:22', 1, '2022-06-10 15:12:22', 0, 'atlas:v1', 1, 0, 0, 0);
INSERT INTO `pt_train_algorithm`(`algorithm_name`, `description`, `algorithm_source`, `algorithm_status`, `code_dir`, `run_command`, `algorithm_usage`, `accuracy`, `p4_inference_speed`, `create_user_id`, `create_time`, `update_user_id`, `update_time`, `deleted`, `image_name`, `is_train_model_out`, `is_train_out`, `is_visualized_log`, `origin_user_id`)
VALUES ('common-feature-learning', '炼知模型重组训练CFL算法', 2, 1, '/algorithm-manage/common/9/20220620150033773qocy/', 'python  comprehensive_classification_common_feature_learning.py', '101', '', NULL, 9, '2022-06-20 15:00:34', 9, '2022-06-20 15:00:34', 0, 'train/atlas:v1', 1, 0, 0, 0);
INSERT INTO `pt_train_algorithm`(`algorithm_name`, `description`, `algorithm_source`, `algorithm_status`, `code_dir`, `run_command`, `algorithm_usage`, `accuracy`, `p4_inference_speed`, `create_user_id`, `create_time`, `update_user_id`, `update_time`, `deleted`, `image_name`, `is_train_model_out`, `is_train_out`, `is_visualized_log`, `origin_user_id`)
VALUES ('task-branching', '炼知模型重组训练多任务算法', 2, 1, '/algorithm-manage/common/9/20220704110347339o7cr/', 'python  joint_scene_parsing_task_branching.py', '101', '', NULL, 1, '2022-07-04 11:03:47', 1, '2022-07-04 11:03:47', 0, 'atlas:v1', 0, 0, 0, 0);

CREATE TABLE `pt_atlas_camera` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `camera_resource` tinyint(1) DEFAULT '0' COMMENT '摄像头来源（1：第三方vms）',
  `region_index_code` varchar(64) DEFAULT NULL COMMENT '区域唯一标识',
  `camera_index_code` varchar(64) NOT NULL COMMENT '设备唯一标识',
  `camera_name` varchar(255) NOT NULL COMMENT '摄像头名称',
  `hls_url` varchar(128) NOT NULL COMMENT 'hls视频流地址',
  `rtsp_url` varchar(128) DEFAULT NULL COMMENT 'rtsp视频流地址',
  `status` tinyint(1) NOT NULL,
  `status_name` varchar(32) NOT NULL,
  `create_user_id` bigint(20) DEFAULT NULL COMMENT '创建人id',
  `update_user_id` bigint(20) DEFAULT NULL COMMENT '修改人id',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) DEFAULT b'0' COMMENT '删除标记 0正常，1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `camera_index_uniq` (`camera_index_code`) COMMENT '设备标识唯一'
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COMMENT='模型炼知视频流管理';

ALTER TABLE pt_image DROP image_status;
