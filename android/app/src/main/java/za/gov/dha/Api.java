package za.gov.dha;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Talks to the Node/MySQL API. Network calls run on a background thread,
 * and the result is delivered back on the main (UI) thread through a Callback,
 * so it is safe to touch views inside onSuccess / onError.
 */
public class Api {

    // 10.0.2.2 = "my laptop" as seen from the Android EMULATOR.
    // Real phone on the same Wi-Fi: use your laptop's IP, e.g. "http://192.168.1.20:3000"
    // After deploying: use your https://....onrender.com address (no trailing slash)
    public static final String BASE_URL = "http://10.0.2.2:3000";

    public static String token = null;   // sent with every request after login
    public static String role = null;    // CITIZEN, OFFICIAL...

    /** Result handler used by every call. */
    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    // ---------- Small data holders ----------
    public static class AppInfo {
        public int id; public String service, status, stage;
    }
    public static class ApptInfo {
        public String date, service, branch, status;
    }
    public static class StaffApp {
        public int id; public String service, applicant, status, stage;
    }

    private static final ExecutorService pool = Executors.newFixedThreadPool(2);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ------------------------------------------------------------------
    // Core: run a job in the background and hand the result to the UI thread
    // ------------------------------------------------------------------
    private static <T> void run(final Callable<T> job, final Callback<T> cb) {
        pool.execute(() -> {
            try {
                final T result = job.call();
                mainHandler.post(() -> cb.onSuccess(result));
            } catch (final Exception e) {
                final String msg = friendly(e);
                mainHandler.post(() -> cb.onError(msg));
            }
        });
    }

    private static String friendly(Exception e) {
        if (e instanceof IOException) {
            return "Cannot reach the server. Check your internet and that the API is running.";
        }
        return e.getMessage() == null ? "Something went wrong" : e.getMessage();
    }

    /** Sends one HTTP request and returns the response body as text. */
    private static String request(String method, String path, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(30000);   // a free host can take ~1 minute to wake up
        c.setReadTimeout(70000);
        c.setRequestProperty("Content-Type", "application/json");
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);

        if ("POST".equals(method)) {
            c.setDoOutput(true);
            byte[] data = (body == null ? new JSONObject() : body).toString().getBytes("UTF-8");
            try (OutputStream os = c.getOutputStream()) { os.write(data); }
        }

        int code = c.getResponseCode();
        String text = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) {
            String msg;
            try { msg = new JSONObject(text).getString("error"); }   // API errors look like {"error":"..."}
            catch (Exception ignored) { msg = "Server error (" + code + ")"; }
            throw new Exception(msg);
        }
        return text;
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Account
    // ------------------------------------------------------------------

    /** Logs in with the ID number. Returns {role, firstName}. */
    public static void login(final String idNumber, final String password, Callback<String[]> cb) {
        run(() -> {
            JSONObject body = new JSONObject().put("idNumber", idNumber).put("password", password);
            JSONObject json = new JSONObject(request("POST", "/api/login", body));
            token = json.getString("token");
            role = json.optString("role");
            return new String[] { role, json.optString("firstName") };
        }, cb);
    }

    /** Creates an account. The user then signs in on the login screen. */
    public static void register(final String idNumber, final String firstName, final String lastName,
                                final String email, final String phone, final String password,
                                final boolean foreignNational, Callback<Boolean> cb) {
        run(() -> {
            JSONObject body = new JSONObject()
                    .put("idNumber", idNumber).put("firstName", firstName).put("lastName", lastName)
                    .put("email", email).put("phone", phone).put("password", password)
                    .put("role", foreignNational ? "FOREIGN_NATIONAL" : "CITIZEN");
            request("POST", "/api/register", body);
            return true;
        }, cb);
    }

    public static void signOut() {
        token = null;
        role = null;
    }

    // ------------------------------------------------------------------
    // Applications
    // ------------------------------------------------------------------

    /** Saves an application for ANY service. Returns the new application number. */
    public static void submitApplication(final String serviceId, final Map<String, String> formData,
                                         Callback<Integer> cb) {
        run(() -> {
            JSONObject body = new JSONObject().put("serviceId", serviceId).put("formData", new JSONObject(formData));
            return new JSONObject(request("POST", "/api/applications", body)).getInt("applicationId");
        }, cb);
    }

    /** The user's newest application, or null if they have none. */
    public static void latestApplication(Callback<AppInfo> cb) {
        run(() -> {
            JSONArray arr = new JSONArray(request("GET", "/api/applications/mine", null));
            if (arr.length() == 0) return null;
            JSONObject o = arr.getJSONObject(0);          // the API returns newest first
            AppInfo a = new AppInfo();
            a.id = o.getInt("application_id");
            a.service = o.getString("service_name");
            a.status = o.getString("status");
            a.stage = o.optString("current_stage");
            return a;
        }, cb);
    }

    /** Simulated payment. Returns the amount paid, or null if the service has no fee. */
    public static void pay(final int applicationId, Callback<Double> cb) {
        run(() -> {
            JSONObject body = new JSONObject().put("applicationId", applicationId).put("method", "CARD");
            JSONObject json = new JSONObject(request("POST", "/api/payments", body));
            if (json.optBoolean("paid")) return json.getDouble("amount");
            return null;
        }, cb);
    }

    // ------------------------------------------------------------------
    // Appointments
    // ------------------------------------------------------------------

    /** Books an appointment from the free-text form. date format: YYYY-MM-DD. */
    public static void requestAppointment(final String office, final String date, final String purpose,
                                          Callback<Boolean> cb) {
        run(() -> {
            JSONObject body = new JSONObject().put("office", office).put("date", date).put("purpose", purpose);
            request("POST", "/api/appointment-requests", body);
            return true;
        }, cb);
    }

    /** The user's latest appointment, or null if none. */
    public static void latestAppointment(Callback<ApptInfo> cb) {
        run(() -> {
            JSONArray arr = new JSONArray(request("GET", "/api/appointments/mine", null));
            if (arr.length() == 0) return null;
            JSONObject o = arr.getJSONObject(arr.length() - 1);
            ApptInfo a = new ApptInfo();
            String dt = o.optString("appointment_datetime");
            a.date = dt.length() >= 10 ? dt.substring(0, 10) : dt;   // keep just YYYY-MM-DD
            a.service = o.optString("service_name");
            a.branch = o.optString("branch_name");
            a.status = o.optString("status");
            return a;
        }, cb);
    }

    // ------------------------------------------------------------------
    // Official
    // ------------------------------------------------------------------

    public static void staffApplications(Callback<List<StaffApp>> cb) {
        run(() -> {
            JSONArray arr = new JSONArray(request("GET", "/api/staff/applications", null));
            List<StaffApp> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                StaffApp s = new StaffApp();
                s.id = o.getInt("application_id");
                s.service = o.getString("service_name");
                s.applicant = o.getString("first_name") + " " + o.getString("last_name");
                s.status = o.getString("status");
                s.stage = o.optString("current_stage");
                list.add(s);
            }
            return list;
        }, cb);
    }

    /** Moves an application to its next stage. */
    public static void advance(final int applicationId, Callback<Boolean> cb) {
        run(() -> {
            request("POST", "/api/staff/applications/" + applicationId + "/advance", null);
            return true;
        }, cb);
    }
}