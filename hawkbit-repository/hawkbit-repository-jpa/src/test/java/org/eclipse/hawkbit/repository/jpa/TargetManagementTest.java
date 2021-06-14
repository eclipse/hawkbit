/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.validation.ConstraintViolationException;

import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.hawkbit.im.authentication.SpPermission;
import org.eclipse.hawkbit.repository.FilterParams;
import org.eclipse.hawkbit.repository.event.remote.TargetAssignDistributionSetEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetAttributesRequestedEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetPollEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.ActionCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.ActionUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.DistributionSetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.SoftwareModuleCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetTagCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetUpdatedEvent;
import org.eclipse.hawkbit.repository.exception.AssignmentQuotaExceededException;
import org.eclipse.hawkbit.repository.exception.EntityAlreadyExistsException;
import org.eclipse.hawkbit.repository.exception.InvalidTargetAddressException;
import org.eclipse.hawkbit.repository.exception.TenantNotExistException;
import org.eclipse.hawkbit.repository.jpa.model.JpaTarget;
import org.eclipse.hawkbit.repository.jpa.model.JpaTargetMetadata;
import org.eclipse.hawkbit.repository.model.Action.Status;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.DistributionSetAssignmentResult;
import org.eclipse.hawkbit.repository.model.MetaData;
import org.eclipse.hawkbit.repository.model.NamedEntity;
import org.eclipse.hawkbit.repository.model.Tag;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetMetadata;
import org.eclipse.hawkbit.repository.model.TargetTag;
import org.eclipse.hawkbit.repository.test.matcher.Expect;
import org.eclipse.hawkbit.repository.test.matcher.ExpectEvents;
import org.eclipse.hawkbit.repository.test.util.WithSpringAuthorityRule;
import org.eclipse.hawkbit.repository.test.util.WithUser;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.google.common.collect.Iterables;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Step;
import io.qameta.allure.Story;

@Feature("Component Tests - Repository")
@Story("Target Management")
public class TargetManagementTest extends AbstractJpaIntegrationTest {

    private static final String WHITESPACE_ERROR = "target with whitespaces in controller id should not be created";

    @Test
    @Description("Verifies that management get access react as specified on calls for non existing entities by means "
            + "of Optional not present.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1) })
    public void nonExistingEntityAccessReturnsNotPresent() {
        final Target target = testdataFactory.createTarget();
        assertThat(targetManagement.getByControllerID(NOT_EXIST_ID)).isNotPresent();
        assertThat(targetManagement.get(NOT_EXIST_IDL)).isNotPresent();
        assertThat(targetManagement.getMetaDataByControllerId(target.getControllerId(), NOT_EXIST_ID)).isNotPresent();
    }

    @Test
    @Description("Verifies that management queries react as specified on calls for non existing entities "
            + " by means of throwing EntityNotFoundException.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetTagCreatedEvent.class, count = 1) })
    public void entityQueriesReferringToNotExistingEntitiesThrowsException() {
        final TargetTag tag = targetTagManagement.create(entityFactory.tag().create().name("A"));
        final Target target = testdataFactory.createTarget();

        verifyThrownExceptionBy(
                () -> targetManagement.assignTag(Collections.singletonList(target.getControllerId()), NOT_EXIST_IDL),
                "TargetTag");
        verifyThrownExceptionBy(() -> targetManagement.assignTag(Collections.singletonList(NOT_EXIST_ID), tag.getId()),
                "Target");

        verifyThrownExceptionBy(() -> targetManagement.findByTag(PAGE, NOT_EXIST_IDL), "TargetTag");
        verifyThrownExceptionBy(() -> targetManagement.findByRsqlAndTag(PAGE, "name==*", NOT_EXIST_IDL), "TargetTag");

        verifyThrownExceptionBy(() -> targetManagement.countByAssignedDistributionSet(NOT_EXIST_IDL),
                "DistributionSet");
        verifyThrownExceptionBy(() -> targetManagement.countByInstalledDistributionSet(NOT_EXIST_IDL),
                "DistributionSet");
        verifyThrownExceptionBy(() -> targetManagement.existsByInstalledOrAssignedDistributionSet(NOT_EXIST_IDL),
                "DistributionSet");

        verifyThrownExceptionBy(() -> targetManagement.countByTargetFilterQuery(NOT_EXIST_IDL), "TargetFilterQuery");
        verifyThrownExceptionBy(() -> targetManagement.countByRsqlAndNonDS(NOT_EXIST_IDL, "name==*"),
                "DistributionSet");

        verifyThrownExceptionBy(() -> targetManagement.deleteByControllerID(NOT_EXIST_ID), "Target");
        verifyThrownExceptionBy(() -> targetManagement.delete(Collections.singletonList(NOT_EXIST_IDL)), "Target");

        verifyThrownExceptionBy(() -> targetManagement.findByTargetFilterQueryAndNonDS(PAGE, NOT_EXIST_IDL, "name==*"),
                "DistributionSet");
        verifyThrownExceptionBy(() -> targetManagement.findByInRolloutGroupWithoutAction(PAGE, NOT_EXIST_IDL),
                "RolloutGroup");
        verifyThrownExceptionBy(() -> targetManagement.findByAssignedDistributionSet(PAGE, NOT_EXIST_IDL),
                "DistributionSet");
        verifyThrownExceptionBy(
                () -> targetManagement.findByAssignedDistributionSetAndRsql(PAGE, NOT_EXIST_IDL, "name==*"),
                "DistributionSet");

        verifyThrownExceptionBy(() -> targetManagement.findByInstalledDistributionSet(PAGE, NOT_EXIST_IDL),
                "DistributionSet");
        verifyThrownExceptionBy(
                () -> targetManagement.findByInstalledDistributionSetAndRsql(PAGE, NOT_EXIST_IDL, "name==*"),
                "DistributionSet");

        verifyThrownExceptionBy(() -> targetManagement
                .toggleTagAssignment(Collections.singletonList(target.getControllerId()), NOT_EXIST_ID), "TargetTag");
        verifyThrownExceptionBy(
                () -> targetManagement.toggleTagAssignment(Collections.singletonList(NOT_EXIST_ID), tag.getName()),
                "Target");

        verifyThrownExceptionBy(() -> targetManagement.unAssignTag(NOT_EXIST_ID, tag.getId()), "Target");
        verifyThrownExceptionBy(() -> targetManagement.unAssignTag(target.getControllerId(), NOT_EXIST_IDL),
                "TargetTag");
        verifyThrownExceptionBy(() -> targetManagement.update(entityFactory.target().update(NOT_EXIST_ID)), "Target");

        verifyThrownExceptionBy(() -> targetManagement.createMetaData(NOT_EXIST_ID,
                Arrays.asList(entityFactory.generateTargetMetadata("123", "123"))), "Target");
        verifyThrownExceptionBy(() -> targetManagement.deleteMetaData(NOT_EXIST_ID, "xxx"), "Target");
        verifyThrownExceptionBy(() -> targetManagement.deleteMetaData(target.getControllerId(), NOT_EXIST_ID),
                "TargetMetadata");
        verifyThrownExceptionBy(() -> targetManagement.getMetaDataByControllerId(NOT_EXIST_ID, "xxx"), "Target");
        verifyThrownExceptionBy(() -> targetManagement.findMetaDataByControllerId(PAGE, NOT_EXIST_ID), "Target");
        verifyThrownExceptionBy(() -> targetManagement.findMetaDataByControllerIdAndRsql(PAGE, NOT_EXIST_ID, "key==*"),
                "Target");
        verifyThrownExceptionBy(
                () -> targetManagement.updateMetadata(NOT_EXIST_ID, entityFactory.generateTargetMetadata("xxx", "xxx")),
                "Target");
        verifyThrownExceptionBy(() -> targetManagement.updateMetadata(target.getControllerId(),
                entityFactory.generateTargetMetadata(NOT_EXIST_ID, "xxx")), "TargetMetadata");
    }

    @Test
    @Description("Ensures that retrieving the target security is only permitted with the necessary permissions.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1) })
    public void getTargetSecurityTokenOnlyWithCorrectPermission() throws Exception {
        final Target createdTarget = targetManagement
                .create(entityFactory.target().create().controllerId("targetWithSecurityToken").securityToken("token"));

        // retrieve security token only with READ_TARGET_SEC_TOKEN permission
        final String securityTokenWithReadPermission = WithSpringAuthorityRule.runAs(
                WithSpringAuthorityRule.withUser("OnlyTargetReadPermission", false, SpPermission.READ_TARGET_SEC_TOKEN),
                createdTarget::getSecurityToken);

        // retrieve security token as system code execution
        final String securityTokenAsSystemCode = systemSecurityContext.runAsSystem(createdTarget::getSecurityToken);

        // retrieve security token without any permissions
        final String securityTokenWithoutPermission = WithSpringAuthorityRule
                .runAs(WithSpringAuthorityRule.withUser("NoPermission", false), createdTarget::getSecurityToken);

        assertThat(createdTarget.getSecurityToken()).isEqualTo("token");
        assertThat(securityTokenWithReadPermission).isNotNull();
        assertThat(securityTokenAsSystemCode).isNotNull();

        assertThat(securityTokenWithoutPermission).isNull();
    }

    @Test
    @Description("Ensures that targets cannot be created e.g. in plug'n play scenarios when tenant does not exists.")
    @WithUser(allSpPermissions = true, autoCreateTenant = false)
    @ExpectEvents(value = { @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void createTargetForTenantWhichDoesNotExistThrowsTenantNotExistException() {
        try {
            targetManagement.create(entityFactory.target().create().controllerId("targetId123"));
            fail("should not be possible as the tenant does not exist");
        } catch (final TenantNotExistException e) {
            // ok
        }
    }

    @Test
    @Description("Verify that a target with same controller ID than another device cannot be created.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1) })
    public void createTargetThatViolatesUniqueConstraintFails() {
        targetManagement.create(entityFactory.target().create().controllerId("123"));

        assertThatExceptionOfType(EntityAlreadyExistsException.class)
                .isThrownBy(() -> targetManagement.create(entityFactory.target().create().controllerId("123")));
    }

    @Test
    @Description("Verify that a target with with invalid properties cannot be created or updated")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 0) })
    public void createAndUpdateTargetWithInvalidFields() {
        final Target target = testdataFactory.createTarget();

        createTargetWithInvalidControllerId();
        createAndUpdateTargetWithInvalidDescription(target);
        createAndUpdateTargetWithInvalidName(target);
        createAndUpdateTargetWithInvalidSecurityToken(target);
        createAndUpdateTargetWithInvalidAddress(target);
    }

    @Step
    private void createAndUpdateTargetWithInvalidDescription(final Target target) {

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement.create(entityFactory.target().create().controllerId("a")
                        .description(RandomStringUtils.randomAlphanumeric(513))))
                .as("target with too long description should not be created");

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement
                        .create(entityFactory.target().create().controllerId("a").description(INVALID_TEXT_HTML)))
                .as("target with invalid description should not be created");

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement.update(entityFactory.target().update(target.getControllerId())
                        .description(RandomStringUtils.randomAlphanumeric(513))))
                .as("target with too long description should not be updated");

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement
                        .update(entityFactory.target().update(target.getControllerId()).description(INVALID_TEXT_HTML)))
                .as("target with invalid description should not be updated");

    }

    @Step
    private void createAndUpdateTargetWithInvalidName(final Target target) {

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement.create(entityFactory.target().create().controllerId("a")
                        .name(RandomStringUtils.randomAlphanumeric(NamedEntity.NAME_MAX_SIZE + 1))))
                .as("target with too long name should not be created");

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement
                        .create(entityFactory.target().create().controllerId("a").name(INVALID_TEXT_HTML)))
                .as("target with invalid name should not be created");

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement.update(entityFactory.target().update(target.getControllerId())
                        .name(RandomStringUtils.randomAlphanumeric(NamedEntity.NAME_MAX_SIZE + 1))))
                .as("target with too long name should not be updated");

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement
                        .update(entityFactory.target().update(target.getControllerId()).name(INVALID_TEXT_HTML)))
                .as("target with invalid name should not be updated");

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(
                        () -> targetManagement.update(entityFactory.target().update(target.getControllerId()).name("")))
                .as("target with too short name should not be updated");

    }

    @Step
    private void createAndUpdateTargetWithInvalidSecurityToken(final Target target) {

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement.create(entityFactory.target().create().controllerId("a")
                        .securityToken(RandomStringUtils.randomAlphanumeric(Target.SECURITY_TOKEN_MAX_SIZE + 1))))
                .as("target with too long token should not be created");

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement
                        .create(entityFactory.target().create().controllerId("a").securityToken(INVALID_TEXT_HTML)))
                .as("target with invalid token should not be created");

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement.update(entityFactory.target().update(target.getControllerId())
                        .securityToken(RandomStringUtils.randomAlphanumeric(Target.SECURITY_TOKEN_MAX_SIZE + 1))))
                .as("target with too long token should not be updated");

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement.update(
                        entityFactory.target().update(target.getControllerId()).securityToken(INVALID_TEXT_HTML)))
                .as("target with invalid token should not be updated");

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement
                        .update(entityFactory.target().update(target.getControllerId()).securityToken("")))
                .as("target with too short token should not be updated");
    }

    @Step
    private void createAndUpdateTargetWithInvalidAddress(final Target target) {

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement.create(entityFactory.target().create().controllerId("a")
                        .address(RandomStringUtils.randomAlphanumeric(513))))
                .as("target with too long address should not be created");

        assertThatExceptionOfType(InvalidTargetAddressException.class)
                .isThrownBy(() -> targetManagement
                        .create(entityFactory.target().create().controllerId("a").address(INVALID_TEXT_HTML)))
                .as("target with invalid should not be created");

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement.update(entityFactory.target().update(target.getControllerId())
                        .address(RandomStringUtils.randomAlphanumeric(513))))
                .as("target with too long address should not be updated");

        assertThatExceptionOfType(InvalidTargetAddressException.class)
                .isThrownBy(() -> targetManagement
                        .update(entityFactory.target().update(target.getControllerId()).address(INVALID_TEXT_HTML)))
                .as("target with invalid address should not be updated");
    }

    @Step
    private void createTargetWithInvalidControllerId() {
        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement.create(entityFactory.target().create().controllerId("")))
                .as("target with empty controller id should not be created");

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement.create(entityFactory.target().create().controllerId(null)))
                .as("target with null controller id should not be created");

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement.create(entityFactory.target().create()
                        .controllerId(RandomStringUtils.randomAlphanumeric(Target.CONTROLLER_ID_MAX_SIZE + 1))))
                .as("target with too long controller id should not be created");

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(
                        () -> targetManagement.create(entityFactory.target().create().controllerId(INVALID_TEXT_HTML)))
                .as("target with invalid controller id should not be created");

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement.create(entityFactory.target().create().controllerId(" ")))
                .as(WHITESPACE_ERROR);

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement.create(entityFactory.target().create().controllerId("a b")))
                .as(WHITESPACE_ERROR);

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement.create(entityFactory.target().create().controllerId("     ")))
                .as(WHITESPACE_ERROR);

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetManagement.create(entityFactory.target().create().controllerId("aaa   bbb")))
                .as(WHITESPACE_ERROR);

    }

    @Test
    @Description("Ensures that targets can assigned and unassigned to a target tag. Not exists target will be ignored for the assignment.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 4),
            @Expect(type = TargetTagCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 5) })
    public void assignAndUnassignTargetsToTag() {
        final List<String> assignTarget = new ArrayList<>();
        assignTarget.add(
                targetManagement.create(entityFactory.target().create().controllerId("targetId123")).getControllerId());
        assignTarget.add(targetManagement.create(entityFactory.target().create().controllerId("targetId1234"))
                .getControllerId());
        assignTarget.add(targetManagement.create(entityFactory.target().create().controllerId("targetId1235"))
                .getControllerId());
        assignTarget.add(targetManagement.create(entityFactory.target().create().controllerId("targetId1236"))
                .getControllerId());

        final TargetTag targetTag = targetTagManagement.create(entityFactory.tag().create().name("Tag1"));

        final List<Target> assignedTargets = targetManagement.assignTag(assignTarget, targetTag.getId());
        assertThat(assignedTargets.size()).as("Assigned targets are wrong").isEqualTo(4);
        assignedTargets.forEach(target -> assertThat(
                targetTagManagement.findByTarget(PAGE, target.getControllerId()).getNumberOfElements()).isEqualTo(1));

        TargetTag findTargetTag = targetTagManagement.getByName("Tag1").get();
        assertThat(assignedTargets.size()).as("Assigned targets are wrong")
                .isEqualTo(targetManagement.findByTag(PAGE, targetTag.getId()).getNumberOfElements());

        final Target unAssignTarget = targetManagement.unAssignTag("targetId123", findTargetTag.getId());
        assertThat(unAssignTarget.getControllerId()).as("Controller id is wrong").isEqualTo("targetId123");
        assertThat(targetTagManagement.findByTarget(PAGE, unAssignTarget.getControllerId())).as("Tag size is wrong")
                .isEmpty();
        findTargetTag = targetTagManagement.getByName("Tag1").get();
        assertThat(targetManagement.findByTag(PAGE, targetTag.getId())).as("Assigned targets are wrong").hasSize(3);
        assertThat(targetManagement.findByRsqlAndTag(PAGE, "controllerId==targetId123", targetTag.getId()))
                .as("Assigned targets are wrong").isEmpty();
        assertThat(targetManagement.findByRsqlAndTag(PAGE, "controllerId==targetId1234", targetTag.getId()))
                .as("Assigned targets are wrong").hasSize(1);

    }

    @Test
    @Description("Ensures that targets can deleted e.g. test all cascades")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 12),
            @Expect(type = TargetDeletedEvent.class, count = 12), @Expect(type = TargetUpdatedEvent.class, count = 6) })
    public void deleteAndCreateTargets() {
        Target target = targetManagement.create(entityFactory.target().create().controllerId("targetId123"));
        assertThat(targetManagement.count()).as("target count is wrong").isEqualTo(1);
        targetManagement.delete(Collections.singletonList(target.getId()));
        assertThat(targetManagement.count()).as("target count is wrong").isEqualTo(0);

        target = createTargetWithAttributes("4711");
        assertThat(targetManagement.count()).as("target count is wrong").isEqualTo(1);
        assertThat(targetManagement.existsByControllerId("4711")).isTrue();
        targetManagement.delete(Collections.singletonList(target.getId()));
        assertThat(targetManagement.count()).as("target count is wrong").isEqualTo(0);
        assertThat(targetManagement.existsByControllerId("4711")).isFalse();

        final List<Long> targets = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            target = targetManagement.create(entityFactory.target().create().controllerId("" + i));
            targets.add(target.getId());
            targets.add(createTargetWithAttributes("" + (i * i + 1000)).getId());
        }
        assertThat(targetManagement.count()).as("target count is wrong").isEqualTo(10);
        targetManagement.delete(targets);
        assertThat(targetManagement.count()).as("target count is wrong").isEqualTo(0);
    }

    private Target createTargetWithAttributes(final String controllerId) {
        final Map<String, String> testData = new HashMap<>();
        testData.put("test1", "testdata1");

        targetManagement.create(entityFactory.target().create().controllerId(controllerId));
        final Target target = controllerManagement.updateControllerAttributes(controllerId, testData, null);

        assertThat(targetManagement.getControllerAttributes(controllerId)).as("Controller Attributes are wrong")
                .isEqualTo(testData);
        return target;
    }

    @Test
    @Description("Finds a target by given ID and checks if all data is in the response (including the data defined as lazy).")
    @ExpectEvents({ @Expect(type = DistributionSetCreatedEvent.class, count = 2),
            @Expect(type = TargetCreatedEvent.class, count = 1), @Expect(type = TargetUpdatedEvent.class, count = 5),
            @Expect(type = ActionCreatedEvent.class, count = 2), @Expect(type = ActionUpdatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 2),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 6),
            @Expect(type = TargetAttributesRequestedEvent.class, count = 1),
            @Expect(type = TargetPollEvent.class, count = 1) })
    public void findTargetByControllerIDWithDetails() {
        final DistributionSet set = testdataFactory.createDistributionSet("test");
        final DistributionSet set2 = testdataFactory.createDistributionSet("test2");

        assertThat(targetManagement.countByAssignedDistributionSet(set.getId())).as("Target count is wrong")
                .isEqualTo(0);
        assertThat(targetManagement.countByInstalledDistributionSet(set.getId())).as("Target count is wrong")
                .isEqualTo(0);
        assertThat(targetManagement.existsByInstalledOrAssignedDistributionSet(set.getId())).as("Target count is wrong")
                .isFalse();
        assertThat(targetManagement.countByAssignedDistributionSet(set2.getId())).as("Target count is wrong")
                .isEqualTo(0);
        assertThat(targetManagement.countByInstalledDistributionSet(set2.getId())).as("Target count is wrong")
                .isEqualTo(0);
        assertThat(targetManagement.existsByInstalledOrAssignedDistributionSet(set2.getId())).as("Target count is wrong")
                .isFalse();

        Target target = createTargetWithAttributes("4711");

        final long current = System.currentTimeMillis();
        controllerManagement.findOrRegisterTargetIfItDoesNotExist("4711", LOCALHOST);

        final DistributionSetAssignmentResult result = assignDistributionSet(set.getId(), "4711");

        controllerManagement.addUpdateActionStatus(
                entityFactory.actionStatus().create(getFirstAssignedActionId(result)).status(Status.FINISHED));
        assignDistributionSet(set2.getId(), "4711");

        target = targetManagement.getByControllerID("4711").get();
        // read data

        assertThat(targetManagement.countByAssignedDistributionSet(set.getId())).as("Target count is wrong")
                .isEqualTo(0);
        assertThat(targetManagement.countByInstalledDistributionSet(set.getId())).as("Target count is wrong")
                .isEqualTo(1);
        assertThat(targetManagement.existsByInstalledOrAssignedDistributionSet(set.getId())).as("Target count is wrong")
                .isTrue();
        assertThat(targetManagement.countByAssignedDistributionSet(set2.getId())).as("Target count is wrong")
                .isEqualTo(1);
        assertThat(targetManagement.countByInstalledDistributionSet(set2.getId())).as("Target count is wrong")
                .isEqualTo(0);
        assertThat(targetManagement.existsByInstalledOrAssignedDistributionSet(set2.getId())).as("Target count is wrong")
                .isTrue();
        assertThat(target.getLastTargetQuery()).as("Target query is not work").isGreaterThanOrEqualTo(current);
        assertThat(deploymentManagement.getAssignedDistributionSet("4711").get()).as("Assigned ds size is wrong")
                .isEqualTo(set2);
        assertThat(deploymentManagement.getInstalledDistributionSet("4711").get()).as("Installed ds is wrong")
                .isEqualTo(set);
    }

    @Test
    @Description("Checks if the EntityAlreadyExistsException is thrown if the targets with the same controller ID are created twice.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 5) })
    public void createMultipleTargetsDuplicate() {
        testdataFactory.createTargets(5, "mySimpleTargs", "my simple targets");
        try {
            testdataFactory.createTargets(5, "mySimpleTargs", "my simple targets");
            fail("Targets already exists");
        } catch (final EntityAlreadyExistsException e) {
        }

    }

    @Test
    @Description("Checks if the EntityAlreadyExistsException is thrown if a single target with the same controller ID are created twice.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1) })
    public void createTargetDuplicate() {
        targetManagement.create(entityFactory.target().create().controllerId("4711"));
        try {
            targetManagement.create(entityFactory.target().create().controllerId("4711"));
            fail("Target already exists");
        } catch (final EntityAlreadyExistsException e) {
        }
    }

    /**
     * verifies, that all {@link TargetTag} of parameter. NOTE: it's accepted
     * that the target have additional tags assigned to them which are not
     * contained within parameter tags.
     *
     * @param strict
     *            if true, the given targets MUST contain EXACTLY ALL given
     *            tags, AND NO OTHERS. If false, the given targets MUST contain
     *            ALL given tags, BUT MAY CONTAIN FURTHER ONE
     * @param targets
     *            targets to be verified
     * @param tags
     *            are contained within tags of all targets.
     * @param tags
     *            to be found in the tags of the targets
     */
    private void checkTargetHasTags(final boolean strict, final Iterable<Target> targets, final TargetTag... tags) {
        _target: for (final Target tl : targets) {
            for (final Tag tt : targetTagManagement.findByTarget(PAGE, tl.getControllerId())) {
                for (final Tag tag : tags) {
                    if (tag.getName().equals(tt.getName())) {
                        continue _target;
                    }
                }
                if (strict) {
                    fail("Target does not contain all tags");
                }
            }
            fail("Target does not contain any tags or the expected tag was not found");
        }
    }

    private void checkTargetHasNotTags(final Iterable<Target> targets, final TargetTag... tags) {
        for (final Target tl : targets) {
            targetManagement.getByControllerID(tl.getControllerId()).get();

            for (final Tag tag : tags) {
                for (final Tag tt : targetTagManagement.findByTarget(PAGE, tl.getControllerId())) {
                    if (tag.getName().equals(tt.getName())) {
                        fail("Target should have no tags");
                    }
                }
            }
        }
    }

    @Test
    @WithUser(allSpPermissions = true)
    @Description("Creates and updates a target and verifies the changes in the repository.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1) })
    public void singleTargetIsInsertedIntoRepo() throws Exception {

        final String myCtrlID = "myCtrlID";

        Target savedTarget = testdataFactory.createTarget(myCtrlID);
        assertThat(savedTarget).isNotNull().as("The target should not be null");
        final Long createdAt = savedTarget.getCreatedAt();
        Long modifiedAt = savedTarget.getLastModifiedAt();

        assertThat(createdAt).as("CreatedAt compared with modifiedAt").isEqualTo(modifiedAt);
        assertThat(savedTarget.getCreatedAt()).isNotNull().as("The createdAt attribute of the target should no be null");
        assertThat(savedTarget.getLastModifiedAt()).isNotNull().as("The lastModifiedAt attribute of the target should no be null");

        Thread.sleep(1);
        savedTarget = targetManagement.update(
                entityFactory.target().update(savedTarget.getControllerId()).description("changed description"));
        assertThat(savedTarget.getLastModifiedAt()).isNotNull().as("The lastModifiedAt attribute of the target should not be null");
        assertThat(createdAt).as("CreatedAt compared with saved modifiedAt")
                .isNotEqualTo(savedTarget.getLastModifiedAt());
        assertThat(modifiedAt).as("ModifiedAt compared with saved modifiedAt")
                .isNotEqualTo(savedTarget.getLastModifiedAt());
        modifiedAt = savedTarget.getLastModifiedAt();

        final Target foundTarget = targetManagement.getByControllerID(savedTarget.getControllerId()).get();
        assertThat(foundTarget).isNotNull().as("The target should not be null");
        assertThat(myCtrlID).as("ControllerId compared with saved controllerId")
                .isEqualTo(foundTarget.getControllerId());
        assertThat(savedTarget).as("Target compared with saved target").isEqualTo(foundTarget);
        assertThat(createdAt).as("CreatedAt compared with saved createdAt").isEqualTo(foundTarget.getCreatedAt());
        assertThat(modifiedAt).as("LastModifiedAt compared with saved lastModifiedAt")
                .isEqualTo(foundTarget.getLastModifiedAt());
    }

    @Test
    @WithUser(allSpPermissions = true)
    @Description("Create multiple targets as bulk operation and delete them in bulk.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 101),
            @Expect(type = TargetUpdatedEvent.class, count = 100),
            @Expect(type = TargetDeletedEvent.class, count = 51) })
    public void bulkTargetCreationAndDelete() throws Exception {
        final String myCtrlID = "myCtrlID";
        List<Target> firstList = testdataFactory.createTargets(100, myCtrlID, "first description");

        final Target extra = testdataFactory.createTarget("myCtrlID-00081XX");

        final Iterable<JpaTarget> allFound = targetRepository.findAll();

        assertThat(Long.valueOf(firstList.size())).as("List size of targets")
                .isEqualTo(firstList.spliterator().getExactSizeIfKnown());
        assertThat(Long.valueOf(firstList.size() + 1)).as("LastModifiedAt compared with saved lastModifiedAt")
                .isEqualTo(allFound.spliterator().getExactSizeIfKnown());

        // change the objects and save to again to trigger a change on
        // lastModifiedAt
        firstList = firstList.stream()
                .map(t -> targetManagement.update(
                        entityFactory.target().update(t.getControllerId()).name(t.getName().concat("\tchanged"))))
                .collect(Collectors.toList());

        // verify that all entries are found
        _founds: for (final Target foundTarget : allFound) {
            for (final Target changedTarget : firstList) {
                if (changedTarget.getControllerId().equals(foundTarget.getControllerId())) {
                    assertThat(changedTarget.getDescription())
                            .as("Description of changed target compared with description saved target")
                            .isEqualTo(foundTarget.getDescription());
                    assertThat(changedTarget.getName()).as("Name of changed target starts with name of saved target")
                            .startsWith(foundTarget.getName());
                    assertThat(changedTarget.getName()).as("Name of changed target ends with 'changed'")
                            .endsWith("changed");
                    assertThat(changedTarget.getCreatedAt()).as("CreatedAt compared with saved createdAt")
                            .isEqualTo(foundTarget.getCreatedAt());
                    assertThat(changedTarget.getLastModifiedAt()).as("LastModifiedAt compared with saved createdAt")
                            .isNotEqualTo(changedTarget.getCreatedAt());
                    continue _founds;
                }
            }

            if (!foundTarget.getControllerId().equals(extra.getControllerId())) {
                fail("The controllerId of the found target is not equal to the controllerId of the saved target");
            }
        }

        targetManagement.deleteByControllerID(extra.getControllerId());

        final int numberToDelete = 50;
        final Collection<Target> targetsToDelete = firstList.subList(0, numberToDelete);
        final Target[] deletedTargets = Iterables.toArray(targetsToDelete, Target.class);
        final List<Long> targetsIdsToDelete = targetsToDelete.stream().map(Target::getId).collect(Collectors.toList());

        targetManagement.delete(targetsIdsToDelete);

        final List<Target> targetsLeft = targetManagement.findAll(PageRequest.of(0, 200)).getContent();
        assertThat(firstList.spliterator().getExactSizeIfKnown() - numberToDelete).as("Size of split list")
                .isEqualTo(targetsLeft.spliterator().getExactSizeIfKnown());

        assertThat(targetsLeft).as("Not all undeleted found").doesNotContain(deletedTargets);
    }

    @Test
    @Description("Tests the assignment of tags to the a single target.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 2),
            @Expect(type = TargetTagCreatedEvent.class, count = 7),
            @Expect(type = TargetUpdatedEvent.class, count = 7) })
    public void targetTagAssignment() {
        final Target t1 = testdataFactory.createTarget("id-1");
        final int noT2Tags = 4;
        final int noT1Tags = 3;
        final List<TargetTag> t1Tags = testdataFactory.createTargetTags(noT1Tags, "tag1");

        t1Tags.forEach(tag -> targetManagement.assignTag(Collections.singletonList(t1.getControllerId()), tag.getId()));

        final Target t2 = testdataFactory.createTarget("id-2");
        final List<TargetTag> t2Tags = testdataFactory.createTargetTags(noT2Tags, "tag2");
        t2Tags.forEach(tag -> targetManagement.assignTag(Collections.singletonList(t2.getControllerId()), tag.getId()));

        final Target t11 = targetManagement.getByControllerID(t1.getControllerId()).get();
        assertThat(targetTagManagement.findByTarget(PAGE, t11.getControllerId()).getContent()).as("Tag size is wrong")
                .hasSize(noT1Tags).containsAll(t1Tags);
        assertThat(targetTagManagement.findByTarget(PAGE, t11.getControllerId()).getContent()).as("Tag size is wrong")
                .hasSize(noT1Tags).doesNotContain(Iterables.toArray(t2Tags, TargetTag.class));

        final Target t21 = targetManagement.getByControllerID(t2.getControllerId()).get();
        assertThat(targetTagManagement.findByTarget(PAGE, t21.getControllerId()).getContent()).as("Tag size is wrong")
                .hasSize(noT2Tags).containsAll(t2Tags);
        assertThat(targetTagManagement.findByTarget(PAGE, t21.getControllerId()).getContent()).as("Tag size is wrong")
                .hasSize(noT2Tags).doesNotContain(Iterables.toArray(t1Tags, TargetTag.class));
    }

    @Test
    @Description("Tests the assignment of tags to multiple targets.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 50),
            @Expect(type = TargetTagCreatedEvent.class, count = 4),
            @Expect(type = TargetUpdatedEvent.class, count = 80) })
    public void targetTagBulkAssignments() {
        final List<Target> tagATargets = testdataFactory.createTargets(10, "tagATargets", "first description");
        final List<Target> tagBTargets = testdataFactory.createTargets(10, "tagBTargets", "first description");
        final List<Target> tagCTargets = testdataFactory.createTargets(10, "tagCTargets", "first description");

        final List<Target> tagABTargets = testdataFactory.createTargets(10, "tagABTargets", "first description");

        final List<Target> tagABCTargets = testdataFactory.createTargets(10, "tagABCTargets", "first description");

        final TargetTag tagA = targetTagManagement.create(entityFactory.tag().create().name("A"));
        final TargetTag tagB = targetTagManagement.create(entityFactory.tag().create().name("B"));
        final TargetTag tagC = targetTagManagement.create(entityFactory.tag().create().name("C"));
        targetTagManagement.create(entityFactory.tag().create().name("X"));

        // doing different assignments
        toggleTagAssignment(tagATargets, tagA);
        toggleTagAssignment(tagBTargets, tagB);
        toggleTagAssignment(tagCTargets, tagC);

        toggleTagAssignment(tagABTargets, tagA);
        toggleTagAssignment(tagABTargets, tagB);

        toggleTagAssignment(tagABCTargets, tagA);
        toggleTagAssignment(tagABCTargets, tagB);
        toggleTagAssignment(tagABCTargets, tagC);

        assertThat(targetManagement.countByFilters(null, null, null, null, Boolean.FALSE, "X"))
                .as("Target count is wrong").isEqualTo(0);

        // search for targets with tag tagA
        final List<Target> targetWithTagA = new ArrayList<>();
        final List<Target> targetWithTagB = new ArrayList<>();
        final List<Target> targetWithTagC = new ArrayList<>();

        // storing target lists to enable easy evaluation
        Iterables.addAll(targetWithTagA, tagATargets);
        Iterables.addAll(targetWithTagA, tagABTargets);
        Iterables.addAll(targetWithTagA, tagABCTargets);

        Iterables.addAll(targetWithTagB, tagBTargets);
        Iterables.addAll(targetWithTagB, tagABTargets);
        Iterables.addAll(targetWithTagB, tagABCTargets);

        Iterables.addAll(targetWithTagC, tagCTargets);
        Iterables.addAll(targetWithTagC, tagABCTargets);

        // check the target lists as returned by assignTag
        checkTargetHasTags(false, targetWithTagA, tagA);
        checkTargetHasTags(false, targetWithTagB, tagB);
        checkTargetHasTags(false, targetWithTagC, tagC);

        checkTargetHasNotTags(tagATargets, tagB, tagC);
        checkTargetHasNotTags(tagBTargets, tagA, tagC);
        checkTargetHasNotTags(tagCTargets, tagA, tagB);

        // check again target lists refreshed from DB
        assertThat(targetManagement.countByFilters(null, null, null, null, Boolean.FALSE, "A"))
                .as("Target count is wrong").isEqualTo(targetWithTagA.size());
        assertThat(targetManagement.countByFilters(null, null, null, null, Boolean.FALSE, "B"))
                .as("Target count is wrong").isEqualTo(targetWithTagB.size());
        assertThat(targetManagement.countByFilters(null, null, null, null, Boolean.FALSE, "C"))
                .as("Target count is wrong").isEqualTo(targetWithTagC.size());
    }

    @Test
    @Description("Tests the unassigment of tags to multiple targets.")
    @ExpectEvents({ @Expect(type = TargetTagCreatedEvent.class, count = 3),
            @Expect(type = TargetCreatedEvent.class, count = 109),
            @Expect(type = TargetUpdatedEvent.class, count = 227) })
    public void targetTagBulkUnassignments() {
        final TargetTag targTagA = targetTagManagement.create(entityFactory.tag().create().name("Targ-A-Tag"));
        final TargetTag targTagB = targetTagManagement.create(entityFactory.tag().create().name("Targ-B-Tag"));
        final TargetTag targTagC = targetTagManagement.create(entityFactory.tag().create().name("Targ-C-Tag"));

        final List<Target> targAs = testdataFactory.createTargets(25, "target-id-A", "first description");
        final List<Target> targBs = testdataFactory.createTargets(20, "target-id-B", "first description");
        final List<Target> targCs = testdataFactory.createTargets(15, "target-id-C", "first description");

        final List<Target> targABs = testdataFactory.createTargets(12, "target-id-AB", "first description");
        final List<Target> targACs = testdataFactory.createTargets(13, "target-id-AC", "first description");
        final List<Target> targBCs = testdataFactory.createTargets(7, "target-id-BC", "first description");
        final List<Target> targABCs = testdataFactory.createTargets(17, "target-id-ABC", "first description");

        toggleTagAssignment(targAs, targTagA);
        toggleTagAssignment(targABs, targTagA);
        toggleTagAssignment(targACs, targTagA);
        toggleTagAssignment(targABCs, targTagA);

        toggleTagAssignment(targBs, targTagB);
        toggleTagAssignment(targABs, targTagB);
        toggleTagAssignment(targBCs, targTagB);
        toggleTagAssignment(targABCs, targTagB);

        toggleTagAssignment(targCs, targTagC);
        toggleTagAssignment(targACs, targTagC);
        toggleTagAssignment(targBCs, targTagC);
        toggleTagAssignment(targABCs, targTagC);

        checkTargetHasTags(true, targAs, targTagA);
        checkTargetHasTags(true, targBs, targTagB);
        checkTargetHasTags(true, targABs, targTagA, targTagB);
        checkTargetHasTags(true, targACs, targTagA, targTagC);
        checkTargetHasTags(true, targBCs, targTagB, targTagC);
        checkTargetHasTags(true, targABCs, targTagA, targTagB, targTagC);

        toggleTagAssignment(targCs, targTagC);
        toggleTagAssignment(targACs, targTagC);
        toggleTagAssignment(targBCs, targTagC);
        toggleTagAssignment(targABCs, targTagC);

        checkTargetHasTags(true, targAs, targTagA); // 0
        checkTargetHasTags(true, targBs, targTagB);
        checkTargetHasTags(true, targABs, targTagA, targTagB);
        checkTargetHasTags(true, targBCs, targTagB);
        checkTargetHasTags(true, targACs, targTagA);

        checkTargetHasNotTags(targCs, targTagC);
        checkTargetHasNotTags(targACs, targTagC);
        checkTargetHasNotTags(targBCs, targTagC);
        checkTargetHasNotTags(targABCs, targTagC);
    }

    @Test
    @Description("Test that NO TAG functionality which gives all targets with no tag assigned.")
    @ExpectEvents({ @Expect(type = TargetTagCreatedEvent.class, count = 1),
            @Expect(type = TargetCreatedEvent.class, count = 50),
            @Expect(type = TargetUpdatedEvent.class, count = 25) })
    public void findTargetsWithNoTag() {

        final TargetTag targTagA = targetTagManagement.create(entityFactory.tag().create().name("Targ-A-Tag"));
        final List<Target> targAs = testdataFactory.createTargets(25, "target-id-A", "first description");
        toggleTagAssignment(targAs, targTagA);

        testdataFactory.createTargets(25, "target-id-B", "first description");

        final String[] tagNames = null;
        final List<Target> targetsListWithNoTag = targetManagement
                .findByFilters(PAGE, new FilterParams(null, null, null, null, Boolean.TRUE, tagNames)).getContent();

        assertThat(50L).as("Total targets").isEqualTo(targetManagement.count());
        assertThat(25).as("Targets with no tag").isEqualTo(targetsListWithNoTag.size());

    }

    @Test
    @Description("Tests the a target can be read with only the read target permission")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetPollEvent.class, count = 1) })
    public void targetCanBeReadWithOnlyReadTargetPermission() throws Exception {
        final String knownTargetControllerId = "readTarget";
        controllerManagement.findOrRegisterTargetIfItDoesNotExist(knownTargetControllerId, new URI("http://127.0.0.1"));

        WithSpringAuthorityRule.runAs(WithSpringAuthorityRule.withUser("bumlux", "READ_TARGET"), () -> {
            final Target findTargetByControllerID = targetManagement.getByControllerID(knownTargetControllerId).get();
            assertThat(findTargetByControllerID).isNotNull();
            assertThat(findTargetByControllerID.getPollStatus()).isNotNull();
            return null;
        });

    }

    @Test
    @Description("Test that RSQL filter finds targets with tags or specific ids.")
    public void findTargetsWithTagOrId() {
        final String rsqlFilter = "tag==Targ-A-Tag,id==target-id-B-00001,id==target-id-B-00008";
        final TargetTag targTagA = targetTagManagement.create(entityFactory.tag().create().name("Targ-A-Tag"));
        final List<String> targAs = testdataFactory.createTargets(25, "target-id-A", "first description").stream()
                .map(Target::getControllerId).collect(Collectors.toList());
        targetManagement.toggleTagAssignment(targAs, targTagA.getName());

        testdataFactory.createTargets(25, "target-id-B", "first description");

        final Page<Target> foundTargets = targetManagement.findByRsql(PAGE, rsqlFilter);

        assertThat(targetManagement.count()).as("Total targets").isEqualTo(50L);
        assertThat(foundTargets.getTotalElements()).as("Targets in RSQL filter").isEqualTo(27L);
    }

    @Test
    @Description("Verify that the find all targets by ids method contains the entities that we are looking for")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 12) })
    public void verifyFindTargetAllById() {
        final List<Long> searchIds = Arrays.asList(testdataFactory.createTarget("target-4").getId(),
                testdataFactory.createTarget("target-5").getId(), testdataFactory.createTarget("target-6").getId());
        for (int i = 0; i < 9; i++) {
            testdataFactory.createTarget("test" + i);
        }

        final List<Target> foundDs = targetManagement.get(searchIds);

        assertThat(foundDs).hasSize(3);

        final List<Long> collect = foundDs.stream().map(Target::getId).collect(Collectors.toList());
        assertThat(collect).containsAll(searchIds);
    }

    @Test
    @Description("Verify that the flag for requesting controller attributes is set correctly.")
    public void verifyRequestControllerAttributes() {
        final String knownControllerId = "KnownControllerId";
        final Target target = createTargetWithAttributes(knownControllerId);

        assertThat(targetManagement.findByControllerAttributesRequested(PAGE)).isEmpty();
        assertThat(targetManagement.isControllerAttributesRequested(knownControllerId)).isFalse();

        targetManagement.requestControllerAttributes(knownControllerId);
        final Target updated = targetManagement.getByControllerID(knownControllerId).get();

        assertThat(target.isRequestControllerAttributes()).isFalse();
        assertThat(targetManagement.findByControllerAttributesRequested(PAGE).getContent()).contains(updated);
        assertThat(targetManagement.isControllerAttributesRequested(knownControllerId)).isTrue();

    }

    @Test
    @Description("Checks that metadata for a target can be created.")
    public void createTargetMetadata() {
        final String knownKey = "targetMetaKnownKey";
        final String knownValue = "targetMetaKnownValue";

        final Target target = testdataFactory.createTarget("targetIdWithMetadata");
        final JpaTargetMetadata createdMetadata = insertTargetMetadata(knownKey, knownValue, target);

        assertThat(createdMetadata).isNotNull();
        assertThat(createdMetadata.getId().getKey()).isEqualTo(knownKey);
        assertThat(createdMetadata.getTarget().getControllerId()).isEqualTo(target.getControllerId());
        assertThat(createdMetadata.getTarget().getId()).isEqualTo(target.getId());
        assertThat(createdMetadata.getValue()).isEqualTo(knownValue);
    }

    private JpaTargetMetadata insertTargetMetadata(final String knownKey, final String knownValue,
            final Target target) {
        final JpaTargetMetadata metadata = new JpaTargetMetadata(knownKey, knownValue, target);
        return (JpaTargetMetadata) targetManagement
                .createMetaData(target.getControllerId(), Collections.singletonList(metadata)).get(0);
    }

    @Test
    @Description("Verifies the enforcement of the metadata quota per target.")
    public void createTargetMetadataUntilQuotaIsExceeded() {

        // add meta data one by one
        final Target target1 = testdataFactory.createTarget("target1");
        final int maxMetaData = quotaManagement.getMaxMetaDataEntriesPerTarget();
        for (int i = 0; i < maxMetaData; ++i) {
            assertThat(insertTargetMetadata("k" + i, "v" + i, target1)).isNotNull();
        }

        // quota exceeded
        assertThatExceptionOfType(AssignmentQuotaExceededException.class)
                .isThrownBy(() -> insertTargetMetadata("k" + maxMetaData, "v" + maxMetaData, target1));

        // add multiple meta data entries at once
        final Target target2 = testdataFactory.createTarget("target2");
        final List<MetaData> metaData2 = new ArrayList<>();
        for (int i = 0; i < maxMetaData + 1; ++i) {
            metaData2.add(new JpaTargetMetadata("k" + i, "v" + i, target2));
        }
        // verify quota is exceeded
        assertThatExceptionOfType(AssignmentQuotaExceededException.class)
                .isThrownBy(() -> targetManagement.createMetaData(target2.getControllerId(), metaData2));

        // add some meta data entries
        final Target target3 = testdataFactory.createTarget("target3");
        final int firstHalf = Math.round(maxMetaData / 2);
        for (int i = 0; i < firstHalf; ++i) {
            insertTargetMetadata("k" + i, "v" + i, target3);
        }
        // add too many data entries
        final int secondHalf = maxMetaData - firstHalf;
        final List<MetaData> metaData3 = new ArrayList<>();
        for (int i = 0; i < secondHalf + 1; ++i) {
            metaData3.add(new JpaTargetMetadata("kk" + i, "vv" + i, target3));
        }
        // verify quota is exceeded
        assertThatExceptionOfType(AssignmentQuotaExceededException.class)
                .isThrownBy(() -> targetManagement.createMetaData(target3.getControllerId(), metaData3));

    }

    @Test
    @WithUser(allSpPermissions = true)
    @Description("Checks that metadata for a target can be updated.")
    public void updateTargetMetadata() throws InterruptedException {
        final String knownKey = "myKnownKey";
        final String knownValue = "myKnownValue";
        final String knownUpdateValue = "myNewUpdatedValue";

        // create a target
        final Target target = testdataFactory.createTarget("target1");
        // initial opt lock revision must be zero
        assertThat(target.getOptLockRevision()).isEqualTo(1);

        // create target meta data entry
        insertTargetMetadata(knownKey, knownValue, target);

        Target changedLockRevisionTarget = targetManagement.get(target.getId()).get();
        assertThat(changedLockRevisionTarget.getOptLockRevision()).isEqualTo(2);

        Thread.sleep(100);

        // update the target metadata
        final JpaTargetMetadata updated = (JpaTargetMetadata) targetManagement.updateMetadata(target.getControllerId(),
                entityFactory.generateTargetMetadata(knownKey, knownUpdateValue));
        // we are updating the target meta data so also modifying the base
        // software
        // module so opt lock
        // revision must be three
        changedLockRevisionTarget = targetManagement.get(target.getId()).get();
        assertThat(changedLockRevisionTarget.getOptLockRevision()).isEqualTo(3);
        assertThat(changedLockRevisionTarget.getLastModifiedAt()).isGreaterThan(0L);

        // verify updated meta data contains the updated value
        assertThat(updated).isNotNull();
        assertThat(updated.getValue()).isEqualTo(knownUpdateValue);
        assertThat(updated.getId().getKey()).isEqualTo(knownKey);
        assertThat(updated.getTarget().getControllerId()).isEqualTo(target.getControllerId());
        assertThat(updated.getTarget().getId()).isEqualTo(target.getId());
    }

    @Test
    @Description("Queries and loads the metadata related to a given target.")
    public void findAllTargetMetadataByControllerId() {
        // create targets
        final Target target1 = createTargetWithMetadata("target1", 10);
        final Target target2 = createTargetWithMetadata("target2", 8);

        final Page<TargetMetadata> metadataOfTarget1 = targetManagement
                .findMetaDataByControllerId(PageRequest.of(0, 100), target1.getControllerId());

        final Page<TargetMetadata> metadataOfTarget2 = targetManagement
                .findMetaDataByControllerId(PageRequest.of(0, 100), target2.getControllerId());

        assertThat(metadataOfTarget1.getNumberOfElements()).isEqualTo(10);
        assertThat(metadataOfTarget1.getTotalElements()).isEqualTo(10);

        assertThat(metadataOfTarget2.getNumberOfElements()).isEqualTo(8);
        assertThat(metadataOfTarget2.getTotalElements()).isEqualTo(8);
    }

    private Target createTargetWithMetadata(final String controllerId, final int count) {
        final Target target = testdataFactory.createTarget(controllerId);

        for (int index = 1; index <= count; index++) {
            insertTargetMetadata("key" + index, controllerId + "-value" + index, target);
        }

        return target;
    }

    @Test
    @Description("Test that RSQL filter finds targets with metadata and/or controllerId.")
    public void findTargetsByRsqlWithMetadata() {
        final String controllerId1 = "target1";
        final String controllerId2 = "target2";
        createTargetWithMetadata(controllerId1, 2);
        createTargetWithMetadata(controllerId2, 2);

        final String rsqlAndControllerIdFilter = "id==target1 and metadata.key1==target1-value1";
        final String rsqlAndControllerIdWithWrongKeyFilter = "id==* and metadata.unknown==value1";
        final String rsqlAndControllerIdNotEqualFilter = "id==* and metadata.key2!=target1-value2";
        final String rsqlOrControllerIdFilter = "id==target1 or metadata.key1==*value1";
        final String rsqlOrControllerIdWithWrongKeyFilter = "id==target2 or metadata.unknown==value1";
        final String rsqlOrControllerIdNotEqualFilter = "id==target1 or metadata.key1!=target1-value1";

        assertThat(targetManagement.count()).as("Total targets").isEqualTo(2);
        validateFoundTargetsByRsql(rsqlAndControllerIdFilter, controllerId1);
        validateFoundTargetsByRsql(rsqlAndControllerIdWithWrongKeyFilter);
        validateFoundTargetsByRsql(rsqlAndControllerIdNotEqualFilter, controllerId2);
        validateFoundTargetsByRsql(rsqlOrControllerIdFilter, controllerId1, controllerId2);
        validateFoundTargetsByRsql(rsqlOrControllerIdWithWrongKeyFilter, controllerId2);
        validateFoundTargetsByRsql(rsqlOrControllerIdNotEqualFilter, controllerId1, controllerId2);
    }

    private void validateFoundTargetsByRsql(final String rsqlFilter, final String... controllerIds) {
        final Page<Target> foundTargetsByMetadataAndControllerId = targetManagement.findByRsql(PAGE, rsqlFilter);

        assertThat(foundTargetsByMetadataAndControllerId.getTotalElements()).as("Targets count in RSQL filter is wrong")
                .isEqualTo(controllerIds.length);
        assertThat(foundTargetsByMetadataAndControllerId.getContent().stream().map(Target::getControllerId))
                .as("Targets found by RSQL filter have wrong controller ids").containsExactlyInAnyOrder(controllerIds);
    }
}
