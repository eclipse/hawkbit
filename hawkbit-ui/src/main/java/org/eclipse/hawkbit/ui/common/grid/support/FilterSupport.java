/**
 * Copyright (c) 2020 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.hawkbit.ui.common.grid.support;

import java.util.EnumMap;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

import org.eclipse.hawkbit.ui.common.event.FilterType;

import com.vaadin.data.provider.ConfigurableFilterDataProvider;
import com.vaadin.data.provider.DataProvider;
import com.vaadin.data.provider.DataProviderWrapper;
import com.vaadin.data.provider.Query;

/**
 * Support for Filter in Grid
 *
 * @param <T>
 *            Data provider Proxy entity type
 * @param <F>
 *            Custom filter type
 */
public class FilterSupport<T, F> {
    private final CustomFilterDataProviderWrapper<T, F> filterDataProvider;
    private final EnumMap<FilterType, FilterTypeSetter<?>> filterTypeToSetterMapping;
    private final UnaryOperator<F> filterCloner;
    private final Runnable afterRefreshFilterCallback;

    private F entityFilter;

    /**
     * Constructor for FilterSupport
     *
     * @param dataProvider
     *            Data provider that should be enhanced with a custom filter
     */
    public FilterSupport(final DataProvider<T, F> dataProvider) {
        this(dataProvider, null, null);
    }

    /**
     * Constructor for FilterSupport
     *
     * @param dataProvider
     *            Data provider that should be enhanced with a custom filter
     * @param filterCloner
     *            Creates a clone for a filter
     */
    public FilterSupport(final DataProvider<T, F> dataProvider, final UnaryOperator<F> filterCloner) {
        this(dataProvider, filterCloner, null);
    }

    /**
     * Constructor for FilterSupport
     *
     * @param dataProvider
     *            Data provider that should be enhanced with a custom filter
     * @param afterRefreshFilterCallback
     *            Callback to be called after filter/data refresh
     */
    public FilterSupport(final DataProvider<T, F> dataProvider, final Runnable afterRefreshFilterCallback) {
        this(dataProvider, null, afterRefreshFilterCallback);
    }

    /**
     * Constructor for FilterSupport
     *
     * @param dataProvider
     *            Data provider that should be enhanced with a custom filter
     * @param filterCloner
     *            Creates a clone for a filter
     * @param afterRefreshFilterCallback
     *            Callback to be called after filter/data refresh
     */
    public FilterSupport(final DataProvider<T, F> dataProvider, final UnaryOperator<F> filterCloner,
            final Runnable afterRefreshFilterCallback) {
        this.filterDataProvider = new CustomFilterDataProviderWrapper<>(dataProvider);
        this.filterTypeToSetterMapping = new EnumMap<>(FilterType.class);
        this.filterCloner = filterCloner;
        this.afterRefreshFilterCallback = afterRefreshFilterCallback;
    }

    /**
     * Add setter mapping to filter type
     *
     * @param filterType
     *            Filter type
     * @param setter
     *            Filter type setter
     * @param <R>
     *            Generic
     */
    public <R> void addMapping(final FilterType filterType, final BiConsumer<F, R> setter) {
        filterTypeToSetterMapping.put(filterType, new FilterTypeSetter<>(setter));
    }

    /**
     * Add setter mapping to filter type
     *
     * @param filterType
     *            Filter type
     * @param setter
     *            Filter type setter
     * @param defaultValue
     *            Default value for setter
     * @param <R>
     *            Generic
     */
    public <R> void addMapping(final FilterType filterType, final BiConsumer<F, R> setter, final R defaultValue) {
        filterTypeToSetterMapping.put(filterType, new FilterTypeSetter<>(setter, defaultValue));
    }

    /**
     * Update filter value
     *
     * @param filterType
     *            Filter type
     * @param filterValue
     *            Filter value
     * @param <R>
     *            Generic
     */
    public <R> void updateFilter(final FilterType filterType, final R filterValue) {
        updateFilter((BiConsumer<F, R>) filterTypeToSetterMapping.get(filterType).getSetter(), filterValue);
    }

    /**
     * Update filter value
     *
     * @param setter
     *            Filter type setter
     * @param filterValue
     *            Filter value
     * @param <R>
     *            Generic
     */
    public <R> void updateFilter(final BiConsumer<F, R> setter, final R filterValue) {
        if (setter != null) {
            setter.accept(entityFilter, filterValue);
            refreshFilter();
        }
    }

    /**
     * Refresh filter data
     */
    public void refreshFilter() {
        // data provider receives a fresh copy of current entity filter
        // to make it effectively immutable
        filterDataProvider.setFilter(filterCloner != null ? filterCloner.apply(entityFilter) : entityFilter);

        if (afterRefreshFilterCallback != null) {
            afterRefreshFilterCallback.run();
        }
    }

    /**
     * @return Filter data provider
     */
    public CustomFilterDataProviderWrapper<T, F> getFilterDataProvider() {
        return filterDataProvider;
    }

    /**
     * @return Original data provider
     */
    public DataProvider<T, F> getOriginalDataProvider() {
        return filterDataProvider.getDataProvider();
    }

    /**
     * Verifies if filter type is supported
     *
     * @param filterType
     *            Filter type
     *
     * @return True if filter type exist in the filter to setter map else false
     */
    public boolean isFilterTypeSupported(final FilterType filterType) {
        return filterTypeToSetterMapping.keySet().contains(filterType);
    }

    /**
     * @return Entity filter
     */
    public F getFilter() {
        return entityFilter;
    }

    /**
     * Sets the entity filter
     *
     * @param entityFilter
     *            Generic type entity filter
     */
    public void setFilter(final F entityFilter) {
        this.entityFilter = entityFilter;
    }

    /**
     * Update the filter value to default in filter to setter mapping
     */
    public void restoreFilter() {
        filterTypeToSetterMapping.values().forEach(FilterTypeSetter::restoreDefaultValue);
        refreshFilter();
    }

    private class FilterTypeSetter<R> {
        private final BiConsumer<F, R> setter;
        private final R defaultValue;

        /**
         * Constructor for FilterTypeSetter
         *
         * @param setter
         *            Setter
         */
        public FilterTypeSetter(final BiConsumer<F, R> setter) {
            this(setter, null);
        }

        /**
         * Constructor for FilterTypeSetter
         *
         * @param setter
         *            Setter
         * @param defaultValue
         *            Filter default value
         */
        public FilterTypeSetter(final BiConsumer<F, R> setter, final R defaultValue) {
            this.setter = setter;
            this.defaultValue = defaultValue;
        }

        /**
         * @return Setter
         */
        public BiConsumer<F, R> getSetter() {
            return setter;
        }

        /**
         * Reset the filter value to default
         */
        public void restoreDefaultValue() {
            if (defaultValue != null) {
                setter.accept(entityFilter, defaultValue);
            }
        }
    }

    /**
     * Data provider with custom filter that can be set programmatically.
     *
     * @param <T>
     *            Data provider Proxy entity type
     * @param <F>
     *            Custom filter type
     */
    public static class CustomFilterDataProviderWrapper<T, F> extends DataProviderWrapper<T, Void, F>
            implements ConfigurableFilterDataProvider<T, Void, F> {
        private static final long serialVersionUID = 1L;

        private transient F customFilter;

        /**
         * Constructor.
         *
         * @param dataProvider
         *            the wrapped data provider
         */
        public CustomFilterDataProviderWrapper(final DataProvider<T, F> dataProvider) {
            super(dataProvider);
        }

        @Override
        protected F getFilter(final Query<T, Void> query) {
            // Filter of Void query is always null, so can be ignored
            return getFilter();
        }

        /**
         * Gets the custom filter.
         *
         * @return custom filter
         */
        public F getFilter() {
            return customFilter;
        }

        @Override
        public void setFilter(final F filter) {
            this.customFilter = filter;
            refreshAll();
        }

        /**
         * Gets original data provider.
         *
         * @return original data provider
         */
        public DataProvider<T, F> getDataProvider() {
            return dataProvider;
        }
    }
}
