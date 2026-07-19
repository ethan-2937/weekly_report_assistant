package com.yzzhang.weeklyreport.service;

import java.nio.file.Path;

public interface OriginalReportExportService {
    Path exportXlsx(String week);
}
