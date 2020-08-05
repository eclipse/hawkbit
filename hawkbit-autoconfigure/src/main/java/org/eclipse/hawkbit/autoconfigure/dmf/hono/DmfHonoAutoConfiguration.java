/**
 * Copyright (c) 2019 Kiwigrid GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.autoconfigure.dmf.hono;

import org.eclipse.hawkbit.dmf.hono.DmfHonoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * The Eclipse Hono based device Management Federation API (DMF) auto configuration.
 */
@Configuration
@ConditionalOnClass(DmfHonoConfiguration.class)
@Import(DmfHonoConfiguration.class)
public class DmfHonoAutoConfiguration {
}