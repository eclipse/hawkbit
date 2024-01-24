/**
 * Copyright (c) 2021 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.repository.jpa;

import java.io.InputStream;
import java.util.function.UnaryOperator;

import org.eclipse.hawkbit.artifact.repository.model.DbArtifact;
import org.eclipse.hawkbit.artifact.repository.model.DbArtifactHash;

/**
 * {@link DbArtifact} implementation that decrypts the underlying artifact
 * binary input stream.
 */
public class EncryptionAwareDbArtifact implements DbArtifact {

    private final DbArtifact encryptedDbArtifact;
    private final UnaryOperator<InputStream> decryptionFunction;
    private final int encryptionOverhead;

    public EncryptionAwareDbArtifact(final DbArtifact encryptedDbArtifact,
            final UnaryOperator<InputStream> decryptionFunction) {
        this.encryptedDbArtifact = encryptedDbArtifact;
        this.decryptionFunction = decryptionFunction;
        this.encryptionOverhead = 0;
    }

    public EncryptionAwareDbArtifact(final DbArtifact encryptedDbArtifact,
            final UnaryOperator<InputStream> decryptionFunction, final int encryptionOverhead) {
        this.encryptedDbArtifact = encryptedDbArtifact;
        this.decryptionFunction = decryptionFunction;
        this.encryptionOverhead = encryptionOverhead;
    }

    @Override
    public String getArtifactId() {
        return encryptedDbArtifact.getArtifactId();
    }

    @Override
    public DbArtifactHash getHashes() {
        return encryptedDbArtifact.getHashes();
    }

    @Override
    public long getSize() {
        return encryptedDbArtifact.getSize() - encryptionOverhead;
    }

    @Override
    public String getContentType() {
        return encryptedDbArtifact.getContentType();
    }

    @Override
    public InputStream getFileInputStream() {
        return decryptionFunction.apply(encryptedDbArtifact.getFileInputStream());
    }

    @Override
    public InputStream getFileInputStream(long start, long end) {
        return decryptionFunction.apply(encryptedDbArtifact.getFileInputStream(start, end));
    }
}
