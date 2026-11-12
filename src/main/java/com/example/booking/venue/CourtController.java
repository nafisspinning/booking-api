package com.example.booking.venue;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import com.example.booking.common.web.PageResponse;
import com.example.booking.venue.dto.CourtResponse;

@RestController
@RequestMapping ("/api/courts")
@RequiredArgsConstructor
public class CourtController {
    
    public final VenueService venueService;

    @GetMapping
    public PageResponse<CourtResponse> search(
            @RequestParam(required = false) SportType sportType,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return venueService.searchCourts(sportType, pageable);
    }

    @GetMapping("/{id}")
    public CourtResponse getCourt(@PathVariable UUID id) {
        return venueService.getCourt(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus (HttpStatus.NO_CONTENT)
    public void deactivateCourt(@PathVariable UUID id) {
        venueService.deactivateCourt(id);
    }
}
