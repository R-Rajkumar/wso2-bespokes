package com.raj.gateway.bespokes.internal;

import com.raj.gateway.bespokes.RoleBasedApplicationRegistrationWorkflowExecutor;

import java.util.Dictionary;
import org.osgi.service.component.ComponentContext;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowExecutor;
import org.wso2.carbon.user.core.service.RealmService;

/**
 * @scr.component name="RoleBasedApplicationRegistrationWorkflowExecutorServiceComponent" immediate="true"
 * @scr.reference name="realm.service" interface="org.wso2.carbon.user.core.service.RealmService"
 * cardinality="1..1" policy="dynamic" bind="setRealmService" unbind="unsetRealmService"
 */
public class RoleBasedApplicationRegistrationWorkflowExecutorServiceComponent {
    private static RealmService realmService;

    public RoleBasedApplicationRegistrationWorkflowExecutorServiceComponent() {
    }

    public static RealmService getRealmService() {
        return realmService;
    }

    protected void setRealmService(RealmService realmService) {
        this.realmService = realmService;
    }

    protected void activate(ComponentContext context) {
        RoleBasedApplicationRegistrationWorkflowExecutor
                roleBasedApplicationRegistrationWorkflowExecutor = new RoleBasedApplicationRegistrationWorkflowExecutor();
        context.getBundleContext().registerService(WorkflowExecutor.class.getName(),
                roleBasedApplicationRegistrationWorkflowExecutor, (Dictionary)null);
    }

    protected void deactivate(ComponentContext context) {
    }

    protected void unsetRealmService(RealmService realmService) {
        this.realmService = null;
    }
}
