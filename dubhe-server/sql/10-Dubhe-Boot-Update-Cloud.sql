/**
  注意一下三张表可能有分表
  data_dataset_version、data_file、data_file_annotation
  在分表上同样执行表结构的修改语句
 */


-- DML 脚本
use `dubhe-cloud-prod`;

SET SQL_SAFE_UPDATES = 0;
DELIMITER //
DROP PROCEDURE IF EXISTS  fourthEditionProc ;
CREATE PROCEDURE fourthEditionProc()
BEGIN
    DECLARE t_error INTEGER DEFAULT 0 ;
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET t_error=1;
    SET autocommit=0;
    START TRANSACTION;


-- data_dataset_version
    ALTER TABLE `data_dataset_version` MODIFY `data_conversion` INT(1) NOT NULL DEFAULT '0' COMMENT '数据转换；0：图片未复制；1：未转换；2：已转换；3：无法转换; 4:标注未复制';
    ALTER TABLE `data_dataset_version` MODIFY `origin_user_id` BIGINT(20) DEFAULT '0' COMMENT '资源拥有人id';
    ALTER TABLE `data_dataset_version` ADD `of_record` TINYINT(1) DEFAULT '0' COMMENT '是否生成ofRecord文件';

-- data_file
    ALTER TABLE `data_file` DROP COLUMN `md5`;
    ALTER TABLE `data_file` MODIFY `origin_user_id` BIGINT(19) DEFAULT NULL COMMENT '资源拥有者ID';
    ALTER TABLE `data_file` ADD `es_transport` INT(1) DEFAULT '0' COMMENT '是否上传至es';
    ALTER TABLE `data_file` ADD `exclude_header` SMALLINT(6) NOT NULL DEFAULT '1' COMMENT 'table数据导入时，是否排除文件头';
    ALTER TABLE `data_file` ADD KEY `es_transport` (`es_transport`) USING BTREE;

-- data_file_annotation
    ALTER TABLE `data_file_annotation` ADD `file_name` VARCHAR(255) DEFAULT NULL COMMENT '文件名称';
    ALTER TABLE `data_file_annotation` ADD `status` TINYINT(1) DEFAULT '0' COMMENT '状态 0: 新增 1:删除 2:正常';
    ALTER TABLE `data_file_annotation` ADD `invariable` TINYINT(1) DEFAULT '0' COMMENT '是否为版本标注信息0：否 1：是';
    ALTER TABLE `data_file_annotation` ADD KEY `label_dataset_id_indx` (`label_id`,`dataset_id`) USING BTREE;

    DROP TABLE IF EXISTS `auth`;


    CREATE TABLE `auth` (
                            `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
                            `auth_code` VARCHAR(32) NOT NULL COMMENT '权限code',
                            `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
                            `create_user_id` BIGINT(20) DEFAULT NULL COMMENT '创建人id',
                            `update_user_id` BIGINT(20) DEFAULT NULL COMMENT '修改人id',
                            `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                            `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            `deleted` BIT(1) DEFAULT b'0' COMMENT '删除标记 0正常，1已删除',
                            PRIMARY KEY (`id`),
                            KEY `auth_index_auth_code` (`auth_code`)
    ) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COMMENT='权限管理表';



-- auth_permission
    DROP TABLE IF EXISTS `auth_permission`;

    CREATE TABLE `auth_permission` (
                                       `auth_id` BIGINT(20) NOT NULL,
                                       `permission_id` BIGINT(20) NOT NULL
    ) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COMMENT='权限组-权限关联表';


-- data_dataset
    ALTER TABLE `data_dataset` MODIFY  `type` TINYINT(4) NOT NULL DEFAULT '0' COMMENT '类型 0: private 私有数据,  1:team  团队数据  2:public 公开数据';

    ALTER TABLE `data_dataset` MODIFY `data_type` TINYINT(4) NOT NULL DEFAULT '0' COMMENT '数据类型:0图片，1视频，2文本';

    ALTER TABLE `data_dataset` MODIFY `annotate_type` TINYINT(4) NOT NULL DEFAULT '0' COMMENT '标注类型：2分类,1目标检测,5目标跟踪';

    ALTER TABLE `data_dataset` MODIFY `origin_user_id` BIGINT(20) DEFAULT NULL COMMENT '资源拥有人id';

    ALTER TABLE `data_dataset` ADD `source_id` BIGINT(20) DEFAULT NULL COMMENT '数据集源ID';

    ALTER TABLE `data_dataset` drop key  `idx_name_unique`;



-- -- data_dataset_label
    ALTER TABLE `data_dataset_label` ADD `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
    ALTER TABLE `data_dataset_label` ADD `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间';
    ALTER TABLE `data_dataset_label` ADD `deleted` TINYINT(1) NOT NULL DEFAULT '0' COMMENT '删除(0正常，1已删除)';
    ALTER TABLE `data_dataset_label` ADD `create_user_id` BIGINT(20) DEFAULT NULL COMMENT '创建人id';
    ALTER TABLE `data_dataset_label` ADD `update_user_id` BIGINT(20) DEFAULT NULL COMMENT '修改人id';




-- data_label_group
    ALTER TABLE `data_label_group` MODIFY `origin_user_id` BIGINT(20) DEFAULT NULL COMMENT '资源拥有人';
    ALTER TABLE `data_label_group` MODIFY  `label_group_type` int(1) NOT NULL DEFAULT '0' COMMENT '标签组数据类型  0:视觉  1:文本';


-- data_sequence
    ALTER TABLE `data_sequence` add UNIQUE KEY `business_code_unique` (`business_code`) USING BTREE;

-- data_task
    ALTER TABLE `data_task` ADD `merge_column` VARCHAR(255) DEFAULT NULL COMMENT 'csv合并列';
    ALTER TABLE `data_task` ADD `version_name` VARCHAR(255) DEFAULT NULL COMMENT '转预置版本号';
    ALTER TABLE `data_task` ADD `target_id` BIGINT(20) DEFAULT NULL COMMENT '目标数据集id';


-- model_opt_task
    ALTER TABLE `model_opt_task` ADD `origin_user_id` BIGINT(20) DEFAULT NULL COMMENT '资源用有人ID';

-- model_opt_task_instance
    ALTER TABLE `model_opt_task_instance` ADD `status_detail` JSON DEFAULT NULL COMMENT '状态对应的详情信息';
    ALTER TABLE `model_opt_task_instance` ADD `origin_user_id` BIGINT(20) DEFAULT NULL COMMENT '资源用有人ID';


-- notebook
    ALTER TABLE `notebook` MODIFY `cpu_num` INT(11) NOT NULL DEFAULT '0' COMMENT 'CPU数量(核)';
    ALTER TABLE `notebook` MODIFY `gpu_num` INT(11) NOT NULL DEFAULT '0' COMMENT 'GPU数量（核）';
    ALTER TABLE `notebook` MODIFY `mem_num` INT(11) NOT NULL DEFAULT '0' COMMENT '内存大小（M）';
    ALTER TABLE `notebook` MODIFY `disk_mem_num` INT(11) NOT NULL DEFAULT '0' COMMENT '硬盘内存大小（M）';
    ALTER TABLE `notebook` ADD `status_detail` JSON DEFAULT NULL COMMENT '状态对应的详情信息';
    ALTER TABLE `notebook` MODIFY `notebook_name` varchar(100) DEFAULT NULL COMMENT 'notebook名称(供前端使用)';


    DROP TABLE IF EXISTS `notebook_model`;
    DROP TABLE IF EXISTS `pt_train_job_specs`;
    DROP TABLE IF EXISTS `recycle_task`;


    DROP TABLE IF EXISTS `oauth_access_token`;

    CREATE TABLE `oauth_access_token` (
                                          `token_id` VARCHAR(256) DEFAULT NULL,
                                          `token` BLOB,
                                          `authentication_id` VARCHAR(256) DEFAULT NULL,
                                          `user_name` VARCHAR(256) DEFAULT NULL,
                                          `client_id` VARCHAR(256) DEFAULT NULL,
                                          `authentication` BLOB,
                                          `refresh_token` VARCHAR(256) DEFAULT NULL
    ) ENGINE=INNODB DEFAULT CHARSET=utf8 COMMENT='auth token存储表';

/*Table structure for table `oauth_client_details` */

    DROP TABLE IF EXISTS `oauth_client_details`;

    CREATE TABLE `oauth_client_details` (
                                            `client_id` VARCHAR(256) NOT NULL,
                                            `resource_ids` VARCHAR(256) DEFAULT NULL,
                                            `client_secret` VARCHAR(256) DEFAULT NULL,
                                            `scope` VARCHAR(256) DEFAULT NULL,
                                            `authorized_grant_types` VARCHAR(256) DEFAULT NULL,
                                            `web_server_redirect_uri` VARCHAR(256) DEFAULT NULL,
                                            `authorities` VARCHAR(256) DEFAULT NULL,
                                            `access_token_validity` INT(11) DEFAULT NULL,
                                            `refresh_token_validity` INT(11) DEFAULT NULL,
                                            `additional_information` VARCHAR(4096) DEFAULT NULL,
                                            `autoapprove` VARCHAR(256) DEFAULT NULL,
                                            PRIMARY KEY (`client_id`) USING BTREE
    ) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COMMENT='客户端权限配置表';

/*Table structure for table `oauth_refresh_token` */

    DROP TABLE IF EXISTS `oauth_refresh_token`;

    CREATE TABLE `oauth_refresh_token` (
                                           `token_id` VARCHAR(256) DEFAULT NULL,
                                           `token` BLOB,
                                           `authentication` BLOB
    ) ENGINE=INNODB DEFAULT CHARSET=utf8 COMMENT='权限token刷新表';


/*Table structure for table `permission` */

    DROP TABLE IF EXISTS `permission`;

    CREATE TABLE `permission` (
                                  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
                                  `pid` BIGINT(20) NOT NULL DEFAULT '0' COMMENT '父id',
                                  `name` VARCHAR(64) DEFAULT NULL COMMENT '菜单/操作按钮名称',
                                  `permission` VARCHAR(128) DEFAULT NULL COMMENT '权限标识',
                                  `create_user_id` BIGINT(20) DEFAULT NULL COMMENT '创建人id',
                                  `update_user_id` BIGINT(20) DEFAULT NULL COMMENT '修改人id',
                                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                  `deleted` BIT(1) DEFAULT b'0' COMMENT '删除标记 0正常，1已删除',
                                  PRIMARY KEY (`id`)
    ) ENGINE=INNODB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8 COMMENT='权限表';

-- pt_auxiliary_info
    ALTER TABLE `pt_auxiliary_info` MODIFY `origin_user_id` BIGINT(20) NOT NULL COMMENT '资源拥有者id';

/*Table structure for table `pt_group` */

    DROP TABLE IF EXISTS `pt_group`;

    CREATE TABLE `pt_group` (
                                `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
                                `name` VARCHAR(32) DEFAULT NULL COMMENT '用户组名称',
                                `description` VARCHAR(255) DEFAULT NULL COMMENT '备注',
                                `create_user_id` BIGINT(20) DEFAULT NULL COMMENT '创建人id',
                                `update_user_id` BIGINT(20) DEFAULT NULL COMMENT '修改人id',
                                `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                `deleted` BIT(1) DEFAULT b'0' COMMENT '删除标记 0正常，1已删除',
                                PRIMARY KEY (`id`),
                                UNIQUE KEY `group_name_uindex` (`name`)
    ) ENGINE=INNODB DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='用户组表';


-- pt_image
    ALTER TABLE `pt_image` MODIFY `image_tag` VARCHAR(64) NOT NULL COMMENT '镜像版本';
    ALTER TABLE `pt_image` MODIFY `origin_user_id` BIGINT(20) DEFAULT NULL COMMENT '资源拥有者ID';

-- pt_job_param
    ALTER TABLE `pt_job_param` MODIFY `delay_create_time` TIMESTAMP NULL DEFAULT NULL COMMENT '创建时间';
    ALTER TABLE `pt_job_param` MODIFY `delay_delete_time` TIMESTAMP NULL DEFAULT NULL COMMENT '创建时间';

-- pt_measure
    ALTER TABLE `pt_measure` ADD `dataset_id` BIGINT(20) NOT NULL COMMENT '数据集id';
    ALTER TABLE `pt_measure` ADD `dataset_url` VARCHAR(32) DEFAULT NULL COMMENT '数据集url';
    ALTER TABLE `pt_measure` ADD `model_urls` TEXT COMMENT '模型url';
    ALTER TABLE `pt_measure` ADD `measure_status` TINYINT(1) NOT NULL DEFAULT '0' COMMENT '度量文件生成状态，0：生成中，1：生成成功，2：生成失败';
    ALTER TABLE `pt_measure` drop KEY `measure_unidex` ;

-- pt_model_branch
    ALTER TABLE `pt_model_branch` MODIFY `origin_user_id` BIGINT(20) DEFAULT NULL COMMENT '资源用有人ID';

-- pt_model_info
    ALTER TABLE `pt_model_info` ADD  `tags` JSON DEFAULT NULL COMMENT 'tag信息';
    ALTER TABLE `pt_model_info` ADD  `packaged` TINYINT(4) NOT NULL DEFAULT '0' COMMENT '模型是否已经打包，0未打包，1打包完成';
    ALTER TABLE `pt_model_info` modify `model_description` varchar(255) DEFAULT NULL COMMENT '模型描述';



/*Table structure for table `pt_model_suffix` */

    DROP TABLE IF EXISTS `pt_model_suffix`;

    CREATE TABLE `pt_model_suffix` (
                                       `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
                                       `model_type` TINYINT(4) NOT NULL COMMENT '模型文件的格式',
                                       `model_suffix` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '模型文件的格式对应后缀名',
                                       `create_user_id` BIGINT(20) DEFAULT NULL COMMENT '创建用户ID',
                                       `update_user_id` BIGINT(20) DEFAULT NULL COMMENT '更新用户ID',
                                       `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '0 正常，1 已删除',
                                       `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                       `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                                       `origin_user_id` BIGINT(20) DEFAULT NULL COMMENT '资源用有人ID',
                                       PRIMARY KEY (`id`)
    ) ENGINE=INNODB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8 COMMENT='模型后缀名';

/*Table structure for table `pt_model_type` */

    DROP TABLE IF EXISTS `pt_model_type`;

    CREATE TABLE `pt_model_type` (
                                     `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
                                     `frame_type` TINYINT(4) NOT NULL COMMENT '框架类型',
                                     `model_type` VARCHAR(255) NOT NULL COMMENT '模型文件的格式',
                                     `create_user_id` BIGINT(20) DEFAULT NULL COMMENT '创建用户ID',
                                     `update_user_id` BIGINT(20) DEFAULT NULL COMMENT '更新用户ID',
                                     `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '0 正常，1 已删除',
                                     `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                                     `origin_user_id` BIGINT(20) DEFAULT NULL COMMENT '资源用有人ID',
                                     PRIMARY KEY (`id`)
    ) ENGINE=INNODB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8 COMMENT='模型格式';


-- pt_train
    ALTER TABLE `pt_train` MODIFY `origin_user_id` BIGINT(20) DEFAULT NULL COMMENT '资源用有人ID';


-- pt_train_algorithm
    ALTER TABLE `pt_train_algorithm` CHANGE `is_train_out` `is_train_model_out` TINYINT(1) DEFAULT '1' COMMENT '是否输出训练结果:1是，0否';
    ALTER TABLE `pt_train_algorithm` CHANGE `is_train_log` `is_train_out` TINYINT(1) DEFAULT '1' COMMENT '是否输出训练信息:1是，0否';
    ALTER TABLE `pt_train_algorithm` ADD `inference` TINYINT(1) NOT NULL DEFAULT '0' COMMENT '算法文件是否可推理（1可推理，0不可推理）';
    ALTER TABLE `pt_train_algorithm` MODIFY `origin_user_id` BIGINT(20) DEFAULT NULL COMMENT '资源拥有者ID';

-- pt_train_job
    alter table `pt_train_job` change out_path model_path varchar(128) default '' null comment '训练模型输出路径';
    alter table `pt_train_job` change log_path out_path varchar(128) default '' null comment '训练输出路径';
    ALTER TABLE `pt_train_job` MODIFY `val_data_source_name` VARCHAR(127) DEFAULT NULL COMMENT '验证数据集名称';
    ALTER TABLE `pt_train_job` MODIFY `val_data_source_path` VARCHAR(255) DEFAULT NULL COMMENT '验证数据集路径';
    ALTER TABLE `pt_train_job` MODIFY `origin_user_id` BIGINT(20) DEFAULT NULL COMMENT '资源拥有者ID';
    ALTER TABLE `pt_train_job` ADD `status_detail` JSON DEFAULT NULL COMMENT '状态对应的详情信息';

/*Table structure for table `recycle` */

    DROP TABLE IF EXISTS `recycle`;

    CREATE TABLE `recycle` (
                               `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
                               `recycle_module` varchar(32) NOT NULL COMMENT '回收模块',
                               `recycle_delay_date` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '回收日期',
                               `recycle_custom` varchar(64) DEFAULT NULL COMMENT '回收定制化方式',
                               `recycle_status` tinyint(4) DEFAULT '0' COMMENT '回收任务状态(0:待删除，1:已删除，2:删除失败，3：删除中，4：还原中，5：已还原)',
                               `create_user_id` bigint(20) DEFAULT NULL COMMENT '创建人ID',
                               `update_user_id` bigint(20) DEFAULT NULL COMMENT '修改人ID',
                               `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                               `recycle_note` varchar(512) DEFAULT NULL COMMENT '回收说明',
                               `remark` varchar(512) DEFAULT NULL COMMENT '备注',
                               `recycle_response` varchar(512) DEFAULT NULL COMMENT '回收响应信息',
                               `restore_custom` varchar(64) DEFAULT NULL COMMENT '还原定制化方式',
                               `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '删除(0正常，1已删除)',
                               PRIMARY KEY (`id`)
    ) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='垃圾回收任务主表';

/*Table structure for table `recycle_detail` */

    DROP TABLE IF EXISTS `recycle_detail`;

    CREATE TABLE `recycle_detail` (
                                      `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
                                      `recycle_id` bigint(20) NOT NULL COMMENT '垃圾回收任务主表ID',
                                      `recycle_type` tinyint(1) NOT NULL DEFAULT '0' COMMENT '回收类型(0文件，1数据库表数据)',
                                      `recycle_condition` text NOT NULL COMMENT '回收条件(回收表数据sql、回收文件绝对路径)',
                                      `recycle_status` tinyint(4) DEFAULT '0' COMMENT '回收任务状态(0:待删除，1:已删除，2:删除失败，3：删除中)',
                                      `create_user_id` bigint(20) DEFAULT NULL COMMENT '创建人ID',
                                      `update_user_id` bigint(20) DEFAULT NULL COMMENT '修改人ID',
                                      `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                                      `recycle_note` varchar(512) DEFAULT NULL COMMENT '回收说明',
                                      `remark` varchar(512) DEFAULT NULL COMMENT '备注',
                                      `recycle_response` varchar(512) DEFAULT NULL COMMENT '回收响应信息',
                                      `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '删除(0正常，1已删除)',
                                      PRIMARY KEY (`id`),
                                      KEY `recycle_task_main_id` (`recycle_id`)
    ) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='垃圾回收任务详情表';


    -- pt_train_param
-- 修改任务参数模型输出路径、训练输出路径
    alter table pt_train_param change out_path model_path varchar(128) default '' null comment '模型输出路径';
    alter table pt_train_param change log_path out_path varchar(128) default '' null comment '输出路径';

/*Table structure for table `roles_auth` */

    DROP TABLE IF EXISTS `roles_auth`;

    CREATE TABLE `roles_auth` (
                                  `role_id` bigint(20) NOT NULL,
                                  `auth_id` bigint(20) DEFAULT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='角色权限关联表';




/*Table structure for table `resource_specs` */

    DROP TABLE IF EXISTS `resource_specs`;

    CREATE TABLE `resource_specs` (
                                      `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                      `specs_name` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '规格名称',
                                      `resources_pool_type` TINYINT(1) NOT NULL DEFAULT '0' COMMENT '规格类型(0为CPU, 1为GPU)',
                                      `module` INT(11) NOT NULL COMMENT '所属业务场景(0:通用，1：dubhe-notebook，2：dubhe-train，3：dubhe-serving)',
                                      `cpu_num` INT(11) NOT NULL COMMENT 'CPU数量,单位：核',
                                      `gpu_num` INT(11) NOT NULL COMMENT 'GPU数量，单位：核',
                                      `mem_num` INT(11) NOT NULL COMMENT '内存大小，单位：M',
                                      `workspace_request` INT(11) NOT NULL COMMENT '工作空间的存储配额，单位：M',
                                      `create_user_id` BIGINT(20) DEFAULT NULL COMMENT '创建人',
                                      `create_time` TIMESTAMP NULL DEFAULT NULL COMMENT '创建时间',
                                      `update_user_id` BIGINT(20) DEFAULT NULL COMMENT '更新人',
                                      `update_time` TIMESTAMP NULL DEFAULT NULL COMMENT '更新时间',
                                      `deleted` TINYINT(1) NOT NULL DEFAULT '0' COMMENT '删除(0正常，1已删除)',
                                      PRIMARY KEY (`id`)
    ) ENGINE=INNODB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COMMENT='资源规格';


/*Table structure for table `resource_specs` */

    DROP TABLE IF EXISTS `resource_specs`;

    CREATE TABLE `resource_specs` (
                                      `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                      `specs_name` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '规格名称',
                                      `resources_pool_type` TINYINT(1) NOT NULL DEFAULT '0' COMMENT '规格类型(0为CPU, 1为GPU)',
                                      `module` INT(11) NOT NULL COMMENT '所属业务场景(0:通用，1：dubhe-notebook，2：dubhe-train，3：dubhe-serving)',
                                      `cpu_num` INT(11) NOT NULL COMMENT 'CPU数量,单位：核',
                                      `gpu_num` INT(11) NOT NULL COMMENT 'GPU数量，单位：核',
                                      `mem_num` INT(11) NOT NULL COMMENT '内存大小，单位：M',
                                      `workspace_request` INT(11) NOT NULL COMMENT '工作空间的存储配额，单位：M',
                                      `create_user_id` BIGINT(20) DEFAULT NULL COMMENT '创建人',
                                      `create_time` TIMESTAMP NULL DEFAULT NULL COMMENT '创建时间',
                                      `update_user_id` BIGINT(20) DEFAULT NULL COMMENT '更新人',
                                      `update_time` TIMESTAMP NULL DEFAULT NULL COMMENT '更新时间',
                                      `deleted` TINYINT(1) NOT NULL DEFAULT '0' COMMENT '删除(0正常，1已删除)',
                                      PRIMARY KEY (`id`)
    ) ENGINE=INNODB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COMMENT='资源规格';



-- serving_batch
    ALTER TABLE `serving_batch` ADD `model_branch_id` BIGINT(20) DEFAULT NULL COMMENT '模型对应版本id';
    ALTER TABLE `serving_batch` ADD `status_detail` JSON DEFAULT NULL COMMENT '状态对应的详情信息';
    ALTER TABLE `serving_batch` CHANGE `reasoning_script_path` `script_path` VARCHAR(255) DEFAULT NULL COMMENT '推理脚本路径';
    ALTER TABLE `serving_batch` DROP COLUMN `model_config_path`;

    ALTER TABLE `serving_batch` ADD `image` VARCHAR(255) DEFAULT NULL COMMENT '镜像';
    ALTER TABLE `serving_batch` ADD `use_script` BIT(1) DEFAULT b'0' COMMENT '是否使用脚本';
    ALTER TABLE `serving_batch` ADD `algorithm_id` INT(11) DEFAULT NULL COMMENT '算法ID';
    ALTER TABLE `serving_batch` ADD `image_name` VARCHAR(255) DEFAULT NULL COMMENT '镜像名称';
    ALTER TABLE `serving_batch` ADD `image_tag` VARCHAR(255) DEFAULT NULL COMMENT '镜像版本';
    ALTER TABLE `serving_batch` ADD `origin_user_id` BIGINT(20) DEFAULT NULL COMMENT '资源用有人ID';

-- serving_info
    ALTER TABLE `serving_info` ADD `status_detail` JSON DEFAULT NULL COMMENT '状态对应的详情信息';
    ALTER TABLE `serving_info` ADD `origin_user_id` BIGINT(20) DEFAULT NULL COMMENT '资源用有人ID';


-- serving_model_config
    ALTER TABLE `serving_model_config` ADD `model_branch_id` BIGINT(20) DEFAULT NULL COMMENT '模型对应版本id';
    ALTER TABLE `serving_model_config` MODIFY `deploy_params` JSON DEFAULT NULL COMMENT '部署参数';
    ALTER TABLE `serving_model_config` ADD `image` VARCHAR(255) DEFAULT NULL COMMENT '镜像';
    ALTER TABLE `serving_model_config` ADD `use_script` BIT(1) DEFAULT b'0' COMMENT '是否使用脚本';
    ALTER TABLE `serving_model_config` ADD `script_path` varchar(255) DEFAULT NULL COMMENT '推理脚本路径';
    ALTER TABLE `serving_model_config` ADD `algorithm_id` INT(11) DEFAULT NULL COMMENT '算法ID';
    ALTER TABLE `serving_model_config` ADD `image_name` VARCHAR(255) DEFAULT NULL COMMENT '镜像名称';
    ALTER TABLE `serving_model_config` ADD `image_tag` VARCHAR(255) DEFAULT NULL COMMENT '镜像版本';


/*Table structure for table `user_group` */

    DROP TABLE IF EXISTS `user_group`;

    CREATE TABLE `user_group` (
                                  `group_id` BIGINT(20) NOT NULL COMMENT '用户组id',
                                  `user_id` BIGINT(20) NOT NULL COMMENT '用户id',
                                  UNIQUE KEY `group_user_user_id` (`user_id`),
                                  KEY `group_user_group_id` (`group_id`)
    ) ENGINE=INNODB DEFAULT CHARSET=utf8 COMMENT='用户组-用户关联表';

-- menu
    ALTER TABLE `menu` ADD `back_to` VARCHAR(255) DEFAULT NULL COMMENT '上级菜单';
    ALTER TABLE `menu` ADD `ext_config` VARCHAR(255) DEFAULT NULL COMMENT '扩展配置';


    -- permission
-- 控制台
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) VALUES (0, '控制台', 1, 1);
-- 控制台-用户管理操作权限初始化
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) select id, '用户管理', 1, 1 from permission where name='控制台';
    select @pid := @@IDENTITY;
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '创建用户', 'system:user:create', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '编辑用户', 'system:user:edit', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '删除用户', 'system:user:delete', 1, 1);
-- 控制台-用户组管理操作权限初始化
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) select id, '用户组管理', 1, 1 from permission where name='控制台';
    select @pid := @@IDENTITY;
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '创建用户组', 'system:userGroup:create', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '编辑用户组', 'system:userGroup:edit', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '删除用户组', 'system:userGroup:delete', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '编辑成员', 'system:userGroup:editUser', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '批量修改角色', 'system:userGroup:editUserRole', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '批量激活锁定', 'system:userGroup:editUserState', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '批量删除用户', 'system:userGroup:deleteUser', 1, 1);
-- 控制台-权限管理操作权限初始化
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) select id, '权限管理', 1, 1 from permission where name='控制台';
    select @pid := @@IDENTITY;
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '创建权限组', 'system:authCode:create', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '编辑权限组', 'system:authCode:edit', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '删除权限`组', 'system:authCode:delete', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '创建权限', 'system:permission:create', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '编辑权限', 'system:permission:edit', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '删除权限', 'system:permission:delete', 1, 1);
-- 控制台-角色管理操作权限初始化
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) select id, '角色管理', 1, 1 from permission where name='控制台';
    select @pid := @@IDENTITY;
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '创建角色', 'system:role:create', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '编辑角色', 'system:role:edit', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '删除角色', 'system:role:delete', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '权限分配', 'system:role:auth', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '菜单分配', 'system:role:menu', 1, 1);
-- 控制台-菜单管理操作权限初始化
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) select id, '菜单管理', 1, 1 from permission where name='控制台';
    select @pid := @@IDENTITY;
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '删除菜单', 'system:menu:create', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '编辑菜单', 'system:menu:edit', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '创建菜单', 'system:menu:delete', 1, 1);
-- 控制台-字典管理操作权限初始化
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) select id, '字典管理', 1, 1 from permission where name='控制台';
    select @pid := @@IDENTITY;
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '创建字典', 'system:dict:create', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '编辑字典', 'system:dict:edit', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '删除字典', 'system:dict:delete', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '字典详情-创建', 'system:dictDetail:create', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '字典详情-修改', 'system:dictDetail:edit', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '字典详情-删除', 'system:dictDetail:delete', 1, 1);
-- 控制台-资源规格管理操作权限初始化
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) select id, '资源规格管理', 1, 1 from permission where name='控制台';
    select @pid := @@IDENTITY;
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '创建资源规格', 'system:specs:create', 1, 1 );
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '修改资源规格', 'system:specs:edit', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '删除资源规格', 'system:specs:delete', 1, 1);

-- 算法开发
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) VALUES (0, '算法开发', 1, 1);
-- 算法开发-算法管理操作权限初始化
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) select id, '算法管理', 1, 1 from permission where name='算法开发';
    select @pid := @@IDENTITY;
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '创建算法', 'development:algorithm:create', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '修改算法', 'development:algorithm:edit', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '删除算法', 'development:algorithm:delete', 1, 1);
-- 算法开发-notebook操作权限初始化
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) select id, 'notebook', 1, 1 from permission where name='算法开发';
    select @pid := @@IDENTITY;
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '创建notebook', 'notebook:create', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '修改notebook', 'notebook:update', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '打开notebook', 'notebook:open', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '启动notebook', 'notebook:start', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '停止notebook', 'notebook:stop', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '删除notebook', 'notebook:delete', 1, 1);

-- 模型管理
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) VALUES (0, '模型管理', 1, 1);
-- 模型管理-模型列表操作权限初始化
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) select id, '模型列表', 1, 1 from permission where name='模型管理';
    select @pid := @@IDENTITY;
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '模型列表-创建', 'model:model:create', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '模型列表-修改', 'model:model:edit', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '模型列表-删除', 'model:model:delete', 1, 1);
-- 模型管理-模型优化操作权限初始化
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) select id, '模型优化', 1, 1 from permission where name='模型管理';
    select @pid := @@IDENTITY;
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '创建任务', 'model:optimize:createTask', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '提交任务', 'model:optimize:submitTask', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '修改任务', 'model:optimize:editTask', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '删除任务', 'model:optimize:deleteTask', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '提交任务实例', 'model:optimize:submitTaskInstance', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '取消任务实例实例', 'model:optimize:cancelTaskInstance', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '删除任务实例', 'model:optimize:deleteTaskInstance', 1, 1);
-- 模型管理-模型列表历史版本-模型版本管理操作权限初始化
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) select id, '模型版本管理', 1, 1 from permission where name='模型管理';
    select @pid := @@IDENTITY;
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '模型版本管理-创建', 'model:branch:create', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '模型版本管理-删除', 'model:branch:delete', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '模型版本管理-转预置', 'model:branch:convertPreset', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '模型版本管理-转onnx', 'model:branch:convertOnnx', 1, 1);

-- 训练管理
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) VALUES (0, '训练管理', 1, 1);
-- 训练管理-镜像管理操作权限初始化
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) select id, '镜像管理', 1, 1 from permission where name='训练管理';
    select @pid := @@IDENTITY;
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '上传镜像', 'training:image:upload', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '修改镜像', 'training:image:edit', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '删除镜像', 'training:image:delete', 1, 1);
-- 训练管理-训练任务操作权限初始化
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) select id, '训练任务', 1, 1 from permission where name='训练管理';
    select @pid := @@IDENTITY;
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '创建训练', 'training:job:create', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '修改训练', 'training:job:update', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '删除训练', 'training:job:delete', 1, 1);

-- 云端serving
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) VALUES (0, '云端serving', 1, 1);
-- 云端serving-在线服务操作权限初始化
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) select id, '在线服务', 1, 1 from permission where name='云端serving';
    select @pid := @@IDENTITY;
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '创建服务', 'serving:online:create', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '修改服务', 'serving:online:edit', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '删除服务', 'serving:online:delete', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '启动服务', 'serving:online:start', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '停止服务', 'serving:online:stop', 1, 1);
-- 云端serving-批量服务操作权限初始化
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) select id, '批量服务', 1, 1 from permission where name='云端serving';
    select @pid := @@IDENTITY;
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '创建批量任务', 'serving:batch:create', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '修改批量服务', 'serving:batch:edit', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '删除批量服务', 'serving:batch:delete', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '启动批量服务', 'serving:batch:start', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '停止批量服务', 'serving:batch:stop', 1, 1);

-- 模型炼知
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) VALUES (0, '模型炼知', 1, 1);
-- 模型炼知-度量管理操作权限初始化
    insert into `permission` (`pid`, `name`, `create_user_id`, `update_user_id`) select id, '度量管理', 1, 1 from permission where name='模型炼知';
    select @pid := @@IDENTITY;
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '创建度量', 'atlas:measure:create', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '编辑度量', 'atlas:measure:edit', 1, 1);
    insert into `permission` (`pid`, `name`, `permission`, `create_user_id`, `update_user_id`) values (@pid, '删除度量', 'atlas:measure:delete', 1, 1);

-- 管理员角色操作权限初始化
    insert into auth(id, auth_code, description, create_user_id, update_user_id) values (1, 'admin权限组', '默认全部操作权限', 1, 1);
    insert into auth_permission (auth_id, permission_id) select 1, id from permission;
    INSERT INTO `roles_auth` (role_id, auth_id) values (1, 1);

-- 修改模型格式表pt_model_type
    update pt_model_type set model_type='1,17'  where frame_type=1;
    update pt_model_type set model_type='1,13'  where frame_type=4;


    -- -- data_label
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1, '行人', '#ffbb96', 0, '2020-04-27 12:34:37', 0, '2020-07-08 16:30:14', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (2, '自行车', '#fcffe6', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (3, '汽车', '#f4ffb8', 0, '2020-04-20 07:00:00', 0, '2020-07-08 16:30:14', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (4, '摩托车', '#254000', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (5, '飞机', '#e6f7ff', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (6, '巴士', '#bae7ff', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (7, '火车', '#003a8c', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (8, '货车', '#002766', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (9, '轮船', '#d6e4ff', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (10, '交通灯', '#d3adf7', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (11, '消防栓', '#b37feb', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (12, '停止标志', '#9254de', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (13, '停车收费表', '#722ed1', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (14, '长凳', '#531dab', 0, '2020-04-20 07:00:00', 0, '2020-07-08 16:30:14', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (15, '鸟', '#f6ffed', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (16, '猫', '#d9f7be', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (17, '狗', '#b7eb8f', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (18, '马', '#95de64', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (19, '羊', '#73d13d', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (20, '牛', '#52c41a', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (21, '大象', '#389e0d', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (22, '熊', '#237804', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (23, '斑马', '#135200', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (24, '长颈鹿', '#092b00', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (25, '背包', '#ffadd2', 0, '2020-04-20 07:00:00', 0, '2020-07-08 16:30:14', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (26, '雨伞', '#ff85c0', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (27, '手提包', '#f759ab', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (28, '领带', '#eb2f96', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (29, '手提箱', '#c41d7f', 0, '2020-04-20 07:00:00', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (30, '飞盘', '#e6fffb', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (31, '雪板', '#b5f5ec', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (32, '滑雪板', '#87e8de', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (33, '球', '#5cdbd3', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (34, '风筝', '#36cfc9', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (35, '棒球棒', '#13c2c2', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (36, '棒球手套', '#08979c', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (37, '滑板', '#006d75', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (38, '冲浪板', '#00474f', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (39, '网球拍', '#002329', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (40, '瓶子', '#fffb8f', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (41, '红酒杯', '#fff566', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (42, '杯子', '#ffec3d', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (43, '叉子', '#fadb14', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (44, '刀', '#d4b106', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (45, '勺子', '#ad8b00', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (46, '碗', '#876800', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (47, '香蕉', '#fff7e6', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (48, '苹果', '#ffe7ba', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (49, '三明治', '#ffd591', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (50, '橙子', '#ffc069', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (51, '西兰花', '#ffa940', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (52, '胡萝卜', '#fa8c16', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (53, '热狗', '#d46b08', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (54, '披萨', '#ad4e00', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (55, '甜甜圈', '#873800', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (56, '蛋糕', '#612500', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (57, '椅子', '#ffe58f', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (58, '长椅', '#ffd666', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (59, '盆栽', '#ffc53d', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (60, '床', '#faad14', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (61, '餐桌', '#d48806', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (62, '厕所', '#ad6800', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (63, '电视', '#91d5ff', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (64, '笔记本电脑', '#69c0ff', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (65, '鼠标', '#40a9ff', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (66, '路由器', '#1890ff', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (67, '键盘', '#096dd9', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (68, '手机', '#0050b3', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (69, '微波炉', '#adc6ff', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (70, '烤箱', '#85a5ff', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (71, '烤面包机', '#597ef7', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (72, '水槽', '#2f54eb', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (73, '冰箱', '#1d39c4', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (74, '书籍', '#eaff8f', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (75, '钟表', '#d3f261', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (76, '花瓶', '#bae637', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (77, '剪刀', '#a0d911', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (78, '泰迪熊', '#7cb305', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (79, '吹风机', '#5b8c00', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (80, '牙刷', '#3f6600', 0, '2020-04-20 07:00:01', 0, '2020-07-09 10:13:44', b'0', 1);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (81, '行人', '#ffbb96', 0, '2020-04-27 12:34:37', null, '2020-07-01 12:49:14', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (82, '自行车', '#fcffe6', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:15', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (83, '汽车', '#f4ffb8', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:17', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (84, '摩托车', '#254000', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (85, '飞机', '#e6f7ff', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (86, '巴士', '#bae7ff', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (87, '火车', '#003a8c', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (88, '货车', '#002766', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (89, '轮船', '#d6e4ff', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (90, '交通灯', '#d3adf7', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (91, '消防栓', '#b37feb', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (92, '停止标志', '#9254de', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (93, '停车收费表', '#722ed1', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (94, '长凳', '#531dab', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (95, '鸟', '#f6ffed', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (96, '猫', '#d9f7be', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (97, '狗', '#b7eb8f', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (98, '马', '#95de64', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (99, '羊', '#73d13d', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (100, '牛', '#52c41a', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (101, '大象', '#389e0d', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (102, '熊', '#237804', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (103, '斑马', '#135200', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (104, '长颈鹿', '#092b00', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (105, '背包', '#ffadd2', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (106, '雨伞', '#ff85c0', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (107, '手提包', '#f759ab', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (108, '领带', '#eb2f96', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (109, '手提箱', '#c41d7f', 0, '2020-04-20 07:00:00', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (110, '飞盘', '#e6fffb', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (111, '雪板', '#b5f5ec', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (112, '滑雪板', '#87e8de', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (113, '球', '#5cdbd3', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (114, '风筝', '#36cfc9', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (115, '棒球棒', '#13c2c2', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (116, '棒球手套', '#08979c', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (117, '滑板', '#006d75', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (118, '冲浪板', '#00474f', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (119, '网球拍', '#002329', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (120, '瓶子', '#fffb8f', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (121, '红酒杯', '#fff566', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (122, '杯子', '#ffec3d', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (123, '叉子', '#fadb14', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (124, '刀', '#d4b106', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (125, '勺子', '#ad8b00', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (126, '碗', '#876800', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (127, '香蕉', '#fff7e6', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (128, '苹果', '#ffe7ba', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (129, '三明治', '#ffd591', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (130, '橙子', '#ffc069', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (131, '西兰花', '#ffa940', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (132, '胡萝卜', '#fa8c16', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (133, '热狗', '#d46b08', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (134, '披萨', '#ad4e00', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (135, '甜甜圈', '#873800', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (136, '蛋糕', '#612500', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (137, '椅子', '#ffe58f', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (138, '长椅', '#ffd666', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (139, '盆栽', '#ffc53d', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (140, '床', '#faad14', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (141, '餐桌', '#d48806', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (142, '厕所', '#ad6800', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (143, '电视', '#91d5ff', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (144, '笔记本电脑', '#69c0ff', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (145, '鼠标', '#40a9ff', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (146, '路由器', '#1890ff', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (147, '键盘', '#096dd9', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (148, '手机', '#0050b3', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (149, '微波炉', '#adc6ff', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (150, '烤箱', '#85a5ff', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (151, '烤面包机', '#597ef7', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (152, '水槽', '#2f54eb', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (153, '冰箱', '#1d39c4', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (154, '书籍', '#eaff8f', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (155, '钟表', '#d3f261', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (156, '花瓶', '#bae637', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (157, '剪刀', '#a0d911', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (158, '泰迪熊', '#7cb305', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (159, '吹风机', '#5b8c00', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (160, '牙刷', '#3f6600', 0, '2020-04-20 07:00:01', null, '2020-07-01 12:49:37', b'0', 3);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (161, 'tench Tinca tinca', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (162, 'goldfish Carassius auratus', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (163, 'great white shark white shark man-eater man-eating shark Carcharodon carcharias', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (164, 'tiger shark Galeocerdo cuvieri', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (165, 'hammerhead hammerhead shark', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (166, 'electric ray crampfish numbfish torpedo', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (167, 'stingray', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (168, 'cock', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (169, 'hen', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (170, 'ostrich Struthio camelus', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (171, 'brambling Fringilla montifringilla', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (172, 'goldfinch Carduelis carduelis', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (173, 'house finch linnet Carpodacus mexicanus', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (174, 'junco snowbird', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (175, 'indigo bunting indigo finch indigo bird Passerina cyanea', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (176, 'robin American robin Turdus migratorius', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (177, 'bulbul', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (178, 'jay', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (179, 'magpie', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (180, 'chickadee', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (181, 'water ouzel dipper', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (182, 'kite', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (183, 'bald eagle American eagle Haliaeetus leucocephalus', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (184, 'vulture', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (185, 'great grey owl great gray owl Strix nebulosa', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (186, 'European fire salamander Salamandra salamandra', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (187, 'common newt Triturus vulgaris', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (188, 'eft', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (189, 'spotted salamander Ambystoma maculatum', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (190, 'axolotl mud puppy Ambystoma mexicanum', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (191, 'bullfrog Rana catesbeiana', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (192, 'tree frog tree-frog', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (193, 'tailed frog bell toad ribbed toad tailed toad Ascaphus trui', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (194, 'loggerhead loggerhead turtle Caretta caretta', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (195, 'leatherback turtle leatherback leathery turtle Dermochelys coriacea', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (196, 'mud turtle', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (197, 'terrapin', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (198, 'box turtle box tortoise', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (199, 'banded gecko', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (200, 'common iguana iguana Iguana iguana', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (201, 'American chameleon anole Anolis carolinensis', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (202, 'whiptail whiptail lizard', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (203, 'agama', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (204, 'frilled lizard Chlamydosaurus kingi', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (205, 'alligator lizard', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (206, 'Gila monster Heloderma suspectum', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (207, 'green lizard Lacerta viridis', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (208, 'African chameleon Chamaeleo chamaeleon', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (209, 'Komodo dragon Komodo lizard dragon lizard giant lizard Varanus komodoensis', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (210, 'African crocodile Nile crocodile Crocodylus niloticus', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (211, 'American alligator Alligator mississipiensis', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (212, 'triceratops', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (213, 'thunder snake worm snake Carphophis amoenus', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (214, 'ringneck snake ring-necked snake ring snake', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (215, 'hognose snake puff adder sand viper', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (216, 'green snake grass snake', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (217, 'king snake kingsnake', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (218, 'garter snake grass snake', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (219, 'water snake', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (220, 'vine snake', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (221, 'night snake Hypsiglena torquata', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (222, 'boa constrictor Constrictor constrictor', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (223, 'rock python rock snake Python sebae', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (224, 'Indian cobra Naja naja', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (225, 'green mamba', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (226, 'sea snake', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (227, 'horned viper cerastes sand viper horned asp Cerastes cornutus', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (228, 'diamondback diamondback rattlesnake Crotalus adamanteus', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (229, 'sidewinder horned rattlesnake Crotalus cerastes', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (230, 'trilobite', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (231, 'harvestman daddy longlegs Phalangium opilio', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (232, 'scorpion', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (233, 'black and gold garden spider Argiope aurantia', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (234, 'barn spider Araneus cavaticus', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (235, 'garden spider Aranea diademata', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (236, 'black widow Latrodectus mactans', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (237, 'tarantula', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (238, 'wolf spider hunting spider', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (239, 'tick', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (240, 'centipede', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (241, 'black grouse', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (242, 'ptarmigan', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (243, 'ruffed grouse partridge Bonasa umbellus', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (244, 'prairie chicken prairie grouse prairie fowl', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (245, 'peacock', '#000000', null, '2020-07-01 17:22:05', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (246, 'quail', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (247, 'partridge', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (248, 'African grey African gray Psittacus erithacus', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (249, 'macaw', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (250, 'sulphur-crested cockatoo Kakatoe galerita Cacatua galerita', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (251, 'lorikeet', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (252, 'coucal', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (253, 'bee eater', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (254, 'hornbill', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (255, 'hummingbird', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (256, 'jacamar', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (257, 'toucan', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (258, 'drake', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (259, 'red-breasted merganser Mergus serrator', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (260, 'goose', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (261, 'black swan Cygnus atratus', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (262, 'tusker', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (263, 'echidna spiny anteater anteater', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (264, 'platypus duckbill duckbilled platypus duck-billed platypus Ornithorhynchus anatinus', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (265, 'wallaby brush kangaroo', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (266, 'koala koala bear kangaroo bear native bear Phascolarctos cinereus', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (267, 'wombat', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (268, 'jellyfish', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (269, 'sea anemone anemone', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (270, 'brain coral', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (271, 'flatworm platyhelminth', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (272, 'nematode nematode worm roundworm', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (273, 'conch', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (274, 'snail', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (275, 'slug', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (276, 'sea slug nudibranch', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (277, 'chiton coat-of-mail shell sea cradle polyplacophore', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (278, 'chambered nautilus pearly nautilus nautilus', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (279, 'Dungeness crab Cancer magister', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (280, 'rock crab Cancer irroratus', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (281, 'fiddler crab', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (282, 'king crab Alaska crab Alaskan king crab Alaska king crab Paralithodes camtschatica', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:14', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (283, 'American lobster Northern lobster Maine lobster Homarus americanus', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (284, 'spiny lobster langouste rock lobster crawfish crayfish sea crawfish', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (285, 'crayfish crawfish crawdad crawdaddy', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (286, 'hermit crab', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (287, 'isopod', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (288, 'white stork Ciconia ciconia', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (289, 'black stork Ciconia nigra', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (290, 'spoonbill', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (291, 'flamingo', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (292, 'little blue heron Egretta caerulea', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (293, 'American egret great white heron Egretta albus', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (294, 'bittern', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (295, 'crane', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (296, 'limpkin Aramus pictus', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (297, 'European gallinule Porphyrio porphyrio', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (298, 'American coot marsh hen mud hen water hen Fulica americana', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (299, 'bustard', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (300, 'ruddy turnstone Arenaria interpres', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (301, 'red-backed sandpiper dunlin Erolia alpina', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (302, 'redshank Tringa totanus', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (303, 'dowitcher', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (304, 'oystercatcher oyster catcher', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (305, 'pelican', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (306, 'king penguin Aptenodytes patagonica', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (307, 'albatross mollymawk', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (308, 'grey whale gray whale devilfish Eschrichtius gibbosus Eschrichtius robustus', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (309, 'killer whale killer orca grampus sea wolf Orcinus orca', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (310, 'dugong Dugong dugon', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (311, 'sea lion', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (312, 'Chihuahua', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (313, 'Japanese spaniel', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (314, 'Maltese dog Maltese terrier Maltese', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (315, 'Pekinese Pekingese Peke', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (316, 'Shih-Tzu', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (317, 'Blenheim spaniel', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (318, 'papillon', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (319, 'toy terrier', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (320, 'Rhodesian ridgeback', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (321, 'Afghan hound Afghan', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (322, 'basset basset hound', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (323, 'beagle', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (324, 'bloodhound sleuthhound', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (325, 'bluetick', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (326, 'black-and-tan coonhound', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (327, 'Walker hound Walker foxhound', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (328, 'English foxhound', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (329, 'redbone', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (330, 'borzoi Russian wolfhound', '#000000', null, '2020-07-01 17:22:06', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (331, 'Irish wolfhound', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (332, 'Italian greyhound', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (333, 'whippet', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (334, 'Ibizan hound Ibizan Podenco', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (335, 'Norwegian elkhound elkhound', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (336, 'otterhound otter hound', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (337, 'Saluki gazelle hound', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (338, 'Scottish deerhound deerhound', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (339, 'Weimaraner', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (340, 'Staffordshire bullterrier Staffordshire bull terrier', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (341, 'American Staffordshire terrier Staffordshire terrier American pit bull terrier pit bull terrier', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (342, 'Bedlington terrier', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (343, 'Border terrier', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (344, 'Kerry blue terrier', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (345, 'Irish terrier', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (346, 'Norfolk terrier', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (347, 'Norwich terrier', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (348, 'Yorkshire terrier', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (349, 'wire-haired fox terrier', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (350, 'Lakeland terrier', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (351, 'Sealyham terrier Sealyham', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (352, 'Airedale Airedale terrier', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (353, 'cairn cairn terrier', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (354, 'Australian terrier', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (355, 'Dandie Dinmont Dandie Dinmont terrier', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (356, 'Boston bull Boston terrier', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (357, 'miniature schnauzer', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (358, 'giant schnauzer', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (359, 'standard schnauzer', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (360, 'Scotch terrier Scottish terrier Scottie', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (361, 'Tibetan terrier chrysanthemum dog', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (362, 'silky terrier Sydney silky', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (363, 'soft-coated wheaten terrier', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (364, 'West Highland white terrier', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (365, 'Lhasa Lhasa apso', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (366, 'flat-coated retriever', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (367, 'curly-coated retriever', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (368, 'golden retriever', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (369, 'Labrador retriever', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (370, 'Chesapeake Bay retriever', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (371, 'German short-haired pointer', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (372, 'vizsla Hungarian pointer', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (373, 'English setter', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (374, 'Irish setter red setter', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (375, 'Gordon setter', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (376, 'Brittany spaniel', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (377, 'clumber clumber spaniel', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (378, 'English springer English springer spaniel', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (379, 'Welsh springer spaniel', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (380, 'cocker spaniel English cocker spaniel cocker', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (381, 'Sussex spaniel', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (382, 'Irish water spaniel', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (383, 'kuvasz', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (384, 'schipperke', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (385, 'groenendael', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (386, 'malinois', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (387, 'briard', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (388, 'kelpie', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (389, 'komondor', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (390, 'Old English sheepdog bobtail', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (391, 'Shetland sheepdog Shetland sheep dog Shetland', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (392, 'collie', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (393, 'Border collie', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (394, 'Bouvier des Flandres Bouviers des Flandres', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (395, 'Rottweiler', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (396, 'German shepherd German shepherd dog German police dog alsatian', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (397, 'Doberman Doberman pinscher', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (398, 'miniature pinscher', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (399, 'Greater Swiss Mountain dog', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (400, 'Bernese mountain dog', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (401, 'Appenzeller', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (402, 'EntleBucher', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (403, 'boxer', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (404, 'bull mastiff', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (405, 'Tibetan mastiff', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (406, 'French bulldog', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (407, 'Great Dane', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (408, 'Saint Bernard St Bernard', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (409, 'Eskimo dog husky', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (410, 'malamute malemute Alaskan malamute', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (411, 'Siberian husky', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (412, 'dalmatian coach dog carriage dog', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (413, 'affenpinscher monkey pinscher monkey dog', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (414, 'basenji', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (415, 'pug pug-dog', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (416, 'Leonberg', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (417, 'Newfoundland Newfoundland dog', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (418, 'Great Pyrenees', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (419, 'Samoyed Samoyede', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (420, 'Pomeranian', '#000000', null, '2020-07-01 17:22:07', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (421, 'chow chow chow', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (422, 'keeshond', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (423, 'Brabancon griffon', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (424, 'Pembroke Pembroke Welsh corgi', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (425, 'Cardigan Cardigan Welsh corgi', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (426, 'toy poodle', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (427, 'miniature poodle', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (428, 'standard poodle', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (429, 'Mexican hairless', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (430, 'timber wolf grey wolf gray wolf Canis lupus', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (431, 'white wolf Arctic wolf Canis lupus tundrarum', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (432, 'red wolf maned wolf Canis rufus Canis niger', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (433, 'coyote prairie wolf brush wolf Canis latrans', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (434, 'dingo warrigal warragal Canis dingo', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (435, 'dhole Cuon alpinus', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (436, 'African hunting dog hyena dog Cape hunting dog Lycaon pictus', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (437, 'hyena hyaena', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (438, 'red fox Vulpes vulpes', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (439, 'kit fox Vulpes macrotis', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (440, 'Arctic fox white fox Alopex lagopus', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (441, 'grey fox gray fox Urocyon cinereoargenteus', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (442, 'tabby tabby cat', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (443, 'tiger cat', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (444, 'Persian cat', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (445, 'Siamese cat Siamese', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:15', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (446, 'Egyptian cat', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (447, 'cougar puma catamount mountain lion painter panther Felis concolor', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (448, 'lynx catamount', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (449, 'leopard Panthera pardus', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (450, 'snow leopard ounce Panthera uncia', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (451, 'jaguar panther Panthera onca Felis onca', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (452, 'lion king of beasts Panthera leo', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (453, 'tiger Panthera tigris', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (454, 'cheetah chetah Acinonyx jubatus', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (455, 'brown bear bruin Ursus arctos', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (456, 'American black bear black bear Ursus americanus Euarctos americanus', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (457, 'ice bear polar bear Ursus Maritimus Thalarctos maritimus', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (458, 'sloth bear Melursus ursinus Ursus ursinus', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (459, 'mongoose', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (460, 'meerkat mierkat', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (461, 'tiger beetle', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (462, 'ladybug ladybeetle lady beetle ladybird ladybird beetle', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (463, 'ground beetle carabid beetle', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (464, 'long-horned beetle longicorn longicorn beetle', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (465, 'leaf beetle chrysomelid', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (466, 'dung beetle', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (467, 'rhinoceros beetle', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (468, 'weevil', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (469, 'fly', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (470, 'bee', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (471, 'ant emmet pismire', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (472, 'grasshopper hopper', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (473, 'cricket', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (474, 'walking stick walkingstick stick insect', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (475, 'cockroach roach', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (476, 'mantis mantid', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (477, 'cicada cicala', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (478, 'leafhopper', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (479, 'lacewing lacewing fly', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (480, 'dragonfly darning needle devil''s darning needle sewing needle snake feeder snake doctor mosquito hawk skeeter hawk', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (481, 'damselfly', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (482, 'admiral', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (483, 'ringlet ringlet butterfly', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (484, 'monarch monarch butterfly milkweed butterfly Danaus plexippus', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (485, 'cabbage butterfly', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (486, 'sulphur butterfly sulfur butterfly', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (487, 'lycaenid lycaenid butterfly', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (488, 'starfish sea star', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (489, 'sea urchin', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (490, 'sea cucumber holothurian', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (491, 'wood rabbit cottontail cottontail rabbit', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (492, 'hare', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (493, 'Angora Angora rabbit', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (494, 'hamster', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (495, 'porcupine hedgehog', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (496, 'fox squirrel eastern fox squirrel Sciurus niger', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (497, 'marmot', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (498, 'beaver', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (499, 'guinea pig Cavia cobaya', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (500, 'sorrel', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (501, 'zebra', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (502, 'hog pig grunter squealer Sus scrofa', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (503, 'wild boar boar Sus scrofa', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (504, 'warthog', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (505, 'hippopotamus hippo river horse Hippopotamus amphibius', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (506, 'ox', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (507, 'water buffalo water ox Asiatic buffalo Bubalus bubalis', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (508, 'bison', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (509, 'ram tup', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (510, 'bighorn bighorn sheep cimarron Rocky Mountain bighorn Rocky Mountain sheep Ovis canadensis', '#000000', null, '2020-07-01 17:22:08', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (511, 'ibex Capra ibex', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (512, 'hartebeest', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (513, 'impala Aepyceros melampus', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (514, 'gazelle', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (515, 'Arabian camel dromedary Camelus dromedarius', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (516, 'llama', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (517, 'weasel', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (518, 'mink', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (519, 'polecat fitch foulmart foumart Mustela putorius', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (520, 'black-footed ferret ferret Mustela nigripes', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (521, 'otter', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (522, 'skunk polecat wood pussy', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (523, 'badger', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (524, 'armadillo', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (525, 'three-toed sloth ai Bradypus tridactylus', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (526, 'orangutan orang orangutang Pongo pygmaeus', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (527, 'gorilla Gorilla gorilla', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (528, 'chimpanzee chimp Pan troglodytes', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (529, 'gibbon Hylobates lar', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (530, 'siamang Hylobates syndactylus Symphalangus syndactylus', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (531, 'guenon guenon monkey', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (532, 'patas hussar monkey Erythrocebus patas', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (533, 'baboon', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (534, 'macaque', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (535, 'langur', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (536, 'colobus colobus monkey', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (537, 'proboscis monkey Nasalis larvatus', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (538, 'marmoset', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (539, 'capuchin ringtail Cebus capucinus', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (540, 'howler monkey howler', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (541, 'titi titi monkey', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (542, 'spider monkey Ateles geoffroyi', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (543, 'squirrel monkey Saimiri sciureus', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (544, 'Madagascar cat ring-tailed lemur Lemur catta', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (545, 'indri indris Indri indri Indri brevicaudatus', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (546, 'Indian elephant Elephas maximus', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (547, 'African elephant Loxodonta africana', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (548, 'lesser panda red panda panda bear cat cat bear Ailurus fulgens', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (549, 'giant panda panda panda bear coon bear Ailuropoda melanoleuca', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (550, 'barracouta snoek', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (551, 'eel', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (552, 'coho cohoe coho salmon blue jack silver salmon Oncorhynchus kisutch', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (553, 'rock beauty Holocanthus tricolor', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (554, 'anemone fish', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (555, 'sturgeon', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (556, 'gar garfish garpike billfish Lepisosteus osseus', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (557, 'lionfish', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (558, 'puffer pufferfish blowfish globefish', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (559, 'abacus', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (560, 'abaya', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (561, 'academic gown academic robe judge''s robe', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (562, 'accordion piano accordion squeeze box', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (563, 'acoustic guitar', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (564, 'aircraft carrier carrier flattop attack aircraft carrier', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (565, 'airliner', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (566, 'airship dirigible', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (567, 'altar', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (568, 'ambulance', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (569, 'amphibian amphibious vehicle', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (570, 'analog clock', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (571, 'apiary bee house', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (572, 'apron', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (573, 'ashcan trash can garbage can wastebin ash bin ash-bin ashbin dustbin trash barrel trash bin', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (574, 'assault rifle assault gun', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (575, 'backpack back pack knapsack packsack rucksack haversack', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (576, 'bakery bakeshop bakehouse', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (577, 'balance beam beam', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (578, 'balloon', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (579, 'ballpoint ballpoint pen ballpen Biro', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (580, 'Band Aid', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (581, 'banjo', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (582, 'bannister banister balustrade balusters handrail', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (583, 'barbell', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (584, 'barber chair', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (585, 'barbershop', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (586, 'barn', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (587, 'barometer', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (588, 'barrel cask', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (589, 'barrow garden cart lawn cart wheelbarrow', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (590, 'baseball', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (591, 'basketball', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (592, 'bassinet', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (593, 'bassoon', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (594, 'bathing cap swimming cap', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (595, 'bath towel', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (596, 'bathtub bathing tub bath tub', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (597, 'beach wagon station wagon wagon estate car beach waggon station waggon waggon', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (598, 'beacon lighthouse beacon light pharos', '#000000', null, '2020-07-01 17:22:09', null, '2020-07-01 18:55:16', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (599, 'beaker', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (600, 'bearskin busby shako', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (601, 'beer bottle', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (602, 'beer glass', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (603, 'bell cote bell cot', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (604, 'bib', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (605, 'bicycle-built-for-two tandem bicycle tandem', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (606, 'bikini two-piece', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (607, 'binder ring-binder', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (608, 'binoculars field glasses opera glasses', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (609, 'birdhouse', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (610, 'boathouse', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (611, 'bobsled bobsleigh bob', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (612, 'bolo tie bolo bola tie bola', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (613, 'bonnet poke bonnet', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (614, 'bookcase', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (615, 'bookshop bookstore bookstall', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (616, 'bottlecap', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (617, 'bow', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (618, 'bow tie bow-tie bowtie', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (619, 'brass memorial tablet plaque', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (620, 'brassiere bra bandeau', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (621, 'breakwater groin groyne mole bulwark seawall jetty', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (622, 'breastplate aegis egis', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (623, 'broom', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (624, 'bucket pail', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (625, 'buckle', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (626, 'bulletproof vest', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (627, 'bullet train bullet', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (628, 'butcher shop meat market', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (629, 'cab hack taxi taxicab', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (630, 'caldron cauldron', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (631, 'candle taper wax light', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (632, 'cannon', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (633, 'canoe', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (634, 'can opener tin opener', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (635, 'cardigan', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (636, 'car mirror', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (637, 'carousel carrousel merry-go-round roundabout whirligig', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (638, 'carpenter''s kit tool kit', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (639, 'carton', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (640, 'car wheel', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (641, 'cash machine cash dispenser automated teller machine automatic teller machine automated teller automatic teller ATM', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (642, 'cassette', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (643, 'cassette player', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (644, 'castle', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (645, 'catamaran', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (646, 'CD player', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (647, 'cello violoncello', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (648, 'cellular telephone cellular phone cellphone cell mobile phone', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (649, 'chain', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (650, 'chainlink fence', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (651, 'chain mail ring mail mail chain armor chain armour ring armor ring armour', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (652, 'chain saw chainsaw', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (653, 'chest', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (654, 'chiffonier commode', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (655, 'chime bell gong', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (656, 'china cabinet china closet', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (657, 'Christmas stocking', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (658, 'church church building', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (659, 'cinema movie theater movie theatre movie house picture palace', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (660, 'cleaver meat cleaver chopper', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (661, 'cliff dwelling', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (662, 'cloak', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (663, 'clog geta patten sabot', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (664, 'cocktail shaker', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (665, 'coffee mug', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (666, 'coffeepot', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (667, 'coil spiral volute whorl helix', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (668, 'combination lock', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (669, 'computer keyboard keypad', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (670, 'confectionery confectionary candy store', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (671, 'container ship containership container vessel', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (672, 'convertible', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (673, 'corkscrew bottle screw', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (674, 'cornet horn trumpet trump', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (675, 'cowboy boot', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (676, 'cowboy hat ten-gallon hat', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (677, 'cradle', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (678, 'crane', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (679, 'crash helmet', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (680, 'crate', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (681, 'crib cot', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (682, 'Crock Pot', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (683, 'croquet ball', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (684, 'crutch', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (685, 'cuirass', '#000000', null, '2020-07-01 17:22:10', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (686, 'dam dike dyke', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (687, 'desk', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (688, 'desktop computer', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (689, 'dial telephone dial phone', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (690, 'diaper nappy napkin', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (691, 'digital clock', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (692, 'digital watch', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (693, 'dining table board', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (694, 'dishrag dishcloth', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (695, 'dishwasher dish washer dishwashing machine', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (696, 'disk brake disc brake', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (697, 'dock dockage docking facility', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (698, 'dogsled dog sled dog sleigh', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (699, 'dome', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (700, 'doormat welcome mat', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (701, 'drilling platform offshore rig', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (702, 'drum membranophone tympan', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (703, 'drumstick', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (704, 'dumbbell', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (705, 'Dutch oven', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (706, 'electric fan blower', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (707, 'electric guitar', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (708, 'electric locomotive', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (709, 'entertainment center', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (710, 'envelope', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (711, 'espresso maker', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (712, 'face powder', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (713, 'feather boa boa', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (714, 'file file cabinet filing cabinet', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (715, 'fireboat', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (716, 'fire engine fire truck', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (717, 'fire screen fireguard', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (718, 'flagpole flagstaff', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (719, 'flute transverse flute', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (720, 'folding chair', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (721, 'football helmet', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (722, 'forklift', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (723, 'fountain', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (724, 'fountain pen', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (725, 'four-poster', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (726, 'freight car', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (727, 'French horn horn', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (728, 'frying pan frypan skillet', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (729, 'fur coat', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (730, 'garbage truck dustcart', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (731, 'gasmask respirator gas helmet', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (732, 'gas pump gasoline pump petrol pump island dispenser', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (733, 'goblet', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (734, 'go-kart', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (735, 'golf ball', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (736, 'golfcart golf cart', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (737, 'gondola', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (738, 'gong tam-tam', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (739, 'gown', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (740, 'grand piano grand', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (741, 'greenhouse nursery glasshouse', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (742, 'grille radiator grille', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (743, 'grocery store grocery food market market', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:17', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (744, 'guillotine', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (745, 'hair slide', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (746, 'hair spray', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (747, 'half track', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (748, 'hammer', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (749, 'hamper', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (750, 'hand blower blow dryer blow drier hair dryer hair drier', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (751, 'hand-held computer hand-held microcomputer', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (752, 'handkerchief hankie hanky hankey', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (753, 'hard disc hard disk fixed disk', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (754, 'harmonica mouth organ harp mouth harp', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (755, 'harp', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (756, 'harvester reaper', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (757, 'hatchet', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (758, 'holster', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (759, 'home theater home theatre', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (760, 'honeycomb', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (761, 'hook claw', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (762, 'hoopskirt crinoline', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (763, 'horizontal bar high bar', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (764, 'horse cart horse-cart', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (765, 'hourglass', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (766, 'iPod', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (767, 'iron smoothing iron', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (768, 'jack-o''-lantern', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (769, 'jean blue jean denim', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (770, 'jeep landrover', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (771, 'jersey T-shirt tee shirt', '#000000', null, '2020-07-01 17:22:11', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (772, 'jigsaw puzzle', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (773, 'jinrikisha ricksha rickshaw', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (774, 'joystick', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (775, 'kimono', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (776, 'knee pad', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (777, 'knot', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (778, 'lab coat laboratory coat', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (779, 'ladle', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (780, 'lampshade lamp shade', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (781, 'laptop laptop computer', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (782, 'lawn mower mower', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (783, 'lens cap lens cover', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (784, 'letter opener paper knife paperknife', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (785, 'library', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (786, 'lifeboat', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (787, 'lighter light igniter ignitor', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (788, 'limousine limo', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (789, 'liner ocean liner', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (790, 'lipstick lip rouge', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (791, 'Loafer', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (792, 'lotion', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (793, 'loudspeaker speaker speaker unit loudspeaker system speaker system', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (794, 'loupe jeweler''s loupe', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (795, 'lumbermill sawmill', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (796, 'magnetic compass', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (797, 'mailbag postbag', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (798, 'mailbox letter box', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (799, 'maillot', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (800, 'maillot tank suit', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (801, 'manhole cover', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (802, 'maraca', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (803, 'marimba xylophone', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (804, 'mask', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (805, 'matchstick', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (806, 'maypole', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (807, 'maze labyrinth', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (808, 'measuring cup', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (809, 'medicine chest medicine cabinet', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (810, 'megalith megalithic structure', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (811, 'microphone mike', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (812, 'microwave microwave oven', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (813, 'military uniform', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (814, 'milk can', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (815, 'minibus', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (816, 'miniskirt mini', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (817, 'minivan', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (818, 'missile', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (819, 'mitten', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (820, 'mixing bowl', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (821, 'mobile home manufactured home', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (822, 'Model T', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (823, 'modem', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (824, 'monastery', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (825, 'monitor', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (826, 'moped', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (827, 'mortar', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (828, 'mortarboard', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (829, 'mosque', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (830, 'mosquito net', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (831, 'motor scooter scooter', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (832, 'mountain bike all-terrain bike off-roader', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (833, 'mountain tent', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (834, 'mouse computer mouse', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (835, 'mousetrap', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (836, 'moving van', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (837, 'muzzle', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (838, 'nail', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (839, 'neck brace', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (840, 'necklace', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (841, 'nipple', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (842, 'notebook notebook computer', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (843, 'obelisk', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (844, 'oboe hautboy hautbois', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (845, 'ocarina sweet potato', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (846, 'odometer hodometer mileometer milometer', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (847, 'oil filter', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (848, 'organ pipe organ', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (849, 'oscilloscope scope cathode-ray oscilloscope CRO', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (850, 'overskirt', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (851, 'oxcart', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (852, 'oxygen mask', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (853, 'packet', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (854, 'paddle boat paddle', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (855, 'paddlewheel paddle wheel', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (856, 'padlock', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (857, 'paintbrush', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (858, 'pajama pyjama pj''s jammies', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (859, 'palace', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (860, 'panpipe pandean pipe syrinx', '#000000', null, '2020-07-01 17:22:12', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (861, 'paper towel', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (862, 'parachute chute', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (863, 'parallel bars bars', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (864, 'park bench', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (865, 'parking meter', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (866, 'passenger car coach carriage', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (867, 'patio terrace', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (868, 'pay-phone pay-station', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (869, 'pedestal plinth footstall', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (870, 'pencil box pencil case', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (871, 'pencil sharpener', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (872, 'perfume essence', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (873, 'Petri dish', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (874, 'photocopier', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (875, 'pick plectrum plectron', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (876, 'pickelhaube', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (877, 'picket fence paling', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (878, 'pickup pickup truck', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (879, 'pier', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (880, 'piggy bank penny bank', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (881, 'pill bottle', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (882, 'pillow', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (883, 'ping-pong ball', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (884, 'pinwheel', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (885, 'pirate pirate ship', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (886, 'pitcher ewer', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (887, 'plane carpenter''s plane woodworking plane', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (888, 'planetarium', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (889, 'plastic bag', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (890, 'plate rack', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (891, 'plow plough', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (892, 'plunger plumber''s helper', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (893, 'Polaroid camera Polaroid Land camera', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (894, 'pole', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (895, 'police van police wagon paddy wagon patrol wagon wagon black Maria', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (896, 'poncho', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (897, 'pool table billiard table snooker table', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (898, 'pop bottle soda bottle', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (899, 'pot flowerpot', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (900, 'potter''s wheel', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (901, 'power drill', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (902, 'prayer rug prayer mat', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (903, 'printer', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (904, 'prison prison house', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (905, 'projectile missile', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (906, 'projector', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (907, 'puck hockey puck', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (908, 'punching bag punch bag punching ball punchball', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (909, 'purse', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (910, 'quill quill pen', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (911, 'quilt comforter comfort puff', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (912, 'racer race car racing car', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (913, 'racket racquet', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (914, 'radiator', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (915, 'radio wireless', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:18', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (916, 'radio telescope radio reflector', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (917, 'rain barrel', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (918, 'recreational vehicle RV R.V.', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (919, 'reel', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (920, 'reflex camera', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (921, 'refrigerator icebox', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (922, 'remote control remote', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (923, 'restaurant eating house eating place eatery', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (924, 'revolver six-gun six-shooter', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (925, 'rifle', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (926, 'rocking chair rocker', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (927, 'rotisserie', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (928, 'rubber eraser rubber pencil eraser', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (929, 'rugby ball', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (930, 'rule ruler', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (931, 'running shoe', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (932, 'safe', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (933, 'safety pin', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (934, 'saltshaker salt shaker', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (935, 'sandal', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (936, 'sarong', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (937, 'sax saxophone', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (938, 'scabbard', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (939, 'scale weighing machine', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (940, 'school bus', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (941, 'schooner', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (942, 'scoreboard', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (943, 'screen CRT screen', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (944, 'screw', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (945, 'screwdriver', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (946, 'seat belt seatbelt', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (947, 'sewing machine', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (948, 'shield buckler', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (949, 'shoe shop shoe-shop shoe store', '#000000', null, '2020-07-01 17:22:13', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (950, 'shoji', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (951, 'shopping basket', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (952, 'shopping cart', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (953, 'shovel', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (954, 'shower cap', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (955, 'shower curtain', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (956, 'ski', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (957, 'ski mask', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (958, 'sleeping bag', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (959, 'slide rule slipstick', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (960, 'sliding door', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (961, 'slot one-armed bandit', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (962, 'snorkel', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (963, 'snowmobile', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (964, 'snowplow snowplough', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (965, 'soap dispenser', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (966, 'soccer ball', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (967, 'sock', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (968, 'solar dish solar collector solar furnace', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (969, 'sombrero', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (970, 'soup bowl', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (971, 'space bar', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (972, 'space heater', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (973, 'space shuttle', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (974, 'spatula', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (975, 'speedboat', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (976, 'spider web spider''s web', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (977, 'spindle', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (978, 'sports car sport car', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (979, 'spotlight spot', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (980, 'stage', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (981, 'steam locomotive', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (982, 'steel arch bridge', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (983, 'steel drum', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (984, 'stethoscope', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (985, 'stole', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (986, 'stone wall', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (987, 'stopwatch stop watch', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (988, 'stove', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (989, 'strainer', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (990, 'streetcar tram tramcar trolley trolley car', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (991, 'stretcher', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (992, 'studio couch day bed', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (993, 'stupa tope', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (994, 'submarine pigboat sub U-boat', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (995, 'suit suit of clothes', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (996, 'sundial', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (997, 'sunglass', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (998, 'sunglasses dark glasses shades', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (999, 'sunscreen sunblock sun blocker', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1000, 'suspension bridge', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1001, 'swab swob mop', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1002, 'sweatshirt', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1003, 'swimming trunks bathing trunks', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1004, 'swing', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1005, 'switch electric switch electrical switch', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1006, 'syringe', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1007, 'table lamp', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1008, 'tank army tank armored combat vehicle armoured combat vehicle', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1009, 'tape player', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1010, 'teapot', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1011, 'teddy teddy bear', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1012, 'television television system', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1013, 'tennis ball', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1014, 'thatch thatched roof', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1015, 'theater curtain theatre curtain', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1016, 'thimble', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1017, 'thresher thrasher threshing machine', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1018, 'throne', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1019, 'tile roof', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1020, 'toaster', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1021, 'tobacco shop tobacconist shop tobacconist', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1022, 'toilet seat', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1023, 'torch', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1024, 'totem pole', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1025, 'tow truck tow car wrecker', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1026, 'toyshop', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1027, 'tractor', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1028, 'trailer truck tractor trailer trucking rig rig articulated lorry semi', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1029, 'tray', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1030, 'trench coat', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1031, 'tricycle trike velocipede', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1032, 'trimaran', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1033, 'tripod', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1034, 'triumphal arch', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1035, 'trolleybus trolley coach trackless trolley', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1036, 'trombone', '#000000', null, '2020-07-01 17:22:14', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1037, 'tub vat', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1038, 'turnstile', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1039, 'typewriter keyboard', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1040, 'umbrella', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1041, 'unicycle monocycle', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1042, 'upright upright piano', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1043, 'vacuum vacuum cleaner', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1044, 'vase', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1045, 'vault', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1046, 'velvet', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1047, 'vending machine', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1048, 'vestment', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1049, 'viaduct', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1050, 'violin fiddle', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1051, 'volleyball', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1052, 'waffle iron', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1053, 'wall clock', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1054, 'wallet billfold notecase pocketbook', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1055, 'wardrobe closet press', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1056, 'warplane military plane', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1057, 'washbasin handbasin washbowl lavabo wash-hand basin', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1058, 'washer automatic washer washing machine', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1059, 'water bottle', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1060, 'water jug', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1061, 'water tower', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1062, 'whiskey jug', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1063, 'whistle', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1064, 'wig', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1065, 'window screen', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1066, 'window shade', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1067, 'Windsor tie', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1068, 'wine bottle', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1069, 'wing', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1070, 'wok', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1071, 'wooden spoon', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1072, 'wool woolen woollen', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1073, 'worm fence snake fence snake-rail fence Virginia fence', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1074, 'wreck', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1075, 'yawl', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1076, 'yurt', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1077, 'web site website internet site site', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1078, 'comic book', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1079, 'crossword puzzle crossword', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1080, 'street sign', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1081, 'traffic light traffic signal stoplight', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:19', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1082, 'book jacket dust cover dust jacket dust wrapper', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1083, 'menu', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1084, 'plate', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1085, 'guacamole', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1086, 'consomme', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1087, 'hot pot hotpot', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1088, 'trifle', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1089, 'ice cream icecream', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1090, 'ice lolly lolly lollipop popsicle', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1091, 'French loaf', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1092, 'bagel beigel', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1093, 'pretzel', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1094, 'cheeseburger', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1095, 'hotdog hot dog red hot', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1096, 'mashed potato', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1097, 'head cabbage', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1098, 'broccoli', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1099, 'cauliflower', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1100, 'zucchini courgette', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1101, 'spaghetti squash', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1102, 'acorn squash', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1103, 'butternut squash', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1104, 'cucumber cuke', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1105, 'artichoke globe artichoke', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1106, 'bell pepper', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1107, 'cardoon', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1108, 'mushroom', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1109, 'Granny Smith', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1110, 'strawberry', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1111, 'orange', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1112, 'lemon', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1113, 'fig', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1114, 'pineapple ananas', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1115, 'banana', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1116, 'jackfruit jak jack', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1117, 'custard apple', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1118, 'pomegranate', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1119, 'hay', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1120, 'carbonara', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1121, 'chocolate sauce chocolate syrup', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1122, 'dough', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1123, 'meat loaf meatloaf', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1124, 'pizza pizza pie', '#000000', null, '2020-07-01 17:22:15', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1125, 'potpie', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1126, 'burrito', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1127, 'red wine', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1128, 'espresso', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1129, 'cup', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1130, 'eggnog', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1131, 'alp', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1132, 'bubble', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1133, 'cliff drop drop-off', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1134, 'coral reef', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1135, 'geyser', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1136, 'lakeside lakeshore', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1137, 'promontory headland head foreland', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1138, 'sandbar sand bar', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1139, 'seashore coast seacoast sea-coast', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1140, 'valley vale', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1141, 'volcano', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1142, 'ballplayer baseball player', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1143, 'groom bridegroom', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1144, 'scuba diver', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1145, 'rapeseed', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1146, 'daisy', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1147, 'yellow lady''s slipper yellow lady-slipper Cypripedium calceolus Cypripedium parviflorum', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1148, 'corn', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1149, 'acorn', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1150, 'hip rose hip rosehip', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1151, 'buckeye horse chestnut conker', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1152, 'coral fungus', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1153, 'agaric', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1154, 'gyromitra', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1155, 'stinkhorn carrion fungus', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1156, 'earthstar', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1157, 'hen-of-the-woods hen of the woods Polyporus frondosus Grifola frondosa', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1158, 'bolete', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 17:23:28', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1159, 'ear spike capitulum', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 18:55:20', b'0', 2);
-- insert into `data_label`(`id`,`name`,`color`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`type`) values (1160, 'toilet tissue toilet paper bathroom tissue', '#000000', null, '2020-07-01 17:22:16', null, '2020-07-01 18:55:20', b'0', 2);



-- -- data_label_group 预置标签组
-- INSERT INTO data_label_group (id, name, create_user_id, create_time, update_user_id, update_time, deleted, remark, type, origin_user_id) VALUES (1, 'COCO', 0, current_timestamp, null, current_timestamp, false, 'test', 1, 0);
-- INSERT INTO data_label_group (id, name, create_user_id, create_time, update_user_id, update_time, deleted, remark, type, origin_user_id) VALUES (2, 'Imagenet', 0, current_timestamp, null, current_timestamp, false, 'test', 1, 0);


-- -- data_group_label 新增预置标签组和标签之间的关系
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (81, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (82, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (83, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (84, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (85, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (86, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (87, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (88, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (89, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (90, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (91, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (92, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (93, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (94, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (95, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (96, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (97, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (98, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (99, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (100, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (101, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (102, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (103, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (104, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (105, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (106, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (107, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (108, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (109, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (110, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (111, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (112, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (113, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (114, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (115, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (116, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (117, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (118, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (119, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (120, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (121, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (122, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (123, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (124, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (125, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (126, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (127, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (128, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (129, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (130, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (131, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (132, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (133, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (134, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (135, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (136, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (137, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (138, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (139, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (140, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (141, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (142, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (143, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (144, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (145, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (146, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (147, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (148, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (149, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (150, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (151, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (152, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (153, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (154, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (155, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (156, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (157, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (158, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (159, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (160, 1, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (161, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (162, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (163, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (164, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (165, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (166, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (167, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (168, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (169, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (170, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (171, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (172, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (173, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (174, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (175, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (176, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (177, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (178, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (179, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (180, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (181, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (182, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (183, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (184, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (185, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (186, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (187, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (188, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (189, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (190, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (191, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (192, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (193, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (194, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (195, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (196, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (197, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (198, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (199, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (200, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (201, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (202, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (203, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (204, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (205, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (206, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (207, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (208, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (209, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (210, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (211, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (212, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (213, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (214, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (215, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (216, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (217, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (218, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (219, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (220, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (221, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (222, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (223, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (224, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (225, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (226, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (227, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (228, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (229, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (230, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (231, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (232, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (233, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (234, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (235, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (236, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (237, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (238, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (239, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (240, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (241, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (242, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (243, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (244, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (245, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (246, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (247, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (248, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (249, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (250, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (251, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (252, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (253, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (254, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (255, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (256, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (257, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (258, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (259, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (260, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (261, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (262, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (263, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (264, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (265, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (266, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (267, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (268, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (269, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (270, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (271, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (272, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (273, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (274, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (275, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (276, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (277, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (278, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (279, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (280, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (281, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (282, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (283, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (284, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (285, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (286, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (287, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (288, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (289, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (290, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (291, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (292, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (293, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (294, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (295, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (296, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (297, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (298, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (299, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (300, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (301, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (302, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (303, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (304, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (305, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (306, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (307, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (308, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (309, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (310, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (311, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (312, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (313, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (314, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (315, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (316, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (317, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (318, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (319, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (320, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (321, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (322, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (323, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (324, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (325, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (326, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (327, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (328, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (329, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (330, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (331, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (332, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (333, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (334, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (335, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (336, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (337, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (338, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (339, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (340, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (341, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (342, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (343, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (344, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (345, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (346, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (347, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (348, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (349, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (350, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (351, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (352, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (353, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (354, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (355, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (356, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (357, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (358, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (359, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (360, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (361, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (362, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (363, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (364, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (365, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (366, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (367, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (368, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (369, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (370, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (371, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (372, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (373, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (374, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (375, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (376, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (377, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (378, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (379, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (380, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (381, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (382, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (383, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (384, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (385, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (386, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (387, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (388, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (389, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (390, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (391, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (392, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (393, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (394, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (395, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (396, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (397, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (398, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (399, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (400, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (401, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (402, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (403, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (404, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (405, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (406, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (407, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (408, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (409, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (410, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (411, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (412, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (413, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (414, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (415, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (416, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (417, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (418, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (419, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (420, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (421, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (422, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (423, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (424, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (425, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (426, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (427, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (428, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (429, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (430, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (431, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (432, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (433, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (434, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (435, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (436, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (437, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (438, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (439, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (440, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (441, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (442, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (443, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (444, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (445, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (446, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (447, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (448, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (449, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (450, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (451, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (452, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (453, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (454, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (455, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (456, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (457, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (458, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (459, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (460, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (461, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (462, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (463, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (464, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (465, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (466, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (467, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (468, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (469, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (470, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (471, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (472, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (473, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (474, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (475, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (476, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (477, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (478, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (479, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (480, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (481, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (482, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (483, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (484, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (485, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (486, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (487, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (488, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (489, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (490, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (491, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (492, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (493, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (494, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (495, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (496, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (497, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (498, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (499, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (500, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (501, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (502, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (503, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (504, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (505, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (506, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (507, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (508, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (509, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (510, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (511, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (512, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (513, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (514, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (515, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (516, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (517, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (518, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (519, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (520, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (521, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (522, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (523, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (524, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (525, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (526, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (527, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (528, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (529, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (530, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (531, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (532, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (533, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (534, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (535, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (536, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (537, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (538, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (539, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (540, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (541, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (542, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (543, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (544, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (545, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (546, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (547, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (548, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (549, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (550, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (551, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (552, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (553, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (554, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (555, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (556, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (557, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (558, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (559, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (560, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (561, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (562, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (563, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (564, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (565, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (566, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (567, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (568, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (569, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (570, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (571, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (572, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (573, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (574, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (575, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (576, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (577, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (578, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (579, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (580, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (581, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (582, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (583, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (584, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (585, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (586, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (587, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (588, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (589, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (590, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (591, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (592, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (593, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (594, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (595, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (596, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (597, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (598, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (599, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (600, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (601, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (602, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (603, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (604, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (605, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (606, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (607, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (608, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (609, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (610, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (611, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (612, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (613, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (614, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (615, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (616, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (617, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (618, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (619, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (620, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (621, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (622, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (623, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (624, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (625, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (626, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (627, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (628, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (629, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (630, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (631, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (632, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (633, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (634, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (635, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (636, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (637, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (638, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (639, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (640, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (641, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (642, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (643, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (644, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (645, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (646, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (647, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (648, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (649, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (650, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (651, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (652, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (653, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (654, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (655, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (656, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (657, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (658, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (659, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (660, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (661, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (662, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (663, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (664, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (665, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (666, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (667, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (668, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (669, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (670, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (671, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (672, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (673, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (674, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (675, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (676, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (677, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (678, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (679, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (680, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (681, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (682, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (683, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (684, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (685, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (686, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (687, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (688, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (689, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (690, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (691, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (692, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (693, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (694, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (695, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (696, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (697, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (698, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (699, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (700, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (701, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (702, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (703, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (704, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (705, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (706, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (707, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (708, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (709, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (710, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (711, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (712, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (713, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (714, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (715, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (716, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (717, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (718, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (719, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (720, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (721, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (722, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (723, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (724, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (725, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (726, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (727, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (728, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (729, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (730, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (731, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (732, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (733, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (734, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (735, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (736, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (737, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (738, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (739, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (740, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (741, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (742, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (743, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (744, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (745, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (746, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (747, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (748, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (749, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (750, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (751, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (752, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (753, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (754, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (755, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (756, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (757, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (758, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (759, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (760, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (761, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (762, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (763, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (764, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (765, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (766, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (767, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (768, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (769, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (770, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (771, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (772, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (773, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (774, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (775, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (776, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (777, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (778, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (779, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (780, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (781, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (782, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (783, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (784, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (785, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (786, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (787, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (788, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (789, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (790, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (791, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (792, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (793, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (794, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (795, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (796, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (797, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (798, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (799, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (800, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (801, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (802, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (803, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (804, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (805, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (806, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (807, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (808, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (809, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (810, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (811, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (812, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (813, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (814, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (815, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (816, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (817, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (818, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (819, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (820, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (821, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (822, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (823, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (824, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (825, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (826, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (827, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (828, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (829, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (830, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (831, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (832, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (833, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (834, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (835, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (836, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (837, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (838, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (839, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (840, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (841, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (842, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (843, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (844, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (845, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (846, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (847, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (848, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (849, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (850, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (851, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (852, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (853, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (854, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (855, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (856, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (857, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (858, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (859, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (860, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (861, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (862, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (863, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (864, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (865, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (866, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (867, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (868, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (869, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (870, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (871, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (872, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (873, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (874, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (875, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (876, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (877, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (878, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (879, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (880, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (881, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (882, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (883, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (884, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (885, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (886, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (887, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (888, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (889, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (890, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (891, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (892, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (893, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (894, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (895, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (896, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (897, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (898, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (899, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (900, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (901, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (902, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (903, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (904, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (905, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (906, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (907, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (908, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (909, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (910, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (911, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (912, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (913, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (914, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (915, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (916, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (917, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (918, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (919, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (920, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (921, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (922, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (923, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (924, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (925, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (926, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (927, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (928, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (929, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (930, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (931, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (932, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (933, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (934, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (935, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (936, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (937, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (938, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (939, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (940, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (941, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (942, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (943, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (944, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (945, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (946, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (947, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (948, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (949, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (950, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (951, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (952, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (953, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (954, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (955, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (956, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (957, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (958, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (959, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (960, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (961, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (962, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (963, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (964, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (965, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (966, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (967, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (968, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (969, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (970, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (971, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (972, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (973, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (974, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (975, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (976, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (977, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (978, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (979, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (980, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (981, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (982, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (983, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (984, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (985, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (986, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (987, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (988, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (989, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (990, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (991, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (992, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (993, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (994, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (995, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (996, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (997, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (998, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (999, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1000, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1001, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1002, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1003, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1004, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1005, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1006, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1007, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1008, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1009, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1010, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1011, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1012, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1013, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1014, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1015, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1016, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1017, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1018, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1019, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1020, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1021, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1022, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1023, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1024, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1025, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1026, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1027, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1028, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1029, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1030, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1031, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1032, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1033, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1034, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1035, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1036, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1037, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1038, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1039, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1040, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1041, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1042, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1043, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1044, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1045, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1046, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1047, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1048, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1049, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1050, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1051, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1052, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1053, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1054, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1055, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1056, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1057, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1058, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1059, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1060, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1061, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1062, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1063, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1064, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1065, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1066, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1067, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1068, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1069, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1070, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1071, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1072, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1073, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1074, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1075, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1076, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1077, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1078, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1079, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1080, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1081, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1082, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1083, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1084, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1085, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1086, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1087, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1088, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1089, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1090, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1091, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1092, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1093, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1094, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1095, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1096, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1097, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1098, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1099, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1100, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1101, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1102, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1103, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1104, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1105, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1106, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1107, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1108, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1109, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1110, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1111, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1112, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1113, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1114, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1115, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1116, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1117, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1118, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1119, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1120, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1121, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1122, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1123, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1124, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1125, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1126, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1127, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1128, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1129, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1130, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1131, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1132, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1133, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1134, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1135, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1136, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1137, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1138, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1139, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1140, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1141, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1142, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1143, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1144, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1145, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1146, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1147, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1148, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1149, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1150, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1151, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1152, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1153, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1154, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1155, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1156, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1157, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1158, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1159, 2, 1, current_timestamp, 1, current_timestamp, false);
-- INSERT INTO data_group_label (label_id, label_group_id, create_user_id, create_time, update_user_id, update_time, deleted) VALUES (1160, 2, 1, current_timestamp, 1, current_timestamp, false);

-- 新增 表data_sequence 默认配置
-- INSERT INTO data_sequence (id, business_code, start, step) VALUES (1, 'DATA_FILE', 1, 5000);
-- INSERT INTO data_sequence (id, business_code, start, step) VALUES (2, 'DATA_VERSION_FILE', 1, 5000);
    INSERT INTO data_sequence (id, business_code, start, step) VALUES (3, 'DATA_FILE_ANNOTATION', 1, 5000);


    -- -- data_label_group
-- insert  into `data_label_group`(`id`,`name`,`create_user_id`,`create_time`,`update_user_id`,`update_time`,`deleted`,`remark`,`type`,`origin_user_id`,`operate_type`,`label_group_type`) values
-- (1,'COCO',0,'2021-07-02 03:22:20',NULL,'2021-07-02 03:22:20','\0','test',1,0,NULL,0),
-- (2,'ImageNet',0,'2021-07-02 03:22:20',NULL,'2021-07-02 03:22:20','\0','test',1,0,NULL,0),
-- (3,'文本自动标注标签',1,'2021-07-02 03:22:21',1,'2021-07-02 03:22:21','\0','IMDB',1,0,1,1);

-- dict
    insert  into `dict`(`id`,`name`,`remark`) values
    (1, 'Layout', '页面布局'),
    (2, 'user_status', '用户状态'),
    (3, 'node_status', '节点状态'),
    (4, 'pods_status', 'POD状态'),
    (5, 'node_warning', '节点异常状态'),
    (6, 'frame_type', '框架名称'),
    (7, 'model_type', '模型格式'),
    (8, 'model_class', '模型分类'),
    (9, 'model_source', '模型来源'),
    (10, 'job_status', '训练任务状态'),
    (11, 'dataset_type', '数据集分类'),
    (12, 'dataset_enhance', '数据集增强')
    ON DUPLICATE KEY UPDATE `id`=VALUES(`id`),`name`=VALUES(`name`),`remark`=VALUES(`remark`);


-- dict_detail
    insert  into `dict_detail`(`dict_id`,`label`,`value`,`sort`) values
    (1, '基本布局', 'BaseLayout', 1),
    (1, '二级页面', 'SubpageLayout', 2),
    (1, '详情页面', 'DetailLayout', 3),
    (1, '数据集页面', 'DatasetLayout', 4),
    (2, '激活', 'true', 1),
    (2, '锁定', 'false', 2),
    (3, '就绪', 'Ready', 1),
    (3, '未就绪', 'NotReady', 2),
    (4, '运行中', 'Running', 1),
    (4, '调度中', 'Pending', 2),
    (4, '运行完成', 'Succeeded', 3),
    (4, '已删除', 'Deleted', 4),
    (4, '运行失败', 'Failed', 5),
    (4, '未知状态', 'Unknown', 6),
    (5, '网络资源不足', 'NetworkUnavailable', 1),
    (5, '内存资源不足', 'MemoryPressure', 2),
    (5, '磁盘资源不足', 'DiskPressure', 3),
    (5, '进程资源不足', 'PIDPressure', 4),
    (6, 'oneflow', '1', 1),
    (6, 'tensorflow', '2', 2),
    (6, 'pytorch', '3', 3),
    (6, 'keras', '4', 4),
    (6, 'caffe', '5', 5),
    (6, 'blade', '6', 6),
    (6, 'mxnet', '7', 7),
    (7, 'SavedModel', '1', 1),
    (7, 'FrozenPb', '2', 2),
    (7, 'KerasH5', '3', 3),
    (7, 'CaffePrototxt', '4', 4),
    (7, 'ONNX', '5', 5),
    (7, 'BladeModel', '6', 6),
    (7, 'PMML', '7', 7),
    (7, 'Pytorch PTH', '8', 8),
    (7, 'pb', '9', 9),
    (7, 'ckpt', '10', 10),
    (7, 'pkt', '11', 11),
    (7, 'pt', '12', 12),
    (7, 'h5(HDF5)', '13', 13),
    (7, 'caffemodel', '14', 14),
    (7, 'params', '15', 15),
    (7, 'json', '16', 16),
    (7, 'Directory', '17', 17),
    (8, '目标检测', '1', 1),
    (8, '目标分类', '2', 2),
    (8, '行为分析', '3', 3),
    (8, '异常检测', '4', 4),
    (8, '目标跟踪', '5', 5),
    (8, '模型优化', '6', 6),
    (9, '用户上传', '0', 1),
    (9, '训练生成', '1', 2),
    (9, '优化生成', '2', 3),
    (9, '模型转换', '3', 4),
    (10, '待处理', '0', 1),
    (10, '运行中', '1', 2),
    (10, '运行完成', '2', 3),
    (10, '运行失败', '3', 4),
    (10, '停止', '4', 5),
    (10, '未知', '5', 6),
    (10, '创建失败', '7', 7),
    (11, '图像分类', '0', 1),
    (11, '目标检测', '1', 2),
    (11, '目标跟踪', '2', 3),
    (12, '去雾', '1', 1),
    (12, '增雾', '2', 2),
    (12, '对比度增强', '3', 3),
    (12, '直方图均衡化', '4', 4),
    (36, '1CPU4GB内存 1GPU', '{"cpuNum": 1000, "gpuNum": 1, "memNum": 4000, "workspaceRequest": "100Mi"}', 1),
    (36, '2CPU4GB内存 1GPU', '{"cpuNum": 2000, "gpuNum": 1, "memNum": 4000, "workspaceRequest": "500Mi"}', 2),
    (36, '4CPU8GB内存 1GPU', '{"cpuNum": 4000, "gpuNum": 1, "memNum": 8000, "workspaceRequest": "500Mi"}', 3),
    (36, '8CPU16GB内存 4GPU', '{"cpuNum": 8000, "gpuNum": 4, "memNum": 16000, "workspaceRequest": "500Mi"}', 6),
    (36, '8CPU16GB内存 1GPU', '{"cpuNum": 8000, "gpuNum": 1, "memNum": 16000, "workspaceRequest": "500Mi"}', 5),
    (36, '8CPU32GB内存 1GPU', '{"cpuNum": 8000, "gpuNum": 4, "memNum": 32000, "workspaceRequest": "500Mi"}', 7),
    (36, '4CPU8GB内存 2GPU', '{"cpuNum": 4000, "gpuNum": 2, "memNum": 8000, "workspaceRequest": "500Mi"}', 4),
    (35, '1CPU2GB内存', '{"cpuNum": 1000, "gpuNum": 0, "memNum": 2000, "workspaceRequest": "100Mi"}', 1),
    (35, '2CPU4GB内存', '{"cpuNum": 2000, "gpuNum": 0, "memNum": 4000, "workspaceRequest": "100Mi"}', 2),
    (38, 'alexnet', 'alexnet', '1'),
    (38, 'resnet18', 'resnet18', '2'),
    (38, 'resnet34', 'resnet34', '3'),
    (38, 'resnet50', 'resnet50', '4'),
    (38, 'resnet101', 'resnet101', '5'),
    (38, 'resnet152', 'resnet152', '6'),
    (38, 'resnext50_32x4d', 'resnext50_32x4d', '7'),
    (38, 'resnext101_32x8d', 'resnext101_32x8d', '8'),
    (38, 'wide_resnet50_2', 'wide_resnet50_2', '9'),
    (38, 'wide_resnet101_2', 'wide_resnet101_2', '10'),
    (38, 'vgg11', 'vgg11', '11'),
    (38, 'vgg13', 'vgg13', '12'),
    (38, 'vgg16', 'vgg16', '13'),
    (38, 'vgg19', 'vgg19', '14'),
    (38, 'vgg11_bn', 'vgg11_bn', '15'),
    (38, 'vgg13_bn', 'vgg13_bn', '16'),
    (38, 'vgg16_bn', 'vgg16_bn', '17'),
    (38, 'vgg19_bn', 'vgg19_bn', '18'),
    (38, 'squeezenet1_0', 'squeezenet1_0', '19'),
    (38, 'squeezenet1_1', 'squeezenet1_1', '20'),
    (38, 'inception_v3', 'inception_v3', '21'),
    (38, 'densenet121', 'densenet121', '22'),
    (38, 'densenet169', 'densenet169', '23'),
    (38, 'densenet201', 'densenet201', '24'),
    (38, 'densenet161', 'densenet161', '25'),
    (38, 'googlenet', 'googlenet', '26'),
    (38, 'mobilenet_v2', 'mobilenet_v2', '27'),
    (38, 'mnasnet0_5', 'mnasnet0_5', '28'),
    (38, 'mnasnet0_75', 'mnasnet0_75', '29'),
    (38, 'mnasnet1_0', 'mnasnet1_0', '30'),
    (38, 'mnasnet1_3', 'mnasnet1_3', '31'),
    (38, 'shufflenet_v2_x0_5', 'shufflenet_v2_x0_5', '32'),
    (38, 'shufflenet_v2_x1_0', 'shufflenet_v2_x1_0', '33'),
    (38, 'shufflenet_v2_x1_5', 'shufflenet_v2_x1_5', '34'),
    (38, 'shufflenet_v2_x2_0', 'shufflenet_v2_x2_0', '35'),
    (27, '部署中', '1', 1),
    (27, '运行中', '2', 2),
    (27, '已停止', '3', 3),
    (27, '运行失败', '0', 4),
    (30, 'signature_name', 'Tensorflow模型接口定义名称（serving_default）', 1),
    (30, 'reshape_size', '图片预处理形状 [H, W]', 2),
    (30, 'prepare_mode', 'keras/Tensoflow模型预处理模式(tfhub、caffe、tf、torch)', 3),
    (30, 'model_structure', 'pytorch模型保存网络名称（model）', 4),
    (30, 'job_name', 'oneflow模型推理job名称（inference）', 5),
    (9, '模型转换', '3', 4) ;
-- ON DUPLICATE KEY UPDATE `dict_id`=VALUES(`dict_id`),`label`=VALUES(`label`),`value`=VALUES(`value`),`sort`=VALUES(`sort`);


    insert  into `menu` (`id`,`pid`,`type`,`name`,`icon`,`path`,`component`,`component_name`,`layout`,`permission`,`back_to`,`ext_config`,`hidden`,`cache`,`sort`,`create_user_id`,`update_user_id`,`deleted`) values
    (1,0,1,'概览','yibiaopan','dashboard','dashboard/dashboard','Dashboard','BaseLayout',NULL,NULL,NULL,'\0','\0',1,NULL,NULL,'\0'),
    (10,0,0,'数据管理','shujuguanli','data',NULL,NULL,NULL,'data',NULL,NULL,'\0','\0',2,NULL,NULL,'\0'),
    (11,10,1,'数据集管理','shujuguanli','datasets/list','dataset/list','Datasets','BaseLayout','data:dataset',NULL,NULL,'\0','\0',999,NULL,NULL,'\0'),
    (12,10,1,'图像分类',NULL,'datasets/classify/:datasetId','dataset/classify','DatasetClassify','DetailLayout',NULL,NULL,NULL,'','\0',999,NULL,NULL,'\0'),
    (13,10,1,'目标检测',NULL,'datasets/annotate/:datasetId/file/:fileId','dataset/annotate','AnnotateDatasetFile','DetailLayout',NULL,NULL,NULL,'','\0',999,NULL,NULL,'\0'),
    (14,10,1,'目标检测',NULL,'datasets/annotate/:datasetId','dataset/annotate','AnnotateDataset','DetailLayout',NULL,NULL,NULL,'','\0',999,NULL,NULL,'\0'),
    (15,10,1,'目标跟踪',NULL,'datasets/track/:datasetId/file/:fileId','dataset/annotate','TrackDatasetFile','DatasetLayout',NULL,NULL,NULL,'','\0',999,NULL,NULL,'\0'),
    (16,10,1,'目标跟踪',NULL,'datasets/track/:datasetId','dataset/annotate','TrackDataset','DatasetLayout',NULL,NULL,NULL,'','\0',999,NULL,NULL,'\0'),
    (17,10,1,'数据集版本管理',NULL,'datasets/:datasetId/version','dataset/version','DatasetVersion','SubpageLayout',NULL,NULL,NULL,'','\0',999,NULL,NULL,'\0'),
    (30,0,0,'算法开发','xunlianzhunbei','development',NULL,NULL,NULL,'development',NULL,NULL,'\0','\0',3,NULL,NULL,'\0'),
    (31,30,1,'Notebook','kaifahuanjing','development:notebook','development/notebook','Notebook','BaseLayout','notebook',NULL,NULL,'\0','\0',999,NULL,NULL,'\0'),
    (32,30,1,'算法管理','mobanguanli','algorithm','algorithm/index','Algorithm','BaseLayout','development:algorithm',NULL,NULL,'\0','\0',999,NULL,NULL,'\0'),
    (40,0,0,'训练管理','xunlianguocheng','training',NULL,NULL,NULL,'training',NULL,NULL,'\0','\0',4,NULL,NULL,'\0'),
    (41,40,1,'镜像管理','jingxiangguanli','image','trainingImage/index','TrainingImage','BaseLayout','training:image',NULL,NULL,'\0','\0',999,NULL,NULL,'\0'),
    (42,40,1,'训练任务','renwuguanli','job','trainingJob/index','TrainingJob','BaseLayout','training:job',NULL,NULL,'\0','\0',999,NULL,NULL,'\0'),
    (43,40,1,'任务详情',NULL,'jobDetail','trainingJob/detail','JobDetail','SubpageLayout',NULL,NULL,NULL,'','\0',999,NULL,NULL,'\0'),
    (44,40,1,'添加任务',NULL,'jobAdd','trainingJob/add','jobAdd','SubpageLayout',NULL,NULL,NULL,'','\0',999,NULL,NULL,'\0'),
    (45,40,1,'可视化任务','mobanguanli','visual','trainingJob/trainingVisualList','TrainVisual','BaseLayout','training:visual',NULL,NULL,'\0','\0',999,NULL,NULL,'\0'),
    (50,0,1,'模型管理','moxingguanli','model',NULL,NULL,NULL,'model',NULL,NULL,'\0','\0',5,NULL,NULL,'\0'),
    (51,50,1,'模型列表','zongshili','model','model/index','ModelModel','BaseLayout','model:model',NULL,NULL,'\0','\0',999,NULL,NULL,'\0'),
    (52,50,1,'模型优化','caidanguanli','optimize','modelOptimize/index','ModelOptimize','BaseLayout','model:optimize',NULL,NULL,'\0','\0',52,1,1,'\0'),
    (53,50,1,'模型版本管理',NULL,'version','model/version','ModelVersion','SubpageLayout','model:branch',NULL,NULL,'','\0',999,NULL,NULL,'\0'),
    (54,50,1,'模型优化执行记录',NULL,'optimize/record','modelOptimize/record','ModelOptRecord','SubpageLayout',NULL,NULL,NULL,'\0','\0',54,1,1,'\0'),
    (90,0,0,'控制台','kongzhitaixitongguanliyuankejian','system',NULL,NULL,NULL,'system',NULL,NULL,'\0','\0',999,NULL,NULL,'\0'),
    (91,90,1,'用户管理','yonghuguanli','user','system/user/index','SystemUser','BaseLayout','system:user',NULL,NULL,'\0','\0',999,NULL,NULL,'\0'),
    (92,90,1,'角色管理','jiaoseguanli','role','system/role/index','SystemRole','BaseLayout','system:role',NULL,NULL,'\0','\0',999,NULL,NULL,'\0'),
    (93,90,1,'菜单管理','caidanguanli','menu','system/menu/index','SystemMenu','BaseLayout','system:menu',NULL,NULL,'\0','\0',999,NULL,NULL,'\0'),
    (94,90,1,'字典管理','mobanguanli','dict','system/dict/index','SystemDict','BaseLayout','system:dict',NULL,NULL,'\0','\0',999,NULL,NULL,'\0'),
    (95,90,1,'集群状态','jiqunguanli','node','system/node/index','SystemNode','BaseLayout','system:node',NULL,NULL,'\0','\0',999,NULL,NULL,'\0'),
    (96,90,1,'回收站','shuju1','recycle','system/recycle/index','SystemRecycle','BaseLayout','system:recycle',NULL,NULL,'\0','\0',999,1,1,'\0'),
    (97,0,0,'模型炼知','icon_huabanfuben1','atlas',NULL,NULL,NULL,NULL,NULL,NULL,'\0','\0',70,1,1,'\0'),
    (100,10,1,'编辑标签组',NULL,'labelgroup/edit','labelGroup/labelGroupForm','LabelGroupEdit','SubpageLayout',NULL,NULL,NULL,'','\0',24,1,1,'\0'),
    (101,10,1,'标签组详情',NULL,'labelgroup/detail','labelGroup/labelGroupForm','LabelGroupDetail','SubpageLayout',NULL,NULL,NULL,'','\0',23,1,1,'\0'),
    (102,10,1,'创建标签组',NULL,'labelgroup/create','labelGroup/labelGroupForm','LabelGroupCreate','SubpageLayout',NULL,NULL,NULL,'','\0',22,1,1,'\0'),
    (103,10,1,'标签组管理','mobanguanli','labelgroup','labelGroup/index','LabelGroup','BaseLayout','',NULL,NULL,'\0','\0',21,1,1,'\0'),
    (1058,0,0,'云端Serving','shujumoxing','cloudserving',NULL,NULL,NULL,NULL,NULL,NULL,'\0','\0',60,1,1,'\0'),
    (1059,1058,1,'在线服务','shujumoxing','onlineserving','cloudServing','CloudServing','BaseLayout','serving:online',NULL,NULL,'\0','\0',61,1,1,'\0'),
    (1060,1058,1,'批量服务','shujumoxing','batchserving','cloudServing/batch','BatchServing','BaseLayout','serving:batch',NULL,NULL,'\0','\0',62,1,1,'\0'),
    (1061,1058,1,'部署详情',NULL,'onlineserving/detail','cloudServing/detail','CloudServingDetail','SubpageLayout','serving:online',NULL,NULL,'','\0',63,1,1,'\0'),
    (1062,1058,1,'部署详情',NULL,'batchserving/detail','cloudServing/batchDetail','BatchServingDetail','SubpageLayout','serving:batch',NULL,NULL,'','\0',64,1,1,'\0'),
    (1063,1058,1,'部署在线服务',NULL,'onlineserving/form','cloudServing/formPage','CloudServingForm','SubpageLayout','serving:online',NULL,NULL,'','\0',65,1,1,'\0'),
    (1064,97,1,'度量管理','icon_huabanfuben1','measure','atlas/measure','Measure','BaseLayout','atlas:measure',NULL,NULL,'\0','\0',71,1,1,'\0'),
    (1065,97,1,'图谱可视化','icon_huabanfuben1','graphvisual','atlas/graphVisual','AtlasGraphVisual','BaseLayout',NULL,NULL,NULL,'\0','\0',72,1,1,'\0'),
    (1066,97,1,'图谱列表','icon_huabanfuben1','graph','atlas/graphList','AtlasGraph','BaseLayout',NULL,NULL,NULL,'\0','\0',73,1,1,'\0'),
    (1067,10,1,'图像语义分割',NULL,'datasets/segmentation/:datasetId','dataset/annotate','SegmentationDataset','DatasetLayout',NULL,NULL,'{\"test\": 1}','','\0',19,1,1,'\0'),
    (1068,10,1,'图像语义分割',NULL,'datasets/segmentation/:datasetId/file/:fileId','dataset/annotate','SegmentationDatasetFile','DatasetLayout',NULL,NULL,NULL,'','\0',18,1,1,'\0'),
    (1069,10,1,'医学影像阅读','beauty','datasets/medical/viewer/:medicalId','dataset/medical/viewer','DatasetMedicalViewer','FullpageLayout',NULL,NULL,NULL,'','\0',999,1,1,'\0'),
    (1070,10,1,'数据集管理','shujuguanli','datasets','dataset/fork','DatasetFork','BaseLayout',NULL,NULL,NULL,'\0','\0',17,1,1,'\0'),
    (1071,10,1,'医疗影像数据集',NULL,'datasets/medical','dataset/medical/list','DatasetMedical','BaseLayout',NULL,NULL,NULL,'','\0',25,1,1,'\0'),
    (1072,10,1,'数据集场景选择',NULL,'datasets/entrance','dataset/entrance','Entrance','BaseLayout',NULL,NULL,NULL,'','\0',20,1,1,'\0'),
    (1073,10,1,'文本分类',NULL,'datasets/textclassify/:datasetId','dataset/nlp/textClassify','TextClassify','DetailLayout','',NULL,NULL,'','\0',26,1,1,'\0'),
    (1074,10,1,'文本标注',NULL,'datasets/text/annotation/:datasetId','dataset/nlp/annotation','TextAnnotation','DetailLayout',NULL,NULL,NULL,'','\0',27,1,1,'\0'),
    (1075,10,1,'导入表格',NULL,'datasets/table/import','dataset/tableImport','TableImport','DetailLayout',NULL,NULL,'{}','','\0',999,1,1,'\0'),
    (1076,90,1,'用户组管理','tuanduiguanli-tuanduiguanli','userGroup','system/userGroup','UserGroup','BaseLayout','system:userGroup',NULL,'{}','\0','\0',91,3,3,'\0'),
    (1077,90,1,'权限管理','fuwuguanli','authCode','system/authCode','AuthCode','BaseLayout','system:authCode',NULL,'{}','\0','\0',92,1,3,'\0'),
    (1078,10,1,'文本数据集',NULL,'datasets/text/list/:datasetId','dataset/nlp/list','TextList','DetailLayout',NULL,NULL,'{}','','\0',999,1,1,'\0'),
    (1079,10,1,'音频数据集',NULL,'datasets/audio/list/:datasetId','dataset/audio/list','AudioList','DetailLayout',NULL,NULL,'{}','','\0',999,1,1,'\0'),
    (1080,10,1,'音频标注',NULL,'datasets/audio/annotation/:datasetId','dataset/audio/annotation','AudioAnnotation','DetailLayout',NULL,NULL,'{}','','\0',999,1,1,'\0'),
    (1081,10,1,'自定义数据集',NULL,'datasets/custom/:datasetId','dataset/custom','CustomList','DetailLayout',NULL,NULL,'{}','','\0',999,1,1,'\0'),
    (1084,90,1,'资源规格管理','xunlianzhunbei','resources','system/resources','Resources','BaseLayout','system:specs',NULL,NULL,'\0','\0',999,NULL,NULL,'\0')
    ON DUPLICATE KEY UPDATE `id`=VALUES(`id`)
                          ,`pid`=VALUES(`pid`)
                          ,`type`=VALUES(`type`)
                          ,`name`=VALUES(`name`)
                          ,`icon`=VALUES(`icon`)
                          ,`path`=VALUES(`path`)
                          ,`component`=VALUES(`component`)
                          ,`component_name`=VALUES(`component_name`)
                          ,`layout`=VALUES(`layout`)
                          ,`permission`=VALUES(`permission`)
                          ,`hidden`=VALUES(`hidden`)
                          ,`cache`=VALUES(`cache`)
                          ,`sort`=VALUES(`sort`)
                          ,`create_user_id`=VALUES(`create_user_id`)
                          ,`update_user_id`=VALUES(`update_user_id`)
                          ,`deleted`=VALUES(`deleted`)
                          ,`back_to`=VALUES(`back_to`)
                          ,`ext_config`=VALUES(`ext_config`);

-- 新增算法用途预置数据
    insert into `pt_auxiliary_info` (`origin_user_id`,`type`,`aux_info`) values (0,'algorithem_usage','模型优化');

-- 初始化模型后缀名表pt_model_suffix
    INSERT INTO `pt_model_suffix`(`model_type`) VALUES (1);
    INSERT INTO `pt_model_suffix`(`model_type`, `model_suffix`) VALUES (2, '.pb');
    INSERT INTO `pt_model_suffix`(`model_type`, `model_suffix`) VALUES (3, '.h5');
    INSERT INTO `pt_model_suffix`(`model_type`, `model_suffix`) VALUES (4, '.caffeprototxt');
    INSERT INTO `pt_model_suffix`(`model_type`) VALUES (5);
    INSERT INTO `pt_model_suffix`(`model_type`) VALUES (6);
    INSERT INTO `pt_model_suffix`(`model_type`) VALUES (7);
    INSERT INTO `pt_model_suffix`(`model_type`, `model_suffix`) VALUES (8, '.pth');
    INSERT INTO `pt_model_suffix`(`model_type`, `model_suffix`) VALUES (9, '.pb');
    INSERT INTO `pt_model_suffix`(`model_type`, `model_suffix`) VALUES (10, '.ckpt');
    INSERT INTO `pt_model_suffix`(`model_type`, `model_suffix`) VALUES (11, '.pkt');
    INSERT INTO `pt_model_suffix`(`model_type`, `model_suffix`) VALUES (12, '.pt');
    INSERT INTO `pt_model_suffix`(`model_type`, `model_suffix`) VALUES (13, '.h5');
    INSERT INTO `pt_model_suffix`(`model_type`, `model_suffix`) VALUES (14, '.caffemodel');
    INSERT INTO `pt_model_suffix`(`model_type`, `model_suffix`) VALUES (15, '.params');
    INSERT INTO `pt_model_suffix`(`model_type`, `model_suffix`) VALUES (16, '.json');

-- 初始化模型格式表pt_model_type
    INSERT INTO `pt_model_type`(`frame_type`, `model_type`) VALUES (1, '1,17');
    INSERT INTO `pt_model_type`(`frame_type`, `model_type`) VALUES (2, '1,9,10');
    INSERT INTO `pt_model_type`(`frame_type`, `model_type`) VALUES (3, '8,11,12');
    INSERT INTO `pt_model_type`(`frame_type`, `model_type`) VALUES (4, '1,13');
    INSERT INTO `pt_model_type`(`frame_type`, `model_type`) VALUES (5, '4,14');
    INSERT INTO `pt_model_type`(`frame_type`, `model_type`) VALUES (6, '6');
    INSERT INTO `pt_model_type`(`frame_type`, `model_type`) VALUES (7, '15,16');


-- 初始化资源规格
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('1CPU2GB内存',0,1,1,0,2048,500);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('2CPU4GB内存',0,1,2,0,2048,500);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('1CPU2GB内存 1GPU',1,1,1,1,2048,2048);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('1CPU2GB内存 2GPU',1,1,1,2,2048,2048);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('2CPU4GB内存 1GPU',1,1,2,1,4096,2048);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('2CPU4GB内存 2GPU',1,1,2,2,4096,2048);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('4CPU16GB内存 2GPU',1,1,16,2,16000,4096);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('4CPU16GB内存 4GPU',1,1,16,4,16000,4096);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('8CPU64GB内存 2GPU',1,1,8,2,64000,4096);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('16CPU128GB内存 4GPU',1,1,16,4,128000,50000);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('32CPU256GB内存 8GPU',1,1,32,8,256000,50000);
-- 初始化train资源规格
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('1CPU2GB内存',0,2,1,0,2048,500);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('2CPU4GB内存',0,2,2,0,2048,500);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('1CPU2GB内存 1GPU',1,2,1,1,2048,2048);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('1CPU2GB内存 2GPU',1,2,1,2,2048,2048);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('2CPU4GB内存 1GPU',1,2,2,1,4096,2048);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('2CPU4GB内存 2GPU',1,2,2,2,4096,2048);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('4CPU16GB内存 2GPU',1,2,16,2,16000,4096);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('4CPU16GB内存 4GPU',1,2,16,4,16000,4096);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('8CPU64GB内存 2GPU',1,2,8,2,64000,4096);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('16CPU128GB内存 4GPU',1,2,16,4,128000,50000);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('32CPU256GB内存 8GPU',1,2,32,8,256000,50000);
-- 初始化serving资源规格
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('1CPU2GB内存',0,3,1,0,2048,500);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('2CPU4GB内存',0,3,2,0,2048,500);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('1CPU2GB内存 1GPU',1,3,1,1,2048,2048);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('1CPU2GB内存 2GPU',1,3,1,2,2048,2048);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('2CPU4GB内存 1GPU',1,3,2,1,4096,2048);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('2CPU4GB内存 2GPU',1,3,2,2,4096,2048);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('4CPU16GB内存 2GPU',1,3,16,2,16000,4096);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('4CPU16GB内存 4GPU',1,3,16,4,16000,4096);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('8CPU64GB内存 2GPU',1,3,8,2,64000,4096);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('16CPU128GB内存 4GPU',1,3,16,4,128000,50000);
    INSERT INTO resource_specs(specs_name,resources_pool_type,module,cpu_num,gpu_num,mem_num,workspace_request) value
        ('32CPU256GB内存 8GPU',1,3,32,8,256000,50000);

-- 初始化默认角色
    INSERT INTO `role`(`id`, `name`, `permission`) VALUES (1, '管理员', 'admin')
                                                        ,(2, '注册用户', 'register')
    ON DUPLICATE KEY UPDATE `id`=VALUES(`id`),`name`=VALUES(`name`),`permission`=VALUES(`permission`)
    ;

-- 初始化 admin 和 注册用户 菜单
    INSERT INTO `roles_menus` (`role_id`, `menu_id`) SELECT 1, `id` FROM `menu`
    ON DUPLICATE KEY UPDATE `role_id`=VALUES(`role_id`),`menu_id`=VALUES(`menu_id`);
    INSERT INTO `roles_menus` (`role_id`, `menu_id`) SELECT 2, `id` FROM `menu` WHERE `id` != 90 and pid != 90
    ON DUPLICATE KEY UPDATE `role_id`=VALUES(`role_id`),`menu_id`=VALUES(`menu_id`);

    INSERT INTO `oauth_client_details` (`client_id`, `resource_ids`, `client_secret`, `scope`, `authorized_grant_types`, `web_server_redirect_uri`, `authorities`, `access_token_validity`, `refresh_token_validity`, `additional_information`, `autoapprove`) VALUES
    ('dubhe-client', NULL, '$2a$10$RUYBRsyV2jpG7pvg/VNus.YHVebzfRen3RGeDe1LVEIJeHYe2F1YK', 'all', 'authorization_code,password,refresh_token', 'http://localhost:8866/oauth/callback', NULL, 3600, 2592000, NULL, NULL);

update user set password='$2a$10$VhAWNoUtpJKr000UYmfMee4SONBXJuRWGus64bmomyFKEo4kiwHve' where id =1;

    IF t_error = 1 THEN
        ROLLBACK;
    ELSE
        COMMIT;
    END IF;
    SELECT t_error;
END //
DELIMITER ;
CALL fourthEditionProc();
