package org.texttechnologylab.udav.api.service;

import org.springframework.stereotype.Service;
import org.texttechnologylab.udav.api.Repositories.DocumentRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
public class FileService {

    private final DocumentRepository documentRepository;

    public FileService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public List<String> listDocumentIds(int page, int size, String q) {
        return documentRepository.listDocumentIds(page, size, q);
    }

    public List<String> listTags(String q) {
        return documentRepository.listTags(q);
    }

    public Set<String> documentIdsMatching(Set<String> tags, LocalDate from, LocalDate to) {
        return documentRepository.documentIdsMatching(tags, from, to);
    }

}
