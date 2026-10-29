package za.gov.dha;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.Deque;

public class MainActivity extends Activity {
    private final Deque<String> screenHistory = new ArrayDeque<>();
    private String currentScreen = "login";
    private String selectedService = "Passport";
    private String savedId = "";
    private String savedName = "";
    private boolean demoApplicationSubmitted = false;
    private String demoApplicationTitle = "";
    private boolean appointmentRequested = false;
    private String requestedOffice = "";
    private String requestedDate = "";
    private boolean notificationsRead = false;
    private boolean passwordVisible = false;

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
            currentScreen = "login";
            renderScreen();
        } else {
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
            case "login":
            default:
                currentScreen = "login";
                setContentView(R.layout.screen_login);
                setupLogin();
                break;
        }
    }

    private void setupLogin() {
        EditText idField = findViewById(R.id.loginId);
        if (!savedId.isEmpty()) idField.setText(savedId);
        bind(R.id.createAccountButton, () -> navigateTo("signup"));
        bind(R.id.forgotButton, () -> navigateTo("recovery"));
        bind(R.id.loginHelpButton, () -> navigateTo("help"));
        bind(R.id.signInButton, this::signIn);
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
        String idValue = value(id);
        String passwordValue = value(password);
        if (!isValidId(idValue)) {
            showError(id, "Enter a valid 13-digit ID number");
            return;
        }
        if (passwordValue.isEmpty()) {
            showError(password, "Enter your password");
            return;
        }
        savedId = idValue;
        if (savedName.isEmpty()) savedName = "Citizen";
        screenHistory.clear();
        currentScreen = "dashboard";
        renderScreen();
        Toast.makeText(this, "Demo sign-in successful", Toast.LENGTH_SHORT).show();
    }

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
            savedId = value(id);
            savedName = value(name);
            new AlertDialog.Builder(this)
                    .setTitle("Account details ready")
                    .setMessage("This demo does not create or store an account. A secure account service can be connected later.")
                    .setPositiveButton("Go to sign in", (dialog, which) -> {
                        screenHistory.clear();
                        currentScreen = "login";
                        renderScreen();
                    })
                    .show();
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
        new AlertDialog.Builder(this)
                .setTitle("Review " + serviceType.getSelectedItem() + " application")
                .setMessage("This demo will save a temporary application summary on this device only. It will not be sent to Home Affairs.")
                .setNegativeButton("Review details", null)
                .setPositiveButton("Save demo application", (dialog, which) -> {
                    demoApplicationSubmitted = true;
                    demoApplicationTitle = String.valueOf(serviceType.getSelectedItem());
                    Toast.makeText(this, "Demo application saved for this session", Toast.LENGTH_LONG).show();
                    navigateTo("applications");
                })
                .show();
    }

    private void setupApplications() {
        bind(R.id.applicationsBack, this::goBack);
        bind(R.id.openTracking, () -> navigateTo("tracking"));
        bind(R.id.newApplication, () -> openService("Passport"));
        TextView title = findViewById(R.id.draftApplicationTitle);
        TextView status = findViewById(R.id.draftApplicationStatus);
        if (demoApplicationSubmitted) {
            title.setText(demoApplicationTitle + " application");
            status.setText("Saved in this session · Demo only");
        }
    }

    private void setupAppointments() {
        bind(R.id.appointmentsBack, this::goBack);
        bind(R.id.appointmentGuide, () -> navigateTo("guide"));
        bind(R.id.requestAppointment, () -> navigateTo("appointmentRequest"));
        TextView state = findViewById(R.id.appointmentState);
        TextView date = findViewById(R.id.appointmentDate);
        TextView location = findViewById(R.id.appointmentLocation);
        if (appointmentRequested) {
            state.setText("DEMO REQUEST SAVED");
            date.setText(requestedDate);
            location.setText(requestedOffice);
        }
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
            showError(office, "Enter a preferred office");
        } else if (value(date).isEmpty()) {
            showError(date, "Enter a preferred date");
        } else if (value(purpose).isEmpty()) {
            showError(purpose, "Enter the service you need");
        } else {
            appointmentRequested = true;
            requestedOffice = value(office);
            requestedDate = value(date);
            new AlertDialog.Builder(this)
                    .setTitle("Demo request saved")
                    .setMessage("No real appointment has been booked. You can review this local demo request on the appointments page.")
                    .setPositiveButton("View appointments", (dialog, which) -> navigateTo("appointments"))
                    .show();
        }
    }

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
                ? "This is an interface prototype. It does not send your personal information to a server or connect to a database. Details entered in forms are only used temporarily to demonstrate the screen flow. Before launch, the service needs a secure backend, a clear privacy notice, and approved data-handling practices."
                : "For this prototype, all actions are demonstrations and no Home Affairs request is submitted. Sign-in accepts any 13-digit ID number with any non-empty password. For a real service, connect these screens to an approved secure backend. If you need help using the prototype, use the Back button to return to your services.");
        bind(R.id.infoBack, this::goBack);
        bind(R.id.infoContact, this::goBack);
    }

    private void signOut() {
        screenHistory.clear();
        currentScreen = "login";
        savedId = "";
        savedName = "";
        renderScreen();
        Toast.makeText(this, "You have signed out", Toast.LENGTH_SHORT).show();
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
