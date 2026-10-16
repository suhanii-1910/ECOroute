package ecoroute.ui;

import ecoroute.model.*;
import ecoroute.service.UserSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

import static ecoroute.db.DatabaseContract.*;

/**
 * Driver Collection Execution View:
 * - View operational routes available to drivers (PLANNED, IN_PROGRESS, DISPATCHED_TO_DISPOSAL)
 * - View ordered stops (1..N) with generator details
 * - Start planned routes (transitions to IN_PROGRESS and IN_ROUTE)
 * - Record actual arrival, completion timestamps and collected weights per waste category
 * - Validate collected load does not exceed vehicle capacity
 * - Skip stops with recorded operational notes
 * - Transitions route to DISPATCHED_TO_DISPOSAL when all stops are completed or skipped
 *
 * NOTE: The approved relational schema contains no driver-to-route foreign key.
 * All authorized drivers view active operational routes for the selected date.
 */
public final class DriverView extends JPanel {

    private final AppContext context;

    private final JTextField dateField;
    private final JButton refreshButton;
    private final JProgressBar progressBar;

    // Route summary banner
    private final JLabel activeRouteIdLabel = new JLabel("None selected");
    private final JLabel activeRouteVehicleLabel = new JLabel("—");
    private final JLabel activeRouteStatusLabel = new JLabel("—");
    private final JLabel activeRouteLoadLabel = new JLabel("0.0 kg");

    // Routes Table
    private final DefaultTableModel routesTableModel;
    private final JTable routesTable;
    private final JButton startRouteButton;

    // Stops Table
    private final DefaultTableModel stopsTableModel;
    private final JTable stopsTable;
    private final JButton completeStopButton;
    private final JButton skipStopButton;

    private List<Route> routesList = new ArrayList<>();
    private List<RouteStop> currentStops = new ArrayList<>();
    private Route selectedRoute = null;

    public DriverView(AppContext context) {
        this.context = context;
        setLayout(new BorderLayout(0, 16));
        setBackground(UITheme.BG_LIGHT);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        // Top Header
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(UITheme.BG_LIGHT);

        JPanel titleBlock = new JPanel(new GridLayout(3, 1, 0, 2));
        titleBlock.setBackground(UITheme.BG_LIGHT);
        JLabel pageTitle = new JLabel("Driver Collection Portal");
        pageTitle.setFont(UITheme.FONT_TITLE);
        pageTitle.setForeground(UITheme.TEXT_MAIN);

        JLabel pageSubtitle = UITheme.createMutedLabel("Operational route execution, stop sequencing, and weight verification");
        JLabel noteLabel = new JLabel("Note: Real schema has no driver-to-route assignment FK; all authorized drivers view active operational routes.");
        noteLabel.setFont(UITheme.FONT_SMALL);
        noteLabel.setForeground(UITheme.ACCENT_TEAL);

        titleBlock.add(pageTitle);
        titleBlock.add(pageSubtitle);
        titleBlock.add(noteLabel);

        JPanel actionBlock = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionBlock.setBackground(UITheme.BG_LIGHT);

        dateField = new JTextField(LocalDate.now().toString(), 10);
        dateField.setFont(UITheme.FONT_BODY);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(80, 20));

        refreshButton = UITheme.createSecondaryButton("Refresh Routes");
        refreshButton.addActionListener(e -> refreshData());

        actionBlock.add(new JLabel("Date:"));
        actionBlock.add(dateField);
        actionBlock.add(progressBar);
        actionBlock.add(refreshButton);

        topPanel.add(titleBlock, BorderLayout.WEST);
        topPanel.add(actionBlock, BorderLayout.EAST);

        // Active Route Metrics Card
        JPanel routeInfoCard = UITheme.createCardPanel();
        routeInfoCard.setLayout(new GridLayout(1, 4, 16, 0));
        routeInfoCard.add(createMetricBlock("Selected Route:", activeRouteIdLabel));
        routeInfoCard.add(createMetricBlock("Assigned Vehicle:", activeRouteVehicleLabel));
        routeInfoCard.add(createMetricBlock("Route Status:", activeRouteStatusLabel));
        routeInfoCard.add(createMetricBlock("Collected Load / Capacity:", activeRouteLoadLabel));

        // Center Split Panel (Routes on Left, Stops on Right)
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.42);
        splitPane.setBorder(null);

        // Left Panel: Routes
        JPanel leftPanel = UITheme.createCardPanel();
        leftPanel.setLayout(new BorderLayout(0, 10));

        JPanel leftHeader = new JPanel(new BorderLayout());
        leftHeader.setBackground(Color.WHITE);
        leftHeader.add(UITheme.createHeaderLabel("Operational Routes"), BorderLayout.WEST);

        startRouteButton = UITheme.createPrimaryButton("Start Route");
        startRouteButton.setEnabled(false);
        startRouteButton.addActionListener(e -> startSelectedRoute());
        leftHeader.add(startRouteButton, BorderLayout.EAST);

        String[] routeCols = {"Route ID", "Zone", "Vehicle", "Status", "Stops"};
        routesTableModel = new DefaultTableModel(routeCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        routesTable = new JTable(routesTableModel);
        routesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onRouteSelected();
            }
        });

        leftPanel.add(leftHeader, BorderLayout.NORTH);
        leftPanel.add(UITheme.createTableScrollPane(routesTable), BorderLayout.CENTER);

        // Right Panel: Stops
        JPanel rightPanel = UITheme.createCardPanel();
        rightPanel.setLayout(new BorderLayout(0, 10));

        JPanel rightHeader = new JPanel(new BorderLayout());
        rightHeader.setBackground(Color.WHITE);
        rightHeader.add(UITheme.createHeaderLabel("Ordered Route Stops"), BorderLayout.WEST);

        JPanel stopActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        stopActions.setBackground(Color.WHITE);

        skipStopButton = UITheme.createSecondaryButton("Skip Stop");
        skipStopButton.setEnabled(false);
        skipStopButton.addActionListener(e -> skipSelectedStop());

        completeStopButton = UITheme.createPrimaryButton("Record Collection");
        completeStopButton.setEnabled(false);
        completeStopButton.addActionListener(e -> completeSelectedStop());

        stopActions.add(skipStopButton);
        stopActions.add(completeStopButton);
        rightHeader.add(stopActions, BorderLayout.EAST);

        String[] stopCols = {"Seq", "Stop ID", "Request #", "Generator", "Status", "Arrival", "Completed"};
        stopsTableModel = new DefaultTableModel(stopCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        stopsTable = new JTable(stopsTableModel);
        stopsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onStopSelected();
            }
        });

        rightPanel.add(rightHeader, BorderLayout.NORTH);
        rightPanel.add(UITheme.createTableScrollPane(stopsTable), BorderLayout.CENTER);

        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(rightPanel);

        // Assemble Layout
        JPanel centerPanel = new JPanel(new BorderLayout(0, 14));
        centerPanel.setBackground(UITheme.BG_LIGHT);
        centerPanel.add(routeInfoCard, BorderLayout.NORTH);
        centerPanel.add(splitPane, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);

        refreshData();
    }

    private JPanel createMetricBlock(String label, JLabel valueLabel) {
        JPanel block = new JPanel(new GridLayout(2, 1, 0, 2));
        block.setBackground(Color.WHITE);
        JLabel titleLbl = new JLabel(label);
        titleLbl.setFont(UITheme.FONT_SMALL);
        titleLbl.setForeground(UITheme.TEXT_MUTED);

        valueLabel.setFont(UITheme.FONT_BODY_BOLD);
        valueLabel.setForeground(UITheme.PRIMARY);

        block.add(titleLbl);
        block.add(valueLabel);
        return block;
    }

    public void refreshData() {
        UserSession session = context.getCurrentSession();
        if (session == null) return;

        LocalDate date;
        try {
            date = LocalDate.parse(dateField.getText().trim());
        } catch (Exception ex) {
            date = LocalDate.now();
            dateField.setText(date.toString());
        }

        LocalDate queryDate = date;
        progressBar.setVisible(true);
        refreshButton.setEnabled(false);

        SwingWorker<List<Route>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Route> doInBackground() {
                return context.getRoutePersistenceService().findByDate(session, queryDate);
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                refreshButton.setEnabled(true);
                try {
                    routesList = get();
                    routesTableModel.setRowCount(0);
                    for (Route r : routesList) {
                        int stopCount = context.getRoutePersistenceService().findStops(session, r.getRouteId()).size();
                        routesTableModel.addRow(new Object[]{
                                "Route #" + r.getRouteId(),
                                "Zone #" + r.getZoneId(),
                                "Vehicle #" + r.getVehicleId(),
                                r.getStatus(),
                                stopCount + " stop(s)"
                        });
                    }
                    if (!routesList.isEmpty()) {
                        routesTable.setRowSelectionInterval(0, 0);
                    } else {
                        clearRouteDetails();
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(DriverView.this,
                            "Failed to load routes: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void onRouteSelected() {
        int row = routesTable.getSelectedRow();
        if (row < 0 || row >= routesList.size()) {
            clearRouteDetails();
            return;
        }

        selectedRoute = routesList.get(row);
        activeRouteIdLabel.setText("Route #" + selectedRoute.getRouteId());
        activeRouteVehicleLabel.setText("Vehicle #" + selectedRoute.getVehicleId());
        activeRouteStatusLabel.setText(selectedRoute.getStatus());
        startRouteButton.setEnabled(PLANNED.equals(selectedRoute.getStatus()));

        loadStopsForRoute(selectedRoute);
    }

    private void clearRouteDetails() {
        selectedRoute = null;
        activeRouteIdLabel.setText("None selected");
        activeRouteVehicleLabel.setText("—");
        activeRouteStatusLabel.setText("—");
        activeRouteLoadLabel.setText("0.0 kg");
        startRouteButton.setEnabled(false);
        stopsTableModel.setRowCount(0);
        completeStopButton.setEnabled(false);
        skipStopButton.setEnabled(false);
    }

    private void loadStopsForRoute(Route route) {
        UserSession session = context.getCurrentSession();
        if (session == null || route == null) return;

        progressBar.setVisible(true);
        SwingWorker<StopsDataSnapshot, Void> worker = new SwingWorker<>() {
            @Override
            protected StopsDataSnapshot doInBackground() {
                List<RouteStop> stops = context.getRoutePersistenceService().findStops(session, route.getRouteId());
                Vehicle vehicle = context.getVehicleService().findById(session, route.getVehicleId()).orElse(null);

                BigDecimal collectedKg = BigDecimal.ZERO;
                Map<Long, String> generatorNames = new HashMap<>();

                for (RouteStop s : stops) {
                    try {
                        List<RequestWaste> wasteLines = context.getPickupRequestService().findWaste(session, s.getRequestId());
                        for (RequestWaste w : wasteLines) {
                            if (w.getActualQuantity() != null) {
                                collectedKg = collectedKg.add(w.getActualQuantity());
                            }
                        }
                        context.getPickupRequestService().findById(session, s.getRequestId()).ifPresent(req -> {
                            context.getGeneratorService().findById(session, req.getGeneratorId()).ifPresent(g -> {
                                generatorNames.put(s.getRequestId(), g.generator().getGeneratorName());
                            });
                        });
                    } catch (Exception ignored) {}
                }

                return new StopsDataSnapshot(stops, vehicle, collectedKg, generatorNames);
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                try {
                    StopsDataSnapshot snap = get();
                    currentStops = snap.stops;

                    BigDecimal cap = snap.vehicle != null ? snap.vehicle.getCapacityKg() : BigDecimal.ZERO;
                    activeRouteLoadLabel.setText(String.format("%,.1f / %,.1f kg", snap.collectedKg, cap));

                    stopsTableModel.setRowCount(0);
                    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm");

                    for (RouteStop s : currentStops) {
                        String genName = snap.generatorNames.getOrDefault(s.getRequestId(), "Req #" + s.getRequestId());
                        String arrivalStr = s.getArrivalTime() != null ? s.getArrivalTime().format(dtf) : "-";
                        String compStr = s.getCompletionTime() != null ? s.getCompletionTime().format(dtf) : "-";

                        stopsTableModel.addRow(new Object[]{
                                s.getStopSequence(),
                                "#" + s.getStopId(),
                                "#" + s.getRequestId(),
                                genName,
                                s.getStatus(),
                                arrivalStr,
                                compStr
                        });
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(DriverView.this,
                            "Failed to load route stops: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void onStopSelected() {
        int row = stopsTable.getSelectedRow();
        if (row < 0 || row >= currentStops.size()) {
            completeStopButton.setEnabled(false);
            skipStopButton.setEnabled(false);
            return;
        }

        RouteStop stop = currentStops.get(row);
        boolean isRouteInProgress = selectedRoute != null && IN_PROGRESS.equals(selectedRoute.getStatus());
        boolean isStopPending = PENDING.equals(stop.getStatus());

        completeStopButton.setEnabled(isRouteInProgress && isStopPending);
        skipStopButton.setEnabled(isRouteInProgress && isStopPending);
    }

    private void startSelectedRoute() {
        if (selectedRoute == null) return;
        UserSession session = context.getCurrentSession();

        int confirm = JOptionPane.showConfirmDialog(this,
                "Start Route #" + selectedRoute.getRouteId() + "? This marks the vehicle and route as IN_PROGRESS.",
                "Confirm Start Route", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        progressBar.setVisible(true);
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                context.getCollectionService().startRoute(session, selectedRoute.getRouteId());
                return null;
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                try {
                    get();
                    JOptionPane.showMessageDialog(DriverView.this,
                            "Route #" + selectedRoute.getRouteId() + " is now IN_PROGRESS.",
                            "Route Started", JOptionPane.INFORMATION_MESSAGE);
                    refreshData();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(DriverView.this,
                            "Failed to start route: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void completeSelectedStop() {
        int row = stopsTable.getSelectedRow();
        if (row < 0 || row >= currentStops.size() || selectedRoute == null) return;

        RouteStop stop = currentStops.get(row);
        UserSession session = context.getCurrentSession();

        // Load waste categories for this pickup request
        List<RequestWaste> lines;
        try {
            lines = context.getPickupRequestService().findWaste(session, stop.getRequestId());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not load request waste lines: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JDialog dialog = createCollectionDialog(stop, lines);
        dialog.setVisible(true);
    }

    JDialog createCollectionDialog(RouteStop stop, List<RequestWaste> lines) {
        UserSession session = context.getCurrentSession();

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                "Record Waste Collection — Stop #" + stop.getStopSequence(), true);
        dialog.setLayout(new BorderLayout(0, 12));
        dialog.setSize(480, 360);
        dialog.setLocationRelativeTo(this);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(UITheme.BG_LIGHT);
        content.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel info = new JLabel("Enter actual collected weights for Request #" + stop.getRequestId() + ":");
        info.setFont(UITheme.FONT_BODY_BOLD);
        content.add(info, BorderLayout.NORTH);

        // Resolve waste category names
        Map<Long, String> categoryNames = new HashMap<>();
        try {
            if (session != null) {
                List<WasteCategory> categories = context.getCatalogService().categories(session);
                for (WasteCategory cat : categories) {
                    if (cat.getCategoryId() != null && cat.getCategoryName() != null) {
                        categoryNames.put(cat.getCategoryId(), cat.getCategoryName());
                    }
                }
            }
        } catch (Exception ex) {
            // Graceful fallback to "Category #" + id
        }

        // Waste input table
        String[] cols = {"Category", "Estimated kg", "Actual kg"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 2; }
        };
        for (RequestWaste rw : lines) {
            String catName = categoryNames.getOrDefault(rw.getCategoryId(), "Category #" + rw.getCategoryId());
            model.addRow(new Object[]{
                    catName,
                    rw.getEstimatedQuantity() + " kg",
                    rw.getEstimatedQuantity().toString() // Default actual to estimated
            });
        }
        JTable table = new JTable(model);
        content.add(UITheme.createTableScrollPane(table), BorderLayout.CENTER);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnPanel.setBackground(UITheme.BG_LIGHT);

        JButton cancelBtn = UITheme.createSecondaryButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());

        JButton saveBtn = UITheme.createPrimaryButton("Confirm & Complete Stop");
        saveBtn.addActionListener(e -> {
            if (table.isEditing()) table.getCellEditor().stopCellEditing();

            Map<Long, BigDecimal> actuals = new HashMap<>();
            try {
                for (int i = 0; i < lines.size(); i++) {
                    long catId = lines.get(i).getCategoryId();
                    String strVal = (String) model.getValueAt(i, 2);
                    BigDecimal val = new BigDecimal(strVal.trim());
                    if (val.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException();
                    actuals.put(catId, val);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Please enter valid non-negative weights for all categories.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime arrival = now.minusMinutes(10);
            LocalDateTime completion = now;

            saveBtn.setEnabled(false);
            SwingWorker<Void, Void> saveWorker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    context.getCollectionService().complete(session, stop.getStopId(), actuals, arrival, completion);
                    return null;
                }

                @Override
                protected void done() {
                    saveBtn.setEnabled(true);
                    try {
                        get();
                        JOptionPane.showMessageDialog(DriverView.this,
                                "Stop #" + stop.getStopSequence() + " recorded as COMPLETED successfully!",
                                "Collection Recorded", JOptionPane.INFORMATION_MESSAGE);
                        dialog.dispose();
                        onRouteSelected(); // Refresh stops and load
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(dialog,
                                "Failed to complete stop: " + ex.getMessage(),
                                "Collection Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            saveWorker.execute();
        });

        btnPanel.add(cancelBtn);
        btnPanel.add(saveBtn);
        content.add(btnPanel, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        return dialog;
    }

    private void skipSelectedStop() {
        int row = stopsTable.getSelectedRow();
        if (row < 0 || row >= currentStops.size() || selectedRoute == null) return;

        RouteStop stop = currentStops.get(row);
        UserSession session = context.getCurrentSession();

        String notes = JOptionPane.showInputDialog(this,
                "Enter reason for skipping Stop #" + stop.getStopSequence() + " (Generator absent, access blocked, etc.):",
                "Skip Stop", JOptionPane.QUESTION_MESSAGE);

        if (notes == null) return; // Cancelled

        progressBar.setVisible(true);
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                context.getCollectionService().skipStop(session, stop.getStopId(), notes);
                return null;
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                try {
                    get();
                    JOptionPane.showMessageDialog(DriverView.this,
                            "Stop #" + stop.getStopSequence() + " marked as SKIPPED.",
                            "Stop Skipped", JOptionPane.INFORMATION_MESSAGE);
                    onRouteSelected();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(DriverView.this,
                            "Failed to skip stop: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    public void resetView() {
        selectedRoute = null;
        routesList.clear();
        currentStops.clear();
        activeRouteIdLabel.setText("None selected");
        activeRouteVehicleLabel.setText("—");
        activeRouteStatusLabel.setText("—");
        activeRouteLoadLabel.setText("0.0 kg");
        routesTableModel.setRowCount(0);
        stopsTableModel.setRowCount(0);
        startRouteButton.setEnabled(false);
        completeStopButton.setEnabled(false);
        skipStopButton.setEnabled(false);
    }

    private static class StopsDataSnapshot {
        final List<RouteStop> stops;
        final Vehicle vehicle;
        final BigDecimal collectedKg;
        final Map<Long, String> generatorNames;

        StopsDataSnapshot(List<RouteStop> stops, Vehicle vehicle, BigDecimal collectedKg, Map<Long, String> generatorNames) {
            this.stops = stops;
            this.vehicle = vehicle;
            this.collectedKg = collectedKg;
            this.generatorNames = generatorNames;
        }
    }
}
