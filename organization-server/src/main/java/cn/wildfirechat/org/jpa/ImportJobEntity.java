package cn.wildfirechat.org.jpa;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "t_import_job")
public class ImportJobEntity implements Serializable {
    @Id
    @Column(length = 64, name = "job_id")
    public String jobId;

    @Column(length = 16)
    public String status;

    @Column
    public int total;

    @Column
    public int processed;

    @Column(name = "success_count")
    public int successCount;

    @Column(name = "fail_count")
    public int failCount;

    @Column(name = "department_count")
    public int departmentCount;

    @Column(length = 1024, name = "error_message")
    public String errorMessage;

    @Column(length = 4000, name = "fail_details")
    public String failDetails;

    @Column(name = "create_dt")
    public long createDt;

    @Column(name = "update_dt")
    public long updateDt;
}
