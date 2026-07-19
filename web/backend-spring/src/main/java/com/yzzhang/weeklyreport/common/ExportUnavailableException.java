package com.yzzhang.weeklyreport.common;

public class ExportUnavailableException extends RuntimeException {
    public ExportUnavailableException() {
        super("export unavailable");
    }
}
