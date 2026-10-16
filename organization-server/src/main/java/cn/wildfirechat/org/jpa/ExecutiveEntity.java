package cn.wildfirechat.org.jpa;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;

/**
 * 高管名单。员工是否高管由这张表决定（不写死在 t_employee 上）：读员工时按 employee_id
 * 关联带上标记，管理后台可增删。
 *
 * <p>表由 Hibernate 按 {@code spring.jpa.hibernate.ddl-auto=update} 自动创建（本服务没有 Flyway）。
 */
@Entity
@Table(name = "t_executive")
public class ExecutiveEntity implements Serializable {

    @Id
    @Column(name = "employee_id", length = 64)
    public String employeeId;

    @Column(name = "created_at")
    public long createdAt;
}
