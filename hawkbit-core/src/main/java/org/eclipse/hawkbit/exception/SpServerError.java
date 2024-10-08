/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.exception;

/**
 * Define the Error code for Error handling
 */

public enum SpServerError {


    SP_REPO_GENERIC_ERROR("hawkbit.server.error.repo.genericError", "unknown error occurred"),
    SP_REPO_ENTITY_ALREADY_EXISTS("hawkbit.server.error.repo.entitiyAlreayExists",
          "The given entity already exists in database"),
    SP_REPO_AUTO_CONFIRMATION_ALREADY_ACTIVE("hawkbit.server.error.repo.autoConfAlreadyActive",
          "Auto confirmation is already active"),
    SP_CONFIRMATION_FEEDBACK_INVALID("hawkbit.server.confirmation.feedback.invalid",
          "Confirmation feedback is not valid"),
    SP_REPO_CONSTRAINT_VIOLATION("hawkbit.server.error.repo.constraintViolation",
            "The given entity cannot be saved due to Constraint Violation"),
    SP_REPO_INVALID_TARGET_ADDRESS("hawkbit.server.error.repo.invalidTargetAddress",
            "The target address is not well formed"),
    SP_REPO_ENTITY_NOT_EXISTS("hawkbit.server.error.repo.entityNotFound",
            "The given entity does not exist in the repository"),
    SP_REPO_CONCURRENT_MODIFICATION("hawkbit.server.error.repo.concurrentModification",
            "The given entity has been changed by another user/session"),
    SP_TARGET_ATTRIBUTES_INVALID("hawkbit.server.error.repo.invalidTargetAttributes",
            "The given target attributes are invalid"),
    SP_REST_SORT_PARAM_SYNTAX("hawkbit.server.error.rest.param.sortParamSyntax",
            "The given sort parameter is not well formed"),
    SP_REST_RSQL_SEARCH_PARAM_SYNTAX("hawkbit.server.error.rest.param.rsqlParamSyntax",
            "The given search parameter is not well formed"),
    SP_REST_RSQL_PARAM_INVALID_FIELD("hawkbit.server.error.rest.param.rsqlInvalidField",
            "The given search parameter field does not exist"),
    SP_REST_SORT_PARAM_INVALID_FIELD("hawkbit.server.error.rest.param.invalidField",
            "The given sort parameter field does not exist"),
    SP_REST_SORT_PARAM_INVALID_DIRECTION("hawkbit.server.error.rest.param.invalidDirection",
            "The given sort parameter direction does not exist"),
    SP_REST_BODY_NOT_READABLE("hawkbit.server.error.rest.body.notReadable",
            "The given request body is not well formed"),
    SP_ARTIFACT_UPLOAD_FAILED("hawkbit.server.error.artifact.uploadFailed",
            "Upload of artifact failed with internal server error."),
    SP_ARTIFACT_ENCRYPTION_NOT_SUPPORTED("hawkbit.server.error.artifact.encryptionNotSupported",
            "Artifact encryption is not supported."),
    SP_ARTIFACT_ENCRYPTION_FAILED("hawkbit.server.error.artifact.encryptionFailed",
            "Artifact encryption operation failed."),
    SP_ARTIFACT_UPLOAD_FAILED_MD5_MATCH("hawkbit.server.error.artifact.uploadFailed.checksum.md5.match",
            "Upload of artifact failed as the provided MD5 checksum did not match with the provided artifact."),
    SP_ARTIFACT_UPLOAD_FAILED_SHA1_MATCH("hawkbit.server.error.artifact.uploadFailed.checksum.sha1.match",
            "Upload of artifact failed as the provided SHA1 checksum did not match with the provided artifact."),
    SP_ARTIFACT_UPLOAD_FAILED_SHA256_MATCH("hawkbit.server.error.artifact.uploadFailed.checksum.sha256.match",
            "Upload of artifact failed as the provided SHA256 checksum did not match with the provided artifact."),
    SP_DS_CREATION_FAILED_MISSING_MODULE("hawkbit.server.error.distributionset.creationFailed.missingModule",
            "Creation if Distribution Set failed as module is missing that is configured as mandatory."),
    SP_INSUFFICIENT_PERMISSION("hawkbit.server.error.insufficientpermission", "Insufficient Permission"),
    SP_ARTIFACT_DELETE_FAILED("hawkbit.server.error.artifact.deleteFailed",
            "Deletion of artifact failed with internal server error."),
    SP_ARTIFACT_LOAD_FAILED("hawkbit.server.error.artifact.loadFailed",
            "Load of artifact failed with internal server error."),
    SP_ARTIFACT_BINARY_DELETED("hawkbit.server.error.artifact.binaryDeleted",
            "The artifact binary does not exist anymore."),
    SP_QUOTA_EXCEEDED("hawkbit.server.error.quota.tooManyEntries", "Too many entries have been inserted."),
    /**
     * error that describes that size of uploaded file exceeds size quota
     */
    SP_FILE_SIZE_QUOTA_EXCEEDED("hawkbit.server.error.quota.fileSizeExceeded", "File exceeds size quota."),
    /**
     * error that describes that size of uploaded file exceeds storage quota
     */
    SP_STORAGE_QUOTA_EXCEEDED("hawkbit.server.error.quota.storageExceeded",
            "Storage quota will be exceeded if file is uploaded."),
    /**
     * error message, which describes that the action can not be canceled cause
     * the action is inactive.
     */
    SP_ACTION_NOT_CANCELABLE("hawkbit.server.error.action.notcancelable",
            "Only active actions which are in status pending are cancelable."),
    /**
     * error message, which describes that the action can not be force quit
     * cause the action is inactive.
     */
    SP_ACTION_NOT_FORCE_QUITABLE("hawkbit.server.error.action.notforcequitable",
            "Only active actions which are in status pending can be force quit."),
    SP_DS_INCOMPLETE("hawkbit.server.error.distributionset.incomplete",
            "Distribution set is assigned/locked to a target that is incomplete (i.e. mandatory modules are missing)"),
    /**
     * error message, which describes that an entity is locked and can't be functionally modified
     */
    SP_LOCKED("hawkbit.server.error.locked", "Entry is locked. Could not be functionally modified"),
    /**
     * error message, which describes that an entity is locked and can't be functionally modified
     */
    SP_DELETED("hawkbit.server.error.deleted", "Entry is soft deleted"),
    SP_DS_INVALID("hawkbit.server.error.distributionset.invalid", "Invalid distribution set is assigned to a target"),
    SP_DS_TYPE_UNDEFINED("hawkbit.server.error.distributionset.type.undefined",
            "Distribution set type is not yet defined. Modules cannot be added until definition."),
    SP_DS_MODULE_UNSUPPORTED("hawkbit.server.error.distributionset.modules.unsupported",
            "Distribution set type does not contain the given module, i.e. is incompatible."),
    SP_REPO_TENANT_NOT_EXISTS("hawkbit.server.error.repo.tenantNotExists",
            "The entity cannot be inserted due the tenant does not exists"),
    SP_ENTITY_LOCKED("hawkbit.server.error.entitiylocked", "The given entity is locked by the server."),
    SP_REPO_ENTITY_READ_ONLY("hawkbit.server.error.entityreadonly",
            "The given entity is read only and the change cannot be completed."),
    SP_CONFIGURATION_VALUE_INVALID("hawkbit.server.error.configValueInvalid",
            "The given configuration value is invalid."),
    SP_CONFIGURATION_KEY_INVALID("hawkbit.server.error.configKeyInvalid", "The given configuration key is invalid."),
    SP_ROLLOUT_ILLEGAL_STATE("hawkbit.server.error.rollout.illegalstate",
            "The rollout is in the wrong state for the requested operation"),
    SP_ROLLOUT_VERIFICATION_FAILED("hawkbit.server.error.rollout.verificationFailed",
            "The rollout configuration could not be verified successfully"),
    SP_REPO_OPERATION_NOT_SUPPORTED("hawkbit.server.error.operation.notSupported",
            "Operation or method is (no longer) supported by service."),
    /**
     * Error message informing that the maintenance schedule is invalid.
     */
    SP_MAINTENANCE_SCHEDULE_INVALID("hawkbit.server.error.maintenanceScheduleInvalid",
            "Information for schedule, duration or timezone is missing; or there is no valid maintenance window available in future."),
    /**
     * Error message informing that the action type for auto-assignment is
     * invalid.
     */
    SP_AUTO_ASSIGN_ACTION_TYPE_INVALID("hawkbit.server.error.repo.invalidAutoAssignActionType",
            "The given action type for auto-assignment is invalid: allowed values are ['forced', 'soft', 'downloadonly']"),
    /**
     * Error message informing the user that the requested tenant configuration
     * change is not allowed.
     */
    SP_CONFIGURATION_VALUE_CHANGE_NOT_ALLOWED("hawkbit.server.error.repo.tenantConfigurationValueChangeNotAllowed",
            "The requested tenant configuration value modification is not allowed."),
    SP_MULTIASSIGNMENT_NOT_ENABLED("hawkbit.server.error.multiassignmentNotEnabled",
            "The requested operation requires multi assignments to be enabled."),
    SP_NO_WEIGHT_PROVIDED_IN_MULTIASSIGNMENT_MODE("hawkbit.server.error.noWeightProvidedInMultiAssignmentMode",
            "The requested operation requires a weight to be specified when multi assignments is enabled."),
    SP_TARGET_TYPE_IN_USE("hawkbit.server.error.target.type.used", "Target type is still in use by a target."),
    SP_TARGET_TYPE_INCOMPATIBLE("hawkbit.server.error.target.type.incompatible",
            "Target type of target is not compatible with distribution set."),
    SP_TARGET_TYPE_KEY_OR_NAME_REQUIRED("hawkbit.server.error.target.type.keyOrNameRequired",
            "Target type key or name is required."),
    SP_STOP_ROLLOUT_FAILED("hawkbit.server.error.stopRolloutFailed", "Stopping the rollout failed.");

    private final String key;
    private final String message;

    /*
     * Repository side Error codes
     */
    SpServerError(final String errorKey, final String message) {
        key = errorKey;
        this.message = message;
    }

    /**
     * Gets the key of the error
     *
     * @return the key of the error
     */
    public String getKey() {
        return key;
    }

    /**
     * Gets the message of the error
     *
     * @return message of the error
     */
    public String getMessage() {
        return message;
    }

}
