package nl.das.terraria.fragments;

import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import nl.das.terraria.R;
import nl.das.terraria.TerrariaApp;
import nl.das.terraria.json.Device;

public class TimersFragment extends Fragment {

    private int tabnr;
    private LinearLayout deviceLayout;
    private Button btnCurrent;

    public TimersFragment() {
        // Required empty public constructor
    }

    public static TimersFragment newInstance(int tabnr) {
        TimersFragment fragment = new TimersFragment();
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        int backcolorSel = getResources().getColor(R.color.colorPrimaryDark, null);
        int backcolor = getResources().getColor(R.color.notActive, null);
        View view = inflater.inflate(R.layout.fragment_timers, container, false);
        deviceLayout = view.findViewById(R.id.deviceButtons);
        for (Device d :  TerrariaApp.configs[tabnr - 1].getDevices()) {
            View v = inflater.inflate(R.layout.dynamic_button, container, false);
            Button b = v.findViewById(R.id.dyn_button_id);
            String devname = d.getDevice();
            int r = getResources().getIdentifier(devname, "string", "nl.das.terrariaBT");
            b.setText(getResources().getString(r));
            b.setOnClickListener(bv -> {
                Button btn = (Button) bv;
                btn.getBackground().mutate().setColorFilter(new PorterDuffColorFilter(backcolorSel, PorterDuff.Mode.SRC));
                btn.setTextColor(getResources().getColor(R.color.white, null));
                if( btnCurrent != null) {
                    btnCurrent.getBackground().mutate().setColorFilter(new PorterDuffColorFilter(backcolor, PorterDuff.Mode.SRC));
                    btnCurrent.setTextColor(getResources().getColor(R.color.black, null));
                }
                btnCurrent = btn;
                FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                ft.replace(R.id.tmr_timers, TimersListFragment.newInstance(tabnr, d.getDevice()));
                ft.commit();
            });
            deviceLayout.addView(v);
        }
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        View v = deviceLayout.getChildAt(0);
        Button firstButton = v.findViewById(R.id.dyn_button_id);
        firstButton.callOnClick();
    }
}