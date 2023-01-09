package cn.wildfirechat.org.jpa;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "t_organization", indexes = {@Index(columnList = "parent_id"), @Index(columnList = "manager_id"), @Index(columnList = "name")})
public class OrganizationEntity implements Serializable {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column
	public int id;

	@Column(name = "parent_id")
	public int parentId;

	@Column(length = 64, name = "manager_id")
	public String managerId;

	@Column(length = 64)
	public String name;

	@Column(length = 1024)
	public String description;

	@Column(length = 1024)
	public String portraitUrl;

	@Column(length = 64)
	public String tel;

	@Column(length = 1024)
	public String office;

	@Column(length = 64, name = "group_id")
	public String groupId;

	@Column(name = "member_count")
	public int memberCount;

	@Column
	public int sort;

	@Column
	public long updateDt;

	@Column
	public long createDt;
}
