/**
 * Copyright (c) 2020 devolo AG and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa;

import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetMetadata;
import org.eclipse.hawkbit.repository.model.TargetTag;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.hawkbit.repository.TargetFields;

/**
 * Service to extract field values from {@link Target}-Object
 */
@Service
public class TargetFieldExtractor {

    private String controllerId;
    private String name;
    private String description;
    private String updateStatus;
    private String address;
    private String lastQuery;
    private List<TargetTag> targetTagList;
    private List<TargetMetadata> metadata;
    private Map<String, String> attributes;
    private DistributionSet assignedDs;
    private DistributionSet installedDs;
    private String createdAt;
    private String lastModifiedAt;

    private static final String EMPTY_STRING = "";

    private TargetFieldData fieldData;

    /**
     * Method to extract the field values from {@link Target}-Object
     *
     * @param   target to extract the data from
     * @return  {@link TargetFieldData} container with the extracted data
     */
    public TargetFieldData extractData(Target target){

        fieldData = new TargetFieldData();

        fetchTargetFieldValues(target);

        fieldData.add(TargetFields.ID, controllerId);
        fieldData.add(TargetFields.CONTROLLERID, controllerId);
        fieldData.add(TargetFields.NAME, name);
        fieldData.add(TargetFields.DESCRIPTION, description);
        fieldData.add(TargetFields.CREATEDAT, createdAt);
        fieldData.add(TargetFields.LASTMODIFIEDAT, lastModifiedAt);
        fieldData.add(TargetFields.UPDATESTATUS, updateStatus);
        fieldData.add(TargetFields.IPADDRESS, address);
        fieldData.add(TargetFields.LASTCONTROLLERREQUESTAT, lastQuery);

        addMetadata();
        addAttributes();
        addAssignedDsData();
        addInstalledDsData();
        addTagData();

        return fieldData;
    }

    private void fetchTargetFieldValues(Target target){
        controllerId = target.getControllerId() == null ? EMPTY_STRING : target.getControllerId();
        name = target.getName() == null ? EMPTY_STRING : target.getName();
        description = target.getDescription() == null ? EMPTY_STRING : target.getDescription();
        updateStatus = target.getUpdateStatus() == null ? TargetUpdateStatus.UNKNOWN.name(): target.getUpdateStatus().name();
        address = target.getAddress() == null ? EMPTY_STRING : target.getAddress().toString();
        lastQuery = (target.getLastTargetQuery() == null) ? EMPTY_STRING : target.getLastTargetQuery().toString();
        targetTagList = new ArrayList<>(target.getTags());
        metadata = target.getMetadata();
        attributes = target.getControllerAttributes();
        assignedDs = target.getAssignedDistributionSet();
        installedDs = target.getInstalledDistributionSet();
        createdAt = Long.toString(target.getCreatedAt());
        lastModifiedAt = Long.toString(target.getLastModifiedAt());
    }

    private void addTagData(){
        if(targetTagList.isEmpty()) {
            return;
        }

        targetTagList.forEach(tag -> fieldData.add(TargetFields.TAG, tag.getName()));
    }

    private void addAttributes(){
        attributes.forEach((key, value) -> fieldData.add(TargetFields.ATTRIBUTE, key, value));
    }

    private void addMetadata(){
        metadata.forEach(data -> fieldData.add(TargetFields.METADATA, data.getKey(), data.getValue()));
    }

    private void addAssignedDsData(){
        if(assignedDs == null) {
            return;
        }

        fieldData.add(TargetFields.ASSIGNEDDS, "name", assignedDs.getName());
        fieldData.add(TargetFields.ASSIGNEDDS, "version", assignedDs.getVersion());
    }

    private void addInstalledDsData(){
        if(installedDs == null) {
            return;
        }

        fieldData.add(TargetFields.INSTALLEDDS, "name", installedDs.getName());
        fieldData.add(TargetFields.INSTALLEDDS, "version", installedDs.getVersion());
    }
}
