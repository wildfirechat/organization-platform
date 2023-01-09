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
}
