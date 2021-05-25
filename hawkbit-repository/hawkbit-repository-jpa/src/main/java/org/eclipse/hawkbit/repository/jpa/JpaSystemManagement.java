/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;

import org.eclipse.hawkbit.artifact.repository.ArtifactRepository;
import org.eclipse.hawkbit.cache.TenancyCacheManager;
import org.eclipse.hawkbit.repository.RolloutStatusCache;
import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.repository.TenantStatsManagement;
import org.eclipse.hawkbit.repository.exception.EntityNotFoundException;
import org.eclipse.hawkbit.repository.jpa.configuration.Constants;
import org.eclipse.hawkbit.repository.jpa.configuration.MultiTenantJpaTransactionManager;
import org.eclipse.hawkbit.repository.jpa.model.JpaDistributionSetType;
import org.eclipse.hawkbit.repository.jpa.model.JpaSoftwareModuleType;
import org.eclipse.hawkbit.repository.jpa.model.JpaTenantMetaData;
import org.eclipse.hawkbit.repository.jpa.utils.DeploymentHelper;
import org.eclipse.hawkbit.repository.model.DistributionSetType;
import org.eclipse.hawkbit.repository.model.SoftwareModuleType;
import org.eclipse.hawkbit.repository.model.TenantMetaData;
import org.eclipse.hawkbit.repository.report.model.SystemUsageReport;
import org.eclipse.hawkbit.repository.report.model.SystemUsageReportWithTenants;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.eclipse.persistence.config.PersistenceUnitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.vendor.Database;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * JPA implementation of {@link SystemManagement}.
 *
 */
@Transactional(readOnly = true)
@Validated
public class JpaSystemManagement implements CurrentTenantCacheKeyGenerator, SystemManagement {

    private static final Logger LOGGER = LoggerFactory.getLogger(JpaSystemManagement.class);

    private static final int MAX_TENANTS_QUERY = 1000;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TargetRepository targetRepository;

    @Autowired
    private TargetFilterQueryRepository targetFilterQueryRepository;

    @Autowired
    private DistributionSetRepository distributionSetRepository;

    @Autowired
    private SoftwareModuleRepository softwareModuleRepository;

    @Autowired
    private TenantMetaDataRepository tenantMetaDataRepository;

    @Autowired
    private DistributionSetTypeRepository distributionSetTypeRepository;

    @Autowired
    private SoftwareModuleTypeRepository softwareModuleTypeRepository;

    @Autowired
    private TargetTagRepository targetTagRepository;

    @Autowired
    private DistributionSetTagRepository distributionSetTagRepository;

    @Autowired
    private TenantConfigurationRepository tenantConfigurationRepository;

    @Autowired
    private RolloutRepository rolloutRepository;

    @Autowired
    private TenantAware tenantAware;

    @Autowired
    private TenantStatsManagement systemStatsManagement;

    @Autowired
    private TenancyCacheManager cacheManager;

    @Autowired
    private SystemManagementCacheKeyGenerator currentTenantCacheKeyGenerator;

    @Autowired
    private SystemSecurityContext systemSecurityContext;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private RolloutStatusCache rolloutStatusCache;

    @Autowired
    private ArtifactRepository artifactRepository;

    private final String countArtifactQuery;
    private final String countSoftwareModulesQuery;

    /**
     * Constructor.
     * 
     * @param properties
     *            properties to get the underlying database
     */
    public JpaSystemManagement(final JpaProperties properties) {

        final String isDeleted = isPostgreSql(properties) ? "false" : "0";
        countArtifactQuery = "SELECT COUNT(a.id) FROM sp_artifact a INNER JOIN sp_base_software_module sm ON a.software_module = sm.id WHERE sm.deleted = "
                + isDeleted;
        countSoftwareModulesQuery = "select SUM(file_size) from sp_artifact a INNER JOIN sp_base_software_module sm ON a.software_module = sm.id WHERE sm.deleted = "
                + isDeleted;
    }

    @Override
    public SystemUsageReport getSystemUsageStatistics() {

        final Number count = (Number) entityManager.createNativeQuery(countSoftwareModulesQuery).getSingleResult();

        long sumOfArtifacts = 0;
        if (count != null) {
            sumOfArtifacts = count.longValue();
        }

        // we use native queries to punch through the tenant boundaries. This
        // has to be used with care!
        final long targets = ((Number) entityManager.createNativeQuery("SELECT COUNT(id) FROM sp_target")
                .getSingleResult()).longValue();

        final long artifacts = ((Number) entityManager.createNativeQuery(countArtifactQuery).getSingleResult())
                .longValue();

        final long actions = ((Number) entityManager.createNativeQuery("SELECT COUNT(id) FROM sp_action")
                .getSingleResult()).longValue();

        return new SystemUsageReportWithTenants(targets, artifacts, actions, sumOfArtifacts,
                tenantMetaDataRepository.count());
    }

    private static boolean isPostgreSql(final JpaProperties properties) {
        return Database.POSTGRESQL == properties.getDatabase();
    }

    @Override
    public SystemUsageReportWithTenants getSystemUsageStatisticsWithTenants() {
        final SystemUsageReportWithTenants result = (SystemUsageReportWithTenants) getSystemUsageStatistics();

        usageStatsPerTenant(result);

        return result;
    }

    private void usageStatsPerTenant(final SystemUsageReportWithTenants report) {
        forEachTenant(tenant -> report.addTenantData(systemStatsManagement.getStatsOfTenant()));
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public KeyGenerator currentTenantKeyGenerator() {
        return currentTenantCacheKeyGenerator.currentTenantKeyGenerator();
    }

    @Override
    public TenantMetaData getTenantMetadata(final String tenant) {
        final TenantMetaData result = tenantMetaDataRepository.findByTenantIgnoreCase(tenant);
        // Create if it does not exist
        if (result == null) {
            try {
                currentTenantCacheKeyGenerator.getCreateInitialTenant().set(tenant);
                return createInitialTenantMetaData(tenant);

            } finally {
                currentTenantCacheKeyGenerator.getCreateInitialTenant().remove();
            }
        }
        return result;
    }

    /**
     * Creating the initial tenant meta-data in a new transaction. Due the
     * {@link MultiTenantJpaTransactionManager} is using the current tenant to
     * set the necessary tenant discriminator to the query. This is not working
     * if we don't have a current tenant set. Due the
     * {@link #getTenantMetadata(String)} is maybe called without having a
     * current tenant we need to re-open a new transaction so the
     * {@link MultiTenantJpaTransactionManager} is called again and set the
     * tenant for this transaction.
     * 
     * @param tenant
     *            the tenant to be created
     * @return the initial created {@link TenantMetaData}
     */
    private TenantMetaData createInitialTenantMetaData(final String tenant) {
        return systemSecurityContext.runAsSystemAsTenant(() -> {
            try {
                return DeploymentHelper.runInNewTransaction(txManager, "initial-tenant-creation", status -> {
                    final DistributionSetType defaultDsType = createStandardSoftwareDataSetup();
                    return tenantMetaDataRepository.save(new JpaTenantMetaData(defaultDsType, tenant));
                });
            } catch (final TransactionExecutionException e) {
                LOGGER.error("Failed to create initial tenant metadata for tenant {}", tenant, e);
                return null;
            }
        }, tenant);
    }

    @Override
    public Page<String> findTenants(final Pageable pageable) {
        final Page<JpaTenantMetaData> result = tenantMetaDataRepository.findAll(pageable);

        return new PageImpl<>(
                Collections.unmodifiableList(
                        result.getContent().stream().map(TenantMetaData::getTenant).collect(Collectors.toList())),
                pageable, result.getTotalElements());
    }

    @Override
    @Transactional
    @Retryable(include = {
            ConcurrencyFailureException.class }, maxAttempts = Constants.TX_RT_MAX, backoff = @Backoff(delay = Constants.TX_RT_DELAY))
    public void deleteTenant(final String t) {
        final String tenant = t.toUpperCase();
        cacheManager.evictCaches(tenant);
        rolloutStatusCache.evictCaches(tenant);
        tenantAware.runAsTenant(tenant, () -> {
            entityManager.setProperty(PersistenceUnitProperties.MULTITENANT_PROPERTY_DEFAULT, tenant);
            tenantMetaDataRepository.deleteByTenantIgnoreCase(tenant);
            tenantConfigurationRepository.deleteByTenant(tenant);
            targetRepository.deleteByTenant(tenant);
            targetFilterQueryRepository.deleteByTenant(tenant);
            rolloutRepository.deleteByTenant(tenant);
            targetTagRepository.deleteByTenant(tenant);
            distributionSetTagRepository.deleteByTenant(tenant);
            distributionSetRepository.deleteByTenant(tenant);
            distributionSetTypeRepository.deleteByTenant(tenant);
            softwareModuleRepository.deleteByTenant(tenant);
            artifactRepository.deleteByTenant(tenant);
            softwareModuleTypeRepository.deleteByTenant(tenant);
            return null;
        });
    }

    @Override
    public TenantMetaData getTenantMetadata() {
        if (tenantAware.getCurrentTenant() == null) {
            throw new IllegalStateException("Tenant not set");
        }

        return getTenantMetadata(tenantAware.getCurrentTenant());
    }

    @Override
    @Cacheable(value = "currentTenant", keyGenerator = "currentTenantKeyGenerator", cacheManager = "directCacheManager", unless = "#result == null")
    public String currentTenant() {
        final String initialTenantCreation = currentTenantCacheKeyGenerator.getCreateInitialTenant().get();
        if (initialTenantCreation == null) {
            final TenantMetaData findByTenant = tenantMetaDataRepository
                    .findByTenantIgnoreCase(tenantAware.getCurrentTenant());
            return findByTenant != null ? findByTenant.getTenant() : null;
        }
        return initialTenantCreation;
    }

    @Override
    @Transactional
    @Retryable(include = {
            ConcurrencyFailureException.class }, maxAttempts = Constants.TX_RT_MAX, backoff = @Backoff(delay = Constants.TX_RT_DELAY))
    public TenantMetaData updateTenantMetadata(final long defaultDsType) {
        final JpaTenantMetaData data = (JpaTenantMetaData) getTenantMetadata();

        data.setDefaultDsType(distributionSetTypeRepository.findById(defaultDsType)
                .orElseThrow(() -> new EntityNotFoundException(DistributionSetType.class, defaultDsType)));

        return tenantMetaDataRepository.save(data);
    }

    private DistributionSetType createStandardSoftwareDataSetup() {
        final SoftwareModuleType app = softwareModuleTypeRepository
                .save(new JpaSoftwareModuleType(org.eclipse.hawkbit.repository.Constants.SMT_DEFAULT_APP_KEY,
                        org.eclipse.hawkbit.repository.Constants.SMT_DEFAULT_APP_NAME, "Application Addons",
                        Integer.MAX_VALUE));
        final SoftwareModuleType os = softwareModuleTypeRepository.save(new JpaSoftwareModuleType(
                org.eclipse.hawkbit.repository.Constants.SMT_DEFAULT_OS_KEY,
                org.eclipse.hawkbit.repository.Constants.SMT_DEFAULT_OS_NAME, "Core firmware or operationg system", 1));

        // make sure the module types get their IDs
        entityManager.flush();

        distributionSetTypeRepository
                .save(new JpaDistributionSetType(org.eclipse.hawkbit.repository.Constants.DST_DEFAULT_OS_ONLY_KEY,
                        org.eclipse.hawkbit.repository.Constants.DST_DEFAULT_OS_ONLY_NAME,
                        "Default type with Firmware/OS only.").addMandatoryModuleType(os));

        distributionSetTypeRepository
                .save(new JpaDistributionSetType(org.eclipse.hawkbit.repository.Constants.DST_DEFAULT_APP_ONLY_KEY,
                        org.eclipse.hawkbit.repository.Constants.DST_DEFAULT_APP_ONLY_NAME,
                        "Default type with app(s) only.").addMandatoryModuleType(app));

        return distributionSetTypeRepository
                .save(new JpaDistributionSetType(org.eclipse.hawkbit.repository.Constants.DST_DEFAULT_OS_WITH_APPS_KEY,
                        org.eclipse.hawkbit.repository.Constants.DST_DEFAULT_OS_WITH_APPS_NAME,
                        "Default type with Firmware/OS and optional app(s).").addMandatoryModuleType(os)
                                .addOptionalModuleType(app));
    }

    @Override
    public TenantMetaData getTenantMetadata(final long tenantId) {
        return tenantMetaDataRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException(TenantMetaData.class, tenantId));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    // Exception squid:S2229 - calling findTenants without transaction is
    // intended in this case
    @SuppressWarnings("squid:S2229")
    public void forEachTenant(final Consumer<String> consumer) {

        Page<String> tenants;
        Pageable query = PageRequest.of(0, MAX_TENANTS_QUERY);
        do {
            tenants = findTenants(query);
            tenants.forEach(tenant -> tenantAware.runAsTenant(tenant, () -> {
                try {
                    consumer.accept(tenant);
                } catch (final RuntimeException ex) {
                    LOGGER.debug("Exception on forEachTenant execution for tenant {}. Continue with next tenant.",
                            tenant, ex);
                    LOGGER.error(
                            "Exception on forEachTenant execution for tenant {} with error message [{}] and cause [{}]. "
                                    + "Continue with next tenant.", tenant, ex.getMessage(),
                            ex.getCause() == null ? "" : ex.getCause().getMessage());
                }
                return null;
            }));
        } while ((query = tenants.nextPageable()) != Pageable.unpaged());

    }
}
