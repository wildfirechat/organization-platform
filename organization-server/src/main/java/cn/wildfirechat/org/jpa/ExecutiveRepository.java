package cn.wildfirechat.org.jpa;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 高管名单。
 *
 * <p>读员工时不要逐行 {@code existsById}（N+1）：用 {@link #findExecutiveIds()} 一次拿到全部
 * 高管 id（表很小），在内存里判断。高管列表用 join 员工表一次查出来。
 *
 * <p>不用 MySQL 专有的 {@code insert ignore}，避免达梦等库不兼容。
 */
public interface ExecutiveRepository extends CrudRepository<ExecutiveEntity, String> {

    /** 全部高管 employeeId（一次查询，供员工读取时批量打标记） */
    @Query(value = "select employee_id from t_executive", nativeQuery = true)
    List<String> findExecutiveIds();

    /** 这批 employeeId 里已经在高管名单中的（一次查询，供批量添加去重） */
    @Query(value = "select employee_id from t_executive where employee_id in ?1", nativeQuery = true)
    List<String> findExistingExecutiveIds(Collection<String> employeeIds);

    /**
     * 高管列表：与员工表 join，按加入时间倒序。
     *
     * <p>用 JPQL 的隐式内连接（返回实体而不是原生 SQL 的 Object[]），既是真的连表，
     * 又能直接拿到 {@link EmployeeEntity}。
     */
    @Query("select e from EmployeeEntity e, ExecutiveEntity x where x.employeeId = e.employeeId "
        + "order by x.createdAt desc")
    List<EmployeeEntity> findExecutiveEmployees();
}
