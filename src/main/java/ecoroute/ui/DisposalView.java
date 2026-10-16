package ecoroute.ui;

import ecoroute.model.*;
import ecoroute.service.DisposalService;
import ecoroute.service.UserSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static ecoroute.db.DatabaseContract.*;

/**
 * Disposal Facility Check-in & Weigh-in View:
 * - View routes awaiting disposal (DISPATCHED_TO_DISPOSAL)
 * - Detailed waste stream status (collected categories, weights, pending vs disposed)
 * - Recommend nearest suitable facility with remaining capacity for route waste categories
 * - Dynamic compatibility evaluation: prevent submission to incompatible facilities with clear guidance
 * - Multi-facility disposal support for mixed-category routes
 * - Record gross and tare vehicle weights invoking stored procedure sp_record_disposal
 * - Stored generated column net_disposed_weight_kg = gross_weight_kg - tare_weight_kg
 * - View complete historical disposal receipts and audit logs
 */
public final class DisposalView extends JPanel {

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(DisposalView.class.getName());

    private final AppContext context;

    private final JComboBox<RouteOption> routeCombo;
    private final JLabel routeWasteStatusLabel = new JLabel("Select a route to view waste streams...");
    private final JLabel recommendedFacilityLabel = new JLabel("Select a route to find suitable facilities...");
    private final JComboBox<FacilityOption> facilityCombo;
    private final JLabel facilityCompatibilityLabel = new JLabel(" ");
    private final JTextField grossWeightField;
    private final JTextField tareWeightField;
    private final JLabel netWeightLabel = new JLabel("0.000 kg");
    private final JTextField receiptField;
    private final JButton submitDisposalButton;
    private final JProgressBar progressBar;

    private final DefaultTableModel recordsTableModel;
    private final JTable recordsTable;
    private final JButton refreshButton;

    private List<Route> pendingDisposalRoutes = new ArrayList<>();
    private List<DisposalFacility> facilitiesList = new ArrayList<>();
    private List<DisposalRecord> recordsList = new ArrayList<>();

    public DisposalView(AppContext context) {
        this.context = context;
        setLayout(new BorderLayout(0, 16));
        setBackground(UITheme.BG_LIGHT);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        // Top Header
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(UITheme.BG_LIGHT);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 0, 2));
        titleBlock.setBackground(UITheme.BG_LIGHT);
        JLabel pageTitle = new JLabel("Disposal Facility Weigh-in & Records");
        pageTitle.setFont(UITheme.FONT_TITLE);
        pageTitle.setForeground(UITheme.TEXT_MAIN);
        JLabel pageSubtitle = UITheme.createMutedLabel("Record route disposal via MySQL procedure sp_record_disposal and audit receipts");
        titleBlock.add(pageTitle);
        titleBlock.add(pageSubtitle);

        JPanel actionBlock = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionBlock.setBackground(UITheme.BG_LIGHT);
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(80, 20));

        refreshButton = UITheme.createSecondaryButton("Refresh Data");
        refreshButton.addActionListener(e -> refreshData());

        actionBlock.add(progressBar);
        actionBlock.add(refreshButton);

        topPanel.add(titleBlock, BorderLayout.WEST);
        topPanel.add(actionBlock, BorderLayout.EAST);

        // Center Split (Form on Left / Top, History Table on Right / Bottom)
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.48);
        splitPane.setBorder(null);

        // Left Panel: Check-in / Weigh-in Form
        JPanel formCard = UITheme.createCardPanel();
        formCard.setLayout(new BorderLayout(0, 14));

        JLabel formTitle = UITheme.createHeaderLabel("Record Route Disposal");
        formCard.add(formTitle, BorderLayout.NORTH);

        JPanel formBody = new JPanel(new GridBagLayout());
        formBody.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 6, 5, 6);

        // Row 0: Route selector
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1; gbc.weightx = 0.35;
        JLabel routeLbl = new JLabel("Route for Disposal:");
        routeLbl.setFont(UITheme.FONT_BODY_BOLD);
        formBody.add(routeLbl, gbc);

        gbc.gridx = 1; gbc.weightx = 0.65;
        routeCombo = new JComboBox<>();
        routeCombo.setFont(UITheme.FONT_BODY);
        routeCombo.addActionListener(e -> onRouteSelected());
        formBody.add(routeCombo, gbc);

        // Row 1: Waste Streams on Selected Route
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        JPanel wasteCard = new JPanel(new BorderLayout(6, 4));
        wasteCard.setBackground(new Color(248, 250, 252));
        wasteCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
                new EmptyBorder(6, 8, 6, 8)
        ));
        JLabel wasteTitle = new JLabel("Route Waste Stream Status:");
        wasteTitle.setFont(UITheme.FONT_SMALL);
        wasteTitle.setForeground(UITheme.TEXT_MUTED);
        routeWasteStatusLabel.setFont(UITheme.FONT_BODY);
        routeWasteStatusLabel.setForeground(UITheme.TEXT_MAIN);
        wasteCard.add(wasteTitle, BorderLayout.NORTH);
        wasteCard.add(routeWasteStatusLabel, BorderLayout.CENTER);
        formBody.add(wasteCard, gbc);

        // Row 2: Nearest Facility Recommendation Banner
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        JPanel recCard = new JPanel(new BorderLayout(6, 4));
        recCard.setBackground(new Color(240, 249, 255));
        recCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(186, 230, 253), 1),
                new EmptyBorder(6, 8, 6, 8)
        ));
        JLabel recTitle = new JLabel("Nearest Suitable Facility Recommendation:");
        recTitle.setFont(UITheme.FONT_SMALL);
        recTitle.setForeground(new Color(3, 105, 161));
        recommendedFacilityLabel.setFont(UITheme.FONT_BODY_BOLD);
        recommendedFacilityLabel.setForeground(new Color(12, 74, 110));
        recCard.add(recTitle, BorderLayout.NORTH);
        recCard.add(recommendedFacilityLabel, BorderLayout.CENTER);
        formBody.add(recCard, gbc);

        // Row 3: Destination Facility selector
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1; gbc.weightx = 0.35;
        JLabel facLbl = new JLabel("Destination Facility:");
        facLbl.setFont(UITheme.FONT_BODY_BOLD);
        formBody.add(facLbl, gbc);

        gbc.gridx = 1; gbc.weightx = 0.65;
        facilityCombo = new JComboBox<>();
        facilityCombo.setFont(UITheme.FONT_BODY);
        facilityCombo.addActionListener(e -> evaluateFacilityCompatibility());
        formBody.add(facilityCombo, gbc);

        // Row 4: Facility Compatibility Status Banner
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        facilityCompatibilityLabel.setFont(UITheme.FONT_BODY);
        facilityCompatibilityLabel.setBorder(new EmptyBorder(2, 4, 2, 4));
        formBody.add(facilityCompatibilityLabel, gbc);

        // Row 5: Gross Weight
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 1; gbc.weightx = 0.35;
        JLabel grossLbl = new JLabel("Gross Weight (kg):");
        grossLbl.setFont(UITheme.FONT_BODY_BOLD);
        formBody.add(grossLbl, gbc);

        gbc.gridx = 1; gbc.weightx = 0.65;
        grossWeightField = new JTextField("3500.000", 12);
        grossWeightField.setFont(UITheme.FONT_BODY);
        formBody.add(grossWeightField, gbc);

        // Row 6: Tare Weight
        gbc.gridx = 0; gbc.gridy = 6; gbc.weightx = 0.35;
        JLabel tareLbl = new JLabel("Tare Weight (kg):");
        tareLbl.setFont(UITheme.FONT_BODY_BOLD);
        formBody.add(tareLbl, gbc);

        gbc.gridx = 1; gbc.weightx = 0.65;
        tareWeightField = new JTextField("2000.000", 12);
        tareWeightField.setFont(UITheme.FONT_BODY);
        formBody.add(tareWeightField, gbc);

        // Row 7: Net Disposed Weight
        gbc.gridx = 0; gbc.gridy = 7; gbc.weightx = 0.35;
        JLabel netLbl = new JLabel("Net Disposed Mass:");
        netLbl.setFont(UITheme.FONT_BODY_BOLD);
        formBody.add(netLbl, gbc);

        gbc.gridx = 1; gbc.weightx = 0.65;
        netWeightLabel.setFont(UITheme.FONT_BODY_BOLD);
        netWeightLabel.setForeground(UITheme.PRIMARY);
        formBody.add(netWeightLabel, gbc);

        // Document listeners to auto-calculate net weight
        DocumentListener weightCalc = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { calculateNet(); }
            @Override public void removeUpdate(DocumentEvent e) { calculateNet(); }
            @Override public void changedUpdate(DocumentEvent e) { calculateNet(); }
        };
        grossWeightField.getDocument().addDocumentListener(weightCalc);
        tareWeightField.getDocument().addDocumentListener(weightCalc);
        calculateNet();

        // Row 8: Receipt Number
        gbc.gridx = 0; gbc.gridy = 8; gbc.weightx = 0.35;
        JLabel recLbl = new JLabel("Receipt Number:");
        recLbl.setFont(UITheme.FONT_BODY_BOLD);
        formBody.add(recLbl, gbc);

        gbc.gridx = 1; gbc.weightx = 0.65;
        receiptField = new JTextField("RCP-" + System.currentTimeMillis() % 100000, 12);
        receiptField.setFont(UITheme.FONT_BODY);
        formBody.add(receiptField, gbc);

        // Row 9: Submit Button
        gbc.gridx = 0; gbc.gridy = 9; gbc.gridwidth = 2;
        submitDisposalButton = UITheme.createPrimaryButton("Submit Disposal Check-in (sp_record_disposal)");
        submitDisposalButton.addActionListener(e -> submitDisposal());
        formBody.add(submitDisposalButton, gbc);

        formCard.add(formBody, BorderLayout.CENTER);

        // Right Panel: Historical Disposal Records Table
        JPanel tableCard = UITheme.createCardPanel();
        tableCard.setLayout(new BorderLayout(0, 10));

        JLabel tableTitle = UITheme.createHeaderLabel("Disposal Records & Audit Receipts");
        tableCard.add(tableTitle, BorderLayout.NORTH);

        String[] cols = {"Disposal ID", "Route #", "Facility #", "Gross (kg)", "Tare (kg)", "Net Disposed (kg)", "Status", "Receipt #", "Date"};
        recordsTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        recordsTable = new JTable(recordsTableModel);
        tableCard.add(UITheme.createTableScrollPane(recordsTable), BorderLayout.CENTER);

        splitPane.setLeftComponent(formCard);
        splitPane.setRightComponent(tableCard);

        add(topPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        refreshData();
    }

    private void calculateNet() {
        try {
            BigDecimal gross = new BigDecimal(grossWeightField.getText().trim());
            BigDecimal tare = new BigDecimal(tareWeightField.getText().trim());
            if (gross.compareTo(tare) >= 0) {
                BigDecimal net = gross.subtract(tare);
                netWeightLabel.setText(String.format("%,.3f kg", net));
                netWeightLabel.setForeground(UITheme.PRIMARY);
            } else {
                netWeightLabel.setText("Invalid: Gross < Tare");
                netWeightLabel.setForeground(UITheme.STATUS_DANGER_FG);
            }
        } catch (Exception ignored) {
            netWeightLabel.setText("0.000 kg");
            netWeightLabel.setForeground(UITheme.TEXT_MUTED);
        }
    }

    public void refreshData() {
        UserSession session = context.getCurrentSession();
        if (session == null) return;

        progressBar.setVisible(true);
        refreshButton.setEnabled(false);

        SwingWorker<DisposalSnapshot, Void> worker = new SwingWorker<>() {
            @Override
            protected DisposalSnapshot doInBackground() {
                // Find routes awaiting disposal or in progress
                List<Route> routes = new ArrayList<>();
                try {
                    List<Route> todayRoutes = context.getRoutePersistenceService().findByDate(session, LocalDate.now());
                    for (Route r : todayRoutes) {
                        if (DISPATCHED_TO_DISPOSAL.equals(r.getStatus())) {
                            routes.add(r);
                        }
                    }
                } catch (Exception ignored) {}

                List<DisposalFacility> facilities = List.of();
                try {
                    facilities = context.getDisposalService().findAllFacilities(session);
                } catch (Exception ignored) {}

                List<DisposalRecord> records = List.of();
                try {
                    records = context.getDisposalService().findAllRecords(session);
                } catch (Exception ignored) {}

                return new DisposalSnapshot(routes, facilities, records);
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                refreshButton.setEnabled(true);
                try {
                    DisposalSnapshot snap = get();
                    pendingDisposalRoutes = snap.routes;
                    facilitiesList = snap.facilities;
                    recordsList = snap.records;

                    // Update Route combo — show date + status
                    routeCombo.removeAllItems();
                    for (Route r : snap.routes) {
                        routeCombo.addItem(new RouteOption(r.getRouteId(),
                                "Route #" + r.getRouteId() + " [" + r.getRouteDate() + "] — " + r.getStatus() + " · Vehicle #" + r.getVehicleId()));
                    }

                    // Update Facility combo
                    facilityCombo.removeAllItems();
                    for (DisposalFacility f : snap.facilities) {
                        facilityCombo.addItem(new FacilityOption(f.getFacilityId(),
                                f.getFacilityName() + " (" + f.getFacilityType() + ") - " + f.getCapacityTonnesPerDay() + " T/day"));
                    }

                    // Update Records table
                    recordsTableModel.setRowCount(0);
                    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                    for (DisposalRecord dr : snap.records) {
                        String dateStr = dr.getDisposalDate() != null ? dr.getDisposalDate().format(dtf) : "-";
                        String facilityName = snap.facilities.stream()
                                .filter(f -> f.getFacilityId().equals(dr.getFacilityId()))
                                .findFirst()
                                .map(DisposalFacility::getFacilityName)
                                .orElse("Facility #" + dr.getFacilityId());
                        recordsTableModel.addRow(new Object[]{
                                "#" + dr.getDisposalId(),
                                "Route #" + dr.getRouteId(),
                                facilityName,
                                String.format("%,.1f", dr.getGrossWeightKg()),
                                String.format("%,.1f", dr.getTareWeightKg()),
                                dr.getNetDisposedWeightKg() != null ? String.format("%,.1f", dr.getNetDisposedWeightKg()) : "-",
                                dr.getStatus(),
                                dr.getReceiptNumber() != null ? dr.getReceiptNumber() : "-",
                                dateStr
                        });
                    }

                    if (snap.routes.isEmpty()) {
                        recommendedFacilityLabel.setText("No routes currently awaiting disposal.");
                        routeWasteStatusLabel.setText("No active routes found.");
                        facilityCompatibilityLabel.setText(" ");
                        submitDisposalButton.setEnabled(false);
                    } else {
                        onRouteSelected();
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(DisposalView.this,
                            "Failed to load disposal data: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void onRouteSelected() {
        RouteOption sel = (RouteOption) routeCombo.getSelectedItem();
        if (sel == null) {
            recommendedFacilityLabel.setText("No route selected.");
            routeWasteStatusLabel.setText("Select a route to view waste streams...");
            facilityCompatibilityLabel.setText(" ");
            submitDisposalButton.setEnabled(false);
            return;
        }

        UserSession session = context.getCurrentSession();
        long routeId = sel.id;

        // Query route waste categories and status asynchronously
        SwingWorker<RouteSelectionData, Void> worker = new SwingWorker<>() {
            @Override
            protected RouteSelectionData doInBackground() {
                List<DisposalService.RouteCategoryStatus> categoryStatuses =
                        context.getDisposalService().getRouteCategoriesStatus(session, routeId);

                // Default coordinates (12.9716, 77.5946 centroid)
                double fromLat = 12.9716;
                double fromLon = 77.5946;
                try {
                    List<RouteStop> stops = context.getRoutePersistenceService().findStops(session, routeId);
                    if (!stops.isEmpty()) {
                        RouteStop lastStop = stops.get(stops.size() - 1);
                        PickupRequest req = context.getPickupRequestService().findById(session, lastStop.getRequestId()).orElse(null);
                        if (req != null) {
                            var gen = context.getGeneratorService().findById(session, req.getGeneratorId()).orElse(null);
                            if (gen != null && gen.generator().getLatitude() != null && gen.generator().getLongitude() != null) {
                                fromLat = gen.generator().getLatitude().doubleValue();
                                fromLon = gen.generator().getLongitude().doubleValue();
                            }
                        }
                    }
                } catch (Exception ex) {
                    LOGGER.log(java.util.logging.Level.WARNING, "Failed to resolve generator coordinates for route: " + ex.getMessage());
                }

                DisposalFacility rec = context.getDisposalService()
                        .selectNearestSuitableFacility(session, routeId, fromLat, fromLon).orElse(null);

                return new RouteSelectionData(categoryStatuses, rec);
            }

            @Override
            protected void done() {
                try {
                    RouteSelectionData data = get();

                    // Format Route Waste Streams
                    if (data.categoryStatuses.isEmpty()) {
                        routeWasteStatusLabel.setText("No waste items recorded for this route.");
                    } else {
                        StringBuilder sb = new StringBuilder("<html>");
                        int count = 0;
                        for (DisposalService.RouteCategoryStatus rcs : data.categoryStatuses) {
                            if (count > 0) sb.append(" &nbsp;|&nbsp; ");
                            sb.append("<b>").append(rcs.categoryName()).append(":</b> ")
                              .append(String.format("%.1f kg", rcs.weightKg()));
                            if (rcs.disposed()) {
                                sb.append(" <font color='#166534'><b>[DISPOSED @ ").append(rcs.disposedAtFacility()).append("]</b></font>");
                            } else {
                                sb.append(" <font color='#b91c1c'><b>[PENDING]</b></font>");
                            }
                            count++;
                        }
                        sb.append("</html>");
                        routeWasteStatusLabel.setText(sb.toString());
                    }

                    // Format Recommended Facility
                    if (data.recommendedFacility != null) {
                        DisposalFacility rec = data.recommendedFacility;
                        recommendedFacilityLabel.setText(rec.getFacilityName() + " (" + rec.getFacilityType() + ") — "
                                + rec.getCapacityTonnesPerDay() + " T/day Cap");
                        // Automatically select in combo
                        for (int i = 0; i < facilityCombo.getItemCount(); i++) {
                            if (facilityCombo.getItemAt(i).id.equals(rec.getFacilityId())) {
                                facilityCombo.setSelectedIndex(i);
                                break;
                            }
                        }
                    } else {
                        recommendedFacilityLabel.setText("No pending waste categories remain or no eligible facility available.");
                    }

                    // Evaluate compatibility for currently selected facility
                    evaluateFacilityCompatibility();

                } catch (Exception ex) {
                    LOGGER.log(java.util.logging.Level.WARNING, "Error updating route selection data: " + ex.getMessage());
                    recommendedFacilityLabel.setText("Unable to evaluate facility recommendation.");
                }
            }
        };
        worker.execute();
    }

    private void evaluateFacilityCompatibility() {
        RouteOption selRoute = (RouteOption) routeCombo.getSelectedItem();
        FacilityOption selFac = (FacilityOption) facilityCombo.getSelectedItem();

        if (selRoute == null || selFac == null) {
            facilityCompatibilityLabel.setText(" ");
            submitDisposalButton.setEnabled(false);
            return;
        }

        UserSession session = context.getCurrentSession();
        long routeId = selRoute.id;
        long facilityId = selFac.id;

        SwingWorker<DisposalService.FacilityCompatibility, Void> worker = new SwingWorker<>() {
            @Override
            protected DisposalService.FacilityCompatibility doInBackground() {
                return context.getDisposalService().evaluateFacilityCompatibility(session, routeId, facilityId);
            }

            @Override
            protected void done() {
                try {
                    DisposalService.FacilityCompatibility comp = get();
                    if (comp.compatible()) {
                        submitDisposalButton.setEnabled(true);
                        facilityCompatibilityLabel.setText("<html><font color='#166534'><b>" + comp.message() + "</b></font></html>");
                        // Populate suggested weights
                        tareWeightField.setText("2000.000");
                        BigDecimal suggestedGross = new BigDecimal("2000.000").add(comp.expectedNetWeightKg());
                        grossWeightField.setText(String.format("%.3f", suggestedGross));
                        calculateNet();
                    } else {
                        submitDisposalButton.setEnabled(false); // Incompatible facilities cannot be submitted!
                        facilityCompatibilityLabel.setText("<html><font color='#b91c1c'><b>" + comp.message() + "</b></font></html>");
                    }
                } catch (Exception ex) {
                    LOGGER.log(java.util.logging.Level.WARNING, "Failed to evaluate facility compatibility: " + ex.getMessage());
                    facilityCompatibilityLabel.setText("<html><font color='#b91c1c'>Could not evaluate compatibility.</font></html>");
                    submitDisposalButton.setEnabled(false);
                }
            }
        };
        worker.execute();
    }

    private void submitDisposal() {
        RouteOption selRoute = (RouteOption) routeCombo.getSelectedItem();
        FacilityOption selFac = (FacilityOption) facilityCombo.getSelectedItem();

        if (selRoute == null || selFac == null) {
            JOptionPane.showMessageDialog(this, "Please select both a Route and a Facility.", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        BigDecimal gross, tare;
        try {
            gross = new BigDecimal(grossWeightField.getText().trim());
            tare = new BigDecimal(tareWeightField.getText().trim());
            if (gross.compareTo(BigDecimal.ZERO) <= 0 || tare.compareTo(BigDecimal.ZERO) < 0 || gross.compareTo(tare) < 0) {
                throw new IllegalArgumentException();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Please enter valid numeric weights with Gross >= Tare.", "Invalid Weights", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String receipt = receiptField.getText().trim();
        UserSession session = context.getCurrentSession();
        long routeId = selRoute.id;
        long facilityId = selFac.id;
        BigDecimal net = gross.subtract(tare);

        int confirm = JOptionPane.showConfirmDialog(this,
                "Execute sp_record_disposal for Route #" + routeId + " at " + selFac.name + "?\n"
                        + "Net Disposed Mass: " + net + " kg",
                "Confirm Stored Procedure Call", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        progressBar.setVisible(true);
        submitDisposalButton.setEnabled(false);

        SwingWorker<Long, Void> worker = new SwingWorker<>() {
            @Override
            protected Long doInBackground() {
                return context.getDisposalService().recordDisposal(
                        session, routeId, facilityId, gross, tare, receipt
                );
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                submitDisposalButton.setEnabled(true);
                try {
                    long disposalId = get();
                    Route updatedRoute = context.getRoutePersistenceService().findById(session, routeId).orElse(null);

                    if (updatedRoute != null && COMPLETED.equals(updatedRoute.getStatus())) {
                        JOptionPane.showMessageDialog(DisposalView.this,
                                "Disposal recorded successfully via sp_record_disposal!\n"
                                        + "Generated Disposal ID: #" + disposalId + "\n"
                                        + "Receipt: " + receipt + "\n\n"
                                        + "All waste categories for Route #" + routeId + " have been safely disposed.\n"
                                        + "Route status is now COMPLETED and Vehicle #" + updatedRoute.getVehicleId() + " is AVAILABLE.",
                                "Disposal Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(DisposalView.this,
                                "Disposal check-in recorded successfully!\n"
                                        + "Generated Disposal ID: #" + disposalId + "\n"
                                        + "Receipt: " + receipt + "\n\n"
                                        + "Disposed " + net + " kg at " + selFac.name + ".\n"
                                        + "Remaining waste streams on Route #" + routeId + " must be unloaded at a specialized facility before the route can be completed.",
                                "Disposal Check-in Verified", JOptionPane.INFORMATION_MESSAGE);
                    }

                    receiptField.setText("RCP-" + System.currentTimeMillis() % 100000);
                    refreshData();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(DisposalView.this,
                            "Failed to record disposal: " + ex.getMessage(),
                            "Procedure Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    public void resetView() {
        LOGGER.log(java.util.logging.Level.FINE, "Resetting disposal view");
        pendingDisposalRoutes.clear();
        facilitiesList.clear();
        recordsList.clear();
        routeCombo.removeAllItems();
        facilityCombo.removeAllItems();
        routeWasteStatusLabel.setText("Select a route to view waste streams...");
        recommendedFacilityLabel.setText("Select a route to find suitable facilities...");
        facilityCompatibilityLabel.setText(" ");
        grossWeightField.setText("");
        tareWeightField.setText("");
        netWeightLabel.setText("0.000 kg");
        receiptField.setText("");
        submitDisposalButton.setEnabled(false);
        recordsTableModel.setRowCount(0);
    }

    private static class RouteOption {
        final Long id;
        final String label;

        RouteOption(Long id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override public String toString() { return label; }
    }

    private static class FacilityOption {
        final Long id;
        final String name;

        FacilityOption(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override public String toString() { return name; }
    }

    private static class DisposalSnapshot {
        final List<Route> routes;
        final List<DisposalFacility> facilities;
        final List<DisposalRecord> records;

        DisposalSnapshot(List<Route> routes, List<DisposalFacility> facilities, List<DisposalRecord> records) {
            this.routes = routes;
            this.facilities = facilities;
            this.records = records;
        }
    }

    private static class RouteSelectionData {
        final List<DisposalService.RouteCategoryStatus> categoryStatuses;
        final DisposalFacility recommendedFacility;

        RouteSelectionData(List<DisposalService.RouteCategoryStatus> categoryStatuses, DisposalFacility recommendedFacility) {
            this.categoryStatuses = categoryStatuses;
            this.recommendedFacility = recommendedFacility;
        }
    }
}
