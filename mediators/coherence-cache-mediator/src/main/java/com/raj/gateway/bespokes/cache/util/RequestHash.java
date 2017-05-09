package com.raj.gateway.bespokes.cache.util;

import java.io.Serializable;

/**
 * Represents a SOAP Request Hash
 */
public class RequestHash implements Serializable {

	private static final long serialVersionUID = -2880048895625522928L;

	/**
	 * This holds the hash value of the request payload which is calculated form the specified DigestGenerator,
	 * and is used to index the cached response.
	 */
	private String requestHash;

	/**
	 * RequestHash constructor sets the hash of the request to the cache
	 *
	 * @param requestHash - hash of the request payload to be set as an String
	 */
	public RequestHash(String requestHash) {
		this.requestHash = requestHash;
	}

	/**
	 * This method gives the hash value of the request payload stored in the cache
	 *
	 * @return String hash of the request payload
	 */
	public String getRequestHash() {
		return requestHash;
	}

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RequestHash that = (RequestHash) o;
        return requestHash.equals(that.requestHash);
    }

    @Override
    public int hashCode() {
        return requestHash.hashCode();
    }
}
