/**
 * Copyright (c) 2021 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.artifact.repository.model;

import java.io.InputStream;

/**
 * Interface definition for artifact binary.
 */
public interface DbArtifact {

    /**
     * @return ID of the artifact
     */
    String getArtifactId();

    /**
     * @return hashes of the artifact
     */
    DbArtifactHash getHashes();

    /**
     * @return size of the artifact in bytes
     */
    long getSize();

    /**
     * @return content-type if known by the repository or <code>null</code>
     */
    String getContentType();

    /**
     * Creates an {@link InputStream} on this artifact. Caller has to take care of
     * closing the stream. Repeatable calls open a new {@link InputStream}.
     * 
     * @return {@link InputStream} to read from artifact.
     */
    InputStream getFileInputStream();

    /**
     * Creates an {@link InputStream} on a single range of this artifact.
     * The caller has to take care of closing the stream.
     * Repeatable calls open a new {@link InputStream}.
     *
     * @return {@link InputStream} to read from artifact.
     */
    InputStream getFileInputStream(long start, long end);
}
