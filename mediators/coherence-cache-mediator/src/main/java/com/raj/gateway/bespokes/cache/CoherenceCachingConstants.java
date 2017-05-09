package com.raj.gateway.bespokes.cache;

import com.raj.gateway.bespokes.cache.digest.DigestGenerator;
import com.raj.gateway.bespokes.cache.digest.DomHashGenerator;
import org.apache.synapse.config.xml.XMLConfigConstants;

import javax.xml.namespace.QName;

public class CoherenceCachingConstants {
    public static final String REQUEST_HASH = "requestHash";
    public static final DigestGenerator DEFAULT_XML_IDENTIFIER = new DomHashGenerator();
    public static final String CACHED_OBJECT = "CoherenceCacheableResponse";
    public static final QName CACHE_Q = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "coherence");
}
