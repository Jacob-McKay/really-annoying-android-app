/*
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.jobscheduler;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PersistableBundle;
import android.support.annotation.ColorRes;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.jobscheduler.service.AudioService;
import com.example.android.jobscheduler.service.AudioServiceBinder;
import com.example.android.jobscheduler.service.MyJobService;

import java.lang.ref.WeakReference;
import java.util.List;


/**
 * Schedules and configures jobs to be executed by a {@link JobScheduler}.
 * <p>
 * {@link MyJobService} can send messages to this via a {@link Messenger}
 * that is sent in the Intent that starts the Service.
 */
public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();

    public static final int MSG_UNCOLOR_START = 0;
    public static final int MSG_UNCOLOR_STOP = 1;
    public static final int MSG_COLOR_START = 2;
    public static final int MSG_COLOR_STOP = 3;
    public static final int MSG_IT_AINT_MY_TURN = 4;
    public static final int MSG_IT_IS_MY_TURN = 5;


    public static final String MESSENGER_INTENT_KEY
            = BuildConfig.APPLICATION_ID + ".MESSENGER_INTENT_KEY";
    public static final String WORK_DURATION_KEY =
            BuildConfig.APPLICATION_ID + ".WORK_DURATION_KEY";

    private EditText mDelayEditText;
    private EditText mDeadlineEditText;
    private EditText mDurationTimeEditText;
    private RadioButton mWiFiConnectivityRadioButton;
    private RadioButton mAnyConnectivityRadioButton;
    private CheckBox mRequiresChargingCheckBox;
    private CheckBox mRequiresIdleCheckbox;

    private ComponentName mServiceComponent;

    private int mJobId = 0;

    // Handler for incoming messages from the service.
    private IncomingMessageHandler mHandler;

    private AudioServiceBinder audioServiceBinder = null;

    // This service connection object is the bridge between activity and background service.
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            // Cast and assign background service's onBind method returned iBander object.
            audioServiceBinder = (AudioServiceBinder) iBinder;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };

    // Bind background service with caller activity. Then this activity can use
    // background service's AudioServiceBinder instance to invoke related methods.
    private void bindAudioService()
    {
        if(audioServiceBinder == null) {
            Intent intent = new Intent(MainActivity.this, AudioService.class);

            // Below code will invoke serviceConnection's onServiceConnected method.
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    // Unbound background audio service with caller activity.
    private void unBoundAudioService()
    {
        if(audioServiceBinder != null) {
            unbindService(serviceConnection);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sample_main);

        // Bind background audio service when activity is created.
        bindAudioService();
//        mAnnoyingSound = MediaPlayer.create(MainActivity.this, R.raw.chanel_west_coast_awful_laugh);

        // Set up UI.
        mDelayEditText = (EditText) findViewById(R.id.delay_time);
        mDurationTimeEditText = (EditText) findViewById(R.id.duration_time);
        mDeadlineEditText = (EditText) findViewById(R.id.deadline_time);
        mWiFiConnectivityRadioButton = (RadioButton) findViewById(R.id.checkbox_unmetered);
        mAnyConnectivityRadioButton = (RadioButton) findViewById(R.id.checkbox_any);
        mRequiresChargingCheckBox = (CheckBox) findViewById(R.id.checkbox_charging);
        mRequiresIdleCheckbox = (CheckBox) findViewById(R.id.checkbox_idle);
        mServiceComponent = new ComponentName(this, MyJobService.class);

        mHandler = new IncomingMessageHandler(this);
    }

    @Override
    protected void onStop() {
        // A service can be "started" and/or "bound". In this case, it's "started" by this Activity
        // and "bound" to the JobScheduler (also called "Scheduled" by the JobScheduler). This call
        // to stopService() won't prevent scheduled jobs to be processed. However, failing
        // to call stopService() would keep it alive indefinitely.
        stopService(new Intent(this, MyJobService.class));
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Start service and provide it a way to communicate with this class.
        Intent startServiceIntent = new Intent(this, MyJobService.class);
        Messenger messengerIncoming = new Messenger(mHandler);
        startServiceIntent.putExtra(MESSENGER_INTENT_KEY, messengerIncoming);
        startService(startServiceIntent);
    }

    @Override
    protected void onDestroy() {
        // Unbound background audio service when activity is destroyed.
        unBoundAudioService();
        super.onDestroy();
    }

    /**
     * Executed when user clicks on SCHEDULE JOB.
     */
    public void scheduleJob(View v) {
        // Set application context.
        audioServiceBinder.setContext(getApplicationContext());
        // Start audio in background service.
//        audioServiceBinder.startAudio();
        for (int i = 1; i < 2; i++) { // just for debugging
//        for (int i = 1; i < 101; i++) { // queue up jobs for the next 8 minutes
            this.scheduleJobInSeconds(i * 5);
        }
    }

    private void scheduleJobInSeconds(int runInSeconds) {
        JobInfo.Builder builder = new JobInfo.Builder(mJobId++, mServiceComponent);

        // this blows, they clamp it to 15 minutes :face_palm:
//        builder.setPeriodic(5000); // repeat every five seconds bruh;

        String delay = mDelayEditText.getText().toString();
        if (!TextUtils.isEmpty(delay)) {
            builder.setMinimumLatency(Long.valueOf(runInSeconds) * 1000);
        }
        // not no mo you dont
        String deadline = mDeadlineEditText.getText().toString();
        if (!TextUtils.isEmpty(deadline)) {
            builder.setOverrideDeadline(Long.valueOf(runInSeconds) * 1000);
        }
        boolean requiresUnmetered = mWiFiConnectivityRadioButton.isChecked();
        boolean requiresAnyConnectivity = mAnyConnectivityRadioButton.isChecked();
        if (requiresUnmetered) {
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
        } else if (requiresAnyConnectivity) {
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        }
        builder.setRequiresDeviceIdle(mRequiresIdleCheckbox.isChecked());
        builder.setRequiresCharging(mRequiresChargingCheckBox.isChecked());

        // Extras, work duration.
        PersistableBundle extras = new PersistableBundle();
        String workDuration = mDurationTimeEditText.getText().toString();
        if (TextUtils.isEmpty(workDuration)) {
            workDuration = "1";
        }
        extras.putLong(WORK_DURATION_KEY, Long.valueOf(workDuration) * 1000);

        builder.setExtras(extras);

        // Schedule job
        Log.d(TAG, "Scheduling job");
        JobScheduler tm = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        tm.schedule(builder.build());
    }

    /**
     * Executed when user clicks on CANCEL ALL.
     */
    public void cancelAllJobs(View v) {
        JobScheduler tm = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        tm.cancelAll();
        Toast.makeText(MainActivity.this, R.string.all_jobs_cancelled, Toast.LENGTH_SHORT).show();
    }

    /**
     * Executed when user clicks on FINISH LAST TASK.
     */
    public void finishJob(View v) {
        JobScheduler jobScheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        List<JobInfo> allPendingJobs = jobScheduler.getAllPendingJobs();
        if (allPendingJobs.size() > 0) {
            // Finish the last one
            int jobId = allPendingJobs.get(0).getId();
            jobScheduler.cancel(jobId);
            Toast.makeText(
                    MainActivity.this, String.format(getString(R.string.cancelled_job), jobId),
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(
                    MainActivity.this, getString(R.string.no_jobs_to_cancel),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // THIS CLASS WAS STATIC, CAN IT BE NON STATIC?
    /**
     * A {@link Handler} allows you to send messages associated with a thread. A {@link Messenger}
     * uses this handler to communicate from {@link MyJobService}. It's also used to make
     * the start and stop views blink for a short period of time.
     */
    private class IncomingMessageHandler extends Handler {

        // Prevent possible leaks with a weak reference.
        private WeakReference<MainActivity> mActivity;

        IncomingMessageHandler(MainActivity activity) {
            super(/* default looper */);
            this.mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity mainActivity = mActivity.get();
            if (mainActivity == null) {
                // Activity is no longer available, exit.
                return;
            }
            View showStartView = mainActivity.findViewById(R.id.onstart_textview);
            View showStopView = mainActivity.findViewById(R.id.onstop_textview);
            Message m;
            switch (msg.what) {
                /*
                 * Receives callback from the service when a job has landed
                 * on the app. Turns on indicator and sends a message to turn it off after
                 * a second.
                 */
                case MSG_COLOR_START:
                    // Start received, turn on the indicator and show text.
                    showStartView.setBackgroundColor(getColor(R.color.start_received));
                    updateParamsTextView(msg.obj, "started");

                    // Send message to turn it off after a second.
                    m = Message.obtain(this, MSG_UNCOLOR_START);
                    sendMessageDelayed(m, 1000L);
                    break;
                /*
                 * Receives callback from the service when a job that previously landed on the
                 * app must stop executing. Turns on indicator and sends a message to turn it
                 * off after two seconds.
                 */
                case MSG_COLOR_STOP:
                    // Stop received, turn on the indicator and show text.
                    showStopView.setBackgroundColor(getColor(R.color.stop_received));
                    updateParamsTextView(msg.obj, "stopped");

                    // Send message to turn it off after a second.
                    m = obtainMessage(MSG_UNCOLOR_STOP);
                    sendMessageDelayed(m, 2000L);
                    break;
                case MSG_UNCOLOR_START:
                    showStartView.setBackgroundColor(getColor(R.color.none_received));
                    updateParamsTextView(null, "");
                    break;
                case MSG_UNCOLOR_STOP:
                    showStopView.setBackgroundColor(getColor(R.color.none_received));
                    updateParamsTextView(null, "");
                    break;
                case MSG_IT_AINT_MY_TURN:
                    break;
                case MSG_IT_IS_MY_TURN:
// 1. Instantiate an <code><a href="/reference/android/app/AlertDialog.Builder.html">AlertDialog.Builder</a></code> with its constructor
                    AlertDialog.Builder builder = new AlertDialog.Builder(mActivity.get());

// 2. Chain together various setter methods to set the dialog characteristics
                    builder.setMessage("Click the right dismiss button")
                            .setTitle("Do you hear, it?   Isn't it great?");


                    builder.setPositiveButton("Left", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
//                            mAnnoyingSound.stop();
                        }
                    });
                    builder.setNegativeButton("Right", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // User cancelled the dialog
                        }
                    });

// 3. Get the <code><a href="/reference/android/app/AlertDialog.html">AlertDialog</a></code> from <code><a href="/reference/android/app/AlertDialog.Builder.html#create()">create()</a></code>
                    AlertDialog dialog = builder.create();
                    dialog.show();
                    break;
            }
        }

        private void updateParamsTextView(@Nullable Object jobId, String action) {
            TextView paramsTextView = (TextView) mActivity.get().findViewById(R.id.task_params);
            if (jobId == null) {
                paramsTextView.setText("");
                return;
            }
            String jobIdText = String.valueOf(jobId);
            paramsTextView.setText(String.format("Job ID %s %s", jobIdText, action));
        }

        private int getColor(@ColorRes int color) {
            return mActivity.get().getResources().getColor(color);
        }
    }
}
