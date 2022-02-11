package dev.kins.getcarrierconfig;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telephony.CarrierConfigManager;
import android.os.PersistableBundle;
import android.util.Log;
import android.os.Bundle;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

import java.lang.reflect.Field;
import java.util.Objects;


public class MainActivity extends AppCompatActivity {

    private String EscapeQuotes(String str)
    {
        return "\"" + str.replaceAll("\"", "\\\\\"") + "\"";
    }

    public String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
            return Capitalize(model);
        } else {
            return Capitalize(manufacturer) + " " + model;
        }
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

    String GetArray(Object[] arr)
    {
        StringBuilder value= new StringBuilder();
        String prefix="[";
        for (Object o : arr) {
            value.append(prefix).append(MakeJsonString(o));
            prefix = ", ";
        }
        return value.append("]").toString();
    }

    /**
     * @param o value to add to the JSON
     * @return Properly escaped json value for the type
     */
    String MakeJsonString(@NonNull Object o) {
        String result;
        Log.v("GetCarrierConfig", o.toString());
        if (o.getClass().isArray()) {
            result = GetArray((Object[]) o);
        } else if (o instanceof String) {
            result = EscapeQuotes(o.toString());
        } else if (o instanceof Boolean) {
            result = o.toString();
        } else if (o instanceof Long) {
            result = o.toString();
        } else if (o instanceof Integer) {
            result = o.toString();
        } else if (o instanceof Double) {
            result = o.toString();
        } else if (o instanceof Float) {
            result = o.toString();
        } else {
            result = EscapeQuotes(o.toString());
        }
        return result;
    }

    public void OnClick(View view)
    {
        try{
            String json_data = GenerateJSON();
            LogJSON(json_data);
            ShareJSON(json_data);
        }catch (Exception ex)
        {
            ShowMissingPermissionsNotice();
        }
    }

    @NonNull
    private String GenerateJSON() {
        StringBuilder json= new StringBuilder();
        Context context = getApplicationContext();
        CarrierConfigManager ccm = context.getSystemService(CarrierConfigManager.class);
        Class<? extends CarrierConfigManager> ccm_cls = ccm.getClass();
        PersistableBundle carrierConfig = ccm.getConfigForSubId(1);
        String prefix=",\n  ";
        Field[] fields = ccm_cls.getFields();
        Class<?> strType = String.class;
        json.append("{\n  \"Device\": \"");
        json.append(getDeviceName());
        json.append("\"");
        for (Field field : fields) {
            // Only look at string fields
            if (field.getType().isAssignableFrom(strType)) {
                String name = field.getName();
                try {
                    String field_value = Objects.requireNonNull(field.get(ccm)).toString();
                    Log.v("GetCarrierConfig", field_value);
                    Object value = carrierConfig.get(field_value);
                    String element = prefix +
                            "\"" +
                            name +
                            "\" : " +
                            MakeJsonString(value);
                    json.append(element);
                } catch (Exception ex) {
                    Log.v("GetCarrierConfig", "Couldn't get data for: " + name);
                    Log.v("GetCarrierConfig", ex.getMessage());
                    Log.v("GetCarrierConfig", Log.getStackTraceString(ex));
                }
            }
        }
        return json + "\n}";
    }

    private void LogJSON(String data) {
        Log.v("GetCarrierConfig", data);
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

    }
}