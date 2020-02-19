/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.artifacts.upload;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.hawkbit.repository.ArtifactManagement;
import org.eclipse.hawkbit.repository.RegexCharacterCollection;
import org.eclipse.hawkbit.repository.RegexCharacterCollection.RegexChar;
import org.eclipse.hawkbit.repository.exception.ArtifactUploadFailedException;
import org.eclipse.hawkbit.repository.exception.AssignmentQuotaExceededException;
import org.eclipse.hawkbit.repository.exception.FileSizeQuotaExceededException;
import org.eclipse.hawkbit.repository.exception.InvalidMD5HashException;
import org.eclipse.hawkbit.repository.exception.InvalidSHA1HashException;
import org.eclipse.hawkbit.repository.exception.StorageQuotaExceededException;
import org.eclipse.hawkbit.repository.model.Artifact;
import org.eclipse.hawkbit.repository.model.ArtifactUpload;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.ui.artifacts.event.SoftwareModuleEvent;
import org.eclipse.hawkbit.ui.artifacts.event.SoftwareModuleEvent.SoftwareModuleEventType;
import org.eclipse.hawkbit.ui.artifacts.state.ArtifactUploadState;
import org.eclipse.hawkbit.ui.artifacts.upload.FileUploadProgress.FileUploadStatus;
import org.eclipse.hawkbit.ui.utils.SpringContextHelper;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vaadin.spring.events.EventBus;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.ui.UI;

/**
 * Abstract base class for transferring files from the browser to the
 * repository.
 */
public abstract class AbstractFileTransferHandler implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(AbstractFileTransferHandler.class);
    private static final String MESSAGE_UPLOAD_FAILED = "message.upload.failed";

    private volatile boolean uploadInterrupted;

    private volatile String failureReason;

    private final ArtifactUploadState artifactUploadState;

    private final transient UIEventBus eventBus;

    private final transient ArtifactManagement artifactManagement;

    private final VaadinMessageSource i18n;

    protected final UINotification uiNotification;

    private final transient Lock uploadLock;

    protected static final RegexCharacterCollection ILLEGAL_FILENAME_CHARACTERS = new RegexCharacterCollection(
            RegexChar.GREATER_THAN, RegexChar.LESS_THAN, RegexChar.SLASHES);

    AbstractFileTransferHandler(final ArtifactManagement artifactManagement, final VaadinMessageSource i18n,
            final Lock uploadLock) {
        this.artifactManagement = artifactManagement;
        this.i18n = i18n;
        this.eventBus = SpringContextHelper.getBean(EventBus.UIEventBus.class);
        this.artifactUploadState = SpringContextHelper.getBean(ArtifactUploadState.class);
        this.uiNotification = SpringContextHelper.getBean(UINotification.class);
        this.uploadLock = uploadLock;
    }

    protected boolean isUploadInterrupted() {
        return uploadInterrupted;
    }

    protected void resetState() {
        uploadInterrupted = false;
        failureReason = null;
    }

    protected ArtifactUploadState getUploadState() {
        return artifactUploadState;
    }

    protected VaadinMessageSource getI18n() {
        return i18n;
    }

    protected void startTransferToRepositoryThread(final InputStream inputStream, final FileUploadId fileUploadId,
            final String mimeType) {
        SpringContextHelper.getBean("asyncExecutor", ExecutorService.class)
                .execute(new TransferArtifactToRepositoryRunnable(inputStream, fileUploadId, mimeType, UI.getCurrent(),
                        uploadLock));
    }

    private void interruptUploadAndSetReason(final String failureReason) {
        uploadInterrupted = true;
        this.failureReason = failureReason;
    }

    protected void interruptUploadDueToUploadFailed() {
        interruptUploadAndSetReason(i18n.getMessage(MESSAGE_UPLOAD_FAILED));
    }

    protected void interruptUploadDueToAssignmentQuotaExceeded() {
        interruptUploadAndSetReason(i18n.getMessage("message.upload.assignmentQuota"));
    }

    protected void interruptUploadDueToFileSizeQuotaExceeded(final String exceededValue) {
        interruptUploadAndSetReason(i18n.getMessage("message.upload.fileSizeQuota", exceededValue));
    }

    protected void interruptUploadDueToStorageQuotaExceeded(final String exceededValue) {
        interruptUploadAndSetReason(i18n.getMessage("message.upload.storageQuota", exceededValue));
    }

    protected void interruptUploadDueToDuplicateFile() {
        interruptUploadAndSetReason(i18n.getMessage("message.no.duplicateFiles"));
    }

    protected void interruptUploadDueToIllegalFilename() {
        interruptUploadAndSetReason(i18n.getMessage("message.uploadedfile.illegalFilename"));
    }

    protected boolean isFileAlreadyContainedInSoftwareModule(final FileUploadId newFileUploadId,
            final SoftwareModule softwareModule) {
        for (final Artifact artifact : softwareModule.getArtifacts()) {
            final FileUploadId existingId = new FileUploadId(artifact.getFilename(), softwareModule);
            if (existingId.equals(newFileUploadId)) {
                return true;
            }
        }

        return false;
    }

    protected void publishUploadStarted(final FileUploadId fileUploadId) {
        LOG.info("Upload started for file {}", fileUploadId);
        final FileUploadProgress fileUploadProgress = new FileUploadProgress(fileUploadId,
                FileUploadStatus.UPLOAD_STARTED);
        artifactUploadState.updateFileUploadProgress(fileUploadId, fileUploadProgress);
        eventBus.publish(this, fileUploadProgress);
    }

    protected void publishUploadProgressEvent(final FileUploadId fileUploadId, final long bytesReceived,
            final long fileSize) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Upload in progress for file {} - {}%", fileUploadId,
                    String.format("%.0f", (double) bytesReceived / (double) fileSize * 100));
        }
        final FileUploadProgress fileUploadProgress = new FileUploadProgress(fileUploadId,
                FileUploadStatus.UPLOAD_IN_PROGRESS, bytesReceived, fileSize);
        artifactUploadState.updateFileUploadProgress(fileUploadId, fileUploadProgress);
        eventBus.publish(this, fileUploadProgress);
    }

    protected void publishUploadFinishedEvent(final FileUploadId fileUploadId) {
        LOG.debug("Upload finished for file {}", fileUploadId);
        final FileUploadProgress fileUploadProgress = new FileUploadProgress(fileUploadId,
                FileUploadStatus.UPLOAD_FINISHED);
        eventBus.publish(this, fileUploadProgress);
    }

    protected void publishUploadSucceeded(final FileUploadId fileUploadId, final long fileSize) {
        LOG.info("Upload succeeded for file {}", fileUploadId);
        final FileUploadProgress fileUploadProgress = new FileUploadProgress(fileUploadId,
                FileUploadStatus.UPLOAD_SUCCESSFUL, fileSize, fileSize);
        artifactUploadState.updateFileUploadProgress(fileUploadId, fileUploadProgress);
        eventBus.publish(this, fileUploadProgress);
    }

    protected void publishUploadFailedEvent(final FileUploadId fileUploadId) {
        LOG.info("Upload failed for file {} due to reason: {}", fileUploadId, failureReason);
        final FileUploadProgress fileUploadProgress = new FileUploadProgress(fileUploadId,
                FileUploadStatus.UPLOAD_FAILED,
                StringUtils.isBlank(failureReason) ? i18n.getMessage(MESSAGE_UPLOAD_FAILED) : failureReason);
        artifactUploadState.updateFileUploadProgress(fileUploadId, fileUploadProgress);
        eventBus.publish(this, fileUploadProgress);
    }

    protected void publishArtifactsChanged(final FileUploadId fileUploadId) {
        eventBus.publish(this,
                new SoftwareModuleEvent(SoftwareModuleEventType.ARTIFACTS_CHANGED, fileUploadId.getSoftwareModuleId()));
    }

    protected void publishUploadFailedAndFinishedEvent(final FileUploadId fileUploadId) {
        publishUploadFailedEvent(fileUploadId);
    }

    protected void assertStateConsistency(final FileUploadId fileUploadId, final String filenameExtractedFromEvent) {
        if (!filenameExtractedFromEvent.equals(fileUploadId.getFilename())) {
            throw new IllegalStateException("Event filename " + filenameExtractedFromEvent + " but stored filename "
                    + fileUploadId.getFilename());
        }
    }

    protected static void tryToCloseIOStream(final OutputStream outputStream) {
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (final IOException e1) {
                LOG.warn("Closing output stream caused an exception {}", e1);
            }
        }

    }

    protected static void tryToCloseIOStream(final InputStream inputStream) {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (final IOException e1) {
                LOG.warn("Closing input stream caused an exception {}", e1);
            }
        }
    }

    private final class TransferArtifactToRepositoryRunnable implements Runnable {
        private final InputStream inputStream;
        private final FileUploadId fileUploadId;
        private final String mimeType;
        private final UI vaadinUi;
        private final Lock uploadLock;

        public TransferArtifactToRepositoryRunnable(final InputStream inputStream, final FileUploadId fileUploadId,
                final String mimeType, final UI vaadinUi, final Lock uploadLock) {
            this.inputStream = inputStream;
            this.fileUploadId = fileUploadId;
            this.mimeType = mimeType;
            this.vaadinUi = vaadinUi;
            this.uploadLock = uploadLock;
        }

        /**
         * a lock object is used here that is propagated down from
         * {@link org.eclipse.hawkbit.ui.artifacts.UploadArtifactView}. It
         * ensures that from within the same UI instance all uploads are
         * executed sequentially to avoid issues that occur when multiple files
         * are processed at the same time (e.g. regarding quota checks)
         */
        @Override
        public void run() {
            try {
                UI.setCurrent(vaadinUi);
                uploadLock.lock();
                streamToRepository();
            } catch (final FileSizeQuotaExceededException e) {
                interruptUploadDueToFileSizeQuotaExceeded(e.getExceededQuotaValueString());
                LOG.debug("Upload failed due to file size quota exceeded:", e);
            } catch (final StorageQuotaExceededException e) {
                interruptUploadDueToStorageQuotaExceeded(e.getExceededQuotaValueString());
                LOG.debug("Upload failed due to storage quota exceeded:", e);
            } catch (final AssignmentQuotaExceededException e) {
                interruptUploadDueToAssignmentQuotaExceeded();
                LOG.debug("Upload failed due to assignment quota exceeded:", e);
            } catch (final RuntimeException e) {
                interruptUploadDueToUploadFailed();
                publishUploadFailedAndFinishedEvent(fileUploadId);
                LOG.warn("Failed to transfer file to repository", e);
            } finally {
                tryToCloseIOStream(inputStream);
                uploadLock.unlock();
            }
        }

        private void streamToRepository() {
            if (fileUploadId == null) {
                throw new ArtifactUploadFailedException();
            }
            final String filename = fileUploadId.getFilename();
            LOG.debug("Transfering file {} directly to repository", filename);
            final Artifact artifact = uploadArtifact(filename);
            if (isUploadInterrupted()) {
                handleUploadFailure(artifact);
                publishUploadFinishedEvent(fileUploadId);
                return;
            }
            publishUploadSucceeded(fileUploadId, artifact.getSize());
            publishUploadFinishedEvent(fileUploadId);
            publishArtifactsChanged(fileUploadId);
        }

        private Artifact uploadArtifact(final String filename) {
            try {
                return artifactManagement.create(new ArtifactUpload(inputStream, fileUploadId.getSoftwareModuleId(),
                        filename, null, null, null, true, mimeType, -1));
            } catch (final ArtifactUploadFailedException | InvalidSHA1HashException | InvalidMD5HashException e) {
                throw new ArtifactUploadFailedException(e);
            }
        }

        private void handleUploadFailure(final Artifact artifact) {
            Exception exception;
            int tries = 0;
            do {
                try {
                    artifactManagement.delete(artifact.getId());
                    return;
                } catch (final RuntimeException e) {
                    exception = e;
                    tries++;
                }
            } while (tries < 5);
            LOG.error("Failed to delete artifact from repository after upload was interrupted", exception);
        }
    }

}
