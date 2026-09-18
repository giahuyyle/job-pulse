package com.huy.jobpulse.discovery.application;

import com.huy.jobpulse.discovery.domain.CompanySeed;
import com.huy.jobpulse.discovery.infrastructure.CompanySeedRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

@Service
public class CompanySeedService {

    private final CompanySeedRepository repository;

    public CompanySeedService(CompanySeedRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SeedImportResult importCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            throw new IllegalArgumentException("CSV body must not be blank");
        }
        int imported = 0;
        int existing = 0;
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .get()
                .parse(new StringReader(csv))) {
            requireHeaders(parser);
            for (CSVRecord record : parser) {
                CompanySeed seed = CompanySeed.create(
                        record.get("company_name"),
                        record.get("careers_url")
                );
                if (repository.findByCareersUrl(seed.getCareersUrl())
                        .isPresent()) {
                    existing++;
                    continue;
                }
                repository.save(seed);
                imported++;
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not parse CSV", exception);
        }
        return new SeedImportResult(imported, existing);
    }

    @Transactional(readOnly = true)
    public List<CompanySeed> findAll() {
        return repository.findAllByOrderByCompanyNameAsc();
    }

    private static void requireHeaders(CSVParser parser) {
        if (!parser.getHeaderMap().containsKey("company_name")
                || !parser.getHeaderMap().containsKey("careers_url")) {
            throw new IllegalArgumentException(
                    "CSV must contain company_name and careers_url headers"
            );
        }
    }
}
