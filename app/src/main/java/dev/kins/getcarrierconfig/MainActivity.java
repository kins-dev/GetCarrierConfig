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

    private String QuoteString(String str)
    {
        return "\"" + str.replaceAll("\"", "\\\\\"") + "\"";
    }

    private String GetDeviceName() {
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
    private <T>String GetIntArray(int[] arr, String indent)
    {
        StringBuilder value= new StringBuilder();
        String itemIndent="    ";
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
        String itemIndent="    ";
        String prefix= String.format("\n%s", indent);
        String suffix="";
        value.append("[");
        for (Object o : arr) {
            value.append(prefix);
            value.append(itemIndent);
            value.append(MakeJsonString(o, indent + itemIndent));
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
        String result="";
        if (o.getClass().isArray()) {
            if(o instanceof Object[]) {
                result = GetArray((Object[]) o, indent);
            }else if (o instanceof int[]){
                result = GetIntArray((int[]) o, indent);
            }else{
                Log.v(getString(R.string.TAG), "Unhandled type: " + o.getClass());
            }
        } else if (o instanceof String) {
            result = QuoteString(o.toString());
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
            result = QuoteString(o.toString());
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
        String indent = "    ";
        StringBuilder json= new StringBuilder();
        Context context = getApplicationContext();
        Class<? extends CarrierConfigManager> ccm_cls = CarrierConfigManager.class;
        Class<?>[] nested_classes = ccm_cls.getDeclaredClasses();
        CarrierConfigManager ccm = context.getSystemService(ccm_cls);
        PersistableBundle carrierConfig = ccm.getConfigForSubId(1);
        json.append("{\n");
        json.append(indent);
        json.append("\"Device\": \"");
        json.append(GetDeviceName());
        json.append("\"");

        json.append(GetClassFieldData(ccm_cls, ccm, carrierConfig, indent));
        for (Class<?> current_class : nested_classes) {
            json.append(GetClassFieldData(current_class, ccm, carrierConfig, indent));
        }
        return json + "\n}";
    }

    private String GetClassFieldData(Class<?> current_class, CarrierConfigManager ccm, PersistableBundle carrierConfig, String indent) {
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
                    String element = prefix +
                            "\"" +
                            name +
                            "\": " +
                            MakeJsonString(value, indent);
                    json.append(element);
                } catch (Exception ex) {
                    Log.v(getString(R.string.TAG), "Couldn't get data for: " + name);
                    Log.v(getString(R.string.TAG), ex.getMessage());
                    Log.v(getString(R.string.TAG), Log.getStackTraceString(ex));
                }
            }
        }
        return json.toString();
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

    }
}