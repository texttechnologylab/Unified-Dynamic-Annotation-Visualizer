package org.texttechnologylab.udav.api.service;

import org.springframework.stereotype.Service;
import org.texttechnologylab.udav.api.Repositories.UIMATypeRepository;
import org.texttechnologylab.udav.api.dto.UimaTypeRow;

import java.util.List;

@Service
public class UIMATypeService {

    private final UIMATypeRepository repository;

    public UIMATypeService(UIMATypeRepository repository) {
        this.repository = repository;
    }

    public List<UimaTypeRow> list(int page, int size, String q) {
        return repository.list(page, size, q);
    }

}
