package com.raj.gateway.bespokes.cache;

import com.tangosol.net.CacheFactory;
import com.tangosol.net.NamedCache;
import com.raj.gateway.bespokes.cache.digest.DigestGenerator;
import com.raj.gateway.bespokes.cache.util.RequestHash;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axiom.soap.SOAPHeader;
import org.apache.axiom.soap.SOAPHeaderBlock;
import org.apache.axis2.Constants;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.OperationContext;
import org.apache.synapse.ManagedLifecycle;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.SynapseLog;
import org.apache.synapse.continuation.ContinuationStackManager;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.core.axis2.Axis2Sender;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.util.FixedByteArrayOutputStream;
import org.apache.synapse.util.MessageHelper;

import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class CoherenceCacheMediator extends AbstractMediator implements ManagedLifecycle {

    /**
     * Cache configuration ID.
     */
    private String id = null;

    /**
     * Oracle coherence cache name
     */
    private String coherenceCacheName = null;

    /**
     * The SequenceMediator to the onCacheHit sequence to be executed when an incoming message is identified as an
     * equivalent to a previously received message based on the value defined for the Hash Generator field.
     */
    private SequenceMediator onCacheHitSequence = null;

    /**
     * The reference to the onCacheHit sequence to be executed when an incoming message is identified as an
     * equivalent to a previously received message based on the value defined for the Hash Generator field.
     */
    private String onCacheHitRef = null;

    /**
     * The maximum size of the messages to be cached. This is specified in bytes.
     */
    private int maxMessageSize = 0;

    /**
     * This specifies whether the mediator should be in the incoming path (to check the request) or in the outgoing
     * path (to cache the response).
     */
    private boolean collector = false;

    /**
     * This is used to define the logic used by the mediator to evaluate the hash values of incoming messages.
     */
    private DigestGenerator digestGenerator = CoherenceCachingConstants.DEFAULT_XML_IDENTIFIER;

    public void init(SynapseEnvironment synapseEnvironment) {
        if (onCacheHitSequence != null) {
            onCacheHitSequence.init(synapseEnvironment);
        }
    }

    public void destroy() {
        if (onCacheHitSequence != null) {
            onCacheHitSequence.destroy();
        }
    }

    @Override
    public boolean isContentAware() {
        return true;
    }

    public boolean mediate(MessageContext synCtx) {

        if (synCtx.getEnvironment().isDebugEnabled()) {
            if (super.divertMediationRoute(synCtx)) {
                return true;
            }
        }

        SynapseLog synLog = getLog(synCtx);

        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("Start : Coherence Cache mediator");

            if (synLog.isTraceTraceEnabled()) {
                synLog.traceTrace("Message : " + synCtx.getEnvelope());
            }
        }

        // if maxMessageSize is specified check for the message size before processing
        if (synCtx.isResponse() && maxMessageSize > 0) {
            FixedByteArrayOutputStream fbaos = new FixedByteArrayOutputStream(maxMessageSize);
            try {
                MessageHelper.cloneSOAPEnvelope(synCtx.getEnvelope()).serialize(fbaos);
            } catch (XMLStreamException e) {
                handleException("Error in checking the message size", e, synCtx);
            } catch (SynapseException syne) {
                synLog.traceOrDebug("Message size exceeds the upper bound for caching, response will not be cached");
                return true;
            } finally {
                try {
                    fbaos.close();
                } catch (IOException e) {
                    handleException("Error occurred while closing the FixedByteArrayOutputStream ", e, synCtx);
                }
            }
        }

        ConfigurationContext cfgCtx = ((Axis2MessageContext) synCtx).getAxis2MessageContext().getConfigurationContext();

        if (cfgCtx == null) {
            handleException("Unable to perform caching,  ConfigurationContext cannot be found", synCtx);
            return false;
        }

        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("Looking up cache with name : " + coherenceCacheName);
        }

        boolean result = true;
        if (synCtx.isResponse()) {
            processResponseMessage(synCtx, synLog);
        } else {
            result = processRequestMessage(synCtx, synLog);
        }

        synLog.traceOrDebug("End : Cache Coherence mediator");

        return result;
    }

    /**
     * Processes a request message through the cache mediator. Generates the request hash and looks
     * up for a hit, if found; then the specified named or anonymous sequence is executed or marks
     * this message as a response and sends back directly to client.
     *
     * @param synCtx incoming request message
     * @param synLog the Synapse log to use
     * @return should this mediator terminate further processing?
     */
    private boolean processRequestMessage(MessageContext synCtx, SynapseLog synLog) {
        if (collector) {
            handleException("Request messages cannot be handled in a collector cache", synCtx);
        }

        OperationContext opCtx = ((Axis2MessageContext) synCtx).getAxis2MessageContext().getOperationContext();
        String requestHash = null;

        try {
            requestHash = digestGenerator.getDigest(((Axis2MessageContext) synCtx).getAxis2MessageContext());
            synCtx.setProperty(CoherenceCachingConstants.REQUEST_HASH, requestHash);
        } catch (CoherenceCachingException e) {
            handleException("Error in calculating the hash value of the request", e, synCtx);
        }

        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("Generated request hash : " + requestHash);
        }

        RequestHash hash = new RequestHash(requestHash);

        CoherenceCacheableResponse cachedResponse = null;
        try {
            if (getMediatorCache() != null) {
                try {
                    cachedResponse = (CoherenceCacheableResponse) getMediatorCache().get(requestHash);
                } catch (ClassCastException ex) {
                    synLog.auditWarn("Unable to cast the cached response retrieved from the cache : "
                            + coherenceCacheName + " : " + ex.getMessage());
                } catch (Exception ex) {
                    synLog.auditWarn("Unable to get the cache "
                            + coherenceCacheName + " from oracle coherence. " + ex.getMessage());
                }
            } else {
                synLog.auditWarn("Unable to get the cache "
                        + coherenceCacheName + " from oracle coherence. Skipping caching");
            }
        } catch (CoherenceCachingException ex) {
            synLog.auditWarn("Unable to get the cache "
                    + coherenceCacheName + " from oracle coherence. Skipping caching. " + ex.getMessage());
        }


        org.apache.axis2.context.MessageContext msgCtx = ((Axis2MessageContext) synCtx).getAxis2MessageContext();
        opCtx.setProperty(CoherenceCachingConstants.REQUEST_HASH, requestHash);

        Map<String, Object> headerProperties;
        if (cachedResponse != null && (cachedResponse.getResponseEnvelope()) != null) {
            // get the response from the cache and attach to the context and change the
            // direction of the message
            if (synLog.isTraceOrDebugEnabled()) {
                synLog.traceOrDebug("Cache-hit for message ID : " + synCtx.getMessageID());
            }
            // mark as a response and replace envelope from cache
            synCtx.setResponse(true);
            opCtx.setProperty(CoherenceCachingConstants.CACHED_OBJECT, cachedResponse);

            OMElement response = null;
            try {
                if (msgCtx.isDoingREST()) {
                    if ((headerProperties = cachedResponse.getHeaderProperties()) != null) {
                        String replacementValue = new String(cachedResponse.getResponseEnvelope());
                        try {
                            response = AXIOMUtil.stringToOM(replacementValue);
                        } catch (XMLStreamException e) {
                            handleException("Error creating response OM from cache : " + coherenceCacheName, synCtx);
                        }
                        msgCtx.removeProperty("NO_ENTITY_BODY");
                        msgCtx.removeProperty(Constants.Configuration.CONTENT_TYPE);
                        msgCtx.setProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS, headerProperties);
                        msgCtx.setProperty(Constants.Configuration.MESSAGE_TYPE,
                                headerProperties.get(Constants.Configuration.MESSAGE_TYPE));
                    }
                } else {
                    String replacementValue = new String(cachedResponse.getResponseEnvelope());
                    try {
                        response = AXIOMUtil.stringToOM(replacementValue);
                    } catch (XMLStreamException e) {
                        handleException("Error creating response OM from cache : " + coherenceCacheName, synCtx);
                    }
                }

                if (response != null) {
                    // Set the headers of the message
                    if (response.getFirstElement().getLocalName().contains("Header")) {
                        Iterator childElements = msgCtx.getEnvelope().getHeader().getChildElements();
                        while (childElements.hasNext()) {
                            ((OMElement) childElements.next()).detach();
                        }
                        SOAPEnvelope env = synCtx.getEnvelope();
                        SOAPHeader header = env.getHeader();
                        SOAPFactory fac = (SOAPFactory) env.getOMFactory();

                        Iterator headers = response.getFirstElement().getChildElements();
                        while (headers.hasNext()) {
                            OMElement soapHeader = (OMElement) headers.next();
                            SOAPHeaderBlock hb = header.addHeaderBlock(soapHeader.getLocalName(),
                                    fac.createOMNamespace(soapHeader.getNamespace().getNamespaceURI(),
                                            soapHeader.getNamespace().getPrefix()));
                            hb.setText(soapHeader.getText());
                        }
                        response.getFirstElement().detach();
                    }
                    // Set the body of the message
                    if (msgCtx.getEnvelope().getBody().getFirstElement() != null) {
                        msgCtx.getEnvelope().getBody().getFirstElement().detach();
                    }
                    msgCtx.getEnvelope().getBody().addChild(response.getFirstElement().getFirstElement());
                }
            } catch (Exception ex) {
                handleException("Error setting response envelope from cache : "
                        + coherenceCacheName, synCtx);
            }

            // take specified action on cache hit
            if (onCacheHitSequence != null) {
                // if there is an onCacheHit use that for the mediation
                synLog.traceOrDebug("Delegating message to the onCachingHit "
                        + "Anonymous sequence");
                ContinuationStackManager.addReliantContinuationState(synCtx, 0, getMediatorPosition());
                if (onCacheHitSequence.mediate(synCtx)) {
                    ContinuationStackManager.removeReliantContinuationState(synCtx);
                }

            } else if (onCacheHitRef != null) {
                if (synLog.isTraceOrDebugEnabled()) {
                    synLog.traceOrDebug("Delegating message to the onCachingHit " +
                            "sequence : " + onCacheHitRef);
                }
                ContinuationStackManager.updateSeqContinuationState(synCtx, getMediatorPosition());
                synCtx.getSequence(onCacheHitRef).mediate(synCtx);

            } else {
                if (synLog.isTraceOrDebugEnabled()) {
                    synLog.traceOrDebug("Request message " + synCtx.getMessageID() +
                            " was served from the cache : " + coherenceCacheName);
                }
                // send the response back if there is not onCacheHit is specified
                synCtx.setTo(null);
                Axis2Sender.sendBack(synCtx);

            }
            // stop any following mediators from executing
            return false;
        } else {
            cacheNewResponse(msgCtx, hash, synLog);
        }

        return true;
    }


    /**
     * Process a response message through this cache mediator. This finds the Cache used, and
     * updates it for the corresponding request hash
     *
     * @param synLog the Synapse log to use
     * @param synCtx the current message (response)
     */
    private void processResponseMessage(MessageContext synCtx, SynapseLog synLog) {
        if (!collector) {
            handleException("Response messages cannot be handled in a non collector cache", synCtx);
        }

        org.apache.axis2.context.MessageContext msgCtx = ((Axis2MessageContext) synCtx).getAxis2MessageContext();
        OperationContext operationContext = msgCtx.getOperationContext();
        CoherenceCacheableResponse response =
                (CoherenceCacheableResponse) operationContext.getProperty(CoherenceCachingConstants.CACHED_OBJECT);

        if (response != null) {
            if (synLog.isTraceOrDebugEnabled()) {
                synLog.traceOrDebug("Storing the response message into the cache with name : "
                        + coherenceCacheName + " for request hash : " + response.getRequestHash());
            }
            if (synLog.isTraceOrDebugEnabled()) {
                synLog.traceOrDebug("Storing the response for the message with ID : " + synCtx.getMessageID() + " " +
                        "with request hash ID : " + response.getRequestHash() + " in the cache : " +
                        coherenceCacheName);
            }

            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            try {
                synCtx.getEnvelope().serialize(outStream);
                response.setResponseEnvelope(outStream.toByteArray());
                if (msgCtx.isDoingREST()) {
                    Map<String, String> headers =
                            (Map) msgCtx.getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
                    String messageType = (String) msgCtx.getProperty(Constants.Configuration.MESSAGE_TYPE);
                    Map<String, Object> headerProperties = new HashMap<String, Object>();
                    headerProperties.putAll(headers);
                    headerProperties.put(Constants.Configuration.MESSAGE_TYPE, messageType);
                    response.setHeaderProperties(headerProperties);
                }

            } catch (XMLStreamException e) {
                handleException("Unable to set the response to the Cache", e, synCtx);
            } finally {
                try {
                    outStream.close();
                } catch (IOException e) {
                    handleException("Error occurred while closing the FixedByteArrayOutputStream ", e, synCtx);
                }
            }

            try {
                if (getMediatorCache() != null) {
                    getMediatorCache().put(response.getRequestHash(), response);
                } else {
                    synLog.auditWarn("Unable to get the cache "
                            + coherenceCacheName + " from oracle coherence. Skipping caching.");
                }
            } catch (CoherenceCachingException ex) {
                synLog.auditWarn("Unable to get the cache "
                        + coherenceCacheName + " from oracle coherence. Skipping caching. " + ex.getMessage());
            }

        } else {
            synLog.auditWarn("A response message without a valid mapping to the " +
                    "request hash found. Unable to store the response in cache");
        }
    }

    /**
     * Caches the CoherenceCacheableResponse object with currently available attributes against the requestHash
     *
     * @param msgContext  axis2 message context of the request message
     * @param requestHash the request hash that has already been computed
     */
    private void cacheNewResponse(org.apache.axis2.context.MessageContext msgContext, RequestHash requestHash,
                                  SynapseLog synLog) {
        try {
            if (getMediatorCache() != null) {
                OperationContext opCtx = msgContext.getOperationContext();
                CoherenceCacheableResponse response = new CoherenceCacheableResponse();
                response.setRequestHash(requestHash.getRequestHash());
                getMediatorCache().put(requestHash.getRequestHash(), response);
                opCtx.setProperty(CoherenceCachingConstants.CACHED_OBJECT, response);
            } else {
                synLog.auditWarn("Unable to get the cache "
                        + coherenceCacheName + " from oracle coherence. Skipping caching.");
            }
        } catch (CoherenceCachingException ex) {
            synLog.auditWarn("Unable to get the cache "
                    + coherenceCacheName + " from oracle coherence. Skipping caching. " + ex.getMessage());
        }
    }

    private NamedCache getMediatorCache() {
        NamedCache cache;
        try {
            cache = CacheFactory.getCache(coherenceCacheName);
        } catch (Throwable ex) {
            throw new CoherenceCachingException("Unable to get the cache "
                    + coherenceCacheName + " from oracle coherence", ex);
        }

        return cache;
    }

    public SequenceMediator getOnCacheHitSequence() {
        return onCacheHitSequence;
    }

    public void setOnCacheHitSequence(SequenceMediator onCacheHitSequence) {
        this.onCacheHitSequence = onCacheHitSequence;
    }

    public String getOnCacheHitRef() {
        return onCacheHitRef;
    }

    public void setOnCacheHitRef(String onCacheHitRef) {
        this.onCacheHitRef = onCacheHitRef;
    }

    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    public void setMaxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    public boolean isCollector() {
        return collector;
    }

    public void setCollector(boolean collector) {
        this.collector = collector;
    }

    public DigestGenerator getDigestGenerator() {
        return digestGenerator;
    }

    public void setDigestGenerator(DigestGenerator digestGenerator) {
        this.digestGenerator = digestGenerator;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCoherenceCacheName() {
        return coherenceCacheName;
    }

    public void setCoherenceCacheName(String coherenceCacheName) {
        this.coherenceCacheName = coherenceCacheName;
    }
}
