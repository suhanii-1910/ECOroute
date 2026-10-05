package ecoroute.dao;
import ecoroute.model.*;
import java.sql.Connection;
import java.util.Optional;

public final class GeneratorSubtypeDAO {
    private final Connection connection;
    public GeneratorSubtypeDAO(Connection connection) { this.connection = connection; }

    public void insert(Hospital value) {
        Jdbc.change(
                connection,
                "INSERT INTO `HOSPITAL` (generator_id, license_number, biomedical_auth_number, auth_expiry_date) VALUES (?, ?, ?, ?)",
                value.getGeneratorId(),
                value.getLicenseNumber(),
                value.getBiomedicalAuthNumber(),
                value.getAuthExpiryDate());
    }

    public Optional<Hospital> findHospital(long generatorId) {
        return Jdbc.one(connection, "SELECT generator_id, license_number, biomedical_auth_number, auth_expiry_date FROM `HOSPITAL` WHERE generator_id = ?",
                r -> new Hospital(Jdbc.nullableLong(r, "generator_id"), r.getString("license_number"), r.getString("biomedical_auth_number"), r.getObject("auth_expiry_date", java.time.LocalDate.class)), generatorId);
    }

    public void insert(HousingSociety value) {
        Jdbc.change(
                connection,
                "INSERT INTO `HOUSING_SOCIETY` (generator_id, registration_number, number_of_flats) VALUES (?, ?, ?)",
                value.getGeneratorId(),
                value.getRegistrationNumber(),
                value.getNumberOfFlats());
    }

    public Optional<HousingSociety> findHousingSociety(long generatorId) {
        return Jdbc.one(connection, "SELECT generator_id, registration_number, number_of_flats FROM `HOUSING_SOCIETY` WHERE generator_id = ?",
                r -> new HousingSociety(Jdbc.nullableLong(r, "generator_id"), r.getString("registration_number"), r.getObject("number_of_flats", Integer.class)), generatorId);
    }

    public void insert(Factory value) {
        Jdbc.change(
                connection,
                "INSERT INTO `FACTORY` (generator_id, industry_type, pollution_consent_no, consent_expiry_date) VALUES (?, ?, ?, ?)",
                value.getGeneratorId(),
                value.getIndustryType(),
                value.getPollutionConsentNo(),
                value.getConsentExpiryDate());
    }

    public Optional<Factory> findFactory(long generatorId) {
        return Jdbc.one(connection, "SELECT generator_id, industry_type, pollution_consent_no, consent_expiry_date FROM `FACTORY` WHERE generator_id = ?",
                r -> new Factory(Jdbc.nullableLong(r, "generator_id"), r.getString("industry_type"), r.getString("pollution_consent_no"), r.getObject("consent_expiry_date", java.time.LocalDate.class)), generatorId);
    }
}
