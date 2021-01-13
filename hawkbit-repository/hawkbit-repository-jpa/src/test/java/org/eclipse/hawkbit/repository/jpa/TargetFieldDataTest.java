/**
 * Copyright (c) 2020 devolo AG and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa;

import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.Arrays;

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
import static org.eclipse.hawkbit.repository.model.TargetUpdateStatus.PENDING;
import static org.eclipse.hawkbit.repository.model.TargetUpdateStatus.UNKNOWN;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TargetFieldDataTest {

    final static String LOCALHOST = URI.create("http://127.0.0.1").toString();

    final static String EQUAL = "==";
    final static String NOT_EQUAL = "!=";
    final static String GREATER = "=gt=";
    final static String GREATER_EQUAL = "=ge=";
    final static String LESS = "=lt=";
    final static String LESS_EQUAL = "=le=";
    final static String IN = "=in=";
    final static String OUT = "=out=";

    private TargetFieldData fieldData;
    private TargetFieldData fieldData2;

    @Before
    public void setUp() throws InterruptedException {

        fieldData = new TargetFieldData();

        String createdTime = Long.toString(System.currentTimeMillis());

        Thread.sleep(123);
        String lastModifiedTime = Long.toString(System.currentTimeMillis());

        Thread.sleep(123);
        String lastRequestTime = Long.toString(System.currentTimeMillis());

        fieldData.add(ID, "012345");
        fieldData.add(NAME, "targetName1");
        fieldData.add(DESCRIPTION, "TargetDescription1");
        fieldData.add(CREATEDAT, createdTime);
        fieldData.add(LASTMODIFIEDAT, lastModifiedTime);
        fieldData.add(CONTROLLERID, "012345");
        fieldData.add(UPDATESTATUS, PENDING.name());
        fieldData.add(IPADDRESS, LOCALHOST);
        fieldData.add(ATTRIBUTE, "revision", "1.123");
        fieldData.add(ATTRIBUTE, "device_type", "dev_test");
        fieldData.add(ASSIGNEDDS, "name", "AssignedDs");
        fieldData.add(ASSIGNEDDS, "version", "3.321");
        fieldData.add(INSTALLEDDS, "name", "InstalledDs");
        fieldData.add(INSTALLEDDS, "version", "9.876");
        fieldData.add(TAG, "alpha");
        fieldData.add(TAG, "beta");
        fieldData.add(LASTCONTROLLERREQUESTAT, lastRequestTime);
        fieldData.add(METADATA, "metakey1", "metavalue1");
        fieldData.add(METADATA, "metakey2", "metavalue2");

        fieldData2 = new TargetFieldData();

        fieldData2.add(ID, "543210");
        fieldData2.add(NAME, "targetName2");
        fieldData2.add(DESCRIPTION, "TargetDescription2");
        fieldData2.add(CONTROLLERID, "543210");
        fieldData2.add(UPDATESTATUS, UNKNOWN.name());
        fieldData2.add(IPADDRESS, LOCALHOST);
    }

    @Test
    public void test_key_value_condition_on_targetFieldData(){

        assertTrue("Should match id == 012345", fieldData.request(ID.name(), EQUAL, "012345"));
        assertTrue("Should match id != 543210", fieldData.request(ID.name(), NOT_EQUAL, "543210"));
        assertFalse("Should not match id < ''", fieldData.request(ID.name(), LESS, ""));
        assertFalse("Should not match id <= ''", fieldData.request(ID.name(), LESS_EQUAL, ""));
        assertFalse("Should not match id > ''", fieldData.request(ID.name(), GREATER, ""));
        assertFalse("Should not match id >= ''", fieldData.request(ID.name(), GREATER_EQUAL, ""));
        assertTrue("Should match provided array contains ID", fieldData.request(ID.name(), IN, Arrays.asList("abcd", "012345", "95478")));
        assertTrue("Should match provided array doesn't contain ID", fieldData.request(ID.name(), OUT, Arrays.asList("abcd", "052abc", "95478")));
        assertTrue("Should match id > 012344", fieldData.request(ID.name(), GREATER, "012344"));
        assertTrue("Should match id < 012346", fieldData.request(ID.name(), LESS, "012346"));
        assertTrue("Should match id >= 012345", fieldData.request(ID.name(), GREATER_EQUAL, "012345"));
        assertTrue("Should match id <= 012345", fieldData.request(ID.name(), LESS_EQUAL, "012345"));
        assertTrue("Should match id >= 012343", fieldData.request(ID.name(), GREATER_EQUAL, "012343"));
        assertTrue("Should match id <= 012347", fieldData.request(ID.name(), LESS_EQUAL, "012347"));
        assertFalse("Should not match id == 012...", fieldData.request(ID.name(), EQUAL, "012..."));

        assertTrue("Should match tag=alpha", fieldData.request(TAG.name(), EQUAL, "alpha"));
        assertTrue("Should match tag=beta", fieldData.request(TAG.name(), EQUAL, "beta"));
        assertFalse("Should not match tag=''", fieldData.request(TAG.name(), EQUAL, ""));
        assertFalse("Should not match tag!=alpha", fieldData.request(TAG.name(), NOT_EQUAL, "alpha"));
        assertTrue("Should match tag=''", fieldData.request(TAG.name(), NOT_EQUAL, ""));

        assertTrue("Should match attribute == ''", fieldData2.request(ATTRIBUTE.name(), EQUAL, ""));
        assertFalse("Should not match attribute.attr_name == attr_value", fieldData2.request(ATTRIBUTE.name() + ".attr_name", EQUAL, "attr_value"));
        assertFalse("Should not match attribute.attr_name != attr_value", fieldData2.request(ATTRIBUTE.name() + ".attr_name", NOT_EQUAL, "attr_value"));
        assertFalse("Should not match provided array doesn't contain attr_value", fieldData2.request(ATTRIBUTE.name() + ".attr_name", OUT, Arrays.asList("attr_value", "abcdefg")));
        assertFalse("Should not match provided array contains attr_value", fieldData2.request(ATTRIBUTE.name() + ".attr_name", IN, Arrays.asList("attr_value", "abcdefg")));

        assertTrue("Should match tag == ''", fieldData2.request(TAG.name(), EQUAL, ""));
        assertTrue("Should match tag != alpha", fieldData2.request(TAG.name(), NOT_EQUAL, "alpha"));

        assertTrue("Should match tag =in= ''", fieldData2.request(TAG.name(), IN, ""));
        assertTrue("Should match tag =out= provided list", fieldData2.request(TAG.name(), OUT, Arrays.asList("alpha", "beta")));

        assertTrue("Should match id == *23*", fieldData.request(ID.name(), EQUAL, "*23*"));
        assertTrue("Should match id == 0*23*5", fieldData.request(ID.name(), EQUAL, "0*23*5"));
        assertTrue("Should match id == *5", fieldData.request(ID.name(), EQUAL, "*5"));
        assertTrue("Should match id == 0123*", fieldData.request(ID.name(), EQUAL, "0123*"));
        assertTrue("Should match id == *1*3*4*", fieldData.request(ID.name(), EQUAL, "*1*3*4*"));

        assertFalse("Should not match id != *23*", fieldData.request(ID.name(), NOT_EQUAL, "*23*"));
        assertFalse("Should not match id != 0*23*5", fieldData.request(ID.name(), NOT_EQUAL, "0*23*5"));
        assertFalse("Should not match id != *5", fieldData.request(ID.name(), NOT_EQUAL, "*5"));
        assertFalse("Should not match id != 0123*", fieldData.request(ID.name(), NOT_EQUAL, "0123*"));
        assertFalse("Should not match id != *1*3*4*", fieldData.request(ID.name(), NOT_EQUAL, "*1*3*4*"));

        assertTrue("Should match id != *32*", fieldData.request(ID.name(), NOT_EQUAL, "*32*"));
        assertFalse("Should not match id == 5*3", fieldData.request(ID.name(), EQUAL, "5*3"));

        assertTrue("Should match createdAt < current date", fieldData.request(CREATEDAT.name(), LESS, "${now_ts}"));
    }

}