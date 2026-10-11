package com.gerard.bloqueodns;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserManager;
import android.text.InputType;
import android.util.Base64;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Fija el DNS privado del sistema (NextDNS) y bloquea los ajustes que permiten quitarlo.
 * Solo se puede desbloquear con la contraseña que se puso al bloquear.
 */
public class MainActivity extends Activity {

    /** Restricciones que se activan al bloquear. */
    private static final String[] RESTRICTIONS = {
            UserManager.DISALLOW_CONFIG_PRIVATE_DNS,   // no se puede cambiar el DNS privado
            UserManager.DISALLOW_CONFIG_VPN,           // no se pueden usar VPNs para saltarse el filtro
            UserManager.DISALLOW_DEBUGGING_FEATURES,   // sin opciones de desarrollador ni ADB
            UserManager.DISALLOW_ADD_USER              // no se pueden crear otros usuarios
    };
    private static final String CHROME = "com.android.chrome";

    private DevicePolicyManager dpm;
    private ComponentName admin;
    private SharedPreferences prefs;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        admin = new ComponentName(this, AdminReceiver.class);
        prefs = getSharedPreferences("cfg", MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        root.setPadding(p, p, p, p);
        scroll.addView(root);
        setContentView(scroll);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    // ---------- Pantallas ----------

    private void render() {
        root.removeAllViews();
        title("Bloqueo DNS");

        if (!dpm.isDeviceOwnerApp(getPackageName())) {
            text("La app todavía NO está activada.\n\n"
                    + "Conecta el móvil al ordenador y ejecuta:\n\n"
                    + "adb shell dpm set-device-owner " + getPackageName() + "/.AdminReceiver");
            return;
        }

        if (isLocked()) {
            renderLocked();
        } else {
            renderUnlocked();
        }
    }

    private void renderUnlocked() {
        text("Estado: DESBLOQUEADO");

        final EditText host = edit("Tu DNS de NextDNS (ej: abc123.dns.nextdns.io)", false);
        host.setText(prefs.getString("host", ""));
        final EditText pw1 = edit("Contraseña de desbloqueo (mín. 8 caracteres)", true);
        final EditText pw2 = edit("Repite la contraseña", true);
        final TextView status = text("");

        final Button lock = button("BLOQUEAR");
        lock.setOnClickListener(v -> {
            final String h = host.getText().toString().trim().toLowerCase();
            String a = pw1.getText().toString();
            String b = pw2.getText().toString();
            if (h.isEmpty() || !h.contains(".")) { toast("Escribe tu dirección de NextDNS"); return; }
            if (a.length() < 8) { toast("La contraseña debe tener al menos 8 caracteres"); return; }
            if (!a.equals(b)) { toast("Las contraseñas no coinciden"); return; }

            final String password = a;
            lock.setEnabled(false);
            status.setText("Comprobando el DNS... (necesitas conexión a internet)");

            // Esta llamada hace una comprobación de red: no puede ir en el hilo principal.
            new Thread(() -> {
                int result;
                String error = null;
                if (isConfigured(h)) {
                    // Ya está puesto (por ejemplo, a mano en Ajustes): no hace falta comprobarlo.
                    result = DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR;
                } else {
                    try {
                        result = dpm.setGlobalPrivateDnsModeSpecifiedHost(admin, h);
                    } catch (Exception e) {
                        result = -1;
                        error = e.getMessage();
                    }
                    // Algunos móviles fallan en la comprobación aunque el DNS funcione.
                    if (result != DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR && isConfigured(h)) {
                        result = DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR;
                    }
                }
                final int r = result;
                final String err = error;
                runOnUiThread(() -> {
                    if (r != DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR) {
                        status.setText("No se pudo poner ese DNS (código " + r + ")"
                                + (err != null ? ": " + err : "")
                                + ".\n\nSolución: ponlo a mano en Ajustes → Conexiones → "
                                + "Más ajustes de conexión → DNS privado, con esta misma "
                                + "dirección, y vuelve a pulsar BLOQUEAR.");
                        lock.setEnabled(true);
                        return;
                    }
                    savePassword(password);
                    prefs.edit().putString("host", h).apply();
                    applyRestrictions();
                    toast("Bloqueado");
                    render();
                });
            }).start();
        });

        space();
        text("Para quitar la app del todo (solo funciona estando desbloqueada):");
        Button remove = button("Desactivar y desinstalar la app");
        remove.setOnClickListener(v -> {
            dpm.clearDeviceOwnerApp(getPackageName());
            startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName())));
        });
    }

    private void renderLocked() {
        String host = dpm.getGlobalPrivateDnsHost(admin);
        text("Estado: BLOQUEADO\n\nDNS fijado: " + (host != null ? host : "(ninguno)")
                + "\n\nNo se puede cambiar el DNS privado, usar VPNs, "
                + "entrar en opciones de desarrollador ni crear otros usuarios.");

        space();
        final EditText pw = edit("Contraseña para desbloquear", true);
        final Button unlock = button("DESBLOQUEAR");
        unlock.setOnClickListener(v -> {
            if (checkPassword(pw.getText().toString())) {
                removeRestrictions();
                prefs.edit().remove("hash").remove("salt").apply();
                toast("Desbloqueado");
                render();
            } else {
                pw.setText("");
                toast("Contraseña incorrecta. Espera 30 segundos.");
                unlock.setEnabled(false);
                root.postDelayed(() -> unlock.setEnabled(true), 30_000);
            }
        });
    }

    // ---------- Bloqueo ----------

    /** ¿El DNS privado del sistema ya está puesto con este host? */
    private boolean isConfigured(String host) {
        try {
            return dpm.getGlobalPrivateDnsMode(admin) == DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME
                    && host.equalsIgnoreCase(dpm.getGlobalPrivateDnsHost(admin));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isLocked() {
        Bundle r = dpm.getUserRestrictions(admin);
        return r.getBoolean(UserManager.DISALLOW_CONFIG_PRIVATE_DNS, false);
    }

    private void applyRestrictions() {
        for (String r : RESTRICTIONS) dpm.addUserRestriction(admin, r);

        // Chrome: sin "DNS seguro" propio (que se saltaría NextDNS) y sin modo incógnito.
        Bundle chrome = new Bundle();
        chrome.putString("DnsOverHttpsMode", "off");
        chrome.putInt("IncognitoModeAvailability", 1);
        dpm.setApplicationRestrictions(admin, CHROME, chrome);
    }

    private void removeRestrictions() {
        for (String r : RESTRICTIONS) dpm.clearUserRestriction(admin, r);
        dpm.setApplicationRestrictions(admin, CHROME, new Bundle());
    }

    // ---------- Contraseña (solo se guarda su hash) ----------

    private void savePassword(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        prefs.edit()
                .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString("hash", hash(salt, password))
                .apply();
    }

    private boolean checkPassword(String password) {
        String s = prefs.getString("salt", null);
        String h = prefs.getString("hash", null);
        if (s == null || h == null) return false;
        byte[] salt = Base64.decode(s, Base64.NO_WRAP);
        return MessageDigest.isEqual(
                h.getBytes(StandardCharsets.UTF_8),
                hash(salt, password).getBytes(StandardCharsets.UTF_8));
    }

    private static String hash(byte[] salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = password.getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 50_000; i++) {
                md.reset();
                md.update(salt);
                md.update(out);
                out = md.digest();
            }
            return Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- Interfaz ----------

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(24);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, dp(16));
        root.addView(t);
    }

    private TextView text(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(16);
        t.setTextIsSelectable(true);
        t.setPadding(0, dp(4), 0, dp(4));
        root.addView(t);
        return t;
    }

    private EditText edit(String hint, boolean password) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(password
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(e);
        return e;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        root.addView(b);
        return b;
    }

    private void space() {
        TextView t = new TextView(this);
        t.setHeight(dp(16));
        root.addView(t);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
