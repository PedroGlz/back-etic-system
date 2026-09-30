package com.etic.system.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;

@Configuration(proxyBeanMethods = false)
public class EticDataSourceConfig {

	@Bean
	@Primary
	@ConfigurationProperties("spring.datasource")
	DataSourceProperties eticDataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean
	@Primary
	@ConfigurationProperties("spring.datasource.hikari")
	DataSource dataSource(@Qualifier("eticDataSourceProperties") DataSourceProperties properties) {
		return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
	}

	@Bean
	@Primary
	JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}

	@Bean
	@Primary
	NamedParameterJdbcTemplate namedParameterJdbcTemplate(@Qualifier("jdbcTemplate") JdbcTemplate jdbcTemplate) {
		return new NamedParameterJdbcTemplate(jdbcTemplate);
	}

	@Bean
	@Primary
	JdbcClient jdbcClient(@Qualifier("jdbcTemplate") JdbcTemplate jdbcTemplate) {
		return JdbcClient.create(jdbcTemplate);
	}

	@Bean
	@Primary
	JdbcTransactionManager transactionManager(@Qualifier("dataSource") DataSource dataSource) {
		return new JdbcTransactionManager(dataSource);
	}
}
