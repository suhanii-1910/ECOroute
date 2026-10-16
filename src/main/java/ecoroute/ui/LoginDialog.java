package ecoroute.ui;

import ecoroute.service.UserSession;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Authentication dialog for ECOroute.
 * Safe credential handling, clear error messages, and one-click demo role logins.
 */
public final class LoginDialog extends JDialog {

    private final AppContext context;
    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JLabel errorLabel;
    private final JButton loginButton;
    private final JProgressBar progressBar;

    private boolean authenticated = false;

    public LoginDialog(Frame parent, AppContext context) {
        super(parent, "ECOroute — Sign In", true);
        this.context = context;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());
        mainPanel.setBackground(UITheme.BG_LIGHT);
        mainPanel.setBorder(new EmptyBorder(24, 28, 24, 28));

        // Header Panel (Branding)
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(UITheme.BG_LIGHT);

        JLabel titleLabel = new JLabel("ECOroute");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        titleLabel.setForeground(UITheme.PRIMARY);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitleLabel = new JLabel("Waste Management & Routing System");
        subtitleLabel.setFont(UITheme.FONT_BODY);
        subtitleLabel.setForeground(UITheme.TEXT_MUTED);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(titleLabel);
        headerPanel.add(Box.createVerticalStrut(4));
        headerPanel.add(subtitleLabel);
        headerPanel.add(Box.createVerticalStrut(20));

        // Form Card Panel
        JPanel formCard = UITheme.createCardPanel();
        formCard.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 6, 6, 6);

        // Username
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        JLabel userLabel = new JLabel("Username:");
        userLabel.setFont(UITheme.FONT_BODY_BOLD);
        userLabel.setForeground(UITheme.TEXT_MAIN);
        formCard.add(userLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 0.7;
        usernameField = new JTextField(16);
        usernameField.setFont(UITheme.FONT_BODY);
        formCard.add(usernameField, gbc);

        // Password
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.3;
        JLabel passLabel = new JLabel("Password:");
        passLabel.setFont(UITheme.FONT_BODY_BOLD);
        passLabel.setForeground(UITheme.TEXT_MAIN);
        formCard.add(passLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 0.7;
        passwordField = new JPasswordField(16);
        passwordField.setFont(UITheme.FONT_BODY);
        formCard.add(passwordField, gbc);

        // Error message label
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        errorLabel = new JLabel(" ");
        errorLabel.setFont(UITheme.FONT_SMALL);
        errorLabel.setForeground(UITheme.STATUS_DANGER_FG);
        formCard.add(errorLabel, gbc);

        // Progress bar
        gbc.gridy = 3;
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        formCard.add(progressBar, gbc);

        // Login Button
        gbc.gridy = 4;
        loginButton = UITheme.createPrimaryButton("Sign In");
        loginButton.addActionListener(e -> attemptLogin());
        formCard.add(loginButton, gbc);

        // Demo Logins Section
        JPanel demoPanel = new JPanel();
        demoPanel.setLayout(new BoxLayout(demoPanel, BoxLayout.Y_AXIS));
        demoPanel.setBackground(UITheme.BG_LIGHT);
        demoPanel.setBorder(new EmptyBorder(16, 0, 0, 0));

        JLabel demoHeader = new JLabel("Quick Demo Sign-In (Password: Password@123):");
        demoHeader.setFont(UITheme.FONT_SMALL);
        demoHeader.setForeground(UITheme.TEXT_MUTED);
        demoHeader.setAlignmentX(Component.CENTER_ALIGNMENT);
        demoPanel.add(demoHeader);
        demoPanel.add(Box.createVerticalStrut(8));

        JPanel demoButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 2));
        demoButtons.setBackground(UITheme.BG_LIGHT);

        demoButtons.add(createDemoButton("Admin", "admin", "Password@123"));
        demoButtons.add(createDemoButton("Operator", "operator1", "Password@123"));
        demoButtons.add(createDemoButton("Driver", "driver1", "Password@123"));
        demoButtons.add(createDemoButton("Hospital", "gen_hospital", "Password@123"));

        demoPanel.add(demoButtons);

        // Enter key submits form
        KeyAdapter enterListener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    attemptLogin();
                }
            }
        };
        usernameField.addKeyListener(enterListener);
        passwordField.addKeyListener(enterListener);

        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(formCard, BorderLayout.CENTER);
        mainPanel.add(demoPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(parent);
    }

    private JButton createDemoButton(String label, String user, String pass) {
        JButton btn = new JButton(label);
        btn.setFont(UITheme.FONT_SMALL);
        btn.setBackground(Color.WHITE);
        btn.setForeground(UITheme.PRIMARY);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new CompoundBorder(
                new LineBorder(UITheme.BORDER_COLOR, 1, true),
                new EmptyBorder(3, 8, 3, 8)
        ));
        btn.addActionListener(e -> {
            usernameField.setText(user);
            passwordField.setText(pass);
            attemptLogin();
        });
        return btn;
    }

    private void attemptLogin() {
        String username = usernameField.getText().trim();
        char[] password = passwordField.getPassword();

        if (username.isEmpty()) {
            errorLabel.setText("Please enter your username.");
            usernameField.requestFocusInWindow();
            return;
        }
        if (password.length == 0) {
            errorLabel.setText("Please enter your password.");
            passwordField.requestFocusInWindow();
            return;
        }

        loginButton.setEnabled(false);
        progressBar.setVisible(true);
        errorLabel.setText("Authenticating...");

        // Authenticate on background thread using SwingWorker
        SwingWorker<UserSession, Void> worker = new SwingWorker<>() {
            @Override
            protected UserSession doInBackground() {
                return context.getAuthService().login(username, password);
            }

            @Override
            protected void done() {
                loginButton.setEnabled(true);
                progressBar.setVisible(false);
                try {
                    UserSession session = get();
                    context.setCurrentSession(session);
                    authenticated = true;
                    dispose();
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    if (msg == null || msg.contains("Credentials rejected")) {
                        errorLabel.setText("Invalid username or password.");
                    } else {
                        errorLabel.setText("Login failed: " + msg);
                    }
                    passwordField.setText("");
                    passwordField.requestFocusInWindow();
                }
            }
        };
        worker.execute();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }
}
