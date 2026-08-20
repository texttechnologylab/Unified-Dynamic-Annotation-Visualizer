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
        return repository.list(page, size, q).stream()
                .map(this::formatAnnotationLabel)
                .toList();
    }

    private UimaTypeRow formatAnnotationLabel(UimaTypeRow row) {
        if (row.rowCount() == -1 || row.annotation() == null) {
            return row;
        }
        String uri = row.annotation();
        int lastDot = uri.lastIndexOf('.');
        if (lastDot == -1 || lastDot == uri.length() - 1) {
            return row;
        }
        String shortName = uri.substring(lastDot + 1);
        return new UimaTypeRow(shortName + " (" + uri + ")", row.rowCount());
    }
}
