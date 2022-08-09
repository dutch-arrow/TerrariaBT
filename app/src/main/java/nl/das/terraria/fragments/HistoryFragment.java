package nl.das.terraria.fragments;

import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import nl.das.terraria.R;
import nl.das.terraria.dialogs.WaitSpinner;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link HistoryFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class HistoryFragment extends Fragment {

    private int curTabNr;
    private WaitSpinner wait;

    private static String curIPAddress;
    private Spinner list;
    private LineChart chart;
    private List<ILineDataSet> dataSets = new ArrayList<>();
    private LineData lineData = new LineData(dataSets);

    // map: <device, <time, on>>
    private Map<String, Map<Integer, Boolean>> history_state = new HashMap<>();
    private Map<Integer, Integer> history_temp = new HashMap<>();
    private static SimpleDateFormat dtfmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    private static SimpleDateFormat tmfmt = new SimpleDateFormat("HH:mm", Locale.US);
    private long xstart;
    private int hmstart;
    private long xend;
    private String[] devicesNl = {"lamp1", "lamp2", "lamp3", "lamp4", "uvlamp", "lamp6", "pomp", "nevel", "sproeier", "vent_in", "vent_uit", ""};
    private String[] devicesEn = {"light1", "light2", "light3", "light4", "uvlight", "light6", "pump", "mist", "sprayer", "fan_in", "fan_out", ""};
    private boolean[] devState = {false, false, false, false, false, false, false, false, false, false, false, false};
    private int chartHeight;
    private int chartWidth;
    private List<String> fileList = new ArrayList<>();

    public HistoryFragment() {
        // Required empty public constructor
    }

    public static HistoryFragment newInstance(int tabnr) {
        HistoryFragment fragment = new HistoryFragment();
        Bundle args = new Bundle();
        args.putInt("tabnr", tabnr);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            curTabNr = getArguments().getInt("tabnr");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        curIPAddress = requireContext().getSharedPreferences("TerrariaApp", 0).getString("terrarium" + curTabNr + "_ip_address", "");

        chart = view.findViewById(R.id.linechart);
        chart.setHardwareAccelerationEnabled(true);
        chart.measure(0,0);
        chart.setTouchEnabled(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setDescription(null);
        chart.setDrawGridBackground(true);
        chart.setDrawMarkers(false);
        chart.getLegend().setEnabled(false);
        chartHeight = chart.getMeasuredHeight();
        chartWidth = chart.getMeasuredWidth();

        list = view.findViewById(R.id.his_list);
        fileList = new ArrayList<>();
        fileList.add("<select day>");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), R.layout.file_dropdown, fileList);
        adapter.setDropDownViewResource(R.layout.file_dropdown);
        list.setAdapter(adapter);
        list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    wait = new WaitSpinner(requireContext());
                    wait.start();
                    if (chart.getLineData() != null) {
                        chart.clearValues();
                    }
                    readHistoryState(fileList.get(position));
                    readHistoryTemperture(fileList.get(position));
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
        Button btnView = view.findViewById(R.id.his_OkButton);
        btnView.setOnClickListener(v -> {
            getParentFragmentManager()
                    .beginTransaction()
                    .replace(R.id.layout, StateFragment.newInstance(curTabNr))
                    .commit();
        });
        // get the list of history files
        getHistoryFiles();
    }

    private void getHistoryFiles() {
//        wait = new WaitSpinner(requireContext());
//        wait.start();
//        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
//        String url = "http://" + curIPAddress + "/history/state";
//        // Request list of history files.
//        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(Request.Method.GET, url, null,
//                response -> {
//                    try {
//                        Gson gson = new Gson();
//                        String[] hislst = gson.fromJson(response.toString(), new TypeToken<String[]>() {}.getType());
////                        for (int i = 0; i < 30; i++) {
////                            fileList.add(String.format("202207%02d", i + 1));
////                        }
//                        for (String f : hislst) {
//                            fileList.add(f.replaceAll("state_", ""));
//                        }
//                        fileList.sort(Collections.reverseOrder());
//                    } catch (JsonSyntaxException e) {
//                        new NotificationDialog(requireContext(), "Error", "History files response contains errors:\n" + e.getMessage()).show();
//                    }
//                    wait.dismiss();
//                },
//                error -> {
//                    if (error.getMessage() == null) {
//                        StringWriter sw = new StringWriter();
//                        PrintWriter pw = new PrintWriter(sw);
//                        error.printStackTrace(pw);
//                    } else {
//                        new NotificationDialog(requireContext(), "Error", "Kontakt met Control Unit verloren.").show();
//                    }
//                    wait.dismiss();
//                }
//        );
//        // Add the request to the RequestQueue.
//        RequestQueueSingleton.getInstance(requireContext()).add(jsonArrayRequest);
    }

    private void readHistoryState(String day) {
//        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
//        String url = "http://" + curIPAddress + "/history/state/state_" + day;
//        // Request state history.
//        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
//                (Response.Listener<String>) response -> {
//                    try {
//                        xend = 0;
//                        /*  0123456789012345678
//                            2021-08-01 05:00:00 start
//                            2021-08-01 06:00:00 mist 1 -1
//                            2021-08-01 06:00:00 fan_in 0
//                            2021-08-01 06:00:00 fan_out 0
//                        */
//                        String[] lines = response.split("\n");
//                        for (String line : lines) {
//                            String[] parts = line.split(" ");
//                            if (parts[2].equalsIgnoreCase("start")) {
//                                xstart = Objects.requireNonNull(dtfmt.parse(parts[0] + " " + parts[1])).getTime() / 1000;
//                                String[] tm = parts[1].split(":");
//                                hmstart = Integer.parseInt(tm[0]) * 3600 + Integer.parseInt(tm[1]) * 60 + Integer.parseInt(tm[2]);
//                            } else if (parts[2].equalsIgnoreCase("stop")) {
//                                xend = Objects.requireNonNull(dtfmt.parse(parts[0] + " " + parts[1])).getTime() / 1000;
//                            } else {
//                                int tm = (int)((Objects.requireNonNull(dtfmt.parse(parts[0] + " " + parts[1])).getTime() / 1000) - xstart);
//                                String dev = parts[2];
//                                boolean on = parts[3].equalsIgnoreCase("1");
//                                history_state.computeIfAbsent(dev, k -> new HashMap<>());
//                                Objects.requireNonNull(history_state.get(dev)).put(tm, on);
//                            }
//                            if (xend == 0) {
//                                xend = xstart + 24 * 60 * 60;
//                            }
//                        }
//                        drawChart();
//                    } catch (JsonSyntaxException | ParseException e) {
//                        new NotificationDialog(requireContext(), "Error", "History state response contains errors:\n" + e.getMessage()).show();
//                    }
//                },
//                (Response.ErrorListener) error -> {
//                    if (error.getMessage() == null) {
//                        StringWriter sw = new StringWriter();
//                        PrintWriter pw = new PrintWriter(sw);
//                        error.printStackTrace(pw);
//                    } else {
//                        new NotificationDialog(requireContext(), "Error", "Kontakt met Control Unit verloren.").show();
//                    }
//                }
//        );
//        // Add the request to the RequestQueue.
//        RequestQueueSingleton.getInstance(requireContext()).add(stringRequest);
    }

    private void drawChart() {
        // X axis
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setAxisMinimum(hmstart);
        xAxis.setAxisMaximum((24 * 60 * 60) + hmstart);
//        xAxis.setLabelRotationAngle(270f);
        xAxis.setLabelCount((((int)(xend - xstart)) / 900) + 1, true); // force 11 labels
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                int hr = (int)value / 3600;
                int mn = ((int)value - (hr * 3600)) / 60;
                return String.format("%02d:%02d", (hr >= 24 ? hr -24 : hr), mn);
            }
        });
        // Y axis
        chart.getAxisRight().setEnabled(false); // suppress right y-axis
        YAxis yAxis = chart.getAxisLeft();
        yAxis.setTextSize(14f); // set the text size
        yAxis.setAxisMinimum(0f); // start at zero
        yAxis.setAxisMaximum(devicesNl.length * 1.5f); // the axis maximum
        yAxis.setTextColor(Color.BLACK);
        yAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                int v = (int) (value * 2f); // 0, 3, 6, 9, ....
                if (v % 3 == 0 ) {
                    if (v == 0) {
                        return "Temp";
                    } else {
                        return devicesNl[(v - 3) / 3];
                    }
                } else {
                    return "";
                }
            }
        });
        yAxis.setGranularity(1.5f); // interval 1.5
        yAxis.setLabelCount(13, true); // force 12 labels

        // Constructing the datasets
        dataSets.add(getDatasets("light1",  Color.BLACK));
        dataSets.add(getDatasets("light2",  Color.BLUE));
        dataSets.add(getDatasets("light3",  Color.GRAY));
        dataSets.add(getDatasets("light4",  Color.RED));
        dataSets.add(getDatasets("uvlight", Color.GREEN));
        dataSets.add(getDatasets("light6",  Color.MAGENTA));
        dataSets.add(getDatasets("pump",    Color.BLACK));
        dataSets.add(getDatasets("sprayer", Color.GRAY));
        dataSets.add(getDatasets("mist",    Color.BLUE));
        dataSets.add(getDatasets("fan_in",  Color.RED));
        dataSets.add(getDatasets("fan_out", Color.GREEN));
        chart.setData(lineData);

        chart.invalidate(); // refresh
    }

    private void readHistoryTemperture(String day) {
//        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
//        String url = "http://" + curIPAddress + "/history/temperature/temp_" + day;
//        // Request state history.
//        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
//                (Response.Listener<String>) response -> {
//                    try {
//                        xend = 0;
//                        /*
//                            2021-08-01 05:00:00 r=21 t=21
//                            2021-08-01 06:00:00 r=21 t=21
//                            2021-08-01 06:45:00 r=21 t=21
//                         */
//                        String[] lines = response.split("\n");
//                        for (String line : lines) {
//                            String[] parts = line.split(" ");
//                            if (parts[2].equalsIgnoreCase("start")) {
//                                xstart = Objects.requireNonNull(dtfmt.parse(parts[0] + " " + parts[1])).getTime() / 1000;
//                                String[] tm = parts[1].split(":");
//                                hmstart = Integer.parseInt(tm[0]) * 3600 + Integer.parseInt(tm[1]) * 60 + Integer.parseInt(tm[2]);
//                            } else if (parts[2].equalsIgnoreCase("stop")) {
//                                xend = Objects.requireNonNull(dtfmt.parse(parts[0] + " " + parts[1])).getTime() / 1000;
//                            } else {
//                                int tm = (int)((Objects.requireNonNull(dtfmt.parse(parts[0] + " " + parts[1])).getTime() / 1000) - xstart);
//                                String room = parts[2].split("=")[1];
//                                int terr = Integer.parseInt(parts[3].split("=")[1]);
//                                history_temp.put(tm, terr);
//                            }
//                            if (xend == 0) {
//                                xend = xstart + 24 * 60 * 60;
//                            }
//                        }
//                        drawTerrTempLine(0xFFF43F1A);
//                        wait.dismiss();
//                    } catch (JsonSyntaxException | ParseException e) {
//                        new NotificationDialog(requireContext(), "Error", "History response contains errors:\n" + e.getMessage()).show();
//                    }
//                },
//                (Response.ErrorListener) error -> {
//                    if (error.getMessage() == null) {
//                        StringWriter sw = new StringWriter();
//                        PrintWriter pw = new PrintWriter(sw);
//                        error.printStackTrace(pw);
//                    } else {
//                        new NotificationDialog(requireContext(), "Error", "Kontakt met Control Unit verloren.").show();
//                    }
//                    wait.dismiss();
//                }
//        );
//        // Add the request to the RequestQueue.
//        RequestQueueSingleton.getInstance(requireContext()).add(stringRequest);
    }

    public void drawTerrTempLine(int color) {
        int curTemp = 0;
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < (xend - xstart); i++) {
            if (history_temp.get(i) != null) {
                curTemp = Integer.valueOf(history_temp.get(i));
            }
            entries.add(new Entry(i + hmstart, (curTemp - 15f) / 20f));
        }
        LineDataSet dataSet = new LineDataSet(entries, ""); // add entries to dataset
        dataSet.setColor(color);
        dataSet.setDrawValues(false);
        dataSet.setDrawCircles(false);
        dataSets.add(dataSet);
        chart.invalidate(); // refresh

    }

    private ILineDataSet getDatasets(String device, int color) {
        List<Entry> entries = getEntries(device);
        LineDataSet dataSet = new LineDataSet(entries, ""); // add entries to dataset
        dataSet.setColor(color);
        dataSet.setDrawValues(false);
        dataSet.setDrawCircles(false);
        return dataSet;
    }

    private List<Entry> getEntries(String device) {
        int ix = getIndex(device);
        Map<Integer, Boolean> dev_states = history_state.get(device);
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < (xend - xstart); i++) {
            if (Objects.requireNonNull(dev_states).get(i) != null) {
                devState[ix] = Boolean.TRUE.equals(dev_states.get(i));
            }
            entries.add(new Entry(i + hmstart, (devState[ix] ? 1f : 0f) + (ix + 1) * 1.5f));
        }
        return entries;
    }

    private int getIndex(String device) {
        for (int i = 0; i < devicesEn.length; i++) {
            if (devicesEn[i].equalsIgnoreCase(device)) {
                return i;
            }
        }
        return devicesEn.length;
    }
}