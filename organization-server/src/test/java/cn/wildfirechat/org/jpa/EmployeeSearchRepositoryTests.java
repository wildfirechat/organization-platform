package cn.wildfirechat.org.jpa;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.Database;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 「设置组织领导」候选人搜索用的原生 SQL，在 H2 上验证：
 * 分页有稳定排序、姓名模糊匹配、手机号精确匹配，以及用户输入里的 LIKE 通配符被转义。
 */
public class EmployeeSearchRepositoryTests {

    @Configuration
    @EnableJpaRepositories(
            basePackageClasses = EmployeeEntityRepository.class,
            entityManagerFactoryRef = "entityManagerFactory",
            transactionManagerRef = "transactionManager")
    static class JpaConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:org_employee_search_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("cn.wildfirechat.org.jpa");
            HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
            adapter.setDatabase(Database.H2);
            adapter.setGenerateDdl(true);
            factory.setJpaVendorAdapter(adapter);
            Properties properties = new Properties();
            properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            factory.setJpaProperties(properties);
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }

    private static AnnotationConfigApplicationContext context;
    private static EmployeeEntityRepository employeeRepository;
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    public static void startContext() {
        context = new AnnotationConfigApplicationContext(JpaConfig.class);
        employeeRepository = context.getBean(EmployeeEntityRepository.class);
        jdbcTemplate = context.getBean(JdbcTemplate.class);
    }

    @AfterAll
    public static void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    public void prepareEmployees() {
        jdbcTemplate.update("delete from t_employee");
        jdbcTemplate.update("delete from t_relationship");

        // 120 个普通成员，sort 与工号相反，用来验证分页确实按 sort 排而不是按主键/插入顺序
        for (int i = 1; i <= 120; i++) {
            String employeeId = String.format("emp%03d", i);
            String name = String.format("员工%03d", i);
            addEmployee(employeeId, name, "1380000" + String.format("%04d", i), 1000 - i);
        }

        // 名字里带 LIKE 通配符的成员，用来验证转义
        addEmployee("percent", "100%", "13900000000", 2000);
    }

    private void addEmployee(String employeeId, String name, String mobile, int sort) {
        jdbcTemplate.update(
                "insert into t_employee(employee_id, organization_id, name, mobile, level, type, gender, sort, create_dt, update_dt) "
                        + "values (?, 1, ?, ?, 0, 0, 0, ?, 1, 1)",
                employeeId, name, mobile, sort);
        jdbcTemplate.update(
                "insert into t_relationship(employee_id, organization_id, depth, bottom, parent_organization_id, create_dt, update_dt) "
                        + "values (?, 1, 0, true, 0, 1, 1)",
                employeeId);
    }

    @Test
    public void searchByOrganizationIsPagedAndOrderedBySort() {
        Page<EmployeeEntity> first = employeeRepository.searchByOrganization(1, PageRequest.of(0, 50));
        Page<EmployeeEntity> second = employeeRepository.searchByOrganization(1, PageRequest.of(1, 50));

        assertEquals(121L, first.getTotalElements());
        assertEquals(50, first.getContent().size());
        // sort 越小越靠前：emp120 的 sort 是 880，emp001 是 999
        assertEquals("emp120", first.getContent().get(0).employeeId);
        assertEquals("emp071", first.getContent().get(49).employeeId);
        assertEquals("emp070", second.getContent().get(0).employeeId);
    }

    @Test
    public void searchByKeywordMatchesNameOrExactMobile() {
        Page<EmployeeEntity> byName = employeeRepository.searchByKeywordAndOrganization(
                "员工100", "员工100", 1, PageRequest.of(0, 50));
        assertEquals(1L, byName.getTotalElements());
        assertEquals("emp100", byName.getContent().get(0).employeeId);

        Page<EmployeeEntity> byMobile = employeeRepository.searchByKeywordAndOrganization(
                "不存在", "13800000100", 1, PageRequest.of(0, 50));
        assertEquals(1L, byMobile.getTotalElements());
        assertEquals("emp100", byMobile.getContent().get(0).employeeId);
    }

    @Test
    public void searchByKeywordEscapesLikeWildcards() {
        // 服务层会把 % 转成 !%，SQL 用 escape '!' 后只匹配名字里真的带 % 的人
        Page<EmployeeEntity> page = employeeRepository.searchByKeywordAndOrganization(
                "!%", "!%", 1, PageRequest.of(0, 50));

        assertEquals(1L, page.getTotalElements());
        assertEquals("percent", page.getContent().get(0).employeeId);
    }
}
