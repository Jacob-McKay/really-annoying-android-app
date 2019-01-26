//package com.example.android.jobscheduler.service;
//
//import android.app.Service;
//import android.content.Intent;
//import android.media.MediaPlayer;
//import android.os.IBinder;
//
//import com.example.android.jobscheduler.R;
//
//public class MyAudioService extends Service implements MediaPlayer.OnPreparedListener {
//    private static final String ACTION_PLAY = "com.example.action.PLAY";
//    MediaPlayer mMediaPlayer = null;
//
//    public int onStartCommand(Intent intent, int flags, int startId) {
//        if (intent.getAction().equals(ACTION_PLAY)) {
//            mMediaPlayer = MediaPlayer.create(MyAudioService.this, R.raw.chanel_west_coast_awful_laugh);
//            mMediaPlayer.setOnPreparedListener(this);
//            mMediaPlayer.prepareAsync(); // prepare async to not block main thread
//        }
//
//        return super.onStartCommand(intent, flags, startId);
//    }
//
//    @Override
//    public IBinder onBind(Intent intent) {
//        return iBinder;
//    }
//
//    /** Called when MediaPlayer is ready */
//    public void onPrepared(MediaPlayer player) {
//        player.start();
//    }
//}