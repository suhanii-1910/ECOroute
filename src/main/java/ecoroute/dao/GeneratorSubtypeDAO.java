package ecoroute.dao;

import ecoroute.model.*;
import java.sql.Connection;
import java.util.Optional;

/** Handles all three generator subtype tables aligned with the final schema. */
public final class GeneratorSubtypeDAO {
    private final Connection connection;
    public GeneratorSubtypeDAO(Connection connection) { this.connection = connection; }

    public void insert(Hospital value) {
        Jdbc.change(connection,
                "INSERT INTO `HOSPITAL` (generator_id, license_number, biomedical_auth_number) VALUES (?, ?, ?)",
                value.getGeneratorId(), value.getLicenseNumber(), value.getBiomedicalAuthNumber());
    }

    public Optional<Hospital> findHospital(long generatorId) {
        return Jdbc.one(connection,
                "SELECT generator_id, license_number, biomedical_auth_number FROM `HOSPITAL` WHERE generator_id = ?",
                r -> new Hospital(Jdbc.nullableLong(r, "generator_id"),
                        r.getString("license_number"), r.getString("biomedical_auth_number")),
                generatorId);
    }

    public void insert(HousingSociety value) {
        Jdbc.change(connection,
                "INSERT INTO `HOUSING_SOCIETY` (generator_id, registration_number) VALUES (?, ?)",
                value.getGeneratorId(), value.getRegistrationNumber());
    }

    public Optional<HousingSociety> findHousingSociety(long generatorId) {
        return Jdbc.one(connection,
                "SELECT generator_id, registration_number FROM `HOUSING_SOCIETY` WHERE generator_id = ?",
                r -> new HousingSociety(Jdbc.nullableLong(r, "generator_id"),
                        r.getString("registration_number")),
                generatorId);
    }

    public void insert(Factory value) {
        Jdbc.change(connection,
                "INSERT INTO `FACTORY` (generator_id, pollution_consent_no) VALUES (?, ?)",
                value.getGeneratorId(), value.getPollutionConsentNo());
    }

    public Optional<Factory> findFactory(long generatorId) {
        return Jdbc.one(connection,
                "SELECT generator_id, pollution_consent_no FROM `FACTORY` WHERE generator_id = ?",
                r -> new Factory(Jdbc.nullableLong(r, "generator_id"),
                        r.getString("pollution_consent_no")),
                generatorId);
    }
}
