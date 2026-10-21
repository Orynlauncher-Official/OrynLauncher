package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.AuthType;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalLoginFragment extends Fragment {
    public static final String TAG = "LOCAL_LOGIN_FRAGMENT";

    private final Pattern mUsernameValidationPattern;
    private EditText mUsernameEditText;

    public LocalLoginFragment() {
        super(R.layout.fragment_local_login);
        mUsernameValidationPattern = Pattern.compile("^[a-zA-Z0-9_]*$");
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mUsernameEditText = view.findViewById(R.id.login_edit_email);
        view.findViewById(R.id.login_button).setOnClickListener(v -> {
            if (!checkEditText()) {
                Context context = v.getContext();
                Tools.dialog(context, context.getString(R.string.local_login_bad_username_title),
                        context.getString(R.string.local_login_bad_username_text));
                return;
            }

            String username = mUsernameEditText.getText().toString().trim();
            try {
                // Save the offline/local profile directly. The previous flow only emitted a
                // deferred ExtraCore event; on the Oryn home UI that event may have no consumer,
                // so the profile was never written to the accounts directory.
                Account account = Accounts.create(localAccount -> {
                    localAccount.username = username;
                    localAccount.accessToken = "0";
                    localAccount.profileId = "00000000-0000-0000-0000-000000000000";
                    localAccount.refreshToken = "0";
                    localAccount.authType = AuthType.LOCAL;
                    localAccount.isMicrosoft = false;
                });

                Accounts.setCurrent(account);
                ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
                Toast.makeText(requireContext(), "Local account saved: " + username,
                        Toast.LENGTH_SHORT).show();
                Tools.swapFragment(requireActivity(), MainMenuFragment.class, MainMenuFragment.TAG, null);
            } catch (IOException | RuntimeException e) {
                Toast.makeText(requireContext(), "Could not save local account. Please try again.",
                        Toast.LENGTH_LONG).show();
                android.util.Log.e("OrynLocalAccount", "Failed to save local account", e);
            }
        });
    }

    /** Checks that the local username is valid for Minecraft. */
    private boolean checkEditText() {
        String text = mUsernameEditText.getText().toString().trim();
        Matcher matcher = mUsernameValidationPattern.matcher(text);
        return !text.isEmpty()
                && text.length() >= 3
                && text.length() <= 16
                && matcher.matches();
    }
}
