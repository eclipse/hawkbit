/** 
 * Copyright (c) 2020 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common.layout;

import com.vaadin.server.Sizeable.Unit;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Layout;

/**
 * If footer support is enabled, the footer is placed below the component
 */
public abstract class AbstractFooterSupport {

    /**
     * Creates a sub-layout for the footer.
     *
     * @return the footer sub-layout.
     */
    public Layout createFooterMessageComponent() {
        final HorizontalLayout footerLayout = new HorizontalLayout();
        footerLayout.setMargin(false);
        footerLayout.setSpacing(false);
        footerLayout.setWidth(100, Unit.PERCENTAGE);

        footerLayout.addComponent(getFooterMessageLabel());

        return footerLayout;
    }

    /**
     * Get the footer message label.
     *
     * @return footer message
     */
    protected abstract Label getFooterMessageLabel();
}