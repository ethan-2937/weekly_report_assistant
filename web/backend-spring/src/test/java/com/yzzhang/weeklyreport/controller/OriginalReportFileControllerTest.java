package com.yzzhang.weeklyreport.controller;

import com.yzzhang.weeklyreport.service.OriginalReportExportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OriginalReportFileControllerTest {
    @TempDir
    Path tempDir;

    @Test
    void returnsStandardXlsxHeadersWithUtf8Filename() throws Exception {
        Path file = tempDir.resolve("generated.xlsx");
        Files.write(file, new byte[] {1, 2, 3});
        OriginalReportExportService service = mock(OriginalReportExportService.class);
        when(service.exportXlsx("2026-W29")).thenReturn(file);

        var response = new OriginalReportFileController(service).download("2026-W29");

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ));
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
            .contains("attachment", "filename*=UTF-8''")
            .containsIgnoringCase("%E5%91%A8%E6%8A%A5%E6%98%8E%E7%BB%86_2026-W29.xlsx");
        assertThat(response.getBody()).isNotNull();
    }
}
