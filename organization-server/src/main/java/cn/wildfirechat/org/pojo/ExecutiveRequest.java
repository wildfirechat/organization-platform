package cn.wildfirechat.org.pojo;

import java.util.List;

/** 高管管理接口的请求体：新增支持单个或多选，删除单个。 */
public class ExecutiveRequest {
    /** 删除时用 */
    public String employeeId;
    /** 新增时用（可多选） */
    public List<String> employeeIds;
}
