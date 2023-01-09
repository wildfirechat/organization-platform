package cn.wildfirechat.org;

import cn.wildfirechat.org.exception.IMServerException;
import cn.wildfirechat.org.pojo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

@RequestMapping(value = "/api/")
@RestController
public class ApiController {
    private static final Logger LOG = LoggerFactory.getLogger(ApiController.class);
    @Autowired
    private Service mService;

    @ResponseBody
    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e) {
        LOG.error("handleException: {}", e.getLocalizedMessage());
        if(e instanceof IMServerException) {
            return RestResult.error(RestResult.RestCode.ERROR_IM_SERVER_ERROR);
        } else {
            return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
        }
    }

    /*
    管理后台登陆
     */
    @PostMapping(value = "login", produces = "application/json;charset=UTF-8")
    public Object login(@RequestBody LoginRequest request, HttpServletResponse response) {
        return mService.login(response, request.getAccount(), request.getPassword());
    }

    /*
    客户端登陆
    */
    @PostMapping(value = "user_login", produces = "application/json;charset=UTF-8")
    public Object userLogin(@RequestBody ClientLoginRequest request, HttpServletResponse response) {
        return mService.clientLogin(response, request.authCode);
    }

    /*
    管理后台修改密码
     */
    @PostMapping(value = "update_pwd", produces = "application/json;charset=UTF-8")
    public Object updatePwd(@RequestBody UpdatePasswordRequest request) {
        return mService.updatePassword(request.oldPassword, request.newPassword);
    }

    /*
    获取当前用户ID，管理后台和客户端都可以使用
     */
    @GetMapping(value = "account", produces = "application/json;charset=UTF-8")
    public Object getAccount() {
        return mService.getAccount();
    }

    /*
    管理后台上传图片
     */
    @PostMapping(value = "media/upload")
    public Object uploadMedia(@RequestParam("file") MultipartFile file) throws Exception {
        return mService.uploadMedia(file);
    }

    /**
     * 创建组织
     *
     * @param organizationPojo
     * @return
     * @throws Exception
     */
    @Transactional
    @PostMapping(value = "organization/create", produces = "application/json;charset=UTF-8")
    public Object createOrganization(@RequestBody OrganizationPojo organizationPojo) throws Exception {
        mService.recordOpLog("创建部门", organizationPojo.id + "");
        return mService.createOrganization(organizationPojo);
    }

    /**
     * 更新组织
     *
     * @param organizationPojo
     * @return
     * @throws Exception
     */
    @Transactional
    @PostMapping(value = "organization/update", produces = "application/json;charset=UTF-8")
    public Object updateOrganization(@RequestBody OrganizationPojo organizationPojo) throws Exception {
        mService.recordOpLog("更新部门", organizationPojo.id + "");
        return mService.updateOrganization(organizationPojo);
    }

    /**
     * 移动组织
     *
     * @param moveOrganizationPojo
     * @return
     * @throws Exception
     */
    @Transactional
    @PostMapping(value = "organization/move", produces = "application/json;charset=UTF-8")
    public Object moveOrganization(@RequestBody MoveOrganizationPojo moveOrganizationPojo) throws Exception {
        mService.recordOpLog("移动部门", moveOrganizationPojo.organizationId + "," + moveOrganizationPojo.newParentId);
        return mService.moveOrganization(moveOrganizationPojo.organizationId, moveOrganizationPojo.newParentId);
    }

    /**
     * 查询组织
     *
     * @param idPojo
     * @return
     * @throws Exception
     */
    @PostMapping(value = "organization/query", produces = "application/json;charset=UTF-8")
    public Object queryOrganization(@RequestBody OrganizationIdPojo idPojo) throws Exception {
        return mService.queryOrganization(idPojo.id);
    }

    /**
     * 查询组织，返回被查询组织及其子组织
     *
     * @param idPojo
     * @return
     * @throws Exception
     */
    @PostMapping(value = "organization/query_ex", produces = "application/json;charset=UTF-8")
    public Object queryOrganizationEx(@RequestBody OrganizationIdPojo idPojo) throws Exception {
        return mService.queryOrganizationEx(idPojo.id);
    }

    /**
     * 批量查询组织
     *
     * @param listPojo
     * @return
     * @throws Exception
     */
    @PostMapping(value = "organization/query_list", produces = "application/json;charset=UTF-8")
    public Object queryBatchOrganization(@RequestBody OrganizationIdListPojo listPojo) throws Exception {
        return mService.queryListOrganization(listPojo.ids);
    }

    /**
     * 查找根组织
     *
     * @return
     * @throws Exception
     */
    @PostMapping(value = "organization/root", produces = "application/json;charset=UTF-8")
    public Object queryRootOrganization() throws Exception {
        return mService.queryRootOrganization();
    }

    /**
     * 搜索组织
     *
     * @param searchPojo
     * @return
     * @throws Exception
     */
    @PostMapping(value = "organization/search", produces = "application/json;charset=UTF-8")
    public Object searchOrganization(@RequestBody SearchOrganizationPojo searchPojo) throws Exception {
        return mService.searchOrganization(searchPojo.keyword, searchPojo.page, searchPojo.count);
    }

    /**
     * 删除组织
     *
     * @param idPojo
     * @return
     * @throws Exception
     */
    @Transactional
    @PostMapping(value = "organization/delete", produces = "application/json;charset=UTF-8")
    public Object deleteOrganization(@RequestBody OrganizationIdPojo idPojo) throws Exception {
        mService.recordOpLog("删除部门", idPojo.id + "");
        return mService.deleteOrganization(idPojo.id);
    }

    /**
     * 获取组织下的成员ID列表
     *
     * @param idPojo
     * @return
     * @throws Exception
     */
    @PostMapping(value = "organization/employees", produces = "application/json;charset=UTF-8")
    public Object organizationEmployees(@RequestBody OrganizationIdPojo idPojo) throws Exception {
        return mService.organizationEmployees(idPojo.id);
    }

    /**
     * 获取多个组织下的成员ID列表
     *
     * @param idsPojo
     * @return
     * @throws Exception
     */
    @PostMapping(value = "organization/batch_employees", produces = "application/json;charset=UTF-8")
    public Object organizationsEmployees(@RequestBody OrganizationIdListPojo idsPojo) throws Exception {
        return mService.organizationBatchEmployees(idsPojo.ids);
    }

    /**
     * 创建工作群
     *
     * @param groupPojo
     * @return
     * @throws Exception
     */
    @Transactional
    @PostMapping(value = "organization/create_group", produces = "application/json;charset=UTF-8")
    public Object createOrganizationGroup(@RequestBody OrganizationGroupPojo groupPojo) throws Exception {
        mService.recordOpLog("创建工作群", groupPojo.id+","+groupPojo.groupId);
        return mService.createOrganizationGroup(groupPojo.id, groupPojo.groupId);
    }

    /**
     * 解散工作群
     *
     * @param idPojo
     * @return
     * @throws Exception
     */
    @Transactional
    @PostMapping(value = "organization/dismiss_group", produces = "application/json;charset=UTF-8")
    public Object dismissOrganizationGroup(@RequestBody OrganizationIdPojo idPojo) throws Exception {
        mService.recordOpLog("解散工作群", idPojo.id+"");
        return mService.dismissOrganizationGroup(idPojo.id);
    }

    /**
     * 修复工作群
     *
     * @param idPojo
     * @return
     * @throws Exception
     */
    @Transactional
    @PostMapping(value = "organization/repair_group", produces = "application/json;charset=UTF-8")
    public Object repairOrganizationGroup(@RequestBody OrganizationIdPojo idPojo) throws Exception {
        mService.recordOpLog("修复工作群", idPojo.id+"");
        return mService.repairOrganizationGroup(idPojo.id);
    }

    /**
     * 创建员工
     *
     * @param employeePojo
     * @return
     * @throws Exception
     */
    @Transactional
    @PostMapping(value = "employee/create", produces = "application/json;charset=UTF-8")
    public Object createEmployee(@RequestBody EmployeePojo employeePojo) throws Exception {
        mService.recordOpLog("添加新员工", employeePojo.employeeId);
        return mService.createEmployee(employeePojo);
    }

    /**
     * 更新员工信息
     *
     * @param employeePojo
     * @return
     * @throws Exception
     */
    @Transactional
    @PostMapping(value = "employee/update", produces = "application/json;charset=UTF-8")
    public Object updateEmployee(@RequestBody EmployeePojo employeePojo) throws Exception {
        mService.recordOpLog("更新员工信息", employeePojo.employeeId);
        return mService.updateEmployee(employeePojo);
    }

    /**
     * 员工转移部门
     *
     * @param moveEmployeePojo
     * @return
     * @throws Exception
     */
    @Transactional
    @PostMapping(value = "employee/move", produces = "application/json;charset=UTF-8")
    public Object moveEmployee(@RequestBody MoveEmployeePojo moveEmployeePojo) throws Exception {
        mService.recordOpLog("员工转移部门", moveEmployeePojo.employeeId + "," + moveEmployeePojo.organizations);
        return mService.moveEmployee(moveEmployeePojo.employeeId, moveEmployeePojo.organizations);
    }

    /**
     * 获取员工信息
     *
     * @param employeeId
     * @return
     * @throws Exception
     */
    @PostMapping(value = "employee/query", produces = "application/json;charset=UTF-8")
    public Object queryEmployee(@RequestBody EmployeeId employeeId) throws Exception {
        return mService.queryEmployee(employeeId.employeeId);
    }

    /**
     * 获取员工信息，返回额外的员工关系信息
     *
     * @param employeeId
     * @return
     * @throws Exception
     */
    @PostMapping(value = "employee/query_ex", produces = "application/json;charset=UTF-8")
    public Object queryEmployeeEx(@RequestBody EmployeeId employeeId) throws Exception {
        return mService.queryEmployeeEx(employeeId.employeeId);
    }

    /**
     * 批量获取员工信息
     *
     * @param idList
     * @return
     * @throws Exception
     */
    @PostMapping(value = "employee/query_list", produces = "application/json;charset=UTF-8")
    public Object queryListEmployee(@RequestBody EmployeeIdList idList) throws Exception {
        return mService.queryListEmployee(idList.employeeIds);
    }

    /**
     * 删除员工
     *
     * @param pojo
     * @return
     * @throws Exception
     */
    @Transactional
    @PostMapping(value = "employee/delete", produces = "application/json;charset=UTF-8")
    public Object deleteEmployee(@RequestBody DeleteEmployeePojo pojo) throws Exception {
        mService.recordOpLog("删除员工", pojo.employeeId + "," + pojo.destroyIMUser);
        return mService.deleteEmployee(pojo.employeeId, pojo.destroyIMUser);
    }

    /**
     * 搜索员工
     *
     * @param searchPojo
     * @return
     * @throws Exception
     */
    @PostMapping(value = "employee/search", produces = "application/json;charset=UTF-8")
    public Object searchEmployeeByName(@RequestBody SearchEmployeePojo searchPojo) throws Exception {
        return mService.searchEmployee(searchPojo.keyword, searchPojo.organizationId, searchPojo.root, searchPojo.page, searchPojo.count);
    }

    /**
     * 获取员工关系
     *
     * @param employeeId
     * @return
     * @throws Exception
     */
    @PostMapping(value = "relationship/employee", produces = "application/json;charset=UTF-8")
    public Object queryEmployeeRelationship(@RequestBody EmployeeId employeeId) throws Exception {
        return mService.queryEmployeeRelationship(employeeId.employeeId);
    }

    @ResponseBody
    @RequestMapping(value = "template", method = RequestMethod.GET)
    public String fileDownload(HttpServletResponse response)throws Exception{
        String fileName = "Import_template.xlsx";
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource resource = resourceLoader.getResource("classpath:" + fileName);

        if(!resource.exists()){
            System.out.println("模版文件不存在："+fileName);
            return "模版不存在，下载失败!";
        }else {
            //第一步：设置响应类型
            response.setContentType("application/force-download");//应用程序强制下载
            //第二读取文件
            InputStream inputStream = resource.getInputStream();
            //设置响应头，对文件进行url编码
            fileName = URLEncoder.encode(fileName, "UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename="+fileName);
            response.setContentLength((int) resource.contentLength());

            //第三步：读文件写入http响应
            FileCopyUtils.copy(inputStream, response.getOutputStream());
            inputStream.close();
            return "下载成功!";
        }
    }

    @ResponseBody
    @Transactional
    @PostMapping(value = "/import")
    public Object uploadFiles(@RequestParam("file") MultipartFile file) throws IOException {
        mService.recordOpLog("导入组织结构", "");
        return mService.importOrganization(file);
    }

    @ResponseBody
    @Transactional
    @PostMapping(value = "/reset_all")
    public Object resetAll() throws IOException {
        mService.recordOpLog("清除所有数据", "");
        return mService.resetAll();
    }

    @ResponseBody
    @PostMapping(value = "/logs")
    public Object getLogs(@RequestParam(name = "page", defaultValue = "0")int page, @RequestParam(name = "count", defaultValue = "20")int count) throws IOException {
        return mService.getLogs(page, count);
    }
}
