# 组织架构服务

## 功能特性

- 组织架构管理（部门、子部门）
- 员工管理（添加、删除、变更部门）
- 批量导入组织架构和员工（支持 Excel）
- 员工密码管理（支持设置和修改密码）
- 多数据源支持（主数据库 + 用户密码数据库）

## 前端页面

默认是把前端页面打包到软件包内的，需要先编译前端代码，请先编译 organization-web 工程。

## 编译

在项目目录下执行:
```
mvn clean package
```

## 配置

服务使用外置配置文件，需要把 [config](./config) 目录放到软件包目录下，然后配置目录下的 `application.properties` 文件。

### 必配项

1. **IM 服务管理地址和密钥**
```properties
im.admin_url=http://localhost:18080
im.admin_secret=123456
```

2. **主数据库配置**（MySQL）
```properties
spring.datasource.hikari.jdbc-url=jdbc:mysql://localhost:3306/organization_server?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true
spring.datasource.hikari.username=root
spring.datasource.hikari.password=123456
spring.datasource.hikari.driver-class-name=com.mysql.cj.jdbc.Driver
```

### 可选配置

#### 第二个数据源（用户密码数据库）

如果需要将员工密码同步到 im-app_server 的数据库，需要配置第二个数据源：

```properties
# 第二个数据源 mysql 配置（im-app_server 数据库）
spring.secondary-datasource.hikari.jdbc-url=jdbc:mysql://localhost:3306/appdata?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true
spring.secondary-datasource.hikari.username=root
spring.secondary-datasource.hikari.password=123456
spring.secondary-datasource.hikari.driver-class-name=com.mysql.cj.jdbc.Driver
```

配置后以下功能会将密码写入第二个数据源：
- 添加成员时设置密码
- 修改员工密码
- 批量导入时 Excel 中包含密码列

如果不配置第二个数据源，上述功能会跳过密码保存并记录警告日志，不影响其他功能使用。

## 登录

打开后台登录页面，默认账户/密码为 `admin/admin123`，登录以后注意修改默认密码。

## 密码管理

### 1. 添加成员时设置密码

在"添加成员"界面，填写员工信息后，可以设置初始密码。密码会被加密保存到用户密码数据库。

### 2. 修改员工密码

在组织架构页面，鼠标悬停在员工行上，点击"修改密码"按钮，可以修改该员工的密码。

### 3. 批量导入密码

使用批量导入功能时，Excel 模板中包含"密码"列，导入时会将密码写入用户密码数据库。

### 密码加密方式

密码使用 SHA-1 算法加密存储，与 im-app_server 的密码加密方式保持一致。

## 注意事项

1. 上线前请修改默认的数据库密码和管理员密码
2. 如果使用第二个数据源，请确保 im-app_server 的数据库已创建且可访问
3. 建议定期备份数据库
