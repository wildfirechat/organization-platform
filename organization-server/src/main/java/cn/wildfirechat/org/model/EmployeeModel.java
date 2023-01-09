package cn.wildfirechat.org.model;

import cn.wildfirechat.org.jpa.EmployeeEntity;
import cn.wildfirechat.org.jpa.OrganizationEntity;

import java.util.ArrayList;
import java.util.List;

public class EmployeeModel {
    public List<OrganizationTree> organizationTrees;
    public EmployeeEntity employee;

    public EmployeeModel(EmployeeEntity employee) {
        this.employee = employee;
        organizationTrees = new ArrayList<>();
    }
}
