/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.eclipse.hawkbit.repository.jpa.model.JpaDistributionSet;
import org.eclipse.hawkbit.repository.jpa.model.JpaTarget;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.Tag;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link Target} repository.
 *
 */
@Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
public interface TargetRepository extends BaseEntityRepository<JpaTarget, Long>, JpaSpecificationExecutor<JpaTarget> {

    /**
     * Sets {@link Target#getAssignedDistributionSet()}.
     *
     * @param set
     *            to use
     * @param status
     *            to set
     * @param modifiedAt
     *            current time
     * @param modifiedBy
     *            current auditor
     * @param targets
     *            to update
     */
    @Modifying
    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    @Query("UPDATE JpaTarget t  SET t.assignedDistributionSet = :set, t.lastModifiedAt = :lastModifiedAt, t.lastModifiedBy = :lastModifiedBy, t.updateStatus = :status WHERE t.id IN :targets")
    void setAssignedDistributionSetAndUpdateStatus(@Param("status") TargetUpdateStatus status,
            @Param("set") JpaDistributionSet set, @Param("lastModifiedAt") Long modifiedAt,
            @Param("lastModifiedBy") String modifiedBy, @Param("targets") Collection<Long> targets);

    /**
     * Loads {@link Target} including details {@link EntityGraph} by given ID.
     *
     * @param controllerID
     *            to search for
     * @return found {@link Target} or <code>null</code> if not found.
     */
    @EntityGraph(value = "Target.detail", type = EntityGraphType.LOAD)
    Optional<Target> findByControllerId(String controllerID);

    /**
     * Checks if target with given id exists.
     * 
     * @param controllerId
     *            to check
     * @return <code>true</code> if target with given id exists
     */
    @Query("SELECT CASE WHEN COUNT(t)>0 THEN 'true' ELSE 'false' END FROM JpaTarget t WHERE t.controllerId=:controllerId")
    boolean existsByControllerId(@Param("controllerId") String controllerId);

    /**
     * Deletes the {@link Target}s with the given target IDs.
     *
     * @param targetIDs
     *            to be deleted
     */
    @Modifying
    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    // Workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=349477
    @Query("DELETE FROM JpaTarget t WHERE t.id IN ?1")
    void deleteByIdIn(final Collection<Long> targetIDs);

    /**
     * Finds {@link Target}s by assigned {@link Tag}.
     *
     * @param tagId
     *            to be found
     * @return list of found targets
     */
    @Query(value = "SELECT DISTINCT t FROM JpaTarget t JOIN t.tags tt WHERE tt.id = :tag")
    List<JpaTarget> findByTag(@Param("tag") final Long tagId);

    /**
     * Finds all {@link Target}s based on given {@link Target#getControllerId()}
     * list and assigned {@link Tag#getName()}.
     *
     * @param tag
     *            to search for
     * @param controllerIds
     *            to search for
     * @return {@link List} of found {@link Target}s.
     */
    @Query(value = "SELECT DISTINCT t from JpaTarget t JOIN t.tags tt WHERE tt.name = :tagname AND t.controllerId IN :targets")
    List<JpaTarget> findByTagNameAndControllerIdIn(@Param("tagname") final String tag,
            @Param("targets") final Collection<String> controllerIds);

    /**
     * Used by UI to filter based on selected status.
     * 
     * @param pageable
     *            for page configuration
     * @param status
     *            to filter for
     *
     * @return found targets
     */
    Page<Target> findByUpdateStatus(final Pageable pageable, final TargetUpdateStatus status);

    /**
     * retrieves the {@link Target}s which has the {@link DistributionSet}
     * installed with the given ID.
     * 
     * @param pageable
     *            parameter
     * @param setID
     *            the ID of the {@link DistributionSet}
     * @return the found {@link Target}s
     */
    Page<Target> findByInstalledDistributionSetId(final Pageable pageable, final Long setID);

    /**
     * Finds all targets that have defined {@link DistributionSet} assigned.
     * 
     * @param pageable
     *            for page configuration
     * @param setID
     *            is the ID of the {@link DistributionSet} to filter for.
     *
     * @return page of found targets
     */
    Page<Target> findByAssignedDistributionSetId(final Pageable pageable, final Long setID);

    /**
     * Counts number of targets with given
     * {@link Target#getAssignedDistributionSet()}.
     *
     * @param distId
     *            to search for
     *
     * @return number of found {@link Target}s.
     */
    Long countByAssignedDistributionSetId(final Long distId);

    /**
     * Counts number of targets with given
     * {@link Target#getInstalledDistributionSet()}.
     *
     * @param distId
     *            to search for
     * @return number of found {@link Target}s.
     */
    Long countByInstalledDistributionSetId(final Long distId);

    /**
     * Finds all {@link Target}s in the repository.
     *
     * @return {@link List} of {@link Target}s
     *
     * @see org.springframework.data.repository.CrudRepository#findAll()
     */
    @Override
    List<JpaTarget> findAll();

    @Override
    // Workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=349477
    @Query("SELECT t FROM JpaTarget t WHERE t.id IN ?1")
    List<JpaTarget> findAll(Iterable<Long> ids);

    /**
     * 
     * Finds all targets of a rollout group.
     * 
     * @param rolloutGroupId
     *            the ID of the rollout group
     * @param page
     *            the page request parameter
     * @return a page of all targets related to a rollout group
     */
    Page<Target> findByRolloutTargetGroupRolloutGroupId(final Long rolloutGroupId, Pageable page);

    /**
     * Finds all targets related to a target rollout group stored for a specific
     * rollout.
     * 
     * @param rolloutGroupId
     *            the rollout group the targets should belong to
     * @param page
     *            the page request parameter
     * @return a page of all targets related to a rollout group
     */
    Page<Target> findByActionsRolloutGroupId(Long rolloutGroupId, Pageable page);
}
