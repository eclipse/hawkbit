/**
 * Copyright (c) 2020 devolo AG and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa.rsql;

import org.eclipse.hawkbit.repository.jpa.TargetFieldData;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.GregorianCalendar;

import static java.util.Calendar.DECEMBER;
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
import static org.eclipse.hawkbit.repository.jpa.rsql.RsqlMatcher.matches;
import static org.eclipse.hawkbit.repository.model.TargetUpdateStatus.PENDING;
import static org.eclipse.hawkbit.repository.model.TargetUpdateStatus.UNKNOWN;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RsqlMatcherTest {
    final static String LOCALHOST = URI.create("http://127.0.0.1").toString();

    final static String idEqualTrue = "id == 012345";
    final static String idNotEqualTrue = "id != 54321";
    final static String idNotEqualFalse = "id != 012345";
    final static String idInTrue = "id =in= (abcd, 9999, 012345, 7456)";
    final static String idInFalse = "id =in= (abcd, 9999, 7456)";
    final static String idOutTrue = "id =out= (abcd, 9999, 8546)";
    final static String idOutFalse = "id =out= (abcd, 9999, 8546, 012345)";

    final static String idWildCardEqualTrue1 = "id == *2345";
    final static String idWildCardEqualTrue2 = "id == 0*";
    final static String idWildCardEqualTrue3 = "id == *123*";
    final static String idWildCardEqualTrue4 = "id == *1*4*";
    final static String idWildCardEqualTrue5 = "id == 01*45";
    final static String idWildCardEqualTrue6 = "id == *012345*";
    final static String idWildCardEqualTrue7= "id == *0*1*2*3*4*5*";
    final static String idWildCardEqualFalse2 = "id == 0124*5";

    final static String controllerIdEqualTrue = "controllerid == 012345";
    final static String controllerIdInTrue = "controllerid =in= (abcd, 9999, 012345, 7456)";
    final static String controllerIdInFalse = "controllerid =in= (abcd, 9999, 7456)";
    final static String controllerIdOutTrue = "controllerid =out= (abcd, 9999, 8546)";
    final static String controllerIdOutFalse = "controllerid =out= (abcd, 9999, 8546, 012345)";

    final static String nameEqualTrue1 = "name == targetName1";
    final static String nameEqualTrue2 = "name == 'targetName1'";
    final static String nameInTrue = "name =in= (abcd, targetName1, 012345, 7456)";
    final static String nameInFalse = "name =in= (abcd, 9999, 7456, asdfgh)";
    final static String nameOutTrue = "name =out= (abcd, 9999, 8546)";
    final static String nameOutFalse = "name =out= (abcd, 9999, 8546, targetName1, 012345)";

    final static String descEqualTrue1 = "description == TargetDescription1";
    final static String descEqualTrue2 = "description == \"TargetDescription1\"";
    final static String descInTrue = "description =in= (abcd, TargetDescription1, 012345, 7456)";
    final static String descInFalse = "description =in= (abcd, 9999, 7456, asdfgh)";
    final static String descOutTrue = "description =out= (abcd, 9999, 8546, ghjktrz)";
    final static String descOutFalse = "description =out= (abcd, 9999, 8546, TargetDescription1, 012345)";

    final static String ipAddressEqualTrue1 = "ipaddress == " + LOCALHOST;
    final static String ipAddressEqualTrue2 = "ipaddress == '" + LOCALHOST + "'";
    final static String ipAddressEqualFalse = "ipaddress == \"http:\\\\abcde.123485\"";
    final static String ipAddressNotEqualTrue = "ipaddress != http:\\\\192.168.0.1";
    final static String ipAddressNotEqualFalse = "ipaddress != " + LOCALHOST;
    final static String ipAddressOutTrue = "ipaddress =OUT= (abcd, 9999, 8546, ghjktrz, http:\\\\devolo.com)";
    final static String ipAddressOutFalse = "ipaddress=oUt= (abcd, 9999, 8546,"+ LOCALHOST + ",InstalledDs, 012345)";
    final static String ipAddressInTrue = "ipaddress=iN=(abcd, " + LOCALHOST + ", 8546, ghjktrz)";
    final static String ipAddressInFalse = "ipaddress =In=(abcd, 9874, 8546, ghjktrz)";

    final static String updateStatusEqualTrue1 = "updatestatus == pending";
    final static String updateStatusEqualTrue2 = "updatestatus == pENdInG";
    final static String updateStatusEqualFalse = "updatestatus == in_sync";
    final static String updateStatusInTrue = "updatestatus =IN= (pending, error, abcdefgh123)";
    final static String updateStatusInFalse = "updatestatus =iN= (in_sync, error, abcdefgh123)";
    final static String updateStatusOutTrue = "updatestatus =OUT= (finished, error, abcdefgh123)";
    final static String updateStatusOutFalse = "updatestatus =oUt= (in_sync, pending, error, abcdefgh123)";

    final static String attributeRevisionEqualTrue1 = "attribute.revision == 1.123";
    final static String attributeRevisionEqualTrue2 = "attribute.revision == \"1.123\"";
    final static String attributeRevisionEqualFalse = "attribute.revision == \"abcde\"";
    final static String attributeRevisionNotEqualTrue = "attribute.revision != 6.6.6";
    final static String attributeRevisionNotEqualFalse = "attribute.revision != 1.123";
    final static String attributeRevisionOutTrue = "attribute.revision =out= (abcd, 9999, 8546, ghjktrz)";
    final static String attributeRevisionOutFalse = "attribute.revision =out= (abcd, 9999, 8546, 1.123, 012345)";
    final static String attributeRevisionInTrue = "attribute.revision =in= (abcd, 1.123, 8546, ghjktrz)";
    final static String attributeRevisionInFalse = "attribute.revision =in= (abcd, 9874, 8546, ghjktrz)";

    final static String attributeDevTypeEqualTrue1 = "attribute.device_type == dev_test";
    final static String attributeDevTypeEqualTrue2 = "attribute.device_type == \"dev_test\"";
    final static String attributeDevTypeEqualFalse1 = "attribute.device_type == \"abcde\"";
    final static String attributeDevTypeEqualFalse2 = "attribute.keynotexistant == \"abcde\"";
    final static String attributeDevTypeNotEqualTrue = "attribute.device_type != qwertz";
    final static String attributeDevTypeNotEqualFalse1 = "attribute.device_type != dev_test";
    final static String attributeDevTypeNotEqualFalse2 = "attribute.keynotexistant != dev_test";
    final static String attributeDevTypeOutTrue = "attribute.device_type =OUT= (abcd, 9999, 8546, ghjktrz)";
    final static String attributeDevTypeOutFalse = "attribute.device_type =oUt= (abcd, 9999, 8546, dev_test, 012345)";
    final static String attributeDevTypeInTrue = "attribute.device_type =iN= (abcd, dev_test, 8546, ghjktrz)";
    final static String attributeDevTypeInFalse = "attribute.device_type =In= (abcd, 9874, 8546, ghjktrz)";

    final static String assignedDsNameEqualTrue1 = "assignedds.name == AssignedDs";
    final static String assignedDsNameEqualTrue2 = "assignedds.name == 'AssignedDs'";
    final static String assignedDsNameEqualFalse = "assignedds.name == \"abcde\"";
    final static String assignedDsNameNotEqualTrue = "assignedds.name != asdfg";
    final static String assignedDsNameNotEqualFalse = "assignedds.name != AssignedDs";
    final static String assignedDsNameOutTrue = "assignedds.name =OUT= (abcd, 9999, 8546, ghjktrz, InstalledDs)";
    final static String assignedDsNameOutFalse = "assignedds.name =oUt= (abcd, 9999, 8546, AssignedDs, 012345)";
    final static String assignedDsNameInTrue = "assignedds.name =iN= (abcd, AssignedDs, 8546, ghjktrz)";
    final static String assignedDsNameInFalse = "assignedds.name =In= (abcd, 9874, 8546, ghjktrz)";

    final static String installedDsNameEqualTrue1 = "installedds.name == InstalledDs";
    final static String installedDsNameEqualTrue2 = "installedds.name == 'InstalledDs'";
    final static String installedDsNameEqualFalse = "installedds.name == \"abcde\"";
    final static String installedDsNameNotEqualTrue = "installedds.name != asdfg";
    final static String installedDsNameNotEqualFalse = "installedds.name != InstalledDs";
    final static String installedDsNameOutTrue = "installedds.name =OUT= (abcd, 9999, 8546, ghjktrz, AssignedDs)";
    final static String installedDsNameOutFalse = "installedds.name =oUt= (abcd, 9999, 8546, InstalledDs, 012345)";
    final static String installedDsNameInTrue = "installedds.name =iN= (abcd, InstalledDs, 8546, ghjktrz)";
    final static String installedDsNameInFalse = "installedds.name =In= (abcd, 9874, 8546, ghjktrz)";

    final static String tagEqualTrue1 = "tag == alpha";
    final static String tagEqualTrue2 = "tag == beta";
    final static String tagNotEqualTrue = "tag != nightly";
    final static String tagNotEqualFalse = "tag != alpha";
    final static String tagInTrue1 = "tag =in= (asdh, 9999, 012345, 7456, alpha)";
    final static String tagInTrue2 = "tag =in= (aasdfdg, beta, 012345, 7456)";
    final static String tagInFalse = "tag =in= (abcd, 9999, asdfg, 7456)";
    final static String tagOutTrue = "tag =out= (abcd, 9999, 8546, nightly, random123)";
    final static String tagOutFalse1 = "tag =out= (abcd, alpha, 8546, 012345)";
    final static String tagOutFalse2 = "tag =out= (abcd, rtzu, beta, 012345)";

    final static String metadata1EqualTrue = "metadata.metakey1 == metavalue1";
    final static String metadataEqualFalse = "metadata.wrongkey == metavalue1";
    final static String metadataNotEqualFalse = "metadata.wrongkey != metavalue1";
    final static String metadata1EqualFalse = "metadata.metakey1 == value";
    final static String metadata1NotEqualTrue = "metadata.metakey1 != mtvl";
    final static String metadata1NotEqualFalse = "metadata.metakey1 != metavalue1";
    final static String metadata1OutTrue = "metadata.metakey1 =out= (abcd, 9999, 8546, ghjktrz)";
    final static String metadata1OutFalse = "metadata.metakey1 =out= (abcd, 9999, metavalue1, 1.123, 012345)";
    final static String metadata1InTrue = "metadata.metakey1 =in= (abcd, metavalue1, 8546, ghjktrz)";
    final static String metadata1InFalse = "metadata.metakey1 =in= (abcd, 9874, 8546, ghjktrz)";

    final static String metadata2EqualTrue = "metadata.metakey2 == metavalue2";
    final static String metadata2EqualFalse = "metadata.metakey2 == value";
    final static String metadata2NotEqualTrue = "metadata.metakey2 != mtvl";
    final static String metadata2NotEqualFalse = "metadata.metakey2 != metavalue2";
    final static String metadata2OutTrue = "metadata.metakey2 =out= (abcd, 9999, 8546, ghjktrz)";
    final static String metadata2OutFalse = "metadata.metakey2 =out= (abcd, 9999, metavalue2, 1.123, 012345)";
    final static String metadata2InTrue = "metadata.metakey2 =in= (abcd, metavalue2, 8546, ghjktrz)";
    final static String metadata2InFalse = "metadata.metakey2 =in= (abcd, 9874, 8546, ghjktrz)";

    final static String createdAtLessTrue =
            "createdat =lt= " + new GregorianCalendar(2020, DECEMBER, 1).getTimeInMillis();
    final static String createdAtLessFalse =
            "createdat =lt= " + new GregorianCalendar(2010, DECEMBER, 1).getTimeInMillis();
    final static String createdAtGreaterTrue =
            "createdat =gt= " + new GregorianCalendar(2014, DECEMBER, 1).getTimeInMillis();
    final static String createdAtGreaterFalse =
            "createdat =gt= " + new GregorianCalendar(2020, DECEMBER, 1).getTimeInMillis();
    final static String createdAtLessEqualTrue =
            "createdat =le= " + new GregorianCalendar(2020, DECEMBER, 1).getTimeInMillis();
    final static String createdAtLessEqualFalse =
            "createdat =le= " + new GregorianCalendar(2014, DECEMBER, 1).getTimeInMillis();
    final static String createdAtGreaterEqualTrue =
            "createdat =ge= " + new GregorianCalendar(2010, DECEMBER, 1).getTimeInMillis();
    final static String createdAtGreaterEqualFalse =
            "createdat =ge= " + new GregorianCalendar(2016, DECEMBER, 1).getTimeInMillis();
    final static String createdAtGreaterEqualEqualTrue =
            "createdat =ge= " + new GregorianCalendar(2015, DECEMBER, 1).getTimeInMillis();
    final static String createdAtLessEqualEqualTrue =
            "createdat =le= " + new GregorianCalendar(2015, DECEMBER, 1).getTimeInMillis();

    final static String lastModifiedAtLessTrue =
            "lastmodifiedat =lt= " + new GregorianCalendar(2020, DECEMBER, 1).getTimeInMillis();
    final static String lastModifiedAtLessFalse =
            "lastmodifiedat =lt= " + new GregorianCalendar(2010, DECEMBER, 1).getTimeInMillis();
    final static String lastModifiedAtGreaterTrue =
            "lastmodifiedat =gt= " + new GregorianCalendar(2014, DECEMBER, 1).getTimeInMillis();
    final static String lastModifiedAtGreaterFalse =
            "lastmodifiedat =gt= " + new GregorianCalendar(2020, DECEMBER, 1).getTimeInMillis();
    final static String lastModifiedAtLessEqualTrue =
            "lastmodifiedat =le= " + new GregorianCalendar(2020, DECEMBER, 1).getTimeInMillis();
    final static String lastModifiedAtLessEqualFalse =
            "lastmodifiedat =le= " + new GregorianCalendar(2014, DECEMBER, 1).getTimeInMillis();
    final static String lastModifiedAtGreaterEqualTrue =
            "lastmodifiedat =ge= " + new GregorianCalendar(2010, DECEMBER, 1).getTimeInMillis();
    final static String lastModifiedAtGreaterEqualFalse =
            "lastmodifiedat =ge= " + new GregorianCalendar(2017, DECEMBER, 1).getTimeInMillis();
    final static String lastModifiedAtGreaterEqualEqualTrue =
            "lastmodifiedat =ge= " + new GregorianCalendar(2015, DECEMBER, 1).getTimeInMillis();
    final static String lastModifiedAtLessEqualEqualTrue =
            "lastmodifiedat =le= " + new GregorianCalendar(2016, DECEMBER, 1).getTimeInMillis();

    final static String lastRequestLessTrue =
            "lastcontrollerrequestat =lt= " + new GregorianCalendar(2020, DECEMBER, 1).getTimeInMillis();
    final static String lastRequestLessFalse =
            "lastcontrollerrequestat =lt= " + new GregorianCalendar(2010, DECEMBER, 1).getTimeInMillis();
    final static String lastRequestGreaterTrue =
            "lastcontrollerrequestat =gt= " + new GregorianCalendar(2014, DECEMBER, 1).getTimeInMillis();
    final static String lastRequestGreaterFalse =
            "lastcontrollerrequestat =gt= " + new GregorianCalendar(2020, DECEMBER, 1).getTimeInMillis();
    final static String lastRequestLessEqualTrue =
            "lastcontrollerrequestat =le= " + new GregorianCalendar(2020, DECEMBER, 1).getTimeInMillis();
    final static String lastRequestLessEqualFalse =
            "lastcontrollerrequestat =le= " + new GregorianCalendar(2014, DECEMBER, 1).getTimeInMillis();
    final static String lastRequestGreaterEqualTrue =
            "lastcontrollerrequestat =ge= " + new GregorianCalendar(2010, DECEMBER, 1).getTimeInMillis();
    final static String lastRequestGreaterEqualFalse =
            "lastcontrollerrequestat =ge= " + new GregorianCalendar(2018, DECEMBER, 1).getTimeInMillis();
    final static String lastRequestGreaterEqualEqualTrue =
            "lastcontrollerrequestat =ge= " + new GregorianCalendar(2017, DECEMBER, 1).getTimeInMillis();
    final static String lastRequestLessEqualEqualTrue =
            "lastcontrollerrequestat =le= " + new GregorianCalendar(2017, DECEMBER, 1).getTimeInMillis();

    final static String idAndTagTrue = idEqualTrue + " and " + tagInTrue1;
    final static String idAndTagTrueAndAttributeTrue = idEqualTrue + " and " + tagInTrue1 + " and " + attributeDevTypeInTrue;
    final static String idAndTagFalse = idEqualTrue + " and " + tagInFalse;
    final static String attributeOrMetadataTrue = attributeRevisionInTrue + " or " + metadata1EqualFalse;
    final static String idOrCreatedOrUpdateFalse = idInFalse + " or " + createdAtGreaterFalse + " or " + updateStatusEqualFalse;
    final static String idAndTagOrLastRequestTrue = "(" + idInFalse + " and " + tagInTrue1 + ")" + " or " + lastRequestGreaterTrue;

    final static String attributeKeyNotExistValueEmptyFalse = "attribute.keynotexistant != \"\"";

    private TargetFieldData fieldData;
    private TargetFieldData fieldData2;

    @Before
    public void setUp() {

        fieldData = new TargetFieldData();

        String createdTime = Long.toString(new GregorianCalendar(2015, DECEMBER, 1).getTimeInMillis());
        String lastModifiedTime = Long.toString(new GregorianCalendar(2016, DECEMBER, 1).getTimeInMillis());
        String lastRequestTime = Long.toString(new GregorianCalendar(2017, DECEMBER, 1).getTimeInMillis());

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

        fieldData.add(ID, "123456");
        fieldData.add(NAME, "targetName2");
        fieldData.add(DESCRIPTION, "TargetDescription2");
        fieldData.add(CREATEDAT, createdTime);
        fieldData.add(LASTMODIFIEDAT, lastModifiedTime);
        fieldData.add(CONTROLLERID, "123456");
        fieldData.add(UPDATESTATUS, UNKNOWN.name());
        fieldData.add(IPADDRESS, LOCALHOST);
        fieldData.add(ATTRIBUTE, "device_type", "dev_test");
        fieldData.add(LASTCONTROLLERREQUESTAT, lastRequestTime);
    }

    @Test
    public void match_combined_properties(){
        assertTrue("Should match ID and tag", matches(idAndTagTrue, fieldData));
        assertTrue("Should match ID and tag and attribute", matches(idAndTagTrueAndAttributeTrue, fieldData));
        assertFalse("Should not match ID and tag", matches(idAndTagFalse, fieldData));
        assertTrue("Should match attribute or metadata", matches(attributeOrMetadataTrue, fieldData));
        assertFalse("Should not match ID or createdat or updatestatus", matches(idOrCreatedOrUpdateFalse, fieldData));
        assertTrue("Should match ID and tag or lastrequest", matches(idAndTagOrLastRequestTrue, fieldData));
    }

    @Test
    public void match_single_properties() {

        assertTrue("Should match ID with wildcard as equal", matches(idWildCardEqualTrue1, fieldData));
        assertTrue("Should match ID with wildcard as equal", matches(idWildCardEqualTrue2, fieldData));
        assertTrue("Should match ID with wildcard as equal", matches(idWildCardEqualTrue3, fieldData));
        assertTrue("Should match ID with wildcard as equal", matches(idWildCardEqualTrue4, fieldData));
        assertTrue("Should match ID with wildcard as equal", matches(idWildCardEqualTrue5, fieldData));
        assertTrue("Should match ID with wildcard as equal", matches(idWildCardEqualTrue6, fieldData));
        assertTrue("Should match ID with wildcard as equal", matches(idWildCardEqualTrue7, fieldData));
        assertFalse("Should not match ID with wildcard as equal", matches(idWildCardEqualFalse2, fieldData));

        assertTrue("Should match IP address as equal", matches(ipAddressEqualTrue1, fieldData));
        assertTrue("Should match IP address as equal", matches(ipAddressEqualTrue2, fieldData));
        assertFalse("Should not match IP address as equal", matches(ipAddressEqualFalse, fieldData));
        assertTrue("Should match IP address as not equal", matches(ipAddressNotEqualTrue, fieldData));
        assertFalse("Should not match IP address as not equal", matches(ipAddressNotEqualFalse, fieldData));
        assertTrue("Should match IP address as not containing", matches(ipAddressOutTrue, fieldData));
        assertFalse("Should not match IP address as not containing", matches(ipAddressOutFalse, fieldData));
        assertTrue("Should match IP address as containing", matches(ipAddressInTrue, fieldData));
        assertFalse("Should not match IP address as containing", matches(ipAddressInFalse, fieldData));

        assertTrue("Should match lastrequestdate less than", matches(lastRequestLessTrue, fieldData));
        assertFalse("Should not match lastrequestdate less than", matches(lastRequestLessFalse, fieldData));
        assertTrue("Should match lastrequestdate greater than", matches(lastRequestGreaterTrue, fieldData));
        assertFalse("Should not match lastrequestdate greater than", matches(lastRequestGreaterFalse, fieldData));
        assertTrue("Should match lastrequestdate less or equal", matches(lastRequestLessEqualTrue, fieldData));
        assertFalse("Should not match lastrequestdate less or equal", matches(lastRequestLessEqualFalse, fieldData));
        assertTrue("Should match lastrequestdate greater or equal", matches(lastRequestGreaterEqualTrue, fieldData));
        assertFalse("Should not match lastrequestdate greater or equal", matches(lastRequestGreaterEqualFalse, fieldData));
        assertTrue("Should match lastrequestdate greater or equal", matches(lastRequestGreaterEqualEqualTrue, fieldData));
        assertTrue("Should match lastrequestdate less or equal", matches(lastRequestLessEqualEqualTrue, fieldData));

        assertTrue("Should match metadata equal", matches(metadata2EqualTrue, fieldData));
        assertFalse("Should not match metadata equal", matches(metadata2EqualFalse, fieldData));
        assertTrue("Should match metadata not equal", matches(metadata2NotEqualTrue, fieldData));
        assertFalse("Should not match metadata not equal", matches(metadata2NotEqualFalse, fieldData));
        assertTrue("Should match metadata not containing", matches(metadata2OutTrue, fieldData));
        assertFalse("Should not match metadata not containing", matches(metadata2OutFalse, fieldData));
        assertTrue("Should match metadata containing", matches(metadata2InTrue, fieldData));
        assertFalse("Should not match metadata containing", matches(metadata2InFalse, fieldData));

        assertTrue("Should match metadata equal", matches(metadata1EqualTrue, fieldData));
        assertFalse("Should not match metadata equal", matches(metadataEqualFalse, fieldData));
        assertFalse("Should not match metadata not equal", matches(metadataNotEqualFalse, fieldData));
        assertFalse("Should not match metadata equal", matches(metadata1EqualFalse, fieldData));
        assertTrue("Should match metadata not equal", matches(metadata1NotEqualTrue, fieldData));
        assertFalse("Should not match metadata not equal", matches(metadata1NotEqualFalse, fieldData));
        assertTrue("Should match metadata not containing", matches(metadata1OutTrue, fieldData));
        assertFalse("Should not match metadata not containing", matches(metadata1OutFalse, fieldData));
        assertTrue("Should match metadata containing", matches(metadata1InTrue, fieldData));
        assertFalse("Should not match metadata containing", matches(metadata1InFalse, fieldData));

        assertTrue("Should match tag equal", matches(tagEqualTrue1, fieldData));
        assertTrue("Should match tag equal", matches(tagEqualTrue2, fieldData));
        assertTrue("Should match tag not equal", matches(tagNotEqualTrue, fieldData));
        assertFalse("Should not match tag not equal",matches(tagNotEqualFalse, fieldData));
        assertTrue("Should match tag containing", matches(tagInTrue1, fieldData));
        assertTrue("Should match tag containing", matches(tagInTrue2, fieldData));
        assertFalse("Should not match tag containing", matches(tagInFalse, fieldData));
        assertTrue("Should match tag not containing", matches(tagOutTrue, fieldData));
        assertFalse("Should not match tag not containing", matches(tagOutFalse1, fieldData));
        assertFalse("Should not match tag not containing", matches(tagOutFalse2, fieldData));

        assertTrue("Should match AssignedDS name equal", matches(assignedDsNameEqualTrue1, fieldData));
        assertTrue("Should match AssignedDS name equal", matches(assignedDsNameEqualTrue2, fieldData));
        assertFalse("Should not match AssignedDS name equal", matches(assignedDsNameEqualFalse, fieldData));
        assertTrue("Should match AssignedDS name not equal", matches(assignedDsNameNotEqualTrue, fieldData));
        assertFalse("Should not match AssignedDS name not equal", matches(assignedDsNameNotEqualFalse, fieldData));
        assertTrue("Should match AssignedDS name not containing", matches(assignedDsNameOutTrue, fieldData));
        assertFalse("Should not match AssignedDS name not containing", matches(assignedDsNameOutFalse, fieldData));
        assertTrue("Should match AssignedDS name containing", matches(assignedDsNameInTrue, fieldData));
        assertFalse("Should not match AssignedDS name containing", matches(assignedDsNameInFalse, fieldData));

        assertTrue("Should match InstalledDS name equal", matches(installedDsNameEqualTrue1, fieldData));
        assertTrue("Should match InstalledDS name equal", matches(installedDsNameEqualTrue2, fieldData));
        assertFalse("Should not match InstalledDS name equal", matches(installedDsNameEqualFalse, fieldData));
        assertTrue("Should match InstalledDS name not equal", matches(installedDsNameNotEqualTrue, fieldData));
        assertFalse("Should not match InstalledDS name not equal", matches(installedDsNameNotEqualFalse, fieldData));
        assertTrue("Should match InstalledDS name not containing", matches(installedDsNameOutTrue, fieldData));
        assertFalse("Should not match InstalledDS name not containing", matches(installedDsNameOutFalse, fieldData));
        assertTrue("Should match InstalledDS name containing", matches(installedDsNameInTrue, fieldData));
        assertFalse("Should not match InstalledDS name containing", matches(installedDsNameInFalse, fieldData));

        assertTrue("Should match attribute revision equal", matches(attributeRevisionEqualTrue1, fieldData));
        assertTrue("Should match attribute revision equal", matches(attributeRevisionEqualTrue2, fieldData));
        assertFalse("Should not match attribute revision equal", matches(attributeRevisionEqualFalse, fieldData));
        assertTrue("Should match attribute revision not equal", matches(attributeRevisionNotEqualTrue, fieldData));
        assertFalse("Should not match attribute revision not equal", matches(attributeRevisionNotEqualFalse, fieldData));
        assertTrue("Should match attribute revision not containing", matches(attributeRevisionOutTrue, fieldData));
        assertTrue("Should match attribute revision containing", matches(attributeRevisionInTrue, fieldData));
        assertFalse("Should not match attribute revision not containing", matches(attributeRevisionOutFalse, fieldData));
        assertFalse("Should not match attribute revision containing", matches(attributeRevisionInFalse, fieldData));

        assertTrue("Should match attribute device_type equal", matches(attributeDevTypeEqualTrue1, fieldData));
        assertTrue("Should match attribute device_type equal", matches(attributeDevTypeEqualTrue2, fieldData));
        assertFalse("Should not match attribute device_type equal", matches(attributeDevTypeEqualFalse1, fieldData));
        assertFalse("Should not match attribute device_type equal", matches(attributeDevTypeEqualFalse2, fieldData));
        assertTrue("Should match attribute device_type not equal", matches(attributeDevTypeNotEqualTrue, fieldData));
        assertFalse("Should not match attribute device_type not equal", matches(attributeDevTypeNotEqualFalse1, fieldData));
        assertFalse("Should not match attribute device_type not equal", matches(attributeDevTypeNotEqualFalse2, fieldData));
        assertTrue("Should match attribute device_type not containing", matches(attributeDevTypeOutTrue, fieldData));
        assertTrue("Should match attribute device_type containing", matches(attributeDevTypeInTrue, fieldData));
        assertFalse("Should not match attribute device_type not containing", matches(attributeDevTypeOutFalse, fieldData));
        assertFalse("Should not match attribute device_type containing", matches(attributeDevTypeInFalse, fieldData));
        assertFalse("Should not match attribute device_type key not existing", matches(attributeKeyNotExistValueEmptyFalse, fieldData2));

        assertTrue("Should match ID equal", matches(idEqualTrue, fieldData));
        assertTrue("Should match ID not equal", matches(idNotEqualTrue, fieldData));
        assertFalse("Should not match ID not equal", matches(idNotEqualFalse, fieldData));
        assertTrue("Should match ID containing", matches(idInTrue, fieldData));
        assertFalse("Should not match ID containing", matches(idInFalse, fieldData));
        assertTrue("Should match ID not containing", matches(idOutTrue, fieldData));
        assertFalse("Should not match ID not containing", matches(idOutFalse, fieldData));

        assertTrue("Should match ControllerID equal", matches(controllerIdEqualTrue, fieldData));
        assertTrue("Should match ControllerID containing", matches(controllerIdInTrue, fieldData));
        assertFalse("Should not match ControllerID containing", matches(controllerIdInFalse, fieldData));
        assertTrue("Should match ControllerID not containing", matches(controllerIdOutTrue, fieldData));
        assertFalse("Should not match ControllerID not containing", matches(controllerIdOutFalse, fieldData));

        assertTrue("Should match name equal", matches(nameEqualTrue1, fieldData));
        assertTrue("Should match name equal", matches(nameEqualTrue2, fieldData));
        assertTrue("Should match name containing", matches(nameInTrue, fieldData));
        assertFalse("Should not match name containing", matches(nameInFalse, fieldData));
        assertTrue("Should match name not containing", matches(nameOutTrue, fieldData));
        assertFalse("Should not match name not containing", matches(nameOutFalse, fieldData));

        assertTrue("Should match description equal", matches(descEqualTrue1, fieldData));
        assertTrue("Should match description equal", matches(descEqualTrue2, fieldData));
        assertTrue("Should match description containing", matches(descInTrue, fieldData));
        assertFalse("Should not match description containing", matches(descInFalse, fieldData));
        assertTrue("Should match description not containing", matches(descOutTrue, fieldData));
        assertFalse("Should not match description not containing", matches(descOutFalse, fieldData));

        assertTrue("Should match createdAt less than", matches(createdAtLessTrue, fieldData));
        assertFalse("Should not match createdAt less than", matches(createdAtLessFalse, fieldData));
        assertTrue("Should match createdAt greater than", matches(createdAtGreaterTrue, fieldData));
        assertFalse("Should not match createdAt greater than", matches(createdAtGreaterFalse, fieldData));
        assertTrue("Should match createdAt less or equal", matches(createdAtLessEqualTrue, fieldData));
        assertFalse("Should not match createdAt less or equal", matches(createdAtLessEqualFalse, fieldData));
        assertTrue("Should match createdAt greater or equal", matches(createdAtGreaterEqualTrue, fieldData));
        assertFalse("Should not match createdAt greater or equal", matches(createdAtGreaterEqualFalse, fieldData));
        assertTrue("Should match createdAt greater or equal", matches(createdAtGreaterEqualEqualTrue, fieldData));
        assertTrue("Should match createdAt less or equal", matches(createdAtLessEqualEqualTrue, fieldData));

        assertTrue("Should match modifiedAt less than", matches(lastModifiedAtLessTrue, fieldData));
        assertFalse("Should not match modifiedAt less than", matches(lastModifiedAtLessFalse, fieldData));
        assertTrue("Should match modifiedAt greater than", matches(lastModifiedAtGreaterTrue, fieldData));
        assertFalse("Should not match modifiedAt greater than", matches(lastModifiedAtGreaterFalse, fieldData));
        assertTrue("Should match modifiedAt less or equal", matches(lastModifiedAtLessEqualTrue, fieldData));
        assertFalse("Should not match modifiedAt less or equal", matches(lastModifiedAtLessEqualFalse, fieldData));
        assertTrue("Should match modifiedAt greater or equal", matches(lastModifiedAtGreaterEqualTrue, fieldData));
        assertFalse("Should not match modifiedAt greater or equal", matches(lastModifiedAtGreaterEqualFalse, fieldData));
        assertTrue("Should match modifiedAt greater or equal", matches(lastModifiedAtGreaterEqualEqualTrue, fieldData));
        assertTrue("Should match modifiedAt less or equal", matches(lastModifiedAtLessEqualEqualTrue, fieldData));

        assertTrue("Should match updatestatus equal", matches(updateStatusEqualTrue1, fieldData));
        assertTrue("Should match updatestatus equal", matches(updateStatusEqualTrue2, fieldData));
        assertFalse("Should not match updatestatus equal", matches(updateStatusEqualFalse, fieldData));
        assertTrue("Should match updatestatus containing", matches(updateStatusInTrue, fieldData));
        assertFalse("Should not match updatestatus containing", matches(updateStatusInFalse, fieldData));
        assertTrue("Should match updatestatus not containing", matches(updateStatusOutTrue, fieldData));
        assertFalse("Should not match updatestatus not containing", matches(updateStatusOutFalse, fieldData));
    }
}
