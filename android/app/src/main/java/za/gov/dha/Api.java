
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
import java.util.concurrent.atomic.AtomicLong;

public class Api {

    // Live Render backend
    public static final String BASE_URL =
            "https://citizen-services-portal.onrender.com";

    public static volatile String token = null;
    public static volatile String role = null;
    private static final AtomicLong authGeneration = new AtomicLong();

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    public static class AppInfo {
        public int id;
        public String service, status, stage;
    }

    public static class ApptInfo {
        public String date, service, branch, status;
    }

    public static class StaffApp {
        public int id;
        public String service, applicant, status, stage;
    }

    private static final ExecutorService pool =
            Executors.newFixedThreadPool(2);

    private static final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private static <T> void run(
            final Callable<T> job,
            final Callback<T> cb) {

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
            return "Cannot reach the server. Check your internet connection.";
        }

        return e.getMessage() == null
                ? "Something went wrong"
                : e.getMessage();
    }

    private static String request(
            String method,
            String path,
            JSONObject body,
            String authToken) throws Exception {

        HttpURLConnection c =
                (HttpURLConnection) new URL(BASE_URL + path)
                        .openConnection();

        c.setRequestMethod(method);
        c.setConnectTimeout(30000);
        c.setReadTimeout(70000);

        c.setRequestProperty(
                "Content-Type",
                "application/json"
        );

        if (authToken != null) {
            c.setRequestProperty(
                    "Authorization",
                    "Bearer " + authToken
            );
        }

        try {
            if ("POST".equals(method)) {
                c.setDoOutput(true);

                byte[] data = (
                        body == null ? new JSONObject() : body
                ).toString().getBytes("UTF-8");

                try (OutputStream os = c.getOutputStream()) {
                    os.write(data);
                }
            }

            int code = c.getResponseCode();

            String response = read(
                    code >= 400
                            ? c.getErrorStream()
                            : c.getInputStream()
            );

            if (code >= 400) {
                String msg;

                try {
                    msg = new JSONObject(response).getString("error");
                } catch (Exception ignored) {
                    msg = "Server error (" + code + ")";
                }

                throw new Exception(msg);
            }

            return response;
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in)
            throws IOException {

        if (in == null) return "";

        StringBuilder sb = new StringBuilder();

        try (BufferedReader r =
                     new BufferedReader(
                             new InputStreamReader(in, "UTF-8"))) {

            String line;

            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
        }

        return sb.toString();
    }

    // ACCOUNT

    public static void login(
            final String idNumber,
            final String password,
            Callback<String[]> cb) {

        final long generation = authGeneration.incrementAndGet();
        token = null;
        role = null;

        run(() -> {
            JSONObject body = new JSONObject()
                    .put("idNumber", idNumber)
                    .put("password", password);

            JSONObject json = new JSONObject(
                    request("POST", "/api/login", body, null)
            );

            if (authGeneration.get() != generation) {
                throw new IOException("Sign-in was cancelled");
            }
            token = json.getString("token");
            role = json.optString("role");

            return new String[]{
                    role,
                    json.optString("firstName")
            };

        }, cb);
    }

    public static void register(
            final String idNumber,
            final String firstName,
            final String lastName,
            final String email,
            final String phone,
            final String password,
            final boolean foreignNational,
            Callback<Boolean> cb) {

        run(() -> {
            JSONObject body = new JSONObject()
                    .put("idNumber", idNumber)
                    .put("firstName", firstName)
                    .put("lastName", lastName)
                    .put("email", email)
                    .put("phone", phone)
                    .put("password", password)
                    .put("role",
                            foreignNational
                                    ? "FOREIGN_NATIONAL"
                                    : "CITIZEN");

            request("POST", "/api/register", body, null);

            return true;

        }, cb);
    }

    public static void signOut() {
        authGeneration.incrementAndGet();
        token = null;
        role = null;
    }

    // APPLICATIONS

    public static void submitApplication(
            final String serviceId,
            final Map<String, String> formData,
            Callback<Integer> cb) {

        final String requestToken = token;
        run(() -> {
            JSONObject body = new JSONObject()
                    .put("serviceId", serviceId)
                    .put("formData", new JSONObject(formData));

            return new JSONObject(
                    request("POST", "/api/applications", body, requestToken)
            ).getInt("applicationId");

        }, cb);
    }

    public static void latestApplication(
            Callback<AppInfo> cb) {

        final String requestToken = token;
        run(() -> {
            JSONArray arr = new JSONArray(
                    request("GET", "/api/applications/mine", null, requestToken)
            );

            if (arr.length() == 0) return null;

            JSONObject o = arr.getJSONObject(0);

            AppInfo a = new AppInfo();
            a.id = o.getInt("application_id");
            a.service = o.getString("service_name");
            a.status = o.getString("status");
            a.stage = o.optString("current_stage");

            return a;

        }, cb);
    }

    // PAYMENTS

    public static void pay(
            final int applicationId,
            Callback<Double> cb) {

        final String requestToken = token;
        run(() -> {
            JSONObject body = new JSONObject()
                    .put("applicationId", applicationId)
                    .put("method", "CARD");

            JSONObject json = new JSONObject(
                    request("POST", "/api/payments", body, requestToken)
            );

            if (json.optBoolean("paid")) {
                return json.getDouble("amount");
            }

            return null;

        }, cb);
    }

    // APPOINTMENTS

    public static void requestAppointment(
            final String office,
            final String date,
            final String purpose,
            Callback<Boolean> cb) {

        final String requestToken = token;
        run(() -> {
            JSONObject body = new JSONObject()
                    .put("office", office)
                    .put("date", date)
                    .put("purpose", purpose);

            request(
                    "POST",
                    "/api/appointment-requests",
                    body,
                    requestToken
            );

            return true;

        }, cb);
    }

    public static void latestAppointment(
            Callback<ApptInfo> cb) {

        final String requestToken = token;
        run(() -> {
            JSONArray arr = new JSONArray(
                    request("GET", "/api/appointments/mine", null, requestToken)
            );

            if (arr.length() == 0) return null;

            JSONObject o = arr.getJSONObject(arr.length() - 1);

            ApptInfo a = new ApptInfo();

            String dt = o.optString("appointment_datetime");

            a.date = dt.length() >= 10
                    ? dt.substring(0, 10)
                    : dt;

            a.service = o.optString("service_name");
            a.branch = o.optString("branch_name");
            a.status = o.optString("status");

            return a;

        }, cb);
    }

    // STAFF

    public static void staffApplications(
            Callback<List<StaffApp>> cb) {

        final String requestToken = token;
        run(() -> {
            JSONArray arr = new JSONArray(
                    request("GET", "/api/staff/applications", null, requestToken)
            );

            List<StaffApp> list = new ArrayList<>();

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);

                StaffApp s = new StaffApp();

                s.id = o.getInt("application_id");
                s.service = o.getString("service_name");

                s.applicant =
                        o.getString("first_name") + " " +
                                o.getString("last_name");

                s.status = o.getString("status");
                s.stage = o.optString("current_stage");

                list.add(s);
            }

            return list;

        }, cb);
    }

    public static void advance(
            final int applicationId,
            Callback<Boolean> cb) {

        final String requestToken = token;
        run(() -> {
            request(
                    "POST",
                    "/api/staff/applications/" +
                            applicationId + "/advance",
                    null,
                    requestToken
            );

            return true;

        }, cb);
    }
}
