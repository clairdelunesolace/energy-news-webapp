package com.carya.energynews.source;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SourceService {

    private final SourceRepository sourceRepository;

    public SourceService(SourceRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    public List<SourceResponse> getAll() {
        return sourceRepository.findAll().stream()
                .map(SourceService::toResponse)
                .toList();
    }

    public SourceResponse getById(Long id) {
        Source source = sourceRepository.findById(id)
                .orElseThrow(() -> new SourceNotFoundException(id));
        return toResponse(source);
    }

    public SourceResponse create(CreateSourceRequest request) {
        if (sourceRepository.existsByUrl(request.url())) {
            throw new DuplicateSourceUrlException(request.url());
        }

        Source source = new Source(
                request.name(),
                request.url(),
                request.type(),
                request.priority(),
                request.language()
        );

        try {
            return toResponse(sourceRepository.saveAndFlush(source));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateSourceUrlException(request.url(), exception);
        }
    }

    private static SourceResponse toResponse(Source source) {
        return new SourceResponse(
                source.getId(),
                source.getName(),
                source.getUrl(),
                source.getType(),
                source.getPriority(),
                source.getLanguage(),
                source.isEnabled(),
                source.getCreatedAt(),
                source.getUpdatedAt()
        );
    }
}
