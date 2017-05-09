package com.raj.gateway.bespokes.cache.config.xml;

import com.raj.gateway.bespokes.cache.CoherenceCacheMediator;
import com.raj.gateway.bespokes.cache.CoherenceCachingConstants;
import com.raj.gateway.bespokes.cache.digest.DigestGenerator;
import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.synapse.Mediator;
import org.apache.synapse.config.xml.AbstractMediatorFactory;
import org.apache.synapse.config.xml.SequenceMediatorFactory;
import org.apache.synapse.config.xml.XMLConfigConstants;

import javax.xml.namespace.QName;
import java.util.Properties;

/**
* Creates an instance of a Cache mediator using XML configuration specified
* <p/>
* <pre>
* &lt;coherence [id="string"] [cacheName="coherence-cache-name"] [hashGenerator="class"] collector=(true | false) [maxMessageSize="in-bytes"]&gt;
*   &lt;onCacheHit [sequence="key"]&gt;
*     (mediator)+
*   &lt;/onCacheHit&gt;?
* &lt;/coherence&gt;
* </pre>
*/
public class CoherenceCacheMediatorFactory extends AbstractMediatorFactory {

	/**
	 * QName of the ID of cache configuration
	 */
	private static final QName ATT_ID = new QName("id");

	/**
	 * QName of the collector
	 */
	private static final QName ATT_COLLECTOR = new QName("collector");

	/**
	 * QName of the digest generator
	 */
	private static final QName ATT_HASH_GENERATOR = new QName("hashGenerator");

	/**
	 * QName of the oracle coherence cache name
	 */
	private static final QName ATT_COHERENCE_CACHE_NAME = new QName("cacheName");

	/**
	 * QName of the maximum message size
	 */
	private static final QName ATT_MAX_MSG_SIZE = new QName("maxMessageSize");

	/**
	 * QName of the mediator sequence
	 */
	private static final QName ATT_SEQUENCE = new QName("sequence");

	/**
	 * QName of the onCacheHit mediator sequence reference
	 */
	private static final QName ON_CACHE_HIT_Q = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "onCacheHit");

	public QName getTagQName() {
		return CoherenceCachingConstants.CACHE_Q;
	}

	@Override
	public Mediator createSpecificMediator(OMElement elem, Properties properties) {

		if (!CoherenceCachingConstants.CACHE_Q.equals(elem.getQName())) {
			handleException(
					"Unable to create the coherence mediator. Unexpected element as the coherence mediator configuration");
		}

		OMAttribute coherenceCacheNameAttr = elem.getAttribute(ATT_COHERENCE_CACHE_NAME);
		if (coherenceCacheNameAttr == null || coherenceCacheNameAttr.getAttributeValue() == null) {
			handleException("Unable to create the coherence mediator. Required parameter cacheName is missing");
		}

		CoherenceCacheMediator coherence = new CoherenceCacheMediator();
		OMAttribute idAttr = elem.getAttribute(ATT_ID);
		if (idAttr != null && idAttr.getAttributeValue() != null) {
			coherence.setId(idAttr.getAttributeValue());
		}

		if (coherenceCacheNameAttr != null && coherenceCacheNameAttr.getAttributeValue() != null) {
			coherence.setCoherenceCacheName(coherenceCacheNameAttr.getAttributeValue());
		}

		OMAttribute collectorAttr = elem.getAttribute(ATT_COLLECTOR);
		if (collectorAttr != null && collectorAttr.getAttributeValue() != null &&
		    "true".equals(collectorAttr.getAttributeValue())) {

			coherence.setCollector(true);

			OMAttribute maxMessageSizeAttr = elem.getAttribute(ATT_MAX_MSG_SIZE);
			if (maxMessageSizeAttr != null && maxMessageSizeAttr.getAttributeValue() != null) {
				coherence.setMaxMessageSize(Integer.parseInt(maxMessageSizeAttr.getAttributeValue()));
			}

		} else {

			coherence.setCollector(false);

			OMAttribute hashGeneratorAttr = elem.getAttribute(ATT_HASH_GENERATOR);
			if (hashGeneratorAttr != null && hashGeneratorAttr.getAttributeValue() != null) {
				try {
					Class generator = Class.forName(hashGeneratorAttr.getAttributeValue());
					Object o = generator.newInstance();
					if (o instanceof DigestGenerator) {
						coherence.setDigestGenerator((DigestGenerator) o);
					} else {
						handleException("Specified class for the hashGenerator is not a " +
						                "DigestGenerator. It *must* implement " +
						                "DigestGenerator interface");
					}
				} catch (ClassNotFoundException e) {
					handleException("Unable to load the hash generator class", e);
				} catch (IllegalAccessException e) {
					handleException("Unable to access the hash generator class", e);
				} catch (InstantiationException e) {
					handleException("Unable to instantiate the hash generator class", e);
				}
			}

			OMElement onCacheHitElem = elem.getFirstChildWithName(ON_CACHE_HIT_Q);
			if (onCacheHitElem != null) {
				OMAttribute sequenceAttr = onCacheHitElem.getAttribute(ATT_SEQUENCE);
				if (sequenceAttr != null && sequenceAttr.getAttributeValue() != null) {
					coherence.setOnCacheHitRef(sequenceAttr.getAttributeValue());
				} else if (onCacheHitElem.getFirstElement() != null) {
					coherence.setOnCacheHitSequence(new SequenceMediatorFactory()
							                            .createAnonymousSequence(onCacheHitElem, properties));
				}
			}
		}

		return coherence;
	}
}
