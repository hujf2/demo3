# demo3
demo3
01_分贝通研发数据库规范 V1.0
转至元数据结尾
由 陶明凯创建, 最后修改于16分钟以前转至元数据起始
00 规范说明
------------------------------------------------------------------------------------------------
01 建表模板
------------------------------------------------------------------------------------------------
02 DB 规范
------------------------------------------------------------------------------------------------
03 ORM 规范
00 规范说明
此文档为必须遵守的最小集，否则会引发数据库不稳定或数据库故障。

------------------------------------------------------------------------------------------------
01 建表模板
# 基地车 Vehicle 创建表 DDL SQL 【落地策略：DMS校验】
CREATE TABLE `su_user`
(
    `id`               VARCHAR(32) NOT NULL COMMENT '主键', -- 百万量级情况下，考虑与BIGINT空间、性能差别不大，为了保持ID扩展性，类型为 VARCHAR(32)
  
    `name`             VARCHAR(32) NOT NULL COMMENT '名称',
    `login_name`       VARCHAR(16) NOT NULL COMMENT '登录名',
    `code`             VARCHAR(32)      DEFAULT '' COMMENT '编码',
    `pwd`              VARCHAR(32) NOT NULL COMMENT '密码',
    `salt`             VARCHAR(64)      DEFAULT '' COMMENT '加密盐',
    `mobile`           VARCHAR(11)      DEFAULT '' COMMENT '手机号',
  
    `status_val`       INT UNSIGNED     DEFAULT 0 COMMENT '用户状态值:1000=启用,1001=停用',
    `status_code`      VARCHAR(16)      DEFAULT '' COMMENT '用户状态编码:字典',
  
    `last_access_time` DATETIME(3)      DEFAULT NULL COMMENT '最近访问时间',
    `total_amount`     DECIMAL(19, 4)   DEFAULT NULL COMMENT '账户余额（符合 GAAP 原则）',
    `remark`           VARCHAR(256)     DEFAULT '' COMMENT '备注',
        
    `create_at`        BIGINT UNSIGNED  DEFAULT 0  COMMENT '创建时间戳',
    `create_time`      DATETIME DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间', -- create_time 和 create_at 可二选一，默认二选二
    `create_by`        VARCHAR(32)      DEFAULT '' COMMENT '创建人ID',
    `create_name`      VARCHAR(32)      DEFAULT '' COMMENT '创建人名称',
    `update_at`        BIGINT UNSIGNED  DEFAULT 0  COMMENT '更新时间戳',
    `update_time`      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', -- update_time 和 update_at 可二选一，默认二选二
    `update_by`        VARCHAR(32)      DEFAULT '' COMMENT '更新人ID',
    `update_name`      VARCHAR(32)      DEFAULT '' COMMENT '更新人名称',
    `is_del`           TINYINT UNSIGNED DEFAULT 0  COMMENT '是否删除',
    INDEX (`is_del`), -- 默认创建 `is_del` 普通索引
    PRIMARY KEY (`id`)
) ENGINE = INNODB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='用户';
表基础字段（10个）：id、create_at、create_time、create_by、create_name、update_at、update_time、update_by、update_name、is_del，默认都会由基地车框架进行数据设置。

create_at & create_time & update_at & update_time：无论是否有值，都会由基地车框架强制进行当前时间覆盖。

create_by & create_name & update_by & update_name：有值，则基地车框架不会强制覆盖；无值，则由基地车框架获取默认的当前用户信息设置。

------------------------------------------------------------------------------------------------
02 DB 规范
02-01【强制】库名、表名全部使用小写字母、表名及字段名不要使用MySQL的关键字，多个单词之间可以下划线连接。

    说明：为了避免不同操作系统之间的大小写敏感问题以及增加代码的可读性和可维护性；使用关键字会导致语法错误或查询结果不正确。

    示例：create table su_user(id varchar(32) not null comment '主键', primary key(id))

02-02【强制】表及字段需要加注释，如果修改字段含义或者对字段表示的状态追加时，需要及时更新字段注释。

   说明：为了以后注释内容成为分类分级的标准。

   示例：ALTER TABLE su_user add column name varchar(20) comment '姓名'; // 修改表也需要带注释

02-03【强制】新增库、表均使用 UTF8MB4 字符集。

   说明：与UTF8相比，可以多存储表情，比如：昵称中的特殊表情、富文本编辑等。 

   示例：create table su_user(id varchar(32) not null comment '主键', primary key(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

02-04【强制】所有表排序规则统一为 utf8mb4_unicode_ci 或者 utf8mb4_general_ci。

   说明：utf8mb4对应的排序字符集有utf8mb4_unicode_ci、utf8mb4_general_ci。

   示例：create table su_user(id varchar(32) not null comment '主键', primary key(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

02-05【强制】所有表必须要包含主键ID，类型为 VARCHAR(32) 且禁止自增，采用有序递增的雪花算法 UUID。

    说明：自增主键8.0之前存在回溯问题；自增只能在当前实例保证唯一，不能保证全局唯一。使用有序递增的雪花算法 UUID 可以保证全局唯一；通过本地生成，性能较快；百万量级情况下，考虑与BIGINT 空间、性能差别不大，为了保持ID扩展性，类型为 VARCHAR(32)。

    示例：create table su_user(id varchar(32) not null comment '主键', primary key(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

02-06【强制】新建表必须有一个二级索引，对于固定数量的字典表或者数据量极少的表可考虑除外。

   说明：为了防止建表的时候只有主键，忘记创建合适的索引导致查询慢。

   示例：create table su_user(id varchar(32) not null comment '主键', name varchar(20) comment '姓名', primary key(id), key idx_name(name)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

02-07【强制】不允许在数据库中存放图片、二进制大文件等大对象。

   说明：会占用大量空间；严重影响数据性能；备份恢复、重启时间长。

   示例：数据库存储图片的地址，把图片存放在文件系统中。

02-08【强制】临时表/中间表命名以 tmp_ 开头，备份表以 bak_ 开头，对临时表和备份表应定期清理。

   说明：根据表名字就知道表的作用，提高沟通效率；定期清理减少磁盘使用率。

   示例：create table tmp_user(id varchar(32) not null comment '主键', primary key(id));

02-09【强制】字段数据包含小数时需使用 DECIMAl 替代 FlOAT/DOUBLE 类型，存储日期+时间的数据时需使用 DATETIME 类型。 

   说明：float 和 double 都存在精度损失的问题，很可能在比较值的时候，得到不正确的结果。

   示例：create table su_user(id varchar(32) not null comment '主键', cost decimal(20,3) default 0.000 not null comment '花费',create_time datetime DEFAULT NULL COMMENT '创建时间',primary key(id));

02-10【强制】使用 VARCHAR 替代 CHAR 类型。

   说明：连表查询的时候，2张表的条件字段类型不一样，不会使用索引；统一字符串类型。

   示例：create table su_user(id varchar(32) not null comment '主键', name varchar(20) comment '姓名', primary key(id));

02-11【强制】字段存储非负数值时增加 UNSIGNED 属性。

   说明：可以确保该字段只能存储非负数值，防止数据出现负数的情况；同时可以节约存储空间，因为无需存储符号位；可以提高查询效率，因为无需再进行符号位的判断。

   示例：create table su_user(id int UNSIGNED  not null comment '主键', primary key(id));

02-12【强制】禁止对列设置单独的字符集。

   说明：统一规定字符集的粒度到表级别。

   示例：create table su_user(id varchar(32) not null comment '主键', name varchar(20) comment '姓名', primary key(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

02-13【强制】Nacos和本地的数据库配置文件中不存储数据库明文密码。目前 finhub-kms 未完全覆盖，待推广使用。 陶明凯

   说明：所有配置文件中数据库密码使用需要加解密，加解密规则给到各部门 leader。

02-14【强制】表上不要存在重复索引。

   说明：innodb 索引即数据，造成空间浪费。

   示例：删除重复索引，已有的索引基本都能被使用到。

02-15【强制】禁止使用视图。

02-16【强制】禁止使用存储过程。

   说明：存储过程容易将业务与数据库耦合，增加系统复杂性，难以调试和扩展，更没有移植性。

   示例：不使用存储过程。

02-17【强制】 禁止使用触发器。    

   说明：触发器容易将业务与数据库耦合，增加系统复杂性，难以调试和扩展，更没有移植性。

   示例：不使用触发器。

02-18【强制】 避免使用 select * 语句。

   说明：SElECT语句应只获取需要的字段，不建议使用SElECT *，增加很多不必要的消耗（cpu、io、内存、网络带宽），且降低了使用覆盖索引的可能性。

   示例：select demo_id,demo_name from t_demo

02-19【强制】insert 时需要写列全名。

   说明：为避免数据表出现结构上变化时造成程序错误。

   示例：insert into t_demo(demo_id,demo_name) values(1,’MySQL’);insert into t_demo(demo_name,demo_id) values(‘MySQL’,1)

02-20【强制】避免出现大事务。

   说明：执行时间过长，占用资源过多；造成主从同步延迟。

   示例：如需要对表清空，不要采用delete方式，要采用drop或truncate方式；truncate table t_demo;

             DElETE，UPDATE后必须带WHERE条件，且不能是 1=1 等这样的条件；

             UPDATE 多表时 set 的列，必须指定表的前缀；

             如需要对表大量数据进行删除，需要拆分成小批次执行，防止大事务出现造成对表的锁定时间过长及从库回放堵塞引发延迟问题

               delete from t_demo where demo_id>10000 and demo_id<20000;
               delete from t_demo where demo_id>=20000 and demo_id<30000;
              …..

02-21【强制】 避免在应用数据库执行统计分析类的查询。

   说明：统计分析类的查询往往需要读取大量数据、进行复杂计算，需要消耗很多数据库服务器资源，很容易造成数据库服务器响应缓慢，尽量避免在应用服务执行这类查询；与事务型的查询相比，统计分析类的查询需要完全不同的优化技术，需要不同的数据结构、建不同的索引、甚至需选用不同的MySQL存储引擎。建议使用专门用于数据分析服务的数据仓库。

   示例：不在数据库上执行统计分析类sql。

02-22【强制】查询条件中数据类型需与表定义字段数据类型保持一致。 

   说明：查询条件中的常量类型要与表中定义的类型一致，避免涉及隐式类型转换，使用不到索引。

   示例：select demo_id, demo_name from t_demo where demo_id='10'; // 可能使用不到索引

02-23【强制】使用 ISNULL()来判断是否为 NULL 值。

   说明：NULL 与任何值的直接比较都为 NULL；当某一列的值全是 NULL 时，count(col)的返回结果为 0，sum(col)的返回结果为NULL，因此使用 sum()时需注意空指针问题。

   示例：SELECT IFNULL(SUM(column), 0) FROM table;

02-24【强制】对于数据库中表记录的查询和变更，只要涉及多个表，都需要在列名前加表的别名（或表名）进行限定。 

   说明：对多表进行查询记录、更新记录、删除记录时，如果对操作列没有限定表的别名（或表名），并且操作列在多个表中存在时，就会抛异常。

   示例：select a.id, b.name from a, b on a.id =b.id;

02-25【强制】in 操作能避免则避免，若实在避免不了，需要仔细评估 in 后边的集合元素数量，控制在 1000 个之内。    

   说明：in后面的集合元素多，会导致查询语句的性能变差，会消耗大量的内存和CPU资源。

   示例：控制在1000个之内。

02-26【强制】禁止使用 force/use index(idx_name)。

   说明：随着表大小的增加，force 的索引可能不是最优的索引。

   示例：不要使用force/use index。

02-28【强制】BLOB, TEXT, GEOMETRY，JSON 类型不允许设置默认值。

   说明：mysql本身不允许。

   示例：不使用这几个类型。

02-29【强制】SQL连表查询：大表连表禁止，连表最多3个。大表：大于100w行或者空间大于10G 。

   说明：连接的表过多，会导致查询语句性能变差，会消耗大量的内存和CPU资源。

   示例：不要使用连表查询。

现有大表
商旅 hotel fbt_hotel_rate_plan
商旅 fenbeitong_hotel fenbeitong_room_price_status
商旅 fs_hotel_data fs_screen_min_price_record_12
商旅 hotel ctrip_comment_media
商旅 hotel tmpcoldback_ctrip_hotel_comment_info
商旅 hotel room_type_picture_copy
商旅 hotel hotel_picture
商旅 db_car_order car_order
商旅 hotel meituan_hotel_image
商旅 hotel tongcheng_hotel_room
商旅 fb_car car_journey
商旅 db_noc tb_mall_order_product_snapshot
商城 fb_mall jd_goods
  
费控 saas order_cost_info_detail
费控 saas order_cost_info
费控 saas act_hi_identitylink
  
平台 usercenter sys_auth_role_rel_privilege_v2
平台 fb-meta mt_layout_items
平台 usercenter operate_log
平台 usercenter employee_personal_savings_order_history
平台 db_supplier_gateway tb_profile_data_02
平台 usercenter login_info_track_backup
  
支付 db_pay cashier_order_cost_attribution
支付 db_pay cashier_order_company_extend
支付 db_pay tb_bank_acct_flow
支付 db_operation tb_order_operation_record_02
  
内部系统 stereo_settlement bill_overdue_bill
内部系统 stereo bak_bill_detail
内部系统 stereo_settlement bill_invoice_record_mapping
02-30【强制】在PostgreSQL中，创建索引务必采用 在线模式。

   说明：不堵塞其他会话对被创建索引表。

   示例：CREATE INDEX CONCURRENTLY idx_name ON tbl_name (col_name,....);    

02-31【强制】在PostgreSQL中，禁止将普通索引命名为 xxx_pkey。

   说明：因为表名_pkey 为默认的 主键名称。

   示例：CREATE INDEX CONCURRENTLY idx_name ON a(name);    

------------------------------------------------------------------------------------------------
03 ORM 规范
03-01【强制】在表查询中，一律不要使用 * 作为查询的字段列表，需要哪些字段必须明确写明。

  说明：1）增加查询分析器解析成本。2）增减字段容易与 resultMap 配置不一致。3）无用字段增加网络消耗，尤其是 text 类型的字段。

03-02【强制】不要用 resultClass，当返回参数，即使所有类属性名与数据库字段一一对应，也需要定义<resultMap>；反过来，每一个表也必然有一个<resultMap>与之对应。基地车生成代码mapper.xml中默认都有定义<resultMap>。

  说明：配置映射关系，使字段与 DO 类解耦，方便维护。 

03-03【强制】MyBatis sql.xml 配置参数使用：#{}，#param# 不要使用${} 此种方式容易出现 SQL 注入。

03-04【强制】更新数据表记录时，必须同时更新记录对应的 update_time 字段值为当前时间，基地车默认已统一处理。
