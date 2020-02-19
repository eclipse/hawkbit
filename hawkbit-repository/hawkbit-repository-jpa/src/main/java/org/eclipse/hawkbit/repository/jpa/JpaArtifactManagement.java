/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.eclipse.hawkbit.artifact.repository.ArtifactRepository;
import org.eclipse.hawkbit.artifact.repository.ArtifactStoreException;
import org.eclipse.hawkbit.artifact.repository.HashNotMatchException;
import org.eclipse.hawkbit.artifact.repository.model.AbstractDbArtifact;
import org.eclipse.hawkbit.artifact.repository.model.DbArtifactHash;
import org.eclipse.hawkbit.repository.ArtifactManagement;
import org.eclipse.hawkbit.repository.QuotaManagement;
import org.eclipse.hawkbit.repository.exception.ArtifactDeleteFailedException;
import org.eclipse.hawkbit.repository.exception.ArtifactUploadFailedException;
import org.eclipse.hawkbit.repository.exception.EntityAlreadyExistsException;
import org.eclipse.hawkbit.repository.exception.EntityNotFoundException;
import org.eclipse.hawkbit.repository.exception.InvalidMD5HashException;
import org.eclipse.hawkbit.repository.exception.InvalidSHA1HashException;
import org.eclipse.hawkbit.repository.exception.InvalidSHA256HashException;
import org.eclipse.hawkbit.repository.jpa.configuration.Constants;
import org.eclipse.hawkbit.repository.jpa.model.JpaArtifact;
import org.eclipse.hawkbit.repository.jpa.model.JpaSoftwareModule;
import org.eclipse.hawkbit.repository.jpa.utils.FileSizeAndStorageQuotaCheckingInputStream;
import org.eclipse.hawkbit.repository.jpa.utils.QuotaHelper;
import org.eclipse.hawkbit.repository.model.Artifact;
import org.eclipse.hawkbit.repository.model.ArtifactUpload;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * JPA based {@link ArtifactManagement} implementation.
 *
 */
@Transactional(readOnly = true)
@Validated
public class JpaArtifactManagement implements ArtifactManagement {

    private static final Logger LOG = LoggerFactory.getLogger(JpaArtifactManagement.class);

    private final LocalArtifactRepository localArtifactRepository;

    private final SoftwareModuleRepository softwareModuleRepository;

    private final ArtifactRepository artifactRepository;

    private final TenantAware tenantAware;

    private final QuotaManagement quotaManagement;

    JpaArtifactManagement(final LocalArtifactRepository localArtifactRepository,
            final SoftwareModuleRepository softwareModuleRepository, final ArtifactRepository artifactRepository,
            final QuotaManagement quotaManagement, final TenantAware tenantAware) {
        this.localArtifactRepository = localArtifactRepository;
        this.softwareModuleRepository = softwareModuleRepository;
        this.artifactRepository = artifactRepository;
        this.quotaManagement = quotaManagement;
        this.tenantAware = tenantAware;
    }

    private static Artifact checkForExistingArtifact(final String filename, final boolean overrideExisting,
            final SoftwareModule softwareModule) {
        final Optional<Artifact> artifact = softwareModule.getArtifactByFilename(filename);

        if (artifact.isPresent()) {
            if (overrideExisting) {
                LOG.debug("overriding existing artifact with new filename {}", filename);
                return artifact.get();
            } else {
                throw new EntityAlreadyExistsException("File with that name already exists in the Software Module");
            }
        }
        return null;
    }

    @Override
    @Transactional
    @Retryable(include = {
            ConcurrencyFailureException.class }, maxAttempts = Constants.TX_RT_MAX, backoff = @Backoff(delay = Constants.TX_RT_DELAY))
    public Artifact create(final ArtifactUpload artifactUpload) {
        final String filename = artifactUpload.getFilename();
        final long moduleId = artifactUpload.getModuleId();

        final SoftwareModule softwareModule = getModuleAndThrowExceptionIfThatFails(moduleId);

        final Artifact existing = checkForExistingArtifact(filename, artifactUpload.overrideExisting(), softwareModule);

        assertArtifactQuota(moduleId, 1);

        return getOrCreateArtifact(artifactUpload)
                .map(artifact -> storeArtifactMetadata(softwareModule, filename, artifact, existing)).orElse(null);
    }

    private Optional<AbstractDbArtifact> getOrCreateArtifact(final ArtifactUpload artifactUpload) {
        final String providedSha1Sum = artifactUpload.getProvidedSha1Sum();

        if (!StringUtils.isEmpty(providedSha1Sum)
                && artifactRepository.existsByTenantAndSha1(tenantAware.getCurrentTenant(), providedSha1Sum)) {
            return Optional
                    .ofNullable(artifactRepository.getArtifactBySha1(tenantAware.getCurrentTenant(), providedSha1Sum));
        }

        return Optional.of(storeArtifact(artifactUpload));
    }

    private AbstractDbArtifact storeArtifact(final ArtifactUpload artifactUpload) {
        try(final InputStream quotaStream = wrapInQuotaStream(artifactUpload.getInputStream())) {
            return artifactRepository.store(tenantAware.getCurrentTenant(), quotaStream,
                    artifactUpload.getFilename(), artifactUpload.getContentType(),
                    new DbArtifactHash(artifactUpload.getProvidedSha1Sum(), artifactUpload.getProvidedMd5Sum(),
                            artifactUpload.getProvidedSha256Sum()));
        } catch (final ArtifactStoreException | IOException e) {
            throw new ArtifactUploadFailedException(e);
        } catch (final HashNotMatchException e) {
            if (e.getHashFunction().equals(HashNotMatchException.SHA1)) {
                throw new InvalidSHA1HashException(e.getMessage(), e);
            } else if (e.getHashFunction().equals(HashNotMatchException.SHA256)) {
                throw new InvalidSHA256HashException(e.getMessage(), e);
            } else {
                throw new InvalidMD5HashException(e.getMessage(), e);
            }
        }
    }

    private void assertArtifactQuota(final long id, final int requested) {
        QuotaHelper.assertAssignmentQuota(id, requested, quotaManagement.getMaxArtifactsPerSoftwareModule(),
                Artifact.class, SoftwareModule.class, localArtifactRepository::countBySoftwareModuleId);
    }

    private InputStream wrapInQuotaStream(final InputStream in) {
        final long maxArtifactSize = quotaManagement.getMaxArtifactSize();

        final long currentlyUsed = localArtifactRepository.getSumOfUndeletedArtifactSize().orElse(0L);
        final long maxArtifactSizeTotal = quotaManagement.getMaxArtifactStorage();

        return new FileSizeAndStorageQuotaCheckingInputStream(in, maxArtifactSize,
                maxArtifactSizeTotal - currentlyUsed);
    }

    @Override
    @Transactional
    @Retryable(include = {
            ConcurrencyFailureException.class }, maxAttempts = Constants.TX_RT_MAX, backoff = @Backoff(delay = Constants.TX_RT_DELAY))
    public boolean clearArtifactBinary(final String sha1Hash, final long moduleId) {
        final long count = localArtifactRepository.countBySha1HashAndTenantAndSoftwareModuleDeletedIsFalse(sha1Hash,
                tenantAware.getCurrentTenant());
        if (count > 1) {
            // there are still other artifacts that need the binary
            return false;
        }
        try {
            LOG.debug("deleting artifact from repository {}", sha1Hash);
            artifactRepository.deleteBySha1(tenantAware.getCurrentTenant(), sha1Hash);
            return true;
        } catch (final ArtifactStoreException e) {
            throw new ArtifactDeleteFailedException(e);
        }
    }

    @Override
    @Transactional
    @Retryable(include = {
            ConcurrencyFailureException.class }, maxAttempts = Constants.TX_RT_MAX, backoff = @Backoff(delay = Constants.TX_RT_DELAY))
    public void delete(final long id) {
        final JpaArtifact existing = (JpaArtifact) get(id)
                .orElseThrow(() -> new EntityNotFoundException(Artifact.class, id));

        clearArtifactBinary(existing.getSha1Hash(), existing.getSoftwareModule().getId());

        ((JpaSoftwareModule) existing.getSoftwareModule()).removeArtifact(existing);
        softwareModuleRepository.save((JpaSoftwareModule) existing.getSoftwareModule());
        localArtifactRepository.deleteById(id);
    }

    @Override
    public Optional<Artifact> get(final long id) {
        return Optional.ofNullable(localArtifactRepository.findById(id).orElse(null));
    }

    @Override
    public Optional<Artifact> getByFilenameAndSoftwareModule(final String filename, final long softwareModuleId) {
        throwExceptionIfSoftwareModuleDoesNotExist(softwareModuleId);

        return localArtifactRepository.findFirstByFilenameAndSoftwareModuleId(filename, softwareModuleId);
    }

    @Override
    public Optional<Artifact> findFirstBySHA1(final String sha1Hash) {
        return localArtifactRepository.findFirstBySha1Hash(sha1Hash);
    }

    @Override
    public Optional<Artifact> getByFilename(final String filename) {
        return localArtifactRepository.findFirstByFilename(filename);
    }

    @Override
    public Page<Artifact> findBySoftwareModule(final Pageable pageReq, final long swId) {
        throwExceptionIfSoftwareModuleDoesNotExist(swId);

        return localArtifactRepository.findBySoftwareModuleId(pageReq, swId);
    }

    private void throwExceptionIfSoftwareModuleDoesNotExist(final Long swId) {
        if (!softwareModuleRepository.existsById(swId)) {
            throw new EntityNotFoundException(SoftwareModule.class, swId);
        }
    }

    @Override
    public Optional<AbstractDbArtifact> loadArtifactBinary(final String sha1Hash) {
        return Optional.ofNullable(artifactRepository.existsByTenantAndSha1(tenantAware.getCurrentTenant(), sha1Hash)
                ? artifactRepository.getArtifactBySha1(tenantAware.getCurrentTenant(), sha1Hash)
                : null);
    }

    private Artifact storeArtifactMetadata(final SoftwareModule softwareModule, final String providedFilename,
            final AbstractDbArtifact result, final Artifact existing) {
        JpaArtifact artifact = (JpaArtifact) existing;
        if (existing == null) {
            artifact = new JpaArtifact(result.getHashes().getSha1(), providedFilename, softwareModule);
        }
        artifact.setMd5Hash(result.getHashes().getMd5());
        artifact.setSha256Hash(result.getHashes().getSha256());
        artifact.setSha1Hash(result.getHashes().getSha1());
        artifact.setSize(result.getSize());

        LOG.debug("storing new artifact into repository {}", artifact);
        return localArtifactRepository.save(artifact);
    }

    @Override
    public long count() {
        return localArtifactRepository.count();
    }

    private SoftwareModule getModuleAndThrowExceptionIfThatFails(final Long moduleId) {
        return softwareModuleRepository.findById(moduleId)
                .orElseThrow(() -> new EntityNotFoundException(SoftwareModule.class, moduleId));
    }

}
