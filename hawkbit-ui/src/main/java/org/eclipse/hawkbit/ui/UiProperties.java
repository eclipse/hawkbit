/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Properties for Management UI customization.
 *
 */
@ConfigurationProperties("hawkbit.server.ui")
public class UiProperties implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean gravatar;

    private String fixedTimeZone;

    private final Localization localization = new Localization();

    private final Links links = new Links();

    private final Login login = new Login();

    private final Demo demo = new Demo();

    private final Event event = new Event();

    public boolean isGravatar() {
        return gravatar;
    }

    public void setGravatar(final boolean gravatar) {
        this.gravatar = gravatar;
    }

    public String getFixedTimeZone() {
        return fixedTimeZone;
    }

    public void setFixedTimeZone(final String fixedTimeZone) {
        this.fixedTimeZone = fixedTimeZone;
    }

    /**
     * Localization information
     */
    public static class Localization implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * Default localization
         */
        private Locale defaultLocal = Locale.ENGLISH;

        /**
         * List of available localizations
         */
        private List<Locale> availableLocals = Collections.singletonList(Locale.ENGLISH);

        public Locale getDefaultLocal() {
            return defaultLocal;
        }

        public List<Locale> getAvailableLocals() {
            return availableLocals;
        }

        public void setDefaultLocal(final Locale defaultLocal) {
            this.defaultLocal = defaultLocal;
        }

        public void setAvailableLocals(final List<Locale> availableLocals) {
            this.availableLocals = availableLocals;
        }
    }

    /**
     * Demo account login information.
     *
     */
    public static class Demo implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * Demo tenant.
         */
        private String tenant = "";
        /**
         * Demo user name.
         */
        private String user = "";

        /**
         * Demo user password.
         */
        // Exception squid:S2068 - Empty password
        @SuppressWarnings({ "squid:S2068" })
        private String password = "";

        private String disclaimer = "";

        public String getPassword() {
            return password;
        }

        public String getTenant() {
            return tenant;
        }

        public String getUser() {
            return user;
        }

        public void setPassword(final String password) {
            this.password = password;
        }

        public void setTenant(final String tenant) {
            this.tenant = tenant;
        }

        public void setUser(final String user) {
            this.user = user;
        }

        public String getDisclaimer() {
            return disclaimer;
        }

        public void setDisclaimer(final String disclaimer) {
            this.disclaimer = disclaimer;
        }
    }

    /**
     * Links to potentially other systems (e.g. support, user management,
     * documentation etc.).
     *
     */
    public static class Links implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * Configuration of UI documentation links.
         *
         */
        public static class Documentation implements Serializable {
            private static final long serialVersionUID = 1L;
            /**
             * Link to root of documentation and user guides.
             */
            private String root = "";

            /**
             * Link to documentation of deployment view.
             */
            private String deploymentView = "";

            /**
             * Link to documentation of distribution view.
             */
            private String distributionView = "";

            /**
             * Link to documentation of upload view.
             */
            private String uploadView = "";

            /**
             * Link to documentation of maintenance window view.
             */
            private String maintenanceWindowView = "";

            /**
             * Link to documentation of system configuration view.
             */
            private String systemConfigurationView = "";

            /**
             * Link to security related documentation.
             */
            private String security = "";

            /**
             * Link to rollout related documentation.
             */
            private String rollout = "";

            /**
             * Link to multi assignments related documentation.
             */
            private String multiAssignments = "";

            /**
             * Link to target filter view.
             */
            private String targetfilterView = "";

            /**
             * Link to documentation of rollout view.
             */
            private String rolloutView = "";

            private String provisioningStateMachine = "";

            public String getDeploymentView() {
                return deploymentView;
            }

            public String getDistributionView() {
                return distributionView;
            }

            public String getRolloutView() {
                return rolloutView;
            }

            public String getRoot() {
                return root;
            }

            public String getSecurity() {
                return security;
            }

            public String getRollout() {
                return rollout;
            }

            public String getMultiAssignments() {
                return multiAssignments;
            }

            public String getSystemConfigurationView() {
                return systemConfigurationView;
            }

            public String getTargetfilterView() {
                return targetfilterView;
            }

            public String getUploadView() {
                return uploadView;
            }

            public String getMaintenanceWindowView() {
                return maintenanceWindowView;
            }

            public String getProvisioningStateMachine() {
                return provisioningStateMachine;
            }

            public void setDeploymentView(final String deploymentView) {
                this.deploymentView = deploymentView;
            }

            public void setDistributionView(final String distributionView) {
                this.distributionView = distributionView;
            }

            public void setRolloutView(final String rolloutView) {
                this.rolloutView = rolloutView;
            }

            public void setRoot(final String root) {
                this.root = root;
            }

            public void setSecurity(final String security) {
                this.security = security;
            }

            public void setRollout(final String rollout) {
                this.rollout = rollout;
            }

            public void setMultiAssignments(String multiAssignments) {
                this.multiAssignments = multiAssignments;
            }

            public void setSystemConfigurationView(final String systemConfigurationView) {
                this.systemConfigurationView = systemConfigurationView;
            }

            public void setTargetfilterView(final String targetfilterView) {
                this.targetfilterView = targetfilterView;
            }

            public void setUploadView(final String uploadView) {
                this.uploadView = uploadView;
            }

            public void setMaintenanceWindowView(final String maintenanceWindowView) {
                this.maintenanceWindowView = maintenanceWindowView;
            }

            public void setProvisioningStateMachine(final String provisioningStateMachine) {
                this.provisioningStateMachine = provisioningStateMachine;
            }

        }

        private final Documentation documentation = new Documentation();

        /**
         * Link to product support.
         */
        private String support = "";

        /**
         * Link to request a system account, access.
         */
        private String requestAccount = "";

        /**
         * Link to user management.
         */
        private String userManagement = "";

        public Documentation getDocumentation() {
            return documentation;
        }

        public String getRequestAccount() {
            return requestAccount;
        }

        public String getSupport() {
            return support;
        }

        public String getUserManagement() {
            return userManagement;
        }

        public void setRequestAccount(final String requestAccount) {
            this.requestAccount = requestAccount;
        }

        public void setSupport(final String support) {
            this.support = support;
        }

        public void setUserManagement(final String userManagement) {
            this.userManagement = userManagement;
        }

    }

    /**
     * Configuration of login view.
     *
     */
    public static class Login implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * Cookie configuration for login credential cookie.
         *
         */
        public static class Cookie implements Serializable {
            private static final long serialVersionUID = 1L;
            /**
             * Secure cookie enabled.
             */
            private boolean secure = true;

            public boolean isSecure() {
                return secure;
            }

            public void setSecure(final boolean secure) {
                this.secure = secure;
            }
        }

        private final Cookie cookie = new Cookie();

        public Cookie getCookie() {
            return cookie;
        }
    }

    /**
     * Configuration of the UI event bus.
     */
    public static class Event implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 
         * Configuration of the UI push.
         *
         */
        public static class Push implements Serializable {
            private static final long serialVersionUID = 1L;

            /**
             * The delay for the ui event forwarding.
             */
            private long delay = TimeUnit.SECONDS.toMillis(2);

            public long getDelay() {
                return delay;
            }

            public void setDelay(final long delay) {
                this.delay = delay;
            }
        }

        private final Push push = new Push();

        public Push getPush() {
            return push;
        }
    }

    public Demo getDemo() {
        return demo;
    }

    public Links getLinks() {
        return links;
    }

    public Login getLogin() {
        return login;
    }

    public Event getEvent() {
        return event;
    }

    public Localization getLocalization() {
        return localization;
    }

}
