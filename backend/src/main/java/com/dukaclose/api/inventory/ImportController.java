package com.dukaclose.api.inventory;

import com.dukaclose.api.auth.AppUserDetails;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shops/{shopId}/inventory/import")
@RequiredArgsConstructor
public class ImportController {

    private final CsvImportService importer;

    /** commit=false (the default) checks the file and saves nothing. */
    @PostMapping
    public ImportReport importCsv(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId,
                                  @RequestParam(defaultValue = "false") boolean commit,
                                  @Valid @RequestBody ImportRequest body) {
        return importer.importCsv(me.userId(), shopId, body.importId(), body.csv(), commit);
    }
}