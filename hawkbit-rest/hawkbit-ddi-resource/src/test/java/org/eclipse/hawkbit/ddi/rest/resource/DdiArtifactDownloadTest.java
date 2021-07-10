/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ddi.rest.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.commons.lang3.RandomUtils;
import org.eclipse.hawkbit.ddi.rest.resource.DdiArtifactDownloadTest.DownloadTestConfiguration;
import org.eclipse.hawkbit.repository.event.remote.DownloadProgressEvent;
import org.eclipse.hawkbit.repository.model.Artifact;
import org.eclipse.hawkbit.repository.model.ArtifactUpload;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.test.util.TestdataFactory;
import org.eclipse.hawkbit.repository.test.util.WithUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.google.common.base.Charsets;
import com.google.common.net.HttpHeaders;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * Test artifact downloads from the controller.
 */
@Feature("Component Tests - Direct Device Integration API")
@Story("Artifact Download Resource")
@SpringBootTest(classes = { DownloadTestConfiguration.class })
public class DdiArtifactDownloadTest extends AbstractDDiApiIntegrationTest {

    private static volatile int downLoadProgress = 0;
    private static volatile long shippedBytes = 0;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);

    @BeforeEach
    public void setup() {
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    @Test
    @Description("Tests non allowed requests on the artifact ressource, e.g. invalid URI, wrong if-match, wrong command.")
    public void invalidRequestsOnArtifactResource() throws Exception {
        // create target
        final Target target = testdataFactory.createTarget();
        final List<Target> targets = Arrays.asList(target);

        // create ds
        final DistributionSet ds = testdataFactory.createDistributionSet("");
        assignDistributionSet(ds, targets);

        // create artifact
        final int artifactSize = 5 * 1024;
        final byte random[] = RandomUtils.nextBytes(artifactSize);
        final Artifact artifact = artifactManagement.create(new ArtifactUpload(new ByteArrayInputStream(random),
                ds.findFirstModuleByType(osType).get().getId(), "file1", false, artifactSize));

        // no artifact available
        mvc.perform(get("/controller/v1/{controllerId}/softwaremodules/{softwareModuleId}/artifacts/123455",
                target.getControllerId(), getOsModule(ds))).andExpect(status().isNotFound());
        mvc.perform(get("/controller/v1/{controllerId}/softwaremodules/{softwareModuleId}/artifacts/123455.MD5SUM",
                target.getControllerId(), getOsModule(ds))).andExpect(status().isNotFound());

        // SM does not exist
        mvc.perform(get("/controller/v1/{controllerId}/softwaremodules/1234567890/artifacts/{filename}",
                target.getControllerId(), artifact.getFilename())).andExpect(status().isNotFound());
        mvc.perform(get("/controller/v1/{controllerId}/softwaremodules/1234567890/artifacts/{filename}.MD5SUM",
                target.getControllerId(), artifact.getFilename())).andExpect(status().isNotFound());

        // test now consistent data to test allowed methods
        mvc.perform(get("/{tenant}/controller/v1/{controllerId}/softwaremodules/{smId}/artifacts/{filename}",
                tenantAware.getCurrentTenant(), target.getControllerId(), getOsModule(ds), artifact.getFilename())
                        .header(HttpHeaders.IF_MATCH, artifact.getSha1Hash()))
                .andExpect(status().isOk());
        mvc.perform(get("/{tenant}/controller/v1/{controllerId}/softwaremodules/{smId}/artifacts/{filename}.MD5SUM",
                tenantAware.getCurrentTenant(), target.getControllerId(), getOsModule(ds), artifact.getFilename()))
                .andExpect(status().isOk());

        // test failed If-match
        mvc.perform(get("/{tenant}/controller/v1/{controllerId}/softwaremodules/{smId}/artifacts/{filename}",
                tenantAware.getCurrentTenant(), target.getControllerId(), getOsModule(ds), artifact.getFilename())
                        .header(HttpHeaders.IF_MATCH, "fsjkhgjfdhg"))
                .andExpect(status().isPreconditionFailed());

        // test invalid range
        mvc.perform(get("/{tenant}/controller/v1/{controllerId}/softwaremodules/{smId}/artifacts/{filename}",
                tenantAware.getCurrentTenant(), target.getControllerId(), getOsModule(ds), artifact.getFilename())
                        .header("Range", "bytes=1-10,hdsfjksdh"))
                .andExpect(header().string("Content-Range", "bytes */" + 5 * 1024))
                .andExpect(status().isRequestedRangeNotSatisfiable());

        mvc.perform(get("/{tenant}/controller/v1/{controllerId}/softwaremodules/{smId}/artifacts/{filename}",
                tenantAware.getCurrentTenant(), target.getControllerId(), getOsModule(ds), artifact.getFilename())
                        .header("Range", "bytes=100-10"))
                .andExpect(header().string("Content-Range", "bytes */" + 5 * 1024))
                .andExpect(status().isRequestedRangeNotSatisfiable());

        // not allowed methods
        mvc.perform(put("/{tenant}/controller/v1/{controllerId}/softwaremodules/{smId}/artifacts/{filename}",
                tenantAware.getCurrentTenant(), target.getControllerId(), getOsModule(ds), artifact.getFilename()))
                .andExpect(status().isMethodNotAllowed());

        mvc.perform(delete("/{tenant}/controller/v1/{controllerId}/softwaremodules/{smId}/artifacts/{filename}",
                tenantAware.getCurrentTenant(), target.getControllerId(), getOsModule(ds), artifact.getFilename()))
                .andExpect(status().isMethodNotAllowed());

        mvc.perform(post("/{tenant}/controller/v1/{controllerId}/softwaremodules/{smId}/artifacts/{filename}",
                tenantAware.getCurrentTenant(), target.getControllerId(), getOsModule(ds), artifact.getFilename()))
                .andExpect(status().isMethodNotAllowed());

        mvc.perform(put("/{tenant}/controller/v1/{controllerId}/softwaremodules/{smId}/artifacts/{filename}.MD5SUM",
                tenantAware.getCurrentTenant(), target.getControllerId(), getOsModule(ds), artifact.getFilename()))
                .andExpect(status().isMethodNotAllowed());

        mvc.perform(delete("/{tenant}/controller/v1/{controllerId}/softwaremodules/{smId}/artifacts/{filename}.MD5SUM",
                tenantAware.getCurrentTenant(), target.getControllerId(), getOsModule(ds), artifact.getFilename()))
                .andExpect(status().isMethodNotAllowed());

        mvc.perform(post("/{tenant}/controller/v1/{controllerId}/softwaremodules/{smId}/artifacts/{filename}.MD5SUM",
                tenantAware.getCurrentTenant(), target.getControllerId(), getOsModule(ds), artifact.getFilename()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @WithUser(principal = "4712", authorities = "ROLE_CONTROLLER", allSpPermissions = true)
    @Description("Tests valid downloads through the artifact resource by identifying the artifact not by ID but file name.")
    public void downloadArtifactThroughFileName() throws Exception {
        downLoadProgress = 1;
        shippedBytes = 0;
        assertThat(softwareModuleManagement.findAll(PAGE)).hasSize(0);

        // create target
        final Target target = testdataFactory.createTarget();
        final List<Target> targets = Arrays.asList(target);

        // create ds
        final DistributionSet ds = testdataFactory.createDistributionSet("");

        // create artifact
        final int artifactSize = (int) quotaManagement.getMaxArtifactSize();
        final byte random[] = RandomUtils.nextBytes(artifactSize);
        final Artifact artifact = artifactManagement.create(new ArtifactUpload(new ByteArrayInputStream(random),
                ds.findFirstModuleByType(osType).get().getId(), "file1", false, artifactSize));

        // download fails as artifact is not yet assigned
        mvc.perform(get("/controller/v1/{controllerId}/softwaremodules/{softwareModuleId}/artifacts/{filename}",
                target.getControllerId(), getOsModule(ds), artifact.getFilename())).andExpect(status().isNotFound());

        // now assign and download successful
        assignDistributionSet(ds, targets);
        final MvcResult result = mvc.perform(get(
                "/{tenant}/controller/v1/{controllerId}/softwaremodules/{softwareModuleId}/artifacts/{filename}",
                tenantAware.getCurrentTenant(), target.getControllerId(), getOsModule(ds), artifact.getFilename()))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Last-Modified", dateFormat.format(new Date(artifact.getCreatedAt()))))
                .andExpect(header().string("Content-Disposition", "attachment;filename=" + artifact.getFilename()))
                .andReturn();

        assertTrue(
                Arrays.equals(result.getResponse().getContentAsByteArray(), random), "The same file that was uploaded is expected when downloaded");

        // download complete
        assertThat(downLoadProgress).isEqualTo(10);
        assertThat(shippedBytes).isEqualTo(artifactSize);
    }

    @Test
    @Description("Tests valid MD5SUm file downloads through the artifact resource by identifying the artifact by ID.")
    public void downloadMd5sumThroughControllerApi() throws Exception {
        // create target
        final Target target = testdataFactory.createTarget();

        // create ds
        final DistributionSet ds = testdataFactory.createDistributionSet("");

        assignDistributionSet(ds, target);

        // create artifact
        final int artifactSize = 5 * 1024;
        final byte random[] = RandomUtils.nextBytes(artifactSize);
        final Artifact artifact = artifactManagement.create(
                new ArtifactUpload(new ByteArrayInputStream(random), getOsModule(ds), "file1", false, artifactSize));

        // download
        final MvcResult result = mvc.perform(get(
                "/{tenant}/controller/v1/{controllerId}/softwaremodules/{softwareModuleId}/artifacts/{filename}.MD5SUM",
                tenantAware.getCurrentTenant(), target.getControllerId(), getOsModule(ds), artifact.getFilename()))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition",
                        "attachment;filename=" + artifact.getFilename() + ".MD5SUM"))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray())
                .isEqualTo((artifact.getMd5Hash() + "  " + artifact.getFilename()).getBytes(Charsets.US_ASCII));
    }

    @Test
    @WithUser(principal = TestdataFactory.DEFAULT_CONTROLLER_ID, authorities = "ROLE_CONTROLLER", allSpPermissions = true)
    @Description("Test various HTTP range requests for artifact download, e.g. chunk download or download resume.")
    public void rangeDownloadArtifact() throws Exception {
        // create target
        final Target target = testdataFactory.createTarget();
        final List<Target> targets = Arrays.asList(target);

        // create ds
        final DistributionSet ds = testdataFactory.createDistributionSet("");

        final int resultLength = (int) quotaManagement.getMaxArtifactSize();

        // create artifact
        final byte random[] = RandomUtils.nextBytes(resultLength);
        final Artifact artifact = artifactManagement.create(
                new ArtifactUpload(new ByteArrayInputStream(random), getOsModule(ds), "file1", false, resultLength));

        assertThat(random.length).isEqualTo(resultLength);

        // now assign and download successful
        assignDistributionSet(ds, targets);

        final int range = resultLength / 50;

        // full file download with standard range request
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (int i = 0; i < resultLength / range; i++) {
            final String rangeString = "" + i * range + "-" + ((i + 1) * range - 1);

            final MvcResult result = mvc.perform(get(
                    "/{tenant}/controller/v1/{controllerId}/softwaremodules/{softwareModuleId}/artifacts/{filename}",
                    tenantAware.getCurrentTenant(), target.getControllerId(), getOsModule(ds), "file1").header("Range",
                            "bytes=" + rangeString))
                    .andExpect(status().isPartialContent()).andExpect(header().string("ETag", artifact.getSha1Hash()))
                    .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                    .andExpect(header().string("Accept-Ranges", "bytes"))
                    .andExpect(header().string("Last-Modified", dateFormat.format(new Date(artifact.getCreatedAt()))))
                    .andExpect(header().longValue("Content-Length", range))
                    .andExpect(header().string("Content-Range", "bytes " + rangeString + "/" + resultLength))
                    .andExpect(header().string("Content-Disposition", "attachment;filename=file1")).andReturn();

            outputStream.write(result.getResponse().getContentAsByteArray());
        }

        assertThat(outputStream.toByteArray()).isEqualTo(random);

        // return last 1000 Bytes
        MvcResult result = mvc.perform(
                get("/{tenant}/controller/v1/{controllerId}/softwaremodules/{softwareModuleId}/artifacts/{filename}",
                        tenantAware.getCurrentTenant(), target.getControllerId(), getOsModule(ds), "file1")
                                .header("Range", "bytes=-1000"))
                .andExpect(status().isPartialContent()).andExpect(header().string("ETag", artifact.getSha1Hash()))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Last-Modified", dateFormat.format(new Date(artifact.getCreatedAt()))))
                .andExpect(header().longValue("Content-Length", 1000))
                .andExpect(header().string("Content-Range",
                        "bytes " + (resultLength - 1000) + "-" + (resultLength - 1) + "/" + resultLength))
                .andExpect(header().string("Content-Disposition", "attachment;filename=file1")).andReturn();

        assertThat(result.getResponse().getContentAsByteArray())
                .isEqualTo(Arrays.copyOfRange(random, resultLength - 1000, resultLength));

        // skip first 1000 Bytes and return the rest
        result = mvc.perform(
                get("/{tenant}/controller/v1/{controllerId}/softwaremodules/{softwareModuleId}/artifacts/{filename}",
                        tenantAware.getCurrentTenant(), target.getControllerId(), getOsModule(ds), "file1")
                                .header("Range", "bytes=1000-"))
                .andExpect(status().isPartialContent()).andExpect(header().string("ETag", artifact.getSha1Hash()))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Last-Modified", dateFormat.format(new Date(artifact.getCreatedAt()))))
                .andExpect(header().longValue("Content-Length", resultLength - 1000))
                .andExpect(header().string("Content-Range",
                        "bytes " + 1000 + "-" + (resultLength - 1) + "/" + resultLength))
                .andExpect(header().string("Content-Disposition", "attachment;filename=file1")).andReturn();

        assertThat(result.getResponse().getContentAsByteArray())
                .isEqualTo(Arrays.copyOfRange(random, 1000, resultLength));

        // Start download from file end fails
        mvc.perform(
                get("/{tenant}/controller/v1/{controllerId}/softwaremodules/{softwareModuleId}/artifacts/{filename}",
                        tenantAware.getCurrentTenant(), target.getControllerId(), getOsModule(ds), "file1")
                                .header("Range", "bytes=" + random.length + "-"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Last-Modified", dateFormat.format(new Date(artifact.getCreatedAt()))))
                .andExpect(header().string("Content-Range", "bytes */" + random.length))
                .andExpect(header().string("Content-Disposition", "attachment;filename=file1"));

        // multipart download - first 20 bytes in 2 parts
        result = mvc.perform(
                get("/{tenant}/controller/v1/{controllerId}/softwaremodules/{softwareModuleId}/artifacts/{filename}",
                        tenantAware.getCurrentTenant(), target.getControllerId(), getOsModule(ds), "file1")
                                .header("Range", "bytes=0-9,10-19"))
                .andExpect(status().isPartialContent()).andExpect(header().string("ETag", artifact.getSha1Hash()))
                .andExpect(content().contentType("multipart/byteranges; boundary=THIS_STRING_SEPARATES_MULTIPART"))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Last-Modified", dateFormat.format(new Date(artifact.getCreatedAt()))))
                .andExpect(header().string("Content-Disposition", "attachment;filename=file1")).andReturn();

        outputStream.reset();

        outputStream.write("\r\n--THIS_STRING_SEPARATES_MULTIPART\r\n".getBytes(StandardCharsets.ISO_8859_1));
        outputStream.write(("Content-Range: bytes 0-9/" + resultLength + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        outputStream.write(Arrays.copyOfRange(random, 0, 10));
        outputStream.write("\r\n--THIS_STRING_SEPARATES_MULTIPART\r\n".getBytes(StandardCharsets.ISO_8859_1));
        outputStream
                .write(("Content-Range: bytes 10-19/" + resultLength + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        outputStream.write(Arrays.copyOfRange(random, 10, 20));
        outputStream.write("\r\n--THIS_STRING_SEPARATES_MULTIPART--".getBytes(StandardCharsets.ISO_8859_1));

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(outputStream.toByteArray());

    }

    @Configuration
    public static class DownloadTestConfiguration {

        @Bean
        public Listener cancelEventHandlerStubBean() {
            return new Listener();
        }

    }

    private static class Listener {

        @EventListener(classes = DownloadProgressEvent.class)
        public static void listen(final DownloadProgressEvent event) {
            downLoadProgress++;
            shippedBytes += event.getShippedBytesSinceLast();

        }
    }

}
