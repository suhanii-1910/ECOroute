package ecoroute.ui;

import ecoroute.service.UserSession;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static ecoroute.db.DatabaseContract.*;

/**
 * Main application frame containing:
 * - Header with system branding, user profile chip, and sign-out action
 * - Role-filtered navigation sidebar
 * - CardLayout view container with all functional screens
 * - Automatic session change reaction and data refreshing
 */
public final class MainFrame extends JFrame {

    private final AppContext context;

    // Header Components
    private final JLabel usernameLabel = new JLabel("Guest");
    private final JLabel roleBadgeLabel = new JLabel("UNKNOWN");
    private final JLabel facilityLabel = new JLabel("");
    private final JButton signOutButton;

    // Sidebar Navigation
    private final JPanel navButtonsPanel;
    private final Map<String, JButton> navButtons = new HashMap<>();
    private final JLabel dbStatusLabel;

    // Card Layout Views
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel centerPanel = new JPanel(cardLayout);

    private final DashboardView dashboardView;
    private final GeneratorView generatorView;
    private final AdminView adminView;
    private final OperatorPlanningView operatorPlanningView;
    private final DriverView driverView;
    private final DisposalView disposalView;
    private final ReportsView reportsView;

    private String currentViewName = "dashboard";

    public MainFrame(AppContext context) {
        super("ECOroute — Waste Routing & Management System");
        this.context = context;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1024, 680));
        setSize(1280, 820);
        setLocationRelativeTo(null);

        // Initialize Views
        dashboardView = new DashboardView(context);
        generatorView = new GeneratorView(context);
        adminView = new AdminView(context);
        operatorPlanningView = new OperatorPlanningView(context);
        driverView = new DriverView(context);
        disposalView = new DisposalView(context);
        reportsView = new ReportsView(context);

        centerPanel.add(dashboardView, "dashboard");
        centerPanel.add(generatorView, "generator");
        centerPanel.add(adminView, "admin");
        centerPanel.add(operatorPlanningView, "planning");
        centerPanel.add(driverView, "driver");
        centerPanel.add(disposalView, "disposal");
        centerPanel.add(reportsView, "reports");

        // Top Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, UITheme.BORDER_COLOR),
                new EmptyBorder(12, 20, 12, 20)
        ));

        // Brand Logo/Title
        JPanel brandPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brandPanel.setBackground(Color.WHITE);

        JLabel logoIcon = new JLabel("♻");
        logoIcon.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        logoIcon.setForeground(UITheme.PRIMARY);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 0, 0));
        titleBlock.setBackground(Color.WHITE);
        JLabel titleLabel = new JLabel("ECOroute");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        titleLabel.setForeground(UITheme.PRIMARY);

        JLabel subtitleLabel = new JLabel("Intelligent Municipal Waste Logistics");
        subtitleLabel.setFont(UITheme.FONT_SMALL);
        subtitleLabel.setForeground(UITheme.TEXT_MUTED);

        titleBlock.add(titleLabel);
        titleBlock.add(subtitleLabel);

        brandPanel.add(logoIcon);
        brandPanel.add(titleBlock);

        // User Profile & Logout
        JPanel userPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        userPanel.setBackground(Color.WHITE);

        JPanel userChip = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        userChip.setBackground(Color.WHITE);

        usernameLabel.setFont(UITheme.FONT_BODY_BOLD);
        usernameLabel.setForeground(UITheme.TEXT_MAIN);

        roleBadgeLabel.setFont(UITheme.FONT_SMALL);
        roleBadgeLabel.setOpaque(true);
        roleBadgeLabel.setBorder(new EmptyBorder(2, 8, 2, 8));

        facilityLabel.setFont(UITheme.FONT_SMALL);
        facilityLabel.setForeground(UITheme.TEXT_MUTED);

        userChip.add(usernameLabel);
        userChip.add(roleBadgeLabel);
        userChip.add(facilityLabel);

        signOutButton = UITheme.createSecondaryButton("Sign Out");
        signOutButton.addActionListener(e -> signOut());

        userPanel.add(userChip);
        userPanel.add(signOutButton);

        headerPanel.add(brandPanel, BorderLayout.WEST);
        headerPanel.add(userPanel, BorderLayout.EAST);

        // Sidebar Navigation
        JPanel sidebarPanel = new JPanel(new BorderLayout());
        sidebarPanel.setPreferredSize(new Dimension(220, 0));
        sidebarPanel.setBackground(Color.WHITE);
        sidebarPanel.setBorder(new MatteBorder(0, 0, 0, 1, UITheme.BORDER_COLOR));

        navButtonsPanel = new JPanel();
        navButtonsPanel.setLayout(new BoxLayout(navButtonsPanel, BoxLayout.Y_AXIS));
        navButtonsPanel.setBackground(Color.WHITE);
        navButtonsPanel.setBorder(new EmptyBorder(16, 12, 16, 12));

        addNavButton("dashboard", "📊  Dashboard");
        addNavButton("generator", "🏢  Generator Portal");
        addNavButton("admin", "⚙️  Admin Console");
        addNavButton("planning", "🗺️  Route Planning");
        addNavButton("driver", "🚛  Driver Collection");
        addNavButton("disposal", "🏭  Disposal Check-in");
        addNavButton("reports", "📈  Reports & Audit");

        sidebarPanel.add(navButtonsPanel, BorderLayout.NORTH);

        // Sidebar Footer with DB status
        JPanel sidebarFooter = new JPanel(new BorderLayout());
        sidebarFooter.setBackground(Color.WHITE);
        sidebarFooter.setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, UITheme.BORDER_COLOR),
                new EmptyBorder(12, 14, 12, 14)
        ));

        dbStatusLabel = new JLabel("● Database: " + context.getConnectionInfo());
        dbStatusLabel.setFont(UITheme.FONT_SMALL);
        dbStatusLabel.setForeground(UITheme.STATUS_SUCCESS_FG);
        sidebarFooter.add(dbStatusLabel, BorderLayout.CENTER);

        sidebarPanel.add(sidebarFooter, BorderLayout.SOUTH);

        // Layout Assembly
        JPanel rootPanel = new JPanel(new BorderLayout());
        rootPanel.add(headerPanel, BorderLayout.NORTH);
        rootPanel.add(sidebarPanel, BorderLayout.WEST);
        rootPanel.add(centerPanel, BorderLayout.CENTER);

        setContentPane(rootPanel);

        // Register listener for session changes
        context.addSessionListener(this::onSessionChanged);

        // Update UI for current session if already set
        if (context.getCurrentSession() != null) {
            onSessionChanged(context.getCurrentSession());
        }
    }

    private void addNavButton(String viewName, String label) {
        JButton btn = new JButton(label);
        btn.setFont(UITheme.FONT_BODY);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btn.setPreferredSize(new Dimension(196, 40));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFocusPainted(false);
        btn.setBackground(Color.WHITE);
        btn.setForeground(UITheme.TEXT_MAIN);
        btn.setBorder(new EmptyBorder(8, 12, 8, 12));

        btn.addActionListener(e -> showView(viewName));

        navButtons.put(viewName, btn);
        navButtonsPanel.add(btn);
        navButtonsPanel.add(Box.createVerticalStrut(4));
    }

    public void showView(String viewName) {
        this.currentViewName = viewName;
        cardLayout.show(centerPanel, viewName);

        // Highlight active nav button
        for (Map.Entry<String, JButton> entry : navButtons.entrySet()) {
            JButton btn = entry.getValue();
            if (entry.getKey().equals(viewName)) {
                btn.setBackground(new Color(236, 253, 245));
                btn.setForeground(UITheme.PRIMARY);
                btn.setFont(UITheme.FONT_BODY_BOLD);
                btn.setBorder(new CompoundBorder(
                        new LineBorder(UITheme.PRIMARY, 1, true),
                        new EmptyBorder(7, 11, 7, 11)
                ));
            } else {
                btn.setBackground(Color.WHITE);
                btn.setForeground(UITheme.TEXT_MAIN);
                btn.setFont(UITheme.FONT_BODY);
                btn.setBorder(new EmptyBorder(8, 12, 8, 12));
            }
        }

        // Trigger view data refresh
        refreshActiveView(viewName);
    }

    public boolean isNavVisible(String viewName) {
        JButton btn = navButtons.get(viewName);
        return btn != null && btn.isVisible();
    }

    public String getCurrentViewName() {
        return currentViewName;
    }

    public void resetAllViews() {
        if (dashboardView != null) dashboardView.resetView();
        if (generatorView != null) generatorView.resetView();
        if (adminView != null) adminView.resetView();
        if (operatorPlanningView != null) operatorPlanningView.resetView();
        if (driverView != null) driverView.resetView();
        if (disposalView != null) disposalView.resetView();
        if (reportsView != null) reportsView.resetView();
    }

    private void refreshActiveView(String viewName) {
        switch (viewName) {
            case "dashboard": dashboardView.refreshData(); break;
            case "generator": generatorView.refreshData(); break;
            case "admin": adminView.refreshAllData(); break;
            case "planning": operatorPlanningView.refreshActiveRoutes(); break;
            case "driver": driverView.refreshData(); break;
            case "disposal": disposalView.refreshData(); break;
            case "reports": reportsView.generateReports(); break;
        }
    }

    public void onSessionChanged(UserSession session) {
        if (session == null) {
            usernameLabel.setText("Signed Out");
            roleBadgeLabel.setText("GUEST");
            roleBadgeLabel.setBackground(UITheme.BG_LIGHT);
            roleBadgeLabel.setForeground(UITheme.TEXT_MUTED);
            facilityLabel.setText("");
            for (JButton btn : navButtons.values()) {
                btn.setVisible(false);
            }
            resetAllViews();
            return;
        }

        usernameLabel.setText(session.getUsername());
        roleBadgeLabel.setText(session.getRole());

        // Style role badge
        switch (session.getRole()) {
            case ADMIN:
                roleBadgeLabel.setBackground(new Color(254, 226, 226));
                roleBadgeLabel.setForeground(new Color(185, 28, 28));
                break;
            case OPERATOR:
                roleBadgeLabel.setBackground(new Color(219, 234, 254));
                roleBadgeLabel.setForeground(new Color(29, 78, 216));
                break;
            case DRIVER:
                roleBadgeLabel.setBackground(new Color(204, 251, 241));
                roleBadgeLabel.setForeground(new Color(15, 118, 110));
                break;
            case GENERATOR:
                roleBadgeLabel.setBackground(new Color(220, 252, 231));
                roleBadgeLabel.setForeground(new Color(21, 128, 61));
                break;
            default:
                roleBadgeLabel.setBackground(UITheme.BG_LIGHT);
                roleBadgeLabel.setForeground(UITheme.TEXT_MUTED);
        }

        if (session.getGeneratorId() != null) {
            facilityLabel.setText("(Generator #" + session.getGeneratorId() + ")");
        } else {
            facilityLabel.setText("");
        }

        // Filter navigation items by role
        String role = session.getRole();
        setNavVisible("dashboard", true); // Available to all roles
        setNavVisible("generator", GENERATOR.equals(role)); // Restricted strictly to GENERATOR
        setNavVisible("admin", ADMIN.equals(role));
        setNavVisible("planning", OPERATOR.equals(role) || ADMIN.equals(role));
        setNavVisible("driver", DRIVER.equals(role) || OPERATOR.equals(role) || ADMIN.equals(role));
        setNavVisible("disposal", DRIVER.equals(role) || OPERATOR.equals(role) || ADMIN.equals(role));
        setNavVisible("reports", OPERATOR.equals(role) || ADMIN.equals(role));

        // Default landing page by role
        String defaultView = "dashboard";
        if (GENERATOR.equals(role)) defaultView = "generator";
        else if (DRIVER.equals(role)) defaultView = "driver";
        else if (OPERATOR.equals(role)) defaultView = "planning";
        else if (ADMIN.equals(role)) defaultView = "dashboard";

        showView(defaultView);
    }

    private void setNavVisible(String viewName, boolean visible) {
        JButton btn = navButtons.get(viewName);
        if (btn != null) {
            btn.setVisible(visible);
        }
    }

    private void signOut() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to sign out?",
                "Sign Out", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        context.logout();
        setVisible(false);

        // Re-open login dialog without parenting to hidden window
        LoginDialog loginDialog = new LoginDialog((Frame) null, context);
        loginDialog.setLocationRelativeTo(null);
        loginDialog.setVisible(true);

        if (loginDialog.isAuthenticated()) {
            setVisible(true);
            toFront();
            requestFocus();
        } else {
            dispose();
            System.exit(0);
        }
    }
}
