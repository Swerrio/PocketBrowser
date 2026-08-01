package io.swerr.pocketbrowser.engine.cdp;

public class CdpException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CdpException(String message) {
        super(message);
    }

    public CdpException(String message, Throwable cause) {
        super(message, cause);
    }
}
