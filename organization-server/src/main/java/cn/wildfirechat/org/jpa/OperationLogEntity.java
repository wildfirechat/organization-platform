package cn.wildfirechat.org.jpa;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "t_optlog")
public class OperationLogEntity implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    public int id;

    @Column(length = 64, name = "user_id")
    public String userId;

    @Column(length = 64)
    public String operation;

    @Column(length = 1024)
    public String operationDesc;

    @Column
    public long timestamp;
}
