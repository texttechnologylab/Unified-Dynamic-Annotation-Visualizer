package org.texttechnologylab.udav.api.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class ConvertionControllerZipTest {

    @Test
    void entryNamesHaveNoDirectories() throws Exception {
        List<org.springframework.web.multipart.MultipartFile> files = List.of(
                new MockMultipartFile("files", "0.svg", "image/svg+xml", new byte[]{1}),
                new MockMultipartFile("files", "../../evil.svg", "image/svg+xml", new byte[]{2}),
                new MockMultipartFile("files", "..\\win.svg", "image/svg+xml", new byte[]{3}));

        byte[] zip = new ConvertionController(new ObjectMapper()).createZip(files).getBody();

        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) names.add(e.getName());
        }
        assertEquals(List.of("0.svg", "evil.svg", "win.svg"), names);
    }
}
