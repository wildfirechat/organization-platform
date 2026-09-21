package cn.wildfirechat.org.im.jpa;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * 野火 IM 库 t_user_session 的只读访问。
 */
@org.springframework.stereotype.Repository
public interface UserSessionRepository extends Repository<UserSessionEntity, Integer> {

    /**
     * 查一批用户在某时间点之后仍然有效的会话。
     * <p>
     * 说明：
     * 1. _deleted = 0 表示“正常”，1 表示“已登出/过期”（来源：官方《野火IM数据库表结构说明》）；
     * 2. _uid in (...) 走表上已有的 session_uid_index 索引，调用方需自行分片，避免 IN 列表过长；
     * 3. 刻意不继承 CrudRepository：避免有人误调 findAll() 把 IM 库整表拉出来。
     *
     * @param since 时间下界，单位与表中 _dt 一致（毫秒），由调用方换算
     * @param uids  用户 ID 列表，不能为空
     */
    @Query(value = "select * from t_user_session where _deleted = 0 and _dt >= ?1 and _uid in (?2)", nativeQuery = true)
    List<UserSessionEntity> findActiveSessionsSince(long since, List<String> uids);

    /**
     * 取表中 _dt 的最大值，用于推断 _dt 的时间单位（秒/毫秒/微秒）。
     * 官方表结构文档未写明单位，只能按实际数据量级判断；表为空时返回 null。
     */
    @Query(value = "select max(_dt) from t_user_session", nativeQuery = true)
    Long findMaxDt();
}
