package com.devchesterunited.devchesterunitedv2.scraping.catalogo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Publica identidades WhoScored únicamente después de completar el preflight. */
@Service
public class WhoscoredTeamIdentityImporter {
    private final WhoscoredTeamIdentityPublicationService publicationService;

    public WhoscoredTeamIdentityImporter(WhoscoredTeamIdentityPublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @Transactional(rollbackFor = IOException.class)
    public ImportResult importCsv(Path csv) throws IOException {
        return publicationService.publish(WhoscoredTeamIdentityCsv.read(csv));
    }

    public ImportResult publish(List<WhoscoredTeamIdentityCsv.Mapping> mappings) {
        return publicationService.publish(mappings);
    }

    public record ImportResult(int total, int inserted, int alreadyExisting) {
        @Override public String toString() {
            return "total=" + total + "\ninsertadas=" + inserted + "\nyaExistentes=" + alreadyExisting;
        }
    }
}
