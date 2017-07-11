package org.wso2.carbon.mediatos.cache.json.digest;

import org.apache.axis2.context.MessageContext;
import org.wso2.carbon.mediatos.cache.json.CachingException;

public class ReqUrlHashGenerator implements DigestGenerator{

    public static final String MD5_DIGEST_ALGORITHM = "MD5";

    public String getDigest(MessageContext msgContext) throws CachingException {
        String toAddress = null;
        if (msgContext.getTo() != null) {
            toAddress = msgContext.getTo().getAddress();
        }

        // do a md5 hash on toAddress

        return null;
    }
}
