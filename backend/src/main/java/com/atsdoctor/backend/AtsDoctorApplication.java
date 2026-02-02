package com.atsdoctor.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * The persistence stack (DataSource + Flyway + JPA) is intentionally excluded
 * here: the app must boot without a database (TASK-006). It is re-enabled only
 * when {@code ats.doctor.persistence.enabled=true} via
 * {@link com.atsdoctor.backend.infrastructure.persistence.PersistenceConfig}.
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        FlywayAutoConfiguration.class
})
@ConfigurationPropertiesScan
public class AtsDoctorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtsDoctorApplication.class, args);
    }
}
