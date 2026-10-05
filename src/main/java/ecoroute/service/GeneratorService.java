package ecoroute.service;

import ecoroute.dao.*;
import ecoroute.db.Database;
import ecoroute.model.*;
import ecoroute.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static ecoroute.db.DatabaseContract.*;

public final class GeneratorService {
    public record Profile(WasteGenerator generator, Hospital hospital, HousingSociety housingSociety, Factory factory) {}
    private final Database database;
    public GeneratorService(Database database) { this.database = database; }

    public long create(UserSession session, Profile profile) {
        Validation.require(profile != null && profile.generator() != null, "Generator profile is required.");
        WasteGenerator generator = profile.generator();
        validate(generator);
        Validation.require(generator.getGeneratorId() == null, "New generator ID must be null.");
        int count = (profile.hospital() == null ? 0 : 1) + (profile.housingSociety() == null ? 0 : 1) + (profile.factory() == null ? 0 : 1);
        switch (generator.getGeneratorType()) {
            case "HOSPITAL" -> {
                Validation.require(count == 1 && profile.hospital() != null, "Hospital details are required.");
            }
            case "HOUSING_SOCIETY" -> {
                Validation.require(count == 1 && profile.housingSociety() != null, "Housing society details are required.");
                Validation.require(profile.housingSociety().getNumberOfFlats() == null || profile.housingSociety().getNumberOfFlats() > 0, "Number of flats must be positive.");
            }
            case "FACTORY" -> {
                Validation.require(count == 1 && profile.factory() != null, "Factory details are required.");
            }
            default -> Validation.require(count == 0, "This generator type has no subtype table.");
        }
        return database.transaction(c -> {
            Access.admin(c, session);
            // Copy rather than assigning IDs to caller-owned objects before the transaction commits.
            WasteGenerator value = new WasteGenerator(null, generator.getName(), generator.getGeneratorType(), generator.getContactPerson(),
                    generator.getPhone(), generator.getEmail(), generator.getAddress(), generator.getLatitude(), generator.getLongitude(),
                    generator.getZoneId(), true, LocalDateTime.now());
            long id = new GeneratorDAO(c).insert(value);
            GeneratorSubtypeDAO subtype = new GeneratorSubtypeDAO(c);
            if (profile.hospital() != null) {
                Hospital h = profile.hospital();
                subtype.insert(new Hospital(id, h.getLicenseNumber(), h.getBiomedicalAuthNumber(), h.getAuthExpiryDate()));
            }
            if (profile.housingSociety() != null) {
                HousingSociety h = profile.housingSociety();
                subtype.insert(new HousingSociety(id, h.getRegistrationNumber(), h.getNumberOfFlats()));
            }
            if (profile.factory() != null) {
                Factory f = profile.factory();
                subtype.insert(new Factory(id, f.getIndustryType(), f.getPollutionConsentNo(), f.getConsentExpiryDate()));
            }
            return id;
        });
    }

    public List<WasteGenerator> findAll(UserSession session) {
        return database.read(c -> { Access.staff(c, session); return new GeneratorDAO(c).findAll(); });
    }
    public List<WasteGenerator> findByZone(UserSession session, long zoneId) {
        return database.read(c -> { Access.staff(c, session); return new GeneratorDAO(c).findByZone(zoneId); });
    }
    public Optional<Profile> findById(UserSession session, long id) {
        return database.read(c -> {
            Access.generator(c, session, id);
            GeneratorSubtypeDAO subtype = new GeneratorSubtypeDAO(c);
            return new GeneratorDAO(c).findById(id).map(g -> new Profile(g, subtype.findHospital(id).orElse(null),
                    subtype.findHousingSociety(id).orElse(null), subtype.findFactory(id).orElse(null)));
        });
    }
    public void update(UserSession session, WasteGenerator generator) {
        Validation.require(generator != null, "Generator is required.");
        validate(generator); Validation.id(generator.getGeneratorId(), "Generator");
        database.transaction(c -> {
            Access.admin(c, session);
            GeneratorDAO dao = new GeneratorDAO(c);
            WasteGenerator current = dao.lockById(generator.getGeneratorId()).orElseThrow(() -> new InvalidRequestException("Generator does not exist."));
            Validation.require(current.getGeneratorType().equals(generator.getGeneratorType()), "Changing generator subtype is not supported.");
            // Request zone is derived, so moving the generator while a request is assigned would change its route contract.
            if (!current.getZoneId().equals(generator.getZoneId())) {
                Validation.require(new RequestDAO(c).findByGenerator(generator.getGeneratorId()).isEmpty(),
                        "Zone changes require a generator with no request history (zones are derived).");
            }
            dao.update(generator);
            return null;
        });
    }
    public void setActive(UserSession session, long id, boolean active) {
        database.transaction(c -> {
            Access.admin(c, session);
            GeneratorDAO dao = new GeneratorDAO(c);
            dao.lockById(id).orElseThrow(() -> new InvalidRequestException("Generator does not exist."));
            if (!active) Validation.require(new RequestDAO(c).findByGenerator(id).stream().noneMatch(r -> ASSIGNED.equals(r.getStatus())),
                    "Complete assigned requests before deactivating the generator.");
            dao.setActive(id, active);
            return null;
        });
    }
    private static void validate(WasteGenerator g) {
        Validation.text(g.getName(), "Generator name");
        Validation.text(g.getAddress(), "Generator address");
        Validation.require(g.getGeneratorType() != null && GENERATOR_TYPES.contains(g.getGeneratorType()), "Unknown generator type.");
        Validation.id(g.getZoneId(), "Zone");
        coordinate(g.getLatitude(), 90, "Latitude"); coordinate(g.getLongitude(), 180, "Longitude");
    }
    private static void coordinate(BigDecimal value, int maximum, String field) {
        if (value != null) Validation.require(value.abs().compareTo(BigDecimal.valueOf(maximum)) <= 0
                && value.stripTrailingZeros().scale() <= 7, field + " must be within range and have at most seven decimal places.");
    }
}
