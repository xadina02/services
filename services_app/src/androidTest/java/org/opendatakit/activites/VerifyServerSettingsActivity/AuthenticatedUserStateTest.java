package org.opendatakit.activites.VerifyServerSettingsActivity;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.isNotEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.intent.Intents;
import androidx.test.espresso.intent.matcher.IntentMatchers;
import androidx.test.espresso.matcher.ViewMatchers;

import org.junit.Test;
import org.opendatakit.BaseUITest;
import org.opendatakit.TestConsts;
import org.opendatakit.consts.IntentConsts;
import org.opendatakit.properties.CommonToolProperties;
import org.opendatakit.properties.PropertiesSingleton;
import org.opendatakit.services.R;
import org.opendatakit.services.resolve.conflict.AllConflictsResolutionActivity;
import org.opendatakit.services.sync.actions.activities.LoginActivity;
import org.opendatakit.services.sync.actions.activities.VerifyServerSettingsActivity;
import org.opendatakit.services.sync.actions.fragments.SetCredentialsFragment;
import org.opendatakit.services.sync.actions.fragments.UpdateServerSettingsFragment;
import org.opendatakit.services.utilities.DateTimeUtil;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class AuthenticatedUserStateTest extends BaseUITest<VerifyServerSettingsActivity> {

      @Override
    protected void setUpPostLaunch() {
        activityScenario.onActivity(activity -> {
            PropertiesSingleton props = activity.getProps();
            assertThat(props).isNotNull();

            Map<String, String> serverProperties = UpdateServerSettingsFragment.getUpdateUrlProperties(TEST_SERVER_URL);
            assertThat(serverProperties).isNotNull();
            props.setProperties(serverProperties);

            Map<String, String> userProperties = SetCredentialsFragment.getCredentialsProperty(TEST_USERNAME, TEST_PASSWORD);
            assertThat(userProperties).isNotNull();
            props.setProperties(userProperties);

            props.setProperties(Collections.singletonMap(CommonToolProperties.KEY_FIRST_LAUNCH, "false"));

            activity.updateViewModelWithProps();
        });

        Espresso.onIdle();
        onView(ViewMatchers.isRoot()).perform(waitForView(withId(R.id.btnDrawerOpenSyncActivity), TestConsts.TIMEOUT_WAIT));
    }

    @Override
    protected Intent getLaunchIntent() {
        Intent intent = new Intent(getContext(), VerifyServerSettingsActivity.class);
        intent.putExtra(IntentConsts.INTENT_KEY_APP_NAME, APP_NAME);

        return intent;
    }

    @Test
    public void verifyVisibilityTest() {
        onView(withId(R.id.tvUserHeadingVerifySettings)).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.GONE)));
        onView(withId(R.id.tvUsernameVerify)).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
        onView(withId(R.id.tvVerificationStatusVerify)).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
        onView(withId(R.id.tvLastSyncTimeVerify)).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
        onView(withId(R.id.btnStartVerifyUser)).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));

        onView(withId(R.id.btnDrawerOpenSyncActivity)).perform(ViewActions.click());
        onView(withId(R.id.drawer_resolve_conflict)).check(matches(isDisplayed()));
        onView(withId(R.id.drawer_switch_sign_in_type)).check(matches(isDisplayed()));
        onView(withId(R.id.drawer_update_credentials)).check(matches(isDisplayed()));
    }

    @Test
    public void verifyValuesTest() {
        onView(withId(R.id.tvUsernameVerify)).check(matches(withText(TEST_USERNAME)));
        onView(withId(R.id.tvVerificationStatusVerify)).check(matches(withText(getContext().getString(R.string.not_verified))));

        onView(withId(R.id.tvLastSyncTimeVerify)).check(matches(withText(getContext().getString(R.string.last_sync_not_available))));

        onView(withId(R.id.btnDrawerOpenSyncActivity)).perform(ViewActions.click());
        onView(withId(R.id.btnDrawerLogin)).check(matches(withText(getContext().getString(R.string.drawer_sign_out_button_text))));
    }

    @Test
    public void verifyLastSyncTimeTest() {
        onView(withId(R.id.tvLastSyncTimeVerify)).check(matches(withText(getContext().getString(R.string.last_sync_not_available))));
        long currentTime = new Date().getTime();
        activityScenario.onActivity(activity -> {
            PropertiesSingleton props = CommonToolProperties.get(activity, activity.getAppName());
            props.setProperties(Collections.singletonMap(CommonToolProperties.KEY_LAST_SYNC_INFO, Long.toString(currentTime)));
            activity.updateViewModelWithProps();
        });
        onView(withId(R.id.tvLastSyncTimeVerify)).check(matches(withText(DateTimeUtil.getDisplayDate(currentTime))));
    }

    @Test
    public void verifyDrawerResolveConflictsClick() {
        onView(withId(R.id.btnDrawerOpenSyncActivity)).perform(ViewActions.click());

        try {
            Thread.sleep(TestConsts.SHORT_WAIT);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        onView(withId(R.id.drawer_resolve_conflict)).perform(ViewActions.click());

        try {
            Thread.sleep(TestConsts.SHORT_WAIT);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Intents.intended(IntentMatchers.hasComponent(AllConflictsResolutionActivity.class.getName()));
    }

    @Test
    public void verifyDrawerSwitchSignInTypeClick() {
        onView(withId(R.id.btnDrawerOpenSyncActivity)).perform(ViewActions.click());
        onView(withId(R.id.drawer_switch_sign_in_type)).perform(ViewActions.click());

        Intents.intended(IntentMatchers.hasComponent(LoginActivity.class.getName()));

        onView(withId(R.id.tvTitleLogin)).check(matches(withText(getContext().getString(R.string.switch_sign_in_type))));
        onView(withId(R.id.btnAnonymousSignInLogin)).check(matches(withText(R.string.anonymous_user)));
        onView(withId(R.id.btnAnonymousSignInLogin)).check(matches(isEnabled()));
        onView(withId(R.id.btnUserSignInLogin)).check(matches(withText(R.string.drawer_item_update_credentials)));
        onView(withId(R.id.btnUserSignInLogin)).check(matches(isNotEnabled()));
    }

    @Test
    public void verifySwitchSignInWhenAnonymousNotAllowed() {
        activityScenario.onActivity(activity -> {
            PropertiesSingleton props = CommonToolProperties.get(activity, activity.getAppName());

            Map<String, String> properties = new HashMap<>();
            properties.put(CommonToolProperties.KEY_IS_ANONYMOUS_SIGN_IN_USED, Boolean.toString(true));
            properties.put(CommonToolProperties.KEY_IS_ANONYMOUS_ALLOWED, Boolean.toString(false));
            props.setProperties(properties);

            activity.updateViewModelWithProps();
        });

        onView(withId(R.id.btnDrawerOpenSyncActivity)).perform(ViewActions.click());
        onView(withId(R.id.drawer_switch_sign_in_type)).check(matches(isNotEnabled()));
    }

    @Test
    public void verifyDrawerUpdateCredentialsClick() {
        onView(withId(R.id.btnDrawerOpenSyncActivity)).perform(ViewActions.click());
        onView(withId(R.id.drawer_update_credentials)).perform(ViewActions.click());

        Intents.intended(IntentMatchers.hasComponent(LoginActivity.class.getName()));

        onView(withId(R.id.tvTitleLogin)).check(matches(withText(getContext().getString(R.string.drawer_item_update_credentials))));
        onView(withId(R.id.btnAuthenticateUserLogin)).check(matches(withText(R.string.drawer_item_update_credentials)));
        onView(withId(R.id.inputUsernameLogin)).check(matches(isDisplayed()));
        onView(withId(R.id.inputTextUsername)).check(matches(withText(TEST_USERNAME)));
        onView(withId(R.id.inputTextPassword)).check(matches(withText("")));
    }

    @Test
    public void verifyDrawerSignOutButtonClick() {
        onView(withId(R.id.btnDrawerOpenSyncActivity)).perform(ViewActions.click());
        onView(withId(R.id.btnDrawerLogin)).perform(ViewActions.click());

        onView(withId(R.id.tvUserHeadingVerifySettings)).check(matches(withText(getContext().getString(R.string.user_logged_out_label))));
        onView(withId(R.id.btnDrawerLogin)).check(matches(withText(getContext().getString(R.string.drawer_sign_in_button_text))));
    }
}
