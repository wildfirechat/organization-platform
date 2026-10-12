package cn.wildfirechat.org;


import cn.wildfirechat.org.exception.IMServerException;
import cn.wildfirechat.org.exception.OrganizationDataCorruptionException;
import cn.wildfirechat.org.exception.OrganizationNoExistException;
import cn.wildfirechat.org.im.jpa.UserSessionEntity;
import cn.wildfirechat.org.im.jpa.UserSessionRepository;
import cn.wildfirechat.org.jpa.*;
import cn.wildfirechat.org.secondary.jpa.UserPassword;
import cn.wildfirechat.org.secondary.jpa.UserPasswordRepository;
import cn.wildfirechat.org.tools.ImTimestamp;
import cn.wildfirechat.org.model.EmployeeModel;
import cn.wildfirechat.org.model.OrganizationTree;
import cn.wildfirechat.org.pojo.*;
import cn.wildfirechat.org.shiro.AuthCodeToken;
import cn.wildfirechat.org.tools.RateLimiter;
import cn.wildfirechat.common.ErrorCode;
import cn.wildfirechat.pojos.*;
import cn.wildfirechat.proto.ProtoConstants;
import cn.wildfirechat.sdk.*;
import cn.wildfirechat.sdk.model.IMResult;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.PutObjectRequest;
import com.google.gson.Gson;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.http.HttpProtocol;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.util.Auth;
import com.qiniu.util.StringUtils;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.MinioException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.*;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.time.ZoneId;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static cn.wildfirechat.org.RestResult.RestCode.*;

@org.springframework.stereotype.Service
public class ServiceImpl implements Service {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceImpl.class);

    @Value("${im.admin_id}")
    private String mAdminId;

    @Value("${im.admin_url}")
    private String mAdminUrl;

    @Value("${im.admin_secret}")
    private String mAdminSecret;

    @Value("${media.server.media_type}")
    private int ossType;

    @Value("${media.server_url}")
    private String ossUrl;

    @Value("${media.access_key}")
    private String ossAccessKey;

    @Value("${media.secret_key}")
    private String ossSecretKey;

    @Value("${media.bucket_name}")
    private String ossBucket;
    @Value("${media.bucket_domain}")
    private String ossBucketDomain;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationEntityRepository organizationEntityRepository;

    @Autowired
    private EmployeeEntityRepository employeeEntityRepository;

    @Autowired
    private RelationshipEntityRepository relationshipEntityRepository;

    @Autowired
    private OperationLogEntityRepository operationLogEntityRepository;

    @Autowired(required = false)
    private UserPasswordRepository userPasswordRepository;

    /**
     * 野火 IM 服务自身库的数据源。未配置 spring.im-datasource.hikari.jdbc-url 时为 null，
     * 此时「今日使用情况」返回“服务器配置错误”，其余功能不受影响。
     */
    @Autowired(required = false)
    private UserSessionRepository userSessionRepository;

    /** 判定「今日」所用的时区，为空则用 JVM 默认时区 */
    @Value("${im.usage.timezone:}")
    private String imUsageTimezone;

    /**
     * IM 库 t_user_session._dt 的时间单位：auto（默认，按数据量级自动探测）/
     * seconds / millis / micros。表数据量很大且不想每次探测 max(_dt) 时可显式指定。
     */
    @Value("${im.session.timestamp-unit:auto}")
    private String imTimestampUnit;

    /** 查 IM 库时 _uid 的 IN 分片大小 */
    private static final int IM_UID_BATCH_SIZE = 500;

    /** 「今日使用情况」导出 CSV 的表头 */
    private static final String USAGE_CSV_HEADER = "状态,姓名,用户ID,部门名称,手机号";

    @Autowired
    private ImportJobRepository importJobRepository;

    private RateLimiter rateLimiter;

    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init() {
        AdminConfig.initAdmin(mAdminUrl, mAdminSecret);
        rateLimiter = new RateLimiter(60, 200);
        markInterruptedImportJobsAsFailed();
    }

    private void markInterruptedImportJobsAsFailed() {
        try {
            Iterable<ImportJobEntity> allJobs = importJobRepository.findAll();
            long now = System.currentTimeMillis();
            for (ImportJobEntity entity : allJobs) {
                if (ImportJob.STATUS_PROCESSING.equals(entity.status)) {
                    entity.status = ImportJob.STATUS_FAILED;
                    entity.errorMessage = "服务重启，导入中断";
                    entity.updateDt = now;
                    importJobRepository.save(entity);
                    LOG.info("Marked interrupted import job {} as FAILED", entity.jobId);
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to mark interrupted import jobs as failed: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    private void destroy() {
        importExecutor.shutdown();
        try {
            if (!importExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                importExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            importExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public RestResult login(HttpServletResponse httpResponse, String account, String password) {
        LOG.info("Service: login, account: {}", account);
        Subject subject = SecurityUtils.getSubject();
        UsernamePasswordToken token = new UsernamePasswordToken(account, password);

        try {
            subject.login(token);
        } catch (UnknownAccountException uae) {
            LOG.warn("Login failed - unknown account: {}", account);
            return RestResult.error(ERROR_CODE_ACCOUNT_NOT_EXIST);
        } catch (IncorrectCredentialsException ice) {
            LOG.warn("Login failed - incorrect credentials: {}", account);
            return RestResult.error(RestResult.RestCode.ERROR_CODE_PASSWORD_INCORRECT);
        } catch (LockedAccountException lae) {
            LOG.warn("Login failed - locked account: {}", account);
            return RestResult.error(RestResult.RestCode.ERROR_CODE_PASSWORD_INCORRECT);
        } catch (ExcessiveAttemptsException eae) {
            LOG.warn("Login failed - excessive attempts: {}", account);
            return RestResult.error(RestResult.RestCode.ERROR_CODE_PASSWORD_INCORRECT);
        } catch (AuthenticationException ae) {
            LOG.warn("Login failed - authentication error: {}", account);
            return RestResult.error(RestResult.RestCode.ERROR_CODE_PASSWORD_INCORRECT);
        }

        if (subject.isAuthenticated()) {
            long timeout = subject.getSession().getTimeout();
            LOG.info("Login success, account: {}, timeout: {}", account, timeout);
        } else {
            token.clear();
            LOG.warn("Login failed - not authenticated: {}", account);
            return RestResult.error(RestResult.RestCode.ERROR_CODE_PASSWORD_INCORRECT);
        }

        Object sessionId = subject.getSession().getId();
        httpResponse.setHeader("authToken", sessionId.toString());

        return RestResult.ok(null);
    }


    @Override
    public RestResult clientLogin(HttpServletResponse httpResponse, String authcode) {
        LOG.info("Service: clientLogin");
        Subject subject = SecurityUtils.getSubject();
        AuthCodeToken token = new AuthCodeToken(authcode);

        try {
            subject.login(token);
        } catch (UnknownAccountException uae) {
            LOG.warn("Client login failed - unknown account");
            return RestResult.error(ERROR_CODE_ACCOUNT_NOT_EXIST);
        } catch (IncorrectCredentialsException ice) {
            LOG.warn("Client login failed - incorrect credentials");
            return RestResult.error(RestResult.RestCode.ERROR_CODE_PASSWORD_INCORRECT);
        } catch (LockedAccountException lae) {
            LOG.warn("Client login failed - locked account");
            return RestResult.error(RestResult.RestCode.ERROR_CODE_PASSWORD_INCORRECT);
        } catch (ExcessiveAttemptsException eae) {
            LOG.warn("Client login failed - excessive attempts");
            return RestResult.error(RestResult.RestCode.ERROR_CODE_PASSWORD_INCORRECT);
        } catch (AuthenticationException ae) {
            LOG.warn("Client login failed - authentication error");
            return RestResult.error(RestResult.RestCode.ERROR_CODE_PASSWORD_INCORRECT);
        }

        if (subject.isAuthenticated()) {
            long timeout = subject.getSession().getTimeout();
            LOG.info("Client login success, timeout: {}", timeout);
        } else {
            LOG.warn("Client login failed - not authenticated");
            return RestResult.error(RestResult.RestCode.ERROR_CODE_PASSWORD_INCORRECT);
        }

        Object sessionId = subject.getSession().getId();
        httpResponse.setHeader("authToken", sessionId.toString());

        return RestResult.ok(null);
    }

    @Override
    public RestResult updatePassword(String oldPassword, String newPassword) {
        Subject subject = SecurityUtils.getSubject();
        if (!subject.isAuthenticated()) {
            return RestResult.error(ERROR_NOT_LOGIN);
        }
        String account = (String) subject.getPrincipal();
        Optional<User> optionalUser = userRepository.findByAccount(account);
        if (!optionalUser.isPresent()) {
            return RestResult.error(ERROR_CODE_ACCOUNT_NOT_EXIST);
        }

        User user = optionalUser.get();
        String md5 = new Base64().encodeToString(DigestUtils.getDigest("MD5").digest((oldPassword + user.getSalt()).getBytes(StandardCharsets.UTF_8)));
        if (!md5.equals(user.getPasswordMd5())) {
            return RestResult.error(ERROR_CODE_PASSWORD_INCORRECT);
        }

        String newMd5 = new Base64().encodeToString(DigestUtils.getDigest("MD5").digest((newPassword + user.getSalt()).getBytes(StandardCharsets.UTF_8)));
        user.setPasswordMd5(newMd5);
        userRepository.save(user);

        return RestResult.ok(null);
    }

    public String getUserId() {
        Subject subject = SecurityUtils.getSubject();
        if (subject.isAuthenticated()) {
            return (String) subject.getPrincipal();
        }
        return null;
    }

    @Override
    public RestResult getAccount() {
        Subject subject = SecurityUtils.getSubject();
        if (subject.isAuthenticated()) {
            IMResult<InputOutputUserInfo> userInfoIMResult = null;
            try {
                userInfoIMResult = UserAdmin.getUserByUserId((String) (subject.getPrincipal()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            if (userInfoIMResult == null) {
                LOG.error("getAccount error {}, UserAdmin.getUserByUserId return null", subject.getPrincipal());
            } else {
                if (userInfoIMResult.code == 0) {
                    OutputApplicationUserInfo outputApplicationUserInfo = new OutputApplicationUserInfo();
                    InputOutputUserInfo userInfo = userInfoIMResult.getResult();
                    outputApplicationUserInfo.setUserId(userInfo.getUserId());
                    outputApplicationUserInfo.setDisplayName(userInfo.getDisplayName());
                    outputApplicationUserInfo.setPortraitUrl(userInfo.getPortrait());
                    return RestResult.ok(outputApplicationUserInfo);
                } else {
                    LOG.error("getAccount error {}, {}", userInfoIMResult.code, userInfoIMResult.msg);
                }
            }
        }
        return RestResult.error(ERROR_NOT_EXIST);
    }

    @Override
    public RestResult uploadMedia(MultipartFile file) throws Exception {
        if(ossType == -1){
            return RestResult.error(ERROR_CODE_NOT_CONFIG_OSS);
        }
        String originalFilename = file.getOriginalFilename();
        String suffix = "";
        String prefix = UUID.randomUUID().toString();
        if (originalFilename != null && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String fileName = prefix + suffix;
        File localFile = File.createTempFile(prefix, suffix);

        try {
            file.transferTo(localFile);
        } catch (IOException e) {
            e.printStackTrace();
            localFile.delete();
            return RestResult.error(ERROR_SERVER_ERROR);
        }

        //限制只能上传图片
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            localFile.delete();
            return RestResult.result(ERROR_INVALID_PARAMETER, "只能上传图片文件");
        }

        String bucket = ossBucket;
        String bucketDomain = ossBucketDomain;


        String url = buildMediaUrl(bucketDomain, fileName);
        if (ossType == 1) {
            //构造一个带指定 Region 对象的配置类
            Configuration cfg = new Configuration(Region.region0());
            //...其他参数参考类注释
            UploadManager uploadManager = new UploadManager(cfg);
            //...生成上传凭证，然后准备上传

            //如果是Windows情况下，格式是 D:\\qiniu\\test.png
            String localFilePath = localFile.getAbsolutePath();
            //默认不指定key的情况下，以文件内容的hash值作为文件名
            String key = fileName;
            Auth auth = Auth.create(ossAccessKey, ossSecretKey);
            String upToken = auth.uploadToken(bucket);
            try {
                Response response = uploadManager.put(localFilePath, key, upToken);
                //解析上传成功的结果
                DefaultPutRet putRet = new Gson().fromJson(response.bodyString(), DefaultPutRet.class);
                System.out.println(putRet.key);
                System.out.println(putRet.hash);
            } catch (QiniuException ex) {
                Response r = ex.response;
                System.err.println(r.toString());
                try {
                    System.err.println(r.bodyString());
                } catch (QiniuException ex2) {
                    //ignore
                }
                return RestResult.error(ERROR_SERVER_ERROR);
            }
        } else if (ossType == 2) {
            // 创建OSSClient实例。
            OSS ossClient = new OSSClientBuilder().build(ossUrl, ossAccessKey, ossSecretKey);

            // 创建PutObjectRequest对象。
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, fileName, localFile);

            // 上传文件。
            try {
                ossClient.putObject(putObjectRequest);
            } catch (OSSException | ClientException e) {
                e.printStackTrace();
                return RestResult.error(ERROR_SERVER_ERROR);
            }
            // 关闭OSSClient。
            ossClient.shutdown();
        } else if (ossType == 3) {
            try (InputStream inputStream = new FileInputStream(localFile);
                 MinioClient minioClient = new MinioClient.Builder().endpoint(ossUrl).credentials(ossAccessKey, ossSecretKey).build();
            ) {
                minioClient.putObject(new PutObjectArgs.Builder()
                    .bucket(bucket)
                    .object(fileName)
                    .stream(inputStream, file.getSize(), 0)
                    .build());
            } catch (MinioException e) {
                System.out.println("Error occurred: " + e);
                return RestResult.error(ERROR_SERVER_ERROR);
            } catch (NoSuchAlgorithmException | IOException | InvalidKeyException e) {
                e.printStackTrace();
                return RestResult.error(ERROR_SERVER_ERROR);
            } catch (Exception e) {
                e.printStackTrace();
                return RestResult.error(ERROR_SERVER_ERROR);
            }
        } else if (ossType == 4) {
            localFile.delete();
            return RestResult.error(ERROR_SERVER_NOT_IMPLEMENT);
        } else if (ossType == 5) {
            COSCredentials cred = new BasicCOSCredentials(ossAccessKey, ossSecretKey);
            ClientConfig clientConfig = new ClientConfig();
            String[] ss = ossUrl.split("\\.");
            if (ss.length > 3) {
                if (!ss[1].equals("accelerate")) {
                    clientConfig.setRegion(new com.qcloud.cos.region.Region(ss[1]));
                } else {
                    clientConfig.setRegion(new com.qcloud.cos.region.Region("ap-shanghai"));
                    try {
                        URL u = new URL(ossUrl);
                        clientConfig.setEndPointSuffix(u.getHost());
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                        return RestResult.error(ERROR_SERVER_ERROR);
                    }
                }
            }

            clientConfig.setHttpProtocol(HttpProtocol.https);
            COSClient cosClient = new COSClient(cred, clientConfig);

            try {
                cosClient.putObject(bucket, fileName, localFile.getAbsoluteFile());
            } catch (CosClientException e) {
                e.printStackTrace();
                return RestResult.error(ERROR_SERVER_ERROR);
            } finally {
                cosClient.shutdown();
            }
        } else {
            localFile.delete();
            return RestResult.error(ERROR_CODE_NOT_CONFIG_OSS);
        }

        UploadFileResponse response = new UploadFileResponse();
        response.setUrl(url);
        localFile.delete();
        return RestResult.ok(response);
    }

    //拼接 bucketDomain 和文件名，避免重复斜杠
    private String buildMediaUrl(String bucketDomain, String fileName) {
        if (bucketDomain == null) {
            bucketDomain = "";
        }
        if (bucketDomain.endsWith("/")) {
            return bucketDomain + fileName;
        }
        return bucketDomain + "/" + fileName;
    }

    private OrganizationEntity convertOrganization(OrganizationPojo organizationPojo) {
        OrganizationEntity entity = new OrganizationEntity();
        entity.id = organizationPojo.id;
        entity.parentId = organizationPojo.parentId;
        entity.managerId = organizationPojo.managerId;
        entity.name = organizationPojo.name;
        entity.description = organizationPojo.description;
        entity.portraitUrl = organizationPojo.portraitUrl;
        entity.tel = organizationPojo.tel;
        entity.office = organizationPojo.office;
        entity.groupId = organizationPojo.groupId;
        entity.memberCount = organizationPojo.memberCount;
        entity.sort = organizationPojo.sort;
        entity.updateDt = organizationPojo.updateDt;
        entity.createDt = organizationPojo.createDt;
        return entity;
    }

    private OrganizationPojo convertOrganization(OrganizationEntity entity) {
        OrganizationPojo pojo = new OrganizationPojo();
        pojo.id = entity.id;
        pojo.parentId = entity.parentId;
        pojo.managerId = entity.managerId;
        pojo.name = entity.name;
        pojo.description = entity.description;
        pojo.portraitUrl = entity.portraitUrl;
        pojo.tel = entity.tel;
        pojo.office = entity.office;
        pojo.groupId = entity.groupId;
        pojo.memberCount = entity.memberCount;
        pojo.sort = entity.sort;
        pojo.updateDt = entity.updateDt;
        pojo.createDt = entity.createDt;
        return pojo;
    }

    @Override
    public RestResult createOrganization(OrganizationPojo organizationPojo) throws Exception {
        LOG.info("Service: createOrganization, name: {}, parentId: {}", organizationPojo.name, organizationPojo.parentId);
        //必须有部门名称
        if (StringUtils.isNullOrEmpty(organizationPojo.name)) {
            LOG.warn("Create organization failed - name is empty");
            return RestResult.error(ERROR_INVALID_PARAMETER);
        }

        //不能有群组id，只有创建群组成功后，再创建群组
        if (!StringUtils.isNullOrEmpty(organizationPojo.groupId)) {
            return RestResult.error(ERROR_CREATE_ORG_HAS_GROUP_ID);
        }

        //检查父节点是否有效
        if (organizationPojo.parentId > 0) {
            Optional<OrganizationEntity> optional = organizationEntityRepository.findById(organizationPojo.parentId);
            if (!optional.isPresent()) {
                LOG.warn("Create organization failed - parent not exist: {}", organizationPojo.parentId);
                return RestResult.error(ERROR_PARENT_NOT_EXIST);
            }
        }

        OrganizationEntity entity = convertOrganization(organizationPojo);
        entity.id = 0;

        entity.createDt = System.currentTimeMillis();
        entity.updateDt = entity.createDt;
        organizationEntityRepository.save(entity);
        OrganizationId result = new OrganizationId();
        result.organizationId = entity.id;
        LOG.info("Organization created successfully, id: {}, name: {}", entity.id, entity.name);

        if (!StringUtils.isNullOrEmpty(organizationPojo.managerId)) {
            // 将 manager 添加到新部门
            addEmployeeToOrganization(entity, organizationPojo.managerId);
        }

        return RestResult.ok(result);
    }

    @Override
    public RestResult updateOrganization(OrganizationPojo organizationPojo) throws Exception {
        LOG.info("Service: updateOrganization, id: {}, name: {}", organizationPojo.id, organizationPojo.name);
        if (organizationPojo.id <= 0) {
            LOG.warn("Update organization failed - invalid id: {}", organizationPojo.id);
            return RestResult.error(ERROR_INVALID_PARAMETER);
        }
        Optional<OrganizationEntity> optional = organizationEntityRepository.findById(organizationPojo.id);
        if (!optional.isPresent()) {
            LOG.warn("Update organization failed - not exist: {}", organizationPojo.id);
            return RestResult.error(ERROR_NOT_EXIST);
        }

        if (optional.get().parentId != organizationPojo.parentId) {
            return RestResult.error(ERROR_CANNOT_MODIFY_OGR_PARENT);
        }

        if (optional.get().groupId == null) {
            if (organizationPojo.groupId != null) {
                return RestResult.error(ERROR_UPDATE_ORG_ADD_GROUP);
            }
        } else {
            if (!optional.get().groupId.equals(organizationPojo.groupId)) {
                return RestResult.error(ERROR_UPDATE_ORG_ADD_GROUP);
            }
        }

        OrganizationEntity entity = convertOrganization(organizationPojo);
        entity.createDt = optional.get().createDt;
        entity.updateDt = System.currentTimeMillis();

        String orgManagerId = optional.get().managerId;

        organizationEntityRepository.save(entity);
        LOG.info("Organization updated successfully, id: {}", entity.id);

        if (!isEqual(entity.managerId, orgManagerId)) {
            // 将新部门 manager 加入部门
            RelationshipID relationshipID = new RelationshipID();
            relationshipID.employeeId = entity.managerId;
            relationshipID.organizationId = entity.id;
            Optional<RelationshipEntity> byId = relationshipEntityRepository.findById(relationshipID);
            if (!byId.isPresent()) {
                addEmployeeToOrganization(entity, entity.managerId);
            }
        }

        if (!StringUtils.isNullOrEmpty(entity.groupId)) {
            //检查部门经理有没有变更，如果变更，需要更新群主
            if (!isEqual(entity.managerId, optional.get().managerId)) {
                try {
                    String groupOwner = StringUtils.isNullOrEmpty(entity.managerId) ? mAdminId : entity.managerId;
                    GroupAdmin.transferGroup(mAdminId, entity.groupId, groupOwner, null, null);
                } catch (Exception e) {
                    LOG.error("Failed to transfer group owner, groupId: {}, newOwner: {}, error: {}", entity.groupId, entity.managerId, e.getMessage(), e);
                    throw new IMServerException();
                }
            }

            //检查部门名称是否变化，如果变化更新群组名称
            if (!isEqual(entity.name, optional.get().name)) {
                try {
                    GroupAdmin.modifyGroupInfo(mAdminId, entity.groupId, ProtoConstants.ModifyGroupInfoType.Modify_Group_Name, entity.name, null, null);
                } catch (Exception e) {
                    LOG.error("Failed to modify group name, groupId: {}, name: {}, error: {}", entity.groupId, entity.name, e.getMessage(), e);
                    throw new IMServerException();
                }
            }

            //检查部门头像是否变化，如果变化更新部门头像
            if (!isEqual(entity.portraitUrl, optional.get().portraitUrl)) {
                try {
                    GroupAdmin.modifyGroupInfo(mAdminId, entity.groupId, ProtoConstants.ModifyGroupInfoType.Modify_Group_Portrait, entity.portraitUrl, null, null);
                } catch (Exception e) {
                    LOG.error("Failed to modify group portrait, groupId: {}, error: {}", entity.groupId, e.getMessage(), e);
                    throw new IMServerException();
                }
            }
        }

        updateOrganizationMemberCount(entity.id);

        return RestResult.ok(null);
    }

    @Override
    public RestResult setOrganizationManager(int id, String managerId) throws Exception {
        LOG.info("Service: setOrganizationManager, id: {}, managerId: {}", id, managerId);
        if (id <= 0) {
            LOG.warn("Set organization manager failed - invalid id: {}", id);
            return RestResult.error(ERROR_INVALID_PARAMETER);
        }
        Optional<OrganizationEntity> optional = organizationEntityRepository.findById(id);
        if (!optional.isPresent()) {
            LOG.warn("Set organization manager failed - not exist: {}", id);
            return RestResult.error(ERROR_NOT_EXIST);
        }

        OrganizationEntity entity = optional.get();
        String orgManagerId = entity.managerId;

        if (isEqual(entity.managerId, managerId)) {
            return RestResult.ok(null);
        }

        entity.managerId = managerId;
        entity.updateDt = System.currentTimeMillis();
        organizationEntityRepository.save(entity);
        LOG.info("Organization manager set successfully, id: {}, managerId: {}", entity.id, managerId);

        if (!StringUtils.isNullOrEmpty(managerId)) {
            RelationshipID relationshipID = new RelationshipID();
            relationshipID.employeeId = managerId;
            relationshipID.organizationId = entity.id;
            Optional<RelationshipEntity> byId = relationshipEntityRepository.findById(relationshipID);
            if (!byId.isPresent()) {
                addEmployeeToOrganization(entity, managerId);
            }
        }

        if (!StringUtils.isNullOrEmpty(entity.groupId)) {
            try {
                String groupOwner = StringUtils.isNullOrEmpty(managerId) ? mAdminId : managerId;
                GroupAdmin.transferGroup(mAdminId, entity.groupId, groupOwner, null, null);
            } catch (Exception e) {
                LOG.error("Failed to transfer group owner, groupId: {}, newOwner: {}, error: {}", entity.groupId, managerId, e.getMessage(), e);
                throw new IMServerException();
            }
        }

        updateOrganizationMemberCount(entity.id);

        return RestResult.ok(null);
    }

    //判断2个字符串是否相等
    private boolean isEqual(String left, String right) {
        if (StringUtils.isNullOrEmpty(left)) {
            return StringUtils.isNullOrEmpty(right);
        } else {
            return left.equals(right);
        }
    }

    @Override
    public RestResult moveOrganization(int id, int newParentId) throws Exception {
        LOG.info("Service: moveOrganization, id: {}, newParentId: {}", id, newParentId);
        Optional<OrganizationEntity> optional = organizationEntityRepository.findById(id);
        if (!optional.isPresent()) {
            //组织不存在
            LOG.warn("Move organization failed - not exist: {}", id);
            return RestResult.error(ERROR_NOT_EXIST);
        }
        OrganizationEntity entity = optional.get();

        if (entity.parentId == newParentId) {
            return RestResult.ok(null);
        }

        if (newParentId > 0) {
            optional = organizationEntityRepository.findById(newParentId);
            if (!optional.isPresent()) {
                //目标组织不存在
                LOG.warn("Move organization failed - target not exist: {}", newParentId);
                return RestResult.error(ERROR_NOT_EXIST);
            }
        }

        //获取移动前的上级组织
        List<OrganizationEntity> previousAncestors = getAncestorOrganization(id);
        //获取移动前的深度
        int oldDepth = getOrganizationDepth(id);
        //获取移动前组织下的所有关系
        List<RelationshipEntity> relationshipEntities = relationshipEntityRepository.getOrganizationRelationshipsBelowDepth(id, oldDepth);
        //删除移动前组织下的所有关系
        relationshipEntityRepository.deleteAll(relationshipEntities);

        //获取组织下的所有员工
        Set<String> employees = new HashSet<>();
        relationshipEntities.forEach(entity12 -> employees.add(entity12.employeeId));

        //把组织移动到新的父组织下
        entity.parentId = newParentId;
        entity.updateDt = System.currentTimeMillis();
        organizationEntityRepository.save(entity);

        //获取移动后的上级组织
        List<OrganizationEntity> afterAncestors = getAncestorOrganization(id);
        //获取移动后的深度
        int newDepth = getOrganizationDepth(id);
        //当前组织的所有关系加上变更
        relationshipEntities.forEach(entity1 -> {
            entity1.depth += (newDepth - oldDepth);
            entity1.updateDt = System.currentTimeMillis();
            if (afterAncestors.isEmpty()) {
                entity1.parentOrganizationId = 0;
            } else {
                entity1.parentOrganizationId = afterAncestors.get(afterAncestors.size() - 1).id;
            }
        });
        //保存当前组织的变更
        relationshipEntityRepository.saveAll(relationshipEntities);

        //获取移动前和移动后的共同节点。
        List<Integer> commonIds = new ArrayList<>();
        previousAncestors.forEach(before -> {
            for (OrganizationEntity after : afterAncestors) {
                if (after.id == before.id) {
                    commonIds.add(after.id);
                    break;
                }
            }
        });

        List<RelationshipID> changedRelationshipIds = new ArrayList<>();
        employees.forEach(s -> {
            RelationshipID relationshipEntity = new RelationshipID();
            relationshipEntity.employeeId = s;
            changedRelationshipIds.add(relationshipEntity);
        });
        //退出的组织，需要退出群组和删掉关系
        for (int i = 0; i < previousAncestors.size(); i++) {
            OrganizationEntity organizationEntity = previousAncestors.get(i);
            if (!commonIds.contains(organizationEntity.id)) {
                int depth = i;
                quitGroup(organizationEntity.groupId, employees);
                changedRelationshipIds.forEach(entity13 -> {
                    entity13.organizationId = organizationEntity.id;
                    entity13.depth = depth;
                });
                relationshipEntityRepository.deleteAllById(changedRelationshipIds);
                updateOrganizationMemberCount(organizationEntity.id);
            }
        }

        List<RelationshipEntity> changedRelationshipEntitys = new ArrayList<>();
        employees.forEach(s -> {
            RelationshipEntity relationshipEntity = new RelationshipEntity();
            relationshipEntity.employeeId = s;
            changedRelationshipEntitys.add(relationshipEntity);
        });
        //加入的新组织，需要加入群和加入关系
        int parentOrgId = 0;
        for (int i = 0; i < afterAncestors.size(); i++) {
            OrganizationEntity organizationEntity = afterAncestors.get(i);
            if (!commonIds.contains(organizationEntity.id)) {
                int depth = i;
                addGroup(organizationEntity.groupId, organizationEntity.managerId, employees);
                for (RelationshipEntity entity13 : changedRelationshipEntitys) {
                    entity13.organizationId = organizationEntity.id;
                    entity13.depth = depth;
                    entity13.parentOrganizationId = parentOrgId;
                    entity13.createDt = System.currentTimeMillis();
                    entity13.updateDt = entity13.createDt;
                }
                relationshipEntityRepository.saveAll(changedRelationshipEntitys);
                updateOrganizationMemberCount(organizationEntity.id);
            }
            parentOrgId = organizationEntity.id;
        }

        LOG.info("Organization moved successfully, id: {}, newParentId: {}", id, newParentId);
        return RestResult.ok(null);
    }

    private void quitGroup(String groupId, Collection<String> members) throws IMServerException {
        if (members == null || members.isEmpty()) {
            return;
        }

        try {
            GroupAdmin.kickoffGroupMembers(mAdminId, groupId, new ArrayList<>(members), null, null);
            LOG.info("Quit group success, groupId: {}, members count: {}", groupId, members.size());
        } catch (Exception e) {
            LOG.error("Quit group failed, groupId: {}, error: {}", groupId, e.getMessage(), e);
            throw new IMServerException();
        }
    }

    private void addGroup(String groupId, String owner, Collection<String> members) throws IMServerException {
        if (StringUtils.isNullOrEmpty(groupId)) {
            return;
        }
        if (members == null || members.isEmpty()) {
            return;
        }

        List<PojoGroupMember> pojoGroupMembers = new ArrayList<>();
        members.forEach(s -> {
            PojoGroupMember pojoGroupMember = new PojoGroupMember();
            pojoGroupMember.setMember_id(s);
            if (s.equals(owner)) {
                pojoGroupMember.setType(ProtoConstants.GroupMemberType.GroupMemberType_Owner);
            }
            pojoGroupMembers.add(pojoGroupMember);
        });

        try {
            GroupAdmin.addGroupMembers(mAdminId, groupId, pojoGroupMembers, null, null);
            LOG.info("Add group members success, groupId: {}, members count: {}", groupId, members.size());
        } catch (Exception e) {
            LOG.error("Add group members failed, groupId: {}, error: {}", groupId, e.getMessage(), e);
            throw new IMServerException();
        }
    }

    @Override
    public RestResult queryOrganization(int id) {
        LOG.debug("Service: queryOrganization, id: {}", id);
        Optional<OrganizationEntity> optional = organizationEntityRepository.findById(id);
        if (!optional.isPresent()) {
            LOG.warn("Query organization failed - not exist: {}", id);
            return RestResult.error(ERROR_NOT_EXIST);
        }
        return RestResult.ok(convertOrganization(optional.get()));
    }

    @Override
    public RestResult queryOrganizationEx(int id) {
        Optional<OrganizationEntity> optional = organizationEntityRepository.findById(id);
        if (!optional.isPresent()) {
            return RestResult.error(ERROR_NOT_EXIST);
        }
        OrganizationWithChildren result = new OrganizationWithChildren();
        result.organization = convertOrganization(optional.get());
        result.subOrganizations = new ArrayList<>();
        List<OrganizationEntity> subOrganizations = organizationEntityRepository.findAllByParentId(id);
        if (subOrganizations != null && !subOrganizations.isEmpty()) {
            List<Integer> subIds = subOrganizations.stream().map(o -> o.id).collect(Collectors.toList());
            List<Integer> hasChildrenIds = organizationEntityRepository.findParentIdsWithChildren(subIds);
            Set<Integer> hasChildrenSet = new HashSet<>(hasChildrenIds);
            subOrganizations.forEach(organizationEntity -> {
                OrganizationPojo pojo = convertOrganization(organizationEntity);
                pojo.hasChildren = hasChildrenSet.contains(organizationEntity.id);
                result.subOrganizations.add(pojo);
            });
        }
        result.employees = new ArrayList<>();
        List<EmployeeEntity> employees = employeeEntityRepository.findByOrganizationId(id);
        if (employees != null && !employees.isEmpty()) {
            employees.forEach(employeeEntity -> {
                if (employeeEntity.employeeId.equals(result.organization.managerId)) {
                    result.employees.add(0, convertEmployee(employeeEntity));
                } else {
                    result.employees.add(convertEmployee(employeeEntity));
                }
            });
        }
        return RestResult.ok(result);
    }

    @Override
    public RestResult queryListOrganization(List<Integer> ids) {
        Iterable<OrganizationEntity> entities = organizationEntityRepository.findAllById(ids);
        List<OrganizationPojo> list = new ArrayList<>();
        entities.forEach(organizationEntity -> list.add(convertOrganization(organizationEntity)));

        return RestResult.ok(list);
    }

    @Override
    public RestResult queryRootOrganization() {
        List<OrganizationEntity> entities = organizationEntityRepository.findRootEntity();
        if (!entities.isEmpty()) {
            List<Integer> ids = entities.stream().map(e -> e.id).collect(Collectors.toList());
            List<Integer> hasChildrenIds = organizationEntityRepository.findParentIdsWithChildren(ids);
            Set<Integer> hasChildrenSet = new HashSet<>(hasChildrenIds);
            entities.forEach(e -> e.hasChildren = hasChildrenSet.contains(e.id));
        }
        return RestResult.ok(entities);
    }

    @Override
    public RestResult searchOrganization(String keyword, int page, int count) {
        if (page < 0) {
            page = 0;
        }
        if (count <= 0) {
            count = 20;
        }

        Pageable pageable = PageRequest.of(page, count);
        Page<OrganizationEntity> entityPage = organizationEntityRepository.searchEntity(keyword, pageable);
        PageResponse<OrganizationPojo> response = new PageResponse<>();
        response.totalCount = (int) entityPage.getTotalElements();
        response.totalPages = entityPage.getTotalPages();
        response.currentPage = page;
        response.contents = new ArrayList<>();
        entityPage.getContent().forEach(organizationEntity -> response.contents.add(convertOrganization(organizationEntity)));
        return RestResult.ok(response);
    }

    @Override
    public RestResult organizationEmployees(int id) {
        List<String> employees = relationshipEntityRepository.getOrganizationMembers(id);
        return RestResult.ok(employees);
    }

    @Override
    public RestResult organizationBatchEmployees(List<Integer> ids) {
        List<String> employees = relationshipEntityRepository.getOrganizationBatchMembers(ids);
        return RestResult.ok(employees);
    }

    /**
     * 今日使用情况：返回该部门（含所有子部门）的<b>全部成员</b>，分两组。
     * 「在线」= 今天登录过且会话仍然有效（t_user_session._deleted = 0 且 _dt >= 今日 0 点），按登录时间倒序；
     * 「未登录」= 部门内其余成员（含“今天登录过但已登出”的人），按部门、姓名排序。
     * <p>
     * 数据来源：野火 IM 库 t_user_session（只读）+ 组织库 t_employee / t_relationship / t_organization。
     * 口径依据官方《野火IM数据库表结构说明》：_dt = 最后更新/活跃时间戳（业务上当登录时间用），
     * _deleted = 0 正常 / 1 已登出或过期。
     */
    @Override
    public RestResult getTodayUsage(int organizationId, int page, int count) {
        RestResult invalid = validateUsageRequest(organizationId);
        if (invalid != null) {
            return invalid;
        }
        if (page < 0) {
            page = 0;
        }
        if (count <= 0 || count > 500) {
            count = 20;
        }

        List<TodayUsagePojo> all = buildTodayUsageList(organizationId);
        int totalCount = all.size();
        int from = Math.min(page * count, totalCount);
        int to = Math.min(from + count, totalCount);

        TodayUsageResponse response = new TodayUsageResponse();
        response.totalCount = totalCount;
        response.totalPages = (totalCount + count - 1) / count;
        response.currentPage = page;
        response.onlineCount = countOnline(all);
        response.notLoggedInCount = totalCount - response.onlineCount;
        response.contents = new ArrayList<>(all.subList(from, to));
        LOG.info("Service: getTodayUsage done, organizationId: {}, online: {}, notLoggedIn: {}",
                organizationId, response.onlineCount, response.notLoggedInCount);
        return RestResult.ok(response);
    }

    /**
     * 导出「今日使用情况」CSV：在线的用户在前、未登录的在后，覆盖部门（含子部门）全部成员。
     * <p>
     * 返回 CSV 文本而不是直接写 HTTP 响应体，是为了复用统一的登录态与错误处理
     * （例如未配 IM 数据源时能正常返回“服务器配置错误”），由前端落成文件。
     */
    @Override
    public RestResult exportTodayUsage(int organizationId) {
        RestResult invalid = validateUsageRequest(organizationId);
        if (invalid != null) {
            return invalid;
        }
        List<TodayUsagePojo> all = buildTodayUsageList(organizationId);
        LOG.info("Service: exportTodayUsage, organizationId: {}, rowCount: {}", organizationId, all.size());
        return RestResult.ok(toUsageCsv(all));
    }

    /** 两个「今日使用情况」接口共用的前置校验，返回 null 表示通过 */
    private RestResult validateUsageRequest(int organizationId) {
        if (userSessionRepository == null) {
            LOG.warn("today usage rejected - IM datasource is not configured, " +
                    "please configure spring.im-datasource.hikari.jdbc-url");
            return RestResult.error(ERROR_SERVER_CONFIG_ERROR);
        }
        if (organizationId <= 0) {
            LOG.warn("today usage rejected - invalid organizationId: {}", organizationId);
            return RestResult.error(ERROR_INVALID_PARAMETER);
        }
        return null;
    }

    /**
     * 组装完整的成员列表：在线在前（按登录时间倒序），未登录在后（按部门、姓名排序）。
     * 列表与 CSV 共用这一份结果，保证界面看到的内容和导出的内容一致。
     */
    private List<TodayUsagePojo> buildTodayUsageList(int organizationId) {
        // 组织库的 t_relationship 里 organization_id 已冗余记录了所有祖先部门，
        // 所以这一次查询拿到的是整棵子树的人员
        List<String> employeeIds = relationshipEntityRepository.getOrganizationMembers(organizationId);

        // _dt 的单位官方文档没写明，先把「今日 0 点」换算成表里实际的单位，否则阈值会差 1000 倍、
        // 当天记录会被全部过滤掉
        ImTimestamp.Unit unit = sessionTimestampUnit();
        long since = ImTimestamp.fromMillis(ImTimestamp.todayStartMillis(usageZone()), unit);
        LOG.info("Service: buildTodayUsageList, organizationId: {}, memberCount: {}, dtUnit: {}, since: {}",
                organizationId, employeeIds.size(), unit, since);

        // 1) 按分片拉取今天的有效会话，为每个用户保留 _dt 最大的那一条
        //    （一个用户多台设备在 t_user_session 里就是多行，这里要合并成一行）
        Map<String, UserSessionEntity> latestByUser = new HashMap<>();
        for (List<String> batch : partition(employeeIds, IM_UID_BATCH_SIZE)) {
            for (UserSessionEntity session : userSessionRepository.findActiveSessionsSince(since, batch)) {
                if (session.getUid() == null) {
                    continue;
                }
                UserSessionEntity exist = latestByUser.get(session.getUid());
                // 同一张表里的 _dt 单位一致，直接比原始值即可
                if (exist == null || exist.getDt() < session.getDt()) {
                    latestByUser.put(session.getUid(), session);
                }
            }
        }
        List<UserSessionEntity> onlineSessions = new ArrayList<>(latestByUser.values());
        // 按登录时间倒序，即“按照最新登录顺序排列”
        onlineSessions.sort((a, b) -> Long.compare(b.getDt(), a.getDt()));

        // 2) 补组织库信息。这里必须覆盖全部成员而不是只查当前页，
        //    因为未登录的人也要出现在列表和 CSV 里
        Map<String, EmployeeEntity> employeeMap = new HashMap<>();
        for (List<String> batch : partition(employeeIds, IM_UID_BATCH_SIZE)) {
            for (EmployeeEntity employee : employeeEntityRepository.findAllById(batch)) {
                employeeMap.put(employee.employeeId, employee);
            }
        }
        Map<String, String> departmentNames = new HashMap<>();
        for (List<String> batch : partition(employeeIds, IM_UID_BATCH_SIZE)) {
            for (Object[] row : relationshipEntityRepository.getEmployeeDepartmentNames(batch)) {
                String employeeId = (String) row[0];
                String departmentName = (String) row[1];
                if (employeeId == null || departmentName == null) {
                    continue;
                }
                // 支持一人多部门，用“、”连接
                String exist = departmentNames.get(employeeId);
                departmentNames.put(employeeId, exist == null ? departmentName : exist + "、" + departmentName);
            }
        }

        // 3) 在线在前
        List<TodayUsagePojo> result = new ArrayList<>();
        Set<String> onlineIds = new HashSet<>();
        for (UserSessionEntity session : onlineSessions) {
            TodayUsagePojo pojo = buildUsagePojo(session.getUid(), employeeMap, departmentNames);
            pojo.status = TodayUsagePojo.STATUS_ONLINE;
            pojo.platform = session.getPlatform();
            pojo.ip = session.getIp();
            pojo.loginTime = ImTimestamp.toMillis(session.getDt(), unit);
            result.add(pojo);
            onlineIds.add(session.getUid());
        }

        // 4) 未登录在后，按部门、姓名排序，保证每次打开的先后顺序一致
        List<TodayUsagePojo> notLoggedIn = new ArrayList<>();
        for (String employeeId : employeeIds) {
            if (onlineIds.contains(employeeId)) {
                continue;
            }
            TodayUsagePojo pojo = buildUsagePojo(employeeId, employeeMap, departmentNames);
            pojo.status = TodayUsagePojo.STATUS_NOT_LOGGED_IN;
            notLoggedIn.add(pojo);
        }
        notLoggedIn.sort(Comparator
                .comparing((TodayUsagePojo p) -> p.departmentName == null ? "" : p.departmentName)
                .thenComparing(p -> p.name == null ? "" : p.name)
                .thenComparing(p -> p.employeeId == null ? "" : p.employeeId));
        result.addAll(notLoggedIn);
        return result;
    }

    /** 用组织库的信息填一行记录（姓名、手机号、头像、部门名称） */
    private TodayUsagePojo buildUsagePojo(String employeeId, Map<String, EmployeeEntity> employeeMap,
                                          Map<String, String> departmentNames) {
        TodayUsagePojo pojo = new TodayUsagePojo();
        pojo.employeeId = employeeId;
        pojo.departmentName = departmentNames.get(employeeId);
        EmployeeEntity employee = employeeMap.get(employeeId);
        if (employee != null) {
            pojo.name = employee.name;
            pojo.mobile = employee.mobile;
            pojo.portraitUrl = employee.portraitUrl;
        }
        return pojo;
    }

    private static int countOnline(List<TodayUsagePojo> rows) {
        int count = 0;
        for (TodayUsagePojo row : rows) {
            if (row.status == TodayUsagePojo.STATUS_ONLINE) {
                count++;
            }
        }
        return count;
    }

    /**
     * 生成 CSV 文本。开头带 UTF-8 BOM，否则 Excel 双击打开中文会乱码；
     * 换行用 \r\n 兼容 Excel。
     */
    private static String toUsageCsv(List<TodayUsagePojo> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append(USAGE_CSV_HEADER).append("\r\n");
        for (TodayUsagePojo row : rows) {
            sb.append(csvField(row.status == TodayUsagePojo.STATUS_ONLINE ? "在线" : "未登录")).append(',')
                    .append(csvField(row.name)).append(',')
                    .append(csvField(row.employeeId)).append(',')
                    .append(csvField(row.departmentName)).append(',')
                    .append(csvField(row.mobile)).append("\r\n");
        }
        return sb.toString();
    }

    /** 按 RFC4180 转义：字段含逗号/引号/换行时用双引号包裹，内部引号翻倍 */
    private static String csvField(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"")
                || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    /**
     * 判定 t_user_session._dt 的时间单位。优先用配置项，
     * 未配置（auto）时取表中 max(_dt) 按数值量级推断。
     */
    private ImTimestamp.Unit sessionTimestampUnit() {
        ImTimestamp.Unit configured = ImTimestamp.parseUnit(imTimestampUnit);
        if (configured != null) {
            return configured;
        }
        Long maxDt = userSessionRepository.findMaxDt();
        ImTimestamp.Unit detected = ImTimestamp.detectUnit(maxDt == null ? 0 : maxDt);
        LOG.info("Service: auto detected _dt unit as {}, max(_dt) = {}", detected, maxDt);
        return detected;
    }

    /**
     * 判定「今日」所用的时区。配置项 im.usage.timezone 为空时用 JVM 默认时区。
     * 容器/服务器时区若是 UTC，务必显式配置，否则「今日」会偏移。
     */
    private ZoneId usageZone() {
        String configured = imUsageTimezone == null ? "" : imUsageTimezone.trim();
        if (configured.isEmpty()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(configured);
        } catch (Exception e) {
            LOG.warn("Invalid im.usage.timezone: {}, fallback to JVM default timezone", configured);
            return ZoneId.systemDefault();
        }
    }

    /** 把列表切成固定大小的批次，避免 SQL 的 IN 列表过长 */
    private static <T> List<List<T>> partition(List<T> source, int size) {
        List<List<T>> batches = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return batches;
        }
        for (int i = 0; i < source.size(); i += size) {
            batches.add(source.subList(i, Math.min(i + size, source.size())));
        }
        return batches;
    }

    @Override
    public RestResult deleteOrganization(int id) throws Exception {
        LOG.info("Service: deleteOrganization, id: {}", id);
        Optional<OrganizationEntity> optional = organizationEntityRepository.findById(id);
        if (!optional.isPresent()) {
            LOG.warn("Delete organization failed - not exist: {}", id);
            return RestResult.error(ERROR_NOT_EXIST);
        }
        List<OrganizationEntity> subEntities = organizationEntityRepository.findAllByParentId(id);
        if (!subEntities.isEmpty()) {
            LOG.warn("Delete organization failed - has children: {}", id);
            return RestResult.error(ERROR_OGR_CHILD_NOT_EMPTY);
        }

        // 删除根组织时，删除所有员工
        // TODO

        // 删除组织时，将员工移动父组织
        if (optional.get().parentId > 0) {
            List<EmployeeEntity> employees = employeeEntityRepository.findByOrganizationId(optional.get().id);
            for (EmployeeEntity employee : employees) {
                moveEmployee(employee.employeeId, Collections.singletonList(optional.get().parentId));
            }
        }

        if (!StringUtils.isNullOrEmpty(optional.get().groupId)) {
            try {
                GroupAdmin.dismissGroup(mAdminId, optional.get().groupId, null, null);
            } catch (Exception e) {
                LOG.error("Dismiss group failed, groupId: {}, error: {}", optional.get().groupId, e.getMessage(), e);
                throw new IMServerException();
            }
        }
        organizationEntityRepository.deleteById(id);
        LOG.info("Organization deleted successfully, id: {}", id);
        return RestResult.ok(null);
    }

    @Override
    public RestResult createOrganizationGroup(int id, String groupId) throws IMServerException {
        LOG.info("Service: createOrganizationGroup, id: {}, groupId: {}", id, groupId);
        Optional<OrganizationEntity> optional = organizationEntityRepository.findById(id);
        if (!optional.isPresent()) {
            LOG.warn("Create organization group failed - organization not exist: {}", id);
            return RestResult.error(ERROR_NOT_EXIST);
        }
        OrganizationEntity entity = optional.get();
        if (!StringUtils.isNullOrEmpty(entity.groupId)) {
            LOG.warn("Create organization group failed - already has group: {}", id);
            return RestResult.error(ERROR_ALREADY_EXIST);
        }

        if (StringUtils.isNullOrEmpty(entity.managerId)) {
            LOG.warn("Create organization group failed - no manager: {}", id);
            return RestResult.error(ERROR_ORG_CREATE_GROUP_NEED_MANAGER);
        }

        PojoGroupInfo groupInfo = new PojoGroupInfo();
        groupInfo.setOwner(entity.managerId);
        groupInfo.setName(entity.name);
        groupInfo.setPortrait(entity.portraitUrl);
        //organization group type
        groupInfo.setType(3);
        groupInfo.setMax_member_count(10000);

        List<String> members = relationshipEntityRepository.getOrganizationMembers(id);
        List<PojoGroupMember> pojoGroupMembers = new ArrayList<>();
        members.forEach(s -> {
            PojoGroupMember pojoGroupMember = new PojoGroupMember();
            pojoGroupMember.setMember_id(s);
            if (s.equals(entity.managerId)) {
                pojoGroupMember.setType(ProtoConstants.GroupMemberType.GroupMemberType_Owner);
            }
            pojoGroupMembers.add(pojoGroupMember);
        });

        try {
            IMResult<OutputCreateGroupResult> createGroupResultIMResult = GroupAdmin.createGroup(mAdminId, groupInfo, pojoGroupMembers, null, null);
            if (createGroupResultIMResult != null && createGroupResultIMResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                entity.groupId = createGroupResultIMResult.result.getGroup_id();
                organizationEntityRepository.save(entity);
                LOG.info("Organization group created successfully, id: {}, groupId: {}", id, entity.groupId);
            } else {
                LOG.error("Create group failed, code: {}, msg: {}", 
                    createGroupResultIMResult != null ? createGroupResultIMResult.getErrorCode() : "null",
                    createGroupResultIMResult != null ? createGroupResultIMResult.msg : "null");
                throw new IMServerException();
            }
        } catch (Exception e) {
            LOG.error("Create organization group failed, id: {}, error: {}", id, e.getMessage(), e);
            throw new IMServerException();
        }
        return RestResult.ok(null);
    }


    @Override
    public RestResult dismissOrganizationGroup(int id) throws IMServerException {
        Optional<OrganizationEntity> optional = organizationEntityRepository.findById(id);
        if (!optional.isPresent()) {
            return RestResult.error(ERROR_NOT_EXIST);
        }
        OrganizationEntity entity = optional.get();
        if (StringUtils.isNullOrEmpty(entity.groupId)) {
            return RestResult.error(ERROR_NOT_EXIST);
        }

        String groupId = entity.groupId;
        entity.groupId = null;
        entity.updateDt = System.currentTimeMillis();
        organizationEntityRepository.save(entity);

        try {
            GroupAdmin.dismissGroup(mAdminId, groupId, null, null);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IMServerException();
        }
        return RestResult.ok(null);
    }

    @Override
    public RestResult repairOrganizationGroup(int id) throws IMServerException {
        Optional<OrganizationEntity> optional = organizationEntityRepository.findById(id);
        if (!optional.isPresent()) {
            return RestResult.error(ERROR_NOT_EXIST);
        }
        OrganizationEntity entity = optional.get();
        if (StringUtils.isNullOrEmpty(entity.groupId)) {
            return RestResult.error(ERROR_NOT_EXIST);
        }

        try {
            //检查群组是否存在
            IMResult<PojoGroupInfo> groupInfoIMResult = GroupAdmin.getGroupInfo(entity.groupId);
            if (groupInfoIMResult.getErrorCode() == ErrorCode.ERROR_CODE_NOT_EXIST) {
                //如果不存在创建
                PojoGroupInfo groupInfo = new PojoGroupInfo();
                groupInfo.setOwner(entity.managerId);
                groupInfo.setName(entity.name);
                groupInfo.setPortrait(entity.portraitUrl);
                groupInfo.setType(2);
                groupInfo.setMax_member_count(10000);
                IMResult<OutputCreateGroupResult> createGroupResultIMResult = GroupAdmin.createGroup(mAdminId, groupInfo, null, null, null);
                if (createGroupResultIMResult.getErrorCode() != ErrorCode.ERROR_CODE_SUCCESS) {
                    throw new IMServerException();
                }
            } else if (groupInfoIMResult.getErrorCode() != ErrorCode.ERROR_CODE_SUCCESS) {
                throw new IMServerException();
            }

            //修正群组名字
            if (!entity.name.equals(groupInfoIMResult.getResult().getName())) {
                GroupAdmin.modifyGroupInfo(mAdminId, entity.groupId, ProtoConstants.ModifyGroupInfoType.Modify_Group_Name, entity.name, null, null);
            }

            //修正群组头像
            boolean portraitEquals = false;
            if (StringUtils.isNullOrEmpty(entity.portraitUrl) && StringUtils.isNullOrEmpty(groupInfoIMResult.getResult().getPortrait())) {
                portraitEquals = true;
            } else if (!StringUtils.isNullOrEmpty(entity.portraitUrl)) {
                portraitEquals = entity.portraitUrl.equals(groupInfoIMResult.getResult().getPortrait());
            }

            if (!portraitEquals) {
                GroupAdmin.modifyGroupInfo(mAdminId, entity.groupId, ProtoConstants.ModifyGroupInfoType.Modify_Group_Portrait, entity.portraitUrl, null, null);
            }

            //获取组织下所有成员
            List<String> members = relationshipEntityRepository.getOrganizationMembers(id);
            if(!StringUtils.isNullOrEmpty(entity.managerId)) {
                //管理员不在组织内？
                members.add(entity.managerId);
            }

            IMResult<OutputGroupMemberList> groupMemberListIMResult = GroupAdmin.getGroupMembers(entity.groupId);
            if (groupMemberListIMResult.getErrorCode() != ErrorCode.ERROR_CODE_SUCCESS) {
                throw new IMServerException();
            }

            //检查群组成员和组织成员的差异
            List<String> tobeRemoveMembers = new ArrayList<>();
            groupMemberListIMResult.getResult().getMembers().forEach(pojoGroupMember -> {
                if (members.contains(pojoGroupMember.getMember_id())) {
                    members.remove(pojoGroupMember.getMember_id());
                } else {
                    tobeRemoveMembers.add(pojoGroupMember.getMember_id());
                }
            });

            //把不在组织的群成员移出群组
            quitGroup(entity.groupId, tobeRemoveMembers);

            //把不在群组的组织成员加入群组
            addGroup(entity.groupId, entity.managerId, members);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IMServerException();
        }

        return RestResult.ok(null);
    }

    private EmployeeEntity convertEmployee(EmployeePojo employeePojo) {
        EmployeeEntity entity = new EmployeeEntity();
        entity.employeeId = employeePojo.employeeId;
        entity.name = employeePojo.name;
        entity.organizationId = employeePojo.organizationId;
        entity.title = employeePojo.title;
        entity.level = employeePojo.level;
        entity.mobile = employeePojo.mobile;
        entity.email = employeePojo.email;
        entity.office = employeePojo.office;
        entity.city = employeePojo.city;
        entity.ext = employeePojo.ext;
        entity.jobNumber = employeePojo.jobNumber;
        entity.joinTime = employeePojo.joinTime;
        entity.type = employeePojo.type;
        entity.portraitUrl = employeePojo.portraitUrl;
        entity.sort = employeePojo.sort;
        entity.gender = employeePojo.gender;
        entity.createDt = employeePojo.createDt;
        entity.updateDt = employeePojo.updateDt;
        return entity;
    }

    private EmployeePojo convertEmployee(EmployeeEntity entity) {
        EmployeePojo pojo = new EmployeePojo();
        pojo.employeeId = entity.employeeId;
        pojo.name = entity.name;
        pojo.organizationId = entity.organizationId;
        pojo.title = entity.title;
        pojo.level = entity.level;
        pojo.mobile = entity.mobile;
        pojo.email = entity.email;
        pojo.office = entity.office;
        pojo.ext = entity.ext;
        pojo.city = entity.city;
        pojo.portraitUrl = entity.portraitUrl;
        pojo.jobNumber = entity.jobNumber;
        pojo.joinTime = entity.joinTime;
        pojo.type = entity.type;
        pojo.sort = entity.sort;
        pojo.gender = entity.gender;
        pojo.createDt = entity.createDt;
        pojo.updateDt = entity.updateDt;
        return pojo;
    }

    @FunctionalInterface
    private interface ImCall<T> {
        IMResult<T> execute() throws Exception;
    }

    private <T> IMResult<T> retryImCall(ImCall<T> call, String desc) throws Exception {
        int maxRetries = 5;
        long sleepMs = 500;
        for (int i = 0; i < maxRetries; i++) {
            IMResult<T> result = call.execute();
            if (result.getErrorCode() == ErrorCode.ERROR_CODE_OVER_FREQUENCY) {
                if (i < maxRetries - 1) {
                    LOG.warn("IM call over frequency, retry {}/{} after {}ms: {}", i + 1, maxRetries, sleepMs, desc);
                    Thread.sleep(sleepMs);
                    if(sleepMs < 3000) {
                        sleepMs *= 2;
                    }
                    continue;
                }
            }
            return result;
        }
        return null;
    }

    @Override
    public RestResult createEmployee(EmployeePojo employeePojo) throws Exception {
        LOG.info("Service: createEmployee, employeeId: {}, name: {}, organizationId: {}", employeePojo.employeeId, employeePojo.name, employeePojo.organizationId);
        //添加员工必须拥有组织
        if (employeePojo.organizationId <= 0) {
            LOG.warn("Create employee failed - invalid organizationId: {}", employeePojo.organizationId);
            return RestResult.error(ERROR_INVALID_PARAMETER);
        }

        //检查组织是否存在
        Optional<OrganizationEntity> optional = organizationEntityRepository.findById(employeePojo.organizationId);
        if (!optional.isPresent()) {
            LOG.warn("Create employee failed - organization not exist: {}", employeePojo.organizationId);
            return RestResult.error(ERROR_ORGANIZATION_NOT_EXIST);
        }

        //检查员工是否存在
        if (!StringUtils.isNullOrEmpty(employeePojo.employeeId)) {
            Optional<EmployeeEntity> optionalEmployee = employeeEntityRepository.findById(employeePojo.employeeId);
            if (optionalEmployee.isPresent()) {
                LOG.warn("Create employee failed - already exist: {}", employeePojo.employeeId);
                return RestResult.error(ERROR_ALREADY_EXIST);
            }
        }

        InputOutputUserInfo inputOutputUserInfo = new InputOutputUserInfo();
        inputOutputUserInfo.setUserId(employeePojo.employeeId);
        inputOutputUserInfo.setDisplayName(employeePojo.name);
        inputOutputUserInfo.setGender(employeePojo.gender);
        inputOutputUserInfo.setPortrait(employeePojo.portraitUrl);
        inputOutputUserInfo.setMobile(employeePojo.mobile);
        inputOutputUserInfo.setEmail(employeePojo.email);

        try {
            boolean needCreateUserInImServer;
            if (!StringUtils.isNullOrEmpty(employeePojo.employeeId)) {
                IMResult<InputOutputUserInfo> outputUserInfoIMResult = retryImCall(() -> UserAdmin.getUserByUserId(employeePojo.employeeId), "getUserByUserId");
                if (outputUserInfoIMResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                    needCreateUserInImServer = false;
                    inputOutputUserInfo.setName(outputUserInfoIMResult.getResult().getName());
                } else if (outputUserInfoIMResult.getErrorCode() == ErrorCode.ERROR_CODE_NOT_EXIST) {
                    if (!StringUtils.isNullOrEmpty(employeePojo.mobile)) {
                        IMResult<InputOutputUserInfo> mobileUserResult = retryImCall(() -> UserAdmin.getUserByMobile(employeePojo.mobile), "getUserByMobile");
                        if (mobileUserResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                            LOG.warn("Create employee failed - employeeId not exist but mobile already used by another user: employeeId={}, mobile={}, existingUserId={}",
                                employeePojo.employeeId, employeePojo.mobile, mobileUserResult.getResult().getUserId());
                            return RestResult.result(ERROR_SERVER_ERROR, "用户ID不存在，但手机号已被其他用户使用");
                        } else if (mobileUserResult.getErrorCode() != ErrorCode.ERROR_CODE_NOT_EXIST) {
                            throw new IMServerException();
                        }
                    }
                    needCreateUserInImServer = true;
                } else {
                    throw new IMServerException();
                }
            } else {
                if (!StringUtils.isNullOrEmpty(employeePojo.mobile)) {
                    IMResult<InputOutputUserInfo> outputUserInfoIMResult = retryImCall(() -> UserAdmin.getUserByMobile(employeePojo.mobile), "getUserByMobile");
                    if (outputUserInfoIMResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                        employeePojo.employeeId = outputUserInfoIMResult.result.getUserId();
                        inputOutputUserInfo.setName(outputUserInfoIMResult.getResult().getName());
                        inputOutputUserInfo.setUserId(employeePojo.employeeId);
                        needCreateUserInImServer = false;
                    } else if (outputUserInfoIMResult.getErrorCode() == ErrorCode.ERROR_CODE_NOT_EXIST) {
                        needCreateUserInImServer = true;
                    } else {
                        throw new IMServerException();
                    }
                } else {
                    needCreateUserInImServer = true;
                }
            }

            if (needCreateUserInImServer) {
                inputOutputUserInfo.setName(UUID.randomUUID().toString());
                IMResult<OutputCreateUser> outputCreateUserIMResult = retryImCall(() -> UserAdmin.createUser(inputOutputUserInfo), "createUser");
                if (outputCreateUserIMResult.getErrorCode() != ErrorCode.ERROR_CODE_SUCCESS) {
                    throw new IMServerException();
                }
                employeePojo.employeeId = outputCreateUserIMResult.result.getUserId();
            } else {
                //用户已经存在，同步用户信息
                IMResult<Void> updateResult = retryImCall(() -> UserAdmin.updateUserInfo(inputOutputUserInfo, ProtoConstants.UpdateUserInfoMask.Update_User_DisplayName
                    | ProtoConstants.UpdateUserInfoMask.Update_User_Gender
                    | ProtoConstants.UpdateUserInfoMask.Update_User_Portrait
                    | ProtoConstants.UpdateUserInfoMask.Update_User_Mobile
                    | ProtoConstants.UpdateUserInfoMask.Update_User_Email), "updateUserInfo");
                if (updateResult.getErrorCode() != ErrorCode.ERROR_CODE_SUCCESS) {
                    throw new IMServerException();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new IMServerException();
        }

        //保存员工信息
        EmployeeEntity entity = convertEmployee(employeePojo);
        entity.createDt = System.currentTimeMillis();
        entity.updateDt = entity.createDt;
        employeeEntityRepository.save(entity);
        LOG.info("Employee created successfully, employeeId: {}, name: {}", employeePojo.employeeId, employeePojo.name);

        addEmployeeToOrganization(optional.get(), employeePojo.employeeId);

        // 如果有密码，保存到第二个数据源
        if (!StringUtils.isNullOrEmpty(employeePojo.password)) {
            saveUserPassword(employeePojo.employeeId, employeePojo.password);
        }

        CreateEmployeeResult createEmployeeResult = new CreateEmployeeResult();
        createEmployeeResult.employeeId = employeePojo.employeeId;
        return RestResult.ok(createEmployeeResult);
    }

    @Override
    public RestResult updateEmployee(EmployeePojo employeePojo) throws IMServerException {
        LOG.info("Service: updateEmployee, employeeId: {}, name: {}", employeePojo.employeeId, employeePojo.name);
        EmployeeEntity entity = convertEmployee(employeePojo);
        if (StringUtils.isNullOrEmpty(entity.employeeId)) {
            LOG.warn("Update employee failed - empty employeeId");
            return RestResult.error(ERROR_INVALID_PARAMETER);
        }

        Optional<EmployeeEntity> optionalEmployee = employeeEntityRepository.findById(entity.employeeId);
        if (!optionalEmployee.isPresent()) {
            LOG.warn("Update employee failed - not exist: {}", entity.employeeId);
            return RestResult.error(ERROR_NOT_EXIST);
        }

        if (entity.organizationId != optionalEmployee.get().organizationId) {
            return RestResult.error(ERROR_CANNOT_MODIFY_EMP_PARENT);
        }

        try {
            IMResult<InputOutputUserInfo> outputUserInfoIMResult = UserAdmin.getUserByUserId(employeePojo.employeeId);
            if (outputUserInfoIMResult.getErrorCode() != ErrorCode.ERROR_CODE_SUCCESS) {
                throw new IMServerException();
            }
            InputOutputUserInfo inputOutputUserInfo = new InputOutputUserInfo();
            inputOutputUserInfo.setUserId(employeePojo.employeeId);
            int flag = 0;
            if (!isEqual(employeePojo.name, outputUserInfoIMResult.result.getDisplayName())) {
                inputOutputUserInfo.setDisplayName(employeePojo.name);
                flag |= ProtoConstants.UpdateUserInfoMask.Update_User_DisplayName;
            }
            if (!isEqual(employeePojo.mobile, outputUserInfoIMResult.result.getMobile())) {
                inputOutputUserInfo.setMobile(employeePojo.mobile);
                flag |= ProtoConstants.UpdateUserInfoMask.Update_User_Mobile;
            }
            if (!isEqual(employeePojo.portraitUrl, outputUserInfoIMResult.result.getPortrait())) {
                inputOutputUserInfo.setPortrait(employeePojo.portraitUrl);
                flag |= ProtoConstants.UpdateUserInfoMask.Update_User_Portrait;
            }
            if (employeePojo.gender != outputUserInfoIMResult.result.getGender()) {
                inputOutputUserInfo.setGender(employeePojo.gender);
                flag |= ProtoConstants.UpdateUserInfoMask.Update_User_Gender;
            }
            if (!isEqual(employeePojo.email, outputUserInfoIMResult.result.getEmail())) {
                inputOutputUserInfo.setEmail(employeePojo.email);
                flag |= ProtoConstants.UpdateUserInfoMask.Update_User_Email;
            }

            if(flag > 0) {
                IMResult<Void> voidIMResult = UserAdmin.updateUserInfo(inputOutputUserInfo, flag);
                if (voidIMResult.getErrorCode() != ErrorCode.ERROR_CODE_SUCCESS) {
                    throw new IMServerException();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new IMServerException();
        }

        entity.createDt = optionalEmployee.get().createDt;
        entity.updateDt = System.currentTimeMillis();
        employeeEntityRepository.save(entity);
        LOG.info("Employee updated successfully, employeeId: {}", entity.employeeId);
        return RestResult.ok(null);
    }

    @Override
    public RestResult moveEmployee(String employeeId, List<Integer> organizations) throws Exception {
        LOG.info("Service: moveEmployee, employeeId: {}, organizations: {}", employeeId, organizations);
        //检查参数有效性
        if (StringUtils.isNullOrEmpty(employeeId) || organizations == null || organizations.isEmpty()) {
            LOG.warn("Move employee failed - invalid parameters");
            return RestResult.error(ERROR_INVALID_PARAMETER);
        }

        //检查员工是否存在
        Optional<EmployeeEntity> optionalEmployee = employeeEntityRepository.findById(employeeId);
        if (!optionalEmployee.isPresent()) {
            LOG.warn("Move employee failed - not exist: {}", employeeId);
            return RestResult.error(ERROR_NOT_EXIST);
        }

        //获取移动之前的所有关系
        List<RelationshipEntity> previousRelationshipEntitys = relationshipEntityRepository.getEmployeeRelationships(employeeId);

        //获取移动之前的所属组织ID
        Set<Integer> previousOrganizationIds = new HashSet<>();
        previousRelationshipEntitys.forEach(entity -> previousOrganizationIds.add(entity.organizationId));

        //计算移动之后的所有关系
        List<RelationshipEntity> afterRelationshipEntitys = new ArrayList<>();
        for (Integer orgId : organizations) {
            List<OrganizationEntity> ancestors = getAncestorOrganization(orgId);
            int parentOrgId = 0;
            for (int i = 0; i < ancestors.size(); i++) {
                OrganizationEntity ancestor = ancestors.get(i);
                RelationshipEntity relationshipEntity = new RelationshipEntity();
                relationshipEntity.employeeId = employeeId;
                relationshipEntity.organizationId = ancestor.id;
                relationshipEntity.depth = i;
                relationshipEntity.parentOrganizationId = parentOrgId;
                relationshipEntity.bottom = false;
                parentOrgId = ancestor.id;
                boolean duplicated = false;
                for (RelationshipEntity existEntity : afterRelationshipEntitys) {
                    if (existEntity.organizationId == relationshipEntity.organizationId && existEntity.depth == relationshipEntity.depth && existEntity.bottom == relationshipEntity.bottom) {
                        duplicated = true;
                        break;
                    }
                }
                if (!duplicated) {
                    afterRelationshipEntitys.add(relationshipEntity);
                }
            }
            OrganizationEntity parentEntity = organizationEntityRepository.findById(orgId).get();
            RelationshipEntity relationshipEntity = new RelationshipEntity();
            relationshipEntity.employeeId = employeeId;
            relationshipEntity.organizationId = parentEntity.id;
            relationshipEntity.depth = ancestors.size();
            relationshipEntity.bottom = true;
            relationshipEntity.parentOrganizationId = parentOrgId;
            afterRelationshipEntitys.add(relationshipEntity);
        }

        //计算移动之后的所属组织ID
        Set<Integer> afterOrganizationIds = new HashSet<>();
        afterRelationshipEntitys.forEach(entity -> afterOrganizationIds.add(entity.organizationId));

        //计算关系的差值
        List<RelationshipEntity> tobeRemoveRelationshipEntitys = new ArrayList<>();
        previousRelationshipEntitys.forEach(entity -> {
            boolean keepRelationship = false;
            for (RelationshipEntity relationshipEntity : afterRelationshipEntitys) {
                if (entity.organizationId == relationshipEntity.organizationId && entity.depth == relationshipEntity.depth && entity.bottom == relationshipEntity.bottom) {
                    keepRelationship = true;
                    afterRelationshipEntitys.remove(relationshipEntity);
                    break;
                }
            }
            if (!keepRelationship) {
                tobeRemoveRelationshipEntitys.add(entity);
            }
        });

        //新插入的关系，设置时间
        afterRelationshipEntitys.forEach(entity -> {
            entity.updateDt = System.currentTimeMillis();
            entity.createDt = entity.updateDt;
        });

        //删除不需要的关系
        relationshipEntityRepository.deleteAll(tobeRemoveRelationshipEntitys);

        //添加新的关系
        relationshipEntityRepository.saveAll(afterRelationshipEntitys);

        //计算退出的组织和新加入的组织，计算之后previousOrganizationIds为离开的部门，afterOrganizationIds为新加入的部门。
        Set<Integer> tmp = new HashSet<>(previousOrganizationIds);
        previousOrganizationIds.removeAll(afterOrganizationIds);
        afterOrganizationIds.removeAll(tmp);

        //更新变动组织成员数
        previousOrganizationIds.forEach(this::updateOrganizationMemberCount);
        afterOrganizationIds.forEach(this::updateOrganizationMemberCount);

        //退出的组织退出群组
        previousOrganizationIds.forEach(integer -> {
            try {
                OrganizationEntity organizationEntity = organizationEntityRepository.findById(integer).get();
                if (!StringUtils.isNullOrEmpty(organizationEntity.groupId)) {
                    quitGroup(organizationEntity.groupId, Arrays.asList(employeeId));
                }
            } catch (IMServerException e) {
                e.printStackTrace();
            }
        });

        //新加入的组织加入群组
        afterOrganizationIds.forEach(integer -> {
            try {
                OrganizationEntity organizationEntity = organizationEntityRepository.findById(integer).get();
                if (!StringUtils.isNullOrEmpty(organizationEntity.groupId)) {
                    addGroup(organizationEntity.groupId, organizationEntity.managerId, Arrays.asList(employeeId));
                }
            } catch (IMServerException e) {
                e.printStackTrace();
            }
        });

        //更新用户的组织为第一个
        EmployeeEntity entity = optionalEmployee.get();
        entity.organizationId = organizations.get(0);
        employeeEntityRepository.save(entity);
        LOG.info("Employee moved successfully, employeeId: {}, newOrganization: {}", employeeId, organizations.get(0));
        return RestResult.ok(null);
    }

    @Override
    public RestResult queryEmployee(String employeeId) {
        LOG.debug("Service: queryEmployee, employeeId: {}", employeeId);
        Optional<EmployeeEntity> optionalEmployee = employeeEntityRepository.findById(employeeId);
        if (!optionalEmployee.isPresent()) {
            LOG.warn("Query employee failed - not exist: {}", employeeId);
            return RestResult.error(ERROR_NOT_EXIST);
        }

        return RestResult.ok(convertEmployee(optionalEmployee.get()));
    }

    @Override
    public RestResult queryEmployeeEx(String employeeId) {
        EmployeeWithRelationship result = new EmployeeWithRelationship();
        Optional<EmployeeEntity> optionalEmployee = employeeEntityRepository.findById(employeeId);
        if (!optionalEmployee.isPresent()) {
            return RestResult.error(ERROR_NOT_EXIST);
        }
        result.employee = convertEmployee(optionalEmployee.get());

        List<RelationshipEntity> entities = relationshipEntityRepository.getEmployeeRelationships(employeeId);
        result.relationships = new ArrayList<>();
        entities.forEach(entity -> result.relationships.add(convertRelationship(entity)));

        return RestResult.ok(result);
    }

    @Override
    public RestResult queryListEmployee(List<String> employeeIds) {
        Iterable<EmployeeEntity> entities = employeeEntityRepository.findAllById(employeeIds);
        List<EmployeePojo> list = new ArrayList<>();
        entities.forEach(employeeEntity -> list.add(convertEmployee(employeeEntity)));
        return RestResult.ok(list);
    }

    @Override
    public RestResult deleteEmployee(String employeeId, boolean destroyIMUser) throws Exception {
        LOG.info("Service: deleteEmployee, employeeId: {}, destroyIMUser: {}", employeeId, destroyIMUser);
        Optional<EmployeeEntity> optionalEmployee = employeeEntityRepository.findById(employeeId);
        if (!optionalEmployee.isPresent()) {
            LOG.warn("Delete employee failed - not exist: {}", employeeId);
            return RestResult.error(ERROR_NOT_EXIST);
        }
        //获取员工的所有关系
        List<RelationshipEntity> entities = relationshipEntityRepository.getEmployeeRelationships(employeeId);

        //获取员工的组织
        Set<Integer> orgIds = new HashSet<>();
        entities.forEach(entity -> orgIds.add(entity.organizationId));
        for (Integer orgId : orgIds) {
            Optional<OrganizationEntity> org = organizationEntityRepository.findById(orgId);
            if (org.isPresent()) {
                OrganizationEntity organization = org.get();
                if (Objects.equals(organization.managerId, employeeId)) {
                    return RestResult.error(ERROR_CANNOT_DELETE_ORGANIZATION_MANAGER);
                }
            }
        }

        //删除员工信息
        employeeEntityRepository.deleteById(employeeId);

        //删除员工的所有关系
        relationshipEntityRepository.deleteAll(entities);

        //更新组织成员数
        orgIds.forEach(this::updateOrganizationMemberCount);

        //退出组织的群组
        orgIds.forEach(integer -> {
            OrganizationEntity organization = organizationEntityRepository.findById(integer).get();
            if (!StringUtils.isNullOrEmpty(organization.groupId)) {
                try {
                    quitGroup(organization.groupId, Arrays.asList(employeeId));
                } catch (IMServerException e) {
                    e.printStackTrace();
                }
            }
        });

        //删除IM服务中的用户所有信息
        if (destroyIMUser) {
            try {
                UserAdmin.destroyUser(employeeId);
                LOG.info("IM user destroyed, employeeId: {}", employeeId);
            } catch (Exception e) {
                LOG.error("Failed to destroy IM user, employeeId: {}, error: {}", employeeId, e.getMessage(), e);
            }
        }

        LOG.info("Employee deleted successfully, employeeId: {}", employeeId);
        return RestResult.ok(null);
    }

    @Override
    public RestResult searchEmployee(String keyword, int organizationId, boolean root, int page, int count) {
        if (page < 0) {
            page = 0;
        }
        if (count <= 0) {
            count = 20;
        }

        Page<EmployeeEntity> entityPage = null;
        Pageable pageable = PageRequest.of(page, count);
        if (!StringUtils.isNullOrEmpty(keyword)) {
            if (organizationId > 0) {
                if (root) {
                    entityPage = employeeEntityRepository.searchByKeywordAndOrganizationRoot(keyword, organizationId, pageable);
                } else {
                    entityPage = employeeEntityRepository.searchByKeywordAndOrganization(keyword, organizationId, pageable);
                }
            } else {
                entityPage = employeeEntityRepository.searchByKeyword(keyword, pageable);
            }
        } else {
            entityPage = employeeEntityRepository.searchByOrganization(organizationId, pageable);
        }

        PageResponse<EmployeePojo> response = new PageResponse<>();
        response.totalPages = entityPage.getTotalPages();
        response.totalCount = (int) entityPage.getTotalElements();
        response.currentPage = page;
        response.contents = new ArrayList<>();
        entityPage.getContent().forEach(employeeEntity -> response.contents.add(convertEmployee(employeeEntity)));
        return RestResult.ok(response);
    }

    private RelationshipPojo convertRelationship(RelationshipEntity entity) {
        RelationshipPojo pojo = new RelationshipPojo();
        pojo.employeeId = entity.employeeId;
        pojo.organizationId = entity.organizationId;
        pojo.depth = entity.depth;
        pojo.bottom = entity.bottom;
        pojo.parentOrganizationId = entity.parentOrganizationId;
        return pojo;
    }

    @Override
    public RestResult queryEmployeeRelationship(String employeeId) {
        List<RelationshipEntity> entities = relationshipEntityRepository.getEmployeeRelationships(employeeId);
        List<RelationshipPojo> pojos = new ArrayList<>();
        entities.forEach(entity -> pojos.add(convertRelationship(entity)));
        return RestResult.ok(pojos);
    }


    private static class ImportRow {
        String userId;
        String name;
        String mobile;
        String email;
        String department;
        String jobNumber;
        String gender;
        String city;
        String type;
        String manager;
        String office;
        String ext;
        String joinTime;
        String title;
        String level;
        String password;
    }

    private static final int IMPORT_QUERY_BATCH_SIZE = 1000;

    private List<String> findExistingMobilesInBatches(Collection<String> mobiles) {
        List<String> existing = new ArrayList<>();
        List<String> mobileList = new ArrayList<>(mobiles);
        for (int i = 0; i < mobileList.size(); i += IMPORT_QUERY_BATCH_SIZE) {
            List<String> batch = mobileList.subList(i, Math.min(i + IMPORT_QUERY_BATCH_SIZE, mobileList.size()));
            existing.addAll(employeeEntityRepository.findExistingMobiles(batch));
        }
        return existing;
    }

    private List<String> findExistingEmployeeIdsInBatches(Collection<String> employeeIds) {
        List<String> existing = new ArrayList<>();
        List<String> idList = new ArrayList<>(employeeIds);
        for (int i = 0; i < idList.size(); i += IMPORT_QUERY_BATCH_SIZE) {
            List<String> batch = idList.subList(i, Math.min(i + IMPORT_QUERY_BATCH_SIZE, idList.size()));
            existing.addAll(employeeEntityRepository.findExistingEmployeeIds(batch));
        }
        return existing;
    }

    @Override
    public RestResult importOrganization(MultipartFile file) {
        LOG.info("Service: importOrganization, fileName: {}, size: {}", file.getOriginalFilename(), file.getSize());
        try {
            String jobId = UUID.randomUUID().toString();
            File tempFile = File.createTempFile("import_", "_" + file.getOriginalFilename());
            file.transferTo(tempFile);

            long now = System.currentTimeMillis();
            ImportJobEntity entity = new ImportJobEntity();
            entity.jobId = jobId;
            entity.status = ImportJob.STATUS_PENDING;
            entity.createDt = now;
            entity.updateDt = now;
            importJobRepository.save(entity);

            cleanupOldImportJobs();

            ImportJob job = new ImportJob();
            job.setJobId(jobId);
            job.setStatus(ImportJob.STATUS_PENDING);
            job.setCreateTime(now);
            job.setUpdateTime(now);

            String operatorId = getUserId();

            importExecutor.submit(() -> {
                AtomicBoolean finished = new AtomicBoolean(false);
                Thread flushThread = new Thread(() -> {
                    while (!finished.get()) {
                        try {
                            Thread.sleep(1000);
                            syncJobToDb(job);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                });
                flushThread.setName("import-job-flusher-" + jobId);
                flushThread.setDaemon(true);
                flushThread.start();

                try {
                    job.setStatus(ImportJob.STATUS_PROCESSING);
                    job.setUpdateTime(System.currentTimeMillis());
                    syncJobToDb(job);

                    RestResult result = importOrganizationInternal(tempFile, job);
                    if (result.getCode() == SUCCESS.code) {
                        job.setStatus(ImportJob.STATUS_SUCCESS);
                    } else {
                        job.setStatus(ImportJob.STATUS_FAILED);
                        Object res = result.getResult();
                        job.setErrorMessage(res != null ? res.toString() : result.getMessage());
                    }
                } catch (Exception e) {
                    LOG.error("Import job {} failed, error: {}", jobId, e.getMessage(), e);
                    job.setStatus(ImportJob.STATUS_FAILED);
                    job.setErrorMessage(buildImportErrorDetail(e));
                } finally {
                    finished.set(true);
                    try {
                        flushThread.join(3000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    job.setUpdateTime(System.currentTimeMillis());
                    syncJobToDb(job);

                    try {
                        String logDesc = "总人数：" + job.getTotal()
                            + "，成功：" + job.getSuccessCount()
                            + "，失败：" + job.getFailCount()
                            + "，部门数：" + job.getDepartmentCount();
                        recordOpLog(operatorId, "批量导入完成", logDesc, ImportJob.STATUS_SUCCESS.equals(job.getStatus()));
                    } catch (Exception e) {
                        LOG.error("Failed to record import completion log: {}", e.getMessage(), e);
                    }

                    if (!tempFile.delete()) {
                        LOG.warn("Failed to delete temp import file: {}", tempFile.getAbsolutePath());
                    }
                }
            });

            return RestResult.ok(jobId);
        } catch (Exception e) {
            LOG.error("Import organization failed: {}", e.getMessage(), e);
            return RestResult.error(ERROR_SERVER_ERROR);
        }
    }

    private void syncJobToDb(ImportJob job) {
        try {
            ImportJobEntity entity = importJobRepository.findById(job.getJobId()).orElse(new ImportJobEntity());
            entity.jobId = job.getJobId();
            entity.status = job.getStatus();
            entity.total = job.getTotal();
            entity.processed = job.getProcessed();
            entity.successCount = job.getSuccessCount();
            entity.failCount = job.getFailCount();
            entity.departmentCount = job.getDepartmentCount();
            entity.errorMessage = job.getErrorMessage();
            entity.failDetails = job.getFailDetails() == null || job.getFailDetails().isEmpty() ? null : new Gson().toJson(job.getFailDetails());
            entity.createDt = job.getCreateTime();
            entity.updateDt = job.getUpdateTime();
            importJobRepository.save(entity);
        } catch (Exception e) {
            LOG.error("Failed to sync import job {} to db, error: {}", job.getJobId(), e.getMessage(), e);
        }
    }

    private void cleanupOldImportJobs() {
        try {
            long deadline = System.currentTimeMillis() - 24 * 60 * 60 * 1000;
            Iterable<ImportJobEntity> allJobs = importJobRepository.findAll();
            for (ImportJobEntity entity : allJobs) {
                if ((ImportJob.STATUS_SUCCESS.equals(entity.status) || ImportJob.STATUS_FAILED.equals(entity.status))
                    && entity.updateDt < deadline) {
                    importJobRepository.delete(entity);
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to cleanup old import jobs: {}", e.getMessage(), e);
        }
    }

    private ImportJob convertToImportJob(ImportJobEntity entity) {
        ImportJob job = new ImportJob();
        job.setJobId(entity.jobId);
        job.setStatus(entity.status);
        job.setTotal(entity.total);
        job.setProcessed(entity.processed);
        job.setSuccessCount(entity.successCount);
        job.setFailCount(entity.failCount);
        job.setDepartmentCount(entity.departmentCount);
        job.setErrorMessage(entity.errorMessage);
        if (!StringUtils.isNullOrEmpty(entity.failDetails)) {
            try {
                List<String> details = new Gson().fromJson(entity.failDetails, List.class);
                job.setFailDetails(details);
            } catch (Exception e) {
                LOG.error("Failed to parse import job fail details: {}", e.getMessage(), e);
                job.setFailDetails(new ArrayList<>());
            }
        } else {
            job.setFailDetails(new ArrayList<>());
        }
        job.setCreateTime(entity.createDt);
        job.setUpdateTime(entity.updateDt);
        return job;
    }

    @Override
    public RestResult queryImportJob(String jobId) {
        Optional<ImportJobEntity> optional = importJobRepository.findById(jobId);
        if (!optional.isPresent()) {
            return RestResult.error(ERROR_NOT_EXIST);
        }
        return RestResult.ok(convertToImportJob(optional.get()));
    }

    private RestResult importOrganizationInternal(File tempFile, ImportJob job) {
        LOG.info("Service: importOrganizationInternal, file: {}", tempFile.getAbsolutePath());
        try (FileInputStream fis = new FileInputStream(tempFile)) {
            XSSFWorkbook sourceWorkbook = new XSSFWorkbook(fis);
            Iterator<Row> it = sourceWorkbook.getSheetAt(0).rowIterator();
            int currentRow = 0;

            List<OrganizationTree> trees = new ArrayList<>();
            Map<String, EmployeeModel> employeeUserIdMap = new HashMap<>();
            Map<String, EmployeeModel> employeeMobileMap = new HashMap<>();
            Map<String, EmployeeModel> employeeEmailMap = new HashMap<>();
            int employeeSort = 0;
            int departmentSort = 0;

            // 判断是否为首次导入，首次导入时跳过本地存在检查
            boolean firstImport = employeeEntityRepository.count() == 0;

            // 第一遍：读取并缓存所有行，同时收集需要去重校验的手机号和用户ID
            List<ImportRow> rows = new ArrayList<>();
            Set<String> allMobiles = new HashSet<>();
            Set<String> allUserIds = new HashSet<>();
            while (it.hasNext()) {
                Row row = it.next();
                if (currentRow == 0) {
                    LOG.info("读取表的说明");
                } else if (currentRow == 1) {
                    LOG.info("读取表头");
                } else {
                    LOG.info("读取表内容");
                    //读取出来所有数据
                    int index = 0;
                    ImportRow importRow = new ImportRow();
                    importRow.userId = getStringValue(row.getCell(index++));
                    importRow.name = getStringValue(row.getCell(index++));
                    importRow.mobile = getStringValue(row.getCell(index++));
                    importRow.email = getStringValue(row.getCell(index++));
                    importRow.department = getStringValue(row.getCell(index++)).replace("，", ",").trim();
                    importRow.jobNumber = getStringValue(row.getCell(index++));
                    importRow.gender = getStringValue(row.getCell(index++));
                    importRow.city = getStringValue(row.getCell(index++));
                    importRow.type = getStringValue(row.getCell(index++));
                    importRow.manager = getStringValue(row.getCell(index++)).replace("，", ",").trim();
                    importRow.office = getStringValue(row.getCell(index++));
                    importRow.ext = getStringValue(row.getCell(index++));
                    importRow.joinTime = getStringValue(row.getCell(index++));
                    importRow.title = getStringValue(row.getCell(index++));
                    importRow.level = getStringValue(row.getCell(index++));
                    importRow.password = getStringValue(row.getCell(index++));

                    if(StringUtils.isNullOrEmpty(importRow.mobile) && StringUtils.isNullOrEmpty(importRow.name) && StringUtils.isNullOrEmpty(importRow.department)) {
                        //空行
                        continue;
                    }

                    //检查电话号码是否为空
                    if (StringUtils.isNullOrEmpty(importRow.mobile)) {
                        return RestResult.result(ERROR_SERVER_ERROR, importRow.name + " 电话号码不能为空");
                    }

                    rows.add(importRow);
                    allMobiles.add(importRow.mobile);
                    if (!StringUtils.isNullOrEmpty(importRow.userId)) {
                        allUserIds.add(importRow.userId);
                    }
                }
                currentRow++;
            }

            // 非首次导入时，批量检查数据库中是否已存在该员工
            if (!firstImport) {
                if (!allMobiles.isEmpty()) {
                    List<String> existingMobiles = findExistingMobilesInBatches(allMobiles);
                    if (!existingMobiles.isEmpty()) {
                        String mobiles = String.join(", ", existingMobiles);
                        return RestResult.result(ERROR_SERVER_ERROR, "以下电话号码已存在：" + mobiles);
                    }
                }

                if (!allUserIds.isEmpty()) {
                    List<String> existingUserIds = findExistingEmployeeIdsInBatches(allUserIds);
                    if (!existingUserIds.isEmpty()) {
                        String userIds = String.join(", ", existingUserIds);
                        return RestResult.result(ERROR_SERVER_ERROR, "以下用户ID已存在：" + userIds);
                    }
                }
            }

            // 第二遍：处理组织结构和员工数据
            for (ImportRow importRow : rows) {
                String userId = importRow.userId;
                String name = importRow.name;
                String mobile = importRow.mobile;
                String email = importRow.email;
                String department = importRow.department;
                String jobNumber = importRow.jobNumber;
                String gender = importRow.gender;
                String city = importRow.city;
                String type = importRow.type;
                String manager = importRow.manager;
                String office = importRow.office;
                String ext = importRow.ext;
                String joinTime = importRow.joinTime;
                String title = importRow.title;
                String level = importRow.level;
                String password = importRow.password;

                EmployeeModel employeeModel = employeeMobileMap.get(mobile);
                if (employeeModel != null) {
                    return RestResult.result(ERROR_SERVER_ERROR, mobile + " 重复的电话号码");
                }

                //检查是否存在邮箱重复
                if (!StringUtils.isNullOrEmpty(email)) {
                    employeeModel = employeeEmailMap.get(email);
                    if (employeeModel != null) {
                        return RestResult.result(ERROR_SERVER_ERROR, email + " 重复的邮箱");
                    }
                }

                //检查是否存在用户ID重复
                if (!StringUtils.isNullOrEmpty(userId)) {
                    employeeModel = employeeUserIdMap.get(userId);
                    if (employeeModel != null) {
                        return RestResult.result(ERROR_SERVER_ERROR, userId + " 重复的用户ID");
                    }
                }

                //创建用户数据
                EmployeeEntity employeeEntity = new EmployeeEntity();
                employeeEntity.employeeId = userId;
                employeeEntity.name = name;
                employeeEntity.mobile = mobile;
                employeeEntity.email = email;
                employeeEntity.jobNumber = jobNumber;
                employeeEntity.gender = StringUtils.isNullOrEmpty(gender) ? 0 : (gender.equals("男") ? 1 : 2);
                employeeEntity.city = city;
                employeeEntity.type = "正式".equals(type) ? 0 : 1;
                employeeEntity.office = office;
                employeeEntity.ext = ext;
                employeeEntity.joinTime = joinTime;
                employeeEntity.title = title;
                employeeEntity.level = 0;
                employeeEntity.sort = ++employeeSort;
                if(!StringUtils.isNullOrEmpty(level)) {
                    try {
                        employeeEntity.level = Integer.parseInt(level);
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }

                //创建用户model
                employeeModel = new EmployeeModel(employeeEntity);
                employeeModel.password = password;
                employeeMobileMap.put(mobile, employeeModel);
                if (!StringUtils.isNullOrEmpty(email)) {
                    employeeEmailMap.put(email, employeeModel);
                }
                if (!StringUtils.isNullOrEmpty(userId)) {
                    employeeUserIdMap.put(userId, employeeModel);
                }

                //处理部门路径和是否部门负责人
                department = department.replace("，", ",").trim();
                String[] paths = department.split(",");
                String[] mgs = manager.split(",");
                if (paths.length == 0) {
                    return RestResult.result(ERROR_SERVER_ERROR, name + " 部门路径不存在");
                }
                if (paths.length != mgs.length) {
                    if(mgs.length == 1 && StringUtils.isNullOrEmpty(mgs[0])) {
                        mgs = new String[paths.length];
                        for (int i = 0; i < paths.length; i++) {
                            mgs[i] = "否";
                        }
                    } else {
                        return RestResult.result(ERROR_SERVER_ERROR, name + " 部门和负责人数量不匹配");
                    }
                }

                //循环处理部门路径
                for (int i = 0; i < paths.length; i++) {
                    String path = paths[i].trim();
                    if (StringUtils.isNullOrEmpty(path)) {
                        return RestResult.result(ERROR_SERVER_ERROR, name + " 部门路径不正确");
                    }
                    String mg = mgs[i].trim();

                    String[] departNameArray = path.split("/");
                    OrganizationTree currentNode = null;
                    for (String departName : departNameArray) {
                        departName = departName.trim();
                        if (StringUtils.isNullOrEmpty(departName)) {
                            return RestResult.result(ERROR_SERVER_ERROR, name + " 部门名称为空");
                        }

                        if (currentNode == null) {
                            //寻找根路径
                            for (OrganizationTree tree : trees) {
                                if (tree.entity.name.equals(departName)) {
                                    currentNode = tree;
                                    break;
                                }
                            }

                            //如果根路径不存在，创建
                            if (currentNode == null) {
                                OrganizationEntity organizationEntity = new OrganizationEntity();
                                organizationEntity.name = departName;
                                organizationEntity.sort = ++departmentSort;
                                currentNode = new OrganizationTree((organizationEntity));
                                trees.add(currentNode);
                            }
                            continue;
                        }

                        boolean existNode = false;
                        for (OrganizationTree node : currentNode.nodes) {
                            if (node.entity.name.equals(departName)) {
                                currentNode = node;
                                existNode = true;
                                break;
                            }
                        }
                        if (!existNode) {
                            OrganizationEntity organizationEntity = new OrganizationEntity();
                            organizationEntity.name = departName;
                            organizationEntity.sort = ++departmentSort;
                            OrganizationTree tree = new OrganizationTree((organizationEntity));
                            tree.parent = currentNode;
                            currentNode.nodes.add(tree);
                            currentNode = tree;
                        }
                    }
                    if (currentNode != null) {
                        currentNode.leaves.add(employeeModel);
                        if ("是".equals(mg)) {
                            currentNode.manager = employeeModel;
                        }
                        employeeModel.organizationTrees.add(currentNode);
                    } else {
                        LOG.error("should not here");
                    }
                }
            }

            int departmentCount = countOrganizationTreeNodes(trees);
            job.setDepartmentCount(departmentCount);
            saveOrganization(trees, employeeMobileMap, job);
            LOG.info("Organization imported successfully");
            return RestResult.ok(null);
        } catch (IOException e) {
            LOG.error("Import organization failed - IO error: {}", e.getMessage(), e);
            return RestResult.result(ERROR_SERVER_ERROR, buildImportErrorDetail(e));
        } catch (Exception e) {
            LOG.error("Import organization failed: {}", e.getMessage(), e);
            return RestResult.result(ERROR_SERVER_ERROR, buildImportErrorDetail(e));
        }
    }

    //导入失败时写入 error_message 的详细信息：异常类型、消息和完整堆栈，超长截断到列上限
    private static String buildImportErrorDetail(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String detail = sw.toString();
        int maxLen = 4000;
        return detail.length() > maxLen ? detail.substring(0, maxLen) : detail;
    }

    private int countOrganizationTreeNodes(List<OrganizationTree> trees) {
        int count = 0;
        for (OrganizationTree tree : trees) {
            count += countOrganizationTreeNodes(tree);
        }
        return count;
    }

    private int countOrganizationTreeNodes(OrganizationTree tree) {
        if (tree == null) {
            return 0;
        }
        int count = 1;
        for (OrganizationTree node : tree.nodes) {
            count += countOrganizationTreeNodes(node);
        }
        return count;
    }

    private void saveOrganization(List<OrganizationTree> trees, Map<String, EmployeeModel> employeeMobileMap, ImportJob job) throws Exception {
        //先保存组织
        LOG.info("Save organizations");
        for (OrganizationTree tree : trees) {
            importOrganization(tree);
        }

        //再保存员工
        LOG.info("Save employees");
        if (job != null) {
            job.setTotal(job.getDepartmentCount() + employeeMobileMap.size());
            job.setProcessed(job.getDepartmentCount());
            job.setUpdateTime(System.currentTimeMillis());
        }
        importEmployees(employeeMobileMap, job);

        //更新组织的负责人
        LOG.info("Update organization manager");
        for (OrganizationTree tree : trees) {
            importOrganizationManager(tree);
        }

        //更新组织的人数
        LOG.info("Update organization member count");
        for (OrganizationTree tree : trees) {
            importOrganizationMemberCount(tree);
        }
    }

    private void importOrganization(OrganizationTree tree) throws Exception {
        OrganizationEntity entity = tree.entity;
        entity.createDt = System.currentTimeMillis();
        entity.updateDt = entity.createDt;

        int parentId = 0;
        if (tree.parent != null) {
            if (tree.parent.entity.id > 0) {
                parentId = tree.parent.entity.id;
            } else {
                LOG.error("should not be here!");
            }
        }
        entity.parentId = parentId;

        //根据父节点id判断同名节点是否已存在，存在则复用已有节点，避免重复创建
        OrganizationEntity existing = findExistingOrganization(parentId, entity.name);
        if (existing != null) {
            entity.id = existing.id;
            LOG.info("Organization {} already exists under parent {}, reuse id {}", entity.name, parentId, existing.id);
        } else {
            entity.id = 0;
            LOG.info("Save organization {}", entity.name);
            organizationEntityRepository.save(entity);
        }

        for (OrganizationTree node : tree.nodes) {
            importOrganization(node);
        }
    }

    //在指定父节点下查找同名的已有组织节点
    private OrganizationEntity findExistingOrganization(int parentId, String name) {
        if (StringUtils.isNullOrEmpty(name)) {
            return null;
        }
        List<OrganizationEntity> siblings = organizationEntityRepository.findAllByParentId(parentId);
        for (OrganizationEntity sibling : siblings) {
            if (name.equals(sibling.name)) {
                return sibling;
            }
        }
        return null;
    }

    private void importOrganizationManager(OrganizationTree tree) throws Exception {
        if (tree.manager != null) {
            tree.entity.managerId = tree.manager.employee.employeeId;
            updateOrganization(convertOrganization(tree.entity));
        }
        for (OrganizationTree node : tree.nodes) {
            importOrganizationManager(node);
        }
    }

    private void importOrganizationMemberCount(OrganizationTree tree) throws IMServerException {
        organizationEntityRepository.updateOrganizationMemberCount(tree.entity.id);
        for (OrganizationTree node : tree.nodes) {
            importOrganizationMemberCount(node);
        }
    }

    private void importEmployees(Map<String, EmployeeModel> employeeMobileMap, ImportJob job) throws Exception {
        int total = employeeMobileMap.size();
        AtomicInteger current = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        int departmentOffset = job != null ? job.getDepartmentCount() : 0;

        int nThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        List<Future<?>> futures = new ArrayList<>();

        for (EmployeeModel employeeModel : employeeMobileMap.values()) {
            Future<?> future = executor.submit(() -> {
                try {
                    int c = current.incrementAndGet();
                    LOG.info("importEmployees of {}, progress: {}/{}", employeeModel.employee.name, c, total);
                    if (!employeeModel.organizationTrees.isEmpty()) {
                        employeeModel.employee.organizationId = employeeModel.organizationTrees.get(0).entity.id;
                    }
                    EmployeePojo pojo = convertEmployee(employeeModel.employee);
                    RestResult result = createEmployee(pojo);
                    if (result.getCode() == SUCCESS.code) {
                        CreateEmployeeResult createEmployeeResult = (CreateEmployeeResult) result.getResult();
                        employeeModel.employee.employeeId = createEmployeeResult.employeeId;
                        if (employeeModel.organizationTrees.size() > 1) {
                            List<Integer> oids = new ArrayList<>();
                            employeeModel.organizationTrees.forEach(tree -> oids.add(tree.entity.id));
                            moveEmployee(createEmployeeResult.employeeId, oids);
                        }
                        // 保存密码到第二个数据源（im-app_server数据库）
                        if (!StringUtils.isNullOrEmpty(employeeModel.password)) {
                            saveUserPassword(createEmployeeResult.employeeId, employeeModel.password);
                        }
                        successCount.incrementAndGet();
                    } else {
                        LOG.warn("Import employee failed: {}, result: {}", employeeModel.employee.name, result);
                        failCount.incrementAndGet();
                        if (job != null) {
                            String reason = result.getResult() != null ? result.getResult().toString() : result.getMessage();
                            job.addFailDetail(employeeModel.employee.name + "（" + employeeModel.employee.mobile + "）：" + reason);
                        }
                    }
                } catch (Exception e) {
                    LOG.error("Import employee failed: {}, error: {}", employeeModel.employee.name, e.getMessage(), e);
                    failCount.incrementAndGet();
                    if (job != null) {
                        job.addFailDetail(employeeModel.employee.name + "（" + employeeModel.employee.mobile + "）：" + e.getMessage());
                    }
                } finally {
                    if (job != null) {
                        int c = current.get();
                        if (c % 5 == 0 || c == total) {
                            job.updateProgress(departmentOffset + c, successCount.get(), failCount.get());
                        }
                    }
                }
            });
            futures.add(future);
        }

        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();
        if (job != null) {
            job.updateProgress(departmentOffset + total, successCount.get(), failCount.get());
        }
        LOG.info("Import employees finished, total: {}, success: {}, fail: {}", total, successCount.get(), failCount.get());
    }

    private void saveUserPassword(String userId, String password) throws Exception {
        if (userPasswordRepository == null) {
            LOG.warn("Secondary datasource not configured, skip saving password for user: {}", userId);
            return;
        }
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
        digest.reset();
        String salt = java.util.UUID.randomUUID().toString();
        digest.update(salt.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] hashed = digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String hashedPwd = java.util.Base64.getEncoder().encodeToString(hashed);
        
        UserPassword up = new UserPassword(userId, hashedPwd, salt);
        userPasswordRepository.save(up);
    }

    @Override
    public RestResult updateEmployeePassword(String employeeId, String password) throws Exception {
        // 检查员工是否存在
        Optional<EmployeeEntity> optionalEmployee = employeeEntityRepository.findById(employeeId);
        if (!optionalEmployee.isPresent()) {
            return RestResult.error(ERROR_NOT_EXIST);
        }
        
        // 检查第二个数据源是否配置
        if (userPasswordRepository == null) {
            LOG.warn("Secondary datasource not configured, cannot update password for user: {}", employeeId);
            return RestResult.error(ERROR_SERVER_ERROR);
        }
        
        // 更新密码
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
        digest.reset();
        String salt = java.util.UUID.randomUUID().toString();
        digest.update(salt.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] hashed = digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String hashedPwd = java.util.Base64.getEncoder().encodeToString(hashed);
        
        UserPassword up = new UserPassword(employeeId, hashedPwd, salt);
        userPasswordRepository.save(up);
        
        return RestResult.ok(null);
    }

    @Override
    public RestResult resetAll() {
        LOG.info("Service: resetAll - clearing all data");
        try {
            organizationEntityRepository.deleteAll();
            employeeEntityRepository.deleteAll();
            relationshipEntityRepository.deleteAll();
            LOG.info("All data cleared successfully");
            return RestResult.ok(null);
        } catch (Exception e) {
            LOG.error("Reset all failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void recordOpLog(String operation, String value, boolean success) {
        recordOpLog(getUserId(), operation, value, success);
    }

    @Override
    public void recordOpLog(String userId, String operation, String value, boolean success) {
        try {
            OperationLogEntity logEntity = new OperationLogEntity();
            logEntity.userId = userId;
            logEntity.operation = operation;
            logEntity.operationDesc = value;
            logEntity.result = success ? 0 : 1;
            logEntity.timestamp = System.currentTimeMillis();
            operationLogEntityRepository.save(logEntity);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private OperationLogPojo convertOperationLog(OperationLogEntity entity) {
        OperationLogPojo pojo = new OperationLogPojo();
        pojo.id = entity.id;
        pojo.operation = entity.operation;
        pojo.userId = entity.userId;
        pojo.timestamp = entity.timestamp;
        pojo.value = entity.operationDesc;
        pojo.result = entity.result;
        return pojo;
    }

    @Override
    public RestResult getLogs(int page, int count) {
        Pageable pageable = PageRequest.of(page, count);
        long since = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000;
        Page<OperationLogEntity> logEntityPage = operationLogEntityRepository.getLogsByPages(since, pageable);
        PageResponse<OperationLogPojo> response = new PageResponse<>();
        response.totalPages = logEntityPage.getTotalPages();
        response.totalCount = (int) logEntityPage.getTotalElements();
        response.contents = new ArrayList<>();
        logEntityPage.getContent().forEach(entity -> response.contents.add(convertOperationLog(entity)));

        return RestResult.ok(response);
    }

    @Override
    public RestResult clearOperationLogs() {
        try {
            operationLogEntityRepository.deleteAll();
            recordOpLog("清空日志", "清空了所有操作日志", true);
            return RestResult.ok(null);
        } catch (Exception e) {
            LOG.error("Clear operation logs failed: {}", e.getMessage(), e);
            return RestResult.error(ERROR_SERVER_ERROR);
        }
    }

    private String getStringValue(Cell cell) {
        if(cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return (long) cell.getNumericCellValue() + "";
        }
        return cell.getStringCellValue().trim();
    }

    private List<OrganizationEntity> getAncestorOrganization(int organizationId) throws Exception {
        Optional<OrganizationEntity> optional = organizationEntityRepository.findById(organizationId);
        if (!optional.isPresent()) {
            throw new OrganizationNoExistException();
        }

        List<OrganizationEntity> result = new ArrayList<>();
        OrganizationEntity organizationEntity = optional.get();
        while (organizationEntity.parentId > 0) {
            optional = organizationEntityRepository.findById(organizationEntity.parentId);
            if (!optional.isPresent()) {
                throw new OrganizationDataCorruptionException();
            }
            organizationEntity = optional.get();
            result.add(0, organizationEntity);
        }

        return result;
    }

    private int getOrganizationDepth(int organizationId) throws Exception {
        Optional<OrganizationEntity> optional = organizationEntityRepository.findById(organizationId);
        if (!optional.isPresent()) {
            throw new OrganizationNoExistException();
        }

        int depth = 0;
        OrganizationEntity organizationEntity = optional.get();
        while (organizationEntity.parentId > 0) {
            optional = organizationEntityRepository.findById(organizationEntity.parentId);
            if (!optional.isPresent()) {
                throw new OrganizationDataCorruptionException();
            }
            organizationEntity = optional.get();
            depth++;
        }

        return depth;
    }

    private void updateOrganizationMemberCount(int organizationId) {
        organizationEntityRepository.updateOrganizationMemberCount(organizationId);
    }

    private void addEmployeeToOrganization(OrganizationEntity organizationEntity, String employeeId) throws Exception {
        LOG.debug("Add employee to organization, employeeId: {}, orgId: {}", employeeId, organizationEntity.id);
        RelationshipID relationshipID = new RelationshipID();
        relationshipID.organizationId = organizationEntity.id;
        relationshipID.employeeId = employeeId;
        if (relationshipEntityRepository.findById(relationshipID).isPresent()) {
            LOG.debug("Employee already in organization, employeeId: {}, orgId: {}", employeeId, organizationEntity.id);
            return;
        }

        List<OrganizationEntity> organizationEntities = getAncestorOrganization(organizationEntity.id);
        organizationEntities.add(organizationEntity);
        RelationshipEntity relationship = new RelationshipEntity();
        relationship.employeeId = employeeId;
        //保存员工关系
        int parentOrgId = 0;
        for (int i = 0; i < organizationEntities.size(); i++) {
            OrganizationEntity orgEntity = organizationEntities.get(i);
            relationship.organizationId = orgEntity.id;
            relationship.depth = i;
            relationship.parentOrganizationId = parentOrgId;
            relationship.bottom = (orgEntity.id == organizationEntity.id);
            relationship.createDt = System.currentTimeMillis();
            relationship.updateDt = relationship.createDt;
            try {
                relationshipEntityRepository.save(relationship);
            } catch (Exception e) {
                // 关系重复时，忽略，比如一个员工在同级的两个子部门时，在父部门的关系就会重复
            }
            parentOrgId = orgEntity.id;
        }

        for (OrganizationEntity organization : organizationEntities) {
            //检查是否存在群组，如果存在加入群组中
            if (!StringUtils.isNullOrEmpty(organization.groupId)) {
                addGroup(organization.groupId, organization.managerId, Arrays.asList(employeeId));
            }
            //更新组织成员数
            updateOrganizationMemberCount(organization.id);
        }
    }
}
