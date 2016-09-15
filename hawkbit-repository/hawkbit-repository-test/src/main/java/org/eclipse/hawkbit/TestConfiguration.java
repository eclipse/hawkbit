/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.eclipse.hawkbit.cache.DefaultDownloadIdCache;
import org.eclipse.hawkbit.cache.DownloadIdCache;
import org.eclipse.hawkbit.cache.TenancyCacheManager;
import org.eclipse.hawkbit.cache.TenantAwareCacheManager;
import org.eclipse.hawkbit.repository.jpa.model.helper.EventPublisherHolder;
import org.eclipse.hawkbit.repository.test.util.JpaTestRepositoryManagement;
import org.eclipse.hawkbit.repository.test.util.TestRepositoryManagement;
import org.eclipse.hawkbit.repository.test.util.TestdataFactory;
import org.eclipse.hawkbit.security.DdiSecurityProperties;
import org.eclipse.hawkbit.security.SecurityContextTenantAware;
import org.eclipse.hawkbit.security.SpringSecurityAuditorAware;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.guava.GuavaCacheManager;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.data.domain.AuditorAware;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;

import com.mongodb.MongoClientOptions;

/**
 * Spring context configuration required for Dev.Environment.
 *
 *
 *
 */
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true, mode = AdviceMode.PROXY, proxyTargetClass = false, securedEnabled = true)
@EnableConfigurationProperties({ HawkbitServerProperties.class, DdiSecurityProperties.class })
@Profile("test")
@EnableAutoConfiguration
public class TestConfiguration implements AsyncConfigurer {
    @Bean
    public TestRepositoryManagement testRepositoryManagement() {
        return new JpaTestRepositoryManagement();
    }

    @Bean
    public TestdataFactory testdataFactory() {
        return new TestdataFactory();
    }

    @Bean
    public MongoClientOptions options() {
        return MongoClientOptions.builder().connectTimeout(500).maxWaitTime(500).connectionsPerHost(2)
                .serverSelectionTimeout(500).build();

    }

    @Bean
    public TenantAware tenantAware() {
        return new SecurityContextTenantAware();
    }

    @Bean
    public TenancyCacheManager cacheManager() {
        return new TenantAwareCacheManager(new GuavaCacheManager(), tenantAware());
    }

    /**
     * Bean for the download id cache.
     * 
     * @return the cache
     */
    public DownloadIdCache downloadIdCache() {
        return new DefaultDownloadIdCache(cacheManager());
    }

    @Bean(name = AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME)
    public SimpleApplicationEventMulticaster applicationEventMulticaster() {
        final SimpleApplicationEventMulticaster simpleApplicationEventMulticaster = new SimpleApplicationEventMulticaster();
        simpleApplicationEventMulticaster.setTaskExecutor(asyncExecutor());
        return simpleApplicationEventMulticaster;
    }

    @Bean
    public EventPublisherHolder eventBusHolder() {
        return EventPublisherHolder.getInstance();
    }

    @Bean
    public Executor asyncExecutor() {
        return new DelegatingSecurityContextExecutorService(Executors.newSingleThreadExecutor());
    }

    @Bean
    public AuditorAware<String> auditorAware() {
        return new SpringSecurityAuditorAware();
    }

    @Override
    public Executor getAsyncExecutor() {
        return asyncExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }

}
