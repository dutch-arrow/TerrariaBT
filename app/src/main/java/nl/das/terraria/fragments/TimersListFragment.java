package nl.das.terraria.fragments;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import nl.das.terraria.R;
import nl.das.terraria.TerrariaApp;
import nl.das.terraria.Utils;
import nl.das.terraria.dialogs.WaitSpinner;
import nl.das.terraria.json.Timer;

public class TimersListFragment extends Fragment {

    private String deviceID;
    private int tabnr;
    private String curIPAddress;
    private Button btnSave;
    private Button btnRefresh;
    private final EditText[] edtTimeOn = new EditText[5];
    private final EditText[] edtTimeOff = new EditText[5];
    private final EditText[] edtRepeat = new EditText[5];
    private final EditText[] edtPeriod = new EditText[5];


    public static final Map<String, Timer[]> timers = new HashMap<>();
    private WaitSpinner wait;
    private InputMethodManager imm;

    public TimersListFragment() { }

    public static TimersListFragment newInstance(int tabnr, String device) {
        TimersListFragment fragment = new TimersListFragment();
        Bundle args = new Bundle();
        args.putString("deviceID", device);
        args.putInt("tabnr", tabnr);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        deviceID = requireArguments().getString("deviceID");
        tabnr = getArguments().getInt("tabnr");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.five_timers_frg, container, false);
        imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        curIPAddress = requireContext().getSharedPreferences("TerrariaApp", 0).getString("terrarium" + tabnr + "_ip_address", "");
        btnSave = view.findViewById(R.id.ti_btnSave);
        btnSave.setEnabled(false);
        btnSave.setOnClickListener(v -> {
            btnSave.requestFocusFromTouch();
            saveTimers();
            imm.hideSoftInputFromWindow(btnSave.getWindowToken(), 0);
            btnSave.setEnabled(false);
        });
        btnRefresh = view.findViewById(R.id.ti_btnRefresh);
        btnRefresh.setEnabled(true);
        btnRefresh.setOnClickListener(v -> {
            getTimers();
            imm.hideSoftInputFromWindow(btnRefresh.getWindowToken(), 0);
            btnSave.setEnabled(false);
        });
        int resId;
        for (int i = 0; i < 5; i++) {
            int nr = i;
            resId = getResources().getIdentifier("it_edtTimeOn_" + (i + 1), "id", getContext().getPackageName());
            edtTimeOn[i] = view.findViewById(resId);
            edtTimeOn[i].setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    imm.hideSoftInputFromWindow(edtTimeOn[nr].getWindowToken(), 0);
                    if (checkTime(edtTimeOn[nr])) {
                        String value = String.valueOf(edtTimeOn[nr].getText()).trim();
                        int hr = Utils.getH(value);
                        int min = Utils.getM(value);
                        Objects.requireNonNull(timers.get(deviceID))[nr].setHourOn(hr);
                        Objects.requireNonNull(timers.get(deviceID))[nr].setMinuteOn(min);
                        edtTimeOff[nr].requestFocus();
                        btnSave.setEnabled(true);
                    }
                }
                return false;
            });
            edtTimeOn[i].setOnFocusChangeListener(((v, hasFocus) -> {
                if (!hasFocus) {
                    if (checkTime(edtTimeOn[nr])) {
                        String value = String.valueOf(edtTimeOn[nr].getText()).trim();
                        int hr = Utils.getH(value);
                        int min = Utils.getM(value);
                        Objects.requireNonNull(timers.get(deviceID))[nr].setHourOn(hr);
                        Objects.requireNonNull(timers.get(deviceID))[nr].setMinuteOn(min);
                        btnSave.setEnabled(true);
                    }
                }
            }));
            resId = getResources().getIdentifier("it_edtTimeOff_" + (i + 1), "id", getContext().getPackageName());
            edtTimeOff[i] = view.findViewById(resId);
            edtTimeOff[i].setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    imm.hideSoftInputFromWindow(edtTimeOff[nr].getWindowToken(), 0);
                    if (checkTime(edtTimeOff[nr])) {
                        String value = String.valueOf(edtTimeOff[nr].getText()).trim();
                        int hr = Utils.getH(value);
                        int min = Utils.getM(value);
                        Objects.requireNonNull(timers.get(deviceID))[nr].setHourOff(hr);
                        Objects.requireNonNull(timers.get(deviceID))[nr].setMinuteOff(min);
                        if (value.length() > 0) {
                            edtPeriod[nr].setText("0");
                            Objects.requireNonNull(TimersListFragment.timers.get(deviceID))[nr].setPeriod(0);
                        }
                        edtRepeat[nr].requestFocus();
                        btnSave.setEnabled(true);
                    }
                }
                return false;
            });
            edtTimeOff[i].setOnFocusChangeListener(((v, hasFocus) -> {
                if (!hasFocus) {
                    if (checkTime(edtTimeOff[nr])) {
                        String value = String.valueOf(edtTimeOff[nr].getText()).trim();
                        int hr = Utils.getH(value);
                        int min = Utils.getM(value);
                        Objects.requireNonNull(timers.get(deviceID))[nr].setHourOff(hr);
                        Objects.requireNonNull(timers.get(deviceID))[nr].setMinuteOff(min);
                        if (value.length() > 0) {
                            edtPeriod[nr].setText("0");
                            Objects.requireNonNull(TimersListFragment.timers.get(deviceID))[nr].setPeriod(0);
                        }
                        btnSave.setEnabled(true);
                    }
                }
            }));
            resId = getResources().getIdentifier("it_edtRepeat_" + (i + 1), "id", getContext().getPackageName());
            edtRepeat[i] = view.findViewById(resId);
            edtRepeat[i].setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    imm.hideSoftInputFromWindow(edtRepeat[nr].getWindowToken(), 0);
                    String value = String.valueOf(edtRepeat[nr].getText()).trim();
                    try {
                        int rv = Integer.parseInt(value);
                        if (rv < 0 || rv > 1) {
                            edtRepeat[nr].setError("Herhaling moet 0 of 1 zijn. 0 betekent niet aktief, 1 dagelijks");
                        }
                    }
                    catch (NumberFormatException e) {
                        edtRepeat[nr].setError("Herhaling moet 0 of 1 zijn.");
                    }
                    if (edtRepeat[nr].getError() == null) {
                        Objects.requireNonNull(timers.get(deviceID))[nr].setRepeat(Integer.parseInt(value));
                        edtPeriod[nr].requestFocus();
                        btnSave.setEnabled(true);
                    }
                }
                return false;
            });
            edtRepeat[i].setOnFocusChangeListener(((v, hasFocus) -> {
                if (!hasFocus) {
                    String value = String.valueOf(edtRepeat[nr].getText()).trim();
                    try {
                        int rv = Integer.parseInt(value);
                        if (rv < 0 || rv > 1) {
                            edtRepeat[nr].setError("Herhaling moet 0 of 1 zijn. 0 betekent niet aktief, 1 dagelijks");
                        }
                    }
                    catch (NumberFormatException e) {
                        edtRepeat[nr].setError("Herhaling moet 0 of 1 zijn.");
                    }
                    if (edtRepeat[nr].getError() == null) {
                        Objects.requireNonNull(timers.get(deviceID))[nr].setRepeat(Integer.parseInt(value));
                        btnSave.setEnabled(true);
                    }
                }
            }));
            resId = getResources().getIdentifier("it_edtPeriod_" + (i + 1), "id", getContext().getPackageName());
            edtPeriod[i] = view.findViewById(resId);
            edtPeriod[i].setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    imm.hideSoftInputFromWindow(edtPeriod[nr].getWindowToken(), 0);
                    String value = String.valueOf(edtPeriod[nr].getText()).trim();
                    try {
                        int val = Integer.parseInt(value);
                        if (val < 0 || val > 3600) {
                            edtPeriod[nr].setError("Periode moet een getal tussen 0 en 3600 zijn.");
                        }
                    }
                    catch (NumberFormatException e) {
                        edtPeriod[nr].setError("Periode moet een getal tussen 0 en 3600 zijn.");
                    }
                    if (edtPeriod[nr].getError() == null) {
                        Objects.requireNonNull(TimersListFragment.timers.get(deviceID))[nr].setPeriod(Integer.parseInt(value));
                        if (!value.equalsIgnoreCase("0")) {
                            edtTimeOff[nr].setText("");
                            Objects.requireNonNull(timers.get(deviceID))[nr].setHourOff(0);
                            Objects.requireNonNull(timers.get(deviceID))[nr].setMinuteOff(0);
                        }
                        btnSave.setEnabled(true);
                    }
                }
                btnSave.requestFocusFromTouch();
                return false;
            });
            edtPeriod[i].setOnFocusChangeListener(((v, hasFocus) -> {
                if (!hasFocus) {
                    String value = String.valueOf(edtPeriod[nr].getText()).trim();
                    try {
                        int val = Integer.parseInt(value);
                        if (val < 0 || val > 3600) {
                            edtPeriod[nr].setError("Periode moet een getal tussen 0 en 3600 zijn.");
                        }
                    }
                    catch (NumberFormatException e) {
                        edtPeriod[nr].setError("Periode moet een getal tussen 0 en 3600 zijn.");
                    }
                    if (edtPeriod[nr].getError() == null) {
                        Objects.requireNonNull(TimersListFragment.timers.get(deviceID))[nr].setPeriod(Integer.parseInt(value));
                        if (!value.equalsIgnoreCase("0")) {
                            edtTimeOff[nr].setText("");
                            Objects.requireNonNull(timers.get(deviceID))[nr].setHourOff(0);
                            Objects.requireNonNull(timers.get(deviceID))[nr].setMinuteOff(0);
                        }
                        btnSave.setEnabled(true);
                    }
                }
            }));
        }
        getTimers();
     }

    private void updateTimers() {
        int[] ids = {R.id.it_layTimer_1, R.id.it_layTimer_2, R.id.it_layTimer_3, R.id.it_layTimer_4, R.id.it_layTimer_5};
        for (int i = 0; i < Objects.requireNonNull(timers.get(deviceID)).length; i++) {
            ConstraintLayout fcv = requireView().findViewById(ids[i]);
            fcv.setVisibility(View.VISIBLE);
            edtTimeOn[i].setText(Utils.cvthm2string(Objects.requireNonNull(timers.get(deviceID))[i].getHourOn(), Objects.requireNonNull(timers.get(deviceID))[i].getMinuteOn()));
            edtTimeOff[i].setText(Utils.cvthm2string(Objects.requireNonNull(timers.get(deviceID))[i].getHourOff(), Objects.requireNonNull(timers.get(deviceID))[i].getMinuteOff()));
            edtRepeat[i].setText(Objects.requireNonNull(timers.get(deviceID))[i].getRepeat() + "");
            edtPeriod[i].setText(Objects.requireNonNull(timers.get(deviceID))[i].getPeriod() + "");
        }

    }

    private void getTimers() {
        wait = new WaitSpinner(requireContext());
        wait.start();
        if (TerrariaApp.MOCK[tabnr - 1]) {
            try {
                Gson gson = new Gson();
                String response = new BufferedReader(
                        new InputStreamReader(getResources().getAssets().open("timers_" + deviceID + "_" + TerrariaApp.configs[tabnr - 1].getMockPostfix() + ".json")))
                        .lines().collect(Collectors.joining("\n"));
                Timer[] devTimers = gson.fromJson(response.toString(), new TypeToken<Timer[]>() {}.getType());
                timers.put(deviceID, devTimers);
                updateTimers();
                wait.dismiss();
            } catch (IOException e) {
                wait.dismiss();
            }
        } else {
//            String url = "http://" + curIPAddress + "/timers/" + deviceID;
//            // Request sensor readings.
//            JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(Request.Method.GET, url, null,
//                    response1 -> {
//                        Gson gson = new Gson();
//                        try {
//                            Timer[] devTimers = gson.fromJson(response1.toString(), new TypeToken<Timer[]>() {
//                            }.getType());
//                            timers.put(deviceID, devTimers);
//                            updateTimers();
//                            wait.dismiss();
//                        } catch (JsonSyntaxException e) {
//                            new NotificationDialog(requireContext(), "Error", "Timers response contains errors:\n" + e.getMessage()).show();
//                        }
//                    },
//                    error -> {
//                        if (error.getMessage() == null) {
//                            StringWriter sw = new StringWriter();
//                            PrintWriter pw = new PrintWriter(sw);
//                            error.printStackTrace(pw);
//                        } else {
//                            new NotificationDialog(requireContext(), "Error", "Kontakt met Control Unit verloren.").show();
//                        }
//                        wait.dismiss();
//                    }
//            );
//            // Add the request to the RequestQueue.
//            RequestQueueSingleton.getInstance(requireContext()).add(jsonArrayRequest);
        }
    }

    private void saveTimers() {
//        wait = new WaitSpinner(requireContext());
//        wait.start();
//        String url = "http://" + curIPAddress + "/timers/" + deviceID;
//        Gson gson = new Gson();
//        String json = gson.toJson(timers.get(deviceID));
//        VoidRequest req = new VoidRequest(Request.Method.PUT, url, json,
//                response -> {
//                    wait.dismiss();
//                },
//                error -> {
//                    wait.dismiss();
//                    new NotificationDialog(requireActivity(), "Error", "Kontakt met Control Unit verloren.").show();
//                }
//        );
//        // Add the request to the RequestQueue.
//        RequestQueueSingleton.getInstance(requireContext()).add(req);
    }

    public boolean checkTime(EditText field) {
        String value = String.valueOf(field.getText()).trim();
        if (value.length() != 0) {
            String[] parts = value.split("\\.");
            if (parts.length == 2) {
                try {
                    int hr = Integer.parseInt(parts[0].trim());
                    if (hr < 0 || hr > 23) {
                        field.setError("Uuropgave moet tussen 0 en 23 zijn");
                    }
                } catch (NumberFormatException e) {
                    field.setError("Uuropgave is geen getal");
                }
                try {
                    int min = Integer.parseInt(parts[1].trim());
                    if (min < 0 || min > 59) {
                        field.setError("Minutenopgave moet tussen 0 en 59 zijn");
                    }
                } catch (NumberFormatException e) {
                    field.setError("Minutenopgave is geen getal");
                }
            } else {
                field.setError("Tijdopgave is niet juist. Formaat: hh.mm");
            }
        }
        return field.getError() == null;
    }
}