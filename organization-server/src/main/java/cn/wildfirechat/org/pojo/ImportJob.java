package cn.wildfirechat.org.pojo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ImportJob implements Serializable {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private String jobId;
    private String status;
    private int total;
    private int processed;
    private int successCount;
    private int failCount;
    private int departmentCount;
    private String errorMessage;
    private List<String> failDetails = new ArrayList<>();
    private long createTime;
    private long updateTime;

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getProcessed() {
        return processed;
    }

    public void setProcessed(int processed) {
        this.processed = processed;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }

    public int getDepartmentCount() {
        return departmentCount;
    }

    public void setDepartmentCount(int departmentCount) {
        this.departmentCount = departmentCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<String> getFailDetails() {
        return failDetails;
    }

    public void setFailDetails(List<String> failDetails) {
        this.failDetails = failDetails;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    public synchronized void updateProgress(int processed, int successCount, int failCount) {
        this.processed = processed;
        this.successCount = successCount;
        this.failCount = failCount;
        this.updateTime = System.currentTimeMillis();
    }

    public synchronized void addFailDetail(String detail) {
        if (this.failDetails == null) {
            this.failDetails = new ArrayList<>();
        }
        this.failDetails.add(detail);
        this.updateTime = System.currentTimeMillis();
    }
}
