package ecoroute.model;


/** JDBC data holder. A null surrogate ID means it has not been inserted yet. */
public class WasteCategory {
    private Long categoryId;
    private String categoryName;
    private String description;
    private String hazardLevel;

    public WasteCategory() {}

    public WasteCategory(Long categoryId, String categoryName, String description, String hazardLevel) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.description = description;
        this.hazardLevel = hazardLevel;
    }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getHazardLevel() { return hazardLevel; }
    public void setHazardLevel(String hazardLevel) { this.hazardLevel = hazardLevel; }

    @Override
    public String toString() {
        return "WasteCategory{" + categoryId + ", " + categoryName + "}";
    }
}
