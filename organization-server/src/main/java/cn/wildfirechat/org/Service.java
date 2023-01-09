package cn.wildfirechat.org;


import cn.wildfirechat.org.exception.IMServerException;
import cn.wildfirechat.org.pojo.EmployeePojo;
import cn.wildfirechat.org.pojo.OrganizationPojo;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

public interface Service {
    RestResult login(HttpServletResponse response, String account, String password);
    RestResult clientLogin(HttpServletResponse response, String authcode);
    RestResult updatePassword(String oldPassword, String newPassword);
    RestResult getAccount();
    RestResult uploadMedia(MultipartFile file) throws Exception;
    RestResult createOrganization(OrganizationPojo organizationPojo) throws Exception;
    RestResult updateOrganization(OrganizationPojo organizationPojo) throws Exception;
    RestResult moveOrganization(int id, int newParentId) throws Exception;
    RestResult queryOrganization(int id);
    RestResult queryOrganizationEx(int id);
    RestResult queryListOrganization(List<Integer> ids);
    RestResult queryRootOrganization();
    RestResult searchOrganization(String keyword, int page, int count);
    RestResult organizationEmployees(int id);
    RestResult organizationBatchEmployees(List<Integer> ids);
    RestResult deleteOrganization(int id) throws IMServerException, Exception;
    RestResult createOrganizationGroup(int id, String groupId) throws IMServerException;
    RestResult dismissOrganizationGroup(int id) throws IMServerException;
    RestResult repairOrganizationGroup(int id) throws IMServerException;

    RestResult createEmployee(EmployeePojo employeePojo) throws Exception;
    RestResult updateEmployee(EmployeePojo employeePojo) throws IMServerException;
    RestResult moveEmployee(String employeeId, List<Integer> organizations) throws Exception;
    RestResult queryEmployee(String employeeId);
    RestResult queryEmployeeEx(String employeeId);
    RestResult queryListEmployee(List<String> employeeIds);
    RestResult deleteEmployee(String employeeId, boolean destroyIMUser) throws Exception;
    RestResult searchEmployee(String keyword, int organizationId, boolean root, int page, int count);

    RestResult queryEmployeeRelationship(String employeeId);
    RestResult importOrganization(MultipartFile file);
    RestResult resetAll();

    void recordOpLog(String operation, String value);
    RestResult getLogs(int page, int count);
}
