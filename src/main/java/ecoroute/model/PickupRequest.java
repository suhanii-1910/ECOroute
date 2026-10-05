package ecoroute.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** JDBC data holder. A null surrogate ID means it has not been inserted yet. */
public class PickupRequest {
    private Long requestId;
    private Long generatorId;
    private LocalDateTime requestDate;
    // Required when persisted; service and DAO reject a missing date.
    private LocalDate preferredPickupDate;
    private String status;
    private LocalDateTime completionDate;
    private String remarks;

    public PickupRequest() {}

    public PickupRequest(Long requestId, Long generatorId, LocalDateTime requestDate, LocalDate preferredPickupDate, String status, LocalDateTime completionDate, String remarks) {
        this.requestId = requestId;
        this.generatorId = generatorId;
        this.requestDate = requestDate;
        this.preferredPickupDate = preferredPickupDate;
        this.status = status;
        this.completionDate = completionDate;
        this.remarks = remarks;
    }

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }

    public Long getGeneratorId() { return generatorId; }
    public void setGeneratorId(Long generatorId) { this.generatorId = generatorId; }

    public LocalDateTime getRequestDate() { return requestDate; }
    public void setRequestDate(LocalDateTime requestDate) { this.requestDate = requestDate; }

    public LocalDate getPreferredPickupDate() { return preferredPickupDate; }
    public void setPreferredPickupDate(LocalDate preferredPickupDate) { this.preferredPickupDate = preferredPickupDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCompletionDate() { return completionDate; }
    public void setCompletionDate(LocalDateTime completionDate) { this.completionDate = completionDate; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    @Override
    public String toString() {
        return "PickupRequest{" + requestId + ", " + generatorId + "}";
    }
}
