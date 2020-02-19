/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa;

import org.eclipse.hawkbit.repository.jpa.model.JpaDistributionSet;
import org.eclipse.hawkbit.repository.jpa.model.JpaTarget;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.Tag;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.eclipse.hawkbit.repository.model.TenantAwareBaseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link Target} repository.
 *
 */
@Transactional(readOnly = true)
public interface TargetRepository extends BaseEntityRepository<JpaTarget, Long>, JpaSpecificationExecutor<JpaTarget> {
    /**
     * Sets {@link JpaTarget#getAssignedDistributionSet()}.
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
    @Transactional
    @Query("UPDATE JpaTarget t SET t.assignedDistributionSet = :set, t.lastModifiedAt = :lastModifiedAt, t.lastModifiedBy = :lastModifiedBy, t.updateStatus = :status WHERE t.id IN :targets")
    void setAssignedDistributionSetAndUpdateStatus(@Param("status") TargetUpdateStatus status,
            @Param("set") JpaDistributionSet set, @Param("lastModifiedAt") Long modifiedAt,
            @Param("lastModifiedBy") String modifiedBy, @Param("targets") Collection<Long> targets);

    /**
     * Sets {@link JpaTarget#getAssignedDistributionSet()},
     * {@link JpaTarget#getInstalledDistributionSet()} and
     * {@link JpaTarget#getInstallationDate()}
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
    @Transactional
    @Query("UPDATE JpaTarget t SET t.assignedDistributionSet = :set, t.installedDistributionSet = :set, t.installationDate = :lastModifiedAt, t.lastModifiedAt = :lastModifiedAt, t.lastModifiedBy = :lastModifiedBy, t.updateStatus = :status WHERE t.id IN :targets")
    void setAssignedAndInstalledDistributionSetAndUpdateStatus(@Param("status") TargetUpdateStatus status,
            @Param("set") JpaDistributionSet set, @Param("lastModifiedAt") Long modifiedAt,
            @Param("lastModifiedBy") String modifiedBy, @Param("targets") Collection<Long> targets);

    /**
     * Loads {@link Target} by given ID.
     *
     * @param controllerID
     *            to search for
     * @return found {@link Target} or <code>null</code> if not found.
     */
    Optional<Target> findByControllerId(String controllerID);

    @Query("SELECT t.controllerAttributes FROM JpaTarget t WHERE t.controllerId=:controllerId")
    Map<String, String> getControllerAttributes(@Param("controllerId") String controllerId);

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
    @Transactional
    // Workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=349477
    @Query("DELETE FROM JpaTarget t WHERE t.id IN ?1")
    void deleteByIdIn(Collection<Long> targetIDs);

    /**
     * Finds {@link Target}s by assigned {@link Tag}.
     * 
     * @param page
     *            pages query and sorting information
     *
     * @param tagId
     *            to be found
     * @return page of found targets
     */
    @Query(value = "SELECT DISTINCT t FROM JpaTarget t JOIN t.tags tt WHERE tt.id = :tag")
    Page<JpaTarget> findByTag(Pageable page, @Param("tag") Long tagId);

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
    List<JpaTarget> findByTagNameAndControllerIdIn(@Param("tagname") String tag,
            @Param("targets") Collection<String> controllerIds);

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
    Page<Target> findByUpdateStatus(Pageable pageable, TargetUpdateStatus status);

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
    Page<Target> findByInstalledDistributionSetId(Pageable pageable, Long setID);

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
    Page<Target> findByAssignedDistributionSetId(Pageable pageable, Long setID);

    /**
     * retrieves {@link Target}s where
     * {@link JpaTarget#isRequestControllerAttributes()}.
     * 
     * @param pageable
     *            page parameter
     *
     * @return the found {@link Target}s
     * 
     */
    Page<Target> findByRequestControllerAttributesIsTrue(Pageable pageable);

    /**
     * Counts number of targets with given distribution set Id.
     *
     * @param distId
     *            to search for
     *
     * @return number of found {@link Target}s.
     */
    Long countByAssignedDistributionSetId(Long distId);

    /**
     * Counts number of targets with given distribution set Id.
     *
     * @param distId
     *            to search for
     * @return number of found {@link Target}s.
     */
    Long countByInstalledDistributionSetId(Long distId);

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
    List<JpaTarget> findAllById(Iterable<Long> ids);

    /**
     * Finds all targets for the given list of controller IDs.
     * 
     * @param controllerIds
     *            The controller IDs to look for.
     * 
     * @return The list of matching targets.
     */
    @Query("SELECT t FROM JpaTarget t WHERE t.controllerId IN ?1")
    List<JpaTarget> findAllByControllerId(Iterable<String> controllerIds);

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
    Page<Target> findByRolloutTargetGroupRolloutGroupId(Long rolloutGroupId, Pageable page);

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

    /**
     * Deletes all {@link TenantAwareBaseEntity} of a given tenant. For safety
     * reasons (this is a "delete everything" query after all) we add the tenant
     * manually to query even if this will by done by {@link EntityManager}
     * anyhow. The DB should take care of optimizing this away.
     *
     * @param tenant
     *            to delete data from
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM JpaTarget t WHERE t.tenant = :tenant")
    void deleteByTenant(@Param("tenant") String tenant);

    /**
     * Filters a given list of controllerIds and returns only the existing IDs.
     * 
     * @param controllerIds
     *            The IDs to be filtered
     * @return only the existing controllerIds
     */
    @Query("SELECT t.controllerId FROM JpaTarget t WHERE t.controllerId IN ?1")
    List<String> filterNonExistingControllerIds(Iterable<String> controllerIds);
}
