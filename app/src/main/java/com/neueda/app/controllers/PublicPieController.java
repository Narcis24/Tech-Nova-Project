package com.neueda.app.controllers;

import com.neueda.app.dtos.PublicPieResponse;
import com.neueda.app.dtos.PublicPieSummary;
import com.neueda.app.services.PublicPieService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/pies/public")
public class PublicPieController {

    private final PublicPieService publicPieService;

    public PublicPieController(PublicPieService publicPieService) {
        this.publicPieService = publicPieService;
    }

    /** Published pies available to browse. */
    @GetMapping
    public ResponseEntity<List<PublicPieSummary>> listPublicPies() {
        return ResponseEntity.ok(publicPieService.list());
    }

    @GetMapping("/{pieId}")
    public ResponseEntity<PublicPieResponse> getPublicPie(@PathVariable UUID pieId) {
        return ResponseEntity.ok(publicPieService.get(pieId));
    }
}
