package cn.wildfirechat.org.pojo;

import java.util.List;

public class OrganizationWithChildren {
    public OrganizationPojo organization;
    public List<OrganizationPojo> subOrganizations;

    public List<EmployeePojo> employees;
}
