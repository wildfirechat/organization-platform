package cn.wildfirechat.org.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeEntityRepository extends CrudRepository<EmployeeEntity, String> {
    Optional<EmployeeEntity> findByEmployeeId(String employeeId);


    @Query(value = "select * from t_employee where employee_id in (select distinct(employee_id) from t_relationship where organization_id = ?1 and bottom = true )", nativeQuery = true)
    List<EmployeeEntity> findByOrganizationId(int organizationId);

    @Query(value = "select * from t_employee where (name like %?1% or mobile = ?1) and employee_id in (select distinct(employee_id) from t_relationship where organization_id = ?2 ) ",
            countQuery = "select count(*) from t_employee where (name like %?1% or mobile = ?1) and employee_id in (select distinct(employee_id) from t_relationship where organization_id = ?2 )",
            nativeQuery = true)
    Page<EmployeeEntity> searchByKeywordAndOrganization(String keyword, int organizationId, Pageable pageable);

    @Query(value = "select * from t_employee where (name like %?1% or mobile = ?1) and employee_id in (select distinct(employee_id) from t_relationship where organization_id = ?2 and bottom = true ) ",
            countQuery = "select count(*) from t_employee where (name like %?1% or mobile = ?1) and employee_id in (select distinct(employee_id) from t_relationship where organization_id = ?2 and bottom = true )",
            nativeQuery = true)
    Page<EmployeeEntity> searchByKeywordAndOrganizationRoot(String keyword, int organizationId, Pageable pageable);

    //这里搜索多个字段有问题，待解决
//    @Query(value = "select * from t_employee where name like %?1% or title like %?1% or mobile = ?1 or email = ?1",
//            countQuery = "select count(*) from t_employee where name like %?1% or title like %?1% or mobile = ?1 or email = ?1",
//            nativeQuery = true)
    @Query(value = "select * from t_employee where name like %?1% or mobile = ?1",
            countQuery = "select count(*) from t_employee where name like %?1% or mobile = ?1",
            nativeQuery = true)
    Page<EmployeeEntity> searchByKeyword(String keyword, Pageable pageable);

    @Query(value = "select * from t_employee where employee_id in (select distinct(employee_id) from t_relationship where organization_id = ?1)",
            countQuery = "select count(distinct(employee_id)) from t_relationship where organization_id = ?1",
            nativeQuery = true)
    Page<EmployeeEntity> searchByOrganization(int organizationId, Pageable pageable);

    @Query(value = "select * from t_employee where employee_id in (select distinct(employee_id) from t_relationship where organization_id = ?1 and bottom = true)",
            countQuery = "select count(distinct(employee_id)) from t_relationship where organization_id = ?1 and bottom = true",
            nativeQuery = true)
    Page<EmployeeEntity> searchByOrganizationRoot(int organizationId, Pageable pageable);
}
