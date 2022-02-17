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
import android.text.Html;
import android.util.Log;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;

import com.google.android.material.snackbar.Snackbar;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    static final int PHONE_PERMISSION_CODE = 100;
    static final int PHONE_PERMISSION_SHARE_CODE = 101;
    private TextView textView = null;
    private boolean hasPermissions = false;

    enum CODE_TYPE {
        type, keyword, literal, comment, string, punctuation, plain
    }

    private static String ElementColor(CODE_TYPE ct)
    {
        switch (ct) {
            case type:
                return "#87cef9";
            case keyword:
                return "#00ff00";
            case literal:
                return "#ffff00";
            case comment:
                return "#999999";
            case string:
                return "#ff4500";
            case punctuation:
                return "#eeeeee";
            case plain:
            default:
        }
        return "#ffffff";
    }


    private String HandleWhitespace(String s)
    {
        StringBuilder stringBuilder = new StringBuilder();
        String [] lines = s.split("\n");
        for (String line : lines) {
            stringBuilder.append("<p><tt>");
            stringBuilder.append(line.replaceAll(getString(R.string.indent), getString(R.string.html_indent)));
            stringBuilder.append("</tt></p>");
        }
        return stringBuilder.toString();
    }

    private String RemoveAllSpans(String s)
    {
        return s.replaceAll("<span style=\"color:#[0-9a-f]{6}\">(.*?)</span>", "$1");
    }

    private String AddSpan(String s, CODE_TYPE c)
    {
        return String.format("<span style=\"color:%s\">%s</span>", ElementColor(c), s);
    }

    /**
     * @param o Object to turn into a string
     * @return String of the object, replacing " with \" and newlines with \n
     */
    private String QuoteString(Object o)
    {
        return AddSpan(String.format("\"%s\"",
                o.toString().replaceAll("\"", "\\\\\"")).replaceAll("\n", "\\\\n"),
                CODE_TYPE.string);
    }

    /**
     * @return String with the consumer name of a device
     */
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

    /**
     * @param s String to capitalize
     * @return String with the first letter in each word in upper case
     */
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

    /**
     * @param arr Integer array to add to the JSON
     * @param indent How far we should be indenting
     * @return String of JSON that represents this int array
     */
    private String GetIntArray(int[] arr, String indent)
    {
        StringBuilder value= new StringBuilder();
        String itemIndent=getString(R.string.indent);
        String prefix= String.format("\n%s", indent);
        String suffix="";
        value.append(AddSpan("[", CODE_TYPE.type));
        for (int i : arr) {
            value.append(prefix);
            value.append(itemIndent);
            value.append(AddSpan(String.format(Locale.US, "%d",i), CODE_TYPE.literal));
            prefix = AddSpan(",", CODE_TYPE.punctuation) + "\n" +indent;
            suffix = String.format("\n%s", indent);
        }
        return value.append(suffix).append(AddSpan("]", CODE_TYPE.type)).toString();
    }
    /**
     * @param arr Object array to add to the JSON
     * @param indent How far we should be indenting
     * @return String of JSON that represents this object array
     */
    private String GetArray(Object[] arr, String indent)
    {
        StringBuilder value= new StringBuilder();
        String itemIndent = getString(R.string.indent);
        String prefix= String.format("\n%s", indent);
        String suffix="";
        value.append(AddSpan("[", CODE_TYPE.type));
        for (Object o : arr) {
            value.append(prefix);
            value.append(itemIndent);
            value.append(MakeJsonString(o, String.format("%s%s", indent, itemIndent)));
            prefix = AddSpan(",", CODE_TYPE.punctuation) + "\n" +indent;
            suffix = String.format("\n%s", indent);
        }
        return value.append(suffix).append(AddSpan("]", CODE_TYPE.type)).toString();
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
        if (o instanceof Boolean)
            return AddSpan(o.toString(), CODE_TYPE.keyword);
        if((o instanceof Long) ||
            (o instanceof Integer) ||
            (o instanceof Double) ||
            (o instanceof Float)) {
            return AddSpan(o.toString(), CODE_TYPE.literal);
        }
        return QuoteString(o);
    }

    /** Checks to see if the user needs permissions and requests them if needed
     * @param permission Permission to check
     * @param requestCode Code used to signal if this is a share request or a view request
     */
    public void CheckPermission(String permission, int requestCode)
    {
        Button b1 = findViewById(R.id.button);
        Button b2 = findViewById(R.id.button2);
        b1.setEnabled(false);
        b2.setEnabled(false);
        // Checking if permission is not granted
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[] { permission }, requestCode);
        }else{
            GatherData(requestCode == PHONE_PERMISSION_SHARE_CODE);
        }
    }

    /** If the user needed to accept permissions, process the result
     * @param requestCode Code used to signal if this is a share request or a view request
     * @param permissions Permissions requested
     * @param grantResults Did the user say yes or no
     */
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
                Button b1 = findViewById(R.id.button);
                Button b2 = findViewById(R.id.button2);
                b1.setEnabled(true);
                b2.setEnabled(true);
            }
        }
    }

    /** Click Callback
     * @param view Called when the user clicks the Show JSON button
     */
    public void OnClick_View(View view)
    {
        CheckPermission(Manifest.permission.READ_PHONE_STATE, PHONE_PERMISSION_CODE);
    }

    /** Click Callback
     * @param view Called when the user clicks the Share JSON button
     */
    public void OnClick_Share(View view)
    {
        CheckPermission(Manifest.permission.READ_PHONE_STATE, PHONE_PERMISSION_SHARE_CODE);
    }

    /** Pulls date from the carrier config
     * @param share Does the user want to share this data
     */
    private void GatherData(boolean share) {
        hasPermissions = true;
        HideTextView();
        try{
            String data = GenerateJSON().toString();
            String plainJSON = RemoveAllSpans(data);
            String html = HandleWhitespace(data);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                textView.setText(Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT));
            else
                textView.setText(Html.fromHtml(html));
            LogData(html);
            if(share) {
                ShareJSON(plainJSON);
            }
        }catch (Exception ex) {
            ShowMissingPermissionsNotice();
        }finally {
            Button b1 = findViewById(R.id.button);
            Button b2 = findViewById(R.id.button2);
            b1.setEnabled(true);
            b2.setEnabled(true);
        }
    }

    private void HideTextView() {
        TextView tv = findViewById(R.id.textView);
        tv.setVisibility(View.GONE);
        tv.setText("");
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
        json.append(AddSpan("{", CODE_TYPE.type));
        json.append("\n");
        json.append(indent);
        json.append(QuoteString("Carrier"));
        json.append(AddSpan(": ", CODE_TYPE.punctuation));
        json.append(QuoteString(GetCarrierName()));
        json.append(AddSpan(",", CODE_TYPE.punctuation));
        json.append("\n");
        json.append(indent);
        json.append(QuoteString("Device"));
        json.append(AddSpan(": ", CODE_TYPE.punctuation));
        json.append(QuoteString(GetDeviceName().toString()));

        json.append(GetClassFieldData(ccm_cls, ccm, carrierConfig, indent));
        for (Class<?> current_class : nested_classes) {
            json.append(GetClassFieldData(current_class, ccm, carrierConfig, indent));
        }
        json.append("\n");
        json.append(AddSpan("}", CODE_TYPE.type));

        return json;
    }

    private StringBuilder GetClassFieldData(Class<?> current_class, CarrierConfigManager ccm, PersistableBundle carrierConfig, String indent) {
        StringBuilder json = new StringBuilder();
        String prefix= AddSpan(",", CODE_TYPE.punctuation) + "\n" + indent;
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
                    String element = prefix + QuoteString(name) +
                            AddSpan(": ", CODE_TYPE.punctuation) +
                            MakeJsonString(value, indent);
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

    private void LogData(String data) {
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

    protected void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean("hasPermissions", hasPermissions);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textMultiLine);
        if(savedInstanceState != null)
        {
            if(savedInstanceState.getBoolean("hasPermissions", false)){
                hasPermissions = true;
                HideTextView();
            }
        }
    }
}
