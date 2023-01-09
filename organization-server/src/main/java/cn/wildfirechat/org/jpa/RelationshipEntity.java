package cn.wildfirechat.org.jpa;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@IdClass(RelationshipID.class)
@Table(name = "t_relationship", indexes = {@Index(name = "org_id_bottom_index", columnList = "organization_id"),
		 								    @Index(name = "org_id_bottom_index", columnList = "bottom"),
                                            @Index(name = "employee_id_org_id_index", columnList = "employee_id, organization_id"),
											@Index(name = "org_parent_id_index", columnList = "parent_organization_id")})
public class RelationshipEntity implements Serializable {
	@Id
	@Column(length = 64, name = "employee_id")
	public String employeeId;

	@Id
	@Column(name = "organization_id")
	public int organizationId;

	@Id
	@Column
	public int depth;

	@Id
	@Column
	public boolean bottom;

	@Column(name = "parent_organization_id")
	public int parentOrganizationId;

	@Column
	public long updateDt;

	@Column
	public long createDt;
}
