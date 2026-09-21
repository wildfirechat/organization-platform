package cn.wildfirechat.org.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface RelationshipEntityRepository extends CrudRepository<RelationshipEntity, RelationshipID> {
    @Query(value = "select * from t_relationship where employee_id = ?1", nativeQuery = true)
    List<RelationshipEntity> getEmployeeRelationships(String employeeId);

    @Query(value = "select * from t_relationship where organization_id = ?1 and depth >= ?2", nativeQuery = true)
    List<RelationshipEntity> getOrganizationRelationshipsBelowDepth(int organizationId, int depth);

    @Query(value = "select distinct employee_id from t_relationship where organization_id = ?1", nativeQuery = true)
    List<String> getOrganizationMembers(int organizationId);

    @Query(value = "select distinct employee_id from t_relationship where organization_id in (?1) ", nativeQuery = true)
    List<String> getOrganizationBatchMembers(List<Integer> organizationIds);

    /**
     * 取这批员工各自所属的部门名称。bottom = true 表示员工直接挂在该部门下，
     * 因此一个人多部门时会返回多行，调用方自行合并。
     * 返回每行两个元素：[0] employee_id，[1] 部门名称。
     */
    @Query(value = "select r.employee_id, o.name from t_relationship r, t_organization o "
            + "where r.organization_id = o.id and r.bottom = true and r.employee_id in (?1)", nativeQuery = true)
    List<Object[]> getEmployeeDepartmentNames(List<String> employeeIds);
}
