package cn.wildfirechat.org.pojo;

import javax.persistence.*;
import java.io.Serializable;

public class OperationLogPojo {
    public int id;
    public String userId;
    public String operation;
    public String value;
    public long timestamp;
}
