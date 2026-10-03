package com.hkscout.field;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override public void onCreate(Bundle savedInstanceState) {
        registerPlugin(LightMeterPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
