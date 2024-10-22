package org.opendatakit.activites.SyncActivity;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.isNotEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.ViewInteraction;
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
import org.opendatakit.services.preferences.activities.AppPropertiesActivity;
import org.opendatakit.services.sync.actions.activities.SyncActivity;
import org.opendatakit.services.sync.actions.fragments.UpdateServerSettingsFragment;

import java.util.Collections;
import java.util.Map;

public class GeneralStateTest extends BaseUITest<SyncActivity> {

    @Override
    protected void setUpPostLaunch() {
        activityScenario.onActivity(activity -> {
            PropertiesSingleton props = activity.getProps();
            assertThat(props).isNotNull();

            Map<String, String> serverProperties = UpdateServerSettingsFragment.getUpdateUrlProperties(TEST_SERVER_URL);
            assertThat(serverProperties).isNotNull();
            props.setProperties(serverProperties);

            props.setProperties(Collections.singletonMap(CommonToolProperties.KEY_FIRST_LAUNCH, "false"));

            activity.updateViewModelWithProps();
        });

        Espresso.onIdle();
        onView(ViewMatchers.isRoot()).perform(waitForView(withId(R.id.btnDrawerOpenSyncActivity), TestConsts.TIMEOUT_WAIT));
    }

    @Override
    protected Intent getLaunchIntent() {
        Intent intent = new Intent(getContext(), SyncActivity.class);
        intent.putExtra(IntentConsts.INTENT_KEY_APP_NAME, APP_NAME);

        return intent;
    }

    @Test
    public void verifyValuesTest() {
        onView(withId(R.id.tvServerUrlSync)).check(matches(withText(TEST_SERVER_URL)));
    }

    @Test
    public void checkToolbarSettingsButtonClick() {
        onView(withId(R.id.action_settings)).perform(ViewActions.click());
        Intents.intended(IntentMatchers.hasComponent(AppPropertiesActivity.class.getName()));
    }

    @Test
    public void checkDrawerSettingsClick() {
        onView(withId(R.id.btnDrawerOpenSyncActivity)).perform(ViewActions.click());
        onView(withId(R.id.drawer_settings)).perform(ViewActions.click());
        Intents.intended(IntentMatchers.hasComponent(AppPropertiesActivity.class.getName()));
    }

    @Test
    public void checkDrawerServerLoginTest() {
        onView(withId(R.id.btnDrawerOpenSyncActivity)).perform(ViewActions.click());
        onView(withId(R.id.drawer_server_login)).perform(ViewActions.click());

        onView(withId(R.id.inputServerUrl)).check(matches(isDisplayed()));
        onView(withId(R.id.inputTextServerUrl)).check(matches(withText(TEST_SERVER_URL)));
    }

    @Test
    public void checkDrawerAboutUsBtnClick() {
        onView(withId(R.id.btnDrawerOpenSyncActivity)).perform(ViewActions.click());

        ViewInteraction btnAboutUs = onView(withId(R.id.drawer_about_us));
        btnAboutUs.check(matches(isEnabled()));

        btnAboutUs.perform(ViewActions.click());

        onView(withId(org.opendatakit.androidlibrary.R.id.versionText)).check(matches(isDisplayed()));
        btnAboutUs.check(matches(isNotEnabled()));
    }
}
