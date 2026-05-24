package cn.wildfirechat.org.jpa;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface OperationLogEntityRepository extends CrudRepository<OperationLogEntity, Integer> {
    @Query(value = "select * from t_optlog where timestamp >= ?1",
            countQuery = "select count(*) from t_optlog where timestamp >= ?1",
            nativeQuery = true)
    Page<OperationLogEntity> getLogsByPages(long since, Pageable pageable);
}
