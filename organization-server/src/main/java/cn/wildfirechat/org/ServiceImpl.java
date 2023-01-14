package cn.wildfirechat.org;


import cn.wildfirechat.org.exception.IMServerException;
import cn.wildfirechat.org.exception.OrganizationDataCorruptionException;
import cn.wildfirechat.org.exception.OrganizationNoExistException;
import cn.wildfirechat.org.jpa.*;
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
import io.minio.PutObjectOptions;
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
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

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

    private RateLimiter rateLimiter;

    @PostConstruct
    private void init() {
        AdminConfig.initAdmin(mAdminUrl, mAdminSecret);
        rateLimiter = new RateLimiter(60, 200);
    }

    public RestResult login(HttpServletResponse httpResponse, String account, String password) {
        Subject subject = SecurityUtils.getSubject();
        UsernamePasswordToken token = new UsernamePasswordToken(account, password);

        try {
            subject.login(token);
        } catch (UnknownAccountException uae) {
            return RestResult.error(ERROR_CODE_ACCOUNT_NOT_EXIST);
        } catch (IncorrectCredentialsException ice) {
            return RestResult.error(RestResult.RestCode.ERROR_CODE_PASSWORD_INCORRECT);
        } catch (LockedAccountException lae) {
            return RestResult.error(RestResult.RestCode.ERROR_CODE_PASSWORD_INCORRECT);
        } catch (ExcessiveAttemptsException eae) {
            return RestResult.error(RestResult.RestCode.ERROR_CODE_PASSWORD_INCORRECT);
        } catch (AuthenticationException ae) {
            return RestResult.error(RestResult.RestCode.ERROR_CODE_PASSWORD_INCORRECT);
        }

        if (subject.isAuthenticated()) {
            long timeout = subject.getSession().getTimeout();
            LOG.info("Login success " + timeout);
        } else {
            token.clear();
            return RestResult.error(RestResult.RestCode.ERROR_CODE_PASSWORD_INCORRECT);
        }

        Object sessionId = subject.getSession().getId();
        httpResponse.setHeader("authToken", sessionId.toString());

        return RestResult.ok(null);
    }


    @Override
    public RestResult clientLogin(HttpServletResponse httpResponse, String authcode) {
        Subject subject = SecurityUtils.getSubject();
        AuthCodeToken token = new AuthCodeToken(authcode);

        try {
            subject.login(token);
        } catch (UnknownAccountException uae) {
            return RestResult.error(ERROR_CODE_ACCOUNT_NOT_EXIST);
        } catch (IncorrectCredentialsException ice) {
            return RestResult.error(RestResult.RestCode.ERROR_CODE_PASSWORD_INCORRECT);
        } catch (LockedAccountException lae) {
            return RestResult.error(RestResult.RestCode.ERROR_CODE_PASSWORD_INCORRECT);
        } catch (ExcessiveAttemptsException eae) {
            return RestResult.error(RestResult.RestCode.ERROR_CODE_PASSWORD_INCORRECT);
        } catch (AuthenticationException ae) {
            return RestResult.error(RestResult.RestCode.ERROR_CODE_PASSWORD_INCORRECT);
        }

        if (subject.isAuthenticated()) {
            long timeout = subject.getSession().getTimeout();
            LOG.info("Login success " + timeout);
        } else {
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
        String fileName = file.getOriginalFilename();
        String suffix = "";
        String prefix = fileName;
        if (fileName.contains(".")) {
            suffix = fileName.substring(fileName.lastIndexOf("."));
            prefix = fileName.substring(0, fileName.lastIndexOf("."));
        }
        File localFile = File.createTempFile(prefix, suffix);

        try {
            file.transferTo(localFile);
        } catch (IOException e) {
            e.printStackTrace();
            return RestResult.error(ERROR_SERVER_ERROR);
        }

        String bucket = ossBucket;
        String bucketDomain = ossBucketDomain;


        String url = bucketDomain + "/" + fileName;
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
            try {
                // 使用MinIO服务的URL，端口，Access key和Secret key创建一个MinioClient对象
//                MinioClient minioClient = new MinioClient("https://play.min.io", "Q3AM3UQ867SPQQA43P2F", "zuf+tfteSlswRu7BJ86wekitnifILbZam1KYY3TG");
                MinioClient minioClient = new MinioClient(ossUrl, ossAccessKey, ossSecretKey);

                // 使用putObject上传一个文件到存储桶中。
//                minioClient.putObject("asiatrip",fileName, localFile.getAbsolutePath(), new PutObjectOptions(PutObjectOptions.MAX_OBJECT_SIZE, PutObjectOptions.MIN_MULTIPART_SIZE));
                minioClient.putObject(bucket, fileName, localFile.getAbsolutePath(), new PutObjectOptions(file.getSize(), 0));
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
            //Todo 需要把文件上传到文件服务器。
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
        }

        UploadFileResponse response = new UploadFileResponse();
        response.setUrl(url);
        localFile.delete();
        return RestResult.ok(response);
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
        //必须有部门名称
        if (StringUtils.isNullOrEmpty(organizationPojo.name)) {
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

        if (!StringUtils.isNullOrEmpty(organizationPojo.managerId)) {
            // 将 manager 添加到新部门
            addEmployeeToOrganization(entity, organizationPojo.managerId);
        }

        return RestResult.ok(result);
    }

    @Override
    public RestResult updateOrganization(OrganizationPojo organizationPojo) throws Exception {
        if (organizationPojo.id <= 0) {
            return RestResult.error(ERROR_INVALID_PARAMETER);
        }
        Optional<OrganizationEntity> optional = organizationEntityRepository.findById(organizationPojo.id);
        if (!optional.isPresent()) {
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

        organizationEntityRepository.save(entity);

        if (!isEqual(entity.managerId, optional.get().managerId)) {
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
                    throw new IMServerException();
                }
            }

            //检查部门名称是否变化，如果变化更新群组名称
            if (!isEqual(entity.name, optional.get().name)) {
                try {
                    GroupAdmin.modifyGroupInfo(mAdminId, entity.groupId, ProtoConstants.ModifyGroupInfoType.Modify_Group_Name, entity.name, null, null);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new IMServerException();
                }
            }

            //检查部门头像是否变化，如果变化更新部门头像
            if (!isEqual(entity.portraitUrl, optional.get().portraitUrl)) {
                try {
                    GroupAdmin.modifyGroupInfo(mAdminId, entity.groupId, ProtoConstants.ModifyGroupInfoType.Modify_Group_Portrait, entity.portraitUrl, null, null);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new IMServerException();
                }
            }
        }

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
        Optional<OrganizationEntity> optional = organizationEntityRepository.findById(id);
        if (!optional.isPresent()) {
            //组织不存在
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

        return RestResult.ok(null);
    }

    private void quitGroup(String groupId, Collection<String> members) throws IMServerException {
        if (members == null || members.isEmpty()) {
            return;
        }

        try {
            GroupAdmin.kickoffGroupMembers(mAdminId, groupId, new ArrayList<>(members), null, null);
        } catch (Exception e) {
            e.printStackTrace();
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
        } catch (Exception e) {
            e.printStackTrace();
            throw new IMServerException();
        }
    }

    @Override
    public RestResult queryOrganization(int id) {
        Optional<OrganizationEntity> optional = organizationEntityRepository.findById(id);
        if (!optional.isPresent()) {
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
            subOrganizations.forEach(organizationEntity -> result.subOrganizations.add(convertOrganization(organizationEntity)));
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

    @Override
    public RestResult deleteOrganization(int id) throws Exception {
        Optional<OrganizationEntity> optional = organizationEntityRepository.findById(id);
        if (!optional.isPresent()) {
            return RestResult.error(ERROR_NOT_EXIST);
        }
        List<OrganizationEntity> subEntities = organizationEntityRepository.findAllByParentId(id);
        if (!subEntities.isEmpty()) {
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
                e.printStackTrace();
                throw new IMServerException();
            }
        }
        organizationEntityRepository.deleteById(id);
        return RestResult.ok(null);
    }

    @Override
    public RestResult createOrganizationGroup(int id, String groupId) throws IMServerException {
        Optional<OrganizationEntity> optional = organizationEntityRepository.findById(id);
        if (!optional.isPresent()) {
            return RestResult.error(ERROR_NOT_EXIST);
        }
        OrganizationEntity entity = optional.get();
        if (!StringUtils.isNullOrEmpty(entity.groupId)) {
            return RestResult.error(ERROR_ALREADY_EXIST);
        }

        if (StringUtils.isNullOrEmpty(entity.managerId)) {
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
            } else {
                throw new IMServerException();
            }
        } catch (Exception e) {
            e.printStackTrace();
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

        entity.groupId = null;
        entity.updateDt = System.currentTimeMillis();
        organizationEntityRepository.save(entity);

        try {
            GroupAdmin.dismissGroup(mAdminId, entity.groupId, null, null);
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

    @Override
    public RestResult createEmployee(EmployeePojo employeePojo) throws Exception {
        //添加员工必须拥有组织
        if (employeePojo.organizationId <= 0) {
            return RestResult.error(ERROR_INVALID_PARAMETER);
        }

        //检查组织是否存在
        Optional<OrganizationEntity> optional = organizationEntityRepository.findById(employeePojo.organizationId);
        if (!optional.isPresent()) {
            return RestResult.error(ERROR_ORGANIZATION_NOT_EXIST);
        }

        //检查员工是否存在
        if (!StringUtils.isNullOrEmpty(employeePojo.employeeId)) {
            Optional<EmployeeEntity> optionalEmployee = employeeEntityRepository.findById(employeePojo.employeeId);
            if (optionalEmployee.isPresent()) {
                return RestResult.error(ERROR_ALREADY_EXIST);
            }
        }

        try {
            boolean needCreateUserInImServer;
            if (!StringUtils.isNullOrEmpty(employeePojo.employeeId)) {
                IMResult<InputOutputUserInfo> outputUserInfoIMResult = UserAdmin.getUserByUserId(employeePojo.employeeId);
                if (outputUserInfoIMResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                    needCreateUserInImServer = false;
                } else if (outputUserInfoIMResult.getErrorCode() == ErrorCode.ERROR_CODE_NOT_EXIST) {
                    needCreateUserInImServer = true;
                } else {
                    throw new IMServerException();
                }
            } else {
                if (!StringUtils.isNullOrEmpty(employeePojo.mobile)) {
                    IMResult<InputOutputUserInfo> outputUserInfoIMResult = UserAdmin.getUserByMobile(employeePojo.mobile);
                    if (outputUserInfoIMResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                        employeePojo.employeeId = outputUserInfoIMResult.result.getUserId();
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

            InputOutputUserInfo inputOutputUserInfo = new InputOutputUserInfo();
            inputOutputUserInfo.setUserId(employeePojo.employeeId);
            inputOutputUserInfo.setDisplayName(employeePojo.name);
            inputOutputUserInfo.setGender(employeePojo.gender);
            inputOutputUserInfo.setPortrait(employeePojo.portraitUrl);
            inputOutputUserInfo.setMobile(employeePojo.mobile);
            inputOutputUserInfo.setEmail(employeePojo.email);
            if (needCreateUserInImServer) {
                inputOutputUserInfo.setName(UUID.randomUUID().toString());
                IMResult<OutputCreateUser> outputCreateUserIMResult = UserAdmin.createUser(inputOutputUserInfo);
                if (outputCreateUserIMResult.getErrorCode() != ErrorCode.ERROR_CODE_SUCCESS) {
                    throw new IMServerException();
                }
                employeePojo.employeeId = outputCreateUserIMResult.result.getUserId();
            } else {
                //用户已经存在，同步用户信息
                UserAdmin.updateUserInfo(inputOutputUserInfo, ProtoConstants.UpdateUserInfoMask.Update_User_DisplayName
                    | ProtoConstants.UpdateUserInfoMask.Update_User_Gender
                    | ProtoConstants.UpdateUserInfoMask.Update_User_Portrait
                    | ProtoConstants.UpdateUserInfoMask.Update_User_Mobile
                    | ProtoConstants.UpdateUserInfoMask.Update_User_Email);
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

        addEmployeeToOrganization(optional.get(), employeePojo.employeeId);

        CreateEmployeeResult createEmployeeResult = new CreateEmployeeResult();
        createEmployeeResult.employeeId = employeePojo.employeeId;
        return RestResult.ok(createEmployeeResult);
    }

    @Override
    public RestResult updateEmployee(EmployeePojo employeePojo) throws IMServerException {
        EmployeeEntity entity = convertEmployee(employeePojo);
        if (!StringUtils.isNullOrEmpty(entity.employeeId)) {
            return RestResult.error(ERROR_INVALID_PARAMETER);
        }

        Optional<EmployeeEntity> optionalEmployee = employeeEntityRepository.findById(entity.employeeId);
        if (!optionalEmployee.isPresent()) {
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

            IMResult<Void> voidIMResult = UserAdmin.updateUserInfo(inputOutputUserInfo, flag);
            if (voidIMResult.getErrorCode() != ErrorCode.ERROR_CODE_SUCCESS) {
                throw new IMServerException();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new IMServerException();
        }

        entity.createDt = optionalEmployee.get().createDt;
        entity.updateDt = System.currentTimeMillis();
        employeeEntityRepository.save(entity);
        return RestResult.ok(null);
    }

    @Override
    public RestResult moveEmployee(String employeeId, List<Integer> organizations) throws Exception {
        //检查参数有效性
        if (StringUtils.isNullOrEmpty(employeeId) || organizations == null || organizations.isEmpty()) {
            return RestResult.error(ERROR_INVALID_PARAMETER);
        }

        //检查员工是否存在
        Optional<EmployeeEntity> optionalEmployee = employeeEntityRepository.findById(employeeId);
        if (!optionalEmployee.isPresent()) {
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
        return RestResult.ok(null);
    }

    @Override
    public RestResult queryEmployee(String employeeId) {
        Optional<EmployeeEntity> optionalEmployee = employeeEntityRepository.findById(employeeId);
        if (!optionalEmployee.isPresent()) {
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
        Optional<EmployeeEntity> optionalEmployee = employeeEntityRepository.findById(employeeId);
        if (!optionalEmployee.isPresent()) {
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
            UserAdmin.destroyUser(employeeId);
        }

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


    @Override
    public RestResult importOrganization(MultipartFile file) {
        try {
            XSSFWorkbook sourceWorkbook = new XSSFWorkbook(file.getInputStream());
            Iterator<Row> it = sourceWorkbook.getSheetAt(0).rowIterator();
            int currentRow = 0;

            List<OrganizationTree> trees = new ArrayList<>();
            Map<String, EmployeeModel> employeeUserIdMap = new HashMap<>();
            Map<String, EmployeeModel> employeeMobileMap = new HashMap<>();
            Map<String, EmployeeModel> employeeEmailMap = new HashMap<>();
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
                    String userId = getStringValue(row.getCell(index++));
                    String name = getStringValue(row.getCell(index++));
                    String mobile = getStringValue(row.getCell(index++));
                    String email = getStringValue(row.getCell(index++));
                    String department = getStringValue(row.getCell(index++)).replace("，", ",").trim();
                    String jobNumber = getStringValue(row.getCell(index++));
                    String gender = getStringValue(row.getCell(index++));
                    String city = getStringValue(row.getCell(index++));
                    String type = getStringValue(row.getCell(index++));
                    String manager = getStringValue(row.getCell(index++)).replace("，", ",").trim();
                    String office = getStringValue(row.getCell(index++));
                    String ext = getStringValue(row.getCell(index++));
                    String joinTime = getStringValue(row.getCell(index++));
                    String title = getStringValue(row.getCell(index++));
                    String level = getStringValue(row.getCell(index++));

                    //检查电话号码是否存在检查是否重复
                    if (StringUtils.isNullOrEmpty(mobile)) {
                        return RestResult.result(ERROR_SERVER_ERROR, "电话号码不能为空");
                    }
                    EmployeeModel employeeModel = employeeMobileMap.get(mobile);
                    if (employeeModel != null) {
                        return RestResult.result(ERROR_SERVER_ERROR, "重复的电话号码");
                    }

                    //检查是否存在邮箱重复
                    if (!StringUtils.isNullOrEmpty(email)) {
                        employeeModel = employeeEmailMap.get(email);
                        if (employeeModel != null) {
                            return RestResult.result(ERROR_SERVER_ERROR, "重复的邮箱");
                        }
                    }

                    //检查是否存在用户ID重复
                    if (!StringUtils.isNullOrEmpty(userId)) {
                        employeeModel = employeeUserIdMap.get(userId);
                        if (employeeModel != null) {
                            return RestResult.result(ERROR_SERVER_ERROR, "重复的用户ID");
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
                    employeeEntity.level = Integer.parseInt(level);

                    //创建用户model
                    employeeModel = new EmployeeModel(employeeEntity);
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
                        return RestResult.result(ERROR_SERVER_ERROR, "部门路径不存在");
                    }
                    if (paths.length != mgs.length) {
                        return RestResult.result(ERROR_SERVER_ERROR, "部门和负责人数量不匹配");
                    }

                    //循环处理部门路径
                    for (int i = 0; i < paths.length; i++) {
                        String path = paths[i].trim();
                        if (StringUtils.isNullOrEmpty(path)) {
                            return RestResult.result(ERROR_SERVER_ERROR, "部门路径");
                        }
                        String mg = mgs[i].trim();
                        if (StringUtils.isNullOrEmpty(mg)) {
                            return RestResult.result(ERROR_SERVER_ERROR, "负责人数据为空");
                        }

                        String[] departNameArray = path.split("/");
                        OrganizationTree currentNode = null;
                        for (String departName : departNameArray) {
                            departName = departName.trim();
                            if (StringUtils.isNullOrEmpty(departName)) {
                                return RestResult.result(ERROR_SERVER_ERROR, "部门名称为空");
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
                currentRow++;
            }
            saveOrganization(trees, employeeMobileMap);
            return RestResult.ok(null);
        } catch (IOException e) {
            e.printStackTrace();
            return RestResult.error(ERROR_SERVER_ERROR);
        } catch (Exception e) {
            e.printStackTrace();
            return RestResult.error(ERROR_SERVER_ERROR);
        }
    }

    private void saveOrganization(List<OrganizationTree> trees, Map<String, EmployeeModel> employeeMobileMap) throws Exception {
        //先保存组织
        for (OrganizationTree tree : trees) {
            importOrganization(tree);
        }

        //再保存员工
        importEmployees(employeeMobileMap);

        //更新组织的负责人
        for (OrganizationTree tree : trees) {
            importOrganizationManager(tree);
        }

        //更新组织的人数
        for (OrganizationTree tree : trees) {
            importOrganizationMemberCount(tree);
        }
    }

    private void importOrganization(OrganizationTree tree) throws Exception {
        OrganizationEntity entity = tree.entity;
        entity.id = 0;
        entity.createDt = System.currentTimeMillis();
        entity.updateDt = entity.createDt;
        if (tree.parent != null) {
            if (tree.parent.entity.id > 0) {
                entity.parentId = tree.parent.entity.id;
            } else {
                LOG.error("should not be here!");
            }
        }
        organizationEntityRepository.save(entity);

        for (OrganizationTree node : tree.nodes) {
            importOrganization(node);
        }
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

    private void importEmployees(Map<String, EmployeeModel> employeeMobileMap) throws Exception {
        for (EmployeeModel employeeModel : employeeMobileMap.values()) {
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
            }
        }
    }

    @Override
    public RestResult resetAll() {
        organizationEntityRepository.deleteAll();
        employeeEntityRepository.deleteAll();
        relationshipEntityRepository.deleteAll();
        return RestResult.ok(null);
    }

    @Override
    public void recordOpLog(String operation, String value) {
        try {
            OperationLogEntity logEntity = new OperationLogEntity();
            logEntity.userId = getUserId();
            logEntity.operation = operation;
            logEntity.operationDesc = value;
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
        return pojo;
    }

    @Override
    public RestResult getLogs(int page, int count) {
        Pageable pageable = PageRequest.of(page, count);
        Page<OperationLogEntity> logEntityPage = operationLogEntityRepository.getLogsByPages(pageable);
        PageResponse<OperationLogPojo> response = new PageResponse<>();
        response.totalPages = logEntityPage.getTotalPages();
        response.totalCount = logEntityPage.getNumberOfElements();
        response.contents = new ArrayList<>();
        logEntityPage.getContent().forEach(entity -> response.contents.add(convertOperationLog(entity)));

        return RestResult.ok(response);
    }

    private String getStringValue(Cell cell) {
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
        RelationshipID relationshipID = new RelationshipID();
        relationshipID.organizationId = organizationEntity.id;
        relationshipID.employeeId = employeeId;
        if (relationshipEntityRepository.findById(relationshipID).isPresent()) {
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
