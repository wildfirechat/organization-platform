package cn.wildfirechat.org.pojo;

import javax.persistence.Column;
import javax.persistence.Id;

public class RelationshipPojo {
    public String employeeId;
    public int organizationId;
    public int depth;
    public boolean bottom;
    public int parentOrganizationId;
}
