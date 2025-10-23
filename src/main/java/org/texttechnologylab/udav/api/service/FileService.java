package org.texttechnologylab.udav.api.service;

import org.springframework.stereotype.Service;
import org.texttechnologylab.udav.api.Repositories.DocumentRepository;

import java.util.List;

@Service
public class FileService {

    private final DocumentRepository documentRepository;

    public FileService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public List<String> listDocumentIds(int page, int size, String q) {
        return documentRepository.listDocumentIds(page, size, q);
    }

}
