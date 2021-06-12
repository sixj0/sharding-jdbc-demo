# sharding-jdbc-demo

> 采⽤Sharding-JDBC实现c_order表分库分表+读写分离 
>
> 1. 基于user_id对c_order表进⾏数据分⽚
> 2. 分别对master1和master2搭建⼀主⼆从架构 
> 3. 基于master1和master2主从集群实现读写分离


### MySQL环境搭建

<img src="http://qiniu.yiyuansucai.cn/Sharding-jdbc集群架构.png" alt="Sharding-jdbc集群架构" style="zoom:67%;" />

#### MySQL安装

分别在六台服务上安装MySQL服务，安装过程：

> 1. 因为CentOS7默认安装mariadb数据库，存中文时可能会有意想不到的问题，先把它删掉就好了，使用命令：
>
>    ```shell
>    yum remove mariadb-libs.x86_64
>    ```
>
> 2. 下载MySql安装包
>
>    ```shell
>    wget http://dev.mysql.com/get/mysql-community-release-el7-5.noarch.rpm
>    ```
>
> 3. **安装软件包**
>
>    ```shell
>    rpm -ivh mysql-community-release-el7-5.noarch.rpm
>    ```
>
> 4. **安装MySQL服务程序**
>
>    ```shell
>    yum install mysql-community-server
>    ```
>
> 5. **完成后重启MySQL服务**
>
>    ```shell
>    service mysqld restart
>    ```
>
>    此时，MySQL就已经成功安装在服务器上。
>
> 6. 修改密码
>
>    输入mysql -uroot -p 后这次我们直接不用密码就进去了，没有密码肯定是不安全的，现在就只要修改密码就好了。
>
>    ```mysql
>    SET PASSWORD = PASSWORD('root123456');
>    ```
>
> 7. 设置开机自启动,可以在/etc/rc.local文件中加上如下MySQL的启动命令，例如：
>
>    ```mysql
>    /etc/init.d/mysql start
>    ```

安装完成之后尝试使用Navicat进行连接，如果拒绝访问，可以暂时先关闭掉防火墙：

> CentOS 7.0默认使用的是firewall作为防火墙
>
> 查看防火墙状态
>
> ```shell
> firewall-cmd --state
> ```
>
> 停止firewall
>
> ```shell
> systemctl stop firewalld.service
> ```
>
> 禁止firewall开机启动
>
> ```shell
> systemctl disable firewalld.service 
> ```

如果关闭防火墙之后，连接数据库报错：1130 - Host XXX is not allowed to connect to this MySQL server ，说明MySQL不支持远程连接，修改配置：

> 1. **登陆服务器，进入数据库**
>
>    ```shell
>    mysql -uroot -p密码
>    ```
>
> 2. **设置权限**
>
>    ```mysql
>    grant all privileges on *.* to root@"%" identified by "root123456";
>    ```
>
> 3. **使配置生效**
>    localhost修改完成后执行以下命令使配置立即生效。
>
>    ```mysql
>    flush privileges;
>    ```
>
>    已成功修改，可以通过navicat(或者其他工具)远程连接了

#### MySQL主从配置

##### Master节点

##### 使用vi /etc/my.cnf命令修改Master配置文件

```properties
#bin_log配置 
log_bin=mysql-bin 
#服务器ID,保证每台服务器id不重复
server-id=1 
sync-binlog=1 
binlog-ignore-db=information_schema 
binlog-ignore-db=mysql 
binlog-ignore-db=performance_schema 
binlog-ignore-db=sys 
#relay_log配置 
relay_log=mysql-relay-bin 
log_slave_updates=1 
relay_log_purge=0
```

##### 重启服务

```
systemctl restart mysqld
```

##### 主库给从库授权

登录MySQL，在MySQL命令行执行如下命令：

```mysql
mysql> grant replication slave on *.* to root@'%' identified by '密码'; 
mysql> grant all privileges on *.* to root@'%' identified by '密码'; 
mysql> flush privileges; 
//查看主库状态信息，例如master_log_file='mysql-bin.000007',master_log_pos=154 
mysql> show master status;
```

#### Slave节点

修改Slave的MySQL配置文件my.cnf，两台Slave的server-id分别设置为2和3

```properties
#bin_log配置 
log_bin=mysql-bin 
#服务器ID,保证每台服务器id不重复
server-id=2 
sync-binlog=1 
binlog-ignore-db=information_schema 
binlog-ignore-db=mysql 
binlog-ignore-db=performance_schema 
binlog-ignore-db=sys 
#relay_log配置 
relay_log=mysql-relay-bin 
log_slave_updates=1 
relay_log_purge=0 
read_only=1
```

##### 重启服务

```
systemctl restart mysqld
```

##### 开启同步

登录MySQL，在Slave节点的MySQL命令行执行同步操作，例如下面命令（注意参数与上面show master status操作显示的参数一致）：

```mysql
change master to master_host='10.211.55.14',master_port=3306,master_user='root',master_password ='root123456',master_log_file='mysql-bin.000001',master_log_pos=120;

start slave; // 开启同步
```

### 配置半同步复制

#### Master节点

登录MySQL，在MySQL命令行执行下面命令安装插件

```shell
install plugin rpl_semi_sync_master soname 'semisync_master.so';
show variables like '%semi%';
```

使用`vi /etc/my.cnf`，修改MySQL配置文件

```properties
# 自动开启半同步复制 
rpl_semi_sync_master_enabled=ON 
rpl_semi_sync_master_timeout=1000
```

重启MySQL服务

```
systemctl restart mysqld
```

#### Slave节点

两台Slave节点都执行以下步骤。

登录MySQL，在MySQL命令行执行下面命令安装插件

```mysql
install plugin rpl_semi_sync_slave soname 'semisync_slave.so';
```

使用`vi /etc/my.cnf`，修改MySQL配置文件

```properties
# 自动开启半同步复制 
rpl_semi_sync_slave_enabled=ON
```

重启服务

```
systemctl restart mysqld
```

#### 测试半同步状态

首先通过MySQL命令行检查参数的方式，查看半同步是否开启。

```
show variables like '%semi%';
```

然后通过MySQL日志再次确认。

```
cat /var/log/mysqld.log
```

可以看到日志中已经启动半同步信息，例如：

```
Start semi-sync binlog_dump to slave (server_id: 2), pos(mysql-bin.000005, 154)
```



#### 创建表

操作主库创建数据库和订单表

创建数据库：`test_db`：

```mysql
create database test_db;
```

创建订单表`c_order`：

```mysql
use test_db;

CREATE TABLE `c_order`(
 `id` bigint(20) NOT NULL AUTO_INCREMENT,
 `is_del` bit(1) NOT NULL DEFAULT 0 COMMENT '是否被删除',
 `user_id` int(11) NOT NULL COMMENT '⽤户id',
 `company_id` int(11) NOT NULL COMMENT '公司id',
 `publish_user_id` int(11) NOT NULL COMMENT 'B端⽤户id',
 `position_id` int(11) NOT NULL COMMENT '职位ID',
 `resume_type` int(2) NOT NULL DEFAULT 0 COMMENT '简历类型：0附件 1在线',
 `status` varchar(256) NOT NULL COMMENT '投递状态 投递状态 WAIT-待处理 AUTO_FILTER-⾃动过滤 PREPARE_CONTACT-待沟通 REFUSE-拒绝 ARRANGE_INTERVIEW-通知⾯试',
 `create_time` datetime NOT NULL COMMENT '创建时间',
 `update_time` datetime NOT NULL COMMENT '处理时间',
 PRIMARY KEY (`id`),
 KEY `index_userId_positionId` (`user_id`, `position_id`),
 KEY `idx_userId_operateTime` (`user_id`, `update_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

从库会进行复制，可以查询到在主库中创建的订单表：

```mysql
use test_db;
SHOW TABLES;
```



### 搭建项目

创建项目`sharding-jdbc-demo`

源码地址：https://github.com/sixj0/sharding-jdbc-demo

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project  xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.sixj</groupId>
    <artifactId>sharding-jdbc-demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>sharding-jdbc-demo</name>
    <description>Sharding-JDBC实现分库分表与读写分离</description>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
        <java.version>1.8</java.version>
        <spring-cloud.version>Dalston.SR1</spring-cloud.version>
        <spring-boot.version>2.2.5.RELEASE</spring-boot.version>
        <mysql-connector.version>5.1.48</mysql-connector.version>
        <shardingsphere.version>4.1.0</shardingsphere.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
            <version>${spring-boot.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
            <version>${spring-boot.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <version>${spring-boot.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <version>${mysql-connector.version}</version>
        </dependency>
        <dependency>
            <groupId>org.apache.shardingsphere</groupId>
            <artifactId>sharding-jdbc-spring-boot-starter</artifactId>
            <version>${shardingsphere.version}</version>
        </dependency>
    </dependencies>


    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.8.1</version>
                <configuration>
                    <source>1.8</source>
                    <target>1.8</target>
                    <testSource>1.8</testSource>
                    <testTarget>1.8</testTarget>
                </configuration>
            </plugin>
        </plugins>
    </build>



</project>
```

配置信息：

```properties
# 六个数据库的数据源信息
spring.shardingsphere.datasource.names=master1,slave1,slave2,master2,slave3,slave4

spring.shardingsphere.datasource.master1.type=com.zaxxer.hikari.HikariDataSource
spring.shardingsphere.datasource.master1.driver-class-name=com.mysql.jdbc.Driver
spring.shardingsphere.datasource.master1.jdbc-url=jdbc:mysql://10.211.55.14:3306/test_db?useUnicode=true&characterEncoding=utf-8&useSSL=false
spring.shardingsphere.datasource.master1.username=root
spring.shardingsphere.datasource.master1.password=root123456

spring.shardingsphere.datasource.slave1.type=com.zaxxer.hikari.HikariDataSource
spring.shardingsphere.datasource.slave1.driver-class-name=com.mysql.jdbc.Driver
spring.shardingsphere.datasource.slave1.jdbc-url=jdbc:mysql://10.211.55.16:3306/test_db?useSSL=false
spring.shardingsphere.datasource.slave1.username=root
spring.shardingsphere.datasource.slave1.password=root123456

spring.shardingsphere.datasource.slave2.type=com.zaxxer.hikari.HikariDataSource
spring.shardingsphere.datasource.slave2.driver-class-name=com.mysql.jdbc.Driver
spring.shardingsphere.datasource.slave2.jdbc-url=jdbc:mysql://10.211.55.17:3306/test_db?useSSL=false
spring.shardingsphere.datasource.slave2.username=root
spring.shardingsphere.datasource.slave2.password=root123456

spring.shardingsphere.datasource.master2.type=com.zaxxer.hikari.HikariDataSource
spring.shardingsphere.datasource.master2.driver-class-name=com.mysql.jdbc.Driver
spring.shardingsphere.datasource.master2.jdbc-url=jdbc:mysql://10.211.55.15:3306/test_db?useUnicode=true&characterEncoding=utf-8&useSSL=false
spring.shardingsphere.datasource.master2.username=root
spring.shardingsphere.datasource.master2.password=root123456

spring.shardingsphere.datasource.slave3.type=com.zaxxer.hikari.HikariDataSource
spring.shardingsphere.datasource.slave3.driver-class-name=com.mysql.jdbc.Driver
spring.shardingsphere.datasource.slave3.jdbc-url=jdbc:mysql://10.211.55.18:3306/test_db?useSSL=false
spring.shardingsphere.datasource.slave3.username=root
spring.shardingsphere.datasource.slave3.password=root123456

spring.shardingsphere.datasource.slave4.type=com.zaxxer.hikari.HikariDataSource
spring.shardingsphere.datasource.slave4.driver-class-name=com.mysql.jdbc.Driver
spring.shardingsphere.datasource.slave4.jdbc-url=jdbc:mysql://10.211.55.19:3306/test_db?useSSL=false
spring.shardingsphere.datasource.slave4.username=root
spring.shardingsphere.datasource.slave4.password=root123456

#id 使用雪花算法
spring.shardingsphere.sharding.tables.c_order.key-generator.column=id
spring.shardingsphere.sharding.tables.c_order.key-generator.type=SNOWFLAKE

#sharding-database-table    基于user_id对c_order表进⾏数据分⽚
spring.shardingsphere.sharding.tables.c_order.database-strategy.inline.sharding-column=user_id
spring.shardingsphere.sharding.tables.c_order.database-strategy.inline.algorithm-expression=master$->{user_id % 2 + 1}
spring.shardingsphere.sharding.tables.c_order.actual-data-nodes=master$->{1..2}.c_order


#master-slave   基于master1和master2主从集群实现读写分离
spring.shardingsphere.sharding.master-slave-rules.master1.master-data-source-name=master1
spring.shardingsphere.sharding.master-slave-rules.master1.slave-data-source-names=slave1,slave2
spring.shardingsphere.sharding.master-slave-rules.master2.master-data-source-name=master2
spring.shardingsphere.sharding.master-slave-rules.master2.slave-data-source-names=slave3,slave4

#多个从库的时候使用负载均衡
spring.shardingsphere.masterslave.load-balance-algorithm-type=ROUND_ROBIN

# 打印执行sql
spring.shardingsphere.props.sql.show=true
```

实体类：

```java
@Entity
@Table(name = "c_order")
public class COrder implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "is_del")
    private Boolean isDel;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "publish_user_id")
    private Integer publishUserId;

    @Column(name = "position_id")
    private Integer positionId;

    @Column(name = "resume_type")
    private Integer resumeType;

    @Column(name = "status")
    private String status;

    @Column(name = "create_time")
    private Date createTime;

    @Column(name = "update_time")
    private Date updateTime;
}
```



```java
public interface COrderRepository extends JpaRepository<COrder, Long> {
}
```

测试类：

```java
@SpringBootTest(classes = ShardingJdbcDemoApplication.class)
class ShardingJdbcDemoApplicationTests {

    @Autowired
    private COrderRepository cOrderRepository;
		
  	/**
  	* 生成20条记录
  	*/
    @Test
    public void testAdd() {
        for (int i = 100; i <120; i++) {
            COrder cOrder = new COrder();
            cOrder.setDel(false);
            cOrder.setUserId(i);
            cOrder.setCompanyId(new Random().nextInt(10));
            cOrder.setPublishUserId(new Random().nextInt(10));
            cOrder.setPositionId(new Random().nextInt(10));
            cOrder.setResumeType(new Random().nextInt(1));
            cOrder.setStatus("ARRANGE_INTERVIEW");
            cOrder.setCreateTime(new Date());
            cOrder.setUpdateTime(new Date());
            cOrderRepository.saveAndFlush(cOrder);
        }
    }

    @Test
    public void testFind() {
        List<COrder> cOrderList = cOrderRepository.findAll();
        cOrderList.forEach(cOrder -> {
            System.out.println(cOrder.toString());
        });
    }

}
```

执行效果：

Master1中的user_id都是偶数

<img src="http://qiniu.yiyuansucai.cn/image-20210612164155775.png" alt="image-20210612164155775" style="zoom:67%;" />

Master2中user_id都是奇数

<img src="http://qiniu.yiyuansucai.cn/image-20210612164420434.png" alt="image-20210612164420434" style="zoom: 67%;" />

查询所有记录时分别从Master1对应的从库和Master2对应的从库中读取数据

<img src="http://qiniu.yiyuansucai.cn/image-20210612164546362.png" alt="image-20210612164546362" style="zoom:67%;" />

