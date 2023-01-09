package cn.wildfirechat.org.jpa;

import java.io.Serializable;

public class RelationshipID implements Serializable {
	public String employeeId;
	public int organizationId;
	public int depth;
	public boolean bottom;
}
