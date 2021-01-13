/**
 * Copyright (c) 2020 devolo AG and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa;

import org.eclipse.hawkbit.repository.UpdateMode;
import org.eclipse.hawkbit.repository.exception.EntityNotFoundException;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.DistributionSetAssignmentResult;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetTag;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.eclipse.hawkbit.repository.TargetFields.ASSIGNEDDS;
import static org.eclipse.hawkbit.repository.TargetFields.ATTRIBUTE;
import static org.eclipse.hawkbit.repository.TargetFields.CONTROLLERID;
import static org.eclipse.hawkbit.repository.TargetFields.CREATEDAT;
import static org.eclipse.hawkbit.repository.TargetFields.DESCRIPTION;
import static org.eclipse.hawkbit.repository.TargetFields.ID;
import static org.eclipse.hawkbit.repository.TargetFields.INSTALLEDDS;
import static org.eclipse.hawkbit.repository.TargetFields.IPADDRESS;
import static org.eclipse.hawkbit.repository.TargetFields.LASTCONTROLLERREQUESTAT;
import static org.eclipse.hawkbit.repository.TargetFields.LASTMODIFIEDAT;
import static org.eclipse.hawkbit.repository.TargetFields.METADATA;
import static org.eclipse.hawkbit.repository.TargetFields.NAME;
import static org.eclipse.hawkbit.repository.TargetFields.TAG;
import static org.eclipse.hawkbit.repository.TargetFields.UPDATESTATUS;
import static org.eclipse.hawkbit.repository.model.TargetUpdateStatus.IN_SYNC;
import static org.eclipse.hawkbit.repository.model.TargetUpdateStatus.PENDING;
import static org.eclipse.hawkbit.repository.test.util.TestdataFactory.DEFAULT_VERSION;

import static org.junit.Assert.assertTrue;

public class TargetFieldExtractorTest extends AbstractJpaIntegrationTest {

    private String targetId;
    private String targetId2;

    @Autowired
    protected TargetFieldExtractor extractorService;

    @Before
    public void setUp() {

        Target target = targetManagement.create(entityFactory.target().create()
                .controllerId("targetId123")
                .name("targetName123")
                .description("targetDescription123"));

        targetId = target.getControllerId();

        final DistributionSet assignedDs = testdataFactory.createDistributionSet("AssignedDs");
        assignDistributionSet(assignedDs.getId(), target.getControllerId(), Action.ActionType.SOFT);

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("revision", "1.11");
        attributes.put("device_type", "dev_test");
        controllerManagement.updateControllerAttributes(targetId, attributes, UpdateMode.REPLACE);


        createTargetMetadata(targetId, Arrays.asList(
                entityFactory.generateDsMetadata("metakey_1", "metavalue_1"),
                entityFactory.generateDsMetadata("metakey_2", "metavalue_2")
        ));

        controllerManagement.findOrRegisterTargetIfItDoesNotExist(targetId, LOCALHOST);

        final TargetTag tag_alpha = targetTagManagement.create(entityFactory.tag().create().name("alpha"));
        final TargetTag tag_beta = targetTagManagement.create(entityFactory.tag().create().name("beta"));
        targetManagement.assignTag(Collections.singletonList(targetId), tag_alpha.getId());
        targetManagement.assignTag(Collections.singletonList(targetId), tag_beta.getId());

        Target target2 = targetManagement.create(entityFactory.target().create()
                .controllerId("targetId123_2")
                .name("targetName123_2")
                .description("targetDescription123_2"));

        targetId2 = target2.getControllerId();

        final DistributionSet installedDs = testdataFactory.createDistributionSet("InstalledDs");
        DistributionSetAssignmentResult result = assignDistributionSet(installedDs.getId(), targetId2, Action.ActionType.SOFT);

        addUpdateActionStatus(getFirstAssignedActionId(result), Action.Status.FINISHED);
    }


    private void addUpdateActionStatus(final Long actionId, final Action.Status actionStatus) {
        controllerManagement.addUpdateActionStatus(entityFactory.actionStatus().create(actionId).status(actionStatus));
    }

    @Test
    public void extractFieldsTest() {

        Target testTarget = targetManagement.getByControllerID(targetId).orElseThrow(EntityNotFoundException::new);
        TargetFieldData fieldData = extractorService.extractData(testTarget);

        Target testTarget2 = targetManagement.getByControllerID(targetId2).orElseThrow(EntityNotFoundException::new);
        TargetFieldData fieldData2 = extractorService.extractData(testTarget2);

        assertTrue("Should contain 'ID = targetId123'",
                fieldData.hasEntry(ID.name(), "targetId123"));
        assertTrue("Should contain 'CONTROLLERID = targetId123'",
                fieldData.hasEntry(CONTROLLERID.name(), "targetId123"));
        assertTrue("Should contain 'NAME = targetName123'",
                fieldData.hasEntry(NAME.name(), "targetName123"));
        assertTrue("Should contain 'DESCRIPTION = targetDescription123'",
                fieldData.hasEntry(DESCRIPTION.name(), "targetDescription123"));
        assertTrue("Should contain 'UPDATESTATUS = PENDING'",
                fieldData.hasEntry(UPDATESTATUS.name(), PENDING.name()));
        assertTrue("Should contain 'ASSIGNEDDS.name = AssignedDs'",
                fieldData.hasEntry(ASSIGNEDDS.name() + ".name", "AssignedDs"));
        assertTrue("Should contain 'ASSIGNEDDS.version = 1.0'",
                fieldData.hasEntry(ASSIGNEDDS.name() + ".version", DEFAULT_VERSION));
        assertTrue("Should contain 'INSTALLEDDS.name = InstalledDs'",
                fieldData2.hasEntry(INSTALLEDDS.name() + ".name", "InstalledDs"));
        assertTrue("Should contain 'INSTALLEDDS.version = 1.0'",
                fieldData2.hasEntry(INSTALLEDDS.name() + ".version", DEFAULT_VERSION));
        assertTrue("Should contain 'UPDATESTATUS = IN_SYNC'",
                fieldData2.hasEntry(UPDATESTATUS.name(), IN_SYNC.name()));
        assertTrue("Should contain 'ATTRIBUTE.revision = 1.11'",
                fieldData.hasEntry(ATTRIBUTE.name() + ".revision", "1.11"));
        assertTrue("Should contain 'ATTRIBUTE.device_type = dev_test'",
                fieldData.hasEntry(ATTRIBUTE.name() + ".device_type", "dev_test"));
        assertTrue("Should contain METADATA.metakey_1 = metavalue_1",
                fieldData.hasEntry(METADATA.name() + ".metakey_1", "metavalue_1"));
        assertTrue("Should contain METADATA.metakey_2 = metavalue_2",
                fieldData.hasEntry(METADATA.name() + ".metakey_2", "metavalue_2"));
        assertTrue("Should contain IPADDRESS = http://127.0.0.1",
                fieldData.hasEntry(IPADDRESS.name() , LOCALHOST.toString()));
        assertTrue("Should contain TAG = alpha",
                fieldData.hasEntry(TAG.name(), "alpha"));
        assertTrue("Should contain TAG = beta",
                fieldData.hasEntry(TAG.name(), "beta"));
        assertTrue("Should contain CREATEDAT = [Long value]",
                fieldData.hasEntry(CREATEDAT.name(), Long.toString(testTarget.getCreatedAt())));
        assertTrue("Should contain LASTMODIFIEDAT = [Long value]",
                fieldData.hasEntry(LASTMODIFIEDAT.name(),  Long.toString(testTarget.getLastModifiedAt())));
        assertTrue("Should contain LASTCONTROLLERREQUESTAT = [Long value]",
                fieldData.hasEntry(LASTCONTROLLERREQUESTAT.name(),  Long.toString(testTarget.getLastTargetQuery())));
    }
}