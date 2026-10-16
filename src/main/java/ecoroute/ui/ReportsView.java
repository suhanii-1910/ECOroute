package ecoroute.ui;

import ecoroute.dao.ReportDAO;
import ecoroute.service.UserSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;

/**
 * Operational Reports View:
 * - Waste Collection Volume by Category & Hazard Level
 * - Request Status Breakdown
 * - Route Weights aggregated from ROUTE and ROUTE_STOP tables
 * - Vehicle Fleet Capacity Utilization
 * - Estimated vs Actual Weight Comparison (Discrepancy Analysis)
 * - CSV Export functionality for audit and record-keeping
 */
public final class ReportsView extends JPanel {

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(ReportsView.class.getName());

    private final AppContext context;

    private final JTextField fromDateField;
    private final JTextField toDateField;
    private final JButton generateButton;
    private final JButton exportButton;
    private final JProgressBar progressBar;

    private final JTabbedPane tabbedPane;

    // Table 1: Volume
    private final DefaultTableModel volumeModel;
    private final JTable volumeTable;

    // Table 2: Statuses
    private final DefaultTableModel statusModel;
    private final JTable statusTable;

    // Table 3: Route Weights
    private final DefaultTableModel routeWeightsModel;
    private final JTable routeWeightsTable;

    // Table 4: Vehicle Utilization
    private final DefaultTableModel utilizationModel;
    private final JTable utilizationTable;

    // Table 5: Estimated vs Actual
    private final DefaultTableModel comparisonModel;
    private final JTable comparisonTable;

    public ReportsView(AppContext context) {
        this.context = context;
        setLayout(new BorderLayout(0, 16));
        setBackground(UITheme.BG_LIGHT);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        // Top Header
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(UITheme.BG_LIGHT);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 0, 2));
        titleBlock.setBackground(UITheme.BG_LIGHT);
        JLabel pageTitle = new JLabel("Operational Reports & Audit");
        pageTitle.setFont(UITheme.FONT_TITLE);
        pageTitle.setForeground(UITheme.TEXT_MAIN);
        JLabel pageSubtitle = UITheme.createMutedLabel("Database-backed reporting, fleet utilization, and CSV audit export");
        titleBlock.add(pageTitle);
        titleBlock.add(pageSubtitle);

        JPanel filterBlock = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filterBlock.setBackground(UITheme.BG_LIGHT);

        fromDateField = new JTextField(LocalDate.now().minusDays(30).toString(), 9);
        toDateField = new JTextField(LocalDate.now().plusDays(1).toString(), 9);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(80, 20));

        generateButton = UITheme.createPrimaryButton("Generate Reports");
        generateButton.addActionListener(e -> generateReports());

        exportButton = UITheme.createSecondaryButton("Export to CSV");
        exportButton.addActionListener(e -> exportCurrentTabToCsv());

        filterBlock.add(new JLabel("From:"));
        filterBlock.add(fromDateField);
        filterBlock.add(new JLabel("To:"));
        filterBlock.add(toDateField);
        filterBlock.add(progressBar);
        filterBlock.add(generateButton);
        filterBlock.add(exportButton);

        topPanel.add(titleBlock, BorderLayout.WEST);
        topPanel.add(filterBlock, BorderLayout.EAST);

        // Center Tabbed Tables
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(UITheme.FONT_BODY_BOLD);

        // Tab 1: Collection Volume
        String[] volCols = {"Category ID", "Category Name", "Actual Disposed (kg)", "Measured Lines"};
        volumeModel = new DefaultTableModel(volCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        volumeTable = new JTable(volumeModel);
        tabbedPane.addTab("Collection Volume", createTabPanel(volumeTable));

        // Tab 2: Request Statuses
        String[] statusCols = {"Request Status", "Count of Requests"};
        statusModel = new DefaultTableModel(statusCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        statusTable = new JTable(statusModel);
        tabbedPane.addTab("Request Statuses", createTabPanel(statusTable));

        // Tab 3: Route Weights
        String[] routeCols = {"Route ID", "Estimated Mass (kg)", "Actual Mass (kg)", "Measured Lines", "Total Lines"};
        routeWeightsModel = new DefaultTableModel(routeCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        routeWeightsTable = new JTable(routeWeightsModel);
        tabbedPane.addTab("Route Weights", createTabPanel(routeWeightsTable));

        // Tab 4: Fleet Utilization
        String[] utilCols = {"Route ID", "Vehicle ID", "Capacity (kg)", "Estimated Ratio", "Actual Ratio"};
        utilizationModel = new DefaultTableModel(utilCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        utilizationTable = new JTable(utilizationModel);
        tabbedPane.addTab("Fleet Utilization", createTabPanel(utilizationTable));

        // Tab 5: Estimated vs Actual
        String[] compCols = {"Category ID", "Category Name", "Estimated Mass (kg)", "Actual Mass (kg)", "Discrepancy (kg)", "Measured Lines"};
        comparisonModel = new DefaultTableModel(compCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        comparisonTable = new JTable(comparisonModel);
        tabbedPane.addTab("Estimated vs Actual", createTabPanel(comparisonTable));

        add(topPanel, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);

        generateReports();
    }

    private JPanel createTabPanel(JTable table) {
        JPanel p = UITheme.createCardPanel();
        p.setLayout(new BorderLayout());
        p.add(UITheme.createTableScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    public void generateReports() {
        UserSession session = context.getCurrentSession();
        if (session == null) return;

        LocalDate from, to;
        try {
            from = LocalDate.parse(fromDateField.getText().trim());
            to = LocalDate.parse(toDateField.getText().trim());
            if (from.isAfter(to)) {
                JOptionPane.showMessageDialog(this, "From Date must not be after To Date.", "Invalid Date Range", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Please enter valid dates in YYYY-MM-DD format.", "Invalid Dates", JOptionPane.ERROR_MESSAGE);
            return;
        }

        LocalDate qFrom = from;
        LocalDate qTo = to;
        progressBar.setVisible(true);
        generateButton.setEnabled(false);
        exportButton.setEnabled(false);

        SwingWorker<ReportBundle, Void> worker = new SwingWorker<>() {
            @Override
            protected ReportBundle doInBackground() {
                List<ReportDAO.CollectionVolume> vol = List.of();
                List<ReportDAO.RequestStatus> statuses = List.of();
                List<ReportDAO.RouteWeight> routeWeights = List.of();
                List<ReportDAO.VehicleUtilization> utilization = List.of();
                List<ReportDAO.QuantityComparison> comparison = List.of();

                try { vol = context.getReportService().collectionVolume(session, qFrom, qTo); } catch (Exception ex) { LOGGER.log(java.util.logging.Level.WARNING, "Failed to load collection volume report: " + ex.getMessage()); }
                try { statuses = context.getReportService().requestStatuses(session, qFrom, qTo); } catch (Exception ex) { LOGGER.log(java.util.logging.Level.WARNING, "Failed to load request statuses report: " + ex.getMessage()); }
                try { routeWeights = context.getReportService().routeWeights(session, qFrom, qTo); } catch (Exception ex) { LOGGER.log(java.util.logging.Level.WARNING, "Failed to load route weights report: " + ex.getMessage()); }
                try { utilization = context.getReportService().vehicleUtilization(session, qFrom, qTo); } catch (Exception ex) { LOGGER.log(java.util.logging.Level.WARNING, "Failed to load vehicle utilization report: " + ex.getMessage()); }
                try { comparison = context.getReportService().estimatedVersusActual(session, qFrom, qTo); } catch (Exception ex) { LOGGER.log(java.util.logging.Level.WARNING, "Failed to load quantity comparison report: " + ex.getMessage()); }

                return new ReportBundle(vol, statuses, routeWeights, utilization, comparison);
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                generateButton.setEnabled(true);
                exportButton.setEnabled(true);
                try {
                    ReportBundle bundle = get();

                    // Tab 1: Volume
                    volumeModel.setRowCount(0);
                    for (ReportDAO.CollectionVolume v : bundle.volumes) {
                        volumeModel.addRow(new Object[]{
                                "#" + v.categoryId(),
                                v.categoryName(),
                                v.actualKg() != null ? String.format("%,.1f", v.actualKg()) : "0.0",
                                v.measuredLines()
                        });
                    }

                    // Tab 2: Statuses
                    statusModel.setRowCount(0);
                    for (ReportDAO.RequestStatus s : bundle.statuses) {
                        statusModel.addRow(new Object[]{
                                s.status(),
                                s.requestCount()
                        });
                    }

                    // Tab 3: Route Weights
                    routeWeightsModel.setRowCount(0);
                    for (ReportDAO.RouteWeight rw : bundle.routeWeights) {
                        routeWeightsModel.addRow(new Object[]{
                                "#" + rw.routeId(),
                                rw.estimatedKg() != null ? String.format("%,.1f", rw.estimatedKg()) : "0.0",
                                rw.actualKg() != null ? String.format("%,.1f", rw.actualKg()) : "0.0",
                                rw.measuredLines(),
                                rw.totalLines()
                        });
                    }

                    // Tab 4: Fleet Utilization
                    utilizationModel.setRowCount(0);
                    for (ReportDAO.VehicleUtilization u : bundle.utilizations) {
                        String estRatio = u.estimatedRatio() != null ? String.format("%.2f", u.estimatedRatio()) : "N/A";
                        String actRatio = u.actualRatio() != null ? String.format("%.2f", u.actualRatio()) : "N/A";
                        utilizationModel.addRow(new Object[]{
                                "Route #" + u.routeId(),
                                "Vehicle #" + u.vehicleId(),
                                String.format("%,.0f", u.capacityKg()),
                                estRatio,
                                actRatio
                        });
                    }

                    // Tab 5: Comparison
                    comparisonModel.setRowCount(0);
                    for (ReportDAO.QuantityComparison c : bundle.comparisons) {
                        String discrepancy = (c.actualKg() != null && c.estimatedKg() != null)
                                ? String.format("%,.1f", c.actualKg().subtract(c.estimatedKg()))
                                : "N/A";
                        comparisonModel.addRow(new Object[]{
                                "#" + c.categoryId(),
                                c.categoryName(),
                                c.estimatedKg() != null ? String.format("%,.1f", c.estimatedKg()) : "0.0",
                                c.actualKg() != null ? String.format("%,.1f", c.actualKg()) : "N/A (not yet measured)",
                                discrepancy,
                                c.measuredLines()
                        });
                    }

                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ReportsView.this,
                            "Failed to load reports: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void exportCurrentTabToCsv() {
        int selIndex = tabbedPane.getSelectedIndex();
        JTable activeTable;
        String tabName = tabbedPane.getTitleAt(selIndex).replaceAll("[^a-zA-Z0-9_]", "_");

        switch (selIndex) {
            case 0: activeTable = volumeTable; break;
            case 1: activeTable = statusTable; break;
            case 2: activeTable = routeWeightsTable; break;
            case 3: activeTable = utilizationTable; break;
            case 4: activeTable = comparisonTable; break;
            default: activeTable = volumeTable;
        }

        if (activeTable.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "The active table has no rows to export.", "Empty Table", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("ecoroute_" + tabName.toLowerCase() + "_" + LocalDate.now() + ".csv"));
        int res = chooser.showSaveDialog(this);
        if (res != JFileChooser.APPROVE_OPTION) return;

        File targetFile = chooser.getSelectedFile();
        try (PrintWriter writer = new PrintWriter(new FileWriter(targetFile))) {
            DefaultTableModel model = (DefaultTableModel) activeTable.getModel();

            // Write headers
            for (int col = 0; col < model.getColumnCount(); col++) {
                writer.print(escapeCsv(model.getColumnName(col)));
                if (col < model.getColumnCount() - 1) writer.print(",");
            }
            writer.println();

            // Write data rows
            for (int row = 0; row < model.getRowCount(); row++) {
                for (int col = 0; col < model.getColumnCount(); col++) {
                    Object val = model.getValueAt(row, col);
                    writer.print(escapeCsv(val != null ? val.toString() : ""));
                    if (col < model.getColumnCount() - 1) writer.print(",");
                }
                writer.println();
            }

            JOptionPane.showMessageDialog(this,
                    "Report exported successfully to:\n" + targetFile.getAbsolutePath(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to export CSV: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String escapeCsv(String val) {
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    public void resetView() {
        LOGGER.log(java.util.logging.Level.FINE, "Resetting reports view");
        volumeModel.setRowCount(0);
        statusModel.setRowCount(0);
        routeWeightsModel.setRowCount(0);
        utilizationModel.setRowCount(0);
        comparisonModel.setRowCount(0);
    }

    private static class ReportBundle {
        final List<ReportDAO.CollectionVolume> volumes;
        final List<ReportDAO.RequestStatus> statuses;
        final List<ReportDAO.RouteWeight> routeWeights;
        final List<ReportDAO.VehicleUtilization> utilizations;
        final List<ReportDAO.QuantityComparison> comparisons;

        ReportBundle(List<ReportDAO.CollectionVolume> volumes, List<ReportDAO.RequestStatus> statuses,
                     List<ReportDAO.RouteWeight> routeWeights, List<ReportDAO.VehicleUtilization> utilizations,
                     List<ReportDAO.QuantityComparison> comparisons) {
            this.volumes = volumes;
            this.statuses = statuses;
            this.routeWeights = routeWeights;
            this.utilizations = utilizations;
            this.comparisons = comparisons;
        }
    }
}
