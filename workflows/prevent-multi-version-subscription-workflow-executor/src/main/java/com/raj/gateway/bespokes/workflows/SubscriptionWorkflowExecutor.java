package com.raj.gateway.bespokes.workflows;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIConsumer;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.WorkflowResponse;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.api.model.SubscribedAPI;
import org.wso2.carbon.apimgt.api.model.Subscriber;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.APIManagerFactory;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.dto.SubscriptionWorkflowDTO;
import org.wso2.carbon.apimgt.impl.dto.WorkflowDTO;
import org.wso2.carbon.apimgt.impl.workflow.GeneralWorkflowResponse;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowException;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowExecutor;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowStatus;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class SubscriptionWorkflowExecutor extends WorkflowExecutor {

    private static final Log log = LogFactory.getLog(SubscriptionWorkflowExecutor.class);

    public SubscriptionWorkflowExecutor() {
    }

    @Override
    public String getWorkflowType() {
        return "AM_SUBSCRIPTION_CREATION";
    }

    @Override
    public List<WorkflowDTO> getWorkflowDetails(String workflowStatus) throws WorkflowException {
        return null;
    }

    @Override
    public WorkflowResponse execute(WorkflowDTO workflowDTO) throws WorkflowException {

        SubscriptionWorkflowDTO subscriptionWorkflowDTO = (SubscriptionWorkflowDTO)workflowDTO;

        if (log.isDebugEnabled()) {
            log.debug(String.format("[SubscriptionWorkflowDTO : (apiName : %s) (apiVersion : %s) (apiContext : %s) " +
                    "(apiProvider : %s) (subscriber : %s) (applicationName : %s) (tierName : %s)]",
                    subscriptionWorkflowDTO.getApiName(), subscriptionWorkflowDTO.getApiVersion(),
                    subscriptionWorkflowDTO.getApiContext(), subscriptionWorkflowDTO.getApiProvider(),
                    subscriptionWorkflowDTO.getSubscriber(), subscriptionWorkflowDTO.getApplicationName(),
                    subscriptionWorkflowDTO.getTierName()));
        }

        String subscriber = subscriptionWorkflowDTO.getSubscriber();
        APIConsumer apiConsumer;
        Set<SubscribedAPI> subscriptions;
        try {
            apiConsumer = APIManagerFactory.getInstance().getAPIConsumer(subscriber);
            subscriptions = apiConsumer.getSubscribedAPIs(new Subscriber(subscriber), subscriptionWorkflowDTO.getApplicationName(),
                    null);
        } catch (APIManagementException e) {
            log.error("Error while retrieving subscribed APIs.", e);
            throw new WorkflowException("Error while retrieving subscribed APIs.", e);
        }

        Iterator<SubscribedAPI> itr = subscriptions.iterator();
        boolean alreadySubscribed = false;
        APIIdentifier apiIdentifier = null;
        while(itr.hasNext()){
            SubscribedAPI subscribedAPI = itr.next();
            apiIdentifier = subscribedAPI.getApiId();

            if (log.isDebugEnabled()) {
                log.debug(String.format("[SubscribedAPI : (providerName : %s) (apiName : %s) (version : %s) " +
                        "(tier : %s) (applicationId : %s)]", apiIdentifier.getProviderName(), apiIdentifier.getApiName(),
                        apiIdentifier.getVersion(), apiIdentifier.getTier(), apiIdentifier.getApplicationId()));
            }
            if (apiIdentifier.getApiName().equals(subscriptionWorkflowDTO.getApiName())
                    && !apiIdentifier.getVersion().equals(subscriptionWorkflowDTO.getApiVersion())
                    && !subscribedAPI.getSubStatus().equals(APIConstants.SubscriptionStatus.REJECTED)) {
                alreadySubscribed = true;
                break;
            }
        }

        if (alreadySubscribed) {
            if (log.isInfoEnabled()) {
                log.info(String.format("User %s has already subscribed to %s %s using application %s", subscriber,
                        apiIdentifier.getApiName(), apiIdentifier.getVersion(),
                        subscriptionWorkflowDTO.getApplicationName()));
            }
            workflowDTO.setStatus(WorkflowStatus.REJECTED);
        } else {
            if (log.isInfoEnabled()) {
                log.info(String.format("User %s haven't subscribed to %s using application %s", subscriber,
                        subscriptionWorkflowDTO.getApiName(), subscriptionWorkflowDTO.getApplicationName()));
            }
            workflowDTO.setStatus(WorkflowStatus.APPROVED);
        }

        return this.complete(workflowDTO);
    }

    @Override
    public WorkflowResponse complete(WorkflowDTO workflowDTO) throws WorkflowException {

        workflowDTO.setUpdatedTime(System.currentTimeMillis());

        log.info("Subscription Creation [Complete] Workflow Invoked. Workflow ID : " + workflowDTO
                .getExternalWorkflowReference() + "Workflow State : " + workflowDTO.getStatus());

        if (WorkflowStatus.APPROVED.equals(workflowDTO.getStatus())) {
            ApiMgtDAO apiMgtDAO = new ApiMgtDAO();
            try {
                apiMgtDAO.updateSubscriptionStatus(Integer.parseInt(workflowDTO.getWorkflowReference()),
                        APIConstants.SubscriptionStatus.UNBLOCKED);
            } catch (APIManagementException e) {
                log.error("Could not complete subscription creation workflow", e);
                throw new WorkflowException("Could not complete subscription creation workflow", e);
            }
        } else if (WorkflowStatus.REJECTED.equals(workflowDTO.getStatus())) {
            ApiMgtDAO apiMgtDAO = new ApiMgtDAO();
            try {
                apiMgtDAO.updateSubscriptionStatus(Integer.parseInt(workflowDTO.getWorkflowReference()),
                        APIConstants.SubscriptionStatus.REJECTED);
                throw new WorkflowException("Could not complete subscription creation workflow");
            } catch (APIManagementException e) {
                log.error("Could not complete subscription creation workflow", e);
                throw new WorkflowException("Could not complete subscription creation workflow", e);
            }
        }

        return new GeneralWorkflowResponse();
    }
}
