package dev.skomlach.biometric.compat.impl;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import dev.skomlach.common.contextprovider.AndroidContext;
import dev.skomlach.common.misc.BroadcastTools;
import dev.skomlach.common.misc.ExecutorHelper;
import dev.skomlach.common.permissions.PermissionUtils;

import java.util.ArrayList;
import java.util.List;

public class PermissionsFragment extends Fragment {
    private static final String LIST_KEY = "permissions_list";
    private static final String INTENT_KEY = "intent_key";

    public static void askForPermissions(@NonNull FragmentActivity activity, @NonNull List<String> permissions, @Nullable Runnable callback) {
        if (!PermissionUtils.INSTANCE.hasSelfPermissions(permissions)) {
            PermissionsFragment fragment = new PermissionsFragment();
            Bundle bundle = new Bundle();
            bundle.putStringArrayList(LIST_KEY, new ArrayList<>(permissions));
            fragment.setArguments(bundle);
            BroadcastTools.registerGlobalBroadcastIntent(AndroidContext.getAppContext(), new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (callback != null)
                        ExecutorHelper.INSTANCE.getHandler().post(callback);
                    BroadcastTools.unregisterGlobalBroadcastIntent(AndroidContext.getAppContext(), this);
                }
            }, new IntentFilter(INTENT_KEY));
            activity.getSupportFragmentManager().beginTransaction().add(fragment, fragment.getClass().getName()).commitNowAllowingStateLoss();
        } else {
            if (callback != null)
                ExecutorHelper.INSTANCE.getHandler().post(callback);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        BroadcastTools.sendGlobalBroadcastIntent(AndroidContext.getAppContext(), new Intent(INTENT_KEY));
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        List<String> permissions = getArguments().getStringArrayList(LIST_KEY);
        if (!PermissionUtils.INSTANCE.hasSelfPermissions(permissions)) {
            this.requestPermissions(permissions.toArray(new String[0]), 100);
        } else {
            getActivity().getSupportFragmentManager().beginTransaction().remove(this).commitNowAllowingStateLoss();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        getActivity().getSupportFragmentManager().beginTransaction().remove(this).commitNowAllowingStateLoss();
    }
}
