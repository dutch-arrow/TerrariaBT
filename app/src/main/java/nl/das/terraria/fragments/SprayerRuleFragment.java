package nl.das.terraria.fragments;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;

import nl.das.terraria.R;
import nl.das.terraria.RequestQueueSingleton;
import nl.das.terraria.VoidRequest;
import nl.das.terraria.dialogs.NotificationDialog;
import nl.das.terraria.dialogs.WaitSpinner;
import nl.das.terraria.json.Action;
import nl.das.terraria.json.SprayerRule;

public class SprayerRuleFragment extends Fragment {
    // Drying rule
    private Button btnSaveDR;
    private Button btnRefreshDR;
    private EditText edtDelay;
    private EditText edtFanInPeriod;
    private EditText edtFanOutPeriod;

    private InputMethodManager imm;
    private WaitSpinner wait;

    private String curIPAddress;
    private SprayerRule dryingRule;
    private int tabnr;

    public SprayerRuleFragment() {
    }

    public static SprayerRuleFragment newInstance(int tabnr) {
        SprayerRuleFragment fragment = new SprayerRuleFragment();
        Bundle args = new Bundle();
        args.putInt("tabnr", tabnr);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            tabnr = getArguments().getInt("tabnr");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.drying_rule_frg, parent, false).getRootView();
        imm = (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);

        btnSaveDR = (Button) view.findViewById(R.id.dr_btnSave);
        btnSaveDR.setEnabled(false);
        btnSaveDR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnSaveDR.requestFocusFromTouch();
                Log.i("Terraria", "Save");
                saveDryingRule();
                btnSaveDR.setEnabled(false);
            }
        });
        btnRefreshDR = (Button) view.findViewById(R.id.dr_btnRefresh);
        btnRefreshDR.setEnabled(true);
        btnRefreshDR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("Terraria", "Refresh");
                getDryingRule();
                btnSaveDR.setEnabled(false);
            }
        });

        edtDelay = view.findViewById(R.id.dr_edtDelay);
        edtDelay.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String value = String.valueOf(edtDelay.getText()).trim();
                if (checkInteger(edtDelay, value, 0, 60)) {
                    dryingRule.setDelay(Integer.parseInt(value));
                    edtFanInPeriod.requestFocus();
                    btnSaveDR.setEnabled(true);
                    imm.hideSoftInputFromWindow(edtDelay.getWindowToken(), 0);
                }
            }
            return false;
        });
        edtDelay.setOnFocusChangeListener(((v, hasFocus) -> {
            if (!hasFocus) {
                String value = String.valueOf(edtDelay.getText()).trim();
                if (checkInteger(edtDelay, value, 0, 60)) {
                    dryingRule.setDelay(Integer.parseInt(value));
                    btnSaveDR.setEnabled(true);
                }
            }
        }));

        edtFanInPeriod = view.findViewById(R.id.dr_edtOnPeriodIn);
        edtFanInPeriod.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String value = String.valueOf(edtFanInPeriod.getText()).trim();
                if (checkInteger(edtFanInPeriod, value, 0, 60)) {
                    dryingRule.getActions().get(0).setDevice("fan_in");
                    dryingRule.getActions().get(0).setOnPeriod(Integer.parseInt(value) * 60);
                    edtFanOutPeriod.requestFocus();
                    btnSaveDR.setEnabled(true);
                    imm.hideSoftInputFromWindow(edtFanInPeriod.getWindowToken(), 0);
                }
            }
            return false;
        });
        edtFanInPeriod.setOnFocusChangeListener(((v, hasFocus) -> {
            if (!hasFocus) {
                String value = String.valueOf(edtFanInPeriod.getText()).trim();
                if (checkInteger(edtFanInPeriod, value, 0, 60)) {
                    dryingRule.getActions().get(0).setDevice("fan_in");
                    dryingRule.getActions().get(0).setOnPeriod(Integer.parseInt(value) * 60);
                    btnSaveDR.setEnabled(true);
                }
            }
        }));


        edtFanOutPeriod = view.findViewById(R.id.dr_edtOnPeriodOut);
        edtFanOutPeriod.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String value = String.valueOf(edtFanOutPeriod.getText()).trim();
                if (checkInteger(edtFanOutPeriod, value, 0, 60)) {
                    dryingRule.getActions().get(1).setDevice("fan_out");
                    dryingRule.getActions().get(1).setOnPeriod(Integer.parseInt(value) * 60);
                    btnSaveDR.setEnabled(true);
                    imm.hideSoftInputFromWindow(edtFanOutPeriod.getWindowToken(), 0);
                }
            }
            return false;
        });
        edtFanOutPeriod.setOnFocusChangeListener(((v, hasFocus) -> {
            if (!hasFocus) {
                String value = String.valueOf(edtFanOutPeriod.getText()).trim();
                if (checkInteger(edtFanOutPeriod, value, 0, 60)) {
                    dryingRule.getActions().get(1).setDevice("fan_out");
                    dryingRule.getActions().get(1).setOnPeriod(Integer.parseInt(value) * 60);
                    btnSaveDR.setEnabled(true);
                }
            }
        }));
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        curIPAddress = getContext().getSharedPreferences("TerrariaApp", 0).getString("terrarium" + tabnr + "_ip_address", "");
        getDryingRule();
    }

    private void getDryingRule() {
        wait = new WaitSpinner(requireContext());
        wait.start();
        String url = "http://" + curIPAddress + "/sprayerrule";
        Log.i("Terraria", "Execute GET request " + url);
        // Request sensor readings.
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                (Response.Listener<JSONObject>) response1 -> {
                    Gson gson = new Gson();
                    try {
                        dryingRule = gson.fromJson(response1.toString(), SprayerRule.class);
                        Log.i("Terraria", "Retrieved dryingrule");
                        updateDryingRule();
                        wait.dismiss();
                    } catch (JsonSyntaxException e) {
                        new NotificationDialog(requireContext(), "Error", "Drying rule response contains errors:\n" + e.getMessage()).show();
                    }
                },
                (Response.ErrorListener) error -> {
                    if (error.getMessage() == null) {
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        error.printStackTrace(pw);
                        Log.i("Terraria", "getDryingRule error:\n" + sw.toString());
                    } else {
                        Log.i("Terraria", "Error " + error.getMessage());
                        new NotificationDialog(requireContext(), "Error", "Kontakt met Control Unit verloren.").show();
                    }
                    wait.dismiss();
                }
        );
        // Add the request to the RequestQueue.
        RequestQueueSingleton.getInstance(requireContext()).add(jsonObjectRequest);
    }

    private void updateDryingRule() {
        int value = dryingRule.getDelay();
        edtDelay.setText(value + "");
        for (int i = 0; i < 4; i++) {
            Action a = dryingRule.getActions().get(i);
            Log.i("Terraria", "Drying rule action " + i + " device '" + a.getDevice() + "' period " + a.getOnPeriod());
            if (a.getDevice().equalsIgnoreCase("fan_in")) {
                edtFanInPeriod.setText(a.getOnPeriod() / 60 + "");
            } else if (a.getDevice().equalsIgnoreCase("fan_out")) {
                edtFanOutPeriod.setText(a.getOnPeriod() / 60 + "");
            }
        }
    }

    private void saveDryingRule() {
        wait = new WaitSpinner(requireContext());
        wait.start();
        String url = "http://" + curIPAddress + "/sprayerrule";
        Log.i("Terraria", "Execute PUT request " + url);
        dryingRule.getActions().get(2).setDevice("no device");
        dryingRule.getActions().get(2).setOnPeriod(0);
        dryingRule.getActions().get(3).setDevice("no device");
        dryingRule.getActions().get(3).setOnPeriod(0);
        Gson gson = new Gson();
        String json = gson.toJson(dryingRule);
        Log.i("Terraria", "JSON sent:");
        Log.i("Terraria", json);
        VoidRequest req = new VoidRequest(Request.Method.PUT, url, json,
                (Response.Listener<Void>) response -> {
                    Log.i("Terrarium", "Sprayer rule has been saved.");
                    wait.dismiss();
                },
                (Response.ErrorListener) error -> {
                    wait.dismiss();
                    Log.i("Terrarium", "Error " + error.getMessage());
                    new NotificationDialog(requireActivity(), "Error", "Kontakt met Control Unit verloren.").show();
                }
        );
        // Add the request to the RequestQueue.
        RequestQueueSingleton.getInstance(requireContext()).add(req);
    }

    private boolean checkInteger(EditText field, String value, int minValue, int maxValue) {
        if (value.trim().length() > 0) {
            try {
                int rv = Integer.parseInt(value);
                if (rv < minValue || rv > maxValue) {
                    field.setError("Waarde moet tussen " + minValue + " en " + maxValue + " zijn.");
                }
            } catch (NumberFormatException e) {
                field.setError("Waarde moet tussen " + minValue + " en " + maxValue + " zijn.");
            }
        }
        return field.getError() == null;
    }
}