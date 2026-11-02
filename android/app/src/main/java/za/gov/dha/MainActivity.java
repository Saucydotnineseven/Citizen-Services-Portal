package za.gov.dha;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private final Deque<String> screenHistory = new ArrayDeque<>();
    private String currentScreen = "login";
    private String selectedService = "Passport";
    private String savedId = "";
    private String savedName = "";
    private boolean notificationsRead = false;
    private boolean passwordVisible = false;
    private boolean staffLoginMode = false;
    private String officialTab = "applications";
    private String adminUserSearch = "";
    private int adminUserOffset = 0;
    private long sessionGeneration = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        renderScreen();
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    private void navigateTo(String screen) {
        screenHistory.push(currentScreen);
        currentScreen = screen;
        passwordVisible = false;
        renderScreen();
    }

    private void goBack() {
        if (!screenHistory.isEmpty()) {
            currentScreen = screenHistory.pop();
            passwordVisible = false;
            renderScreen();
        } else if (!"login".equals(currentScreen)) {
            if (Api.token != null) {
                signOut();
            } else {
                currentScreen = "login";
                renderScreen();
            }
        } else if (staffLoginMode) {
            staffLoginMode = false;
            savedId = "";
            renderScreen();
        } else {
            Api.signOut();
            finish();
        }
    }

    private void renderScreen() {
        switch (currentScreen) {
            case "signup":
                setContentView(R.layout.screen_signup);
                bind(R.id.signupBack, this::goBack);
                bind(R.id.signupSubmit, this::createAccount);
                break;
            case "recovery":
                setContentView(R.layout.screen_recovery);
                bind(R.id.recoveryBack, this::goBack);
                bind(R.id.recoverySubmit, this::recoverAccount);
                break;
            case "dashboard":
                setContentView(R.layout.screen_dashboard);
                setupDashboard();
                break;
            case "service":
                setContentView(R.layout.screen_service_form);
                setupServiceForm();
                break;
            case "tracking":
                setContentView(R.layout.screen_tracking);
                bind(R.id.trackingBack, this::goBack);
                bind(R.id.trackingApplications, () -> navigateTo("applications"));
                break;
            case "applications":
                setContentView(R.layout.screen_applications);
                setupApplications();
                break;
            case "appointments":
                setContentView(R.layout.screen_appointments);
                setupAppointments();
                break;
            case "appointmentRequest":
                setContentView(R.layout.screen_appointment_request);
                setupAppointmentRequest();
                break;
            case "guide":
                setContentView(R.layout.screen_appointment_guide);
                bind(R.id.guideBack, this::goBack);
                bind(R.id.guideStartApplication, () -> openService("Passport"));
                bind(R.id.guideAppointments, () -> navigateTo("appointments"));
                break;
            case "notifications":
                setContentView(R.layout.screen_notifications);
                setupNotifications();
                break;
            case "privacy":
            case "help":
                setContentView(R.layout.screen_info);
                setupInfo();
                break;
            case "official":
                showOfficialScreen();   // built in code, no XML layout needed
                break;
            case "login":
            default:
                currentScreen = "login";
                setContentView(R.layout.screen_login);
                setupLogin();
                break;
        }
    }

    // ------------------------------------------------------------------
    // LOGIN (now checks the database through the API)
    // ------------------------------------------------------------------

    private void setupLogin() {
        EditText idField = findViewById(R.id.loginId);
        TextView title = findViewById(R.id.loginTitle);
        TextView subtitle = findViewById(R.id.loginSubtitle);
        TextView identifierLabel = findViewById(R.id.loginIdentifierLabel);
        TextView staffTitle = findViewById(R.id.staffPanelTitle);
        TextView staffDescription = findViewById(R.id.staffPanelDescription);
        Button staffButton = findViewById(R.id.staffAccessButton);

        if (staffLoginMode) {
            title.setText("Staff and admin sign in");
            subtitle.setText("Authorized Home Affairs personnel only.");
            identifierLabel.setText("Work email or ID number");
            idField.setHint("Enter your work email or ID number");
            idField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            idField.setFilters(new InputFilter[0]);
            staffTitle.setText("Citizen services sign in");
            staffDescription.setText("Return to the citizen account sign-in.");
            staffButton.setText("Back to citizen sign in");
            ((Button) findViewById(R.id.signInButton)).setText("Open staff portal");
            // This container holds the password field as well as its visibility
            // toggle; keep it visible for staff/admin sign-in.
            setVisible(R.id.loginAccountActions, true);
            setVisible(R.id.loginNewUserNote, false);
            setVisible(R.id.createAccountButton, false);
        } else {
            title.setText("Welcome back");
            subtitle.setText("Sign in to manage your Home Affairs services securely.");
            identifierLabel.setText("South African ID number");
            idField.setHint("13-digit ID number");
            idField.setInputType(InputType.TYPE_CLASS_NUMBER);
            idField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(13)});
            staffTitle.setText("Home Affairs staff");
            staffDescription.setText("Authorized staff can review applications. Administrator accounts are provisioned securely.");
            staffButton.setText("Staff and admin sign in");
            ((Button) findViewById(R.id.signInButton)).setText("Sign in");
            setVisible(R.id.loginAccountActions, true);
            setVisible(R.id.loginNewUserNote, true);
            setVisible(R.id.createAccountButton, true);
        }

        if (!savedId.isEmpty()) idField.setText(savedId);
        bind(R.id.createAccountButton, () -> navigateTo("signup"));
        bind(R.id.forgotButton, () -> navigateTo("recovery"));
        bind(R.id.loginHelpButton, () -> navigateTo("help"));
        bind(R.id.signInButton, this::signIn);
        bind(R.id.staffAccessButton, () -> {
            staffLoginMode = !staffLoginMode;
            savedId = "";
            passwordVisible = false;
            renderScreen();
        });
        bind(R.id.togglePassword, () -> {
            EditText password = findViewById(R.id.loginPassword);
            if (password == null) return;
            int selection = password.getSelectionStart();
            passwordVisible = !passwordVisible;
            password.setInputType(passwordVisible
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            password.setSelection(Math.max(0, Math.min(selection, password.length())));
            ((TextView) findViewById(R.id.togglePassword))
                    .setText(passwordVisible ? "Hide" : "Show");
        });
    }

    private void signIn() {
        EditText id = findViewById(R.id.loginId);
        EditText password = findViewById(R.id.loginPassword);
        final String idValue = value(id);
        String passwordValue = value(password);
        if (!staffLoginMode && !isValidId(idValue)) {
            showError(id, "Enter a valid 13-digit ID number");
            return;
        }
        if (staffLoginMode && idValue.isEmpty()) {
            showError(id, "Enter your work email or ID number");
            return;
        }
        if (passwordValue.isEmpty()) {
            showError(password, "Enter your password");
            return;
        }

        final long loginGeneration = ++sessionGeneration;
        setBusy(R.id.signInButton, true);   // stop double taps while waiting for the server
        Toast.makeText(this, "Signing in... the first request can take a minute", Toast.LENGTH_SHORT).show();

        Api.login(idValue, passwordValue, new Api.Callback<String[]>() {
            @Override public void onSuccess(String[] result) {
                if (loginGeneration != sessionGeneration) return;
                // result[0] = role, result[1] = first name
                boolean staff = "OFFICIAL".equals(result[0]) || "ADMIN".equals(result[0]);
                if (staffLoginMode != staff) {
                    Api.signOut();
                    sessionGeneration++;
                    setBusy(R.id.signInButton, false);
                    Toast.makeText(MainActivity.this,
                            staffLoginMode ? "Use an authorized staff or admin account" : "Please use the staff sign-in panel",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                savedId = idValue;
                savedName = result[1].isEmpty() ? "Citizen" : result[1];
                screenHistory.clear();
                officialTab = "applications";
                adminUserSearch = "";
                adminUserOffset = 0;
                currentScreen = staff ? "official" : "dashboard";
                renderScreen();
            }
            @Override public void onError(String message) {
                if (loginGeneration != sessionGeneration) return;
                setBusy(R.id.signInButton, false);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ------------------------------------------------------------------
    // SIGN UP (now saves the account in the database)
    // ------------------------------------------------------------------

    private void createAccount() {
        EditText name = findViewById(R.id.signupName);
        EditText id = findViewById(R.id.signupId);
        EditText email = findViewById(R.id.signupEmail);
        EditText mobile = findViewById(R.id.signupMobile);
        EditText password = findViewById(R.id.signupPassword);
        EditText confirm = findViewById(R.id.signupConfirm);
        if (value(name).isEmpty()) {
            showError(name, "Enter your full name");
        } else if (!isValidId(value(id))) {
            showError(id, "Enter a valid 13-digit ID number");
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(value(email)).matches()) {
            showError(email, "Enter a valid email address");
        } else if (!isValidMobile(value(mobile))) {
            showError(mobile, "Enter a valid mobile number");
        } else if (value(password).length() < 8) {
            showError(password, "Use at least 8 characters");
        } else if (!value(password).equals(value(confirm))) {
            showError(confirm, "Passwords do not match");
        } else {
            // Split "Thandi Nkosi" into first name "Thandi" and last name "Nkosi"
            String full = value(name);
            String first = full;
            String last = "";
            int space = full.lastIndexOf(' ');
            if (space > 0) {
                first = full.substring(0, space).trim();
                last = full.substring(space + 1).trim();
            }
            final String idValue = value(id);
            final String firstName = first;
            final long registrationGeneration = sessionGeneration;

            setBusy(R.id.signupSubmit, true);
            Api.register(idValue, first, last, value(email), value(mobile), value(password), false,
                    new Api.Callback<Boolean>() {
                        @Override public void onSuccess(Boolean result) {
                            if (registrationGeneration != sessionGeneration) return;
                            savedId = idValue;
                            savedName = firstName;
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Account created")
                                    .setMessage("Your account was saved in the prototype database. Sign in with your ID number and password.")
                                    .setCancelable(false)
                                    .setPositiveButton("Go to sign in", (dialog, which) -> {
                                        screenHistory.clear();
                                        currentScreen = "login";
                                        renderScreen();
                                    })
                                    .show();
                        }
                        @Override public void onError(String message) {
                            if (registrationGeneration != sessionGeneration) return;
                            setBusy(R.id.signupSubmit, false);
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    });
        }
    }

    private void recoverAccount() {
        EditText id = findViewById(R.id.recoveryId);
        if (!isValidId(value(id))) {
            showError(id, "Enter a valid 13-digit ID number");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Next step")
                .setMessage("In the live service, password recovery instructions would be sent to the contact details on your account. No message was sent in this demo.")
                .setPositiveButton("Back to sign in", (dialog, which) -> {
                    screenHistory.clear();
                    currentScreen = "login";
                    renderScreen();
                })
                .show();
    }

    // ------------------------------------------------------------------
    // DASHBOARD (unchanged)
    // ------------------------------------------------------------------

    private void setupDashboard() {
        TextView greeting = findViewById(R.id.dashboardGreeting);
        if (!savedName.isEmpty()) greeting.setText("Good day, " + savedName);
        bind(R.id.notificationsButton, () -> navigateTo("notifications"));
        bind(R.id.progressCard, () -> navigateTo("tracking"));
        bind(R.id.passportButton, () -> openService("Passport"));
        bind(R.id.smartIdButton, () -> openService("Smart ID"));
        bind(R.id.certificatesButton, () -> openService("Certificates"));
        bind(R.id.applicationsButton, () -> navigateTo("applications"));
        bind(R.id.appointmentsButton, () -> navigateTo("appointments"));
        bind(R.id.guideButton, () -> navigateTo("guide"));
        bind(R.id.privacyButton, () -> navigateTo("privacy"));
        bind(R.id.helpButton, () -> navigateTo("help"));
        bind(R.id.signOutButton, this::signOut);

        EditText search = findViewById(R.id.serviceSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase();
                setVisible(R.id.servicePassport, query.isEmpty() || "passport".contains(query) || "renew".contains(query));
                setVisible(R.id.serviceSmartId, query.isEmpty() || "smart id".contains(query) || "id".contains(query));
                setVisible(R.id.serviceCertificates, query.isEmpty() || "certificate".contains(query) || "certificates".contains(query) || "birth".contains(query) || "marriage".contains(query));
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void openService(String service) {
        selectedService = service;
        navigateTo("service");
    }

    // ------------------------------------------------------------------
    // SERVICE FORM (now saves the application, then offers a simulated payment)
    // ------------------------------------------------------------------

    private void setupServiceForm() {
        ((TextView) findViewById(R.id.serviceTitle)).setText(selectedService + " application");
        EditText applicantName = findViewById(R.id.formName);
        EditText applicantId = findViewById(R.id.formId);
        if (!savedName.isEmpty()) applicantName.setText(savedName);
        if (!savedId.isEmpty()) applicantId.setText(savedId);
        Spinner serviceType = findViewById(R.id.serviceType);
        TextView serviceHint = findViewById(R.id.serviceHint);
        String[] options;
        if ("Passport".equals(selectedService)) {
            options = new String[] {"New passport", "Renewal", "Lost or stolen passport", "Damaged passport"};
            serviceHint.setText("Choose the passport service you need.");
        } else if ("Smart ID".equals(selectedService)) {
            options = new String[] {"First-time Smart ID", "Renewal", "Lost or stolen card", "Damaged card"};
            serviceHint.setText("Choose the Smart ID service you need.");
        } else {
            options = new String[] {"Birth certificate", "Marriage certificate", "Death certificate", "Certificate amendment"};
            serviceHint.setText("Choose the certificate service you need.");
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        serviceType.setAdapter(adapter);
        bind(R.id.serviceBack, this::goBack);
        bind(R.id.submitApplication, this::submitApplication);
    }

    /** Maps the option picked in the form to a service in the database. */
    private String serviceIdFor(String option) {
        switch (option) {
            case "New passport":            return "passport_new";
            case "Lost or stolen passport":
            case "Damaged passport":        return "passport_renewal";
            case "First-time Smart ID":     return "smart_id_first";
            case "Lost or stolen card":
            case "Damaged card":            return "smart_id_replacement";
            case "Certificate amendment":   return "name_change";
            case "Renewal":
                return "Passport".equals(selectedService) ? "passport_renewal" : "smart_id_replacement";
            default:                        return "certificate_request";  // birth, marriage, death certificates
        }
    }

    private void submitApplication() {
        EditText name = findViewById(R.id.formName);
        EditText id = findViewById(R.id.formId);
        EditText mobile = findViewById(R.id.formMobile);
        Spinner serviceType = findViewById(R.id.serviceType);
        if (value(name).isEmpty()) {
            showError(name, "Enter your full name");
            return;
        }
        if (!isValidId(value(id))) {
            showError(id, "Enter a valid 13-digit ID number");
            return;
        }
        if (!isValidMobile(value(mobile))) {
            showError(mobile, "Enter a valid mobile number");
            return;
        }

        // Copy the values now, because the screen can change before the user taps the dialog button
        final String option = String.valueOf(serviceType.getSelectedItem());
        final String nameVal = value(name);
        final String idVal = value(id);
        final String mobileVal = value(mobile);

        new AlertDialog.Builder(this)
                .setTitle("Review " + option + " application")
                .setMessage("This saves your application in the prototype database. It is NOT sent to the real Department of Home Affairs. Use fake details only.")
                .setNegativeButton("Review details", null)
                .setPositiveButton("Submit application", (dialog, which) -> {
                    // These answers are stored with the application
                    Map<String, String> form = new HashMap<>();
                    form.put("Service type", option);
                    form.put("Full name", nameVal);
                    form.put("ID number", idVal);
                    form.put("Mobile number", mobileVal);

                    Toast.makeText(this, "Submitting...", Toast.LENGTH_SHORT).show();
                    final long requestGeneration = sessionGeneration;
                    Api.submitApplication(serviceIdFor(option), form, new Api.Callback<Integer>() {
                        @Override public void onSuccess(Integer appId) {
                            if (requestGeneration != sessionGeneration) return;
                            askToPay(appId);
                        }
                        @Override public void onError(String message) {
                            if (requestGeneration != sessionGeneration) return;
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .show();
    }

    /** After submitting, offer the simulated fee payment. */
    private void askToPay(final int appId) {
        new AlertDialog.Builder(this)
                .setTitle("Application #" + appId + " submitted")
                .setMessage("Would you like to pay the application fee now? Payment is simulated, so no real money moves.")
                .setCancelable(false)
                .setPositiveButton("Pay now", (dialog, which) -> {
                    final long requestGeneration = sessionGeneration;
                    Api.pay(appId, new Api.Callback<Double>() {
                        @Override public void onSuccess(Double amount) {
                            if (requestGeneration != sessionGeneration) return;
                            String text = amount == null
                                    ? "No fee for this service"
                                    : String.format(Locale.US, "Payment of R%.2f recorded (simulated)", amount);
                            Toast.makeText(MainActivity.this, text, Toast.LENGTH_LONG).show();
                        }
                        @Override public void onError(String message) {
                            if (requestGeneration != sessionGeneration) return;
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    });
                    navigateTo("applications");
                })
                .setNegativeButton("Pay later", (dialog, which) -> navigateTo("applications"))
                .show();
    }

    // ------------------------------------------------------------------
    // APPLICATIONS (shows the newest application and its stage from the database)
    // ------------------------------------------------------------------

    private void setupApplications() {
        bind(R.id.applicationsBack, this::goBack);
        bind(R.id.openTracking, () -> navigateTo("tracking"));
        bind(R.id.newApplication, () -> openService("Passport"));
        final TextView title = findViewById(R.id.draftApplicationTitle);
        final TextView status = findViewById(R.id.draftApplicationStatus);
        final long requestGeneration = sessionGeneration;

        Api.latestApplication(new Api.Callback<Api.AppInfo>() {
            @Override public void onSuccess(Api.AppInfo info) {
                if (requestGeneration != sessionGeneration
                        || !"applications".equals(currentScreen) || info == null) return;
                title.setText(info.service + " application");
                status.setText("Stage: " + info.stage + " · "
                        + info.status.replace('_', ' ') + " · #" + info.id);
            }
            @Override public void onError(String message) {
                if (requestGeneration != sessionGeneration) return;
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ------------------------------------------------------------------
    // APPOINTMENTS (saved in the database)
    // ------------------------------------------------------------------

    private void setupAppointments() {
        bind(R.id.appointmentsBack, this::goBack);
        bind(R.id.appointmentGuide, () -> navigateTo("guide"));
        bind(R.id.requestAppointment, () -> navigateTo("appointmentRequest"));
        final TextView state = findViewById(R.id.appointmentState);
        final TextView date = findViewById(R.id.appointmentDate);
        final TextView location = findViewById(R.id.appointmentLocation);
        final long requestGeneration = sessionGeneration;

        Api.latestAppointment(new Api.Callback<Api.ApptInfo>() {
            @Override public void onSuccess(Api.ApptInfo a) {
                if (requestGeneration != sessionGeneration
                        || !"appointments".equals(currentScreen) || a == null) return;
                state.setText(a.status);
                date.setText(a.date);
                location.setText(a.branch + " · " + a.service);
            }
            @Override public void onError(String message) {
                if (requestGeneration != sessionGeneration) return;
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupAppointmentRequest() {
        bind(R.id.requestBack, this::goBack);
        bind(R.id.submitAppointment, this::saveAppointmentRequest);
    }

    private void saveAppointmentRequest() {
        EditText office = findViewById(R.id.requestOffice);
        EditText date = findViewById(R.id.requestDate);
        EditText purpose = findViewById(R.id.requestPurpose);
        if (value(office).isEmpty()) {
            showError(office, "Enter a preferred office, e.g. Johannesburg");
        } else if (value(date).isEmpty()) {
            showError(date, "Enter a date as YYYY-MM-DD, e.g. 2026-11-20");
        } else if (value(purpose).isEmpty()) {
            showError(purpose, "Enter the service you need");
        } else {
            setBusy(R.id.submitAppointment, true);
            final long requestGeneration = sessionGeneration;
            Api.requestAppointment(value(office), value(date), value(purpose), new Api.Callback<Boolean>() {
                @Override public void onSuccess(Boolean result) {
                    if (requestGeneration != sessionGeneration) return;
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Appointment saved")
                            .setMessage("Your appointment was saved in the prototype database. No real Home Affairs appointment has been booked.")
                            .setPositiveButton("View appointments", (dialog, which) -> navigateTo("appointments"))
                            .show();
                }
                @Override public void onError(String message) {
                    if (requestGeneration != sessionGeneration) return;
                    setBusy(R.id.submitAppointment, false);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    // ------------------------------------------------------------------
    // NOTIFICATIONS and INFO
    // ------------------------------------------------------------------

    private void setupNotifications() {
        bind(R.id.notificationsBack, this::goBack);
        bind(R.id.notificationPassport, () -> navigateTo("tracking"));
        bind(R.id.notificationAppointment, () -> navigateTo("appointments"));
        Button markRead = findViewById(R.id.markNotificationsRead);
        if (notificationsRead) markRead.setText("All caught up");
        markRead.setOnClickListener(view -> {
            notificationsRead = true;
            markRead.setText("All caught up");
            Toast.makeText(this, "Notifications marked as read", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupInfo() {
        boolean privacy = "privacy".equals(currentScreen);
        ((TextView) findViewById(R.id.infoTitle)).setText(privacy ? "Privacy and your data" : "Help and support");
        TextView body = findViewById(R.id.infoBody);
        body.setText(privacy
                ? "This is a student prototype. Your details are saved in a small test database to demonstrate how the service would work. Please use fake details only, such as a made-up ID number. Before launch, the service would need a production-grade secure backend, a clear privacy notice, and approved data-handling practices that follow POPIA."
                : "This prototype is a demonstration and nothing is sent to the real Department of Home Affairs. Create an account with a 13-digit ID number (use a made-up one), then sign in with the same ID number and password. If you need help using the prototype, use the Back button to return to your services.");
        bind(R.id.infoBack, this::goBack);
        bind(R.id.infoContact, this::goBack);
    }

    // ------------------------------------------------------------------
    // OFFICIAL SCREEN (built in code; shown when an OFFICIAL account signs in)
    // ------------------------------------------------------------------

    private void showOfficialScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getResources().getColor(R.color.page));
        scroll.setFillViewport(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(26), dp(24), dp(30));
        scroll.addView(box, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        buildOfficialList(box);
    }

    /** Staff dashboard. Account controls are shown only to database-verified ADMIN users. */
    private void buildOfficialList(final LinearLayout box) {
        box.removeAllViews();
        final boolean admin = "ADMIN".equals(Api.role);

        getLayoutInflater().inflate(R.layout.view_brand_header, box, true);
        View accent = new View(this);
        accent.setBackgroundColor(getResources().getColor(R.color.gold));
        LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(dp(52), dp(3));
        accentParams.topMargin = dp(28);
        box.addView(accent, accentParams);

        TextView title = new TextView(this);
        title.setText(admin ? "Administrator dashboard" : "Staff dashboard");
        title.setTextColor(getResources().getColor(R.color.forest));
        title.setTextSize(26);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        box.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(admin
                ? "Manage applications and citizen accounts. Account identifiers are masked."
                : "Review applications assigned to Home Affairs staff.");
        subtitle.setTextColor(getResources().getColor(R.color.text_secondary));
        subtitle.setPadding(0, dp(4), 0, dp(14));
        box.addView(subtitle);

        Button signOutButton = new Button(this);
        signOutButton.setText("Sign out");
        signOutButton.setAllCaps(false);
        signOutButton.setTextColor(getResources().getColor(R.color.forest));
        signOutButton.setBackgroundResource(R.drawable.bg_secondary_button);
        signOutButton.setOnClickListener(v -> signOut());
        box.addView(signOutButton);

        if (admin) {
            LinearLayout tabs = new LinearLayout(this);
            tabs.setOrientation(LinearLayout.HORIZONTAL);
            tabs.setPadding(0, dp(12), 0, dp(12));
            addTab(tabs, "Applications", "applications");
            addTab(tabs, "User accounts", "users");
            addTab(tabs, "Audit log", "audit");
            box.addView(tabs);
        }

        if (admin && "users".equals(officialTab)) {
            buildAdminUsers(box);
            return;
        }
        if (admin && "audit".equals(officialTab)) {
            buildAdminAudit(box);
            return;
        }

        final TextView note = new TextView(this);
        note.setText("Loading applications...");
        note.setTextColor(getResources().getColor(R.color.text_secondary));
        note.setPadding(0, dp(12), 0, dp(8));
        box.addView(note);
        final long requestGeneration = sessionGeneration;

        Api.staffApplications(new Api.Callback<List<Api.StaffApp>>() {
            @Override public void onSuccess(List<Api.StaffApp> list) {
                if (requestGeneration != sessionGeneration
                        || !"official".equals(currentScreen)) return;
                note.setText(list.isEmpty() ? "No applications yet." : list.size() + " application(s)");
                for (final Api.StaffApp a : list) {
                    LinearLayout card = makeAdminCard();
                    TextView info = new TextView(MainActivity.this);
                    info.setText("#" + a.id + "  " + a.service
                            + "\nApplicant: " + a.applicant
                            + "\nStage: " + a.stage + " (" + a.status + ")");
                    info.setTextColor(getResources().getColor(R.color.text_primary));
                    card.addView(info);

                    Button advance = new Button(MainActivity.this);
                    advance.setText("Advance to next stage");
                    styleAdminButton(advance, true);
                    final long actionGeneration = sessionGeneration;
                    advance.setOnClickListener(v -> Api.advance(a.id, new Api.Callback<Boolean>() {
                        @Override public void onSuccess(Boolean ok) {
                            if (actionGeneration != sessionGeneration
                                    || !"official".equals(currentScreen)) return;
                            buildOfficialList(box);   // reload to show the new stage
                        }
                        @Override public void onError(String message) {
                            if (actionGeneration != sessionGeneration) return;
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    }));
                    card.addView(advance);
                    box.addView(card);
                }
            }
            @Override public void onError(String message) {
                if (requestGeneration != sessionGeneration) return;
                note.setText(message);
            }
        });
    }

    private void addTab(LinearLayout tabs, String label, String tab) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setTextColor(getResources().getColor(
                officialTab.equals(tab) ? R.color.surface : R.color.forest));
        button.setBackgroundResource(officialTab.equals(tab)
                ? R.drawable.bg_primary_button
                : R.drawable.bg_secondary_button);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(46), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        tabs.addView(button, params);
        button.setOnClickListener(v -> {
            officialTab = tab;
            if (!"users".equals(tab)) adminUserOffset = 0;
            showOfficialScreen();
        });
    }

    private LinearLayout makeAdminCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(7), 0, dp(7));
        card.setLayoutParams(params);
        return card;
    }

    private void styleAdminButton(Button button, boolean primary) {
        button.setAllCaps(false);
        button.setTextColor(getResources().getColor(
                primary ? R.color.surface : R.color.forest));
        button.setBackgroundResource(primary
                ? R.drawable.bg_primary_button : R.drawable.bg_secondary_button);
    }

    private void buildAdminUsers(final LinearLayout box) {
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search name, email or ID");
        search.setText(adminUserSearch);
        search.setBackgroundResource(R.drawable.bg_input);
        search.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        searchParams.setMargins(0, 0, dp(8), 0);
        searchRow.addView(search, searchParams);

        Button searchButton = new Button(this);
        searchButton.setText("Search");
        searchButton.setAllCaps(false);
        searchRow.addView(searchButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(50)));
        box.addView(searchRow);

        final TextView note = new TextView(this);
        note.setText("Loading accounts...");
        note.setTextColor(getResources().getColor(R.color.text_secondary));
        note.setPadding(0, dp(10), 0, dp(5));
        box.addView(note);

        searchButton.setOnClickListener(v -> {
            adminUserSearch = value(search);
            adminUserOffset = 0;
            showOfficialScreen();
        });

        final long requestGeneration = sessionGeneration;
        Api.adminUsers(adminUserSearch, adminUserOffset, new Api.Callback<Api.AdminUserPage>() {
            @Override public void onSuccess(Api.AdminUserPage page) {
                if (requestGeneration != sessionGeneration
                        || !"official".equals(currentScreen)
                        || !"users".equals(officialTab)) return;
                note.setText(page.users.isEmpty()
                        ? "No matching accounts."
                        : "Accounts " + (page.offset + 1) + "–"
                        + (page.offset + page.users.size()));

                for (final Api.AdminUser user : page.users) {
                    LinearLayout card = makeAdminCard();
                    TextView details = new TextView(MainActivity.this);
                    details.setText(user.firstName + " " + user.lastName
                            + "\nAccount #" + user.id + " · " + user.role
                            + "\nID: " + user.idNumberMasked
                            + " · Email: " + user.emailMasked
                            + "\nStatus: " + user.status);
                    details.setTextColor(getResources().getColor(R.color.text_primary));
                    card.addView(details);

                    if (!"ADMIN".equals(user.role)) {
                        Button action = new Button(MainActivity.this);
                        final String nextStatus = "SUSPENDED".equals(user.status)
                                ? "ACTIVE" : "SUSPENDED";
                        action.setText("SUSPENDED".equals(user.status)
                                ? "Reactivate account" : "Suspend account");
                        action.setAllCaps(false);
                        styleAdminButton(action, "SUSPENDED".equals(user.status));
                        card.addView(action);
                        action.setOnClickListener(v -> confirmAccountStatus(user, nextStatus));
                    } else {
                        TextView protectedAdmin = new TextView(MainActivity.this);
                        protectedAdmin.setText("Admin roles are managed through the trusted deployment process.");
                        protectedAdmin.setTextColor(getResources().getColor(R.color.text_secondary));
                        protectedAdmin.setPadding(0, dp(10), 0, 0);
                        card.addView(protectedAdmin);
                    }
                    box.addView(card);
                }

                LinearLayout paging = new LinearLayout(MainActivity.this);
                paging.setGravity(android.view.Gravity.CENTER);
                if (page.offset > 0) {
                    Button previous = new Button(MainActivity.this);
                    previous.setText("Previous");
                    previous.setAllCaps(false);
                    paging.addView(previous);
                    previous.setOnClickListener(v -> {
                        adminUserOffset = Math.max(0, page.offset - page.limit);
                        showOfficialScreen();
                    });
                }
                if (page.hasMore) {
                    Button next = new Button(MainActivity.this);
                    next.setText("Next");
                    next.setAllCaps(false);
                    paging.addView(next);
                    next.setOnClickListener(v -> {
                        adminUserOffset = page.offset + page.limit;
                        showOfficialScreen();
                    });
                }
                box.addView(paging);
            }

            @Override public void onError(String message) {
                if (requestGeneration != sessionGeneration) return;
                note.setText(message);
            }
        });
    }

    private void confirmAccountStatus(Api.AdminUser user, String status) {
        boolean suspend = "SUSPENDED".equals(status);
        new AlertDialog.Builder(this)
                .setTitle(suspend ? "Suspend this account?" : "Reactivate this account?")
                .setMessage(suspend
                        ? "This account will no longer be able to sign in. Existing access tokens will be rejected."
                        : "This account will be allowed to sign in again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(suspend ? "Suspend" : "Reactivate", (dialog, which) -> {
                    final long requestGeneration = sessionGeneration;
                    Api.setUserStatus(user.id, status, new Api.Callback<Boolean>() {
                        @Override public void onSuccess(Boolean result) {
                            if (requestGeneration != sessionGeneration
                                    || !"users".equals(officialTab)) return;
                            Toast.makeText(MainActivity.this, "Account status updated", Toast.LENGTH_SHORT).show();
                            showOfficialScreen();
                        }
                        @Override public void onError(String message) {
                            if (requestGeneration != sessionGeneration) return;
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .show();
    }

    private void buildAdminAudit(final LinearLayout box) {
        TextView note = new TextView(this);
        note.setText("Recent account-management activity");
        note.setTextColor(getResources().getColor(R.color.text_secondary));
        note.setPadding(0, dp(6), 0, dp(8));
        box.addView(note);
        final long requestGeneration = sessionGeneration;
        Api.adminAudit(new Api.Callback<List<Api.AuditEntry>>() {
            @Override public void onSuccess(List<Api.AuditEntry> entries) {
                if (requestGeneration != sessionGeneration
                        || !"official".equals(currentScreen)
                        || !"audit".equals(officialTab)) return;
                note.setText(entries.isEmpty() ? "No account changes recorded." : "Last " + entries.size() + " account changes");
                for (Api.AuditEntry entry : entries) {
                    LinearLayout card = makeAdminCard();
                    TextView line = new TextView(MainActivity.this);
                    line.setText(entry.action.replace('_', ' ')
                            + "\nAdministrator: " + entry.actor
                            + "\nAccount: " + (entry.target == null || entry.target.trim().isEmpty()
                                    ? "unavailable" : entry.target)
                            + "\n" + entry.createdAt);
                    line.setTextColor(getResources().getColor(R.color.text_primary));
                    card.addView(line);
                    box.addView(card);
                }
            }
            @Override public void onError(String message) {
                if (requestGeneration != sessionGeneration) return;
                note.setText(message);
            }
        });
    }

    // ------------------------------------------------------------------
    // HELPERS
    // ------------------------------------------------------------------

    private void signOut() {
        sessionGeneration++;
        Api.signOut();
        staffLoginMode = false;
        officialTab = "applications";
        adminUserSearch = "";
        adminUserOffset = 0;
        screenHistory.clear();
        currentScreen = "login";
        savedId = "";
        savedName = "";
        renderScreen();
        Toast.makeText(this, "You have signed out", Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    /** Disables or enables a button while a server request is running. */
    private void setBusy(int id, boolean busy) {
        View view = findViewById(id);
        if (view != null) view.setEnabled(!busy);
    }

    private void setVisible(int id, boolean visible) {
        View view = findViewById(id);
        if (view != null) view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void bind(int id, Runnable action) {
        View view = findViewById(id);
        if (view != null) view.setOnClickListener(ignored -> action.run());
    }

    private String value(EditText field) {
        return field == null || field.getText() == null ? "" : field.getText().toString().trim();
    }

    private boolean isValidId(String value) {
        return value != null && value.matches("\\d{13}");
    }

    private boolean isValidMobile(String value) {
        if (value == null) return false;
        String digits = value.replaceAll("[^0-9]", "");
        return digits.length() >= 9 && digits.length() <= 12;
    }

    private void showError(EditText field, String message) {
        if (field != null) {
            field.setError(message);
            field.requestFocus();
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
