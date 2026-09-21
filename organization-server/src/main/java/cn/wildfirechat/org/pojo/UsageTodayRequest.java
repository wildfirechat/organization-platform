package cn.wildfirechat.org.pojo;

/**
 * 「今日使用情况」查询请求。
 * <p>
 * 统计范围为该部门及其所有子部门的成员（组织库 t_relationship 中 organization_id
 * 已冗余记录了所有祖先部门，因此一次查询即可拿到整棵子树的人员）。
 */
public class UsageTodayRequest {
    /** 部门 ID，必须大于 0 */
    public int organizationId;
    /** 页码，从 0 开始 */
    public int page;
    /** 每页条数 */
    public int count;
}
