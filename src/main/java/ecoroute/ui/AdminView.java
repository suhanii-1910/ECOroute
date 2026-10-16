package ecoroute.ui;

import ecoroute.model.*;
import ecoroute.service.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static ecoroute.db.DatabaseContract.*;

/**
 * Administrative Control Panel:
 * - Zones (Add, Edit, Safe Delete)
 * - Waste Categories & Hazard Levels (Add, Edit, Safe Delete)
 * - Waste Generators & Subtypes (Hospital, Housing Society, Factory)
 * - Vehicles & Fleet Capacity Management
 * - Disposal Facilities & Types
 * - Users & Role Management
 */
public final class AdminView extends JPanel {

    private final AppContext context;

    // Tables & Models
    private final DefaultTableModel zonesModel;
    private final JTable zonesTable;

    private final DefaultTableModel categoriesModel;
    private final JTable categoriesTable;

    private final DefaultTableModel generatorsModel;
    private final JTable generatorsTable;

    private final DefaultTableModel vehiclesModel;
    private final JTable vehiclesTable;

    private final DefaultTableModel facilitiesModel;
    private final JTable facilitiesTable;

    private final DefaultTableModel usersModel;
    private final JTable usersTable;

    private final JProgressBar progressBar;

    public AdminView(AppContext context) {
        this.context = context;
        setLayout(new BorderLayout(0, 16));
        setBackground(UITheme.BG_LIGHT);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        // Header
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(UITheme.BG_LIGHT);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 0, 2));
        titleBlock.setBackground(UITheme.BG_LIGHT);
        JLabel pageTitle = new JLabel("System Administration");
        pageTitle.setFont(UITheme.FONT_TITLE);
        pageTitle.setForeground(UITheme.TEXT_MAIN);
        JLabel pageSubtitle = UITheme.createMutedLabel("Master data management, fleet configuration, and user access control");
        titleBlock.add(pageTitle);
        titleBlock.add(pageSubtitle);

        JPanel actionBlock = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionBlock.setBackground(UITheme.BG_LIGHT);
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(80, 20));

        JButton refreshAllBtn = UITheme.createSecondaryButton("Refresh All");
        refreshAllBtn.addActionListener(e -> refreshAllData());

        actionBlock.add(progressBar);
        actionBlock.add(refreshAllBtn);

        topPanel.add(titleBlock, BorderLayout.WEST);
        topPanel.add(actionBlock, BorderLayout.EAST);

        // Tabbed Panel
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(UITheme.FONT_BODY_BOLD);

        // Tab 1: Zones
        String[] zoneCols = {"Zone ID", "Zone Name", "Description"};
        zonesModel = new DefaultTableModel(zoneCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        zonesTable = new JTable(zonesModel);
        tabbedPane.addTab("Zones", createManagementTab(zonesTable,
                e -> showAddZoneDialog(),
                null,
                "Zone data is referenced by generators, vehicles, and facilities."));

        // Tab 2: Categories
        String[] catCols = {"Category ID", "Category Name", "Hazard Level", "Description"};
        categoriesModel = new DefaultTableModel(catCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        categoriesTable = new JTable(categoriesModel);
        tabbedPane.addTab("Waste Categories", createManagementTab(categoriesTable,
                e -> showAddCategoryDialog(),
                e -> deleteSelectedCategory(),
                "Categories define hazard rules (LOW, MEDIUM, HIGH, BIOMEDICAL) and facility acceptance."));

        // Tab 3: Generators
        String[] genCols = {"ID", "Name", "Type", "Zone", "Address", "Contact", "Phone", "Status"};
        generatorsModel = new DefaultTableModel(genCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        generatorsTable = new JTable(generatorsModel);
        tabbedPane.addTab("Waste Generators", createManagementTab(generatorsTable,
                e -> showAddGeneratorDialog(),
                null,
                "Supports specialized subtypes: Hospital (biomedical license), Society (registration), Factory (pollution consent)."));

        // Tab 4: Vehicles
        String[] vehCols = {"Vehicle ID", "Number", "Capacity (kg)", "Status", "Home Zone"};
        vehiclesModel = new DefaultTableModel(vehCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        vehiclesTable = new JTable(vehiclesModel);
        tabbedPane.addTab("Fleet Vehicles", createManagementTab(vehiclesTable,
                e -> showAddVehicleDialog(),
                e -> toggleVehicleMaintenance(),
                "Fleet capacity is strictly enforced during algorithmic route planning (First-Fit Decreasing)."));

        // Tab 5: Disposal Facilities
        String[] facCols = {"Facility ID", "Name", "Facility Type", "Capacity (Tonnes/Day)", "Address"};
        facilitiesModel = new DefaultTableModel(facCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        facilitiesTable = new JTable(facilitiesModel);
        tabbedPane.addTab("Disposal Facilities", createManagementTab(facilitiesTable,
                e -> showAddFacilityDialog(),
                null,
                "Disposal destinations verified via sp_record_disposal with capacity conversion (Tonnes to KG)."));

        // Tab 6: Users
        String[] userCols = {"User ID", "Username", "Role", "Generator ID", "Status"};
        usersModel = new DefaultTableModel(userCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        usersTable = new JTable(usersModel);
        tabbedPane.addTab("User Accounts", createManagementTab(usersTable,
                e -> showAddUserDialog(),
                e -> deactivateSelectedUser(),
                "Role-based security: ADMIN, OPERATOR, DRIVER, GENERATOR. Generator accounts require a valid generator_id."));

        add(topPanel, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);

        refreshAllData();
    }

    private JPanel createManagementTab(JTable table, java.awt.event.ActionListener onAdd,
                                      java.awt.event.ActionListener onAction, String helpText) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(UITheme.BG_LIGHT);
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(UITheme.BG_LIGHT);

        JLabel helpLbl = UITheme.createMutedLabel(helpText);
        toolbar.add(helpLbl, BorderLayout.WEST);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnPanel.setBackground(UITheme.BG_LIGHT);

        if (onAction != null) {
            JButton actionBtn = UITheme.createSecondaryButton("Action / Toggle");
            actionBtn.addActionListener(onAction);
            btnPanel.add(actionBtn);
        }

        if (onAdd != null) {
            JButton addBtn = UITheme.createPrimaryButton("+ Add New");
            addBtn.addActionListener(onAdd);
            btnPanel.add(addBtn);
        }

        toolbar.add(btnPanel, BorderLayout.EAST);
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(UITheme.createTableScrollPane(table), BorderLayout.CENTER);

        return panel;
    }

    public void refreshAllData() {
        UserSession session = context.getCurrentSession();
        if (session == null) return;

        progressBar.setVisible(true);

        SwingWorker<AdminDataSnapshot, Void> worker = new SwingWorker<>() {
            @Override
            protected AdminDataSnapshot doInBackground() {
                List<Zone> zones = context.getCatalogService().zones(session);
                List<WasteCategory> categories = context.getCatalogService().categories(session);
                List<WasteGenerator> generators = context.getGeneratorService().findAll(session);
                List<Vehicle> vehicles = context.getVehicleService().findAll(session);
                List<DisposalFacility> facilities = context.getDisposalService().findAllFacilities(session);
                List<User> users = context.getAuthService().findAll(session);

                return new AdminDataSnapshot(zones, categories, generators, vehicles, facilities, users);
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                try {
                    AdminDataSnapshot snap = get();

                    // Zones
                    zonesModel.setRowCount(0);
                    for (Zone z : snap.zones) {
                        zonesModel.addRow(new Object[]{z.getZoneId(), z.getZoneName(), z.getDescription() != null ? z.getDescription() : ""});
                    }

                    // Categories
                    categoriesModel.setRowCount(0);
                    for (WasteCategory c : snap.categories) {
                        categoriesModel.addRow(new Object[]{c.getCategoryId(), c.getCategoryName(), c.getHazardLevel(), c.getDescription() != null ? c.getDescription() : ""});
                    }

                    // Generators
                    generatorsModel.setRowCount(0);
                    for (WasteGenerator g : snap.generators) {
                        String zoneName = snap.zones.stream()
                                .filter(z -> z.getZoneId().equals(g.getZoneId()))
                                .findFirst()
                                .map(z -> z.getZoneName() + " (Zone #" + z.getZoneId() + ")")
                                .orElse("Zone #" + g.getZoneId());
                        generatorsModel.addRow(new Object[]{
                                g.getGeneratorId(), g.getGeneratorName(), g.getGeneratorType(),
                                zoneName, g.getAddress(),
                                g.getContactPerson() != null ? g.getContactPerson() : "-",
                                g.getPhone() != null ? g.getPhone() : "-",
                                g.isActive() ? "ACTIVE" : "INACTIVE"
                        });
                    }

                    // Vehicles
                    vehiclesModel.setRowCount(0);
                    for (Vehicle v : snap.vehicles) {
                        vehiclesModel.addRow(new Object[]{
                                v.getVehicleId(), v.getVehicleNumber(), v.getCapacityKg() + " kg",
                                v.getStatus(),
                                v.getAssignedZoneId() != null ? "Zone #" + v.getAssignedZoneId() : "Any Zone (Floating)"
                        });
                    }

                    // Facilities
                    facilitiesModel.setRowCount(0);
                    for (DisposalFacility f : snap.facilities) {
                        facilitiesModel.addRow(new Object[]{
                                f.getFacilityId(), f.getFacilityName(), f.getFacilityType(),
                                f.getCapacityTonnesPerDay() + " tonnes/day", f.getAddress()
                        });
                    }

                    // Users
                    usersModel.setRowCount(0);
                    for (User u : snap.users) {
                        usersModel.addRow(new Object[]{
                                u.getUserId(), u.getUsername(), u.getRole(),
                                u.getGeneratorId() != null ? "#" + u.getGeneratorId() : "-",
                                u.isActive() ? "ACTIVE" : "INACTIVE"
                        });
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(AdminView.this,
                            "Failed to load administrative data: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void showAddZoneDialog() {
        JTextField nameField = new JTextField(15);
        JTextField descField = new JTextField(20);

        JPanel panel = new JPanel(new GridLayout(2, 2, 8, 8));
        panel.add(new JLabel("Zone Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Description:"));
        panel.add(descField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Create New Zone",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Zone name cannot be empty.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            UserSession session = context.getCurrentSession();
            try {
                context.getCatalogService().saveZone(session, new Zone(null, name, descField.getText().trim()));
                JOptionPane.showMessageDialog(this, "Zone created successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                refreshAllData();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to save zone: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showAddCategoryDialog() {
        JTextField nameField = new JTextField(15);
        JComboBox<String> hazardCombo = new JComboBox<>(new String[]{"LOW", "MEDIUM", "HIGH", "BIOMEDICAL"});
        JTextField descField = new JTextField(20);

        JPanel panel = new JPanel(new GridLayout(3, 2, 8, 8));
        panel.add(new JLabel("Category Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Hazard Level:"));
        panel.add(hazardCombo);
        panel.add(new JLabel("Description:"));
        panel.add(descField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Create Waste Category",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Category name cannot be empty.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            UserSession session = context.getCurrentSession();
            try {
                context.getCatalogService().saveCategory(session,
                        new WasteCategory(null, name, descField.getText().trim(), (String) hazardCombo.getSelectedItem()));
                JOptionPane.showMessageDialog(this, "Waste category created.", "Success", JOptionPane.INFORMATION_MESSAGE);
                refreshAllData();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to save category: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void deleteSelectedCategory() {
        int row = categoriesTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a category to delete.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        long categoryId = (Long) categoriesModel.getValueAt(row, 0);
        String catName = (String) categoriesModel.getValueAt(row, 1);

        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete category '" + catName + "'? Only unreferenced categories can be deleted.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        UserSession session = context.getCurrentSession();
        try {
            context.getCatalogService().deleteUnreferencedCategory(session, categoryId);
            JOptionPane.showMessageDialog(this, "Category deleted.", "Success", JOptionPane.INFORMATION_MESSAGE);
            refreshAllData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Cannot delete category: It is referenced by existing pickup requests or facility acceptance rules.\n\nDetails: " + ex.getMessage(),
                    "Protected Reference", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void showAddGeneratorDialog() {
        JTextField nameField = new JTextField(15);
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"OFFICE", "HOTEL", "SCHOOL", "RESTAURANT", "HOSPITAL", "HOUSING_SOCIETY", "FACTORY"});
        JTextField zoneField = new JTextField("1", 5);
        JTextField addrField = new JTextField("123 Green Way", 15);
        JTextField contactField = new JTextField("Manager", 10);
        JTextField phoneField = new JTextField("555-0100", 10);
        JTextField subtypeDetailField = new JTextField("REG-12345", 15);
        JLabel subtypeLabel = new JLabel("Subtype Lic/Auth No:");

        JPanel panel = new JPanel(new GridLayout(7, 2, 8, 8));
        panel.add(new JLabel("Facility Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Generator Type:"));
        panel.add(typeCombo);
        panel.add(new JLabel("Zone ID:"));
        panel.add(zoneField);
        panel.add(new JLabel("Address:"));
        panel.add(addrField);
        panel.add(new JLabel("Contact Person:"));
        panel.add(contactField);
        panel.add(new JLabel("Phone:"));
        panel.add(phoneField);
        panel.add(subtypeLabel);
        panel.add(subtypeDetailField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Register Waste Generator",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String type = (String) typeCombo.getSelectedItem();
            if (name.isEmpty() || type == null) {
                JOptionPane.showMessageDialog(this, "Name and Type are required.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            long zoneId = Long.parseLong(zoneField.getText().trim());

            WasteGenerator gen = new WasteGenerator(null, name, type, contactField.getText().trim(),
                    phoneField.getText().trim(), "contact@facility.org", addrField.getText().trim(),
                    new BigDecimal("18.5204"), new BigDecimal("73.8567"), zoneId, true, LocalDateTime.now());

            Hospital hospital = "HOSPITAL".equals(type) ? new Hospital(null, subtypeDetailField.getText().trim(), "BIO-" + subtypeDetailField.getText().trim()) : null;
            HousingSociety society = "HOUSING_SOCIETY".equals(type) ? new HousingSociety(null, subtypeDetailField.getText().trim()) : null;
            Factory factory = "FACTORY".equals(type) ? new Factory(null, subtypeDetailField.getText().trim()) : null;

            UserSession session = context.getCurrentSession();
            try {
                long genId = context.getGeneratorService().create(session,
                        new GeneratorService.Profile(gen, hospital, society, factory));
                JOptionPane.showMessageDialog(this, "Generator registered with ID #" + genId, "Success", JOptionPane.INFORMATION_MESSAGE);
                refreshAllData();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Registration failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showAddVehicleDialog() {
        JTextField numField = new JTextField("MH12-XX-9999", 12);
        JTextField capField = new JTextField("500.000", 8);
        JTextField zoneField = new JTextField("1", 5);

        JPanel panel = new JPanel(new GridLayout(3, 2, 8, 8));
        panel.add(new JLabel("Vehicle Number:"));
        panel.add(numField);
        panel.add(new JLabel("Capacity (kg):"));
        panel.add(capField);
        panel.add(new JLabel("Assigned Zone ID (blank for float):"));
        panel.add(zoneField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add Fleet Vehicle",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String num = numField.getText().trim();
            BigDecimal cap = new BigDecimal(capField.getText().trim());
            Long zId = zoneField.getText().trim().isEmpty() ? null : Long.parseLong(zoneField.getText().trim());

            UserSession session = context.getCurrentSession();
            try {
                context.getVehicleService().create(session, num, cap, zId);
                JOptionPane.showMessageDialog(this, "Vehicle added to fleet.", "Success", JOptionPane.INFORMATION_MESSAGE);
                refreshAllData();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to add vehicle: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void toggleVehicleMaintenance() {
        int row = vehiclesTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a vehicle to toggle status.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        long vehicleId = (Long) vehiclesModel.getValueAt(row, 0);
        String currentStatus = (String) vehiclesModel.getValueAt(row, 3);
        String newStatus = MAINTENANCE.equals(currentStatus) ? AVAILABLE : MAINTENANCE;

        UserSession session = context.getCurrentSession();
        try {
            context.getVehicleService().updateStatus(session, vehicleId, newStatus);
            JOptionPane.showMessageDialog(this, "Vehicle #" + vehicleId + " status updated to " + newStatus, "Status Updated", JOptionPane.INFORMATION_MESSAGE);
            refreshAllData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Status update failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showAddFacilityDialog() {
        JOptionPane.showMessageDialog(this,
                "Facilities are configured with corresponding subtype records (Recycling Plant, Processing Plant, or Hazardous Disposal Site).\n" +
                "Refer to database seed or use administrative script sql/05_seed_data.sql for comprehensive matrix registration.",
                "Facility Setup", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showAddUserDialog() {
        JTextField usernameField = new JTextField(15);
        JPasswordField passwordField = new JPasswordField(15);
        JComboBox<String> roleCombo = new JComboBox<>(new String[]{"OPERATOR", "DRIVER", "ADMIN", "GENERATOR"});
        JTextField genIdField = new JTextField(8);

        JPanel panel = new JPanel(new GridLayout(4, 2, 8, 8));
        panel.add(new JLabel("Username:"));
        panel.add(usernameField);
        panel.add(new JLabel("Password:"));
        panel.add(passwordField);
        panel.add(new JLabel("Role:"));
        panel.add(roleCombo);
        panel.add(new JLabel("Generator ID (for GENERATOR role):"));
        panel.add(genIdField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Create User Account",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String username = usernameField.getText().trim();
            char[] pw = passwordField.getPassword();
            String role = (String) roleCombo.getSelectedItem();
            Long genId = genIdField.getText().trim().isEmpty() ? null : Long.parseLong(genIdField.getText().trim());

            if (GENERATOR.equals(role) && genId == null) {
                JOptionPane.showMessageDialog(this, "Generator accounts require a valid Generator ID.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!GENERATOR.equals(role) && genId != null) {
                JOptionPane.showMessageDialog(this, "Staff accounts (Admin, Operator, Driver) must not have a Generator ID.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            UserSession session = context.getCurrentSession();
            try {
                User newUser = new User(null, username, null, role, genId, true, LocalDateTime.now());
                long newUserId = context.getAuthService().createUser(session, newUser, pw);
                JOptionPane.showMessageDialog(this, "User '" + username + "' created with ID #" + newUserId, "Success", JOptionPane.INFORMATION_MESSAGE);
                refreshAllData();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to create user: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void deactivateSelectedUser() {
        int row = usersTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a user to deactivate.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        long userId = (Long) usersModel.getValueAt(row, 0);
        String username = (String) usersModel.getValueAt(row, 1);

        int confirm = JOptionPane.showConfirmDialog(this,
                "Deactivate user '" + username + "'? The user will no longer be able to log in.",
                "Confirm Deactivation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        UserSession session = context.getCurrentSession();
        try {
            context.getAuthService().deactivate(session, userId);
            JOptionPane.showMessageDialog(this, "User deactivated.", "Success", JOptionPane.INFORMATION_MESSAGE);
            refreshAllData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Deactivation failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void resetView() {
        zonesModel.setRowCount(0);
        categoriesModel.setRowCount(0);
        generatorsModel.setRowCount(0);
        vehiclesModel.setRowCount(0);
        facilitiesModel.setRowCount(0);
        usersModel.setRowCount(0);
    }

    private static class AdminDataSnapshot {
        final List<Zone> zones;
        final List<WasteCategory> categories;
        final List<WasteGenerator> generators;
        final List<Vehicle> vehicles;
        final List<DisposalFacility> facilities;
        final List<User> users;

        AdminDataSnapshot(List<Zone> zones, List<WasteCategory> categories,
                          List<WasteGenerator> generators, List<Vehicle> vehicles,
                          List<DisposalFacility> facilities, List<User> users) {
            this.zones = zones;
            this.categories = categories;
            this.generators = generators;
            this.vehicles = vehicles;
            this.facilities = facilities;
            this.users = users;
        }
    }
}
