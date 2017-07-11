/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.mediator.cache.json;

import org.apache.axis2.clustering.ClusteringFault;
import org.apache.axis2.clustering.state.Replicator;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.OperationContext;
import org.apache.synapse.ManagedLifecycle;
import org.apache.synapse.Mediator;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseLog;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.continuation.ContinuationStackManager;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.core.axis2.Axis2Sender;
import org.apache.synapse.debug.constructs.EnclosedInlinedSequence;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.wso2.carbon.mediator.cache.json.util.RequestHash;
import org.wso2.carbon.mediator.cache.json.digest.DigestGenerator;

import javax.cache.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CacheMediator will cache the response messages indexed using the hash value of the request message,
 * and subsequent messages with the same request (request hash will be generated and checked for the equality) within
 * the cache expiration period will be served from the stored responses in the cache
 *
 * @see Mediator
 */
public class CacheMediator extends AbstractMediator implements ManagedLifecycle, EnclosedInlinedSequence {

	/**
	 * Cache configuration ID.
	 */
	private String id = null;

	/**
	 * The scope of the cache
	 */
	private String scope = CachingConstants.SCOPE_PER_HOST;

	/**
	 * This specifies whether the mediator should be in the incoming path (to check the request) or in the outgoing
	 * path (to cache the response).
	 */
	private boolean collector = false;

	/**
	 * This is used to define the logic used by the mediator to evaluate the hash values of incoming messages.
	 */
	private DigestGenerator digestGenerator = CachingConstants.DEFAULT_XML_IDENTIFIER;

	/**
	 * The size of the messages to be cached in memory. If this is 0 then no disk cache,
	 * and if there is no size specified in the config  factory will asign a default value to enable disk based caching.
	 */
	private int inMemoryCacheSize = CachingConstants.DEFAULT_CACHE_SIZE;

	/**
	 * The size of the messages to be cached in memory. Disk based and hirearchycal caching is not implemented yet.
	 */
	private int diskCacheSize = 0;

	/**
	 * The time duration for which the cache is kept.
	 */
	private long timeout = 0L;

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
	 * The flag to continue or terminate the flow on cache hit.
	 */
	private boolean continueExecution = true;

	/**
	 * The maximum size of the messages to be cached. This is specified in bytes.
	 */
	private int maxMessageSize = 0;

	/**
	 * Prefix of the cache key
	 */
	private static final String CACHE_KEY_PREFIX = "mediation.cache_key_";

	/**
	 * Key to use in cache configuration
	 */
	private String cacheKey = "mediation.cache_key";

	/**
	 * This holds whether the global cache already initialized or not.
	 */
	private static AtomicBoolean mediatorCacheInit = new AtomicBoolean(false);

	@Override
	public void init(SynapseEnvironment se) {
		if (onCacheHitSequence != null) {
			onCacheHitSequence.init(se);
		}
	}

	@Override
	public void destroy() {
		if (onCacheHitSequence != null) {
			onCacheHitSequence.destroy();
		}
	}

	@Override
	public boolean isContentAware() {
		return true;
	}

	@Override
	public boolean mediate(MessageContext synCtx) {

		if (synCtx.getEnvironment().isDebuggerEnabled()) {
			if (super.divertMediationRoute(synCtx)) {
				return true;
			}
		}

		SynapseLog synLog = getLog(synCtx);

		if (synLog.isTraceOrDebugEnabled()) {
			synLog.traceOrDebug("Start : Cache mediator");

			if (synLog.isTraceTraceEnabled()) {
				synLog.traceTrace("Message : " + synCtx.getEnvelope());
			}
		}

		// if maxMessageSize is specified check for the message size before processing
		if (maxMessageSize > 0) {
			// need to check request's size is exceeding maxMessageSize and skip caching if so
		}

		ConfigurationContext cfgCtx = ((Axis2MessageContext) synCtx).getAxis2MessageContext().getConfigurationContext();

		if (cfgCtx == null) {
			handleException("Unable to perform caching,  ConfigurationContext cannot be found", synCtx);
			return false; // never executes.. but keeps IDE happy
		}

		if (synLog.isTraceOrDebugEnabled()) {
			synLog.traceOrDebug("Looking up cache at scope : " + scope + " with ID : " + cacheKey);
		}

		boolean result = true;
		try {
			if (synCtx.isResponse()) {
				processResponseMessage(synCtx, cfgCtx, synLog);

			} else {
				result = processRequestMessage(synCtx, synLog);
			}

		} catch (ClusteringFault clusteringFault) {
			synLog.traceOrDebug("Unable to replicate Cache mediator state among the cluster");
		}

		synLog.traceOrDebug("End : Cache mediator");

		return result;
	}

	/**
	 * Process a response message through this cache mediator. This finds the Cache used, and
	 * updates it for the corresponding request hash
	 *
	 * @param synLog the Synapse log to use
	 * @param synCtx the current message (response)
	 * @param cfgCtx the abstract context in which the cache will be kept
	 * @throws ClusteringFault is there is an error in replicating the cfgCtx
	 */
	private void processResponseMessage(MessageContext synCtx, ConfigurationContext cfgCtx,
	                                    SynapseLog synLog) throws ClusteringFault {

		if (!collector) {
			handleException("Response messages cannot be handled in a non collector cache", synCtx);
		}
		org.apache.axis2.context.MessageContext msgCtx = ((Axis2MessageContext) synCtx).getAxis2MessageContext();
		OperationContext operationContext = msgCtx.getOperationContext();
		CachableResponse response = (CachableResponse) operationContext.getProperty(CachingConstants.CACHED_OBJECT);

		if (response != null) {
			if (synLog.isTraceOrDebugEnabled()) {
				synLog.traceOrDebug("Storing the response message into the cache at scope : " + scope + " with ID : "
				                    + cacheKey + " for request hash : " + response.getRequestHash());
			}
			if (synLog.isTraceOrDebugEnabled()) {
				synLog.traceOrDebug("Storing the response for the message with ID : " + synCtx.getMessageID() + " " +
				                    "with request hash ID : " + response.getRequestHash() + " in the cache : " +
				                    cacheKey);
			}

			// get response string and header properties and set them to CachableResponse

			if (response.getTimeout() > 0) {
				response.setExpireTimeMillis(System.currentTimeMillis() + response.getTimeout());
			}

			getMediatorCache().put(response.getRequestHash(), response);
			// Finally, we may need to replicate the changes in the cache
			Replicator.replicate(cfgCtx);
		} else {
			synLog.auditWarn("A response message without a valid mapping to the " +
			                 "request hash found. Unable to store the response in cache");
		}

	}

	/**
	 * Processes a request message through the cache mediator. Generates the request hash and looks
	 * up for a hit, if found; then the specified named or anonymous sequence is executed or marks
	 * this message as a response and sends back directly to client.
	 *
	 * @param synCtx incoming request message
	 * @param synLog the Synapse log to use
	 * @return should this mediator terminate further processing?
	 * @throws ClusteringFault if there is an error in replicating the cfgCtx
	 */
	private boolean processRequestMessage(MessageContext synCtx,
	                                      SynapseLog synLog) throws ClusteringFault {

		if (collector) {
			handleException("Request messages cannot be handled in a collector cache", synCtx);
		}

		OperationContext opCtx = ((Axis2MessageContext) synCtx).getAxis2MessageContext().getOperationContext();
		String requestHash = null;

		try {
			requestHash = digestGenerator.getDigest(((Axis2MessageContext) synCtx).getAxis2MessageContext());
			synCtx.setProperty(CachingConstants.REQUEST_HASH, requestHash);
		} catch (CachingException e) {
			handleException("Error in calculating the hash value of the request", e, synCtx);
		}

		if (synLog.isTraceOrDebugEnabled()) {
			synLog.traceOrDebug("Generated request hash : " + requestHash);
		}

		RequestHash hash = new RequestHash(requestHash);
		CachableResponse cachedResponse = getMediatorCache().get(requestHash);
		org.apache.axis2.context.MessageContext msgCtx = ((Axis2MessageContext) synCtx).getAxis2MessageContext();
		opCtx.setProperty(CachingConstants.REQUEST_HASH, requestHash);

		String responsePayload;
		Map<String, Object> headerProperties;
		if (cachedResponse != null && (responsePayload = cachedResponse.getResponsePayload()) != null) {
			// get the response from the cache and attach to the context and change the
			// direction of the message
			if (!cachedResponse.isExpired()) {
				if (synLog.isTraceOrDebugEnabled()) {
					synLog.traceOrDebug("Cache-hit for message ID : " + synCtx.getMessageID());
				}
				cachedResponse.setInUse(true);
				// mark as a response and replace envelope from cache
				synCtx.setResponse(true);
				opCtx.setProperty(CachingConstants.CACHED_OBJECT, cachedResponse);

				// get responsePayload and set it to current transaction

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
						                    " was served from the cache : " + cacheKey);
					}
					// send the response back if there is not onCacheHit is specified
					synCtx.setTo(null);
					Axis2Sender.sendBack(synCtx);

				}
				// continue or stop any following mediators from executing
				return continueExecution;

			} else {
				cachedResponse.reincarnate(timeout);
				if (synLog.isTraceOrDebugEnabled()) {
					synLog.traceOrDebug("Existing cached response has expired. Resetting cache element");
				}
				getMediatorCache().put(hash.getRequestHash(), cachedResponse);
				opCtx.setProperty(CachingConstants.CACHED_OBJECT, cachedResponse);
				Replicator.replicate(opCtx);
			}
		} else {
			cacheNewResponse(msgCtx, hash);
		}

		return true;
	}

	/**
	 * Caches the CachableResponse object with currently available attributes against the requestHash in Cache<String,
	 * CachableResponse>
	 *
	 * @param msgContext axis2 message context of the request message
	 * @param requestHash the request hash that has already been computed
	 * @throws ClusteringFault if there is an error in replicating the cfgCtx
	 */
	private void cacheNewResponse(org.apache.axis2.context.MessageContext msgContext, RequestHash requestHash)
			throws ClusteringFault {
		OperationContext opCtx = msgContext.getOperationContext();
		CachableResponse response = new CachableResponse();
		response.setRequestHash(requestHash.getRequestHash());
		response.setTimeout(timeout);
		getMediatorCache().put(requestHash.getRequestHash(), response);
		opCtx.setProperty(CachingConstants.CACHED_OBJECT, response);
		Replicator.replicate(opCtx);
	}


	/**
	 * Creates default cache to keep mediator cache
	 *
	 * @return global cache
	 */
	public static Cache<String, CachableResponse> getMediatorCache() {
		if (mediatorCacheInit.get()) {
			return Caching.getCacheManagerFactory().getCacheManager(CachingConstants.CACHE_MANAGER)
			              .getCache(CachingConstants.MEDIATOR_CACHE);
		} else {
			CacheManager cacheManager = Caching.getCacheManagerFactory().getCacheManager(CachingConstants.CACHE_MANAGER);
			mediatorCacheInit.getAndSet(true);
			CacheBuilder<String, CachableResponse> mediatorCacheBuilder = cacheManager.createCacheBuilder(CachingConstants.MEDIATOR_CACHE);
			Cache<String, CachableResponse> cache = mediatorCacheBuilder.setExpiry(CacheConfiguration.ExpiryType.MODIFIED,
			                                        new CacheConfiguration.Duration(TimeUnit.SECONDS, CachingConstants.CACHE_INVALIDATION_TIME))
			                                        .setExpiry(CacheConfiguration.ExpiryType.ACCESSED,
					                        new CacheConfiguration.Duration(TimeUnit.SECONDS, CachingConstants.CACHE_INVALIDATION_TIME))
			                                        .setStoreByValue(false).build();
			return cache;
		}
	}

	/**
	 * This methods gives the ID of the cache configuration.
	 *
	 * @return string cache configuration ID.
	 */
	public String getId() {
		return id;
	}

	/**
	 * This methods sets the ID of the cache configuration.
	 *
	 * @param id cache configuration ID to be set.
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * This method gives the scope of the cache.
	 *
	 * @return value of the cache scope.
	 */
	public String getScope() {
		return scope;
	}

	/**
	 * This method sets the scope of the cache.
	 *
	 * @param scope cache scope to be set.
	 */
	public void setScope(String scope) {
		this.scope = scope;
		if (CachingConstants.SCOPE_PER_MEDIATOR.equals(scope)) {
			cacheKey = CACHE_KEY_PREFIX + id;
		}
	}

	/**
	 * This method gives whether the mediator should be in the incoming path or in the outgoing path as a boolean.
	 *
	 * @return boolean true if incoming path false if outgoing path.
	 */
	public boolean isCollector() {
		return collector;
	}

	/**
	 * This method sets whether the mediator should be in the incoming path or in the outgoing path as a boolean.
	 *
	 * @param collector boolean value to be set as collector.
	 */
	public void setCollector(boolean collector) {
		this.collector = collector;
	}

	/**
	 * This method gives the DigestGenerator to evaluate the hash values of incoming messages.
	 *
	 * @return DigestGenerator used evaluate hash values.
	 */
	public DigestGenerator getDigestGenerator() {
		return digestGenerator;
	}

	/**
	 * This method sets the DigestGenerator to evaluate the hash values of incoming messages.
	 *
	 * @param digestGenerator DigestGenerator to be set to evaluate hash values.
	 */
	public void setDigestGenerator(DigestGenerator digestGenerator) {
		this.digestGenerator = digestGenerator;
	}

	/**
	 * This method gives the size of the messages to be cached in memory.
	 *
	 * @return memory cache size in bytes.
	 */
	public int getInMemoryCacheSize() {
		return inMemoryCacheSize;
	}

	/**
	 * This method sets the size of the messages to be cached in memory.
	 *
	 * @param inMemoryCacheSize value(number of bytes) to be set as memory cache size.
	 */
	public void setInMemoryCacheSize(int inMemoryCacheSize) {
		this.inMemoryCacheSize = inMemoryCacheSize;
	}

	/**
	 * This method gives the size of the messages to be cached in disk.
	 *
	 * @return disk cache size in bytes.
	 */
	public int getDiskCacheSize() {
		return diskCacheSize;
	}

	/**
	 * This method sets the size of the messages to be cached in disk.
	 *
	 * @param diskCacheSize value(number of bytes) to be set as disk cache size.
	 */
	public void setDiskCacheSize(int diskCacheSize) {
		this.diskCacheSize = diskCacheSize;
	}

	/**
	 * This method gives the timeout period in milliseconds.
	 *
	 * @return timeout in milliseconds
	 */
	public long getTimeout() {
		return timeout / 1000;
	}

	/**
	 * This method sets the timeout period as milliseconds.
	 *
	 * @param timeout millisecond timeout period to be set.
	 */
	public void setTimeout(long timeout) {
		this.timeout = timeout * 1000;
	}

	/**
	 * This method gives SequenceMediator to be executed.
	 *
	 * @return sequence mediator to be executed.
	 */
	public SequenceMediator getOnCacheHitSequence() {
		return onCacheHitSequence;
	}

	/**
	 * This method sets SequenceMediator to be executed.
	 *
	 * @param onCacheHitSequence sequence mediator to be set.
	 */
	public void setOnCacheHitSequence(SequenceMediator onCacheHitSequence) {
		this.onCacheHitSequence = onCacheHitSequence;
	}

	/**
	 * This method gives reference to the onCacheHit sequence to be executed.
	 *
	 * @return reference to the onCacheHit sequence.
	 */
	public String getOnCacheHitRef() {
		return onCacheHitRef;
	}

	/**
	 * This method sets reference to the onCacheHit sequence to be executed.
	 *
	 * @param onCacheHitRef reference to the onCacheHit sequence to be set.
	 */
	public void setOnCacheHitRef(String onCacheHitRef) {
		this.onCacheHitRef = onCacheHitRef;
	}

	/**
	 * This method gives the maximum size of the messages to be cached in bytes.
	 *
	 * @return maximum size of the messages to be cached in bytes.
	 */
	public int getMaxMessageSize() {
		return maxMessageSize;
	}

	/**
	 * This method sets the maximum size of the messages to be cached in bytes.
	 *
	 * @param maxMessageSize maximum size of the messages to be set in bytes.
	 */
	public void setMaxMessageSize(int maxMessageSize) {
		this.maxMessageSize = maxMessageSize;
	}

	@Override
	public Mediator getInlineSequence(SynapseConfiguration synCfg, int inlinedSeqIdentifier) {
		if (inlinedSeqIdentifier == 0) {
			if (onCacheHitSequence != null) {
				return onCacheHitSequence;
			} else if (onCacheHitRef != null) {
				return synCfg.getSequence(onCacheHitRef);
			}
		}
		return null;
	}

}
