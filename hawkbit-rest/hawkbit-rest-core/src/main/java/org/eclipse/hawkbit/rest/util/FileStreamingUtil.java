/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.rest.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.eclipse.hawkbit.artifact.repository.model.DbArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.common.math.DoubleMath;

/**
 * Utility class for artifact file streaming.
 */
public final class FileStreamingUtil {

    private static final Logger LOG = LoggerFactory.getLogger(FileStreamingUtil.class);

    /**
     * File suffix for MDH hash download (see Linux md5sum).
     */
    public static final String ARTIFACT_MD5_DWNL_SUFFIX = ".MD5SUM";

    private static final int BUFFER_SIZE = 0x2000; // 8k

    private FileStreamingUtil() {

    }

    /**
     * Write a md5 file response.
     *
     * @param response
     *            the response
     * @param md5Hash
     *            of the artifact
     * @param filename
     *            as provided by the client
     * @return the response
     * @throws IOException
     *             cannot write output stream
     */
    public static ResponseEntity<Void> writeMD5FileResponse(final HttpServletResponse response, final String md5Hash,
            final String filename) throws IOException {

        if (md5Hash == null) {
            return ResponseEntity.notFound().build();
        }

        final StringBuilder builder = new StringBuilder();
        builder.append(md5Hash);
        builder.append("  ");
        builder.append(filename);
        final byte[] content = builder.toString().getBytes(StandardCharsets.US_ASCII);

        final StringBuilder header = new StringBuilder().append("attachment;filename=").append(filename)
                .append(ARTIFACT_MD5_DWNL_SUFFIX);

        response.setContentLength(content.length);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, header.toString());

        response.getOutputStream().write(content);

        return ResponseEntity.ok().build();
    }

    /**
     * <p>
     * Write response with target relation and publishes events concerning the
     * download progress based on given update action status.
     * </p>
     *
     * <p>
     * The request supports RFC7233 range requests.
     * </p>
     *
     * @param artifact
     *            the artifact
     * @param filename
     *            to be written to the client response
     * @param lastModified
     *            unix timestamp of the artifact
     * @param response
     *            to be sent back to the requesting client
     * @param request
     *            from the client
     * @param progressListener
     *            to write progress updates to
     *
     * @return http response
     *
     * @see <a href="https://tools.ietf.org/html/rfc7233">https://tools.ietf.org
     *      /html/rfc7233</a>
     * 
     * @throws FileStreamingFailedException
     *             if streaming fails
     */
    public static ResponseEntity<InputStream> writeFileResponse(final DbArtifact artifact, final String filename,
            final long lastModified, final HttpServletResponse response, final HttpServletRequest request,
            final FileStreamingProgressListener progressListener) {

        ResponseEntity<InputStream> result;

        final String etag = artifact.getHashes().getSha1();
        final long length = artifact.getSize();

        resetResponseExceptHeaders(response);

        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + filename);
        response.setHeader(HttpHeaders.ETAG, etag);
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        // set the x-content-type options header to prevent browsers from doing
        // MIME-sniffing when downloading an artifact, as this could cause a
        // security vulnerability
        response.setHeader(com.google.common.net.HttpHeaders.X_CONTENT_TYPE_OPTIONS, "nosniff");
        if (lastModified > 0) {
            response.setDateHeader(HttpHeaders.LAST_MODIFIED, lastModified);
        }

        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setBufferSize(BUFFER_SIZE);

        final ByteRange full = new ByteRange(0, length - 1, length);
        final List<ByteRange> ranges = new ArrayList<>();

        // Validate and process Range and If-Range headers.
        final String range = request.getHeader("Range");
        if (lastModified > 0 && range != null) {
            LOG.debug("range header for filename ({}) is: {}", filename, range);

            // Range header matches"bytes=n-n,n-n,n-n..."
            if (!range.matches("^bytes=\\d*-\\d*(,\\d*-\\d*)*+$")) {
                response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + length);
                LOG.debug("range header for filename ({}) is not satisfiable: ", filename);
                return new ResponseEntity<>(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
            }

            // RFC: if the representation is unchanged, send me the part(s) that
            // I am requesting in
            // Range; otherwise, send me the entire representation.
            checkForShortcut(request, etag, lastModified, full, ranges);

            // it seems there are valid ranges
            result = extractRange(response, length, ranges, range);
            // return if range extraction turned out to be invalid
            if (result != null) {
                return result;
            }
        }

        // full request - no range
        if (ranges.isEmpty() || ranges.get(0).equals(full)) {
            LOG.debug("filename ({}) results into a full request: ", filename);
            result = handleFullFileRequest(artifact, filename, response, progressListener, full);
        }
        // standard range request
        else if (ranges.size() == 1) {
            LOG.debug("filename ({}) results into a standard range request: ", filename);
            result = handleStandardRangeRequest(artifact, filename, response, progressListener, ranges);
        }
        // multipart range request
        else {
            LOG.debug("filename ({}) results into a multipart range request: ", filename);
            result = handleMultipartRangeRequest(artifact, filename, response, progressListener, ranges);
        }

        return result;
    }

    private static void resetResponseExceptHeaders(final HttpServletResponse response){
        // do backup the current headers (like CORS related)
        final Map<String, String> storedHeaders = new HashMap<>();
        for (final String header : response.getHeaderNames()) {
            storedHeaders.put(header, response.getHeader(header));
        }
        // resetting the response is needed only partially. Headers set before e.b. by
        // the CORS security config needs to be persisted.
        response.reset();
        // restore headers again
        storedHeaders.forEach(response::addHeader);
    }

    private static ResponseEntity<InputStream> handleFullFileRequest(final DbArtifact artifact, final String filename,
            final HttpServletResponse response, final FileStreamingProgressListener progressListener,
            final ByteRange full) {
        final ByteRange r = full;
        response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + r.getStart() + "-" + r.getEnd() + "/" + r.getTotal());
        response.setContentLengthLong(r.getLength());

        try (final InputStream from = artifact.getFileInputStream()) {
            final ServletOutputStream to = response.getOutputStream();
            copyStreams(from, to, progressListener, r.getStart(), r.getLength(), filename);
        } catch (final IOException e) {
            throw new FileStreamingFailedException("fullfileRequest " + filename, e);
        }

        return ResponseEntity.ok().build();
    }

    private static ResponseEntity<InputStream> extractRange(final HttpServletResponse response, final long length,
            final List<ByteRange> ranges, final String range) {

        if (ranges.isEmpty()) {
            for (final String part : range.substring(6).split(",")) {
                long start = sublong(part, 0, part.indexOf('-'));
                long end = sublong(part, part.indexOf('-') + 1, part.length());

                if (start == -1) {
                    start = length - end;
                    end = length - 1;
                } else if (end == -1 || end > length - 1) {
                    end = length - 1;
                }

                // Check if Range is syntactically valid. If not, then return
                // 416.
                if (start > end) {
                    response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + length);
                    return new ResponseEntity<>(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
                }

                // Add range.
                ranges.add(new ByteRange(start, end, length));
            }
        }

        return null;
    }

    private static long sublong(final String value, final int beginIndex, final int endIndex) {
        final String substring = value.substring(beginIndex, endIndex);
        return substring.length() > 0 ? Long.parseLong(substring) : -1;
    }

    private static void checkForShortcut(final HttpServletRequest request, final String etag, final long lastModified,
            final ByteRange full, final List<ByteRange> ranges) {
        final String ifRange = request.getHeader(HttpHeaders.IF_RANGE);
        if (ifRange != null && !ifRange.equals(etag)) {
            try {
                final long ifRangeTime = request.getDateHeader(HttpHeaders.IF_RANGE);
                if (ifRangeTime != -1 && ifRangeTime + 1000 < lastModified) {
                    ranges.add(full);
                }
            } catch (final IllegalArgumentException ignore) {
                LOG.info("Invalid if-range header field", ignore);
                ranges.add(full);
            }
        }
    }

    private static ResponseEntity<InputStream> handleMultipartRangeRequest(final DbArtifact artifact,
            final String filename, final HttpServletResponse response,
            final FileStreamingProgressListener progressListener, final List<ByteRange> ranges) {

        response.setContentType("multipart/byteranges; boundary=" + ByteRange.MULTIPART_BOUNDARY);
        response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);

        try {
            final ServletOutputStream to = response.getOutputStream();

            for (final ByteRange r : ranges) {

                try (final InputStream from = artifact.getFileInputStream(r.getStart(), r.getEnd())) {
                    // Add multipart boundary and header fields for every range.
                    to.println();
                    to.println("--" + ByteRange.MULTIPART_BOUNDARY);
                    to.println(HttpHeaders.CONTENT_RANGE + ": bytes " + r.getStart() + "-" + r.getEnd() + "/"
                            + r.getTotal());

                    // Copy single part range of multi part range.
                    copyStreams(from, to, progressListener, r.getStart(), r.getLength(), filename);
                }
            }

            // End with final multipart boundary.
            to.println();
            to.print("--" + ByteRange.MULTIPART_BOUNDARY + "--");
        } catch (final IOException e) {
            throw new FileStreamingFailedException("multipartRangeRequest " + filename, e);
        }

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).build();
    }

    private static ResponseEntity<InputStream> handleStandardRangeRequest(final DbArtifact artifact,
            final String filename, final HttpServletResponse response,
            final FileStreamingProgressListener progressListener, final List<ByteRange> ranges) {
        final ByteRange r = ranges.get(0);
        response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + r.getStart() + "-" + r.getEnd() + "/" + r.getTotal());
        response.setContentLengthLong(r.getLength());
        response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);

        try (final InputStream from = artifact.getFileInputStream(r.getStart(), r.getEnd())) {
            final ServletOutputStream to = response.getOutputStream();
            copyStreams(from, to, progressListener, r.getStart(), r.getLength(), filename);
        } catch (final IOException e) {
            LOG.error("standardRangeRequest of file ({}) failed!", filename, e);
            throw new FileStreamingFailedException(filename);
        }

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).build();
    }

    private static long copyStreams(final InputStream from, final OutputStream to,
            final FileStreamingProgressListener progressListener, final long start, final long length,
            final String filename) throws IOException {

        final long startMillis = System.currentTimeMillis();
        LOG.trace("Start of copy-streams of file {} from {} to {}", filename, start, length);

        Preconditions.checkNotNull(from);
        Preconditions.checkNotNull(to);
        final byte[] buf = new byte[BUFFER_SIZE];
        long total = 0;
        int progressPercent = 1;

        long toRead = length;
        boolean toContinue = true;
        long shippedSinceLastEvent = 0;

        while (toContinue) {
            final int r = from.read(buf);
            if (r == -1) {
                break;
            }

            toRead -= r;
            if (toRead > 0) {
                to.write(buf, 0, r);
                total += r;
                shippedSinceLastEvent += r;
            } else {
                to.write(buf, 0, (int) toRead + r);
                total += toRead + r;
                shippedSinceLastEvent += toRead + r;
                toContinue = false;
            }

            if (progressListener != null) {
                final int newPercent = DoubleMath.roundToInt(total * 100.0 / length, RoundingMode.DOWN);

                // every 10 percent an event
                if (newPercent == 100 || newPercent > progressPercent + 10) {
                    progressPercent = newPercent;
                    progressListener.progress(length, shippedSinceLastEvent, total);
                    shippedSinceLastEvent = 0;
                }
            }
        }

        final long totalTime = System.currentTimeMillis() - startMillis;

        if (total < length) {
            throw new FileStreamingFailedException(filename + ": " + (length - total)
                    + " bytes could not be written to client, total time on write: !" + totalTime + " ms");
        }

        LOG.trace("Finished copy-stream of file {} with length {} in {} ms", filename, length, totalTime);

        return total;
    }

    private static final class ByteRange {
        private static final String MULTIPART_BOUNDARY = "THIS_STRING_SEPARATES_MULTIPART";

        private final long start;
        private final long end;
        private final long length;
        private final long total;

        private ByteRange(final long start, final long end, final long total) {
            this.start = start;
            this.end = end;
            length = end - start + 1;
            this.total = total;
        }

        private long getStart() {
            return start;
        }

        private long getEnd() {
            return end;
        }

        private long getLength() {
            return length;
        }

        private long getTotal() {
            return total;
        }

        @Override
        // Generated code
        @SuppressWarnings("squid:S864")
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (int) (end ^ (end >>> 32));
            result = prime * result + (int) (length ^ (length >>> 32));
            result = prime * result + (int) (start ^ (start >>> 32));
            result = prime * result + (int) (total ^ (total >>> 32));
            return result;
        }

        @Override
        // Generated code
        @SuppressWarnings("squid:S1126")
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ByteRange other = (ByteRange) obj;
            if (end != other.end) {
                return false;
            }
            if (length != other.length) {
                return false;
            }
            if (start != other.start) {
                return false;
            }
            if (total != other.total) {
                return false;
            }
            return true;
        }

    }

}
