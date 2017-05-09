package com.raj.gateway.bespokes.cache;

/**
 * This is the CoherenceCachingException which is a checked exception and will be thrown in case of
 * an error in the caching execution
 *
 * @see Exception
 */
public class CoherenceCachingException extends RuntimeException {

    private static final long serialVersionUID = 8196331429473105374L;

    /**
     * Default constructor of the CoherenceCachingException
     */
    public CoherenceCachingException() {
        super();
    }

    /**
     * This constructor of the CoherenceCachingException sets the given String message to the Exception
     *
     * @param message - String specifying the exception message
     */
    public CoherenceCachingException(String message) {
        super(message);
    }

    /**
     * This constructor of the CoherenceCachingException sets the given String message, and the
     * cause to the Exception
     * 
     * @param message - String specifying the exception message
     * @param cause - Throwable cause of the exception
     */
    public CoherenceCachingException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * This constructor of the CoherenceCachingException sets the given cause to the Exception
     *
     * @param cause - Throwable cause of the exception
     */
    public CoherenceCachingException(Throwable cause) {
        super(cause);
    }
}
