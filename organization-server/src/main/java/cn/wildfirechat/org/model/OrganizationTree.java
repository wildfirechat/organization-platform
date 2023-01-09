package cn.wildfirechat.org.model;

import cn.wildfirechat.org.jpa.EmployeeEntity;
import cn.wildfirechat.org.jpa.OrganizationEntity;

import java.util.ArrayList;
import java.util.List;

public class OrganizationTree {
    public OrganizationEntity entity;
    public List<OrganizationTree> nodes;
    public List<EmployeeModel> leaves;
    public EmployeeModel manager;
    public OrganizationTree parent;

    public OrganizationTree(OrganizationEntity entity) {
        this.entity = entity;
        nodes = new ArrayList<>();
        leaves = new ArrayList<>();
    }
}
