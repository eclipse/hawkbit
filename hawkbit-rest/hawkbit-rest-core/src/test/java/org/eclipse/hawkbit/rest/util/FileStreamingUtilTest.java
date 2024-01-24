/**
 * Copyright (c) 2022 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.rest.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.eclipse.hawkbit.artifact.repository.model.DbArtifact;
import org.eclipse.hawkbit.artifact.repository.model.DbArtifactHash;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@Feature("Component Tests - Management API")
@Story("File streaming")
class FileStreamingUtilTest {
    private final static String CONTENT = "This is some very long string which is intended to test";
    private final static byte[] CONTENT_BYTES = CONTENT.getBytes(StandardCharsets.UTF_8);

    private static final DbArtifact TEST_ARTIFACT = new DbArtifact() {

        @Override
        public String getArtifactId() {
            return "1";
        }

        @Override
        public DbArtifactHash getHashes() {
            return new DbArtifactHash("sha1-111", "md5-123", "sha256-123");
        }

        @Override
        public long getSize() {
            return CONTENT_BYTES.length;
        }

        @Override
        public String getContentType() {
            return "text/plain";
        }

        @Override
        public InputStream getFileInputStream() {
            return new ByteArrayInputStream(CONTENT_BYTES);
        }

        @Override
        public InputStream getFileInputStream(long start, long end) {
            return new ByteArrayInputStream(CONTENT_BYTES, (int) start, (int) (end - start + 1));
        }
    };

    @Test
    void shouldProcessRangeHeaderForMultipartRequests() throws IOException {
        final HttpServletResponse servletResponse = Mockito.mock(HttpServletResponse.class);
        final ServletOutputStream outputStream = Mockito.mock(ServletOutputStream.class);

        Mockito.when(servletResponse.getOutputStream()).thenReturn(outputStream);
        final HttpServletRequest servletRequest = Mockito.mock(HttpServletRequest.class);
        Mockito.when(servletRequest.getHeader("Range")).thenReturn("bytes=0-10,9-15,16-");
        long lastModified = System.currentTimeMillis();

        final ResponseEntity<InputStream> responseEntity = FileStreamingUtil.writeFileResponse(TEST_ARTIFACT,
                "test.file", lastModified, servletResponse, servletRequest, null);

        assertThat(responseEntity).isNotNull();
        verify(servletResponse).setDateHeader(HttpHeaders.LAST_MODIFIED, lastModified);
        final ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Integer> lenCaptor = ArgumentCaptor.forClass(Integer.class);

        verify(outputStream).print(stringCaptor.capture());
        assertThat(stringCaptor.getValue()).contains("--THIS_STRING_SEPARATES_MULTIPART--");
        verify(outputStream, times(3)).write(any(), anyInt(), lenCaptor.capture());
        assertThat(lenCaptor.getAllValues()).containsExactly(11, 7, 39); // Range lengths
    }

    @Test
    void shouldValidateRangeHeaderForMultipartRequests() throws IOException {
        long lastModified = System.currentTimeMillis();
        final HttpServletResponse servletResponse = Mockito.mock(HttpServletResponse.class);
        final ServletOutputStream outputStream = Mockito.mock(ServletOutputStream.class);
        Mockito.when(servletResponse.getOutputStream()).thenReturn(outputStream);

        final HttpServletRequest servletRequest = Mockito.mock(HttpServletRequest.class);
        Mockito.when(servletRequest.getHeader("Range")).thenReturn("bytes=0-10***,9-15,16-");

        final ResponseEntity<InputStream> responseEntity = FileStreamingUtil.writeFileResponse(TEST_ARTIFACT,
                "test.file", lastModified, servletResponse, servletRequest, null);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        verify(outputStream, times(0)).print(anyString());
        verify(outputStream, times(0)).write(any(), anyInt(), anyInt());
    }
}
