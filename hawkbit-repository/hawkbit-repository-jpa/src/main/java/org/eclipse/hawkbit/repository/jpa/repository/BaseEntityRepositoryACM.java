/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.repository.jpa.repository;

import org.eclipse.hawkbit.repository.exception.EntityNotFoundException;
import org.eclipse.hawkbit.repository.exception.InsufficientPermissionException;
import org.eclipse.hawkbit.repository.jpa.acm.AccessController;
import org.eclipse.hawkbit.repository.jpa.model.AbstractJpaTenantAwareBaseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import javax.transaction.Transactional;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class BaseEntityRepositoryACM<T extends AbstractJpaTenantAwareBaseEntity> implements BaseEntityRepository<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseEntityRepositoryACM.class);

    private final BaseEntityRepository<T> repository;
    private final AccessController<T> accessController;

    BaseEntityRepositoryACM(final BaseEntityRepository<T> repository, final AccessController<T> accessController) {
        this.repository = repository;
        this.accessController = accessController;
    }

    @SuppressWarnings("unchecked")
    static <T extends AbstractJpaTenantAwareBaseEntity, R extends BaseEntityRepository<T>> R of(
            final R repository, @NonNull final AccessController<T> accessController) {
        Objects.requireNonNull(repository);
        Objects.requireNonNull(accessController);
        final BaseEntityRepositoryACM<T> repositoryACM =
                new BaseEntityRepositoryACM<>(repository, accessController);
        final R acmProxy = (R) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                repository.getClass().getInterfaces(),
                (proxy, method, args) -> {
                    try {
                        try {
                            // TODO - cache mapping so to speed things
                            final Method delegateMethod =
                                    BaseEntityRepositoryACM.class.getDeclaredMethod(
                                            method.getName(), method.getParameterTypes());
                            return delegateMethod.invoke(repositoryACM, args);
                        } catch (final NoSuchMethodException e) {
                            // call to repository itself
                        }
                        if (method.getName().startsWith("find") || method.getName().startsWith("get")) {
                            final Object result = method.invoke(repository, args);
                            if (Iterable.class.isAssignableFrom(method.getReturnType())) {
                                for (final T e : ((Iterable<T>) result)) {
                                    accessController.assertOperationAllowed(AccessController.Operation.READ, e);
                                }
                            } else if (Optional.class.isAssignableFrom(method.getReturnType())) {
                                return ((Optional<T>)result).map(t -> isOperationAllowed(AccessController.Operation.READ, t, accessController));
                            } else if (repository.getDomainClass().isAssignableFrom(method.getReturnType())) {
                                accessController.assertOperationAllowed(AccessController.Operation.READ, (T)result);
                            }
                            return result;
                        } else if ("toString".equals(method.getName()) && method.getParameterCount() == 0) {
                            return BaseEntityRepositoryACM.class.getSimpleName() +
                                    "(repository: " + repository + ", accessController: " +  accessController + ")";
                        } else {
                            return method.invoke(repository, args);
                        }
                    } catch (final InvocationTargetException e) {
                        throw e.getCause() == null ? e : e.getCause();
                    }
                });
        LOGGER.info("Proxy created -> {}", acmProxy);
        return acmProxy;
    }

    @Override
    @NonNull
    public Optional<T> findById(@NonNull final Long id) {
        return findOne(byIdSpec(id));
    }

    @Override
    @NonNull
    public List<T> findAll() {
        return findAll((Specification<T>) null);
    }

    @Override
    @NonNull
    public List<T> findAllById(@NonNull final Iterable<Long> ids) {
        return findAll(byIdsSpec(ids));
    }

    @Override
    public boolean existsById(@NonNull final Long id) {
        return exists(byIdSpec(id));
    }

    @Override
    public long count() {
        return count(null);
    }

    @Override
    public void delete(@NonNull final T entity) {
        accessController.assertOperationAllowed(AccessController.Operation.DELETE, entity);
        repository.delete(entity);
    }

    @Override
    public void deleteById(@NonNull final Long id) {
        if (!exists(AccessController.Operation.READ, byIdSpec(id))) {
          throw new EntityNotFoundException(repository.getDomainClass(), id);
        }
        if (!exists(AccessController.Operation.DELETE, byIdSpec(id))) {
            throw new InsufficientPermissionException();
        }
        repository.deleteById(id);
    }

    @Override
    public void deleteAllById(@NonNull final Iterable<? extends Long> ids) {
        final List<Long> idList = toList(ids);
        if (count(AccessController.Operation.DELETE, byIdsSpec(idList)) != count(AccessController.Operation.READ, byIdsSpec(idList))) {
            throw new InsufficientPermissionException("Has at least one id that is not allowed for deletion!");
        }
        // TODO - could it be optimized?
        // do delete via query or, after migration to SpringBoot 3.x - to use JPASpecificationExecutor.delete
        final List<T> toDelete = findAllById(idList);
        deleteAll(toDelete);
    }

    @Override
    public void deleteAll(@NonNull final Iterable<? extends T> entities) {
        accessController.assertOperationAllowed(AccessController.Operation.DELETE, entities);
        repository.deleteAll(entities);
    }

    @Override
    public void deleteAll() {
        // TODO - shall this method throw exception having that we have deleteByTenant
        // in order to do not allow deletion for all tenants?
        if (accessController.getAccessRules(AccessController.Operation.DELETE).isPresent()) {
            throw new InsufficientPermissionException(
                    "DELETE operation has restriction for given context! deleteAll can't be executed!");
        }
        repository.deleteAll();
    }

    @Override
    @NonNull
    public <S extends T> S save(@NonNull final S entity) {
        accessController.assertOperationAllowed(AccessController.Operation.UPDATE, entity);
        return repository.save(entity);
    }

    @Override
    public <S extends T> List<S> saveAll(final Iterable<S> entities) {
        accessController.assertOperationAllowed(AccessController.Operation.UPDATE, entities);
        return repository.saveAll(entities);
    }

    @Override
    @NonNull
    public Optional<T> findOne(final Specification<T> spec) {
        return repository.findOne(accessController.appendAccessRules(AccessController.Operation.READ, spec));
    }

    @Override
    @NonNull
    public Iterable<T> findAll(@NonNull final Sort sort) {
        return findAll(null, sort);
    }

    @Override
    @NonNull
    public Page<T> findAll(@NonNull final Pageable pageable) {
        return findAll(null, pageable);
    }

    @Override
    @NonNull
    public List<T> findAll(final Specification<T> spec) {
        return repository.findAll(accessController.appendAccessRules(AccessController.Operation.READ, spec));
    }

    @Override
    @NonNull
    public Page<T> findAll(final Specification<T> spec, @NonNull final Pageable pageable) {
        return repository.findAll(accessController.appendAccessRules(AccessController.Operation.READ, spec), pageable);
    }

    @Override
    @NonNull
    public List<T> findAll(final Specification<T> spec, @NonNull final Sort sort) {
        return repository.findAll(accessController.appendAccessRules(AccessController.Operation.READ, spec), sort);
    }

    @Override
    public boolean exists(@NonNull final Specification<T> spec) {
        return repository.exists(
                Objects.requireNonNull(accessController.appendAccessRules(AccessController.Operation.READ, spec)));
    }

    @Override
    public long count(final Specification<T> spec) {
        return repository.count(accessController.appendAccessRules(AccessController.Operation.READ, spec));
    }

    @Override
    public Slice<T> findAllWithoutCount(final Pageable pageable) {
        return findAllWithoutCount(null, pageable);
    }

    @Override
    public Slice<T> findAllWithoutCount(final Specification<T> spec, final Pageable pageable) {
        return repository.findAllWithoutCount(
                accessController.appendAccessRules(AccessController.Operation.READ, spec), pageable);
    }

    @Override
    @Transactional
    @NonNull
    public <S  extends T> S save(@Nullable AccessController.Operation operation, @NonNull final S entity) {
        if (operation != null) {
            accessController.assertOperationAllowed(operation, entity);
        }
        return repository.save(entity);
    }

    @Override
    @Transactional
    public <S extends T> List<S> saveAll(@Nullable AccessController.Operation operation, final Iterable<S> entities) {
        if (operation != null) {
            accessController.assertOperationAllowed(operation, entities);
        }
        return repository.saveAll(entities);
    }

    @NonNull
    public Optional<T> findOne(@Nullable AccessController.Operation operation, @Nullable Specification<T> spec) {
        if (operation == null) {
            return repository.findOne(spec);
        } else {
            return repository.findOne(accessController.appendAccessRules(operation, spec));
        }
    }

    @Override
    @NonNull
    public List<T> findAll(@Nullable final AccessController.Operation operation, @Nullable final Specification<T> spec) {
        if (operation == null) {
            return repository.findAll(spec);
        } else {
            return repository.findAll(accessController.appendAccessRules(operation, spec));
        }
    }

    @Override
    @NonNull
    public boolean exists(@Nullable AccessController.Operation operation, Specification<T> spec) {
        if (operation == null) {
            return repository.exists(spec);
        } else {
            return repository.exists(
                    Objects.requireNonNull(accessController.appendAccessRules(operation, spec)));
        }
    }

    @Override
    @NonNull
    public long count(@Nullable final AccessController.Operation operation, @Nullable final Specification<T> spec) {
        if (operation == null) {
            return repository.count(spec);
        } else {
            return repository.count(accessController.appendAccessRules(operation, spec));
        }
    }

    @Override
    @NonNull
    public Slice<T> findAllWithoutCount(
            @Nullable final AccessController.Operation operation, @Nullable Specification<T> spec, Pageable pageable) {
        if (operation == null) {
            return repository.findAllWithoutCount(spec, pageable);
        } else {
            return repository.findAllWithoutCount(accessController.appendAccessRules(operation, spec), pageable);
        }
    }

    @Override
    @NonNull
    public Class<T> getDomainClass() {
        return repository.getDomainClass();
    }

    @Override
    public Optional<AccessController<T>> getAccessController() {
        return Optional.of(accessController);
    }

    @Override
    public void deleteByTenant(final String tenant) {
        if (accessController.getAccessRules(AccessController.Operation.DELETE).isPresent()) {
            throw new InsufficientPermissionException(
                    "DELETE operation has restriction for given context! deleteAll can't be executed!");
        }
        repository.deleteByTenant(tenant);
    }

    private static <T> boolean isOperationAllowed(
            final AccessController.Operation operation, T entity,
            final AccessController<T> accessController) {
        try {
            accessController.assertOperationAllowed(operation, entity);
            return true;
        } catch (final InsufficientPermissionException e) {
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T extends Long> List<Long> toList(final Iterable<T> i) {
        if (i instanceof List l) {
            return l;
        } else {
            final List<Long> l = new ArrayList<>();
            i.forEach(l::add);
            return l;
        }
    }
}
