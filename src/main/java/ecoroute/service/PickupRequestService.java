package ecoroute.service;

import ecoroute.dao.*;
import ecoroute.db.Database;
import ecoroute.model.*;
import ecoroute.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static ecoroute.db.DatabaseContract.*;

public final class PickupRequestService {
    public record WasteLine(long categoryId, BigDecimal estimatedQuantity) {}
    private final Database database;
    public PickupRequestService(Database database) { this.database = database; }

    public long create(UserSession session, long generatorId, LocalDate preferredDate, String remarks, List<WasteLine> lines) {
        Validation.id(generatorId, "Generator");
        Validation.require(preferredDate != null, "Preferred pickup date is required.");
        Validation.require(!preferredDate.isBefore(LocalDate.now()), "Preferred date cannot be in the past.");
        Validation.require(lines != null && !lines.isEmpty(), "At least one waste line is required.");
        List<WasteLine> copy = new java.util.ArrayList<>(lines);
        Set<Long> categories = new HashSet<>();
        for (WasteLine line : copy) {
            Validation.require(line != null, "Waste line is required.");
            Validation.id(line.categoryId(), "Category");
            Validation.quantity(line.estimatedQuantity(), true, "Estimated quantity");
            Validation.require(categories.add(line.categoryId()), "Duplicate waste category.");
        }
        return database.transaction(c -> {
            Access.generator(c, session, generatorId);
            WasteGenerator generator = new GeneratorDAO(c).lockById(generatorId)
                    .orElseThrow(() -> new InvalidRequestException("Generator does not exist."));
            Validation.require(generator.isActive(), "Generator is inactive.");
            long requestId = new RequestDAO(c).insert(new PickupRequest(null, generatorId, LocalDateTime.now(),
                    preferredDate, PENDING, null, remarks));
            RequestWasteDAO waste = new RequestWasteDAO(c);
            for (WasteLine line : copy) {
                Validation.require(new CategoryDAO(c).findById(line.categoryId()).isPresent(), "Category does not exist.");
                waste.insertLine(new RequestWaste(requestId, line.categoryId(), line.estimatedQuantity(), null));
            }
            return requestId;
        });
    }

    public List<PickupRequest> findByGenerator(UserSession session, long generatorId) {
        return database.read(c -> { Access.generator(c, session, generatorId); return new RequestDAO(c).findByGenerator(generatorId); });
    }
    public List<PickupRequest> findPending(UserSession session) {
        return database.read(c -> { Access.staff(c, session); return new RequestDAO(c).findPending(); });
    }
    public Optional<PickupRequest> findById(UserSession session, long requestId) {
        return database.read(c -> {
            Access.current(c, session);
            Optional<PickupRequest> result = new RequestDAO(c).findById(requestId);
            result.ifPresent(r -> Access.generator(c, session, r.getGeneratorId()));
            return result;
        });
    }
    public List<RequestWaste> findWaste(UserSession session, long requestId) {
        return database.read(c -> {
            PickupRequest request = new RequestDAO(c).findById(requestId)
                    .orElseThrow(() -> new InvalidRequestException("Request does not exist."));
            Access.generator(c, session, request.getGeneratorId());
            return new RequestWasteDAO(c).findByRequest(requestId);
        });
    }
    public void cancel(UserSession session, long requestId) {
        database.transaction(c -> {
            RequestDAO requests = new RequestDAO(c);
            PickupRequest request = requests.lockById(requestId)
                    .orElseThrow(() -> new InvalidRequestException("Request does not exist."));
            Access.generator(c, session, request.getGeneratorId());
            Validation.transition(request.getStatus(), PENDING);
            requests.cancel(requestId);
            return null;
        });
    }
}
