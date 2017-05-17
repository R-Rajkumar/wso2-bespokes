package com.raj.gateway.bespokes;


import com.raj.gateway.bespokes.internal.RoleBasedApplicationRegistrationWorkflowExecutorServiceComponent;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.WorkflowResponse;
import org.wso2.carbon.apimgt.impl.dto.ApplicationRegistrationWorkflowDTO;
import org.wso2.carbon.apimgt.impl.dto.WorkflowDTO;
import org.wso2.carbon.apimgt.impl.workflow.AbstractApplicationRegistrationWorkflowExecutor;
import org.wso2.carbon.apimgt.impl.workflow.ApplicationRegistrationSimpleWorkflowExecutor;
import org.wso2.carbon.apimgt.impl.workflow.GeneralWorkflowResponse;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowException;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowStatus;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.api.UserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.utils.xml.StringUtils;

public class RoleBasedApplicationRegistrationWorkflowExecutor extends AbstractApplicationRegistrationWorkflowExecutor {
    private static final Log log = LogFactory.getLog(RoleBasedApplicationRegistrationWorkflowExecutor.class);

    private RealmService realmService;
    private String allowedRoles;

    public RoleBasedApplicationRegistrationWorkflowExecutor() {
    }

    public List<WorkflowDTO> getWorkflowDetails(String workflowStatus) throws WorkflowException {
        return null;
    }

    public WorkflowResponse execute(WorkflowDTO workflowDTO) throws WorkflowException {
        ApplicationRegistrationWorkflowDTO appRegDTO = (ApplicationRegistrationWorkflowDTO)workflowDTO;
        int tenantId = appRegDTO.getTenantId();
        String username = appRegDTO.getUserName();
        UserStoreManager userStoreManager;
        this.realmService = RoleBasedApplicationRegistrationWorkflowExecutorServiceComponent.getRealmService();
        boolean authorized = false;

        if(allowedRoles != null && !StringUtils.isEmpty(allowedRoles)) {
            if("*".equals(allowedRoles)) {
                authorized = true;
            } else {
                try {
                    List<String> allowedRolesList = Arrays.asList(allowedRoles.split("\\s*,\\s*"));
                    userStoreManager = this.realmService.getTenantUserRealm(tenantId).getUserStoreManager();
                    String[] roles = userStoreManager.getRoleListOfUser(username);
                    if (roles != null) {
                        for(String role : roles) {
                            if(allowedRolesList.contains(role)) {
                                authorized = true;
                                break;
                            }
                        }
                    }
                } catch (UserStoreException ex) {
                    log.error("Error while retrieving user roles.", ex);
                    throw new WorkflowException("Error while retrieving user roles.", ex);
                }
            }
        }

        if(!authorized) {
            super.execute(workflowDTO);
            workflowDTO.setStatus(WorkflowStatus.REJECTED);
            this.complete(workflowDTO);
            return new GeneralWorkflowResponse();
        } else {
            return (new ApplicationRegistrationSimpleWorkflowExecutor()).execute(workflowDTO);
        }
    }

    public WorkflowResponse complete(WorkflowDTO workFlowDTO) throws WorkflowException {
        return super.complete(workFlowDTO);
    }

    public String getAllowedRoles() {
        return allowedRoles;
    }
    public void setAllowedRoles(String allowedRoles) {
        this.allowedRoles = allowedRoles;
    }
}
