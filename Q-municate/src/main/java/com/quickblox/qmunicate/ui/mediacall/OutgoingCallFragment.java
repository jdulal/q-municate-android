package com.quickblox.qmunicate.ui.mediacall;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.quickblox.module.users.model.QBUser;
import com.quickblox.module.videochat_webrtc.ISignalingChannel;
import com.quickblox.module.videochat_webrtc.QBVideoChat;
import com.quickblox.module.videochat_webrtc.WebRTC;
import com.quickblox.module.videochat_webrtc.render.VideoStreamsView;
import com.quickblox.qmunicate.R;
import com.quickblox.qmunicate.core.communication.SessionDescriptionWrapper;
import com.quickblox.qmunicate.service.QBService;
import com.quickblox.qmunicate.ui.base.BaseFragment;
import com.quickblox.qmunicate.utils.Consts;
import com.quickblox.qmunicate.utils.DialogUtils;
import com.quickblox.qmunicate.utils.ErrorUtils;

import org.webrtc.MediaConstraints;
import org.webrtc.SessionDescription;

import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;


public abstract class OutgoingCallFragment extends BaseFragment implements View.OnClickListener, ISignalingChannel.MessageObserver {

    public static final String TAG = OutgoingCallFragment.class.getSimpleName();
    protected QBVideoChat qbVideoChat;
    protected QBUser opponent;
    private Consts.CALL_DIRECTION_TYPE call_direction_type;
    private SessionDescription remoteSessionDescription;
    private boolean bounded;
    private QBService service;
    private int call_type;
    private Timer callTimer;
    private ServiceConnection serviceConnection = new ChetServiceConnection();
    private OutgoingCallListener outgoingCallListener;
    private String sessionId;

    public interface OutgoingCallListener {

        public void onConnectionAccepted();

        public void onConnectionRejected();

        public void onConnectionClosed();
    }

    private enum STOP_TYPE {
        REJECTED, CLOSED
    }

    protected abstract int getContentView();

    protected abstract MediaConstraints getMediaConstraints();

    public static Bundle generateArguments(SessionDescriptionWrapper sessionDescriptionWrapper, QBUser user,
            Consts.CALL_DIRECTION_TYPE type, int callType, String sessionId) {
        Bundle args = new Bundle();
        args.putSerializable(Consts.USER, user);
        args.putSerializable(Consts.CALL_DIRECTION_TYPE_EXTRA, type);
        args.putInt(Consts.CALL_TYPE_EXTRA, callType);
        args.putParcelable(Consts.REMOTE_DESCRIPTION, sessionDescriptionWrapper);
        args.putString(WebRTC.SESSION_ID_EXTENSION, sessionId);
        return args;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            outgoingCallListener = (OutgoingCallListener) activity;
        } catch (ClassCastException exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        outgoingCallListener = null;
    }

    @Override
    public void onStart() {
        super.onStart();
        connectToService();
    }

    @Override
    public void onStop() {
        super.onStop();
        unbindService();
    }

    @Override
    public void onCall(QBUser user, int callType, SessionDescription sessionDescription, String sessionId,
            final ISignalingChannel.PLATFORM platform,
            final ISignalingChannel.PLATFORM_DEVICE_ORIENTATION orientation, Map<String, String> params) {

    }

    @Override
    public void onAccepted(QBUser user, SessionDescription sessionDescription, String sessionId,
            final ISignalingChannel.PLATFORM platform,
            final ISignalingChannel.PLATFORM_DEVICE_ORIENTATION orientation, Map<String, String> params) {
        cancelCallTimer();
        if (isExistActivity()) {
            getBaseActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DialogUtils.show(getActivity(), "accepted");
                }
            });
        }
        onConnectionEstablished();
    }

    @Override
    public void onParametersChanged(QBUser qbUser, String s,
            ISignalingChannel.PLATFORM_DEVICE_ORIENTATION deviceOrientation,
            Map<String, String> stringStringMap) {
    }

    @Override
    public void onStop(QBUser user, ISignalingChannel.STOP_REASON reason, String sessionId) {
        stopCall(false, STOP_TYPE.CLOSED);
    }

    @Override
    public void onRejected(QBUser user, String sessionId) {
        cancelCallTimer();
        if (isExistActivity()) {
            getBaseActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DialogUtils.show(getActivity(), "Rejected");
                    stopCall(false, STOP_TYPE.REJECTED);
                }
            });
        }
    }

    @Override
    public void onError(List<String> errors) {
        if (isExistActivity()) {
            ErrorUtils.showError(getActivity(), errors.toString());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (qbVideoChat != null) {
            qbVideoChat.onActivityPause();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(getContentView(), container, false);
        SessionDescriptionWrapper sessionDescriptionWrapper = getArguments().getParcelable(
                Consts.REMOTE_DESCRIPTION);
        if (sessionDescriptionWrapper != null) {
            remoteSessionDescription = sessionDescriptionWrapper.getSessionDescription();
        }
        call_direction_type = (Consts.CALL_DIRECTION_TYPE) getArguments().getSerializable(
                Consts.CALL_DIRECTION_TYPE_EXTRA);
        opponent = (QBUser) getArguments().getSerializable(Consts.USER);
        call_type = getArguments().getInt(Consts.CALL_TYPE_EXTRA);
        sessionId = getArguments().getString(WebRTC.SESSION_ID_EXTENSION, "");
        rootView.findViewById(R.id.stopСallButton).setOnClickListener(this);
        rootView.findViewById(R.id.muteMicrophoneButton).setOnClickListener(this);
        postInit(rootView);
        return rootView;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.stopСallButton:
                stopCall(true, STOP_TYPE.CLOSED);
                break;
            case R.id.muteMicrophoneButton:
                muteMicrophone();
                break;
            default:
                break;
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        if (qbVideoChat != null) {
            qbVideoChat.onActivityResume();
        }
    }

    public void initChat(ISignalingChannel signalingChannel) {
        MediaConstraints mediaConstraints = getMediaConstraints();
        VideoStreamsView videoView = (VideoStreamsView) getView().findViewById(R.id.ownVideoScreenImageView);
        qbVideoChat = new QBVideoChat(getActivity(), mediaConstraints, signalingChannel, videoView);
        signalingChannel.addMessageObserver(this);
        if (remoteSessionDescription != null) {
            qbVideoChat.setRemoteSessionDescription(remoteSessionDescription);
        }
        if (Consts.CALL_DIRECTION_TYPE.OUTGOING.equals(call_direction_type) && opponent != null) {
            startCall();
        } else {
            qbVideoChat.accept(opponent, sessionId);
            onConnectionEstablished();
        }
    }

    protected void onConnectionEstablished() {
        if (outgoingCallListener != null) {
            outgoingCallListener.onConnectionAccepted();
        }
    }

    protected void postInit(View rootView) {
    }

    protected void onConnectionClosed() {
        if (outgoingCallListener != null) {
            outgoingCallListener.onConnectionClosed();
        }
    }

    protected void onConnectionRejected() {
        if (outgoingCallListener != null) {
            outgoingCallListener.onConnectionRejected();
        }
    }

    private void muteMicrophone() {
        if (qbVideoChat != null) {
            qbVideoChat.muteMicrophone(!qbVideoChat.isMicrophoneMute());
        }
    }

    private void startCall() {
        qbVideoChat.call(opponent, call_type);
        callTimer = new Timer();
        callTimer.schedule(new CancelCallTimerTask(), 30 * Consts.SECOND);
    }

    private void connectToService() {
        Intent intent = new Intent(getActivity(), QBService.class);
        if (isExistActivity()) {
            getBaseActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void cancelCallTimer() {
        if (callTimer != null) {
            callTimer.cancel();
            callTimer = null;
        }
    }

    private void unbindService() {
        if (isExistActivity() && bounded) {
            getBaseActivity().unbindService(serviceConnection);
        }
    }

    private void stopCall(boolean sendStop, STOP_TYPE stopType) {
        cancelCallTimer();
        if (qbVideoChat != null) {
            if (sendStop) {
                qbVideoChat.stopCall();
            }
            qbVideoChat.clean();
        }
        if (STOP_TYPE.CLOSED.equals(stopType)) {
            onConnectionClosed();
        } else {
            onConnectionRejected();
        }
    }

    private void onConnectedToService() {
        ISignalingChannel signalingChannel = service.getQbChatHelper().getSignalingChannel();
        if (signalingChannel != null && isExistActivity()) {
            initChat(signalingChannel);
        } else if (isExistActivity()) {
            ErrorUtils.showError(getActivity(), "Cannot establish connection. Check internet settings");
        }
    }

    private class ChetServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.i(TAG, "onServiceConnected");
            bounded = true;
            service = ((QBService.QBServiceBinder) binder).getService();
            onConnectedToService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    }

    class CancelCallTimerTask extends TimerTask {

        @Override
        public void run() {
            stopCall(true, STOP_TYPE.CLOSED);
        }
    }
}
