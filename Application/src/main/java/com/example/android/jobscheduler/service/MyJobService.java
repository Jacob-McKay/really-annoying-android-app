/*
 * Copyright 2014 Google Inc.
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

package com.example.android.jobscheduler.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.util.Log;


import com.example.android.jobscheduler.MainActivity;
import com.example.android.jobscheduler.R;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;

import java.io.IOException;

import static com.example.android.jobscheduler.MainActivity.MESSENGER_INTENT_KEY;
import static com.example.android.jobscheduler.MainActivity.MSG_COLOR_START;
import static com.example.android.jobscheduler.MainActivity.MSG_COLOR_STOP;
import static com.example.android.jobscheduler.MainActivity.MSG_IT_AINT_MY_TURN;
import static com.example.android.jobscheduler.MainActivity.MSG_IT_IS_MY_TURN;
import static com.example.android.jobscheduler.MainActivity.WORK_DURATION_KEY;


/**
 * Service to handle callbacks from the JobScheduler. Requests scheduled with the JobScheduler
 * ultimately land on this service's "onStartJob" method. It runs jobs for a specific amount of time
 * and finishes them. It keeps the activity updated with changes via a Messenger.
 */
public class MyJobService extends JobService {

    private static final String TAG = MyJobService.class.getSimpleName();

    private Messenger mActivityMessenger;

    JobParameters mParams;

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
            Intent intent = new Intent(MyJobService.this, AudioService.class);

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
    public void onCreate() {
        super.onCreate();

        bindAudioService();

        Log.i(TAG, "Service created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unbound background audio service when activity is destroyed.
        unBoundAudioService();
        Log.i(TAG, "Service destroyed");
    }

    /**
     * When the app's MainActivity is created, it starts this service. This is so that the
     * activity and this service can communicate back and forth. See "setUiCallback()"
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mActivityMessenger = intent.getParcelableExtra(MESSENGER_INTENT_KEY);
        return START_NOT_STICKY;
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        mParams = params;
        // The work that this service "does" is simply wait for a certain duration and finish
        // the job (on another thread).

        sendMessage(MSG_COLOR_START, params.getJobId());

//        long duration = params.getExtras().getLong(WORK_DURATION_KEY);
        long duration = 5000;
        final String deviceId = params.getExtras().getString("deviceId");

//        mAnnoyingSound = (MediaPlayer) params.getExtras().get("MediaPlayer");
//        MediaPlayer gong = MediaPlayer.create(MyJobService.this, R.raw.gong);
//        gong.start();

        // Uses a handler to delay the execution of jobFinished().
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                new IsItMyTurn().execute(deviceId);

//                sendMessage(MSG_COLOR_STOP, params.getJobId());
            }
        }, duration);
        Log.i(TAG, "on start job: " + params.getJobId());


        // Return true as there's more work to be done with this job.
        return true;
    }

    class IsItMyTurn extends AsyncTask<String, Void, String> {

        private Exception exception;

        protected String doInBackground(String... params) {

            try {
                HttpRequestFactory requestFactory
                        = new NetHttpTransport().createRequestFactory();
                HttpRequest request = requestFactory.buildGetRequest(
                        new GenericUrl("https://prankapp.azurewebsites.net/api/Pranks/reallycheckmyturn/" + params[0]));
                String rawResponse = request.execute().parseAsString();
                return rawResponse;
            } catch (Exception ex) {
                Log.e("morebusted", "morewrecked", ex);
                this.exception = ex;
                return null;
            }
        }

        protected void onPostExecute(String response) {
            if (this.exception != null) {
                sendMessage(MSG_IT_IS_MY_TURN, null);
            } else {
                Log.i("api response: ", response);
                if(response.contains("true")) {
                    audioServiceBinder.startAudio();
                    sendMessage(MSG_IT_IS_MY_TURN, null);
                } else {
                    sendMessage(MSG_IT_AINT_MY_TURN, null);
                }
            }

            jobFinished(mParams, false);
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // Stop tracking these job parameters, as we've 'finished' executing.
        sendMessage(MSG_COLOR_STOP, params.getJobId());
        Log.i(TAG, "on stop job: " + params.getJobId());

        // Return false to drop the job.
        return false;
    }

    private void sendMessage(int messageID, @Nullable Object params) {
        // If this service is launched by the JobScheduler, there's no callback Messenger. It
        // only exists when the MainActivity calls startService() with the callback in the Intent.
        if (mActivityMessenger == null) {
            Log.d(TAG, "Service is bound, not started. There's no callback to send a message to.");
            return;
        }
        Message m = Message.obtain();
        m.what = messageID;
        m.obj = params;
        try {
            mActivityMessenger.send(m);
        } catch (RemoteException e) {
            Log.e(TAG, "Error passing service object back to activity.");
        }
    }
}
