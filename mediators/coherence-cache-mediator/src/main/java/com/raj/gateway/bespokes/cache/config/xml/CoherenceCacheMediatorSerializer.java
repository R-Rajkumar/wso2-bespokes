package com.raj.gateway.bespokes.cache.config.xml;

import com.raj.gateway.bespokes.cache.CoherenceCacheMediator;
import org.apache.axiom.om.OMElement;
import org.apache.synapse.Mediator;
import org.apache.synapse.config.xml.AbstractMediatorSerializer;
import org.apache.synapse.config.xml.MediatorSerializer;
import org.apache.synapse.config.xml.MediatorSerializerFinder;

import java.util.List;

/**
 * Serializes the Cache mediator to the XML configuration specified
 * <p/>
 * <pre>
 * &lt;coherence [id="string"] [cacheName="coherence-cache-name"] [hashGenerator="class"] collector=(true | false) [maxMessageSize="in-bytes"]&gt;
 *   &lt;onCacheHit [sequence="key"]&gt;
 *     (mediator)+
 *   &lt;/onCacheHit&gt;?
 * &lt;/coherence&gt;
 * </pre>
 */
public class CoherenceCacheMediatorSerializer extends AbstractMediatorSerializer {

	@Override
	public OMElement serializeSpecificMediator(Mediator m) {

		if (!(m instanceof CoherenceCacheMediator)) {
			handleException("Unsupported mediator passed in for serialization : " + m.getType());
		}
		CoherenceCacheMediator mediator = (CoherenceCacheMediator) m;
		OMElement coherence = fac.createOMElement("coherence", synNS);
		saveTracingState(coherence, mediator);

		if (mediator.getId() != null) {
			coherence.addAttribute(fac.createOMAttribute("id", nullNS, mediator.getId()));
		}

		if (mediator.getCoherenceCacheName() != null) {
			coherence.addAttribute(fac.createOMAttribute("cacheName", nullNS, mediator.getCoherenceCacheName()));
		}

		if (mediator.isCollector()) {
			coherence.addAttribute(fac.createOMAttribute("collector", nullNS, "true"));

			if (mediator.getMaxMessageSize() != 0) {
				coherence.addAttribute(
						fac.createOMAttribute("maxMessageSize", nullNS,
								Integer.toString(mediator.getMaxMessageSize())));
			}
		} else {

			coherence.addAttribute(fac.createOMAttribute("collector", nullNS, "false"));

			if (mediator.getDigestGenerator() != null) {
				coherence.addAttribute(fac.createOMAttribute("hashGenerator", nullNS,
				                                         mediator.getDigestGenerator().getClass().getName()));
			}

			if (mediator.getOnCacheHitRef() != null) {
				OMElement onCacheHit = fac.createOMElement("onCacheHit", synNS);
				onCacheHit.addAttribute(
						fac.createOMAttribute("sequence", nullNS, mediator.getOnCacheHitRef()));
				coherence.addChild(onCacheHit);
			} else if (mediator.getOnCacheHitSequence() != null) {
				OMElement onCacheHit = fac.createOMElement("onCacheHit", synNS);
				new CoherenceCacheMediatorSerializer()
						.serializeChildren(onCacheHit, mediator.getOnCacheHitSequence().getList());
				coherence.addChild(onCacheHit);
			}
		}

		return coherence;
	}

	public String getMediatorClassName() {
		return CoherenceCacheMediator.class.getName();
	}

	/**
	 * Creates XML representation of the child mediators
	 *
	 * @param parent The mediator for which the XML representation child should be attached
	 * @param list   The mediators list for which the XML representation should be created
	 */
	protected void serializeChildren(OMElement parent, List<Mediator> list) {
		for (Mediator child : list) {
			MediatorSerializer medSer = MediatorSerializerFinder.getInstance().getSerializer(child);
			if (medSer != null) {
				medSer.serializeMediator(parent, child);
			} else {
				handleException("Unable to find a serializer for mediator : " + child.getType());
			}
		}
	}
}
