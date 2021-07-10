/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.test.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.io.IOUtils;
import org.eclipse.hawkbit.repository.ArtifactManagement;
import org.eclipse.hawkbit.repository.Constants;
import org.eclipse.hawkbit.repository.ControllerManagement;
import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.DistributionSetTagManagement;
import org.eclipse.hawkbit.repository.DistributionSetTypeManagement;
import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.QuotaManagement;
import org.eclipse.hawkbit.repository.RolloutManagement;
import org.eclipse.hawkbit.repository.SoftwareModuleManagement;
import org.eclipse.hawkbit.repository.SoftwareModuleTypeManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TargetTagManagement;
import org.eclipse.hawkbit.repository.builder.TagCreate;
import org.eclipse.hawkbit.repository.builder.TargetCreate;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.Action.Status;
import org.eclipse.hawkbit.repository.model.ActionStatus;
import org.eclipse.hawkbit.repository.model.Artifact;
import org.eclipse.hawkbit.repository.model.ArtifactUpload;
import org.eclipse.hawkbit.repository.model.BaseEntity;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.DistributionSetTag;
import org.eclipse.hawkbit.repository.model.DistributionSetType;
import org.eclipse.hawkbit.repository.model.NamedEntity;
import org.eclipse.hawkbit.repository.model.NamedVersionedEntity;
import org.eclipse.hawkbit.repository.model.Rollout;
import org.eclipse.hawkbit.repository.model.RolloutGroup.RolloutGroupErrorAction;
import org.eclipse.hawkbit.repository.model.RolloutGroup.RolloutGroupErrorCondition;
import org.eclipse.hawkbit.repository.model.RolloutGroup.RolloutGroupSuccessCondition;
import org.eclipse.hawkbit.repository.model.RolloutGroupConditionBuilder;
import org.eclipse.hawkbit.repository.model.RolloutGroupConditions;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.repository.model.SoftwareModuleMetadata;
import org.eclipse.hawkbit.repository.model.SoftwareModuleType;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetTag;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import com.google.common.collect.Lists;

import net._01001111.text.LoremIpsum;

/**
 * Data generator utility for tests.
 */
public class TestdataFactory {
    private static final LoremIpsum LOREM = new LoremIpsum();

    public static final String VISIBLE_SM_MD_KEY = "visibleMetdataKey";
    public static final String VISIBLE_SM_MD_VALUE = "visibleMetdataValue";
    public static final String INVISIBLE_SM_MD_KEY = "invisibleMetdataKey";
    public static final String INVISIBLE_SM_MD_VALUE = "invisibleMetdataValue";

    /**
     * default {@link Target#getControllerId()}.
     */
    public static final String DEFAULT_CONTROLLER_ID = "targetExist";

    /**
     * Default {@link SoftwareModule#getVendor()}.
     */
    public static final String DEFAULT_VENDOR = "Vendor Limited, California";

    /**
     * Default {@link NamedVersionedEntity#getVersion()}.
     */
    public static final String DEFAULT_VERSION = "1.0";

    /**
     * Default {@link NamedEntity#getDescription()}.
     */
    public static final String DEFAULT_DESCRIPTION = "Desc: " + LOREM.words(10);

    /**
     * Key of test default {@link DistributionSetType}.
     */
    public static final String DS_TYPE_DEFAULT = "test_default_ds_type";

    /**
     * Key of test "os" {@link SoftwareModuleType} : mandatory firmware in
     * {@link #DS_TYPE_DEFAULT}.
     */
    public static final String SM_TYPE_OS = "os";

    /**
     * Key of test "runtime" {@link SoftwareModuleType} : optional firmware in
     * {@link #DS_TYPE_DEFAULT}.
     */
    public static final String SM_TYPE_RT = "runtime";

    /**
     * Key of test "application" {@link SoftwareModuleType} : optional software
     * in {@link #DS_TYPE_DEFAULT}.
     */
    public static final String SM_TYPE_APP = "application";

    @Autowired
    private ControllerManagement controllerManagament;

    @Autowired
    private SoftwareModuleManagement softwareModuleManagement;

    @Autowired
    private SoftwareModuleTypeManagement softwareModuleTypeManagement;

    @Autowired
    private DistributionSetManagement distributionSetManagement;

    @Autowired
    private DistributionSetTypeManagement distributionSetTypeManagement;

    @Autowired
    private TargetManagement targetManagement;

    @Autowired
    private DeploymentManagement deploymentManagement;

    @Autowired
    private TargetTagManagement targetTagManagement;

    @Autowired
    private DistributionSetTagManagement distributionSetTagManagement;

    @Autowired
    private EntityFactory entityFactory;

    @Autowired
    private ArtifactManagement artifactManagement;

    @Autowired
    private RolloutManagement rolloutManagement;

    @Autowired
    private QuotaManagement quotaManagement;

    /**
     * Creates {@link DistributionSet} in repository including three
     * {@link SoftwareModule}s of types {@link #SM_TYPE_OS}, {@link #SM_TYPE_RT}
     * , {@link #SM_TYPE_APP} with {@link #DEFAULT_VERSION} and
     * {@link DistributionSet#isRequiredMigrationStep()} <code>false</code>.
     * 
     * @param prefix
     *            for {@link SoftwareModule}s and {@link DistributionSet}s name,
     *            vendor and description.
     * 
     * @return {@link DistributionSet} entity.
     */
    public DistributionSet createDistributionSet(final String prefix) {
        return createDistributionSet(prefix, DEFAULT_VERSION, false);
    }

    /**
     * Creates {@link DistributionSet} in repository including three
     * {@link SoftwareModule}s of types {@link #SM_TYPE_OS}, {@link #SM_TYPE_RT}
     * , {@link #SM_TYPE_APP} with {@link #DEFAULT_VERSION} and
     * {@link DistributionSet#isRequiredMigrationStep()} <code>false</code>.
     * 
     * @return {@link DistributionSet} entity.
     */
    public DistributionSet createDistributionSet() {
        return createDistributionSet(UUID.randomUUID().toString(), DEFAULT_VERSION, false);
    }

    /**
     * Creates {@link DistributionSet} in repository including three
     * {@link SoftwareModule}s of types {@link #SM_TYPE_OS}, {@link #SM_TYPE_RT}
     * , {@link #SM_TYPE_APP} with {@link #DEFAULT_VERSION} and
     * {@link DistributionSet#isRequiredMigrationStep()} <code>false</code>.
     * 
     * @param modules
     *            of {@link DistributionSet#getModules()}
     * 
     * @return {@link DistributionSet} entity.
     */
    public DistributionSet createDistributionSet(final Collection<SoftwareModule> modules) {
        return createDistributionSet("", DEFAULT_VERSION, false, modules);
    }

    /**
     * Creates {@link DistributionSet} in repository including three
     * {@link SoftwareModule}s of types {@link #SM_TYPE_OS}, {@link #SM_TYPE_RT}
     * , {@link #SM_TYPE_APP} with {@link #DEFAULT_VERSION} and
     * {@link DistributionSet#isRequiredMigrationStep()} <code>false</code>.
     * 
     * @param modules
     *            of {@link DistributionSet#getModules()}
     * @param prefix
     *            for {@link SoftwareModule}s and {@link DistributionSet}s name,
     *            vendor and description.
     * 
     * @return {@link DistributionSet} entity.
     */
    public DistributionSet createDistributionSet(final Collection<SoftwareModule> modules, final String prefix) {
        return createDistributionSet(prefix, DEFAULT_VERSION, false, modules);
    }

    /**
     * Creates {@link DistributionSet} in repository including three
     * {@link SoftwareModule}s of types {@link #SM_TYPE_OS}, {@link #SM_TYPE_RT}
     * , {@link #SM_TYPE_APP} with {@link #DEFAULT_VERSION}.
     * 
     * @param prefix
     *            for {@link SoftwareModule}s and {@link DistributionSet}s name,
     *            vendor and description.
     * @param isRequiredMigrationStep
     *            for {@link DistributionSet#isRequiredMigrationStep()}
     * 
     * @return {@link DistributionSet} entity.
     */
    public DistributionSet createDistributionSet(final String prefix, final boolean isRequiredMigrationStep) {
        return createDistributionSet(prefix, DEFAULT_VERSION, isRequiredMigrationStep);
    }

    /**
     * Creates {@link DistributionSet} in repository including three
     * {@link SoftwareModule}s of types {@link #SM_TYPE_OS}, {@link #SM_TYPE_RT}
     * , {@link #SM_TYPE_APP} with {@link #DEFAULT_VERSION} and
     * {@link DistributionSet#isRequiredMigrationStep()} <code>false</code>.
     * 
     * @param prefix
     *            for {@link SoftwareModule}s and {@link DistributionSet}s name,
     *            vendor and description.
     * @param tags
     *            {@link DistributionSet#getTags()}
     * 
     * @return {@link DistributionSet} entity.
     */
    public DistributionSet createDistributionSet(final String prefix, final Collection<DistributionSetTag> tags) {
        return createDistributionSet(prefix, DEFAULT_VERSION, tags);
    }

    /**
     * Creates {@link DistributionSet} in repository including three
     * {@link SoftwareModule}s of types {@link #SM_TYPE_OS}, {@link #SM_TYPE_RT}
     * , {@link #SM_TYPE_APP}.
     * 
     * @param prefix
     *            for {@link SoftwareModule}s and {@link DistributionSet}s name,
     *            vendor and description.
     * @param version
     *            {@link DistributionSet#getVersion()} and
     *            {@link SoftwareModule#getVersion()} extended by a random
     *            number.
     * @param isRequiredMigrationStep
     *            for {@link DistributionSet#isRequiredMigrationStep()}
     * 
     * @return {@link DistributionSet} entity.
     */
    public DistributionSet createDistributionSet(final String prefix, final String version,
            final boolean isRequiredMigrationStep) {

        final SoftwareModule appMod = softwareModuleManagement.create(entityFactory.softwareModule().create()
                .type(findOrCreateSoftwareModuleType(SM_TYPE_APP, Integer.MAX_VALUE)).name(prefix + SM_TYPE_APP)
                .version(version + "." + new SecureRandom().nextInt(100)).description(LOREM.words(20))
                .vendor(prefix + " vendor Limited, California"));
        final SoftwareModule runtimeMod = softwareModuleManagement
                .create(entityFactory.softwareModule().create().type(findOrCreateSoftwareModuleType(SM_TYPE_RT))
                        .name(prefix + "app runtime").version(version + "." + new SecureRandom().nextInt(100))
                        .description(LOREM.words(20)).vendor(prefix + " vendor GmbH, Stuttgart, Germany"));
        final SoftwareModule osMod = softwareModuleManagement
                .create(entityFactory.softwareModule().create().type(findOrCreateSoftwareModuleType(SM_TYPE_OS))
                        .name(prefix + " Firmware").version(version + "." + new SecureRandom().nextInt(100))
                        .description(LOREM.words(20)).vendor(prefix + " vendor Limited Inc, California"));

        return distributionSetManagement.create(
                entityFactory.distributionSet().create().name(prefix != null && prefix.length() > 0 ? prefix : "DS")
                        .version(version).description(LOREM.words(10)).type(findOrCreateDefaultTestDsType())
                        .modules(Arrays.asList(osMod.getId(), runtimeMod.getId(), appMod.getId()))
                        .requiredMigrationStep(isRequiredMigrationStep));
    }

    /**
     * Adds {@link SoftwareModuleMetadata} to every module of given
     * {@link DistributionSet}.
     * 
     * {@link #VISIBLE_SM_MD_VALUE}, {@link #VISIBLE_SM_MD_KEY} with
     * {@link SoftwareModuleMetadata#isTargetVisible()} and
     * {@link #INVISIBLE_SM_MD_KEY}, {@link #INVISIBLE_SM_MD_VALUE} without
     * {@link SoftwareModuleMetadata#isTargetVisible()}
     * 
     * @param set
     *            to add metadata to
     */
    public void addSoftwareModuleMetadata(final DistributionSet set) {
        set.getModules().forEach(this::addTestModuleMetadata);
    }

    private void addTestModuleMetadata(final SoftwareModule module) {
        softwareModuleManagement.createMetaData(entityFactory.softwareModuleMetadata().create(module.getId())
                .key(VISIBLE_SM_MD_KEY).value(VISIBLE_SM_MD_VALUE).targetVisible(true));
        softwareModuleManagement.createMetaData(entityFactory.softwareModuleMetadata().create(module.getId())
                .key(INVISIBLE_SM_MD_KEY).value(INVISIBLE_SM_MD_VALUE).targetVisible(false));

    }

    /**
     * Creates {@link DistributionSet} in repository.
     * 
     * @param prefix
     *            for {@link SoftwareModule}s and {@link DistributionSet}s name,
     *            vendor and description.
     * @param version
     *            {@link DistributionSet#getVersion()} and
     *            {@link SoftwareModule#getVersion()} extended by a random
     *            number.
     * @param isRequiredMigrationStep
     *            for {@link DistributionSet#isRequiredMigrationStep()}
     * @param modules
     *            for {@link DistributionSet#getModules()}
     * 
     * @return {@link DistributionSet} entity.
     */
    public DistributionSet createDistributionSet(final String prefix, final String version,
            final boolean isRequiredMigrationStep, final Collection<SoftwareModule> modules) {

        return distributionSetManagement.create(
                entityFactory.distributionSet().create().name(prefix != null && prefix.length() > 0 ? prefix : "DS")
                        .version(version).description(LOREM.words(10)).type(findOrCreateDefaultTestDsType())
                        .modules(modules.stream().map(SoftwareModule::getId).collect(Collectors.toList()))
                        .requiredMigrationStep(isRequiredMigrationStep));
    }

    /**
     * Creates {@link DistributionSet} in repository including three
     * {@link SoftwareModule}s of types {@link #SM_TYPE_OS}, {@link #SM_TYPE_RT}
     * , {@link #SM_TYPE_APP}.
     * 
     * @param prefix
     *            for {@link SoftwareModule}s and {@link DistributionSet}s name,
     *            vendor and description.
     * @param version
     *            {@link DistributionSet#getVersion()} and
     *            {@link SoftwareModule#getVersion()} extended by a random
     *            number.updat
     * @param tags
     *            {@link DistributionSet#getTags()}
     * 
     * @return {@link DistributionSet} entity.
     */
    public DistributionSet createDistributionSet(final String prefix, final String version,
            final Collection<DistributionSetTag> tags) {

        final DistributionSet set = createDistributionSet(prefix, version, false);

        tags.forEach(tag -> distributionSetManagement.toggleTagAssignment(Arrays.asList(set.getId()), tag.getName()));

        return distributionSetManagement.get(set.getId()).get();

    }

    /**
     * Creates {@link DistributionSet}s in repository including three
     * {@link SoftwareModule}s of types {@link #SM_TYPE_OS}, {@link #SM_TYPE_RT}
     * , {@link #SM_TYPE_APP} with {@link #DEFAULT_VERSION} followed by an
     * iterative number and {@link DistributionSet#isRequiredMigrationStep()}
     * <code>false</code>.
     * 
     * @param number
     *            of {@link DistributionSet}s to create
     * 
     * @return {@link List} of {@link DistributionSet} entities
     */
    public List<DistributionSet> createDistributionSets(final int number) {

        return createDistributionSets("", number);
    }

    /**
     * Create a list of {@link DistributionSet}s without modules, i.e.
     * incomplete.
     * 
     * @param number
     *            of {@link DistributionSet}s to create
     * @return {@link List} of {@link DistributionSet} entities
     */
    public List<DistributionSet> createDistributionSetsWithoutModules(final int number) {

        final List<DistributionSet> sets = Lists.newArrayListWithExpectedSize(number);
        for (int i = 0; i < number; i++) {
            sets.add(distributionSetManagement
                    .create(entityFactory.distributionSet().create().name("DS" + i).version(DEFAULT_VERSION + "." + i)
                            .description(LOREM.words(10)).type(findOrCreateDefaultTestDsType())));
        }

        return sets;
    }

    /**
     * Creates {@link DistributionSet}s in repository including three
     * {@link SoftwareModule}s of types {@link #SM_TYPE_OS}, {@link #SM_TYPE_RT}
     * , {@link #SM_TYPE_APP} with {@link #DEFAULT_VERSION} followed by an
     * iterative number and {@link DistributionSet#isRequiredMigrationStep()}
     * <code>false</code>.
     * 
     * @param prefix
     *            for {@link SoftwareModule}s and {@link DistributionSet}s name,
     *            vendor and description.
     * @param number
     *            of {@link DistributionSet}s to create
     * 
     * @return {@link List} of {@link DistributionSet} entities
     */
    public List<DistributionSet> createDistributionSets(final String prefix, final int number) {

        final List<DistributionSet> sets = new ArrayList<>();
        for (int i = 0; i < number; i++) {
            sets.add(createDistributionSet(prefix, DEFAULT_VERSION + "." + i, false));
        }

        return sets;
    }

    /**
     * Creates {@link DistributionSet}s in repository with
     * {@link #DEFAULT_DESCRIPTION} and
     * {@link DistributionSet#isRequiredMigrationStep()} <code>false</code>.
     * 
     * @param name
     *            {@link DistributionSet#getName()}
     * @param version
     *            {@link DistributionSet#getVersion()}
     * 
     * @return {@link DistributionSet} entity
     */
    public DistributionSet createDistributionSetWithNoSoftwareModules(final String name, final String version) {

        return distributionSetManagement.create(entityFactory.distributionSet().create().name(name).version(version)
                .description(DEFAULT_DESCRIPTION).type(findOrCreateDefaultTestDsType()));
    }

    /**
     * Creates {@link Artifact}s for given {@link SoftwareModule} with a small
     * text payload.
     * 
     * @param moduleId
     *            the {@link Artifact}s belong to.
     * 
     * @return {@link Artifact} entity.
     */
    public List<Artifact> createArtifacts(final Long moduleId) {
        final List<Artifact> artifacts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final String artifactData = "some test data" + i;
            artifacts.add(createArtifact(artifactData, moduleId, "filename" + i));
        }

        return artifacts;
    }

    /**
     * Create an {@link Artifact} for given {@link SoftwareModule} with a small
     * text payload.
     * 
     * @param artifactData
     *            the {@link Artifact} Inputstream
     * 
     * @param moduleId
     *            the {@link Artifact} belongs to
     * 
     * @param filename
     *            that was provided during upload.
     * 
     * @return {@link Artifact} entity.
     */
    public Artifact createArtifact(final String artifactData, final Long moduleId, final String filename) {
        final InputStream stubInputStream = IOUtils.toInputStream(artifactData, Charset.forName("UTF-8"));
        return artifactManagement
                .create(new ArtifactUpload(stubInputStream, moduleId, filename, false, artifactData.length()));
    }

    /**
     * Create an {@link Artifact} for given {@link SoftwareModule} with a small
     * text payload.
     *
     * @param artifactData
     *            the {@link Artifact} Inputstream
     *
     * @param moduleId
     *            the {@link Artifact} belongs to
     *
     * @param filename
     *            that was provided during upload.
     *
     * @param fileSize
     *            the file size
     *
     * @return {@link Artifact} entity.
     */
    public Artifact createArtifact(final byte[] artifactData, final Long moduleId, final String filename,
            final int fileSize) {
        return artifactManagement.create(
                new ArtifactUpload(new ByteArrayInputStream(artifactData), moduleId, filename, false, fileSize));
    }

    /**
     * Creates {@link SoftwareModule} with {@link #DEFAULT_VENDOR} and
     * {@link #DEFAULT_VERSION} and random generated
     * {@link Target#getDescription()} in the repository.
     * 
     * @param typeKey
     *            of the {@link SoftwareModuleType}
     * 
     * @return persisted {@link SoftwareModule}.
     */
    public SoftwareModule createSoftwareModule(final String typeKey) {
        return createSoftwareModule(typeKey, "");
    }

    /**
     * Creates {@link SoftwareModule} of type
     * {@value Constants#SMT_DEFAULT_APP_KEY} with {@link #DEFAULT_VENDOR} and
     * {@link #DEFAULT_VERSION} and random generated
     * {@link Target#getDescription()} in the repository.
     * 
     * 
     * @return persisted {@link SoftwareModule}.
     */
    public SoftwareModule createSoftwareModuleApp() {
        return createSoftwareModule(Constants.SMT_DEFAULT_APP_KEY, "");
    }

    /**
     * Creates {@link SoftwareModule} of type
     * {@value Constants#SMT_DEFAULT_APP_KEY} with {@link #DEFAULT_VENDOR} and
     * {@link #DEFAULT_VERSION} and random generated
     * {@link Target#getDescription()} in the repository.
     * 
     * @param prefix
     *            added to name and version
     * 
     * 
     * @return persisted {@link SoftwareModule}.
     */
    public SoftwareModule createSoftwareModuleApp(final String prefix) {
        return createSoftwareModule(Constants.SMT_DEFAULT_APP_KEY, prefix);
    }

    /**
     * Creates {@link SoftwareModule} of type
     * {@value Constants#SMT_DEFAULT_OS_KEY} with {@link #DEFAULT_VENDOR} and
     * {@link #DEFAULT_VERSION} and random generated
     * {@link Target#getDescription()} in the repository.
     * 
     * 
     * @return persisted {@link SoftwareModule}.
     */
    public SoftwareModule createSoftwareModuleOs() {
        return createSoftwareModule(Constants.SMT_DEFAULT_OS_KEY, "");
    }

    /**
     * Creates {@link SoftwareModule} of type
     * {@value Constants#SMT_DEFAULT_OS_KEY} with {@link #DEFAULT_VENDOR} and
     * {@link #DEFAULT_VERSION} and random generated
     * {@link Target#getDescription()} in the repository.
     * 
     * @param prefix
     *            added to name and version
     * 
     * 
     * @return persisted {@link SoftwareModule}.
     */
    public SoftwareModule createSoftwareModuleOs(final String prefix) {
        return createSoftwareModule(Constants.SMT_DEFAULT_OS_KEY, prefix);
    }

    /**
     * Creates {@link SoftwareModule} with {@link #DEFAULT_VENDOR} and
     * {@link #DEFAULT_VERSION} and random generated
     * {@link Target#getDescription()} in the repository.
     * 
     * @param typeKey
     *            of the {@link SoftwareModuleType}
     * @param prefix
     *            added to name and version
     * 
     * @return persisted {@link SoftwareModule}.
     */
    public SoftwareModule createSoftwareModule(final String typeKey, final String prefix) {
        return softwareModuleManagement.create(entityFactory.softwareModule().create()
                .type(findOrCreateSoftwareModuleType(typeKey)).name(prefix + typeKey).version(prefix + DEFAULT_VERSION)
                .description(LOREM.words(10)).vendor(DEFAULT_VENDOR));
    }

    /**
     * @return persisted {@link Target} with {@link #DEFAULT_CONTROLLER_ID}.
     */
    public Target createTarget() {
        return createTarget(DEFAULT_CONTROLLER_ID);
    }

    /**
     * @param controllerId
     *            of the target
     * @return persisted {@link Target}
     */
    public Target createTarget(final String controllerId) {
        return createTarget(controllerId, controllerId);
    }

    /**
     * @param controllerId
     *            of the target
     * @param targetName
     *            name of the target
     * @return persisted {@link Target}
     */
    public Target createTarget(final String controllerId, final String targetName) {
        final Target target = targetManagement
                .create(entityFactory.target().create().controllerId(controllerId).name(targetName));
        assertTargetProperlyCreated(target);
        return target;
    }

    private void assertTargetProperlyCreated(final Target target) {
        assertThat(target.getCreatedBy()).isNotNull();
        assertThat(target.getCreatedAt()).isNotNull();
        assertThat(target.getLastModifiedBy()).isNotNull();
        assertThat(target.getLastModifiedAt()).isNotNull();

        assertThat(target.getUpdateStatus()).isEqualTo(TargetUpdateStatus.UNKNOWN);
    }

    /**
     * Creates {@link DistributionSet}s in repository including three
     * {@link SoftwareModule}s of types {@link #SM_TYPE_OS}, {@link #SM_TYPE_RT}
     * , {@link #SM_TYPE_APP} with {@link #DEFAULT_VERSION} followed by an
     * iterative number and {@link DistributionSet#isRequiredMigrationStep()}
     * <code>false</code>.
     * 
     * In addition it updates the created {@link DistributionSet}s and
     * {@link SoftwareModule}s to ensure that
     * {@link BaseEntity#getLastModifiedAt()} and
     * {@link BaseEntity#getLastModifiedBy()} is filled.
     * 
     * @return persisted {@link DistributionSet}.
     */
    public DistributionSet createUpdatedDistributionSet() {
        DistributionSet set = createDistributionSet("");
        set = distributionSetManagement.update(
                entityFactory.distributionSet().update(set.getId()).description("Updated " + DEFAULT_DESCRIPTION));

        set.getModules().forEach(module -> softwareModuleManagement.update(
                entityFactory.softwareModule().update(module.getId()).description("Updated " + DEFAULT_DESCRIPTION)));

        // load also lazy stuff
        return distributionSetManagement.getWithDetails(set.getId()).get();
    }

    /**
     * @return {@link DistributionSetType} with key {@link #DS_TYPE_DEFAULT} and
     *         {@link SoftwareModuleType}s {@link #SM_TYPE_OS},
     *         {@link #SM_TYPE_RT} , {@link #SM_TYPE_APP}.
     */
    public DistributionSetType findOrCreateDefaultTestDsType() {
        final List<SoftwareModuleType> mand = new ArrayList<>();
        mand.add(findOrCreateSoftwareModuleType(SM_TYPE_OS));

        final List<SoftwareModuleType> opt = new ArrayList<>();
        opt.add(findOrCreateSoftwareModuleType(SM_TYPE_APP, Integer.MAX_VALUE));
        opt.add(findOrCreateSoftwareModuleType(SM_TYPE_RT));

        return findOrCreateDistributionSetType(DS_TYPE_DEFAULT, "OS (FW) mandatory, runtime (FW) and app (SW) optional",
                mand, opt);
    }

    /**
     * Creates {@link DistributionSetType} in repository.
     * 
     * @param dsTypeKey
     *            {@link DistributionSetType#getKey()}
     * @param dsTypeName
     *            {@link DistributionSetType#getName()}
     * 
     * @return persisted {@link DistributionSetType}
     */
    public DistributionSetType findOrCreateDistributionSetType(final String dsTypeKey, final String dsTypeName) {
        return distributionSetTypeManagement.getByKey(dsTypeKey)
                .orElseGet(() -> distributionSetTypeManagement.create(entityFactory.distributionSetType().create()
                        .key(dsTypeKey).name(dsTypeName).description(LOREM.words(10)).colour("black")));
    }

    /**
     * Finds {@link DistributionSetType} in repository with given
     * {@link DistributionSetType#getKey()} or creates if it does not exist yet.
     * 
     * @param dsTypeKey
     *            {@link DistributionSetType#getKey()}
     * @param dsTypeName
     *            {@link DistributionSetType#getName()}
     * @param mandatory
     *            {@link DistributionSetType#getMandatoryModuleTypes()}
     * @param optional
     *            {@link DistributionSetType#getOptionalModuleTypes()}
     * 
     * @return persisted {@link DistributionSetType}
     */
    public DistributionSetType findOrCreateDistributionSetType(final String dsTypeKey, final String dsTypeName,
            final Collection<SoftwareModuleType> mandatory, final Collection<SoftwareModuleType> optional) {
        return distributionSetTypeManagement.getByKey(dsTypeKey)
                .orElseGet(() -> distributionSetTypeManagement.create(entityFactory.distributionSetType().create()
                        .key(dsTypeKey).name(dsTypeName).description(LOREM.words(10)).colour("black")
                        .optional(optional.stream().map(SoftwareModuleType::getId).collect(Collectors.toList()))
                        .mandatory(mandatory.stream().map(SoftwareModuleType::getId).collect(Collectors.toList()))));
    }

    /**
     * Finds {@link SoftwareModuleType} in repository with given
     * {@link SoftwareModuleType#getKey()} or creates if it does not exist yet
     * with {@link SoftwareModuleType#getMaxAssignments()} = 1.
     * 
     * @param key
     *            {@link SoftwareModuleType#getKey()}
     * 
     * @return persisted {@link SoftwareModuleType}
     */
    public SoftwareModuleType findOrCreateSoftwareModuleType(final String key) {
        return findOrCreateSoftwareModuleType(key, 1);
    }

    /**
     * Finds {@link SoftwareModuleType} in repository with given
     * {@link SoftwareModuleType#getKey()} or creates if it does not exist yet.
     * 
     * @param key
     *            {@link SoftwareModuleType#getKey()}
     * @param maxAssignments
     *            {@link SoftwareModuleType#getMaxAssignments()}
     * 
     * @return persisted {@link SoftwareModuleType}
     */
    public SoftwareModuleType findOrCreateSoftwareModuleType(final String key, final int maxAssignments) {
        return softwareModuleTypeManagement.getByKey(key)
                .orElseGet(() -> softwareModuleTypeManagement.create(entityFactory.softwareModuleType().create()
                        .key(key).name(key).description(LOREM.words(10)).maxAssignments(maxAssignments)));
    }

    /**
     * Creates a {@link DistributionSet}.
     *
     * @param name
     *            {@link DistributionSet#getName()}
     * @param version
     *            {@link DistributionSet#getVersion()}
     * @param type
     *            {@link DistributionSet#getType()}
     * @param modules
     *            {@link DistributionSet#getModules()}
     * 
     * @return the created {@link DistributionSet}
     */
    public DistributionSet createDistributionSet(final String name, final String version,
            final DistributionSetType type, final Collection<SoftwareModule> modules) {
        return distributionSetManagement.create(
                entityFactory.distributionSet().create().name(name).version(version).description(LOREM.words(10))
                        .type(type).modules(modules.stream().map(SoftwareModule::getId).collect(Collectors.toList())));
    }

    /**
     * Generates {@link DistributionSet} object without persisting it.
     *
     * @param name
     *            {@link DistributionSet#getName()}
     * @param version
     *            {@link DistributionSet#getVersion()}
     * @param type
     *            {@link DistributionSet#getType()}
     * @param modules
     *            {@link DistributionSet#getModules()}
     * @param requiredMigrationStep
     *            {@link DistributionSet#isRequiredMigrationStep()}
     * 
     * @return the created {@link DistributionSet}
     */
    public DistributionSet generateDistributionSet(final String name, final String version,
            final DistributionSetType type, final Collection<SoftwareModule> modules,
            final boolean requiredMigrationStep) {
        return entityFactory.distributionSet().create().name(name).version(version).description(LOREM.words(10))
                .type(type).modules(modules.stream().map(SoftwareModule::getId).collect(Collectors.toList()))
                .requiredMigrationStep(requiredMigrationStep).build();
    }

    /**
     * Generates {@link DistributionSet} object without persisting it.
     *
     * @param name
     *            {@link DistributionSet#getName()}
     * @param version
     *            {@link DistributionSet#getVersion()}
     * @param type
     *            {@link DistributionSet#getType()}
     * @param modules
     *            {@link DistributionSet#getModules()}
     * 
     * @return the created {@link DistributionSet}
     */
    public DistributionSet generateDistributionSet(final String name, final String version,
            final DistributionSetType type, final Collection<SoftwareModule> modules) {
        return generateDistributionSet(name, version, type, modules, false);
    }

    /**
     * builder method for generating a {@link DistributionSet}.
     *
     * @param name
     *            {@link DistributionSet#getName()}
     * 
     * @return the generated {@link DistributionSet}
     */
    public DistributionSet generateDistributionSet(final String name) {
        return generateDistributionSet(name, DEFAULT_VERSION, findOrCreateDefaultTestDsType(), Collections.emptyList(),
                false);
    }

    /**
     * Creates {@link Target}s in repository and with
     * {@link #DEFAULT_CONTROLLER_ID} as prefix for
     * {@link Target#getControllerId()}.
     * 
     * @param number
     *            of {@link Target}s to create
     * 
     * @return {@link List} of {@link Target} entities
     */
    public List<Target> createTargets(final int number) {

        final List<TargetCreate> targets = Lists.newArrayListWithExpectedSize(number);
        for (int i = 0; i < number; i++) {
            targets.add(entityFactory.target().create().controllerId(DEFAULT_CONTROLLER_ID + i));
        }

        return targetManagement.create(targets);
    }

    /**
     * Creates {@link Target}s in repository and with given targetIds.
     * 
     * @param targetIds
     *            specifies the IDs of the targets
     * 
     * @return {@link List} of {@link Target} entities
     */
    public List<Target> createTargets(final String... targetIds) {

        final List<TargetCreate> targets = new ArrayList<>();
        for (final String targetId : targetIds) {
            targets.add(entityFactory.target().create().controllerId(targetId));
        }

        return targetManagement.create(targets);
    }

    /**
     * Builds {@link Target} objects with given prefix for
     * {@link Target#getControllerId()} followed by a number suffix.
     * 
     * @param start
     *            value for the controllerId suffix
     * @param numberOfTargets
     *            of {@link Target}s to generate
     * @param controllerIdPrefix
     *            for {@link Target#getControllerId()} generation.
     * @return list of {@link Target} objects
     */
    private List<Target> generateTargets(final int start, final int numberOfTargets, final String controllerIdPrefix) {
        final List<Target> targets = Lists.newArrayListWithExpectedSize(numberOfTargets);
        for (int i = start; i < start + numberOfTargets; i++) {
            targets.add(entityFactory.target().create().controllerId(controllerIdPrefix + i).build());
        }

        return targets;
    }

    /**
     * Builds {@link Target} objects with given prefix for
     * {@link Target#getControllerId()} followed by a number suffix starting
     * with 0.
     * 
     * @param numberOfTargets
     *            of {@link Target}s to generate
     * @param controllerIdPrefix
     *            for {@link Target#getControllerId()} generation.
     * @return list of {@link Target} objects
     */
    public List<Target> generateTargets(final int numberOfTargets, final String controllerIdPrefix) {
        return generateTargets(0, numberOfTargets, controllerIdPrefix);
    }

    /**
     * builds a set of {@link Target} fixtures from the given parameters.
     *
     * @param numberOfTargets
     *            number of targets to create
     * @param prefix
     *            prefix used for the controller ID and description
     * @return set of {@link Target}
     */
    public List<Target> createTargets(final int numberOfTargets, final String prefix) {
        return createTargets(numberOfTargets, prefix, prefix);
    }

    /**
     * builds a set of {@link Target} fixtures from the given parameters.
     *
     * @param numberOfTargets
     *            number of targets to create
     * @param controllerIdPrefix
     *            prefix used for the controller ID
     * @param descriptionPrefix
     *            prefix used for the description
     * @return set of {@link Target}
     */
    public List<Target> createTargets(final int numberOfTargets, final String controllerIdPrefix,
            final String descriptionPrefix) {

        return targetManagement.create(IntStream.range(0, numberOfTargets)
                .mapToObj(i -> entityFactory.target().create()
                        .controllerId(String.format("%s-%05d", controllerIdPrefix, i))
                        .description(descriptionPrefix + i))
                .collect(Collectors.toList()));
    }

    /**
     * builds a set of {@link Target} fixtures from the given parameters.
     *
     * @param numberOfTargets
     *            number of targets to create
     * @param controllerIdPrefix
     *            prefix used for the controller ID
     * @param descriptionPrefix
     *            prefix used for the description
     * @param lastTargetQuery
     *            last time the target polled
     * @return set of {@link Target}
     */
    public List<Target> createTargets(final int numberOfTargets, final String controllerIdPrefix,
            final String descriptionPrefix, final Long lastTargetQuery) {

        return targetManagement.create(IntStream.range(0, numberOfTargets)
                .mapToObj(i -> entityFactory.target().create()
                        .controllerId(String.format("%s-%05d", controllerIdPrefix, i))
                        .description(descriptionPrefix + i).lastTargetQuery(lastTargetQuery))
                .collect(Collectors.toList()));
    }

    /**
     * Create a set of {@link TargetTag}s.
     *
     * @param number
     *            number of {@link TargetTag}. to be created
     * @param tagPrefix
     *            prefix for the {@link TargetTag#getName()}
     * @return the created set of {@link TargetTag}s
     */
    public List<TargetTag> createTargetTags(final int number, final String tagPrefix) {
        final List<TagCreate> result = Lists.newArrayListWithExpectedSize(number);

        for (int i = 0; i < number; i++) {
            result.add(entityFactory.tag().create().name(tagPrefix + i).description(tagPrefix + i)
                    .colour(String.valueOf(i)));
        }

        return targetTagManagement.create(result);
    }

    /**
     * Creates {@link DistributionSetTag}s in repository.
     * 
     * @param number
     *            of {@link DistributionSetTag}s
     * 
     * @return the persisted {@link DistributionSetTag}s
     */
    public List<DistributionSetTag> createDistributionSetTags(final int number) {
        final List<TagCreate> result = Lists.newArrayListWithExpectedSize(number);

        for (int i = 0; i < number; i++) {
            result.add(
                    entityFactory.tag().create().name("tag" + i).description("tagdesc" + i).colour(String.valueOf(i)));
        }

        return distributionSetTagManagement.create(result);
    }

    private Action sendUpdateActionStatusToTarget(final Status status, final Action updActA,
            final Collection<String> msgs) {

        return controllerManagament.addUpdateActionStatus(
                entityFactory.actionStatus().create(updActA.getId()).status(status).messages(msgs));
    }

    /**
     * Append {@link ActionStatus} to all {@link Action}s of given
     * {@link Target}s.
     * 
     * @param targets
     *            to add {@link ActionStatus}
     * @param status
     *            to add
     * @param message
     *            to add
     * 
     * @return updated {@link Action}.
     */
    public List<Action> sendUpdateActionStatusToTargets(final Collection<Target> targets, final Status status,
            final String message) {
        return sendUpdateActionStatusToTargets(targets, status, Arrays.asList(message));
    }

    /**
     * Append {@link ActionStatus} to all {@link Action}s of given
     * {@link Target}s.
     * 
     * @param targets
     *            to add {@link ActionStatus}
     * @param status
     *            to add
     * @param msgs
     *            to add
     * 
     * @return updated {@link Action}.
     */
    public List<Action> sendUpdateActionStatusToTargets(final Collection<Target> targets, final Status status,
            final Collection<String> msgs) {
        final List<Action> result = new ArrayList<>();
        for (final Target target : targets) {
            final List<Action> findByTarget = deploymentManagement
                    .findActionsByTarget(target.getControllerId(), PageRequest.of(0, 400)).getContent();
            for (final Action action : findByTarget) {
                result.add(sendUpdateActionStatusToTarget(status, action, msgs));
            }
        }
        return result;
    }

    /**
     * Creates rollout based on given parameters.
     * 
     * @param rolloutName
     *            of the {@link Rollout}
     * @param rolloutDescription
     *            of the {@link Rollout}
     * @param groupSize
     *            of the {@link Rollout}
     * @param filterQuery
     *            to identify the {@link Target}s
     * @param distributionSet
     *            to assign
     * @param successCondition
     *            to switch to next group
     * @param errorCondition
     *            to switch to next group
     * @return created {@link Rollout}
     */
    public Rollout createRolloutByVariables(final String rolloutName, final String rolloutDescription,
            final int groupSize, final String filterQuery, final DistributionSet distributionSet,
            final String successCondition, final String errorCondition) {
        return createRolloutByVariables(rolloutName, rolloutDescription, groupSize, filterQuery, distributionSet,
                successCondition, errorCondition, Action.ActionType.FORCED, null);
    }

    /**
     * Creates rollout based on given parameters.
     *
     * @param rolloutName
     *            of the {@link Rollout}
     * @param rolloutDescription
     *            of the {@link Rollout}
     * @param groupSize
     *            of the {@link Rollout}
     * @param filterQuery
     *            to identify the {@link Target}s
     * @param distributionSet
     *            to assign
     * @param successCondition
     *            to switch to next group
     * @param errorCondition
     *            to switch to next group
     * @param actionType
     *            the type of the Rollout
     * @param weight
     *            weight of the Rollout
     * @return created {@link Rollout}
     */
    public Rollout createRolloutByVariables(final String rolloutName, final String rolloutDescription,
            final int groupSize, final String filterQuery, final DistributionSet distributionSet,
            final String successCondition, final String errorCondition, final Action.ActionType actionType,
            final Integer weight) {
        final RolloutGroupConditions conditions = new RolloutGroupConditionBuilder().withDefaults()
                .successCondition(RolloutGroupSuccessCondition.THRESHOLD, successCondition)
                .errorCondition(RolloutGroupErrorCondition.THRESHOLD, errorCondition)
                .errorAction(RolloutGroupErrorAction.PAUSE, null).build();

        final Rollout rollout = rolloutManagement.create(
                entityFactory.rollout().create().name(rolloutName).description(rolloutDescription)
                        .targetFilterQuery(filterQuery).set(distributionSet).actionType(actionType).weight(weight),
                groupSize, conditions);

        // Run here, because Scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        return rolloutManagement.get(rollout.getId()).get();
    }

    /**
     * Create {@link Rollout} with a new {@link DistributionSet} and
     * {@link Target}s.
     * 
     * @param prefix
     *            for rollouts name, description,
     *            {@link Target#getControllerId()} filter
     * @return created {@link Rollout}
     */
    public Rollout createRollout(final String prefix) {
        createTargets(quotaManagement.getMaxRolloutGroupsPerRollout(), prefix);
        return createRolloutByVariables(prefix, prefix + " description", 5, "controllerId==" + prefix + "*",
                createDistributionSet(prefix), "50", "5");
    }

    /**
     * Create the soft deleted {@link Rollout} with a new
     * {@link DistributionSet} and {@link Target}s.
     * 
     * @param prefix
     *            for rollouts name, description,
     *            {@link Target#getControllerId()} filter
     * @return created {@link Rollout}
     */
    public Rollout createSoftDeletedRollout(final String prefix) {
        final Rollout newRollout = createRollout(prefix);
        rolloutManagement.start(newRollout.getId());
        rolloutManagement.handleRollouts();
        rolloutManagement.delete(newRollout.getId());
        rolloutManagement.handleRollouts();
        return newRollout;
    }
}
