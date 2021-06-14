/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.test.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.hawkbit.im.authentication.SpPermission.SpringEvalExpressions.CONTROLLER_ROLE;
import static org.eclipse.hawkbit.im.authentication.SpPermission.SpringEvalExpressions.SYSTEM_ROLE;
import static org.springframework.test.context.TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.hawkbit.artifact.repository.ArtifactRepository;
import org.eclipse.hawkbit.cache.TenantAwareCacheManager;
import org.eclipse.hawkbit.repository.ArtifactManagement;
import org.eclipse.hawkbit.repository.ControllerManagement;
import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.DistributionSetTagManagement;
import org.eclipse.hawkbit.repository.DistributionSetTypeManagement;
import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.QuotaManagement;
import org.eclipse.hawkbit.repository.RepositoryConstants;
import org.eclipse.hawkbit.repository.RolloutGroupManagement;
import org.eclipse.hawkbit.repository.RolloutManagement;
import org.eclipse.hawkbit.repository.SoftwareModuleManagement;
import org.eclipse.hawkbit.repository.SoftwareModuleTypeManagement;
import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TargetTagManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.repository.event.remote.entity.DistributionSetTypeCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.SoftwareModuleTypeCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.SoftwareModuleTypeUpdatedEvent;
import org.eclipse.hawkbit.repository.exception.TenantNotExistException;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.Action.ActionType;
import org.eclipse.hawkbit.repository.model.DeploymentRequest;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.DistributionSetAssignmentResult;
import org.eclipse.hawkbit.repository.model.DistributionSetMetadata;
import org.eclipse.hawkbit.repository.model.DistributionSetType;
import org.eclipse.hawkbit.repository.model.MetaData;
import org.eclipse.hawkbit.repository.model.RepositoryModelConstants;
import org.eclipse.hawkbit.repository.model.SoftwareModuleType;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetMetadata;
import org.eclipse.hawkbit.repository.test.TestConfiguration;
import org.eclipse.hawkbit.repository.test.matcher.EventVerifier;
import org.eclipse.hawkbit.repository.test.matcher.Expect;
import org.eclipse.hawkbit.repository.test.matcher.ExpectEvents;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.bus.ServiceMatcher;
import org.springframework.cloud.stream.test.binder.TestSupportBinderAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;

import com.google.common.io.Files;

@ActiveProfiles({ "test" })
@ExtendWith({JUnitTestLoggerExtension.class, WithSpringAuthorityRule.class, SharedSqlTestDatabase.class})
@WithUser(principal = "bumlux", allSpPermissions = true, authorities = { CONTROLLER_ROLE, SYSTEM_ROLE })
@SpringBootTest
@ContextConfiguration(classes = { TestConfiguration.class, TestSupportBinderAutoConfiguration.class })
// destroy the context after each test class because otherwise we get problem
// when context is
// refreshed we e.g. get two instances of CacheManager which leads to very
// strange test failures.
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@TestExecutionListeners(listeners = EventVerifier.class, mergeMode = MERGE_WITH_DEFAULTS)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
public abstract class AbstractIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractIntegrationTest.class);

    protected static final Pageable PAGE = PageRequest.of(0, 400, Sort.by(Direction.ASC, "id"));

    protected static final URI LOCALHOST = URI.create("http://127.0.0.1");

    protected static final int DEFAULT_TEST_WEIGHT = 500;

    /**
     * Number of {@link DistributionSetType}s that exist in every test case. One
     * generated by using
     * {@link TestdataFactory#findOrCreateDefaultTestDsType()} and three
     * {@link SystemManagement#getTenantMetadata()};
     */
    protected static final int DEFAULT_DS_TYPES = RepositoryConstants.DEFAULT_DS_TYPES_IN_TENANT + 1;

    @Autowired
    protected EntityFactory entityFactory;

    @Autowired
    protected SoftwareModuleManagement softwareModuleManagement;

    @Autowired
    protected SoftwareModuleTypeManagement softwareModuleTypeManagement;

    @Autowired
    protected DistributionSetManagement distributionSetManagement;

    @Autowired
    protected DistributionSetTypeManagement distributionSetTypeManagement;

    @Autowired
    protected ControllerManagement controllerManagement;

    @Autowired
    protected TargetManagement targetManagement;

    @Autowired
    protected TargetFilterQueryManagement targetFilterQueryManagement;

    @Autowired
    protected TargetTagManagement targetTagManagement;

    @Autowired
    protected DistributionSetTagManagement distributionSetTagManagement;

    @Autowired
    protected DeploymentManagement deploymentManagement;

    @Autowired
    protected ArtifactManagement artifactManagement;

    @Autowired
    protected AuditingHandler auditingHandler;

    @Autowired
    protected TenantAware tenantAware;

    @Autowired
    protected SystemManagement systemManagement;

    @Autowired
    protected TenantConfigurationManagement tenantConfigurationManagement;

    @Autowired
    protected RolloutManagement rolloutManagement;

    @Autowired
    protected RolloutGroupManagement rolloutGroupManagement;

    @Autowired
    protected SystemSecurityContext systemSecurityContext;

    @Autowired
    protected ArtifactRepository binaryArtifactRepository;

    @Autowired
    protected TenantAwareCacheManager cacheManager;

    @Autowired
    protected QuotaManagement quotaManagement;

    protected SoftwareModuleType osType;
    protected SoftwareModuleType appType;
    protected SoftwareModuleType runtimeType;

    protected DistributionSetType standardDsType;

    @Autowired
    protected TestdataFactory testdataFactory;

    @Autowired
    protected ServiceMatcher serviceMatcher;

    @Autowired
    protected ApplicationEventPublisher eventPublisher;

    protected DistributionSetAssignmentResult assignDistributionSet(final long dsID, final String controllerId) {
        return assignDistributionSet(dsID, controllerId, ActionType.FORCED);
    }

    protected DistributionSetAssignmentResult assignDistributionSet(final long dsID, final String controllerId,
            final ActionType actionType) {
        return assignDistributionSet(dsID, Collections.singletonList(controllerId), actionType);
    }

    protected DistributionSetAssignmentResult assignDistributionSet(final long dsID, final String controllerId,
            final ActionType actionType, final long forcedTime) {
        return assignDistributionSet(dsID, Collections.singletonList(controllerId), actionType, forcedTime);
    }

    protected DistributionSetAssignmentResult assignDistributionSet(final long dsID, final List<String> controllerIds,
            final ActionType actionType) {
        return assignDistributionSet(dsID, controllerIds, actionType, RepositoryModelConstants.NO_FORCE_TIME);
    }

    protected DistributionSetAssignmentResult assignDistributionSet(final long dsID, final List<String> controllerIds,
            final ActionType actionType, final long forcedTime) {
        return assignDistributionSet(dsID, controllerIds, actionType, forcedTime, null);
    }

    protected DistributionSetAssignmentResult assignDistributionSet(final long dsID, final List<String> controllerIds,
            final ActionType actionType, final long forcedTime, final Integer weight) {
        final List<DeploymentRequest> deploymentRequests = controllerIds.stream()
                .map(id -> DeploymentManagement.deploymentRequest(id, dsID).setActionType(actionType)
                        .setForceTime(forcedTime).setWeight(weight).build())
                .collect(Collectors.toList());
        final List<DistributionSetAssignmentResult> results = deploymentManagement
                .assignDistributionSets(deploymentRequests);
        assertThat(results).hasSize(1);
        return results.get(0);
    }

    protected DistributionSetAssignmentResult assignDistributionSet(final DistributionSet ds,
            final List<Target> targets) {
        final List<String> targetIds = targets.stream().map(Target::getControllerId).collect(Collectors.toList());
        return assignDistributionSet(ds.getId(), targetIds, ActionType.FORCED);
    }

    protected DistributionSetAssignmentResult assignDistributionSet(final Long dsId, final List<String> targetIds,
            final int weight) {
        return assignDistributionSet(dsId, targetIds, ActionType.FORCED, RepositoryModelConstants.NO_FORCE_TIME,
                weight);
    }

    protected DistributionSetAssignmentResult makeAssignment(final DeploymentRequest request) {
        final List<DistributionSetAssignmentResult> results = deploymentManagement
                .assignDistributionSets(Collections.singletonList(request));
        assertThat(results).hasSize(1);
        return results.get(0);
    }

    /**
     * Test helper method to assign distribution set to a target with a
     * maintenance schedule.
     *
     * @param dsID
     *            is the ID for the distribution set being assigned
     * @param controllerId
     *            is the ID for the controller to which the distribution set is
     *            being assigned
     * @param maintenanceWindowSchedule
     *            is the cron expression to be used for scheduling the
     *            maintenance window. Expression has 6 mandatory fields and 1
     *            last optional field: "second minute hour dayofmonth month
     *            weekday year"
     * @param maintenanceWindowDuration
     *            in HH:mm:ss format specifying the duration of a maintenance
     *            window, for example 00:30:00 for 30 minutes
     * @param maintenanceWindowTimeZone
     *            is the time zone specified as +/-hh:mm offset from UTC, for
     *            example +02:00 for CET summer time and +00:00 for UTC. The
     *            start time of a maintenance window calculated based on the
     *            cron expression is relative to this time zone
     *
     * @return result of the assignment as { @link
     *         DistributionSetAssignmentResult}.
     */
    protected DistributionSetAssignmentResult assignDistributionSetWithMaintenanceWindow(final long dsID,
            final String controllerId, final String maintenanceWindowSchedule, final String maintenanceWindowDuration,
            final String maintenanceWindowTimeZone) {

        return makeAssignment(DeploymentManagement.deploymentRequest(controllerId, dsID)
                .setMaintenance(maintenanceWindowSchedule, maintenanceWindowDuration, maintenanceWindowTimeZone)
                .build());
    }

    protected DistributionSetAssignmentResult assignDistributionSet(final DistributionSet pset, final Target target) {
        return assignDistributionSet(pset, Arrays.asList(target));
    }

    protected DistributionSetAssignmentResult assignDistributionSet(final long dsId, final String targetId,
            final int weight) {
        return assignDistributionSet(dsId, Collections.singletonList(targetId), weight);
    }

    protected void enableMultiAssignments() {
        tenantConfigurationManagement.addOrUpdateConfiguration(TenantConfigurationKey.MULTI_ASSIGNMENTS_ENABLED, true);
    }

    protected DistributionSetMetadata createDistributionSetMetadata(final long dsId, final MetaData md) {
        return createDistributionSetMetadata(dsId, Collections.singletonList(md)).get(0);
    }

    protected List<DistributionSetMetadata> createDistributionSetMetadata(final long dsId, final List<MetaData> md) {
        return distributionSetManagement.createMetaData(dsId, md);
    }

    protected TargetMetadata createTargetMetadata(final String controllerId, final MetaData md) {
        return createTargetMetadata(controllerId, Collections.singletonList(md)).get(0);
    }

    protected List<TargetMetadata> createTargetMetadata(final String controllerId, final List<MetaData> md) {
        return targetManagement.createMetaData(controllerId, md);
    }

    protected Long getOsModule(final DistributionSet ds) {
        return ds.findFirstModuleByType(osType).get().getId();
    }

    protected Action prepareFinishedUpdate(final String controllerId, final String distributionSet,
            final boolean isRequiredMigrationStep) {
        final DistributionSet ds = testdataFactory.createDistributionSet(distributionSet, isRequiredMigrationStep);
        Target savedTarget = testdataFactory.createTarget(controllerId);
        savedTarget = getFirstAssignedTarget(
                assignDistributionSet(ds.getId(), savedTarget.getControllerId(), ActionType.FORCED));
        Action savedAction = deploymentManagement.findActiveActionsByTarget(PAGE, savedTarget.getControllerId())
                .getContent().get(0);

        savedAction = controllerManagement.addUpdateActionStatus(
                entityFactory.actionStatus().create(savedAction.getId()).status(Action.Status.RUNNING));

        return controllerManagement.addUpdateActionStatus(
                entityFactory.actionStatus().create(savedAction.getId()).status(Action.Status.FINISHED));
    }

    @BeforeEach
    @ExpectEvents({@Expect(type = SoftwareModuleTypeCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleTypeUpdatedEvent.class, count = 3),
            @Expect(type = DistributionSetTypeCreatedEvent.class, count = 4)})
    public void beforeEach() throws Exception {

        final String description = "Updated description.";

        try {
            osType = WithSpringAuthorityRule
                    .runAsPrivileged(() -> testdataFactory.findOrCreateSoftwareModuleType(TestdataFactory.SM_TYPE_OS));
            osType = WithSpringAuthorityRule.runAsPrivileged(() -> softwareModuleTypeManagement
                    .update(entityFactory.softwareModuleType().update(osType.getId()).description(description)));

            appType = WithSpringAuthorityRule.runAsPrivileged(
                    () -> testdataFactory.findOrCreateSoftwareModuleType(TestdataFactory.SM_TYPE_APP, Integer.MAX_VALUE));
            appType = WithSpringAuthorityRule.runAsPrivileged(() -> softwareModuleTypeManagement
                    .update(entityFactory.softwareModuleType().update(appType.getId()).description(description)));

            runtimeType = WithSpringAuthorityRule
                    .runAsPrivileged(() -> testdataFactory.findOrCreateSoftwareModuleType(TestdataFactory.SM_TYPE_RT));
            runtimeType = WithSpringAuthorityRule.runAsPrivileged(() -> softwareModuleTypeManagement
                    .update(entityFactory.softwareModuleType().update(runtimeType.getId()).description(description)));

            standardDsType = WithSpringAuthorityRule.runAsPrivileged(() -> testdataFactory.findOrCreateDefaultTestDsType());
        } catch (final TenantNotExistException e) {
            LOG.warn("Not creating default software types since tenant {} has autoCreate = false",
                    tenantAware.getCurrentTenant());
            LOG.trace("", e);
        }
    }

    private static String artifactDirectory = Files.createTempDir().getAbsolutePath() + "/"
            + RandomStringUtils.randomAlphanumeric(20);

    @AfterEach
    public void cleanUp() {
        if (new File(artifactDirectory).exists()) {
            try {
                FileUtils.cleanDirectory(new File(artifactDirectory));
            } catch (final IOException | IllegalArgumentException e) {
                LOG.warn("Cannot cleanup file-directory", e);
            }
        }
    }

    @BeforeAll
    public static void beforeClass() {
        System.setProperty("org.eclipse.hawkbit.repository.file.path", artifactDirectory);
    }

    @AfterAll
    public static void afterClass() {
        if (new File(artifactDirectory).exists()) {
            try {
                FileUtils.deleteDirectory(new File(artifactDirectory));
            } catch (final IOException | IllegalArgumentException e) {
                LOG.warn("Cannot delete file-directory", e);
            }
        }
    }

    /**
     * Gets a valid cron expression describing a schedule with a single
     * maintenance window, starting specified number of minutes after current
     * time.
     *
     * @param minutesToAdd
     *            is the number of minutes after the current time
     *
     * @return {@link String} containing a valid cron expression.
     */
    protected static String getTestSchedule(final int minutesToAdd) {
        ZonedDateTime currentTime = ZonedDateTime.now();
        currentTime = currentTime.plusMinutes(minutesToAdd);
        return String.format("%d %d %d %d %d ? %d", currentTime.getSecond(), currentTime.getMinute(),
                currentTime.getHour(), currentTime.getDayOfMonth(), currentTime.getMonthValue(), currentTime.getYear());
    }

    protected static String getTestDuration(final int duration) {
        return String.format("%02d:%02d:00", duration / 60, duration % 60);
    }

    protected static String getTestTimeZone() {
        final ZonedDateTime currentTime = ZonedDateTime.now();
        return currentTime.getOffset().getId().replace("Z", "+00:00");
    }

    protected static String generateRandomStringWithLength(final int length) {
        final StringBuilder randomStringBuilder = new StringBuilder(length);
        final Random rand = new Random();
        final int lowercaseACode = 97;
        final int lowercaseZCode = 122;

        for (int i = 0; i < length; i++) {
            final char randomCharacter = (char) (rand.nextInt(lowercaseZCode - lowercaseACode + 1) + lowercaseACode);
            randomStringBuilder.append(randomCharacter);
        }

        return randomStringBuilder.toString();
    }

    protected static Action getFirstAssignedAction(
            final DistributionSetAssignmentResult distributionSetAssignmentResult) {
        return distributionSetAssignmentResult.getAssignedEntity().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("expected one assigned action, found none"));
    }

    protected static Long getFirstAssignedActionId(
            final DistributionSetAssignmentResult distributionSetAssignmentResult) {
        return getFirstAssignedAction(distributionSetAssignmentResult).getId();
    }

    protected static Target getFirstAssignedTarget(final DistributionSetAssignmentResult assignment) {
        return getFirstAssignedAction(assignment).getTarget();
    }

    protected static Comparator<Target> controllerIdComparator() {
        return (o1, o2) -> o1.getControllerId().equals(o2.getControllerId()) ? 0 : 1;
    }
}
