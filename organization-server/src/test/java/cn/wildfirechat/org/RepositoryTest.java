package cn.wildfirechat.org;

import cn.wildfirechat.org.jpa.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Repository Tests with Multiple Databases
 * Supports Dameng, H2, and MySQL databases
 *
 * Usage:
 *   -Ddb.type=dameng  : Use Dameng database (default)
 *   -Ddb.type=h2      : Use H2 in-memory database
 *   -Ddb.type=mysql   : Use MySQL database
 */
public class RepositoryTest {

    private EntityManagerFactory emf;
    private EntityManager em;
    private String dbType;
    private boolean initialized = false;

    @Before
    public void setUp() {
        // Get database type from system property, default to dameng
        dbType = System.getProperty("db.type", "dameng");

        System.out.println("========================================");
        System.out.println("Using database: " + dbType.toUpperCase());
        System.out.println("========================================");

        Map<String, String> props = new HashMap<>();

        switch (dbType.toLowerCase()) {
            default:
            case "h2":
                // H2 内存数据库配置
                props.put("javax.persistence.jdbc.url", "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL");
                props.put("javax.persistence.jdbc.driver", "org.h2.Driver");
                props.put("javax.persistence.jdbc.user", "sa");
                props.put("javax.persistence.jdbc.password", "");
                props.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
                props.put("hibernate.hbm2ddl.auto", "create-drop");
                props.put("hibernate.show_sql", "false");
                break;

            case "mysql":
                // MySQL 数据库配置
                // 需要先创建数据库: CREATE DATABASE organization_server CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
                props.put("javax.persistence.jdbc.url", "jdbc:mysql://localhost:3306/organization_server?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true");
                props.put("javax.persistence.jdbc.driver", "com.mysql.cj.jdbc.Driver");
                props.put("javax.persistence.jdbc.user", "root");
                props.put("javax.persistence.jdbc.password", "123456");
                props.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
                props.put("hibernate.hbm2ddl.auto", "update");
                props.put("hibernate.show_sql", "true");
                props.put("hibernate.format_sql", "true");
                break;

            case "dameng":
                // 达梦数据库配置
                props.put("javax.persistence.jdbc.driver", "dm.jdbc.driver.DmDriver");
                props.put("javax.persistence.jdbc.url", "jdbc:dm://192.168.1.6:5237");
                props.put("javax.persistence.jdbc.user", "SYSDBA");
                props.put("javax.persistence.jdbc.password", "Wfc123!@");
                props.put("hibernate.dialect", "org.hibernate.dialect.DmDialect");
                props.put("hibernate.hbm2ddl.auto", "update");
                props.put("hibernate.show_sql", "true");
                props.put("hibernate.format_sql", "true");
                break;
        }

        try {
            emf = Persistence.createEntityManagerFactory("organization-persistence", props);
            em = emf.createEntityManager();

            // 清理测试数据（仅达梦和 MySQL 需要）
            if (!"h2".equals(dbType)) {
                cleanupTestData();
            }
            initialized = true;
        } catch (Exception e) {
            initialized = false;
            throw new RuntimeException("Failed to initialize database connection: " + e.getMessage(), e);
        }
    }

    private void cleanupTestData() {
        if (em == null || !em.isOpen()) return;

        try {
            em.getTransaction().begin();
            em.createNativeQuery("DELETE FROM t_relationship").executeUpdate();
            em.createNativeQuery("DELETE FROM t_employee").executeUpdate();
            em.createNativeQuery("DELETE FROM t_organization").executeUpdate();
            em.createNativeQuery("DELETE FROM t_user").executeUpdate();
            em.createNativeQuery("DELETE FROM t_optlog").executeUpdate();
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
        }
    }

    @After
    public void tearDown() {
        if (em != null && em.isOpen()) {
            em.close();
        }
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    // ==================== Organization Tests ====================

    @Test
    public void testSaveOrganization() {
        em.getTransaction().begin();

        OrganizationEntity org = new OrganizationEntity();
        org.name = "测试部门";
        org.parentId = 0;
        org.description = "部门描述";
        org.sort = 0;
        org.memberCount = 0;
        org.createDt = System.currentTimeMillis();
        org.updateDt = org.createDt;
        em.persist(org);

        em.getTransaction().commit();

        assertTrue(org.id > 0);
        assertEquals("测试部门", org.name);
    }

    @Test
    public void testFindOrganizationById() {
        em.getTransaction().begin();

        OrganizationEntity org = new OrganizationEntity();
        org.name = "查询测试";
        org.parentId = 0;
        org.createDt = System.currentTimeMillis();
        org.updateDt = org.createDt;
        em.persist(org);

        em.getTransaction().commit();

        OrganizationEntity found = em.find(OrganizationEntity.class, org.id);
        assertNotNull(found);
        assertEquals("查询测试", found.name);
    }

    @Test
    public void testFindNonExistentOrganization() {
        OrganizationEntity found = em.find(OrganizationEntity.class, 99999);
        assertNull(found);
    }

    @Test
    public void testUpdateOrganization() {
        em.getTransaction().begin();

        OrganizationEntity org = new OrganizationEntity();
        org.name = "原名称";
        org.parentId = 0;
        org.createDt = System.currentTimeMillis();
        org.updateDt = org.createDt;
        em.persist(org);
        em.flush();

        org.name = "新名称";
        org.description = "新描述";
        em.persist(org);
        em.flush();

        em.getTransaction().commit();

        OrganizationEntity updated = em.find(OrganizationEntity.class, org.id);
        assertEquals("新名称", updated.name);
        assertEquals("新描述", updated.description);
    }

    @Test
    public void testDeleteOrganization() {
        em.getTransaction().begin();

        OrganizationEntity org = new OrganizationEntity();
        org.name = "待删除";
        org.parentId = 0;
        org.createDt = System.currentTimeMillis();
        org.updateDt = org.createDt;
        em.persist(org);
        int id = org.id;
        em.flush();

        em.remove(org);
        em.flush();

        em.getTransaction().commit();

        OrganizationEntity found = em.find(OrganizationEntity.class, id);
        assertNull(found);
    }

    // ==================== Employee Tests ====================

    @Test
    public void testSaveEmployee() {
        em.getTransaction().begin();

        OrganizationEntity org = new OrganizationEntity();
        org.name = "员工部门";
        org.parentId = 0;
        org.createDt = System.currentTimeMillis();
        org.updateDt = org.createDt;
        em.persist(org);
        em.flush();

        EmployeeEntity emp = new EmployeeEntity();
        emp.employeeId = "emp_001";
        emp.organizationId = org.id;
        emp.name = "测试员工";
        emp.title = "工程师";
        emp.level = 1;
        emp.mobile = "13800138000";
        emp.email = "test@example.com";
        emp.gender = 1;
        emp.city = "北京";
        emp.type = 0;
        emp.createDt = System.currentTimeMillis();
        emp.updateDt = emp.createDt;
        em.persist(emp);

        em.getTransaction().commit();

        assertEquals("emp_001", emp.employeeId);
        assertEquals("测试员工", emp.name);
    }

    @Test
    public void testFindEmployeeById() {
        em.getTransaction().begin();

        OrganizationEntity org = new OrganizationEntity();
        org.name = "查询部门";
        org.parentId = 0;
        org.createDt = System.currentTimeMillis();
        org.updateDt = org.createDt;
        em.persist(org);
        em.flush();

        EmployeeEntity emp = new EmployeeEntity();
        emp.employeeId = "emp_002";
        emp.organizationId = org.id;
        emp.name = "查询员工";
        emp.mobile = "13800138001";
        emp.email = "test2@example.com";
        emp.createDt = System.currentTimeMillis();
        emp.updateDt = emp.createDt;
        em.persist(emp);

        em.getTransaction().commit();

        EmployeeEntity found = em.find(EmployeeEntity.class, "emp_002");
        assertNotNull(found);
        assertEquals("查询员工", found.name);
    }

    @Test
    public void testUpdateEmployee() {
        em.getTransaction().begin();

        OrganizationEntity org = new OrganizationEntity();
        org.name = "更新部门";
        org.parentId = 0;
        org.createDt = System.currentTimeMillis();
        org.updateDt = org.createDt;
        em.persist(org);
        em.flush();

        EmployeeEntity emp = new EmployeeEntity();
        emp.employeeId = "emp_003";
        emp.organizationId = org.id;
        emp.name = "原名称";
        emp.mobile = "13800138002";
        emp.email = "test3@example.com";
        emp.createDt = System.currentTimeMillis();
        emp.updateDt = emp.createDt;
        em.persist(emp);
        em.flush();

        emp.name = "新名称";
        emp.title = "高级工程师";
        em.persist(emp);
        em.flush();

        em.getTransaction().commit();

        EmployeeEntity updated = em.find(EmployeeEntity.class, "emp_003");
        assertEquals("新名称", updated.name);
        assertEquals("高级工程师", updated.title);
    }

    @Test
    public void testDeleteEmployee() {
        em.getTransaction().begin();

        OrganizationEntity org = new OrganizationEntity();
        org.name = "删除部门";
        org.parentId = 0;
        org.createDt = System.currentTimeMillis();
        org.updateDt = org.createDt;
        em.persist(org);
        em.flush();

        EmployeeEntity emp = new EmployeeEntity();
        emp.employeeId = "emp_004";
        emp.organizationId = org.id;
        emp.name = "删除员工";
        emp.mobile = "13800138003";
        emp.email = "test4@example.com";
        emp.createDt = System.currentTimeMillis();
        emp.updateDt = emp.createDt;
        em.persist(emp);
        em.flush();

        em.remove(emp);
        em.flush();

        em.getTransaction().commit();

        EmployeeEntity found = em.find(EmployeeEntity.class, "emp_004");
        assertNull(found);
    }

    // ==================== Relationship Tests ====================

    @Test
    public void testSaveRelationship() {
        em.getTransaction().begin();

        RelationshipEntity rel = new RelationshipEntity();
        rel.employeeId = "emp_005";
        rel.organizationId = 1;
        rel.depth = 0;
        rel.bottom = true;
        rel.parentOrganizationId = 0;
        rel.createDt = System.currentTimeMillis();
        rel.updateDt = rel.createDt;
        em.persist(rel);

        em.getTransaction().commit();

        assertEquals("emp_005", rel.employeeId);
        assertEquals(1, rel.organizationId);
    }

    @Test
    public void testFindRelationshipById() {
        em.getTransaction().begin();

        RelationshipEntity rel = new RelationshipEntity();
        rel.employeeId = "emp_006";
        rel.organizationId = 1;
        rel.depth = 0;
        rel.bottom = true;
        rel.parentOrganizationId = 0;
        rel.createDt = System.currentTimeMillis();
        rel.updateDt = rel.createDt;
        em.persist(rel);
        em.flush();

        em.getTransaction().commit();

        RelationshipID id = new RelationshipID();
        id.employeeId = "emp_006";
        id.organizationId = 1;
        id.depth = 0;
        id.bottom = true;

        RelationshipEntity found = em.find(RelationshipEntity.class, id);
        assertNotNull(found);
    }

    // ==================== User Tests ====================

    @Test
    public void testSaveUser() {
        em.getTransaction().begin();

        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        User user = new User();
        user.setAccount("test_user_" + uniqueSuffix);
        user.setMobile("139000000" + String.format("%02d", new Random().nextInt(100)));
        user.setEmail("user_" + uniqueSuffix + "@test.com");
        user.setRole("user");
        user.setSalt("salt");
        user.setPasswordMd5("password_hash");
        em.persist(user);

        em.getTransaction().commit();

        assertNotNull(user.getAccount());
    }

    @Test
    public void testFindUserByAccount() {
        em.getTransaction().begin();

        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        User user = new User();
        user.setAccount("find_user_" + uniqueSuffix);
        user.setMobile("139000001" + String.format("%02d", new Random().nextInt(100)));
        user.setEmail("find_" + uniqueSuffix + "@test.com");
        user.setSalt("salt");
        user.setPasswordMd5("hash");
        em.persist(user);

        em.getTransaction().commit();

        User found = em.find(User.class, user.getAccount());
        assertNotNull(found);
    }

    @Test
    public void testUpdateUser() {
        em.getTransaction().begin();

        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        User user = new User();
        user.setAccount("update_user_" + uniqueSuffix);
        user.setMobile("139000002" + String.format("%02d", new Random().nextInt(100)));
        user.setEmail("original_" + uniqueSuffix + "@test.com");
        user.setSalt("salt");
        user.setPasswordMd5("original_hash");
        em.persist(user);
        em.flush();

        user.setEmail("updated_" + uniqueSuffix + "@test.com");
        user.setPasswordMd5("new_hash");
        em.persist(user);
        em.flush();

        em.getTransaction().commit();

        User updated = em.find(User.class, user.getAccount());
        assertTrue(updated.getEmail().contains("updated_"));
        assertEquals("new_hash", updated.getPasswordMd5());
    }

    @Test
    public void testDeleteUser() {
        em.getTransaction().begin();

        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        User user = new User();
        user.setAccount("delete_user_" + uniqueSuffix);
        user.setMobile("139000003" + String.format("%02d", new Random().nextInt(100)));
        user.setEmail("delete_" + uniqueSuffix + "@test.com");
        user.setSalt("salt");
        user.setPasswordMd5("hash");
        em.persist(user);
        em.flush();

        em.remove(user);
        em.flush();

        em.getTransaction().commit();

        User found = em.find(User.class, user.getAccount());
        assertNull(found);
    }

    // ==================== Operation Log Tests ====================

    @Test
    public void testSaveOperationLog() {
        em.getTransaction().begin();

        OperationLogEntity log = new OperationLogEntity();
        log.userId = "admin";
        log.operation = "LOGIN";
        log.operationDesc = "用户登录";
        log.timestamp = System.currentTimeMillis();
        em.persist(log);

        em.getTransaction().commit();

        assertTrue(log.id > 0);
        assertEquals("LOGIN", log.operation);
    }

    @Test
    public void testFindOperationLogById() {
        em.getTransaction().begin();

        OperationLogEntity log = new OperationLogEntity();
        log.userId = "admin";
        log.operation = "TEST_OP";
        log.operationDesc = "测试操作";
        log.timestamp = System.currentTimeMillis();
        em.persist(log);
        em.flush();

        em.getTransaction().commit();

        OperationLogEntity found = em.find(OperationLogEntity.class, log.id);
        assertNotNull(found);
        assertEquals("TEST_OP", found.operation);
    }

    // ==================== Integrated Tests ====================

    @Test
    public void testOrganizationHierarchy() {
        em.getTransaction().begin();

        OrganizationEntity root = new OrganizationEntity();
        root.name = "公司总部";
        root.parentId = 0;
        root.createDt = System.currentTimeMillis();
        root.updateDt = root.createDt;
        em.persist(root);

        OrganizationEntity dept1 = new OrganizationEntity();
        dept1.name = "技术部";
        dept1.parentId = root.id;
        dept1.createDt = System.currentTimeMillis();
        dept1.updateDt = dept1.createDt;
        em.persist(dept1);

        OrganizationEntity subDept = new OrganizationEntity();
        subDept.name = "研发组";
        subDept.parentId = dept1.id;
        subDept.createDt = System.currentTimeMillis();
        subDept.updateDt = subDept.createDt;
        em.persist(subDept);

        em.getTransaction().commit();

        // Verify hierarchy
        OrganizationEntity foundRoot = em.find(OrganizationEntity.class, root.id);
        assertEquals("公司总部", foundRoot.name);

        // Query children using JPQL
        List<OrganizationEntity> children = em.createQuery(
            "SELECT o FROM OrganizationEntity o WHERE o.parentId = :parentId", OrganizationEntity.class)
            .setParameter("parentId", root.id)
            .getResultList();
        assertEquals(1, children.size());
        assertEquals("技术部", children.get(0).name);
    }

    @Test
    public void testEmployeeWithOrganization() {
        em.getTransaction().begin();

        OrganizationEntity org = new OrganizationEntity();
        org.name = "测试部门";
        org.parentId = 0;
        org.createDt = System.currentTimeMillis();
        org.updateDt = org.createDt;
        em.persist(org);

        EmployeeEntity emp = new EmployeeEntity();
        emp.employeeId = "emp_hier_001";
        emp.organizationId = org.id;
        emp.name = "层级员工";
        emp.mobile = "13800138010";
        emp.email = "hier@test.com";
        emp.createDt = System.currentTimeMillis();
        emp.updateDt = emp.createDt;
        em.persist(emp);

        RelationshipEntity rel = new RelationshipEntity();
        rel.employeeId = "emp_hier_001";
        rel.organizationId = org.id;
        rel.depth = 0;
        rel.bottom = true;
        rel.parentOrganizationId = 0;
        rel.createDt = System.currentTimeMillis();
        rel.updateDt = rel.createDt;
        em.persist(rel);

        em.getTransaction().commit();

        // Verify employee was created with correct organization
        EmployeeEntity foundEmp = em.find(EmployeeEntity.class, "emp_hier_001");
        assertNotNull(foundEmp);
        assertEquals(org.id, foundEmp.organizationId);
        assertEquals("层级员工", foundEmp.name);
    }
}
