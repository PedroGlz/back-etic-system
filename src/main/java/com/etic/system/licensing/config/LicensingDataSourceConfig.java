package com.etic.system.licensing.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;

@Configuration(proxyBeanMethods = false)
public class LicensingDataSourceConfig {

	@Bean
	@ConfigurationProperties("app.datasource.licensing")
	DataSourceProperties licensingDataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean
	@ConfigurationProperties("app.datasource.licensing.hikari")
	DataSource licensingDataSource(@Qualifier("licensingDataSourceProperties") DataSourceProperties properties) {
		return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
	}

	@Bean
	NamedParameterJdbcTemplate licensingJdbc(@Qualifier("licensingDataSource") DataSource dataSource) {
		return new NamedParameterJdbcTemplate(dataSource);
	}

	@Bean
	JdbcTransactionManager licensingTransactionManager(@Qualifier("licensingDataSource") DataSource dataSource) {
		return new JdbcTransactionManager(dataSource);
	}
}
