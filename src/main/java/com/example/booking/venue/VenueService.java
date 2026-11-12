package com.example.booking.venue;

import com.example.booking.common.exception.BusinessException;
import com.example.booking.common.exception.ConflictException;
import com.example.booking.common.exception.NotFoundException;
import com.example.booking.common.web.PageResponse;
import com.example.booking.venue.dto.*;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VenueService {

    private final VenueRepository venueRepository;
    private final CourtRepository courtRepository;

    //fungsi untuk membuat venue baru
    @Transactional
    public VenueResponse createVenue(VenueRequest req) {
        validateHours(req);
        Venue venue = new Venue();
        apply(venue, req);
        return VenueResponse.from(venueRepository.save(venue));
    }

    //fungsi untuk menampilkan daftar venue dengan pagination
    public PageResponse<VenueResponse> listVenues(Pageable pageable) {
        return PageResponse.from(venueRepository.findAll(pageable).map(VenueResponse::from));
    }

    //fungsi untuk menampilkan detail venue berdasarkan id
    public VenueResponse getVenue(UUID id) {
        return VenueResponse.from(findVenue(id));
    }

    //fungsi untuk melakukan update data venue berdasarkan id
    @Transactional
    public VenueResponse updateVenue(UUID id, VenueRequest req) {
        validateHours(req);
        Venue venue = findVenue(id);
        apply(venue, req);
        return VenueResponse.from(venue); // tidak perlu save(): dirty checking
    }

    //fungsi untuk menambahkan lapangan baru ke venue tertentu
    @Transactional
    public CourtResponse addCourt(UUID venueId, CourtRequest req) {
        Venue venue = findVenue(venueId);
        if (courtRepository.existsByVenueIdAndNameIgnoreCase(venueId, req.name())) {
            throw new ConflictException("COURT_NAME_TAKEN",
                    "Nama lapangan '%d' sudah dipakai di venue ini".formatted(req.name()));
        }

        Court court = new Court();
        court.setVenue(venue);
        court.setName(req.name());
        court.setSportType(req.sportType());
        court.setPricePerHour(req.pricePerHour());
        return CourtResponse.from(courtRepository.save(court));
    }

    //fungsi untuk menampilkan daftar lapangan dengan pagination dan bisa difilter berdasarkan jenis olahraga
    public PageResponse<CourtResponse> searchCourts(SportType type, Pageable pageable) {
        Page<Court> page = (type == null)
                ? courtRepository.findByActiveTrue(pageable)
                : courtRepository.findBySportTypeAndActiveTrue(type, pageable);
        return PageResponse.from(page.map(CourtResponse::from));
    }

    //fungsi untuk menampilkan detail lapangan berdasarkan id
    public CourtResponse getCourt(UUID id) {
        return courtRepository.findWithVenueById(id)
                .map(CourtResponse::from)
                .orElseThrow(() -> new NotFoundException("Lapangan %s tidak ditemukan".formatted(id)));
    }

    //fungsi untuk menonaktifkan lapangan berdasarkan id
    @Transactional
    public void deactivateCourt(UUID id) {
        Court court = courtRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Lapangan %s tidak ditemukan".formatted(id)));
        court.setActive(false);
    }

    //fungsi untuk menemukan venue berdasarkan id
    private Venue findVenue(UUID id) {
        return venueRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Venue %s tidak ditemukan".formatted(id)));
    }

    //fungsi untuk melakukan delete venue berdasarkan id
    @Transactional
    public void deleteVenue(UUID id) {
        Venue venue = findVenue(id);
        if (courtRepository.existsByVenueId(id)) {
            throw new ConflictException("VENUE_HAS_COURTS",
                    "Venue %s masih memiliki lapangan, hapus atau nonaktifkan lapangan terlebih dahulu".formatted(id));
        }
        venueRepository.delete(venue);
    }

    //fungsi untuk memvalidasi jam buka dan jam tutup venue
    private void validateHours(VenueRequest req) {
        if (!req.openTime().isBefore(req.closeTime())) {
            throw new BusinessException("INVALID_HOURS", "Jam buka harus sebelum jam tutup");
        }
    }

    //fungsi untuk melakukan update data venue berdasarkan request
    private void apply(Venue venue, VenueRequest req) {
        venue.setName(req.name());
        venue.setAddress(req.address());
        venue.setOpenTime(req.openTime());
        venue.setCloseTime(req.closeTime());
    }
}