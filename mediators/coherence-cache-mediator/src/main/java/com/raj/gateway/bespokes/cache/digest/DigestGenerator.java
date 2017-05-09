package com.raj.gateway.bespokes.cache.digest;

import com.raj.gateway.bespokes.cache.CoherenceCachingException;
import org.apache.axis2.context.MessageContext;

import java.io.Serializable;

/**
 * This is the primary interface for the DigestGenerator which is the unique SOAP request
 * identifier generation interface to be used by the CacheManager inorder to generate a
 * unique identifier key for the normalized XML/SOAP message. This has to be serializable
 * because the DigestGenerator implementations has to be serializable to support clustered
 * caching
 *
 * @see Serializable
 */
public interface DigestGenerator extends Serializable {

    /**
     * This method will be implemented to return the unique XML node identifier
     * on the given XML node
     * 
     * @param msgContext - MessageContext on which the unique identifier will be generated
     * @return Object representing the unique identifier for the msgContext
     * @throws CoherenceCachingException if there is an error in generating the digest key
     */
    String getDigest(MessageContext msgContext) throws CoherenceCachingException;
}
