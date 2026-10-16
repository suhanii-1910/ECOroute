package ecoroute.ui;

import ecoroute.model.PickupRequest;
import ecoroute.model.RequestWaste;
import ecoroute.model.WasteCategory;
import ecoroute.model.WasteGenerator;
import ecoroute.service.PickupRequestService;
import ecoroute.service.UserSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static ecoroute.db.DatabaseContract.PENDING;

/**
 * Generator Screen:
 * - See generator profile and active zone
 * - Submit new pickup request with multiple waste category lines and preferred date
 * - View request history with status badges and actual collected quantities
 * - Cancel pending requests safely
 */
public final class GeneratorView extends JPanel {

    private final AppContext context;

    private final JLabel generatorNameLabel = new JLabel("Loading...");
    private final JLabel generatorTypeLabel = new JLabel("...");
    private final JLabel generatorZoneLabel = new JLabel("...");
    private final JLabel generatorAddressLabel = new JLabel("...");

    private final DefaultTableModel requestsTableModel;
    private final JTable requestsTable;
    private final JButton newRequestButton;
    private final JButton cancelButton;
    private final JButton refreshButton;
    private final JButton detailsButton;
    private final JProgressBar progressBar;

    private List<PickupRequest> currentRequests = new ArrayList<>();

    public GeneratorView(AppContext context) {
        this.context = context;
        setLayout(new BorderLayout(0, 16));
        setBackground(UITheme.BG_LIGHT);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        // Top Header
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(UITheme.BG_LIGHT);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 0, 2));
        titleBlock.setBackground(UITheme.BG_LIGHT);
        JLabel pageTitle = new JLabel("Waste Generator Portal");
        pageTitle.setFont(UITheme.FONT_TITLE);
        pageTitle.setForeground(UITheme.TEXT_MAIN);
        JLabel pageSubtitle = UITheme.createMutedLabel("Manage your facility waste pickup requests");
        titleBlock.add(pageTitle);
        titleBlock.add(pageSubtitle);

        JPanel actionBlock = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionBlock.setBackground(UITheme.BG_LIGHT);
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(80, 20));

        newRequestButton = UITheme.createPrimaryButton("+ New Pickup Request");
        newRequestButton.addActionListener(e -> showNewRequestDialog());

        refreshButton = UITheme.createSecondaryButton("Refresh");
        refreshButton.addActionListener(e -> refreshData());

        actionBlock.add(progressBar);
        actionBlock.add(refreshButton);
        actionBlock.add(newRequestButton);

        topPanel.add(titleBlock, BorderLayout.WEST);
        topPanel.add(actionBlock, BorderLayout.EAST);

        // Generator Profile Card
        JPanel profileCard = UITheme.createCardPanel();
        profileCard.setLayout(new GridLayout(2, 2, 16, 8));

        profileCard.add(createFieldBlock("Facility Name:", generatorNameLabel));
        profileCard.add(createFieldBlock("Generator Type:", generatorTypeLabel));
        profileCard.add(createFieldBlock("Assigned Zone:", generatorZoneLabel));
        profileCard.add(createFieldBlock("Address:", generatorAddressLabel));

        // Requests Table Card
        JPanel tableCard = UITheme.createCardPanel();
        tableCard.setLayout(new BorderLayout(0, 10));

        JPanel tableHeaderPanel = new JPanel(new BorderLayout());
        tableHeaderPanel.setBackground(Color.WHITE);
        JLabel tableTitle = UITheme.createHeaderLabel("My Pickup Requests");
        tableHeaderPanel.add(tableTitle, BorderLayout.WEST);

        JPanel tableActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        tableActions.setBackground(Color.WHITE);

        detailsButton = UITheme.createSecondaryButton("View Waste Lines");
        detailsButton.setEnabled(false);
        detailsButton.addActionListener(e -> showRequestDetailsDialog());

        cancelButton = UITheme.createDangerButton("Cancel Request");
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> cancelSelectedRequest());

        tableActions.add(detailsButton);
        tableActions.add(cancelButton);
        tableHeaderPanel.add(tableActions, BorderLayout.EAST);

        tableCard.add(tableHeaderPanel, BorderLayout.NORTH);

        String[] cols = {"Request ID", "Request Date", "Preferred Date", "Status", "Notes"};
        requestsTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        requestsTable = new JTable(requestsTableModel);
        requestsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateActionButtons();
            }
        });

        JScrollPane scrollPane = UITheme.createTableScrollPane(requestsTable);
        tableCard.add(scrollPane, BorderLayout.CENTER);

        // Assemble Layout
        JPanel centerPanel = new JPanel(new BorderLayout(0, 16));
        centerPanel.setBackground(UITheme.BG_LIGHT);
        centerPanel.add(profileCard, BorderLayout.NORTH);
        centerPanel.add(tableCard, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);

        refreshData();
    }

    private JPanel createFieldBlock(String label, JLabel valueLabel) {
        JPanel block = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        block.setBackground(Color.WHITE);
        JLabel titleLbl = new JLabel(label);
        titleLbl.setFont(UITheme.FONT_BODY_BOLD);
        titleLbl.setForeground(UITheme.TEXT_MUTED);

        valueLabel.setFont(UITheme.FONT_BODY);
        valueLabel.setForeground(UITheme.TEXT_MAIN);

        block.add(titleLbl);
        block.add(valueLabel);
        return block;
    }

    private void updateActionButtons() {
        int selectedRow = requestsTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < currentRequests.size()) {
            PickupRequest req = currentRequests.get(selectedRow);
            detailsButton.setEnabled(true);
            cancelButton.setEnabled(PENDING.equals(req.getStatus()));
        } else {
            detailsButton.setEnabled(false);
            cancelButton.setEnabled(false);
        }
    }

    public void refreshData() {
        UserSession session = context.getCurrentSession();
        if (session == null || session.getGeneratorId() == null) {
            generatorNameLabel.setText("No Generator Account Linked (Requires GENERATOR role)");
            generatorTypeLabel.setText("-");
            generatorZoneLabel.setText("-");
            generatorAddressLabel.setText("-");
            requestsTableModel.setRowCount(0);
            currentRequests = List.of();
            updateActionButtons();
            return;
        }

        progressBar.setVisible(true);
        refreshButton.setEnabled(false);

        SwingWorker<GeneratorDataSnapshot, Void> worker = new SwingWorker<>() {
            @Override
            protected GeneratorDataSnapshot doInBackground() {
                WasteGenerator gen = context.getGeneratorService().findById(session, session.getGeneratorId())
                        .map(ecoroute.service.GeneratorService.Profile::generator)
                        .orElse(null);
                List<PickupRequest> requests = context.getPickupRequestService().findByGenerator(session, session.getGeneratorId());
                return new GeneratorDataSnapshot(gen, requests);
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                refreshButton.setEnabled(true);
                try {
                    GeneratorDataSnapshot snap = get();
                    if (snap.generator != null) {
                        generatorNameLabel.setText(snap.generator.getGeneratorName());
                        generatorTypeLabel.setText(snap.generator.getGeneratorType());
                        generatorZoneLabel.setText("Zone #" + snap.generator.getZoneId());
                        generatorAddressLabel.setText(snap.generator.getAddress());
                    }

                    currentRequests = snap.requests;
                    requestsTableModel.setRowCount(0);
                    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");

                    for (PickupRequest req : snap.requests) {
                        String reqDateStr = req.getRequestDate() != null ? req.getRequestDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "-";
                        String prefDateStr = req.getPreferredPickupDate() != null ? req.getPreferredPickupDate().format(dtf) : "-";
                        requestsTableModel.addRow(new Object[]{
                                "#" + req.getRequestId(),
                                reqDateStr,
                                prefDateStr,
                                req.getStatus(),
                                req.getNotes() != null ? req.getNotes() : ""
                        });
                    }
                    updateActionButtons();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(GeneratorView.this,
                            "Failed to load generator data: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void cancelSelectedRequest() {
        int selectedRow = requestsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= currentRequests.size()) return;

        PickupRequest req = currentRequests.get(selectedRow);
        if (!PENDING.equals(req.getStatus())) {
            JOptionPane.showMessageDialog(this,
                    "Only PENDING requests can be cancelled.",
                    "Cannot Cancel", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to cancel pickup request #" + req.getRequestId() + "?",
                "Confirm Cancellation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        UserSession session = context.getCurrentSession();
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                context.getPickupRequestService().cancel(session, req.getRequestId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(GeneratorView.this,
                            "Pickup request #" + req.getRequestId() + " has been cancelled.",
                            "Cancelled", JOptionPane.INFORMATION_MESSAGE);
                    refreshData();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(GeneratorView.this,
                            "Cancellation failed: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void showRequestDetailsDialog() {
        int selectedRow = requestsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= currentRequests.size()) return;

        PickupRequest req = currentRequests.get(selectedRow);
        UserSession session = context.getCurrentSession();

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                "Request Details #" + req.getRequestId(), true);
        dialog.setLayout(new BorderLayout(0, 12));
        dialog.setSize(520, 360);
        dialog.setLocationRelativeTo(this);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(UITheme.BG_LIGHT);
        content.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel info = new JLabel("Preferred Date: " + req.getPreferredPickupDate() + " | Status: " + req.getStatus());
        info.setFont(UITheme.FONT_BODY_BOLD);
        content.add(info, BorderLayout.NORTH);

        String[] cols = {"Category", "Estimated (kg)", "Actual (kg)"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        JTable table = new JTable(model);
        content.add(UITheme.createTableScrollPane(table), BorderLayout.CENTER);

        JButton closeBtn = UITheme.createSecondaryButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setBackground(UITheme.BG_LIGHT);
        btnPanel.add(closeBtn);
        content.add(btnPanel, BorderLayout.SOUTH);

        // Fetch waste lines asynchronously
        SwingWorker<List<RequestWaste>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<RequestWaste> doInBackground() {
                return context.getPickupRequestService().findWaste(session, req.getRequestId());
            }

            @Override
            protected void done() {
                try {
                    List<RequestWaste> lines = get();
                    java.util.Map<Long, String> categoryNames = new java.util.HashMap<>();
                    try {
                        if (session != null) {
                            List<WasteCategory> cats = context.getCatalogService().categories(session);
                            for (WasteCategory c : cats) {
                                if (c.getCategoryId() != null && c.getCategoryName() != null) {
                                    categoryNames.put(c.getCategoryId(), c.getCategoryName());
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    for (RequestWaste rw : lines) {
                        String catName = categoryNames.getOrDefault(rw.getCategoryId(), "Category #" + rw.getCategoryId());
                        model.addRow(new Object[]{
                                catName,
                                rw.getEstimatedQuantity() + " kg",
                                rw.getActualQuantity() != null ? rw.getActualQuantity() + " kg" : "Pending Collection"
                        });
                    }
                } catch (Exception ignored) {}
            }
        };
        worker.execute();

        dialog.setContentPane(content);
        dialog.setVisible(true);
    }

    private void showNewRequestDialog() {
        UserSession session = context.getCurrentSession();
        if (session == null || session.getGeneratorId() == null) return;

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                "Create Pickup Request", true);
        dialog.setLayout(new BorderLayout(0, 12));
        dialog.setSize(580, 440);
        dialog.setLocationRelativeTo(this);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(UITheme.BG_LIGHT);
        content.setBorder(new EmptyBorder(16, 18, 16, 18));

        // Form Fields (Preferred Date & Notes)
        JPanel formTop = UITheme.createCardPanel();
        formTop.setLayout(new GridLayout(2, 2, 10, 8));

        formTop.add(new JLabel("Preferred Date (YYYY-MM-DD):"));
        JTextField dateField = new JTextField(LocalDate.now().plusDays(1).toString());
        formTop.add(dateField);

        formTop.add(new JLabel("Notes / Instructions:"));
        JTextField notesField = new JTextField("Regular collection");
        formTop.add(notesField);

        // Waste Lines Table
        JPanel tablePanel = UITheme.createCardPanel();
        tablePanel.setLayout(new BorderLayout(0, 8));

        JPanel tableTop = new JPanel(new BorderLayout());
        tableTop.setBackground(Color.WHITE);
        tableTop.add(UITheme.createHeaderLabel("Waste Items"), BorderLayout.WEST);

        String[] cols = {"Category", "Estimated kg"};
        DefaultTableModel linesModel = new DefaultTableModel(cols, 0);
        JTable linesTable = new JTable(linesModel);

        JComboBox<CategoryItem> categoryCombo = new JComboBox<>();
        // Load categories into combo
        try {
            List<WasteCategory> cats = context.getCatalogService().categories(session);
            for (WasteCategory c : cats) {
                categoryCombo.addItem(new CategoryItem(c.getCategoryId(), c.getCategoryName() + " (" + c.getHazardLevel() + ")"));
            }
        } catch (Exception ignored) {}

        JPanel lineButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        lineButtons.setBackground(Color.WHITE);
        JButton addLineBtn = UITheme.createSecondaryButton("+ Add Item");
        JButton removeLineBtn = UITheme.createSecondaryButton("- Remove");

        addLineBtn.addActionListener(e -> {
            CategoryItem selected = (CategoryItem) categoryCombo.getSelectedItem();
            if (selected == null) return;
            String kgStr = JOptionPane.showInputDialog(dialog, "Enter estimated weight (kg) for " + selected.name + ":", "10.000");
            if (kgStr != null && !kgStr.isBlank()) {
                try {
                    BigDecimal val = new BigDecimal(kgStr.trim());
                    if (val.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException();
                    linesModel.addRow(new Object[]{selected, val.setScale(3, java.math.RoundingMode.HALF_UP).toString()});
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog, "Please enter a positive numeric weight (e.g. 15.500)", "Invalid Weight", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        removeLineBtn.addActionListener(e -> {
            int row = linesTable.getSelectedRow();
            if (row >= 0) linesModel.removeRow(row);
        });

        lineButtons.add(categoryCombo);
        lineButtons.add(addLineBtn);
        lineButtons.add(removeLineBtn);
        tableTop.add(lineButtons, BorderLayout.EAST);

        tablePanel.add(tableTop, BorderLayout.NORTH);
        tablePanel.add(UITheme.createTableScrollPane(linesTable), BorderLayout.CENTER);

        // Dialog Action Buttons
        JPanel bottomButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bottomButtons.setBackground(UITheme.BG_LIGHT);

        JButton cancelBtn = UITheme.createSecondaryButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());

        JButton submitBtn = UITheme.createPrimaryButton("Submit Request");
        submitBtn.addActionListener(e -> {
            String dateText = dateField.getText().trim();
            LocalDate preferredDate;
            try {
                preferredDate = LocalDate.parse(dateText);
                if (preferredDate.isBefore(LocalDate.now())) {
                    JOptionPane.showMessageDialog(dialog, "Preferred pickup date cannot be in the past.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Please enter a valid date in YYYY-MM-DD format.", "Invalid Date", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (linesModel.getRowCount() == 0) {
                JOptionPane.showMessageDialog(dialog, "Please add at least one waste line item.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            List<PickupRequestService.WasteLine> wasteLines = new ArrayList<>();
            for (int r = 0; r < linesModel.getRowCount(); r++) {
                CategoryItem item = (CategoryItem) linesModel.getValueAt(r, 0);
                BigDecimal kg = new BigDecimal((String) linesModel.getValueAt(r, 1));
                wasteLines.add(new PickupRequestService.WasteLine(item.id, kg));
            }

            submitBtn.setEnabled(false);
            SwingWorker<Long, Void> submitWorker = new SwingWorker<>() {
                @Override
                protected Long doInBackground() {
                    return context.getPickupRequestService().create(
                            session,
                            session.getGeneratorId(),
                            preferredDate,
                            notesField.getText().trim(),
                            wasteLines
                    );
                }

                @Override
                protected void done() {
                    submitBtn.setEnabled(true);
                    try {
                        long newReqId = get();
                        JOptionPane.showMessageDialog(GeneratorView.this,
                                "Pickup Request #" + newReqId + " submitted successfully!",
                                "Success", JOptionPane.INFORMATION_MESSAGE);
                        dialog.dispose();
                        refreshData();
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(dialog,
                                "Submission failed: " + ex.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            submitWorker.execute();
        });

        bottomButtons.add(cancelBtn);
        bottomButtons.add(submitBtn);

        content.add(formTop, BorderLayout.NORTH);
        content.add(tablePanel, BorderLayout.CENTER);
        content.add(bottomButtons, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.setVisible(true);
    }

    private static class CategoryItem {
        final long id;
        final String name;

        CategoryItem(long id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public void resetView() {
        generatorNameLabel.setText("Not Logged In");
        generatorTypeLabel.setText("-");
        generatorZoneLabel.setText("-");
        generatorAddressLabel.setText("-");
        requestsTableModel.setRowCount(0);
        currentRequests = List.of();
        updateActionButtons();
    }

    private static class GeneratorDataSnapshot {
        final WasteGenerator generator;
        final List<PickupRequest> requests;

        GeneratorDataSnapshot(WasteGenerator generator, List<PickupRequest> requests) {
            this.generator = generator;
            this.requests = requests;
        }
    }
}
