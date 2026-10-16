package ecoroute.ui;

import ecoroute.model.*;
import ecoroute.service.UserSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;

import static ecoroute.db.DatabaseContract.*;

/**
 * Live Dashboard View showing operational metrics and recent activity from MySQL.
 */
public final class DashboardView extends JPanel {

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(DashboardView.class.getName());

    private final AppContext context;

    private final JLabel pendingRequestsVal = new JLabel("...");
    private final JLabel activeRoutesVal = new JLabel("...");
    private final JLabel availableVehiclesVal = new JLabel("...");
    private final JLabel completedPickupsVal = new JLabel("...");
    private final JLabel totalWasteKgVal = new JLabel("...");
    private final JLabel totalDisposedKgVal = new JLabel("...");

    private final DefaultTableModel activityTableModel;
    private final JButton refreshButton;
    private final JProgressBar progressBar;

    public DashboardView(AppContext context) {
        this.context = context;
        setLayout(new BorderLayout(0, 16));
        setBackground(UITheme.BG_LIGHT);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        // Top Header
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(UITheme.BG_LIGHT);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 0, 2));
        titleBlock.setBackground(UITheme.BG_LIGHT);
        JLabel pageTitle = new JLabel("System Overview");
        pageTitle.setFont(UITheme.FONT_TITLE);
        pageTitle.setForeground(UITheme.TEXT_MAIN);
        JLabel pageSubtitle = UITheme.createMutedLabel("Live operational metrics and pickup activity");
        titleBlock.add(pageTitle);
        titleBlock.add(pageSubtitle);

        JPanel actionBlock = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionBlock.setBackground(UITheme.BG_LIGHT);
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(100, 20));

        refreshButton = UITheme.createSecondaryButton("Refresh Metrics");
        refreshButton.addActionListener(e -> refreshData());

        actionBlock.add(progressBar);
        actionBlock.add(refreshButton);

        topPanel.add(titleBlock, BorderLayout.WEST);
        topPanel.add(actionBlock, BorderLayout.EAST);

        // Metrics Grid (2 rows x 3 columns)
        JPanel metricsGrid = new JPanel(new GridLayout(2, 3, 14, 14));
        metricsGrid.setBackground(UITheme.BG_LIGHT);

        metricsGrid.add(createMetricCard("Pending Requests", pendingRequestsVal, "Awaiting vehicle allocation", UITheme.STATUS_PENDING_FG));
        metricsGrid.add(createMetricCard("Active Routes", activeRoutesVal, "Planned or in transit", UITheme.STATUS_BLUE_FG));
        metricsGrid.add(createMetricCard("Available Fleet", availableVehiclesVal, "Ready for dispatch", UITheme.STATUS_SUCCESS_FG));
        metricsGrid.add(createMetricCard("Completed Pickups", completedPickupsVal, "Stops collected", UITheme.STATUS_SUCCESS_FG));
        metricsGrid.add(createMetricCard("Waste Collected (kg)", totalWasteKgVal, "Total recorded mass", UITheme.ACCENT_TEAL));
        metricsGrid.add(createMetricCard("Disposed Mass (kg)", totalDisposedKgVal, "Verified at facilities", UITheme.PRIMARY));

        // Recent Activity Table Card
        JPanel tableCard = UITheme.createCardPanel();
        tableCard.setLayout(new BorderLayout(0, 10));

        JLabel tableTitle = UITheme.createHeaderLabel("Recent Requests & Routes");
        tableCard.add(tableTitle, BorderLayout.NORTH);

        String[] cols = {"Type", "Reference ID", "Generator / Zone", "Date", "Status", "Quantity / Load"};
        activityTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        JTable activityTable = new JTable(activityTableModel);
        JScrollPane scrollPane = UITheme.createTableScrollPane(activityTable);
        tableCard.add(scrollPane, BorderLayout.CENTER);

        // Assemble Layout
        JPanel centerPanel = new JPanel(new BorderLayout(0, 16));
        centerPanel.setBackground(UITheme.BG_LIGHT);
        centerPanel.add(metricsGrid, BorderLayout.NORTH);
        centerPanel.add(tableCard, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);

        // Initial Data Load
        refreshData();
    }

    private JPanel createMetricCard(String title, JLabel valueLabel, String subtitle, Color accentColor) {
        JPanel card = UITheme.createCardPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(UITheme.FONT_SUBHEADER);
        titleLbl.setForeground(UITheme.TEXT_MUTED);

        valueLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        valueLabel.setForeground(accentColor);

        JLabel subLbl = new JLabel(subtitle);
        subLbl.setFont(UITheme.FONT_SMALL);
        subLbl.setForeground(UITheme.TEXT_MUTED);

        card.add(titleLbl);
        card.add(Box.createVerticalStrut(6));
        card.add(valueLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(subLbl);

        return card;
    }

    public void refreshData() {
        UserSession session = context.getCurrentSession();
        if (session == null) return;

        refreshButton.setEnabled(false);
        progressBar.setVisible(true);

        SwingWorker<DashboardSnapshot, Void> worker = new SwingWorker<>() {
            @Override
            protected DashboardSnapshot doInBackground() {
                int pendingCount = 0;
                int activeRoutesCount = 0;
                int availVehicles = 0;
                int totalVehicles = 0;
                int completedPickups = 0;
                BigDecimal totalWasteKg = BigDecimal.ZERO;
                BigDecimal totalDisposedKg = BigDecimal.ZERO;

                // Load vehicles
                try {
                    List<Vehicle> available = context.getVehicleService().findAvailable(session);
                    List<Vehicle> allVehicles = context.getVehicleService().findAll(session);
                    availVehicles = available.size();
                    totalVehicles = allVehicles.size();
                } catch (Exception ex) {
                    LOGGER.log(java.util.logging.Level.WARNING, "Failed to load vehicles for dashboard: " + ex.getMessage());
                }

                // Load pending requests
                List<PickupRequest> pending = List.of();
                try {
                    pending = context.getPickupRequestService().findPending(session);
                    pendingCount = pending.size();
                } catch (Exception ex) {
                    LOGGER.log(java.util.logging.Level.WARNING, "Failed to load pending requests for dashboard: " + ex.getMessage());
                }

                // Load routes by date (today)
                List<Route> todayRoutes = List.of();
                try {
                    todayRoutes = context.getRoutePersistenceService().findByDate(session, java.time.LocalDate.now());
                    for (Route r : todayRoutes) {
                        if (PLANNED.equals(r.getStatus()) || IN_PROGRESS.equals(r.getStatus()) || DISPATCHED_TO_DISPOSAL.equals(r.getStatus())) {
                            activeRoutesCount++;
                        }
                    }
                } catch (Exception ex) {
                    LOGGER.log(java.util.logging.Level.WARNING, "Failed to load active routes for dashboard: " + ex.getMessage());
                }

                // Load disposal records
                List<DisposalRecord> records = List.of();
                try {
                    records = context.getDisposalService().findAllRecords(session);
                    for (DisposalRecord dr : records) {
                        if (dr.getNetDisposedWeightKg() != null) {
                            totalDisposedKg = totalDisposedKg.add(dr.getNetDisposedWeightKg());
                        }
                    }
                } catch (Exception ex) {
                    LOGGER.log(java.util.logging.Level.WARNING, "Failed to load disposal records for dashboard: " + ex.getMessage());
                }

                // Load completed pickup requests
                try {
                    if (GENERATOR.equals(session.getRole()) && session.getGeneratorId() != null) {
                        List<PickupRequest> myReqs = context.getPickupRequestService().findByGenerator(session, session.getGeneratorId());
                        for (PickupRequest r : myReqs) {
                            if (COMPLETED.equals(r.getStatus())) completedPickups++;
                        }
                    } else {
                        // Count completed requests across system
                        // Approximate from completed routes
                        for (Route r : todayRoutes) {
                            if (COMPLETED.equals(r.getStatus())) {
                                completedPickups += context.getRoutePersistenceService().findStops(session, r.getRouteId()).size();
                            }
                        }
                    }
                } catch (Exception ex) {
                    LOGGER.log(java.util.logging.Level.WARNING, "Failed to load completed pickups for dashboard: " + ex.getMessage());
                }

                // Load waste summaries
                try {
                    for (DisposalRecord r : records) {
                        if (r.getNetDisposedWeightKg() != null) {
                            totalWasteKg = totalWasteKg.add(r.getNetDisposedWeightKg());
                        }
                    }
                } catch (Exception ex) {
                    LOGGER.log(java.util.logging.Level.WARNING, "Failed to load waste totals for dashboard: " + ex.getMessage());
                }

                return new DashboardSnapshot(
                        pendingCount,
                        activeRoutesCount,
                        availVehicles,
                        totalVehicles,
                        completedPickups,
                        totalWasteKg,
                        totalDisposedKg,
                        pending,
                        todayRoutes
                );
            }

            @Override
            protected void done() {
                refreshButton.setEnabled(true);
                progressBar.setVisible(false);
                try {
                    DashboardSnapshot snap = get();
                    pendingRequestsVal.setText(String.valueOf(snap.pendingRequests));
                    activeRoutesVal.setText(String.valueOf(snap.activeRoutes));
                    availableVehiclesVal.setText(snap.availableVehicles + " / " + snap.totalVehicles);
                    completedPickupsVal.setText(String.valueOf(snap.completedPickups));
                    totalWasteKgVal.setText(String.format("%,.1f", snap.totalWasteKg));
                    totalDisposedKgVal.setText(String.format("%,.1f", snap.totalDisposedKg));

                    // Populate activity table
                    activityTableModel.setRowCount(0);
                    for (PickupRequest pr : snap.recentRequests) {
                        activityTableModel.addRow(new Object[]{
                                "Pickup Request",
                                "#" + pr.getRequestId(),
                                "Generator #" + pr.getGeneratorId(),
                                pr.getPreferredPickupDate(),
                                pr.getStatus(),
                                pr.getNotes() != null ? pr.getNotes() : "Standard Pickup"
                        });
                    }
                    for (Route rt : snap.recentRoutes) {
                        activityTableModel.addRow(new Object[]{
                                "Planned Route",
                                "Route #" + rt.getRouteId(),
                                "Zone #" + rt.getZoneId() + " (Vehicle #" + rt.getVehicleId() + ")",
                                rt.getRouteDate(),
                                rt.getStatus(),
                                "Status: " + rt.getStatus()
                        });
                    }
                } catch (Exception ex) {
                    LOGGER.log(java.util.logging.Level.WARNING, "Failed to render dashboard activity: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    public void resetView() {
        LOGGER.log(java.util.logging.Level.FINE, "Resetting dashboard view");
        pendingRequestsVal.setText("...");
        activeRoutesVal.setText("...");
        availableVehiclesVal.setText("...");
        completedPickupsVal.setText("...");
        totalWasteKgVal.setText("...");
        totalDisposedKgVal.setText("...");
        activityTableModel.setRowCount(0);
    }

    private static class DashboardSnapshot {
        final int pendingRequests;
        final int activeRoutes;
        final int availableVehicles;
        final int totalVehicles;
        final int completedPickups;
        final BigDecimal totalWasteKg;
        final BigDecimal totalDisposedKg;
        final List<PickupRequest> recentRequests;
        final List<Route> recentRoutes;

        DashboardSnapshot(int pendingRequests, int activeRoutes, int availableVehicles, int totalVehicles,
                          int completedPickups, BigDecimal totalWasteKg, BigDecimal totalDisposedKg,
                          List<PickupRequest> recentRequests, List<Route> recentRoutes) {
            this.pendingRequests = pendingRequests;
            this.activeRoutes = activeRoutes;
            this.availableVehicles = availableVehicles;
            this.totalVehicles = totalVehicles;
            this.completedPickups = completedPickups;
            this.totalWasteKg = totalWasteKg;
            this.totalDisposedKg = totalDisposedKg;
            this.recentRequests = recentRequests;
            this.recentRoutes = recentRoutes;
        }
    }
}
