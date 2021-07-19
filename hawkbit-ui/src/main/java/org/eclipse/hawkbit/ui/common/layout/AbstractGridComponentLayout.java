/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common.layout;

import org.eclipse.hawkbit.ui.common.EventListenersAwareLayout;
import org.eclipse.hawkbit.ui.common.detailslayout.AbstractGridDetailsLayout;
import org.eclipse.hawkbit.ui.common.grid.AbstractGrid;
import org.eclipse.hawkbit.ui.common.grid.header.AbstractDetailsHeader;
import org.eclipse.hawkbit.ui.common.grid.header.AbstractGridHeader;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;

import com.vaadin.ui.Alignment;
import com.vaadin.ui.Component;
import com.vaadin.ui.Layout;
import com.vaadin.ui.VerticalLayout;

/**
 * Abstract grid layout class which builds layout with grid header
 * {@link AbstractGridHeader}, grid {@link AbstractGrid}, optional grid details
 * {@link AbstractGridDetailsLayout} and optional footer.
 */
public abstract class AbstractGridComponentLayout extends VerticalLayout implements EventListenersAwareLayout {
    private static final long serialVersionUID = 1L;

    private Component detailsLayout;

    /**
     * Constructor.
     */
    protected AbstractGridComponentLayout() {
        init();
    }

    protected void init() {
        setSizeFull();
        setSpacing(true);
        setMargin(false);
        setStyleName("group");
    }

    /**
     * Initializes this layout that presents a header and a grid.
     */
    protected void buildLayout(final AbstractGridHeader gridHeader, final AbstractGrid<?, ?> grid) {
        final VerticalLayout gridHeaderLayout = new VerticalLayout();
        gridHeaderLayout.setSizeFull();
        gridHeaderLayout.setSpacing(false);
        gridHeaderLayout.setMargin(false);

        gridHeaderLayout.setStyleName("table-layout");

        gridHeaderLayout.addComponent(gridHeader);
        gridHeaderLayout.setComponentAlignment(gridHeader, Alignment.TOP_CENTER);

        gridHeaderLayout.addComponent(grid);
        gridHeaderLayout.setComponentAlignment(grid, Alignment.TOP_CENTER);
        gridHeaderLayout.setExpandRatio(grid, 1.0F);

        addComponent(gridHeaderLayout);
        setComponentAlignment(gridHeaderLayout, Alignment.TOP_CENTER);
        setExpandRatio(gridHeaderLayout, 1.0F);
    }

    /**
     * Initializes this layout that presents a header, a grid, details header
     * and grid details.
     */
    protected void buildLayout(final AbstractGridHeader gridHeader, final AbstractGrid<?, ?> grid,
            final Component detailsLayout) {
        buildLayout(gridHeader, grid);

        addComponent(detailsLayout);
        setComponentAlignment(detailsLayout, Alignment.TOP_CENTER);

        this.detailsLayout = detailsLayout;
    }

    /**
     * Initializes this layout that presents a header, a grid, details header
     * and grid details.
     */
    protected void buildLayout(final AbstractGridHeader gridHeader, final AbstractGrid<?, ?> grid,
            final AbstractDetailsHeader<?> gridDetailsHeader, final AbstractGridDetailsLayout<?> gridDetailsLayout) {
        final VerticalLayout detailsHeaderLayout = new VerticalLayout();
        detailsHeaderLayout.setSizeFull();
        detailsHeaderLayout.setSpacing(false);
        detailsHeaderLayout.setMargin(false);
        detailsHeaderLayout.setHeightUndefined();
        detailsHeaderLayout.addStyleName(SPUIStyleDefinitions.WIDGET_STYLE);

        detailsHeaderLayout.addComponent(gridDetailsHeader);
        detailsHeaderLayout.setComponentAlignment(gridDetailsHeader, Alignment.TOP_CENTER);

        detailsHeaderLayout.addComponent(gridDetailsLayout);
        detailsHeaderLayout.setComponentAlignment(gridDetailsLayout, Alignment.TOP_CENTER);
        detailsHeaderLayout.setExpandRatio(gridDetailsLayout, 1.0F);

        buildLayout(gridHeader, grid, detailsHeaderLayout);
    }

    /**
     * Initializes this layout that presents a header, a grid and a footer.
     */
    protected void buildLayout(final AbstractGridHeader gridHeader, final AbstractGrid<?, ?> grid,
            final AbstractFooterSupport footerSupport) {
        buildLayout(gridHeader, grid);

        final Layout footerLayout = footerSupport.createFooterMessageComponent();
        addComponent(footerLayout);
        setComponentAlignment(footerLayout, Alignment.BOTTOM_CENTER);
    }

    /**
     * Initializes this layout that presents a header, a grid, grid details and
     * a footer.
     */
    protected void buildLayout(final AbstractGridHeader gridHeader, final AbstractGrid<?, ?> grid,
            final AbstractDetailsHeader<?> gridDetailsHeader, final AbstractGridDetailsLayout<?> gridDetailsLayout,
            final AbstractFooterSupport footerSupport) {
        buildLayout(gridHeader, grid, gridDetailsHeader, gridDetailsLayout);

        final Layout footerLayout = footerSupport.createFooterMessageComponent();
        addComponent(footerLayout);
        setComponentAlignment(footerLayout, Alignment.BOTTOM_CENTER);
    }

    protected void showDetailsLayout() {
        if (detailsLayout != null) {
            detailsLayout.setVisible(true);
        }
    }

    protected void hideDetailsLayout() {
        if (detailsLayout != null) {
            detailsLayout.setVisible(false);
        }
    }
}
