package cn.wildfirechat.org.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
public interface OrganizationEntityRepository extends CrudRepository<OrganizationEntity, Integer> {
    @Query(value = "select * from t_organization where parent_id = ?1 order by sort", nativeQuery = true)
    List<OrganizationEntity> findAllByParentId(int parentId);

    @Query(value = "select * from t_organization where parent_id  = 0", nativeQuery = true)
    List<OrganizationEntity> findRootEntity();

    @Query(value = "select * from t_organization where name like %?1%",
            countQuery = "select count(*) from t_organization where name like %?1%",
            nativeQuery = true)
    Page<OrganizationEntity> searchEntity(String keyword, Pageable pageable);

    @Query(value = "update t_organization set member_count = (select count(distinct(employee_id)) from t_relationship where organization_id = ?1) where id = ?1", nativeQuery = true)
    @Modifying
    int updateOrganizationMemberCount(int organizationId);
}
