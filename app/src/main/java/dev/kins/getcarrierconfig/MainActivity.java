package dev.kins.getcarrierconfig;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.CarrierConfigManager;
import android.os.PersistableBundle;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.util.Log;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

import java.lang.reflect.Field;
import java.util.Objects;


public class MainActivity extends AppCompatActivity {

    static final int PHONE_PERMISSION_CODE = 100;
    static final int PHONE_PERMISSION_SHARE_CODE = 101;
    private TextView editText = null;

    private String QuoteString(Object o)
    {
        return String.format("\"%s\"",
                o.toString().replaceAll("\"", "\\\\\""));
    }

    private StringBuilder GetDeviceName() {
        StringBuilder result;
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
            result = new StringBuilder(Capitalize(model));
        } else {
            result = new StringBuilder(Capitalize(manufacturer));
            result.append(" ");
            result.append(Capitalize(model));
        }
        return result;
    }

    private String Capitalize(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }

    private String GetIntArray(int[] arr, String indent)
    {
        StringBuilder value= new StringBuilder();
        String itemIndent=getString(R.string.indent);
        String prefix= String.format("\n%s", indent);
        String suffix="";
        value.append("[");
        for (int i : arr) {
            value.append(prefix);
            value.append(itemIndent);
            value.append(i);
            prefix = String.format(",\n%s", indent);
            suffix = String.format("\n%s", indent);
        }
        return value.append(suffix).append("]").toString();
    }

    private String GetArray(Object[] arr, String indent)
    {
        StringBuilder value= new StringBuilder();
        String itemIndent = getString(R.string.indent);
        String prefix= String.format("\n%s", indent);
        String suffix="";
        value.append("[");
        for (Object o : arr) {
            value.append(prefix);
            value.append(itemIndent);
            value.append(MakeJsonString(o, String.format("%s%s", indent, itemIndent)));
            prefix = String.format(",\n%s", indent);
            suffix = String.format("\n%s", indent);
        }
        return value.append(suffix).append("]").toString();
    }

    /**
     * @param o value to add to the JSON
     * @param indent How far to indent this item
     * @return Properly escaped json value for the type
     */
    String MakeJsonString(Object o, String indent) {
        if(null == o) return "null";
        if (o.getClass().isArray()) {
            if (o instanceof Object[]) return GetArray((Object[]) o, indent);
            if (o instanceof int[]) return GetIntArray((int[]) o, indent);
            Log.v(getString(R.string.TAG), String.format("Unhandled type: %s", o.getClass()));
        }
        if ((o instanceof Boolean) ||
                    (o instanceof Long) ||
                    (o instanceof Integer) ||
                    (o instanceof Double) ||
                    (o instanceof Float)) {
            return o.toString();
        }
        return QuoteString(o);
    }

    // Function to check and request permission
    public void CheckPermission(String permission, int requestCode)
    {
        // Checking if permission is not granted
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[] { permission }, requestCode);
        }else{
            GatherData(requestCode == PHONE_PERMISSION_SHARE_CODE);
        }
    }

    // This function is called when the user accepts or decline the permission.
    // Request Code is used to check which permission called this function.
    // This request code is provided when the user is prompt for permission.

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,
                permissions,
                grantResults);
        if (requestCode == PHONE_PERMISSION_CODE || requestCode == PHONE_PERMISSION_SHARE_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                GatherData(requestCode == PHONE_PERMISSION_SHARE_CODE);
            } else {
                ShowMissingPermissionsNotice();
            }
        }
    }

    public void OnClick2(View view)
    {
        CheckPermission(Manifest.permission.READ_PHONE_STATE, PHONE_PERMISSION_CODE);
    }

    public void OnClick(View view)
    {
        CheckPermission(Manifest.permission.READ_PHONE_STATE, PHONE_PERMISSION_SHARE_CODE);
    }

    private void GatherData(boolean share) {
        try{
            String json_data = GenerateJSON().toString();
            View v = findViewById(android.R.id.content);
            editText.setText(json_data);

            LogJSON(json_data);
            if(share) {
                ShareJSON(json_data);
            }
        }catch (Exception ex)
        {
            ShowMissingPermissionsNotice();
        }
    }

    private String GetCarrierName()
    {
        Context context = getApplicationContext();
        TelephonyManager telephonyManager = ((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE));
        return telephonyManager.getNetworkOperatorName();
    }

    @NonNull
    private StringBuilder GenerateJSON() {
        String indent = getString(R.string.indent);
        StringBuilder json= new StringBuilder();
        Context context = getApplicationContext();
        Class<? extends CarrierConfigManager> ccm_cls = CarrierConfigManager.class;
        Class<?>[] nested_classes = ccm_cls.getDeclaredClasses();
        CarrierConfigManager ccm = context.getSystemService(ccm_cls);
        PersistableBundle carrierConfig = ccm.getConfigForSubId(1);
        json.append("{\n");
        json.append(indent);
        json.append(QuoteString("Carrier"));
        json.append(": ");
        json.append(QuoteString(GetCarrierName()));
        json.append(",\n");
        json.append(indent);
        json.append(QuoteString("Device"));
        json.append(": ");
        json.append(QuoteString(GetDeviceName().toString()));

        json.append(GetClassFieldData(ccm_cls, ccm, carrierConfig, indent));
        for (Class<?> current_class : nested_classes) {
            json.append(GetClassFieldData(current_class, ccm, carrierConfig, indent));
        }
        json.append("\n}");
        return json;
    }

    private StringBuilder GetClassFieldData(Class<?> current_class, CarrierConfigManager ccm, PersistableBundle carrierConfig, String indent) {
        StringBuilder json = new StringBuilder();
        String prefix=",\n" + indent;
        Field[] fields = current_class.getFields();
        for (Field field : fields) {
            String name = field.getName();
            // Only look at string fields
            if (field.getType().isAssignableFrom(String.class)) {
                try {
                    String field_value = Objects.requireNonNull(field.get(ccm)).toString();
                    Object value = carrierConfig.get(field_value);
                    // Ignore null values
                    if(value == null)
                    {
                        continue;
                    }
                    String element = String.format("%s%s: %s",
                            prefix,
                            QuoteString(name),
                            MakeJsonString(value, indent));
                    json.append(element);
                } catch (Exception ex) {
                    Log.v(getString(R.string.TAG), "Couldn't get data for: " + name);
                    Log.v(getString(R.string.TAG), ex.getMessage());
                    Log.v(getString(R.string.TAG), Log.getStackTraceString(ex));
                }
            }
        }
        return json;
    }

    private void LogJSON(String data) {
        Log.v(getString(R.string.TAG), data);
    }

    private void ShareJSON(String data) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, data);
        sendIntent.setType("text/plain");

        Intent shareIntent = Intent.createChooser(sendIntent, "Carrier Config Data");
        startActivity(shareIntent);
    }

    private void ShowMissingPermissionsNotice() {
        Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content),
                                            R.string.PermWarning,
                                            Snackbar.LENGTH_LONG);
        snackbar.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        editText = findViewById(R.id.editTextTextMultiLine);
    }
}