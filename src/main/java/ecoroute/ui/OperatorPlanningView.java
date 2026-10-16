package ecoroute.ui;

import ecoroute.model.Route;
import ecoroute.model.Zone;
import ecoroute.planning.*;
import ecoroute.service.UserSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static ecoroute.db.DatabaseContract.PLANNED;

/**
 * Operator Route Planning & Dispatch Screen:
 * - Filter pending eligible requests by route date and zone
 * - Preview capacity-constrained bin-packing (FFD) and TSP nearest-neighbour route plans
 * - View ordered stops (1..N) with distance estimates
 * - Inspect unassigned/ineligible requests with actionable reasons (capacity full, oversize, etc.)
 * - Atomically persist planned routes and stops to MySQL
 * - Dispatch planned routes to collection fleet
 */
public final class OperatorPlanningView extends JPanel {

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(OperatorPlanningView.class.getName());

    private final AppContext context;

    private final JTextField dateField;
    private final JComboBox<ZoneOption> zoneCombo;
    private final JButton previewButton;
    private final JButton saveButton;
    private final JButton refreshActiveButton;
    private final JProgressBar progressBar;

    // Plan Metrics Labels
    private final JLabel strategyLabel = new JLabel("Nearest Neighbour");
    private final JLabel routesCountLabel = new JLabel("0");
    private final JLabel stopsCountLabel = new JLabel("0");
    private final JLabel totalWeightLabel = new JLabel("0.0 kg");
    private final JLabel distanceLabel = new JLabel("0.0 km");
    private final JLabel unassignedCountLabel = new JLabel("0");

    // Table Models
    private final DefaultTableModel plannedStopsModel;
    private final JTable plannedStopsTable;

    private final DefaultTableModel unassignedModel;
    private final JTable unassignedTable;

    private final DefaultTableModel activeRoutesModel;
    private final JTable activeRoutesTable;
    private final JButton dispatchRouteButton;

    private DayPlan currentPlan = null;
    private List<Route> activeRoutesList = new ArrayList<>();

    public OperatorPlanningView(AppContext context) {
        this.context = context;
        setLayout(new BorderLayout(0, 16));
        setBackground(UITheme.BG_LIGHT);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        // Header Panel
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(UITheme.BG_LIGHT);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 0, 2));
        titleBlock.setBackground(UITheme.BG_LIGHT);
        JLabel pageTitle = new JLabel("Route Planning & Dispatch");
        pageTitle.setFont(UITheme.FONT_TITLE);
        pageTitle.setForeground(UITheme.TEXT_MAIN);
        JLabel pageSubtitle = UITheme.createMutedLabel("Algorithmic route generation, stop sequencing, and fleet dispatch");
        titleBlock.add(pageTitle);
        titleBlock.add(pageSubtitle);

        JPanel controlsBlock = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controlsBlock.setBackground(UITheme.BG_LIGHT);

        dateField = new JTextField(LocalDate.now().toString(), 10);
        zoneCombo = new JComboBox<>();
        zoneCombo.addItem(new ZoneOption(null, "All Zones"));

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(80, 20));

        previewButton = UITheme.createSecondaryButton("Preview Plan");
        previewButton.addActionListener(e -> previewPlan());

        saveButton = UITheme.createPrimaryButton("Save & Persist Plan");
        saveButton.setEnabled(false);
        saveButton.addActionListener(e -> saveCurrentPlan());

        controlsBlock.add(new JLabel("Date:"));
        controlsBlock.add(dateField);
        controlsBlock.add(new JLabel("Zone:"));
        controlsBlock.add(zoneCombo);
        controlsBlock.add(progressBar);
        controlsBlock.add(previewButton);
        controlsBlock.add(saveButton);

        topPanel.add(titleBlock, BorderLayout.WEST);
        topPanel.add(controlsBlock, BorderLayout.EAST);

        // Plan Summary KPI Banner
        JPanel summaryCard = UITheme.createCardPanel();
        summaryCard.setLayout(new GridLayout(1, 6, 12, 0));

        summaryCard.add(createSummaryBlock("Strategy:", strategyLabel));
        summaryCard.add(createSummaryBlock("Routes:", routesCountLabel));
        summaryCard.add(createSummaryBlock("Total Stops:", stopsCountLabel));
        summaryCard.add(createSummaryBlock("Planned Mass:", totalWeightLabel));
        summaryCard.add(createSummaryBlock("Approx Distance:", distanceLabel));
        summaryCard.add(createSummaryBlock("Unassigned / Ineligible:", unassignedCountLabel));

        // Center Tabbed Pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(UITheme.FONT_BODY_BOLD);

        // Tab 1: Planned Stops (Ordered Sequences)
        String[] stopCols = {"Route #", "Zone", "Vehicle", "Seq #", "Request ID", "Generator", "Est. kg", "Leg Distance"};
        plannedStopsModel = new DefaultTableModel(stopCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        plannedStopsTable = new JTable(plannedStopsModel);
        JPanel plannedPanel = new JPanel(new BorderLayout());
        plannedPanel.setBackground(UITheme.BG_LIGHT);
        plannedPanel.add(UITheme.createTableScrollPane(plannedStopsTable), BorderLayout.CENTER);
        tabbedPane.addTab("Proposed Route Stops", plannedPanel);

        // Tab 2: Unassigned / Ineligible Requests
        String[] unassignedCols = {"Request ID", "Generator", "Zone", "Demanded kg", "Reason Code", "Reason Description"};
        unassignedModel = new DefaultTableModel(unassignedCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        unassignedTable = new JTable(unassignedModel);
        JPanel unassignedPanel = new JPanel(new BorderLayout());
        unassignedPanel.setBackground(UITheme.BG_LIGHT);
        unassignedPanel.add(UITheme.createTableScrollPane(unassignedTable), BorderLayout.CENTER);
        tabbedPane.addTab("Unassigned & Ineligible Requests", unassignedPanel);

        // Tab 3: Active Routes in Database
        String[] activeCols = {"Route ID", "Date", "Zone ID", "Vehicle ID", "Status", "Stops"};
        activeRoutesModel = new DefaultTableModel(activeCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        activeRoutesTable = new JTable(activeRoutesModel);
        JPanel activePanel = new JPanel(new BorderLayout(0, 8));
        activePanel.setBackground(UITheme.BG_LIGHT);
        activePanel.setBorder(new EmptyBorder(8, 0, 0, 0));

        JPanel activeToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        activeToolbar.setBackground(UITheme.BG_LIGHT);

        refreshActiveButton = UITheme.createSecondaryButton("Refresh Active Routes");
        refreshActiveButton.addActionListener(e -> loadActiveRoutes());

        dispatchRouteButton = UITheme.createPrimaryButton("Dispatch Route to Drivers");
        dispatchRouteButton.setEnabled(false);
        dispatchRouteButton.addActionListener(e -> dispatchSelectedRoute());

        activeRoutesTable.getSelectionModel().addListSelectionListener(e -> {
            int row = activeRoutesTable.getSelectedRow();
            if (row >= 0 && row < activeRoutesList.size()) {
                Route r = activeRoutesList.get(row);
                dispatchRouteButton.setEnabled(PLANNED.equals(r.getStatus()));
            } else {
                dispatchRouteButton.setEnabled(false);
            }
        });

        activeToolbar.add(refreshActiveButton);
        activeToolbar.add(dispatchRouteButton);

        activePanel.add(activeToolbar, BorderLayout.NORTH);
        activePanel.add(UITheme.createTableScrollPane(activeRoutesTable), BorderLayout.CENTER);
        tabbedPane.addTab("Active Routes & Dispatch", activePanel);

        // Assemble Layout
        JPanel centerPanel = new JPanel(new BorderLayout(0, 14));
        centerPanel.setBackground(UITheme.BG_LIGHT);
        centerPanel.add(summaryCard, BorderLayout.NORTH);
        centerPanel.add(tabbedPane, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);

        loadZones();
        loadActiveRoutes();
    }

    private JPanel createSummaryBlock(String title, JLabel valueLabel) {
        JPanel block = new JPanel(new GridLayout(2, 1, 0, 2));
        block.setBackground(Color.WHITE);
        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(UITheme.FONT_SMALL);
        titleLbl.setForeground(UITheme.TEXT_MUTED);

        valueLabel.setFont(UITheme.FONT_HEADER);
        valueLabel.setForeground(UITheme.PRIMARY);

        block.add(titleLbl);
        block.add(valueLabel);
        return block;
    }

    private void loadZones() {
        UserSession session = context.getCurrentSession();
        if (session == null) return;
        try {
            List<Zone> zones = context.getCatalogService().zones(session);
            for (Zone z : zones) {
                zoneCombo.addItem(new ZoneOption(z.getZoneId(), "Zone " + z.getZoneId() + ": " + z.getZoneName()));
            }
        } catch (Exception ignored) {}
    }

    private void previewPlan() {
        UserSession session = context.getCurrentSession();
        if (session == null) return;

        LocalDate planDate;
        try {
            planDate = LocalDate.parse(dateField.getText().trim());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Enter a valid date in YYYY-MM-DD format.", "Invalid Date", JOptionPane.ERROR_MESSAGE);
            return;
        }

        ZoneOption selectedZone = (ZoneOption) zoneCombo.getSelectedItem();
        Long zoneId = selectedZone != null ? selectedZone.id : null;

        progressBar.setVisible(true);
        previewButton.setEnabled(false);
        saveButton.setEnabled(false);

        SwingWorker<DayPlan, Void> worker = new SwingWorker<>() {
            @Override
            protected DayPlan doInBackground() {
                return context.getRoutePlanningAdapterService().previewPlan(session, planDate, zoneId);
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                previewButton.setEnabled(true);
                try {
                    currentPlan = get();
                    displayPlan(currentPlan);
                    saveButton.setEnabled(currentPlan.getRouteCount() > 0);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(OperatorPlanningView.this,
                            "Planning preview failed: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void displayPlan(DayPlan plan) {
        strategyLabel.setText(plan.getStrategyName());
        routesCountLabel.setText(String.valueOf(plan.getRouteCount()));
        stopsCountLabel.setText(String.valueOf(plan.getTotalStops()));
        totalWeightLabel.setText(String.format("%,.1f kg", plan.getTotalPlannedWeight()));

        int totalUnlocated = plan.getRoutes().stream().mapToInt(RoutePlan::getUnlocatedStopCount).sum();
        String distText = String.format("~%.2f km", plan.getTotalApproxKilometres());
        if (totalUnlocated > 0) {
            distText += " (" + totalUnlocated + " stop" + (totalUnlocated == 1 ? "" : "s") + " without GPS — excluded from estimate)";
        }
        distanceLabel.setText(distText);
        unassignedCountLabel.setText(String.valueOf(plan.getUnassignedCount() + plan.getIneligibleCount()));

        // Display Planned Stops
        plannedStopsModel.setRowCount(0);
        int routeIdx = 1;
        for (RoutePlan rp : plan.getRoutes()) {
            for (StopPlan sp : rp.getStops()) {
                String distStr = sp.getDistanceFromPrevious() > 0 && sp.getDistanceFromPrevious() < Double.MAX_VALUE / 2
                        ? String.format("~%.2f km (straight-line)", sp.getDistanceFromPrevious() * 111.0)
                        : (sp.getStopSequence() == 1 ? "Start" : "N/A (no GPS coordinates)");
                plannedStopsModel.addRow(new Object[]{
                        "Route #" + routeIdx,
                        "Zone #" + rp.getZoneId(),
                        rp.getVehicleNumber() + " (" + rp.getVehicleId() + ")",
                        sp.getStopSequence(),
                        "#" + sp.getRequestId(),
                        sp.getGeneratorName(),
                        sp.getQuantity() + " kg",
                        distStr
                });
            }
            routeIdx++;
        }

        // Display Unassigned / Ineligible
        unassignedModel.setRowCount(0);
        for (UnassignedRequest u : plan.getUnassigned()) {
            unassignedModel.addRow(new Object[]{
                    "#" + u.getRequestId(),
                    u.getGeneratorName() != null ? u.getGeneratorName() : "Generator #" + u.getRequest().getGeneratorId(),
                    "Zone #" + u.getRequest().getZoneId(),
                    u.getQuantity() + " kg",
                    u.getReasonCode(),
                    u.getReasonMessage()
            });
        }
        for (UnassignedRequest u : plan.getIneligible()) {
            unassignedModel.addRow(new Object[]{
                    "#" + u.getRequestId(),
                    u.getGeneratorName() != null ? u.getGeneratorName() : "Generator #" + u.getRequest().getGeneratorId(),
                    "Zone #" + u.getRequest().getZoneId(),
                    u.getQuantity() + " kg",
                    u.getReasonCode(),
                    u.getReasonMessage()
            });
        }
    }

    private void saveCurrentPlan() {
        if (currentPlan == null || currentPlan.getRouteCount() == 0) return;

        UserSession session = context.getCurrentSession();
        int confirm = JOptionPane.showConfirmDialog(this,
                "Save and persist " + currentPlan.getRouteCount() + " route(s) with " + currentPlan.getTotalStops() + " stop(s) to the database?",
                "Confirm Plan Persistence", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        progressBar.setVisible(true);
        saveButton.setEnabled(false);

        SwingWorker<List<Long>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Long> doInBackground() {
                return context.getRoutePlanningAdapterService().savePlan(session, currentPlan);
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                try {
                    List<Long> savedRouteIds = get();
                    JOptionPane.showMessageDialog(OperatorPlanningView.this,
                            "Successfully saved " + savedRouteIds.size() + " route(s) to the database!\nGenerated Route IDs: " + savedRouteIds,
                            "Plan Saved", JOptionPane.INFORMATION_MESSAGE);
                    saveButton.setEnabled(false);
                    loadActiveRoutes();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(OperatorPlanningView.this,
                            "Failed to persist route plan: " + ex.getMessage(),
                            "Persistence Error", JOptionPane.ERROR_MESSAGE);
                    saveButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    public void refreshActiveRoutes() {
        loadActiveRoutes();
    }

    public void resetView() {
        LOGGER.log(java.util.logging.Level.FINE, "Resetting operator planning view");
        currentPlan = null;
        activeRoutesList = new ArrayList<>();
        strategyLabel.setText("Nearest Neighbour");
        routesCountLabel.setText("0");
        stopsCountLabel.setText("0");
        totalWeightLabel.setText("0.0 kg");
        distanceLabel.setText("0.0 km");
        unassignedCountLabel.setText("0");
        plannedStopsModel.setRowCount(0);
        unassignedModel.setRowCount(0);
        activeRoutesModel.setRowCount(0);
        saveButton.setEnabled(false);
        dispatchRouteButton.setEnabled(false);
    }

    private void loadActiveRoutes() {
        UserSession session = context.getCurrentSession();
        if (session == null) return;

        LocalDate date = LocalDate.now();
        try {
            date = LocalDate.parse(dateField.getText().trim());
        } catch (Exception ex) {
            LOGGER.log(java.util.logging.Level.FINE, "Using default date: " + ex.getMessage());
        }

        LocalDate finalDate = date;
        SwingWorker<List<Route>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Route> doInBackground() {
                return context.getRoutePersistenceService().findByDate(session, finalDate);
            }

            @Override
            protected void done() {
                try {
                    activeRoutesList = get();
                    activeRoutesModel.setRowCount(0);
                    for (Route r : activeRoutesList) {
                        int stopCount = context.getRoutePersistenceService().findStops(session, r.getRouteId()).size();
                        activeRoutesModel.addRow(new Object[]{
                                "#" + r.getRouteId(),
                                r.getRouteDate(),
                                "Zone #" + r.getZoneId(),
                                "Vehicle #" + r.getVehicleId(),
                                r.getStatus(),
                                stopCount + " stop(s)"
                        });
                    }
                } catch (Exception ex) {
                    LOGGER.log(java.util.logging.Level.WARNING, "Failed to load active routes: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void dispatchSelectedRoute() {
        int row = activeRoutesTable.getSelectedRow();
        if (row < 0 || row >= activeRoutesList.size()) return;

        Route route = activeRoutesList.get(row);
        if (!PLANNED.equals(route.getStatus())) {
            JOptionPane.showMessageDialog(this, "Only PLANNED routes can be dispatched.", "Cannot Dispatch", JOptionPane.WARNING_MESSAGE);
            return;
        }

        UserSession session = context.getCurrentSession();
        int confirm = JOptionPane.showConfirmDialog(this,
                "Dispatch Route #" + route.getRouteId() + " to IN_PROGRESS status?",
                "Confirm Dispatch", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                context.getCollectionService().startRoute(session, route.getRouteId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(OperatorPlanningView.this,
                            "Route #" + route.getRouteId() + " dispatched to IN_PROGRESS.",
                            "Dispatched", JOptionPane.INFORMATION_MESSAGE);
                    loadActiveRoutes();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(OperatorPlanningView.this,
                            "Dispatch failed: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private static class ZoneOption {
        final Long id;
        final String label;

        ZoneOption(Long id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
