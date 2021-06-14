/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.eclipse.hawkbit.im.authentication.SpPermission.SpringEvalExpressions.CONTROLLER_ROLE;
import static org.eclipse.hawkbit.im.authentication.SpPermission.SpringEvalExpressions.SYSTEM_ROLE;
import static org.springframework.test.context.TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;

import org.eclipse.hawkbit.repository.exception.EntityNotFoundException;
import org.eclipse.hawkbit.repository.jpa.AbstractJpaIntegrationTest;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.test.util.CleanupTestExecutionListener;
import org.eclipse.hawkbit.repository.test.util.DisposableSqlTestDatabase;
import org.eclipse.hawkbit.repository.test.util.WithSpringAuthorityRule;
import org.eclipse.hawkbit.repository.test.util.WithUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.test.context.TestExecutionListeners;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jcip.annotations.NotThreadSafe;

/**
 * Multi-Tenancy tests which testing the CRUD operations of entities that all
 * CRUD-Operations are tenant aware and cannot access or delete entities not
 * belonging to the current tenant.
 *
 */
@Feature("Component Tests - Repository")
@Story("Multi Tenancy")
@ExtendWith(DisposableSqlTestDatabase.class)
@NotThreadSafe // test methods should not run in parallel, since they use the same tenant
@TestExecutionListeners(listeners = CleanupTestExecutionListener.class, mergeMode = MERGE_WITH_DEFAULTS)
@WithUser(tenantId = "DEFAULT", principal = "bumlux", allSpPermissions = true, authorities = { CONTROLLER_ROLE, SYSTEM_ROLE })
public class MultiTenancyEntityTest extends AbstractJpaIntegrationTest {

    @Test
    @Description(value = "Ensures that multiple targets with same controller-ID can be created for different tenants.")
    public void createMultipleTargetsWithSameIdForDifferentTenant() throws Exception {
        // known controller ID for overall tenants same
        final String knownControllerId = "controllerId";

        // known tenant names
        final String tenant = "aTenant";
        final String anotherTenant = "anotherTenant";
        // create targets
        createTargetForTenant(knownControllerId, tenant);
        createTargetForTenant(knownControllerId, anotherTenant);

        // ensure both tenants see their target
        final Slice<Target> findTargetsForTenant = findTargetsForTenant(tenant);
        assertThat(findTargetsForTenant).hasSize(1);
        assertThat(findTargetsForTenant.getContent().get(0).getTenant().toUpperCase()).isEqualTo(tenant.toUpperCase());
        final Slice<Target> findTargetsForAnotherTenant = findTargetsForTenant(anotherTenant);
        assertThat(findTargetsForAnotherTenant).hasSize(1);
        assertThat(findTargetsForAnotherTenant.getContent().get(0).getTenant().toUpperCase())
                .isEqualTo(anotherTenant.toUpperCase());
    }

    @Test
    @Description(value = "Ensures that targtes created by a tenant are not visible by another tenant.")
    @WithUser(tenantId = "mytenant", allSpPermissions = true)
    public void queryTargetFromDifferentTenantIsNotVisible() throws Exception {
        // create target for another tenant
        final String anotherTenant = "anotherTenant";
        final String controllerAnotherTenant = "anotherController";
        createTargetForTenant(controllerAnotherTenant, anotherTenant);

        // find all targets for current tenant "mytenant"
        final Slice<Target> findTargetsAll = targetManagement.findAll(PAGE);
        // no target has been created for "mytenant"
        assertThat(findTargetsAll).hasSize(0);

        // find all targets for anotherTenant
        final Slice<Target> findTargetsForTenant = findTargetsForTenant(anotherTenant);
        // another tenant should have targets
        assertThat(findTargetsForTenant).hasSize(1);
    }

    @Test
    @Description(value = "Ensures that tenant with proper permissions can read and delete other tenants.")
    @WithUser(tenantId = "mytenant", allSpPermissions = true)
    public void deleteAnotherTenantPossible() throws Exception {
        // create target for another tenant
        final String anotherTenant = "anotherTenant";
        final String controllerAnotherTenant = "anotherController";
        createTargetForTenant(controllerAnotherTenant, anotherTenant);

        assertThat(systemManagement.findTenants(PAGE)).as("Expected number of tenants before deletion is").hasSize(2);

        systemManagement.deleteTenant(anotherTenant);

        assertThat(systemManagement.findTenants(PAGE)).as("Expected number of tenants after deletion is").hasSize(1);
    }

    @Test
    @Description(value = "Ensures that tenant metadata is retrieved for the current tenant.")
    @WithUser(tenantId = "mytenant", autoCreateTenant = false, allSpPermissions = true)
    public void getTenanatMetdata() throws Exception {

        // logged in tenant mytenant - check if tenant default data is
        // autogenerated
        assertThat(distributionSetTypeManagement.findAll(PAGE)).isEmpty();
        assertThat(systemManagement.getTenantMetadata().getTenant().toUpperCase()).isEqualTo("mytenant".toUpperCase());
        assertThat(distributionSetTypeManagement.findAll(PAGE)).isNotEmpty();

        // check that the cache is not getting in the way, i.e. "bumlux" results
        // in bumlux and not
        // mytenant
        assertThat(WithSpringAuthorityRule.runAs(WithSpringAuthorityRule.withUserAndTenant("user", "bumlux"),
                () -> systemManagement.getTenantMetadata().getTenant().toUpperCase()))
                        .isEqualTo("bumlux".toUpperCase());
    }

    @Test
    @Description(value = "Ensures that targets created from a different tenant cannot be deleted from other tenants")
    @WithUser(tenantId = "mytenant", allSpPermissions = true)
    public void deleteTargetFromOtherTenantIsNotPossible() throws Exception {
        // create target for another tenant
        final String anotherTenant = "anotherTenant";
        final String controllerAnotherTenant = "anotherController";
        final Target createTargetForTenant = createTargetForTenant(controllerAnotherTenant, anotherTenant);

        // ensure target cannot be deleted by 'mytenant'
        try {
            targetManagement.delete(Collections.singletonList(createTargetForTenant.getId()));
            fail("mytenant should not have been able to delete target of anotherTenant");
        } catch (final EntityNotFoundException ex) {
            // ok
        }

        Slice<Target> targetsForAnotherTenant = findTargetsForTenant(anotherTenant);
        assertThat(targetsForAnotherTenant).hasSize(1);

        // ensure another tenant can delete the target
        deleteTargetsForTenant(anotherTenant, Collections.singletonList(createTargetForTenant.getId()));
        targetsForAnotherTenant = findTargetsForTenant(anotherTenant);
        assertThat(targetsForAnotherTenant).hasSize(0);
    }

    @Test
    @Description(value = "Ensures that multiple distribution sets with same name and version can be created for different tenants.")
    public void createMultipleDistributionSetsWithSameNameForDifferentTenants() throws Exception {

        // known tenant names
        final String tenant = "aTenant";
        final String anotherTenant = "anotherTenant";
        // create distribution sets
        createDistributionSetForTenant(tenant);
        createDistributionSetForTenant(anotherTenant);

        // ensure both tenants see their distribution sets
        final Page<DistributionSet> findDistributionSetsForTenant = findDistributionSetForTenant(tenant);
        assertThat(findDistributionSetsForTenant).hasSize(1);
        assertThat(findDistributionSetsForTenant.getContent().get(0).getTenant().toUpperCase())
                .isEqualTo(tenant.toUpperCase());
        final Page<DistributionSet> findDistributionSetsForAnotherTenant = findDistributionSetForTenant(anotherTenant);
        assertThat(findDistributionSetsForAnotherTenant).hasSize(1);
        assertThat(findDistributionSetsForAnotherTenant.getContent().get(0).getTenant().toUpperCase())
                .isEqualTo(anotherTenant.toUpperCase());
    }

    private <T> T runAsTenant(final String tenant, final Callable<T> callable) throws Exception {
        return WithSpringAuthorityRule.runAs(WithSpringAuthorityRule.withUserAndTenant("user", tenant), callable);
    }

    private Target createTargetForTenant(final String controllerId, final String tenant) throws Exception {
        return runAsTenant(tenant, () -> testdataFactory.createTarget(controllerId));
    }

    private Slice<Target> findTargetsForTenant(final String tenant) throws Exception {
        return runAsTenant(tenant, () -> targetManagement.findAll(PAGE));
    }

    private void deleteTargetsForTenant(final String tenant, final Collection<Long> targetIds) throws Exception {
        runAsTenant(tenant, () -> {
            targetManagement.delete(targetIds);
            return null;
        });
    }

    private DistributionSet createDistributionSetForTenant(final String tenant) throws Exception {
        return runAsTenant(tenant, () -> testdataFactory.createDistributionSet());
    }

    private Page<DistributionSet> findDistributionSetForTenant(final String tenant) throws Exception {
        return runAsTenant(tenant, () -> distributionSetManagement.findByCompleted(PAGE, true));
    }
}
