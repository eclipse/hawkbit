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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import javax.validation.ConstraintViolationException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.hawkbit.artifact.repository.model.AbstractDbArtifact;
import org.eclipse.hawkbit.im.authentication.SpPermission;
import org.eclipse.hawkbit.repository.ArtifactManagement;
import org.eclipse.hawkbit.repository.event.remote.SoftwareModuleDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.SoftwareModuleCreatedEvent;
import org.eclipse.hawkbit.repository.exception.AssignmentQuotaExceededException;
import org.eclipse.hawkbit.repository.exception.FileSizeQuotaExceededException;
import org.eclipse.hawkbit.repository.exception.InsufficientPermissionException;
import org.eclipse.hawkbit.repository.exception.StorageQuotaExceededException;
import org.eclipse.hawkbit.repository.jpa.model.JpaArtifact;
import org.eclipse.hawkbit.repository.jpa.model.JpaSoftwareModule;
import org.eclipse.hawkbit.repository.model.Artifact;
import org.eclipse.hawkbit.repository.model.ArtifactUpload;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.repository.test.matcher.Expect;
import org.eclipse.hawkbit.repository.test.matcher.ExpectEvents;
import org.eclipse.hawkbit.repository.test.util.HashGeneratorUtils;
import org.eclipse.hawkbit.repository.test.util.WithSpringAuthorityRule;
import org.eclipse.hawkbit.repository.test.util.WithUser;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * Test class for {@link ArtifactManagement}.
 */
@Feature("Component Tests - Repository")
@Story("Artifact Management")
public class ArtifactManagementTest extends AbstractJpaIntegrationTest {

    @Test
    @Description("Verifies that management get access react as specfied on calls for non existing entities by means "
            + "of Optional not present.")
    @ExpectEvents({ @Expect(type = SoftwareModuleCreatedEvent.class, count = 1) })
    public void nonExistingEntityAccessReturnsNotPresent() {
        final SoftwareModule module = testdataFactory.createSoftwareModuleOs();

        assertThat(artifactManagement.get(NOT_EXIST_IDL)).isNotPresent();
        assertThat(artifactManagement.getByFilenameAndSoftwareModule(NOT_EXIST_ID, module.getId()).isPresent())
                .isFalse();

        assertThat(artifactManagement.findFirstBySHA1(NOT_EXIST_ID)).isNotPresent();
        assertThat(artifactManagement.loadArtifactBinary(NOT_EXIST_ID)).isNotPresent();
    }

    @Test
    @Description("Verifies that management queries react as specfied on calls for non existing entities "
            + " by means of throwing EntityNotFoundException.")
    @ExpectEvents({ @Expect(type = SoftwareModuleDeletedEvent.class, count = 0) })
    public void entityQueriesReferringToNotExistingEntitiesThrowsException() throws URISyntaxException {

        final String artifactData = "test";
        final int artifactSize = artifactData.length();
        verifyThrownExceptionBy(
                () -> artifactManagement.create(new ArtifactUpload(IOUtils.toInputStream(artifactData, "UTF-8"),
                        NOT_EXIST_IDL, "xxx", null, null, null, false, null, artifactSize)),
                "SoftwareModule");

        verifyThrownExceptionBy(
                () -> artifactManagement.create(new ArtifactUpload(IOUtils.toInputStream(artifactData, "UTF-8"),
                        NOT_EXIST_IDL, "xxx", null, null, null, false, null, artifactSize)),
                "SoftwareModule");

        verifyThrownExceptionBy(() -> artifactManagement.delete(NOT_EXIST_IDL), "Artifact");

        verifyThrownExceptionBy(() -> artifactManagement.findBySoftwareModule(PAGE, NOT_EXIST_IDL), "SoftwareModule");
        assertThat(artifactManagement.getByFilename(NOT_EXIST_ID).isPresent()).isFalse();

        verifyThrownExceptionBy(() -> artifactManagement.getByFilenameAndSoftwareModule("xxx", NOT_EXIST_IDL),
                "SoftwareModule");
    }

    @Test
    @Description("Test if a local artifact can be created by API including metadata.")
    public void createArtifact() throws IOException {

        // check baseline
        assertThat(softwareModuleRepository.findAll()).hasSize(0);
        assertThat(artifactRepository.findAll()).hasSize(0);

        final JpaSoftwareModule sm = softwareModuleRepository
                .save(new JpaSoftwareModule(osType, "name 1", "version 1", null, null));
        final JpaSoftwareModule sm2 = softwareModuleRepository
                .save(new JpaSoftwareModule(osType, "name 2", "version 2", null, null));
        softwareModuleRepository.save(new JpaSoftwareModule(osType, "name 3", "version 3", null, null));

        final int artifactSize = 5 * 1024;
        final byte[] randomBytes = randomBytes(artifactSize);

        try (final InputStream inputStream1 = new ByteArrayInputStream(randomBytes);
                final InputStream inputStream2 = new ByteArrayInputStream(randomBytes);
                final InputStream inputStream3 = new ByteArrayInputStream(randomBytes);
                final InputStream inputStream4 = new ByteArrayInputStream(randomBytes);) {

            final Artifact artifact1 = createArtifactForSoftwareModule("file1", sm.getId(), artifactSize, inputStream1);
            createArtifactForSoftwareModule("file11", sm.getId(), artifactSize, inputStream2);
            createArtifactForSoftwareModule("file12", sm.getId(), artifactSize, inputStream3);
            final Artifact artifact2 = createArtifactForSoftwareModule("file2", sm2.getId(), artifactSize,
                    inputStream4);

            assertThat(artifact1).isInstanceOf(Artifact.class);
            assertThat(artifact1.getSoftwareModule().getId()).isEqualTo(sm.getId());
            assertThat(artifact2.getSoftwareModule().getId()).isEqualTo(sm2.getId());
            assertThat(((JpaArtifact) artifact1).getFilename()).isEqualTo("file1");
            assertThat(((JpaArtifact) artifact1).getSha1Hash()).isNotNull();
            assertThat(artifact1).isNotEqualTo(artifact2);
            assertThat(((JpaArtifact) artifact1).getSha1Hash()).isEqualTo(((JpaArtifact) artifact2).getSha1Hash());

            assertThat(artifactManagement.getByFilename("file1").get().getSha1Hash())
                    .isEqualTo(HashGeneratorUtils.generateSHA1(randomBytes));
            assertThat(artifactManagement.getByFilename("file1").get().getMd5Hash())
                    .isEqualTo(HashGeneratorUtils.generateMD5(randomBytes));
            assertThat(artifactManagement.getByFilename("file1").get().getSha256Hash())
                    .isEqualTo(HashGeneratorUtils.generateSHA256(randomBytes));

            assertThat(artifactRepository.findAll()).hasSize(4);
            assertThat(softwareModuleRepository.findAll()).hasSize(3);

            assertThat(softwareModuleManagement.get(sm.getId()).get().getArtifacts()).hasSize(3);
        }

    }

    @Test
    @Description("Verifies that artifact management does not create artifacts with illegal filename.")
    public void entityQueryWithIllegalFilenameThrowsException() throws URISyntaxException {
        final String illegalFilename = "<img src=ernw onerror=alert(1)>.xml";
        final String artifactData = "test";
        final int artifactSize = artifactData.length();

        final long smID = softwareModuleRepository
                .save(new JpaSoftwareModule(osType, "smIllegalFilenameTest", "1.0", null, null)).getId();
        assertThatExceptionOfType(ConstraintViolationException.class).isThrownBy(
                () -> artifactManagement.create(new ArtifactUpload(IOUtils.toInputStream(artifactData, "UTF-8"), smID,
                        illegalFilename, false, artifactSize)));
        assertThat(softwareModuleManagement.get(smID).get().getArtifacts()).hasSize(0);
    }

    @Test
    @Description("Verifies that the quota specifying the maximum number of artifacts per software module is enforced.")
    public void createArtifactsUntilQuotaIsExceeded() throws IOException {

        // create a software module
        final long smId = softwareModuleRepository.save(new JpaSoftwareModule(osType, "sm1", "1.0", null, null))
                .getId();

        // now create artifacts for this module until the quota is exceeded
        final long maxArtifacts = quotaManagement.getMaxArtifactsPerSoftwareModule();
        final List<Long> artifactIds = Lists.newArrayList();
        final int artifactSize = 5 * 1024;
        for (int i = 0; i < maxArtifacts; ++i) {
            artifactIds.add(createArtifactForSoftwareModule("file" + i, smId, artifactSize).getId());
        }
        assertThat(artifactRepository.findBySoftwareModuleId(PAGE, smId).getTotalElements()).isEqualTo(maxArtifacts);

        // create one mode to trigger the quota exceeded error
        assertThatExceptionOfType(AssignmentQuotaExceededException.class)
                .isThrownBy(() -> createArtifactForSoftwareModule("file" + maxArtifacts, smId, artifactSize));

        // delete one of the artifacts
        artifactManagement.delete(artifactIds.get(0));
        assertThat(artifactRepository.findBySoftwareModuleId(PAGE, smId).getTotalElements())
                .isEqualTo(maxArtifacts - 1);

        // now we should be able to create an artifact again
        createArtifactForSoftwareModule("fileXYZ", smId, artifactSize);
        assertThat(artifactRepository.findBySoftwareModuleId(PAGE, smId).getTotalElements()).isEqualTo(maxArtifacts);
    }

    @Test
    @Description("Verifies that the quota specifying the maximum artifact storage is enforced (across software modules).")
    public void createArtifactsUntilStorageQuotaIsExceeded() throws IOException {

        // create as many small artifacts as possible w/o violating the storage
        // quota
        final long maxBytes = quotaManagement.getMaxArtifactStorage();
        final List<Long> artifactIds = Lists.newArrayList();

        // choose an artifact size which does not violate the max file size
        final int artifactSize = Math.toIntExact(quotaManagement.getMaxArtifactSize() / 10);
        final int numArtifacts = Math.toIntExact(maxBytes / artifactSize);
        for (int i = 0; i < numArtifacts; ++i) {
            final JpaSoftwareModule sm = softwareModuleRepository
                    .save(new JpaSoftwareModule(osType, "smd" + i, "1.0", null, null));
            artifactIds.add(createArtifactForSoftwareModule("file" + i, sm.getId(), artifactSize).getId());
        }

        // upload one more artifact to trigger the quota exceeded error
        final JpaSoftwareModule sm = softwareModuleRepository
                .save(new JpaSoftwareModule(osType, "smd" + numArtifacts, "1.0", null, null));
        assertThatExceptionOfType(StorageQuotaExceededException.class)
                .isThrownBy(() -> createArtifactForSoftwareModule("file" + numArtifacts, sm.getId(), artifactSize));

        // delete one of the artifacts
        artifactManagement.delete(artifactIds.get(0));

        // now we should be able to create an artifact again
        createArtifactForSoftwareModule("fileXYZ", sm.getId(), artifactSize);
    }

    @Test
    @Description("Verifies that you cannot create artifacts which exceed the configured maximum size.")
    public void createArtifactFailsIfTooLarge() {

        // create a software module
        final JpaSoftwareModule sm1 = softwareModuleRepository
                .save(new JpaSoftwareModule(osType, "sm1", "1.0", null, null));

        // create an artifact that exceeds the configured quota
        final long maxSize = quotaManagement.getMaxArtifactSize();
        assertThatExceptionOfType(FileSizeQuotaExceededException.class)
                .isThrownBy(() -> createArtifactForSoftwareModule("file", sm1.getId(), Math.toIntExact(maxSize) + 8));
    }

    @Test
    @Description("Tests hard delete directly on repository.")
    public void hardDeleteSoftwareModule() throws IOException {

        final JpaSoftwareModule sm = softwareModuleRepository
                .save(new JpaSoftwareModule(osType, "name 1", "version 1", null, null));

        createArtifactForSoftwareModule("file1", sm.getId(), 5 * 1024);
        assertThat(artifactRepository.findAll()).hasSize(1);

        softwareModuleRepository.deleteAll();
        assertThat(artifactRepository.findAll()).hasSize(0);
    }

    /**
     * Test method for
     * {@link org.eclipse.hawkbit.repository.ArtifactManagement#delete(long)} .
     * 
     * @throws IOException
     */
    @Test
    @Description("Tests the deletion of a local artifact including metadata.")
    public void deleteArtifact() throws IOException {
        final JpaSoftwareModule sm = softwareModuleRepository
                .save(new JpaSoftwareModule(osType, "name 1", "version 1", null, null));
        final JpaSoftwareModule sm2 = softwareModuleRepository
                .save(new JpaSoftwareModule(osType, "name 2", "version 2", null, null));

        assertThat(artifactRepository.findAll()).isEmpty();

        final int artifactSize = 5 * 1024;
        try (final InputStream inputStream1 = new RandomGeneratedInputStream(artifactSize);
                final InputStream inputStream2 = new RandomGeneratedInputStream(artifactSize)) {

            final Artifact artifact1 = createArtifactForSoftwareModule("file1", sm.getId(), artifactSize, inputStream1);
            final Artifact artifact2 = createArtifactForSoftwareModule("file2", sm2.getId(), artifactSize,
                    inputStream2);

            assertThat(artifactRepository.findAll()).hasSize(2);

            assertThat(artifact1.getId()).isNotNull();
            assertThat(artifact2.getId()).isNotNull();
            assertThat(((JpaArtifact) artifact1).getSha1Hash()).isNotEqualTo(((JpaArtifact) artifact2).getSha1Hash());

            assertThat(
                    binaryArtifactRepository.getArtifactBySha1(tenantAware.getCurrentTenant(), artifact1.getSha1Hash()))
                            .isNotNull();
            assertThat(
                    binaryArtifactRepository.getArtifactBySha1(tenantAware.getCurrentTenant(), artifact2.getSha1Hash()))
                            .isNotNull();

            artifactManagement.delete(artifact1.getId());

            assertThat(artifactRepository.findAll()).hasSize(1);

            assertThat(
                    binaryArtifactRepository.getArtifactBySha1(tenantAware.getCurrentTenant(), artifact1.getSha1Hash()))
                            .isNull();
            assertThat(
                    binaryArtifactRepository.getArtifactBySha1(tenantAware.getCurrentTenant(), artifact2.getSha1Hash()))
                            .isNotNull();

            artifactManagement.delete(artifact2.getId());
            assertThat(
                    binaryArtifactRepository.getArtifactBySha1(tenantAware.getCurrentTenant(), artifact2.getSha1Hash()))
                            .isNull();

            assertThat(artifactRepository.findAll()).hasSize(0);
        }
    }

    @Test
    @Description("Test the deletion of an artifact metadata where the binary is still linked to another "
            + "metadata element. The expected result is that the metadata is deleted but the binary kept.")
    public void deleteDuplicateArtifacts() throws IOException {

        final JpaSoftwareModule sm = softwareModuleRepository
                .save(new JpaSoftwareModule(osType, "name 1", "version 1", null, null));
        final JpaSoftwareModule sm2 = softwareModuleRepository
                .save(new JpaSoftwareModule(osType, "name 2", "version 2", null, null));

        final int artifactSize = 5 * 1024;
        final byte[] randomBytes = randomBytes(artifactSize);

        try (final InputStream inputStream1 = new ByteArrayInputStream(randomBytes);
                final InputStream inputStream2 = new ByteArrayInputStream(randomBytes)) {
            final Artifact artifact1 = createArtifactForSoftwareModule("file1", sm.getId(), artifactSize, inputStream1);
            final Artifact artifact2 = createArtifactForSoftwareModule("file2", sm2.getId(), artifactSize,
                    inputStream2);

            assertThat(artifactRepository.findAll()).hasSize(2);
            assertThat(artifact1.getId()).isNotNull();
            assertThat(artifact2.getId()).isNotNull();
            assertThat(((JpaArtifact) artifact1).getSha1Hash()).isEqualTo(((JpaArtifact) artifact2).getSha1Hash());

            assertThat(
                    binaryArtifactRepository.getArtifactBySha1(tenantAware.getCurrentTenant(), artifact1.getSha1Hash()))
                            .isNotNull();

            artifactManagement.delete(artifact1.getId());
            assertThat(
                    binaryArtifactRepository.getArtifactBySha1(tenantAware.getCurrentTenant(), artifact1.getSha1Hash()))
                            .isNotNull();
            assertThat(artifactRepository.findAll()).hasSize(1);
            assertThat(artifactRepository.existsById(artifact1.getId())).isFalse();

            artifactManagement.delete(artifact2.getId());
            assertThat(
                    binaryArtifactRepository.getArtifactBySha1(tenantAware.getCurrentTenant(), artifact1.getSha1Hash()))
                            .isNull();
            assertThat(artifactRepository.findAll()).hasSize(0);
        }
    }

    @Test
    @Description("Verifies that you cannot delete an artifact which exists with the same hash, in the same tenant and the SoftwareModule is not deleted .")
    public void deleteArtifactWithSameHashAndSoftwareModuleIsNotDeletedInSameTenants() throws IOException {

        final JpaSoftwareModule sm = softwareModuleRepository
                .save(new JpaSoftwareModule(osType, "name 1", "version 1", null, null));
        final JpaSoftwareModule sm2 = softwareModuleRepository
                .save(new JpaSoftwareModule(osType, "name 2", "version 2", null, null));

        final int artifactSize = 5 * 1024;
        final byte[] randomBytes = randomBytes(artifactSize);

        try (final InputStream inputStream1 = new ByteArrayInputStream(randomBytes);
                final InputStream inputStream2 = new ByteArrayInputStream(randomBytes)) {

            final Artifact artifact1 = createArtifactForSoftwareModule("file1", sm.getId(), artifactSize, inputStream1);
            final Artifact artifact2 = createArtifactForSoftwareModule("file2", sm2.getId(), artifactSize,
                    inputStream2);

            assertEqualFileContents(artifactManagement.loadArtifactBinary(artifact2.getSha1Hash()), randomBytes);

            assertThat(artifactRepository.findAll()).hasSize(2);

            assertThat(artifact1.getSha1Hash()).isEqualTo(artifact2.getSha1Hash());

            assertThat(artifactRepository.countBySha1HashAndTenantAndSoftwareModuleDeletedIsFalse(
                    artifact1.getSha1Hash(), artifact1.getTenant())).isGreaterThan(1);

            artifactRepository.deleteById(artifact1.getId());
            assertThat(artifactRepository.findAll()).hasSize(1);

            assertThat(artifactRepository.countBySha1HashAndTenantAndSoftwareModuleDeletedIsFalse(
                    artifact2.getSha1Hash(), artifact2.getTenant())).isLessThanOrEqualTo(1);

            artifactRepository.deleteById(artifact2.getId());
            assertThat(artifactRepository.findAll()).hasSize(0);

        }
    }

    @Test
    @Description("Verifies that you can not delete artifacts from another tenant which exists in another tenant with the same hash and the SoftwareModule is not deleted")
    public void deleteArtifactWithSameHashAndSoftwareModuleIsNotDeletedInDifferentTenants() throws Exception {
        final String tenant1 = UUID.randomUUID().toString();
        final String tenant2 = UUID.randomUUID().toString();

        final SoftwareModule module = createSoftwareModuleForTenant(tenant1);
        final SoftwareModule module2 = createSoftwareModuleForTenant(tenant2);

        createArtifactForTenant(tenant1, "myInput", module.getId(), "myFirstFile");
        createArtifactForTenant(tenant2, "myInput", module2.getId(), "mySecondFile");
        final Artifact artifactTenant2 = createArtifactForTenant(tenant2, "myInput", module2.getId(), "myThirdFile");

        verifyTenantArtifactCountIs(tenant1, 1);
        verifyTenantArtifactCountIs(tenant2, 2);

        assertThat(runAsTenant(tenant1, () -> artifactRepository
                .countBySha1HashAndTenantAndSoftwareModuleDeletedIsFalse(artifactTenant2.getSha1Hash(), tenant2)))
                        .isLessThanOrEqualTo(1);
        runAsTenant(tenant2, () -> {
            artifactRepository.deleteById(artifactTenant2.getId());
            return null;
        });
        verifyTenantArtifactCountIs(tenant1, 1);
        verifyTenantArtifactCountIs(tenant2, 1);
    }

    @Test
    @Description("Loads an local artifact based on given ID.")
    public void findArtifact() throws IOException {
        final int artifactSize = 5 * 1024;
        try (final InputStream inputStream = new RandomGeneratedInputStream(artifactSize)) {
            final Artifact artifact = createArtifactForSoftwareModule("file1",
                    testdataFactory.createSoftwareModuleOs().getId(), artifactSize, inputStream);
            assertThat(artifactManagement.get(artifact.getId()).get()).isEqualTo(artifact);
        }
    }

    @Test
    @Description("Loads an artifact binary based on given ID.")
    public void loadStreamOfArtifact() throws IOException {
        final int artifactSize = 5 * 1024;
        final byte[] randomBytes = randomBytes(artifactSize);
        try (final InputStream input = new ByteArrayInputStream(randomBytes)) {
            final Artifact artifact = createArtifactForSoftwareModule("file1",
                    testdataFactory.createSoftwareModuleOs().getId(), artifactSize, input);
            assertEqualFileContents(artifactManagement.loadArtifactBinary(artifact.getSha1Hash()), randomBytes);
        }
    }

    @Test
    @WithUser(allSpPermissions = true, removeFromAllPermission = { SpPermission.DOWNLOAD_REPOSITORY_ARTIFACT })
    @Description("Trys and fails to load an artifact without required permission. Checks if expected InsufficientPermissionException is thrown.")
    public void loadArtifactBinaryWithoutDownloadArtifactThrowsPermissionDenied() {
        assertThatExceptionOfType(InsufficientPermissionException.class)
                .as("Should not have worked with missing permission.")
                .isThrownBy(() -> artifactManagement.loadArtifactBinary("123"));
    }

    @Test
    @Description("Searches an artifact through the relations of a software module.")
    public void findArtifactBySoftwareModule() throws IOException {
        final SoftwareModule sm = testdataFactory.createSoftwareModuleOs();
        assertThat(artifactManagement.findBySoftwareModule(PAGE, sm.getId())).isEmpty();

        final int artifactSize = 5 * 1024;
        try (final InputStream input = new RandomGeneratedInputStream(artifactSize)) {
            createArtifactForSoftwareModule("file1", sm.getId(), artifactSize, input);
            assertThat(artifactManagement.findBySoftwareModule(PAGE, sm.getId())).hasSize(1);
        }
    }

    @Test
    @Description("Searches an artifact through the relations of a software module and the filename.")
    public void findByFilenameAndSoftwareModule() throws IOException {
        final SoftwareModule sm = testdataFactory.createSoftwareModuleOs();

        assertThat(artifactManagement.getByFilenameAndSoftwareModule("file1", sm.getId())).isNotPresent();

        final int artifactSize = 5 * 1024;
        try (final InputStream inputStream1 = new RandomGeneratedInputStream(artifactSize);
                final InputStream inputStream2 = new RandomGeneratedInputStream(artifactSize)) {
            createArtifactForSoftwareModule("file1", sm.getId(), artifactSize, inputStream1);
            createArtifactForSoftwareModule("file2", sm.getId(), artifactSize, inputStream2);
            assertThat(artifactManagement.getByFilenameAndSoftwareModule("file1", sm.getId())).isPresent();
        }
    }

    private Artifact createArtifactForSoftwareModule(final String filename, final long moduleId, final int artifactSize)
            throws IOException {
        final byte[] randomBytes = randomBytes(artifactSize);
        try (final InputStream inputStream = new ByteArrayInputStream(randomBytes)) {
            return createArtifactForSoftwareModule(filename, moduleId, artifactSize, inputStream);
        }
    }

    private Artifact createArtifactForSoftwareModule(final String filename, final long moduleId, final int artifactSize,
            final InputStream inputStream) {
        return artifactManagement.create(new ArtifactUpload(inputStream, moduleId, filename, false, artifactSize));
    }

    private static byte[] randomBytes(final int len) {
        return RandomStringUtils.randomAlphanumeric(len).getBytes();
    }

    private <T> T runAsTenant(final String tenant, final Callable<T> callable) throws Exception {
        return WithSpringAuthorityRule.runAs(WithSpringAuthorityRule.withUserAndTenant("user", tenant), callable);
    }

    private SoftwareModule createSoftwareModuleForTenant(final String tenant) throws Exception {
        return runAsTenant(tenant, () -> testdataFactory.createSoftwareModuleApp());
    }

    private Artifact createArtifactForTenant(final String tenant, final String artifactData, final long moduleId,
            final String filename) throws Exception {
        return runAsTenant(tenant, () -> testdataFactory.createArtifact(artifactData, moduleId, filename));
    }

    private void verifyTenantArtifactCountIs(final String tenant, final int count) throws Exception {
        assertThat(runAsTenant(tenant, () -> artifactRepository.findAll())).hasSize(count);
    }

    private void assertEqualFileContents(final Optional<AbstractDbArtifact> artifact, final byte[] randomBytes)
            throws IOException {
        try (final InputStream inputStream = artifactManagement.loadArtifactBinary(artifact.get().getHashes().getSha1())
                .get().getFileInputStream()) {
            assertTrue(IOUtils.contentEquals(new ByteArrayInputStream(randomBytes), inputStream),
                    "The stored binary matches the given binary");
        }
    }

}
