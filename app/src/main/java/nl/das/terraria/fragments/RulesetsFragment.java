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

public class RulesetsFragment extends Fragment {

    private int tabnr;

    private LinearLayout rulesetsLayout;
    private Button btnCurrent;

    public RulesetsFragment() {
        // Required empty public constructor
    }

    public static RulesetsFragment newInstance(int tabnr) {
        RulesetsFragment fragment = new RulesetsFragment();
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
        View view = inflater.inflate(R.layout.fragment_rulesets, container, false);
        int backcolorSel = getResources().getColor(R.color.colorPrimaryDark, null);
        int backcolor = getResources().getColor(R.color.notActive, null);
        int nrrs = TerrariaApp.configs[tabnr - 1].getNrOfPrograms();
        rulesetsLayout = view.findViewById(R.id.rulesetButtons);
        for (int rs = 0; rs < nrrs; rs++) {
            int rsnr = rs;
            View v = inflater.inflate(R.layout.dynamic_button, container, false);
            Button btnRs = v.findViewById(R.id.dyn_button_id);
            btnRs.setOnClickListener(bv1 -> {
                Button btn = (Button) bv1;
                btn.getBackground().mutate().setColorFilter(new PorterDuffColorFilter(backcolorSel, PorterDuff.Mode.SRC));
                btn.setTextColor(getResources().getColor(R.color.white, null));
                if (btnCurrent != null) {
                    btnCurrent.getBackground().mutate().setColorFilter(new PorterDuffColorFilter(backcolor, PorterDuff.Mode.SRC));
                    btnCurrent.setTextColor(getResources().getColor(R.color.black, null));
                }
                btnCurrent = btn;
                FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                ft.replace(R.id.rs_fcvRuleset, RulesetFragment.newInstance(tabnr, rsnr + 1));
                ft.commit();
            });
            rulesetsLayout.addView(v);
        }

        if (hasSprayer()) {
            View v = inflater.inflate(R.layout.dynamic_button, container, false);
            Button btnRs = v.findViewById(R.id.dyn_button_id);
            btnRs.setOnClickListener(dv -> {
                Button btn = (Button) dv;
                btn.getBackground().mutate().setColorFilter(new PorterDuffColorFilter(backcolorSel, PorterDuff.Mode.SRC));
                btn.setTextColor(getResources().getColor(R.color.white, null));
                if (btnCurrent != null) {
                    btnCurrent.getBackground().mutate().setColorFilter(new PorterDuffColorFilter(backcolor, PorterDuff.Mode.SRC));
                    btnCurrent.setTextColor(getResources().getColor(R.color.black, null));
                }
                btnCurrent = btn;
                FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                ft.replace(R.id.rs_fcvRuleset, SprayerRuleFragment.newInstance(tabnr));
                ft.commit();
            });
            rulesetsLayout.addView(v);
        }
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        int nrrs = TerrariaApp.configs[tabnr - 1].getNrOfPrograms();
        int vix = 0;
        int r = getResources().getIdentifier("mitRuleset", "string", "nl.das.terrariaBT");
        for (int rs = 0; rs < nrrs; rs++) {
            Button btn = (Button) rulesetsLayout.getChildAt(vix);
            btn.setText(getResources().getString(r) + (rs + 1));
            vix++;
        }
        if (hasSprayer()) {
            r = getResources().getIdentifier("lblSprayingRules", "string", "nl.das.terrariaBT");
            Button btn = (Button) rulesetsLayout.getChildAt(vix);
            btn.setText(getResources().getString(r));
        }
        View v = rulesetsLayout.getChildAt(0);
        Button btnRs = v.findViewById(R.id.dyn_button_id);
        btnRs.performClick();
    }

    private boolean hasSprayer() {
        for (Device d : TerrariaApp.configs[tabnr - 1].getDevices()) {
            if (d.getDevice().equalsIgnoreCase("sprayer")) {
                return true;
            }
        }
        return false;
    }
}