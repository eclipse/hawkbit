/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common.filterlayout;

import org.eclipse.hawkbit.ui.common.EventListenersAwareLayout;
import org.eclipse.hawkbit.ui.common.grid.header.AbstractGridHeader;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;

import com.vaadin.ui.Alignment;
import com.vaadin.ui.Component;
import com.vaadin.ui.ComponentContainer;
import com.vaadin.ui.VerticalLayout;

/**
 * Parent class for filter button layout.
 */
public abstract class AbstractFilterLayout extends VerticalLayout implements EventListenersAwareLayout {
    private static final long serialVersionUID = 1L;

    protected void buildLayout() {
        setWidth(SPUIDefinitions.FILTER_BY_TYPE_WIDTH, Unit.PIXELS);
        setStyleName("filter-btns-main-layout");
        setHeight(100.0F, Unit.PERCENTAGE);
        setSpacing(false);
        setMargin(false);

        final Component filterHeader = getFilterHeader();
        final ComponentContainer filterButtons = getFilterContent();
        // adding border
        filterButtons.addStyleName("filter-btns-layout");
        filterButtons.setSizeFull();

        addComponents(filterHeader, filterButtons);

        setComponentAlignment(filterHeader, Alignment.TOP_CENTER);
        setComponentAlignment(filterButtons, Alignment.TOP_CENTER);

        setExpandRatio(filterButtons, 1.0F);
    }

    protected abstract AbstractGridHeader getFilterHeader();

    protected abstract ComponentContainer getFilterContent();

    protected static VerticalLayout wrapFilterContent(final Component filterContent) {
        final VerticalLayout filterContentWrapper = new VerticalLayout();
        filterContentWrapper.setMargin(false);
        filterContentWrapper.setSpacing(false);

        filterContentWrapper.addComponent(filterContent);
        filterContentWrapper.setComponentAlignment(filterContent, Alignment.TOP_LEFT);
        filterContentWrapper.setExpandRatio(filterContent, 1.0F);

        return filterContentWrapper;
    }
}
