package com.quickblox.qmunicate.ui.mediacall;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.quickblox.module.users.model.QBUser;
import com.quickblox.module.videochat.model.objects.CallType;
import com.quickblox.module.videochat_webrtc.ISignalingChannel;
import com.quickblox.module.videochat_webrtc.WebRTC;
import com.quickblox.qmunicate.App;
import com.quickblox.qmunicate.R;
import com.quickblox.qmunicate.core.communication.SessionDescriptionWrapper;
import com.quickblox.qmunicate.ui.base.BaseActivity;
import com.quickblox.qmunicate.ui.media.MediaPlayerManager;
import com.quickblox.qmunicate.ui.videocall.VideoCallFragment;
import com.quickblox.qmunicate.ui.voicecall.VoiceCallFragment;
import com.quickblox.qmunicate.utils.Consts;

public class CallActivity extends BaseActivity implements IncomingCallFragment.IncomingCallClickListener, OutgoingCallFragment.OutgoingCallListener {

    private static final String TAG = CallActivity.class.getSimpleName();
    private static final int AUDIO_CALL = 2;
    private QBUser opponent;
    private Consts.CALL_DIRECTION_TYPE call_direction_type;
    private SessionDescriptionWrapper sessionDescriptionWrapper;
    private int call_type;
    private ISignalingChannel signalingChannel;
    private MediaPlayerManager mediaPlayer;
    private String sessionId;

    public static final int VIDEO_AUDIO_CALL = 1;

    public static void start(Context context, QBUser friend, CallType callType) {
        Intent intent = new Intent(context, CallActivity.class);
        intent.putExtra(Consts.USER, friend);
        intent.putExtra(Consts.CALL_DIRECTION_TYPE_EXTRA, Consts.CALL_DIRECTION_TYPE.OUTGOING);
        intent.putExtra(Consts.CALL_TYPE_EXTRA, callType);
        context.startActivity(intent);
    }

    @Override
    public void onBackPressed() {
    }

    @Override
    public void onAcceptClick() {
        accept();
    }

    @Override
    public void onDenyClick() {
        reject();
    }

    @Override
    public void onConnectionAccepted() {
        cancelPlayer();
    }

    @Override
    public void onConnectionRejected() {
        cancelPlayer();
        finish();
    }

    @Override
    public void onConnectionClosed() {
        cancelPlayer();
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_call);
        actionBar.hide();
        mediaPlayer = App.getInstance().getMediaPlayer();
        parseIntentExtras(getIntent().getExtras());
    }

    @Override
    protected void onDestroy() {
        cancelPlayer();
        super.onDestroy();
    }

    @Override
    protected void onConnectedToService() {
        signalingChannel = service.getQbChatHelper().getSignalingChannel();
    }

    private void cancelPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.stopPlaying();
        }
    }

    private void reject() {
        if (signalingChannel != null && opponent != null) {
            signalingChannel.sendReject(opponent, sessionId);
        }
        cancelPlayer();
        finish();
    }

    private void accept() {
        cancelPlayer();
        showOutgoingFragment(sessionDescriptionWrapper, opponent, call_type, sessionId);
    }

    private void parseIntentExtras(Bundle extras) {
        call_direction_type = (Consts.CALL_DIRECTION_TYPE) extras.getSerializable(
                Consts.CALL_DIRECTION_TYPE_EXTRA);
        call_type = extras.getInt(Consts.CALL_TYPE_EXTRA, 1);
        Log.i(TAG, "call_direction_type=" + call_direction_type);
        Log.i(TAG, "call_type=" + call_type);
        sessionId = extras.getString(WebRTC.SESSION_ID_EXTENSION, "");
        opponent = (QBUser) extras.getSerializable(Consts.USER);
        if (call_direction_type != null) {
            if (Consts.CALL_DIRECTION_TYPE.INCOMING.equals(call_direction_type)) {
                sessionDescriptionWrapper = extras.getParcelable(Consts.REMOTE_DESCRIPTION);
                showIncomingFragment();
            } else {
                showOutgoingFragment();
            }
        }
        Log.i(TAG, "opponentId=" + opponent);
    }

    private void showOutgoingFragment() {
        playOutgoingRingtone();
        OutgoingCallFragment outgoingCallFragment = (VIDEO_AUDIO_CALL == call_type) ? new VideoCallFragment() : new VoiceCallFragment();
        Bundle bundle = new Bundle();
        bundle.putSerializable(Consts.CALL_DIRECTION_TYPE_EXTRA, call_direction_type);
        bundle.putSerializable(Consts.USER, opponent);
        bundle.putInt(Consts.CALL_TYPE_EXTRA, call_type);
        outgoingCallFragment.setArguments(bundle);
        setCurrentFragment(outgoingCallFragment);
    }

    private void playOutgoingRingtone() {
        if (mediaPlayer != null) {
            mediaPlayer.playSound("ringback.wav", true);
        }
    }

    private void playIncomingRingtone() {
        if (mediaPlayer != null) {
            mediaPlayer.playDefaultRingTone();
        }
    }

    private void showOutgoingFragment(SessionDescriptionWrapper sessionDescriptionWrapper, QBUser opponentId,
            int callType, String sessionId) {
        Bundle bundle = VideoCallFragment.generateArguments(sessionDescriptionWrapper, opponentId,
                call_direction_type, callType, sessionId);
        OutgoingCallFragment outgoingCallFragment = (VIDEO_AUDIO_CALL == call_type) ? new VideoCallFragment() : new VoiceCallFragment();
        outgoingCallFragment.setArguments(bundle);
        setCurrentFragment(outgoingCallFragment);
    }

    private void showIncomingFragment() {
        playIncomingRingtone();
        IncomingCallFragment incomingCallFragment = IncomingCallFragment.newInstance(call_type,
                opponent.getFullName());
        setCurrentFragment(incomingCallFragment);
    }
}
