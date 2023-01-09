package cn.wildfirechat.org.jpa;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "t_employee", indexes = {@Index(columnList = "organization_id"), @Index(columnList = "title"), @Index(columnList = "name"), @Index(columnList = "mobile"), @Index(columnList = "email")})
public class EmployeeEntity implements Serializable {
	@Id
	@Column(length = 64, name = "employee_id")
	public String employeeId;

	@Column(name = "organization_id")
	public int organizationId;

	@Column(length = 64)
	public String title;

	@Column(length = 64)
	public String name;

	@Column
	public int level;

	@Column(length = 64)
	public String mobile;

	@Column(length = 64)
	public String email;

	@Column(length = 64)
	public String ext;

	@Column(length = 1024)
	public String office;

	@Column(length = 1024)
	public String portraitUrl;

	@Column(length = 64, name = "job_number")
	public String jobNumber;

	@Column(length = 16, name = "join_time")
	public String joinTime;

	@Column(length = 64)
	public String city;

	@Column
	public int type;

	@Column
	public int gender;

	@Column
	public int sort;

	@Column(name = "create_dt")
	public long createDt;

	@Column(name = "update_dt")
	public long updateDt;
}
