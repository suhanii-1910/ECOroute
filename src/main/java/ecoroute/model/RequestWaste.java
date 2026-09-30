package ecoroute.model;

import java.math.BigDecimal;

/** JDBC data holder. A null surrogate ID means it has not been inserted yet. */
public class RequestWaste {
    private Long requestId;
    private Long categoryId;
    private BigDecimal estimatedQuantity;
    private BigDecimal actualQuantity;

    public RequestWaste() {}

    public RequestWaste(Long requestId, Long categoryId, BigDecimal estimatedQuantity, BigDecimal actualQuantity) {
        this.requestId = requestId;
        this.categoryId = categoryId;
        this.estimatedQuantity = estimatedQuantity;
        this.actualQuantity = actualQuantity;
    }

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public BigDecimal getEstimatedQuantity() { return estimatedQuantity; }
    public void setEstimatedQuantity(BigDecimal estimatedQuantity) { this.estimatedQuantity = estimatedQuantity; }

    public BigDecimal getActualQuantity() { return actualQuantity; }
    public void setActualQuantity(BigDecimal actualQuantity) { this.actualQuantity = actualQuantity; }

    @Override
    public String toString() {
        return "RequestWaste{" + requestId + ", " + categoryId + ", " + estimatedQuantity + ", " + actualQuantity + "}";
    }
}
