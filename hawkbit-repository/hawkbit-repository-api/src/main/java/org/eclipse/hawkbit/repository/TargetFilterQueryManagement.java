/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository;

import java.util.Optional;

import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.eclipse.hawkbit.im.authentication.SpPermission.SpringEvalExpressions;
import org.eclipse.hawkbit.repository.builder.AutoAssignDistributionSetUpdate;
import org.eclipse.hawkbit.repository.builder.TargetFilterQueryCreate;
import org.eclipse.hawkbit.repository.builder.TargetFilterQueryUpdate;
import org.eclipse.hawkbit.repository.exception.EntityNotFoundException;
import org.eclipse.hawkbit.repository.exception.InvalidAutoAssignActionTypeException;
import org.eclipse.hawkbit.repository.exception.InvalidAutoAssignDistributionSetException;
import org.eclipse.hawkbit.repository.exception.AssignmentQuotaExceededException;
import org.eclipse.hawkbit.repository.exception.RSQLParameterSyntaxException;
import org.eclipse.hawkbit.repository.exception.RSQLParameterUnsupportedFieldException;
import org.eclipse.hawkbit.repository.model.Action.ActionType;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.TargetFilterQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Management service for {@link TargetFilterQuery}s.
 *
 */
public interface TargetFilterQueryManagement {

    /**
     * Creates a new {@link TargetFilterQuery}.
     *
     * @param create
     *            to create
     * 
     * @return the new {@link TargetFilterQuery}
     * 
     * @throws ConstraintViolationException
     *             if fields are not filled as specified. Check
     *             {@link TargetFilterQueryCreate} for field constraints.
     * 
     * @throws AssignmentQuotaExceededException
     *             if the maximum number of targets that is addressed by the
     *             given query is exceeded (auto-assignments only)
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_CREATE_TARGET)
    TargetFilterQuery create(@NotNull @Valid TargetFilterQueryCreate create);

    /**
     * Deletes the {@link TargetFilterQuery} with the given ID.
     *
     * @param targetFilterQueryId
     *            IDs of target filter query to be deleted
     * 
     * @throws EntityNotFoundException
     *             if filter with given ID does not exist
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_DELETE_TARGET)
    void delete(long targetFilterQueryId);

    /**
     * Verifies the provided filter syntax.
     * 
     * @param query
     *            to verify
     * 
     * @return <code>true</code> if syntax is valid
     * 
     * @throws RSQLParameterUnsupportedFieldException
     *             if a field in the RSQL string is used but not provided by the
     *             given {@code fieldNameProvider}
     * 
     * @throws RSQLParameterSyntaxException
     *             if the RSQL syntax is wrong
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    boolean verifyTargetFilterQuerySyntax(@NotNull String query);

    /**
     *
     * Retrieves all {@link TargetFilterQuery}s.
     *
     * @param pageable
     *            pagination parameter
     * @return the found {@link TargetFilterQuery}s
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    Page<TargetFilterQuery> findAll(@NotNull Pageable pageable);

    /**
     * Counts all {@link TargetFilterQuery}s.
     * 
     * @return the number of all target filter queries
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    long count();

    /**
     * Retrieves all {@link TargetFilterQuery}s which match the given name
     * filter.
     *
     * @param pageable
     *            pagination parameter
     * @param name
     *            name filter
     * @return the page with the found {@link TargetFilterQuery}s
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    Page<TargetFilterQuery> findByName(@NotNull Pageable pageable, @NotNull String name);

    /**
     * Retrieves all {@link TargetFilterQuery} which match the given RSQL
     * filter.
     *
     * @param pageable
     *            pagination parameter
     * @param rsqlFilter
     *            RSQL filter string
     * @return the page with the found {@link TargetFilterQuery}s
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    Page<TargetFilterQuery> findByRsql(@NotNull Pageable pageable, @NotNull String rsqlFilter);

    /**
     * Retrieves all {@link TargetFilterQuery}s which match the given query.
     *
     * @param pageable
     *            pagination parameter
     * @param query
     *            the query saved in the target filter query
     * @return the page with the found {@link TargetFilterQuery}s
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    Page<TargetFilterQuery> findByQuery(@NotNull Pageable pageable, @NotNull String query);

    /**
     * Retrieves all {@link TargetFilterQuery}s which match the given
     * auto-assign distribution set and RSQL filter.
     *
     * @param pageable
     *            pagination parameter
     * @param setId
     *            the auto assign distribution set
     * @param rsqlParam
     *            RSQL filter
     * @return the page with the found {@link TargetFilterQuery}s
     * 
     * @throws EntityNotFoundException
     *             if DS with given ID does not exist
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    Page<TargetFilterQuery> findByAutoAssignDSAndRsql(@NotNull Pageable pageable, long setId, String rsqlParam);

    /**
     * Retrieves all {@link TargetFilterQuery}s with an auto-assign distribution
     * set.
     *
     * @return the page with the found {@link TargetFilterQuery}s
     * @param pageable
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    Page<TargetFilterQuery> findWithAutoAssignDS(@NotNull Pageable pageable);

    /**
     * Finds the {@link TargetFilterQuery} by id.
     *
     * @param targetFilterQueryId
     *            Target filter query id
     * @return the found {@link TargetFilterQuery}
     *
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    Optional<TargetFilterQuery> get(long targetFilterQueryId);

    /**
     * Finds the {@link TargetFilterQuery} that matches the given name.
     *
     * @param targetFilterQueryName
     *            Target filter query name
     * @return the found {@link TargetFilterQuery}
     *
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    Optional<TargetFilterQuery> getByName(@NotNull String targetFilterQueryName);

    /**
     * Updates the {@link TargetFilterQuery}.
     *
     * @param update
     *            to be updated
     * 
     * @return the updated {@link TargetFilterQuery}
     * 
     * @throws EntityNotFoundException
     *             if either {@link TargetFilterQuery} and/or autoAssignDs are
     *             provided but not found
     * 
     * @throws ConstraintViolationException
     *             if fields are not filled as specified. Check
     *             {@link TargetFilterQueryUpdate} for field constraints.
     * 
     * @throws AssignmentQuotaExceededException
     *             if the update contains a new query which addresses too many
     *             targets (auto-assignments only)
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_UPDATE_TARGET)
    TargetFilterQuery update(@NotNull @Valid TargetFilterQueryUpdate update);

    /**
     * Updates the auto assign settings of an {@link TargetFilterQuery}.
     * 
     * @param autoAssignDistributionSetUpdate
     *            the new auto assignment
     * 
     * @return the updated {@link TargetFilterQuery}
     * 
     * @throws EntityNotFoundException
     *             if either {@link TargetFilterQuery} and/or autoAssignDs are
     *             provided but not found
     * 
     * @throws AssignmentQuotaExceededException
     *             if the query that is already associated with this filter
     *             query addresses too many targets (auto-assignments only)
     * 
     * @throws InvalidAutoAssignActionTypeException
     *             if the provided auto-assign {@link ActionType} is not valid
     *             (neither FORCED, nor SOFT)
     * 
     * @throws InvalidAutoAssignDistributionSetException
     *             if the provided auto-assign {@link DistributionSet} is not
     *             valid (incomplete or soft deleted)
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_UPDATE_TARGET)
    TargetFilterQuery updateAutoAssignDS(
            @NotNull @Valid AutoAssignDistributionSetUpdate autoAssignDistributionSetUpdate);
}
