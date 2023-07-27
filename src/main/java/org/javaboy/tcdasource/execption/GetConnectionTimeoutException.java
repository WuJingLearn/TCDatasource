package org.javaboy.tcdasource.execption;

import java.sql.SQLException;

/**
 * @author:majin.wj
 */
public class GetConnectionTimeoutException extends SQLException {

    public GetConnectionTimeoutException(String reason) {
        super(reason);
    }

    public GetConnectionTimeoutException(String reason, Throwable throwable) {
        super(reason, throwable);
    }

}
