package cn.wildfirechat.org.pojo;

import javax.persistence.Column;

public class OrganizationPojo {
    public int id;
    public int parentId;
    public String managerId;
    public String name;
    public String description;
    public String portraitUrl;
    public String tel;
    public String office;
    public String groupId;
    public int memberCount;
    public int sort;
    public long updateDt;
    public long createDt;
}
