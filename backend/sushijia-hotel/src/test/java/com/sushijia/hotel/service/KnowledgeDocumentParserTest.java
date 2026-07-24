package com.sushijia.hotel.service;

import com.sushijia.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeDocumentParserTest {

    private final KnowledgeDocumentParser parser = new KnowledgeDocumentParser();

    @Test
    void parsesUtf8TextAndNormalizesWhitespace(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("hotel.txt");
        Files.writeString(file, "早餐时间  7:00-10:00\n\n\n支持免费停车", StandardCharsets.UTF_8);

        assertEquals("早餐时间 7:00-10:00\n\n支持免费停车", parser.parse(file, "txt"));
    }

    @Test
    void rejectsUnsupportedExtension() {
        assertThrows(BizException.class, () -> parser.extension("hotel.exe"));
    }
}
