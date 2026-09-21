package cn.wildfirechat.org.jpa.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 第三个数据源：野火 IM 服务自身的库（含 t_user_session 等表），只读。
 * <p>
 * 与 secondaryDataSource（im-app_server 的 appdata 库）不是同一个库，不要混用。
 * 该数据源是可选依赖：只有配置了 spring.im-datasource.hikari.jdbc-url 才会创建，
 * 未配置时本服务照常启动，仅“今日使用情况”接口返回“服务器配置错误”。
 * <p>
 * 实体包 cn.wildfirechat.org.im.jpa 不在 PrimaryDataSourceConfig 的扫描范围
 * （cn.wildfirechat.org.jpa）内，因此不会被主数据源误扫。
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "cn.wildfirechat.org.im.jpa",
    entityManagerFactoryRef = "imEntityManagerFactory",
    transactionManagerRef = "imTransactionManager"
)
@ConditionalOnProperty(prefix = "spring.im-datasource.hikari", name = "jdbc-url", matchIfMissing = false)
public class ImDataSourceConfig {

    @Bean(name = "imDataSource")
    @ConfigurationProperties(prefix = "spring.im-datasource.hikari")
    public DataSource imDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "imEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean imEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("imDataSource") DataSource imDataSource) {
        Map<String, Object> properties = new HashMap<>();
        // 本服务对 IM 库只读，绝不能让 hibernate 去改 IM 服务的表结构
        properties.put("hibernate.hbm2ddl.auto", "none");
        return builder
            .dataSource(imDataSource)
            .packages("cn.wildfirechat.org.im.jpa")
            .persistenceUnit("im")
            .properties(properties)
            .build();
    }

    @Bean(name = "imTransactionManager")
    public PlatformTransactionManager imTransactionManager(
            @Qualifier("imEntityManagerFactory") EntityManagerFactory imEntityManagerFactory) {
        return new JpaTransactionManager(imEntityManagerFactory);
    }
}
