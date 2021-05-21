/**
 * Copyright (c) 2020 devolo AG and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.filtermanagement;

import com.google.common.base.Strings;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTargetFilterQuery;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class to export targets of the {@link ProxyTargetFilterQuery} as CSV.
 */
public class TargetDataCsvExporter {

    private static final List<String> HEADER_KEYS = Arrays.asList("Name", "Description", "Status", "Created By",
            "Created Date", "Modified By", "Modified Date");

    private final String separator;
    private final SimpleDateFormat dateFormat;
    private final TargetManagement targetManagement;

    private static final Logger LOG = LoggerFactory.getLogger(TargetFilterAddUpdateLayout.class);

    public TargetDataCsvExporter(final TargetManagement targetManagement, final String separator,
            final String dateFormatPattern) {

        this.targetManagement = targetManagement;
        this.separator = separator;
        this.dateFormat = new SimpleDateFormat(dateFormatPattern);
    }

    public void writeTargetsByFilterId(final Long targetFilterId, final OutputStream out) {
        try {
            addHeader(out);
            convertTargets(targetFilterId, out);
            out.flush();
            out.close();
        } catch (final IOException e) {
            LOG.error("Exception during csv conversion: ", e);
        }
    }

    private void addHeader(final OutputStream out) throws IOException {
        out.write(HEADER_KEYS.stream().collect(Collectors.joining(separator)).concat("\n").getBytes());
    }

    private void convertTargets(final Long targetFilterId, final OutputStream out) throws IOException {
        Pageable pageable = PageRequest.of(0, SPUIDefinitions.PAGE_SIZE);
        Slice<Target> targets;

        do {
            targets = targetManagement.findByTargetFilterQuery(pageable, targetFilterId);
            out.write(targets.stream().map(this::convertTarget).collect(Collectors.joining("\n")).getBytes());
        } while ((pageable = targets.nextPageable()) != Pageable.unpaged());
    }

    private String convertTarget(final Target target) {
        return Arrays
                .asList(target.getControllerId(), target.getDescription(), target.getUpdateStatus().name(),
                        target.getCreatedBy(), toReadableDate(target.getCreatedAt()), target.getLastModifiedBy(),
                        toReadableDate(target.getLastModifiedAt()))
                .stream().map(Strings::nullToEmpty).map(TargetDataCsvExporter::quote)
                .collect(Collectors.joining(separator));
    }

    private String toReadableDate(final long millis) {
        return dateFormat.format(new Date(millis));
    }

    private static String quote(final String input) {
        return String.format("\"%s\"", input);
    }
}
