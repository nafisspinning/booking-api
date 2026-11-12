package com.example.booking.venue;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.example.booking.common.web.PageResponse;
import com.example.booking.venue.dto.CourtRequest;
import com.example.booking.venue.dto.CourtResponse;
import com.example.booking.venue.dto.VenueRequest;
import com.example.booking.venue.dto.VenueResponse;

@RestController 
@RequestMapping ("/api/venues")
@RequiredArgsConstructor 
public class VenueController {
    
    private final VenueService venueService;

    //fungsi controller untuk membuat venue baru
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VenueResponse createVenue(@Valid @RequestBody VenueRequest req) {
        return venueService.createVenue(req);
    }

    //fungsi controller untuk menampilkan daftar venue dengan pagination
    @GetMapping
    public PageResponse<VenueResponse> listVenues(@PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return venueService.listVenues(pageable);
    }

    @GetMapping("/{id}")
    public VenueResponse getVenue(@PathVariable UUID id) {
        return venueService.getVenue(id);
    }

    @PutMapping("/{id}")
    public VenueResponse updateVenue(@PathVariable UUID id, @Valid @RequestBody VenueRequest req) {
        return venueService.updateVenue(id, req);
    }

    @PostMapping("/{id}/courts")
    @ResponseStatus (HttpStatus.CREATED)
    public CourtResponse addCourt(@PathVariable UUID id, @Valid @RequestBody CourtRequest req) {
        return venueService.addCourt(id, req);
    }

    @DeleteMapping ("/{id}")
    @ResponseStatus (HttpStatus.NO_CONTENT)
    public void deleteVenue(@PathVariable UUID id) {
        venueService.deleteVenue(id);
    }
}